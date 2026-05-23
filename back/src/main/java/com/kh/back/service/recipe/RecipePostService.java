package com.kh.back.service.recipe;

import com.kh.back.entity.RecipePost;
import com.kh.back.entity.member.Member;
import com.kh.back.repository.RecipePostRepository;
import com.kh.back.repository.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 레시피 소유권 정보를 DB에서 관리하는 서비스.
 * - 사용자가 레시피를 업로드하면 ES에는 검색 필드만, DB(RecipePost)에는 작성자 정보를 저장.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipePostService {

    private final RecipePostRepository recipePostRepository;
    private final MemberRepository memberRepository;

    /**
     * 레시피 작성 시 DB에 소유권 저장
     *
     * @param memberId    작성자 ID
     * @param esDocId     Elasticsearch 문서 _id
     * @param contentType "food" 또는 "cocktail"
     * @param name        레시피 이름
     */
    @Transactional
    public RecipePost saveRecipePost(Long memberId, String esDocId, String contentType, String name) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보가 존재하지 않습니다. memberId=" + memberId));

        RecipePost post = RecipePost.builder()
                .member(member)
                .esDocId(esDocId)
                .contentType(contentType)
                .name(name)
                .build();

        RecipePost saved = recipePostRepository.save(post);
        log.info("[saveRecipePost] 저장 완료 - memberId={}, esDocId={}, type={}", memberId, esDocId, contentType);
        return saved;
    }

    /**
     * 레시피 수정 시 ES 문서 ID 변경 반영 (기존 레코드 업데이트)
     */
    @Transactional
    public void updateEsDocId(String oldEsDocId, String newEsDocId) {
        recipePostRepository.findByEsDocId(oldEsDocId).ifPresent(post -> {
            post.setEsDocId(newEsDocId);
            log.info("[updateEsDocId] {} → {}", oldEsDocId, newEsDocId);
        });
    }

    /**
     * 특정 회원의 레시피 목록 조회 (DB 기반)
     *
     * @param memberId 회원 ID
     * @param page     페이지 번호 (0-based)
     * @param size     페이지 크기
     * @return id(ES _id), title(name), content_type 포함한 목록
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUserRecipes(Long memberId, int page, int size) {
        Page<RecipePost> posts = recipePostRepository
                .findByMember_MemberIdOrderByCreatedAtDesc(memberId, PageRequest.of(page, size));

        return posts.getContent().stream()
                .map(p -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", p.getEsDocId());
                    map.put("title", p.getName());
                    map.put("content_type", p.getContentType());
                    return map;
                })
                .collect(Collectors.toList());
    }
}
