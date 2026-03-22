import traceback
from redis import Redis
from rq import Queue
from flask import Flask, request, jsonify
from elasticsearch import Elasticsearch
import json
import os
from datetime import datetime, timezone, timedelta
from machine_learning.forest import fetch_data_from_es, load_tfidf_models, recommend_recipe, load_weights_from_json, \
    train_tfidf_model, train_weight

app = Flask(__name__)
es_host = os.getenv("ELASTICSEARCH_HOST", "elasticsearch")
es = Elasticsearch([f"http://{es_host}:9200"])

# Redis 연결
redis_url = os.getenv('REDIS_URL', 'redis://redis:6379')
redis = Redis.from_url(redis_url, socket_timeout=180)
queue = Queue(connection=redis, job_timeout="30m")

# ================================================================
# Utility Functions (공통 ES 인덱스 및 매핑 관리)
# ================================================================

def load_mapping(file_path):
    """
    JSON 파일에서 매핑을 로드하는 함수
    KR: 주어진 파일 경로에서 ElasticSearch 매핑 정보를 로드합니다.
    """
    with open(file_path, "r") as f:
        return json.load(f)
@app.route("/upload/index", methods=["POST"])
def create_index_if_not_exists(index_name, mapping_file=None):
    """
    인덱스가 없으면 매핑을 설정하고 인덱스를 생성하는 함수
    KR: 지정된 인덱스가 존재하지 않으면, mapping_file이 제공되었을 경우 해당 파일에서 매핑 정보를 로드하여
        인덱스를 생성합니다. 매핑 파일이 없거나 존재하지 않으면 빈 매핑({})으로 인덱스를 생성합니다.
    """
    if not es.indices.exists(index=index_name):
        if mapping_file and os.path.exists(mapping_file):
            try:
                mapping = load_mapping(mapping_file)
                es.indices.create(index=index_name, body=mapping)
                app.logger.info("인덱스 '{}'가 매핑 파일 '{}'을 사용하여 생성되었습니다.".format(index_name, mapping_file))
            except Exception as e:
                app.logger.error("매핑 파일 '{}'을 사용하여 인덱스를 생성하는 중 오류 발생: {}. 빈 매핑으로 생성합니다.".format(mapping_file, str(e)))
                es.indices.create(index=index_name, body={})
                app.logger.info("인덱스 '{}'가 빈 매핑으로 생성되었습니다.".format(index_name))
        else:
            # KR: 매핑 파일이 제공되지 않거나 존재하지 않으면 기본(empty) 매핑으로 인덱스 생성
            es.indices.create(index=index_name, body={})
            app.logger.info("인덱스 '{}'가 빈 매핑으로 생성되었습니다.".format(index_name))
        return jsonify({"message": f"Index {index_name} created successfully"}), 200
    else:
        return jsonify({"message": f"Index {index_name} already exists"}), 400

def get_index_and_mapping(file_type: str):
    """
    주어진 file_type에 따라 인덱스 이름과 매핑 파일명을 반환하는 함수
    KR: file_type이 'cocktail', 'food', 'forum_post', 'forum_category' 등일 때 각 ES 인덱스 이름과 매핑 파일을 반환합니다.
    """
    index_mapping = {
        "cocktail": ("recipe_cocktail", "cocktail_mapping.json"),
        "food": ("recipe_food", "food_mapping.json"),
        "feed": ("feed", "feed_mapping.json"),
        "forum_post": ("forum_post", "forum_post_mapping.json"),
        "forum_category": ("forum_category", "forum_category_mapping.json")
    }
    return index_mapping.get(file_type, (None, None))


# ================================================================
# 기존 도메인(칵테일, 음식 등) 관련 엔드포인트 (변경 없이 그대로 유지)
# ================================================================

# 글 하나 업로드
@app.route("/upload/one", methods=["POST"])
def upload_one():
    try:
        data = request.json
        if not data:
            return jsonify({"error": "No data provided"}), 400

        file_type = request.form.get("type")
        if not file_type:
            return jsonify({"error": "Type is required"}), 400

        index_name, mapping_file = get_index_and_mapping(file_type)

        # 인덱스가 없으면 매핑을 적용하여 생성
        if not es.indices.exists(index=index_name):
            create_index_if_not_exists(index_name, mapping_file)

        # 데이터 삽입
        es.index(index=index_name, body=data)
        return jsonify({"message": "Data uploaded successfully"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500
@app.route("/update/reaction-counts", methods=["POST"])
@app.route("/update/likes-reports", methods=["POST"])
def update_reaction_counts():
    """
    Redis에서 받은 좋아요/싫어요 절대 카운트를
    Elasticsearch 문서에 반영하는 엔드포인트
    """
    try:
        data = request.json
        if not data:
            return jsonify({"error": "No data provided"}), 400

        reaction_count_data = data.get("reaction_count_data", [])
        if not reaction_count_data:
            legacy_like_report_data = data.get("like_report_data", [])
            if legacy_like_report_data:
                reaction_count_data = []
                grouped = {}
                for entry in legacy_like_report_data:
                    post_id = entry.get("postId")
                    content_type = entry.get("type")
                    if not post_id or not content_type:
                        continue
                    key = (post_id, content_type)
                    grouped.setdefault(key, {"postId": post_id, "type": content_type, "likeCount": 0, "dislikeCount": 0})
                    key_type = entry.get("keyType")
                    value = entry.get("value", 0)
                    if key_type == "like":
                        grouped[key]["likeCount"] = value
                    elif key_type in ("report", "dislike"):
                        grouped[key]["dislikeCount"] = value
                reaction_count_data = list(grouped.values())

        if not reaction_count_data:
            return jsonify({"error": "No reaction count data provided"}), 400

        for entry in reaction_count_data:
            post_id = entry.get("postId")  # Elasticsearch의 _id
            content_type = entry.get("type")  # "cocktail" 또는 "food"
            like_count = entry.get("likeCount")
            dislike_count = entry.get("dislikeCount")

            if not post_id or not content_type or like_count is None or dislike_count is None:
                app.logger.warning(f"Skipping entry due to missing fields: {entry}")
                continue

            index_name, mapping_file = get_index_and_mapping(content_type)
            if not index_name:
                app.logger.error(f"Invalid content type: {content_type}")
                continue

            try:
                app.logger.info(f"Fetching document {post_id} from index {index_name}")
                doc = es.get(index=index_name, id=post_id, ignore=404)

                if doc.get("found", False):
                    update_data = {
                        "like": like_count,
                        "dislike": dislike_count
                    }
                    app.logger.info(f"Updating document {post_id} in index {index_name} with {update_data}")
                    es.update(index=index_name, id=post_id, body={"doc": update_data})
                else:
                    app.logger.error(f"Document with ID {post_id} not found in index {index_name}")
            except Exception as e:
                error_message = traceback.format_exc()
                app.logger.error(
                    f"Elasticsearch update error for document {post_id} in index {index_name}:\n{error_message}")

        return jsonify({"message": "Reaction counts updated successfully"}), 200
    except Exception as e:
        error_message = traceback.format_exc()
        app.logger.error(f"Unhandled error in /update/reaction-counts:\n{error_message}")
        return jsonify({"error": str(e)}), 500


@app.route("/upload/json", methods=["POST"])
def upload_json():
    try:
        file = request.files.get("file")
        if not file:
            return jsonify({"error": "No file provided"}), 400

        file_type = request.form.get("type")
        if not file_type:
            return jsonify({"error": "Type is required"}), 400

        index_name, mapping_file = get_index_and_mapping(file_type)
        if not index_name:
            return jsonify({"error": "Invalid type"}), 400

        # 인덱스가 존재하지 않으면 생성
        if not es.indices.exists(index=index_name):
            create_index_if_not_exists(index_name, mapping_file)

        # JSON 데이터 읽기
        data = json.load(file)
        for item in data:
            es.index(index=index_name, body=item)

        return jsonify({"message": "Data uploaded successfully"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

# 재료별 검색 (레시피만 검색되도록 수정)
@app.route("/search", methods=["GET"])
def search():
    try:
        q = request.args.get("q", "")
        type_filter = request.args.get("type", "")
        category = request.args.get("category", "")
        cooking_method = request.args.get("cookingMethod", "")
        page = request.args.get("page", 1, type=int)
        size = request.args.get("size", 20, type=int)

        index_name, _ = get_index_and_mapping(type_filter)
        if not index_name:
            return jsonify({"error": "Invalid type filter"}), 400

        # 1) 검색어/카테고리/조리방법 등에 따른 query 빌드 (기존 로직 유지)
        if type_filter == "food":
            category_field = "RCP_PAT2"
            cooking_field = "RCP_WAY2"
            multi_match_fields = ["name", "ingredients.ingredient", "RCP_PAT2"]
        elif type_filter == "forum_post":
            category_field = "category"
            cooking_field = "cookingMethod"  # 포럼에는 없는 필드
            multi_match_fields = ["title", "content", "authorName", "category"]
        else:
            category_field = "category"
            cooking_field = "cookingMethod"
            multi_match_fields = ["name", "ingredients.ingredient", "category"]

        if q and category and cooking_method:
            query = {
                "bool": {
                    "must": [
                        {"multi_match": {"query": q, "fields": multi_match_fields}},
                        {"term": {category_field: {"value": category}}},
                        {"term": {cooking_field: {"value": cooking_method}}}
                    ]
                }
            }
        elif q and category:
            query = {
                "bool": {
                    "must": [
                        {"multi_match": {"query": q, "fields": multi_match_fields}},
                        {"term": {category_field: {"value": category}}}
                    ]
                }
            }
        elif q and cooking_method:
            query = {
                "bool": {
                    "must": [
                        {"multi_match": {"query": q, "fields": multi_match_fields}},
                        {"term": {cooking_field: {"value": cooking_method}}}
                    ]
                }
            }
        elif category and cooking_method:
            query = {
                "bool": {
                    "must": [
                        {"term": {category_field: {"value": category}}},
                        {"term": {cooking_field: {"value": cooking_method}}}
                    ]
                }
            }
        elif q:
            query = {"multi_match": {"query": q, "fields": multi_match_fields}}
        elif category:
            query = {"term": {category_field: {"value": category}}}
        elif cooking_method:
            query = {"term": {cooking_field: {"value": cooking_method}}}
        else:
            query = {"match_all": {}}

        # 2) forum_post인 경우, comments 배열도 함께 가져오도록 source_fields에 추가
        if type_filter == "food":
            source_fields = ["name", "RCP_PAT2", "RCP_WAY2", "like", "abv", "ATT_FILE_NO_MAIN"]
        elif type_filter == "forum_post":
            source_fields = [
                "title", "content", "authorName", "contentJSON",
                "viewsCount", "likesCount", "createdAt", "updatedAt",
                "category", "comments", "sticky"
            ]
        else:
            source_fields = ["name", "category", "like", "abv"]

        body = {
            "from": (page - 1) * size,
            "size": size,
            "_source": source_fields,
            "query": query
        }

        # 3) Elasticsearch 검색
        res = es.search(index=index_name, body=body)

        # 4) 검색 결과 파싱: doc["_source"] + doc["_id"]
        hits = res["hits"]["hits"]
        results = []
        for hit in hits:
            doc = hit["_source"]
            doc["id"] = hit["_id"]
            results.append(doc)

        # 5) forum_post인 경우, comments에서 최신 댓글 추출 → latestComment
        if type_filter == "forum_post":
            for doc in results:
                comments = doc.get("comments", [])
                if comments:
                    # createdAt 기준 내림차순 정렬하여 첫 번째를 최신 댓글로
                    sorted_comments = sorted(
                        comments,
                        key=lambda c: c.get("createdAt", ""),
                        reverse=True
                    )
                    doc["latestComment"] = sorted_comments[0]
                else:
                    doc["latestComment"] = None

        return jsonify(results), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


# 검색 알코올 (레시피용; 단 하나만 남김)
@app.route("/search/alcohol", methods=["GET"], endpoint="unique_search_alcohol")
def search_alcohol():
    try:
        min_abv = request.args.get("min_abv", 0, type=int)
        max_abv = request.args.get("max_abv", 100, type=int)
        page = request.args.get("page", 1, type=int)
        size = request.args.get("size", 20, type=int)
        res = es.search(index="recipe_cocktail", body={
            "from": (page - 1) * size,
            "size": size,
            "_source": ["name", "category", "like"],
            "query": {
                "range": {"abv": {"gte": min_abv, "lte": max_abv}}
            }
        })
        results = [{**hit["_source"], "id": hit["_id"]} for hit in res["hits"]["hits"]]
        return jsonify(results)
    except Exception as e:
        return jsonify({"error": str(e)})

# 상세 조회 (레시피용)
@app.route("/detail/<doc_id>", methods=["GET"], endpoint="recipe_detail")
def detail(doc_id):
    type_filter = request.args.get("type", "")
    index_name, _ = get_index_and_mapping(type_filter)
    if not index_name:
        return jsonify({"error": "Invalid type filter"}), 400
    try:
        # ES 에서 해당 ID 문서 검색 (주의: 변수 이름 수정)
        response = es.get(index=index_name, id=doc_id)
        return jsonify(response["_source"])
    except Exception as e:
        return jsonify({"error": str(e)}), 404


# ================================================================
# Forum Endpoints (ElasticSearch-based) - 새 Forum 기능
# ================================================================

# Forum 게시글 생성 (Flask)
@app.route("/forum/post", methods=["POST"])
def create_forum_post():
    try:
        data = request.json
        if not data:
            return jsonify({"error": "데이터가 제공되지 않았습니다."}), 400

        # 만약 categoryId가 있고 category 필드가 없으면 categoryId 값을 category에 복사
        if "categoryId" in data and "category" not in data:
            data["category"] = data["categoryId"]

        index_name, mapping_file = get_index_and_mapping("forum_post")
        if not es.indices.exists(index=index_name):
            create_index_if_not_exists(index_name, mapping_file)

        # 기본 필드 설정 – 이미 값이 전달된 경우 그대로 사용
        data.setdefault("viewsCount", 0)
        data.setdefault("likesCount", 0)
        data.setdefault("likedBy", [])
        data.setdefault("reportCount", 0)
        data.setdefault("comments", [])
        data.setdefault("sticky", False)  # 새 필드: 고정 게시글 여부 (default: False)
        now = datetime.now(timezone.utc).isoformat()
        data.setdefault("createdAt", now)
        data.setdefault("updatedAt", now)

        # 요청 데이터에 contentJSON, authorName 등 새로운 필드가 있다면 그대로 저장됩니다.
        res = es.index(index=index_name, body=data)

        # 인덱스 새로고침 (문서가 즉시 검색될 수 있도록)
        es.indices.refresh(index=index_name)

        return jsonify({"message": "게시글이 생성되었습니다.", "id": res["_id"]}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500



# Forum 게시글 상세 조회
@app.route("/forum/post/<doc_id>", methods=["GET"], endpoint="forum_post_detail")
def get_forum_post(doc_id):
    """
    게시글 상세 조회 (Forum)
    KR: ES의 'forum_post' 인덱스에서 주어진 문서 ID의 게시글을 조회하여 반환합니다.
        변경 사항: 댓글 배열이 있을 경우, 최신 댓글(latestComment)을 계산하여 추가합니다.
    """
    try:
        index_name, _ = get_index_and_mapping("forum_post")
        res = es.get(index=index_name, id=doc_id)
        data = res["_source"]
        # ES에서 생성된 문서 id를 JSON 응답에 포함시킵니다.
        data["id"] = res["_id"]

        # 댓글 배열이 존재하면 최신 댓글을 계산하여 latestComment 필드에 추가
        if "comments" in data and isinstance(data["comments"], list) and len(data["comments"]) > 0:
            # createdAt 값이 ISO 형식 문자열이라면 정렬이 가능함 (내림차순 정렬)
            sorted_comments = sorted(data["comments"], key=lambda c: c.get("createdAt", ""), reverse=True)
            # 가장 최근 댓글을 latestComment에 저장
            data["latestComment"] = sorted_comments[0]
        else:
            data["latestComment"] = None  # 댓글이 없으면 null 처리

        return jsonify(data)
    except Exception as e:
        return jsonify({"error": str(e)}), 404


# Forum 게시글 제목 수정 (수정된 버전)
@app.route("/forum/post/<doc_id>/title", methods=["PUT"])
def update_forum_post_title(doc_id):
    """
    게시글 제목 수정 (Forum Post)
    KR: 주어진 문서 ID의 게시글에서 제목(title)을 수정합니다.
        이 엔드포인트는 'forum_post' 인덱스를 사용합니다.
    """
    try:
        new_title = request.json.get("title")
        if not new_title:
            return jsonify({"error": "새 제목이 필요합니다."}), 400
        index_name, _ = get_index_and_mapping("forum_post")
        post = es.get(index=index_name, id=doc_id)["_source"]
        post["title"] = new_title
        post["updatedAt"] = datetime.now(timezone.utc).isoformat()

        # 수정자 정보 처리: isAdmin 플래그에 따라 관리자인 경우와 일반 사용자인 경우 분기 처리
        if request.json.get("isAdmin", False):
            post["editedTitleByAdmin"] = True  # 관리자가 수정한 경우
            post["editedByTitle"] = "ADMIN"  # 관리자인 경우 수정자 정보를 "ADMIN" 또는 관리자의 이름/ID로 설정
        else:
            post["editedTitleByAdmin"] = False  # 일반 사용자가 수정한 경우
            post["editedByTitle"] = request.json.get("editedBy", "USER")

        es.index(index=index_name, id=doc_id, body=post)
        return jsonify({"message": "제목이 수정되었습니다.", "title": new_title}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


# Forum 게시글 내용 수정 (TipTap JSON 전용, 수정된 버전)
@app.route("/forum/post/<doc_id>/content", methods=["PUT"])
def update_forum_post_content(doc_id):
    """
    게시글 내용 수정 (Forum - TipTap JSON)
    KR: 게시글의 contentJSON 필드를 업데이트합니다.
    """
    try:
        contentJSON = request.json.get("contentJSON")
        if not contentJSON:
            return jsonify({"error": "contentJSON 필드는 필수입니다."}), 400
        index_name, _ = get_index_and_mapping("forum_post")
        post = es.get(index=index_name, id=doc_id)["_source"]
        post["contentJSON"] = contentJSON
        post["updatedAt"] = datetime.now(timezone.utc).isoformat()

        # 수정자 정보 처리: isAdmin 플래그에 따라 관리자인지 여부를 체크
        if request.json.get("isAdmin", False):
            post["editedContentByAdmin"] = True
            post["editedByContent"] = "ADMIN"  # 관리자인 경우 수정자 정보를 "ADMIN" 또는 관리자의 이름/ID로 설정
            post["locked"] = True
        else:
            post["editedContentByAdmin"] = False
            post["editedByContent"] = request.json.get("editedBy", "USER")

        es.index(index=index_name, id=doc_id, body=post)
        # ★ 인덱스 새로고침 추가: 문서가 즉시 검색될 수 있도록 보장합니다.
        es.indices.refresh(index=index_name)
        return jsonify({"message": "내용이 수정되었습니다.", "contentJSON": contentJSON}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


# Forum 게시글 좋아요 토글
@app.route("/forum/post/<doc_id>/like", methods=["POST"])
def toggle_forum_post_like(doc_id):
    """
    게시글 좋아요 토글 (Forum)
    KR: 특정 게시글에 대해 좋아요를 추가 또는 취소하여 likedBy 배열과 likesCount를 업데이트합니다.
    """
    try:
        # 기존 코드에서 body에 memberId를 받고 있으므로 그대로 둡니다.
        req_data = request.json
        if not req_data or "memberId" not in req_data:
            return jsonify({"error": "memberId가 필요합니다."}), 400
        member_id = req_data["memberId"]

        index_name, _ = get_index_and_mapping("forum_post")
        post_data = es.get(index=index_name, id=doc_id)["_source"]
        liked_by = post_data.get("likedBy", [])

        # 좋아요 추가/취소 로직
        if member_id in liked_by:
            liked_by.remove(member_id)
            post_data["likesCount"] = max(post_data.get("likesCount", 0) - 1, 0)
            liked = False
        else:
            liked_by.append(member_id)
            post_data["likesCount"] = post_data.get("likesCount", 0) + 1
            liked = True

        post_data["likedBy"] = liked_by
        post_data["updatedAt"] = datetime.now(timezone.utc).isoformat()

        # 수정된 문서 재인덱싱
        es.index(index=index_name, id=doc_id, body=post_data)

        # **여기서 핵심: liked + totalLikes로 JSON 응답**
        return jsonify({
            "liked": liked,
            "likesCount": post_data["likesCount"]
        }), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 500


# Forum 게시글 신고 처리
@app.route("/forum/post/<doc_id>/report", methods=["POST"])
def report_forum_post(doc_id):
    try:
        req_data = request.json
        reporter_id = req_data.get("reporterId")
        reason = req_data.get("reason")
        if not reporter_id or not reason:
            return jsonify({"error": "신고자 ID와 신고 사유는 필수입니다."}), 400
        REPORT_THRESHOLD = 10
        index_name, _ = get_index_and_mapping("forum_post")
        post = es.get(index=index_name, id=doc_id)["_source"]

        # 기존 코드는 post["member"]["memberId"]로 작성했지만,
        # 실제 문서에는 최상위에 memberId가 존재하므로 아래와 같이 수정합니다.
        if post.get("memberId") == reporter_id:
            return jsonify({"error": "자신의 게시글은 신고할 수 없습니다."}), 400

        post["reportCount"] = post.get("reportCount", 0) + 1
        if post["reportCount"] >= REPORT_THRESHOLD:
            post["hidden"] = True
        post["updatedAt"] = datetime.now(timezone.utc).isoformat()
        es.index(index=index_name, id=doc_id, body=post)
        return jsonify({"message": "게시글이 신고되었습니다.", "reportCount": post["reportCount"]}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500



# Forum 게시글 삭제 (논리 삭제)
@app.route("/forum/post/<doc_id>", methods=["DELETE"], endpoint="delete_forum_post")
def delete_forum_post(doc_id):
    """
    게시글 삭제 (Forum, 논리 삭제)
    """
    try:
        index_name, _ = get_index_and_mapping("forum_post")
        post = es.get(index=index_name, id=doc_id)["_source"]

        # 1) 원본 제목/내용이 없으면 저장해 둡니다. (이미 있으면 덮어쓰지 않음)
        if "originalTitle" not in post:
            post["originalTitle"] = post.get("title", "")
        if "originalContent" not in post:
            post["originalContent"] = post.get("content", "")

        # 2) 삭제 이력은 forum_post_history 인덱스에 기록
        history = {
            "postId": post.get("id", doc_id),
            "title": post.get("title"),
            "content": post.get("content"),
            "authorName": post.get("member", {}).get("nickName", "Unknown"),
            "deletedAt": datetime.now(timezone.utc).isoformat(),
            "fileUrls": post.get("fileUrls", [])
        }
        es.index(index="forum_post_history", body=history)

        # 3) 실제로 소프트 삭제 처리
        post["removedBy"] = request.args.get("removedBy", "USER")
        post["hidden"] = True
        post["title"] = "[Deleted]"
        post["content"] = "This post has been deleted."
        post["updatedAt"] = datetime.now(timezone.utc).isoformat()

        es.index(index=index_name, id=doc_id, body=post)
        return jsonify({"message": "게시글이 삭제 처리되었습니다."}), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 500



@app.route("/forum/post/<doc_id>/restore", methods=["POST"])
def restore_forum_post(doc_id):
    """
    게시글 복구 (Forum)
    """
    try:
        index_name, _ = get_index_and_mapping("forum_post")
        post = es.get(index=index_name, id=doc_id)["_source"]

        # 1) 저장된 원본 제목과 내용을 복원
        if "originalTitle" in post:
            post["title"] = post["originalTitle"]
        if "originalContent" in post:
            post["content"] = post["originalContent"]

        # 2) hidden 상태를 False로 전환하고 삭제 정보 제거
        post["hidden"] = False
        post["removedBy"] = None  # (선택) 삭제자 정보 제거
        post["updatedAt"] = datetime.now(timezone.utc).isoformat()

        es.index(index=index_name, id=doc_id, body=post)
        return jsonify({"message": "게시글이 복구되었습니다."}), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 500


# Forum 댓글 생성 엔드포인트
@app.route("/forum/comment", methods=["POST"])
def create_forum_comment():
    try:
        data = request.json
        # 필수 필드 확인
        for field in ["postId", "memberId", "content"]:
            if field not in data:
                return jsonify({"error": f"{field} 필드는 필수입니다."}), 400

        index_name, _ = get_index_and_mapping("forum_post")
        post_id = str(data["postId"])
        post = es.get(index=index_name, id=post_id)["_source"]

        # 클라이언트에서 nickName이 제공되지 않으면 "Unknown"으로 기본값 설정
        nick_name = data.get("authorName", data.get("nickName", "Unknown"))

        new_comment = {
            "id": None,
            "content": data["content"],
            "contentJSON": data.get("contentJSON", ""),
            "member": {
                "memberId": data["memberId"],
                "nickName": nick_name
            },
            "authorName": nick_name,       # 댓글 작성자 이름 추가
            "memberId": data["memberId"],   # 댓글 작성자 ID 추가
            "likesCount": 0,
            "hidden": False,
            "removedBy": None,
            "fileUrl": data.get("fileUrl", ""),
            "reportCount": 0,
            "parentCommentId": data.get("parentCommentId"),
            "opAuthorName": data.get("opAuthorName", ""),
            "opContent": data.get("opContent", ""),
            "createdAt": datetime.now(timezone.utc).isoformat(),
            "updatedAt": datetime.now(timezone.utc).isoformat(),
            "editedBy": None,
            "locked": False
        }
        comments = post.get("comments", [])
        new_comment["id"] = len(comments) + 1
        comments.append(new_comment)
        post["comments"] = comments
        post["updatedAt"] = datetime.now(timezone.utc).isoformat()

        es.index(index=index_name, id=post_id, body=post)
        # ★ 인덱스 새로고침 추가: 새 댓글이 즉시 검색되도록 보장합니다.
        es.indices.refresh(index=index_name)

        return jsonify({"message": "댓글이 추가되었습니다.", "comment": new_comment}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

# Forum 댓글 조회
@app.route("/forum/comments", methods=["GET"])
def get_forum_comments():
    """
    댓글 조회 (Forum)
    KR: 쿼리 파라미터 'postId'에 해당하는 게시글 문서에서 'comments' 배열을 반환합니다.
    """
    try:
        post_id = request.args.get("postId")
        if not post_id:
            return jsonify({"error": "postId 파라미터가 필요합니다."}), 400
        index_name, _ = get_index_and_mapping("forum_post")
        post = es.get(index=index_name, id=str(post_id))["_source"]
        comments = post.get("comments", [])
        return jsonify(comments), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

# Forum 댓글 수정
@app.route("/forum/comment/<int:comment_id>", methods=["PUT"])
def update_forum_comment(comment_id):
    """
    댓글 수정 (Forum)
    KR: 특정 댓글의 contentJSON (TipTap JSON)을 업데이트합니다.
    """
    try:
        req_data = request.json
        new_contentJSON = req_data.get("contentJSON")
        if not new_contentJSON:
            return jsonify({"error": "contentJSON 필드는 필수입니다."}), 400

        post_id = req_data.get("postId")
        if not post_id:
            return jsonify({"error": "postId 필드가 필요합니다."}), 400

        index_name, _ = get_index_and_mapping("forum_post")
        post = es.get(index=index_name, id=str(post_id))["_source"]
        comments = post.get("comments", [])
        updated = False
        for comment in comments:
            if comment.get("id") == comment_id:
                comment["contentJSON"] = new_contentJSON
                comment["updatedAt"] = datetime.now(timezone.utc).isoformat()
                comment["editedBy"] = req_data.get("editedBy", "USER")
                if req_data.get("isAdmin", False):
                    comment["locked"] = True
                updated = True
                break
        if not updated:
            return jsonify({"error": "댓글을 찾을 수 없습니다."}), 404
        post["updatedAt"] = datetime.now(timezone.utc).isoformat()
        es.index(index=index_name, id=str(post_id), body=post)
        return jsonify({"message": "댓글이 수정되었습니다."}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

# Forum 댓글 삭제 (논리 삭제)
@app.route("/forum/comment/<int:comment_id>", methods=["DELETE"])
def delete_forum_comment(comment_id):
    """
    댓글 삭제 (Forum, 논리 삭제)
    KR: 특정 댓글의 content를 "[Removed]"로 변경하고, hidden 상태를 true로 설정합니다.
        삭제 이력은 'forum_comment_history' 인덱스에 기록됩니다.
    """
    try:
        post_id = request.args.get("postId")
        if not post_id:
            return jsonify({"error": "postId 파라미터가 필요합니다."}), 400

        index_name, _ = get_index_and_mapping("forum_post")
        post = es.get(index=index_name, id=str(post_id))["_source"]
        comments = post.get("comments", [])
        deleted = False
        for comment in comments:
            if comment.get("id") == comment_id:
                history = {
                    "commentId": comment_id,
                    "content": comment.get("content"),
                    "authorName": comment.get("member", {}).get("nickName", "Unknown"),
                    "deletedAt": datetime.now(timezone.utc).isoformat()
                }
                es.index(index="forum_comment_history", body=history)
                comment["content"] = "[Removed]"
                comment["hidden"] = True
                comment["removedBy"] = request.args.get("removedBy", "USER")
                comment["updatedAt"] = datetime.now(timezone.utc).isoformat()
                deleted = True
                break
        if not deleted:
            return jsonify({"error": "댓글을 찾을 수 없습니다."}), 404
        post["updatedAt"] = datetime.now(timezone.utc).isoformat()
        es.index(index=index_name, id=str(post_id), body=post)
        return jsonify({"message": "댓글이 삭제되었습니다."}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route("/forum/comment/<int:comment_id>/restore", methods=["POST"])
def restore_forum_comment(comment_id):
    try:
        # postId 파라미터를 통해 해당 게시글 ID를 가져옵니다.
        post_id = request.args.get("postId")
        if not post_id:
            return jsonify({"error": "postId 파라미터가 필요합니다."}), 400

        # 'forum_post' 인덱스 이름과 매핑 정보를 가져옵니다.
        index_name, _ = get_index_and_mapping("forum_post")
        # 게시글을 Elasticsearch에서 조회합니다.
        post = es.get(index=index_name, id=str(post_id))["_source"]
        comments = post.get("comments", [])
        restored = False
        original_content = None

        # 댓글 삭제 시 저장된 이력을 통해 원래 내용을 복원합니다.
        # 여기서는 'forum_comment_history' 인덱스에서 해당 댓글의 최근 삭제 이력을 조회합니다.
        history_query = {
            "query": {
                "term": {
                    "commentId": comment_id
                }
            },
            "sort": [
                {
                    "deletedAt": {
                        "order": "desc"
                    }
                }
            ],
            "size": 1
        }
        history_res = es.search(index="forum_comment_history", body=history_query)
        if history_res["hits"]["total"]["value"] > 0:
            original_content = history_res["hits"]["hits"][0]["_source"]["content"]

        # 게시글 내의 댓글 배열에서 해당 comment_id를 가진 댓글을 찾습니다.
        for comment in comments:
            if comment.get("id") == comment_id:
                # 삭제 이력에서 원본 내용이 있으면 복원합니다.
                if original_content:
                    comment["content"] = original_content
                else:
                    # 원본 내용을 찾을 수 없으면, 복원할 수 없다는 메시지를 남깁니다.
                    comment["content"] = "원본 내용 복원 불가"
                comment["hidden"] = False
                comment["removedBy"] = None
                comment["updatedAt"] = datetime.now(timezone.utc).isoformat()
                restored = True
                break

        if not restored:
            return jsonify({"error": "복원할 댓글을 찾을 수 없습니다."}), 404

        # 게시글의 업데이트 시간을 갱신하고 재인덱싱합니다.
        post["updatedAt"] = datetime.now(timezone.utc).isoformat()
        es.index(index=index_name, id=str(post_id), body=post)
        return jsonify({"message": "댓글이 복원되었습니다."}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/forum/comment/<int:comment_id>/like", methods=["POST"])
def toggle_forum_comment_like(comment_id):
    try:
        req_data = request.json
        if not req_data or "memberId" not in req_data:
            return jsonify({"error": "memberId가 필요합니다."}), 400
        member_id = req_data["memberId"]

        post_id = req_data.get("postId")  # 어느 게시글의 댓글인지 알아야 함
        index_name, _ = get_index_and_mapping("forum_post")
        post_data = es.get(index=index_name, id=post_id)["_source"]
        comments = post_data.get("comments", [])

        for c in comments:
            if c["id"] == comment_id:
                # 좋아요 배열 또는 likesCount가 없다면 초기화
                liked_by = c.get("likedBy", [])
                if member_id in liked_by:
                    liked_by.remove(member_id)
                    c["likesCount"] = max(c.get("likesCount", 0) - 1, 0)
                    liked = False
                else:
                    liked_by.append(member_id)
                    c["likesCount"] = c.get("likesCount", 0) + 1
                    liked = True
                c["likedBy"] = liked_by
                c["updatedAt"] = datetime.now(timezone.utc).isoformat()

                # 수정된 댓글을 comments 배열에 반영
                break

        post_data["comments"] = comments
        post_data["updatedAt"] = datetime.now(timezone.utc).isoformat()
        es.index(index=index_name, id=post_id, body=post_data)

        return jsonify({
            "liked": liked,
            "totalLikes": c["likesCount"]
        }), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 500


# Forum 댓글 신고 처리
@app.route("/forum/comment/<int:comment_id>/report", methods=["POST"])
def report_forum_comment(comment_id):
    try:
        req_data = request.json
        reporter_id = req_data.get("reporterId")
        reason = req_data.get("reason")
        if not reporter_id or not reason:
            return jsonify({"error": "신고자 ID와 신고 사유가 필요합니다."}), 400
        REPORT_THRESHOLD = 10
        post_id = req_data.get("postId")
        if not post_id:
            return jsonify({"error": "postId 필드가 필요합니다."}), 400
        index_name, _ = get_index_and_mapping("forum_post")
        post = es.get(index=index_name, id=str(post_id))["_source"]
        comments = post.get("comments", [])
        updated = False
        for comment in comments:
            if comment.get("id") == comment_id:
                if comment.get("member", {}).get("memberId") == reporter_id:
                    return jsonify({"error": "자신의 댓글은 신고할 수 없습니다."}), 400
                comment["reportCount"] = comment.get("reportCount", 0) + 1
                if comment["reportCount"] >= REPORT_THRESHOLD:
                    comment["hidden"] = True
                comment["updatedAt"] = datetime.now(timezone.utc).isoformat()
                updated = True
                break
        if not updated:
            return jsonify({"error": "댓글을 찾을 수 없습니다."}), 404
        post["updatedAt"] = datetime.now(timezone.utc).isoformat()
        es.index(index=index_name, id=str(post_id), body=post)
        return jsonify({"message": "댓글이 신고되었습니다."}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

# Forum 카테고리 조회 - 수정된 엔드포인트
@app.route("/forum/category", methods=["GET"])
def get_forum_categories():
    """
    포럼 카테고리 조회 (Forum)
    KR: ES의 'forum_category' 인덱스에서 모든 카테고리 문서를 검색하여 반환합니다.
    """
    try:
        index_name, _ = get_index_and_mapping("forum_category")
        # Use a match_all query to return all documents
        body = {"query": {"match_all": {}}}
        res = es.search(index=index_name, body=body)
        # Map ES results to include the ES-generated _id as 'id'
        categories = [{**hit["_source"], "id": hit["_id"]} for hit in res["hits"]["hits"]]
        return jsonify(categories), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

# Forum 카테고리 상세 조회 (ID 기반)
@app.route("/forum/category/<category_id>", methods=["GET"])
def get_forum_category_by_id(category_id):
    """
    특정 카테고리 ID를 기반으로 ES의 'forum_category' 인덱스에서 카테고리 문서를 조회합니다.
    만약 해당 ID를 가진 카테고리가 존재하면, 문서의 내용을 반환하며,
    ES에서 생성한 _id 값을 'id' 필드에 할당하여 클라이언트에 반환합니다.

    매개변수:
        category_id (str): 조회할 카테고리의 ID
    반환값:
        - 성공 시: 조회된 카테고리 문서를 JSON 형식으로 반환 (HTTP 200)
        - 실패 시: 에러 메시지를 포함한 JSON 형식의 응답 (HTTP 404)
    """
    try:
        # ES 인덱스 이름과 매핑 파일 정보를 가져옵니다.
        index_name, _ = get_index_and_mapping("forum_category")

        # ES에서 주어진 category_id로 문서를 조회합니다.
        res = es.get(index=index_name, id=category_id)
        category = res["_source"]

        # ES가 생성한 _id 값을 별도의 'id' 필드에 할당합니다.
        category["id"] = res["_id"]

        # 조회된 카테고리 문서를 클라이언트에 반환합니다.
        return jsonify(category), 200
    except Exception as e:
        # 카테고리를 찾지 못한 경우 에러 메시지와 함께 404 응답을 반환합니다.
        return jsonify({"error": str(e)}), 404

# Forum 카테고리 생성 엔드포인트 (POST)
@app.route("/forum/category", methods=["POST"])
def create_forum_category():
    """
    포럼 카테고리 생성 (Forum)
    KR: 클라이언트로부터 받은 카테고리 데이터를 기반으로 ES의 'forum_category' 인덱스에 새로운 카테고리 문서를 생성합니다.
    """
    try:
        data = request.json  # 클라이언트로부터 JSON 데이터 수신
        if not data:
            return jsonify({"error": "데이터가 제공되지 않았습니다."}), 400

        # 변경: 'forum' -> 'forum_category'
        index_name, mapping_file = get_index_and_mapping("forum_category")
        if not es.indices.exists(index=index_name):
            create_index_if_not_exists(index_name, mapping_file)

        # ES에 데이터 삽입 (새 카테고리 생성)
        res = es.index(index=index_name, body=data)
        # 인덱스 새로고침: 생성된 문서가 즉시 검색될 수 있도록 보장합니다.
        es.indices.refresh(index=index_name)
        return jsonify({"message": "카테고리가 생성되었습니다.", "id": res["_id"]}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

# -----------------------------
# 새 Forum 카테고리 검색 엔드포인트
# -----------------------------
@app.route("/forum/category/search", methods=["GET"])
def search_forum_category():
    """
    포럼 카테고리 검색 엔드포인트
    KR: 주어진 제목(title)으로 'forum_category' 인덱스에서 해당 카테고리를 검색합니다.
        만약 검색 결과가 있으면 해당 문서를 반환하고, 없으면 404 에러를 반환합니다.
    """
    try:
        title = request.args.get("title", "")
        if not title:
            return jsonify({"error": "title 파라미터가 필요합니다."}), 400

        index_name, _ = get_index_and_mapping("forum_category")
        # Use a term query on the keyword sub-field for an exact match.
        body = {
            "query": {
                "match_phrase": {"title": title}
            }
        }
        res = es.search(index=index_name, body=body)
        hits = res.get("hits", {}).get("hits", [])
        if hits:
            # 첫 번째 매칭된 문서를 반환합니다.
            doc = hits[0]
            source = doc["_source"]
            source["id"] = doc["_id"]  # ES의 _id를 'id' 필드로 매핑
            return jsonify(source), 200
        else:
            return jsonify({"error": "Not Found"}), 404
    except Exception as e:
        return jsonify({"error": str(e)}), 500

# 조회수 증가 관련
@app.route("/forum/post/<doc_id>/increment-view", methods=["POST"])
def increment_view_count(doc_id):
    """
    게시글 조회수 증가 엔드포인트
    - 주어진 게시글(doc_id)의 조회수를 1 증가시키고,
      업데이트된 조회수를 반환합니다.
    """
    try:
        # "forum_post" 타입에 해당하는 인덱스 이름 가져오기
        index_name, _ = get_index_and_mapping("forum_post")

        # 게시글 문서 가져오기
        res = es.get(index=index_name, id=doc_id)
        data = res["_source"]

        # 조회수 증가 (기존 값이 없으면 0으로 초기화)
        current_views = data.get("viewsCount", 0)
        data["viewsCount"] = current_views + 1

        # 업데이트 시간 갱신
        data["updatedAt"] = datetime.now(timezone.utc).isoformat()

        # 수정된 문서를 재인덱싱
        es.index(index=index_name, id=doc_id, body=data)

        return jsonify({
            "message": "조회수가 증가되었습니다.",
            "viewsCount": data["viewsCount"]
        }), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

#

# (예시) /forum/searchByMember - 특정 유저가 작성한 게시글만 조회# 게시글/댓글 조회
# # @app.route("/forum/my", methods=["GET"])
# # def get_my_content():
# #     try:
# #         member_id = request.args.get("memberId")
# #         if not member_id:
# #             return jsonify({"error": "memberId 파라미터가 필요합니다."}), 400
# #
# #         # 페이지와 사이즈 기본값 설정 (0-based 페이지)
# #         post_page = int(request.args.get("postPage", 0))
# #         post_size = int(request.args.get("postSize", 10))
# #         comment_page = int(request.args.get("commentPage", 0))
# #         comment_size = int(request.args.get("commentSize", 10))
# #
# #         # 'forum_post' 인덱스와 매핑 파일 이름을 가져옵니다.
# #         index_name, _ = get_index_and_mapping("forum_post")
# #
# #         # 1. 해당 회원의 게시글 검색 (memberId 필드를 이용)
# #         post_query = {
# #             "query": {
# #                 "term": {"memberId": member_id}
# #             },
# #             "from": post_page * post_size,
# #             "size": post_size
# #         }
# #         post_res = es.search(index=index_name, body=post_query)
# #         posts = []
# #         for hit in post_res["hits"]["hits"]:
# #             post = hit["_source"]
# #             post["id"] = hit["_id"]
# #             posts.append(post)
# #
# #         # 2. 해당 회원의 댓글 검색
# #         # (댓글이 nested로 매핑되어 있다고 가정)
# #         comment_query = {
# #             "query": {
# #                 "nested": {
# #                     "path": "comments",
# #                     "query": {
# #                         "term": {"comments.memberId": member_id}
# #                     },
# #                     "inner_hits": {
# #                         "from": comment_page * comment_size,
# #                         "size": comment_size
# #                     }
# #                 }
# #             },
# #             "size": 0  # 외부 문서는 필요 없으므로 0으로 설정
# #         }
# #         comment_res = es.search(index=index_name, body=comment_query)
# #         comments = []
# #         for hit in comment_res["hits"]["hits"]:
# #             inner = hit.get("inner_hits", {}).get("comments", {})
# #             for comment_hit in inner.get("hits", {}).get("hits", []):
# #                 comment = comment_hit["_source"]
# #                 # (원하는 경우, 해당 댓글이 속한 게시글 id 등 추가 정보도 넣을 수 있습니다.)
# #                 comments.append(comment)
# #
# #         response = {
# #             "posts": posts,
# #             "comments": comments
# #         }
# #         return jsonify(response), 200
# #
# #     except Exception as e:
# #         app.logger.error("유저 콘텐츠 조회 중 오류: " + str(e))
# #         return jsonify({"error": str(e)}), 500

@app.route("/forum/searchByMember", methods=["GET"])
def search_posts_by_member():
    try:
        member_id = request.args.get("memberId")
        page = int(request.args.get("page", 1))  # 1-based
        size = int(request.args.get("size", 10))

        # 인덱스: forum_post
        index_name, _ = get_index_and_mapping("forum_post")
        from_offset = (page - 1) * size

        # memberId로 term 검색
        body = {
            "query": {
                "term": {
                    "memberId": member_id
                }
            },
            "from": from_offset,
            "size": size
        }

        res = es.search(index=index_name, body=body)
        hits = res["hits"]["hits"]
        posts = []
        for hit in hits:
            doc = hit["_source"]
            doc["id"] = hit["_id"]
            posts.append(doc)

        return jsonify(posts), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


# (예시) /forum/comments/searchByMember - 특정 유저가 작성한 댓글만 조회
@app.route("/forum/comments/searchByMember", methods=["GET"])
def search_comments_by_member():
    try:
        member_id = int(request.args.get("memberId"))  # 문자열을 int로 변환
        page = int(request.args.get("page", 1))
        size = int(request.args.get("size", 10))
        from_offset = (page - 1) * size

        index_name, _ = get_index_and_mapping("forum_post")

        # nested 쿼리: "comments.member.memberId" 로 변경
        body = {
            "query": {
                "nested": {
                    "path": "comments",
                    "query": {
                         "match": {"comments.memberId": member_id}
                    },
                    "inner_hits": {
                        "from": from_offset,
                        "size": size
                    }
                }
            },
            "size": 0
        }

        res = es.search(index=index_name, body=body)
        comments = []
        for hit in res["hits"]["hits"]:
            inner = hit.get("inner_hits", {}).get("comments", {})
            for comment_hit in inner.get("hits", {}).get("hits", []):
                c = comment_hit["_source"]
                comments.append(c)

        return jsonify(comments), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500



# 모델 학습 요청 API
@app.route("/model/train", methods=["POST"])
def train_machine_learning():
    try:
        index_type = request.args.get("type", "")
        if not index_type:
            return jsonify({"message": "type이 비어있습니다."})

        index_name, _ = get_index_and_mapping(index_type)

        # 데이터 가져오기
        df = fetch_data_from_es(index_name)

        train_tfidf_model(df, index_type)

        # 벡터화된 데이터 가져오기
        name_vec, ing_vec, major_vec, minor_vec, abv_sca = load_tfidf_models(index_type)  # 이미 학습된 모델 로드

        # weights.json 파일에서 가중치 로드
        weight_configs = load_weights_from_json(index_type)  # weights.json 파일에서 가중치 로드

        if not weight_configs:
            name_weight = 0.1
            ing_weight = 0.5
            major_weight = 0.4
            minor_weight = 0.2
            abv_weight = 0.1
        else:
            name_weight = weight_configs.get("weight_name", 0.1)
            ing_weight = weight_configs.get("weight_ingredients", 0.5)
            major_weight = weight_configs.get("weight_major", 0.4)
            minor_weight = weight_configs.get("weight_minor", 0.2)
            abv_weight = weight_configs.get("weight_abv", None)

        task = queue.enqueue(train_weight, index_type, df, name_vec, ing_vec, major_vec, minor_vec, abv_sca,
                             name_weight, ing_weight, major_weight, minor_weight, abv_weight, 10, 0.005)

        # 작업 ID를 반환하여 클라이언트가 작업 상태를 추적할 수 있도록 합니다.
        return jsonify({"message": "모델 학습이 시작되었습니다.", "task_id": task.get_id()}), 202

    except Exception as e:
        print(e)
        return jsonify({"message": index_type + " 모델 생성중 에러 : " + str(e)}), 500

# 작업 상태 조회 API
@app.route('/task/status/<task_id>', methods=["GET"])
def task_status(task_id):
    job = queue.fetch_job(task_id)

    if job is None:
        return jsonify({"status": "작업을 찾을 수 없습니다."}), 404

    # job 상태를 갱신
    job.refresh()
    print(f"Job Status: {job.get_status()}")  # 상태 로그 추가

    # 상태 설정
    if job.is_queued:
        return jsonify({"status": "대기중"})
    elif job.is_started:
        return jsonify({"status": "진행 중"})
    elif job.is_finished:
        return jsonify({"status": "완료"})
    elif job.is_failed:
        error_message = job.meta.get("exception") if job.is_failed else "알 수 없는 오류"
        return jsonify({"status": "실패", "message": error_message})

    return jsonify({"status": "알 수 없음"})



@app.route("/model/predict", methods=["POST"])
def predict_machine_learning():
    try:
        index_type = request.args.get("type", "")
        if not index_type:
            return jsonify({"message": "type이 비어있습니다. "})
        
        data = request.json
        if not data:
            return jsonify({"message": "데이터가 비었습니다."})

        index_name, _ = get_index_and_mapping(index_type)

        df = fetch_data_from_es(index_name)

        # TF-IDF 모델 학습
        train_tfidf_model(df, index_type)  # 학습용 tf-idf 모델을 학습하는 함수
        
        name_vec, ing_vec, major_vec, minor_vec, abv_sca = load_tfidf_models(index_type)

        # weights.json 파일에서 가중치 로드
        weight_configs = load_weights_from_json(index_type)
        if not weight_configs:
            name_weight = 0.1
            ing_weight = 0.5
            major_weight = 0.4
            minor_weight = 0.2
            abv_weight = 0.1
        else:
            name_weight = weight_configs.get("weight_name", 0.1)
            ing_weight = weight_configs.get("weight_ingredients", 0.5)
            major_weight = weight_configs.get("weight_major", 0.4)
            minor_weight = weight_configs.get("weight_minor", 0.2)
            abv_weight = weight_configs.get("weight_abv", None)  # 'weight_abv'가 없으면 0 처리

        recommendation = recommend_recipe(data, df, name_vec, ing_vec, major_vec, minor_vec, abv_sca, 3, name_weight, ing_weight, major_weight, minor_weight, abv_weight )
        
        return jsonify(recommendation), 200
    except Exception as e:
        return jsonify({"message": index_type + " 모델 사용중 에러 : " + str(e)}), 500


@app.route("/api/profile/recipes", methods=["GET"])
def get_user_recipes():
    try:
        member_id = request.args.get("memberId")
        if not member_id:
            return jsonify({"error": "memberId is required"}), 400

        try:
            member_id = int(member_id)  # 🔹 문자열을 정수로 변환
        except ValueError:
            return jsonify({"error": "Invalid memberId"}), 400

        page = int(request.args.get("page", 0))  # 기본값 0
        size = int(request.args.get("size", 10))  # 기본값 10
        from_offset = page * size

        # 인덱스명을 고정
        index_names = ["recipe_cocktail", "recipe_food"]

        # 쿼리: memberId(숫자)로 해당 유저가 작성한 레시피를 가져오기
        query = {
            "query": {
                "term": {"author": member_id}  # 🔹 정수 타입으로 검색
            },
            "from": from_offset,
            "size": size,
            "_source": ["name"]  # 🔹 'name'만 가져오기 (createdAt 제거)
        }

        try:
            response = es.search(index=index_names, body=query)
        except Exception as e:
            # Elasticsearch에서 인덱스를 찾을 수 없을 때 발생할 수 있는 오류를 처리
            if "index_not_found_exception" in str(e):
                app.logger.info(f"Index not found: {index_names}. Returning empty array.")
                return jsonify([]), 200  # 인덱스가 없으면 빈 배열 반환
            else:
                app.logger.error(f"Error fetching user recipes: {str(e)}")
                raise  # 다른 예외는 다시 던져서 처리

        hits = response.get("hits", {}).get("hits", [])

        if not hits:
            app.logger.info(f"No recipes found for member_id {member_id} in indexes: {index_names}. Returning empty array.")
            return jsonify([]), 200  # 빈 배열 반환

        # 🔹 결과에 content_type 추가 및 _id 추가
        results = []
        for hit in hits:
            doc = hit["_source"]
            doc["title"] = doc.pop("name", "")  # 'name'을 'title'로 변경
            doc["content_type"] = "cocktail" if hit["_index"] == "recipe_cocktail" else "food"
            doc["id"] = hit["_id"]  # 🔹 Elasticsearch의 _id 추가

            results.append(doc)

        return jsonify(results), 200
    except Exception as e:
        app.logger.error(f"Error in get_user_recipes: {str(e)}")
        return jsonify({"error": str(e)}), 500


@app.route("/update/one", methods=["POST"])
def update_one():
    try:
        data = request.json
        if not data:
            return jsonify({"error": "No data provided"}), 400

        file_type = data.get("type")
        if not file_type:
            return jsonify({"error": "Type is required"}), 400

        # id와 업데이트할 데이터 받기
        doc_id = request.form.get("updateId")
        if not doc_id:
            return jsonify({"error": "ID is required"}), 400

        index_name, mapping_file = get_index_and_mapping(file_type)

        # 인덱스가 없으면 매핑을 적용하여 생성
        if not es.indices.exists(index=index_name):
            create_index_if_not_exists(index_name, mapping_file)

        # id로 문서를 찾아서 업데이트
        es.update(index=index_name, id=doc_id, body={"doc": data})

        return jsonify({"message": "Data updated successfully"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

# if __name__ == "__main__":
#     app.run(debug=True)


if __name__ == '__main__':
    # 수동으로 Redis 연결 확인한 후 Flask 앱 실행
    app.run(host='0.0.0.0', port=5000)
