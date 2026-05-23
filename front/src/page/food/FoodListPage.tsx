import React, { useEffect, useState, useRef, useCallback } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { fetchRecipeList } from "../../api/RecipeListApi";
import placeholder2 from "./style/placeholder2.png";
import { FoodListResDto } from "../../api/dto/FoodListResDto";
import RecipeApi from "../../api/RecipeApi";
import { RecommendedFoodDto } from "../../api/dto/RecipeDto";
import { AxiosError } from "axios";
import SearchSuggestion from "../../component/SearchSuggestion";

/**
 * 음식 레시피 목록 페이지 (작은 카드 버전)
 * - 칵테일 페이지와 동일한 스타일을 사용하고, 카드 사이즈를 줄임.
 * - 검색어(query)와 필터(카테고리 또는 조리방법)를 적용.
 * - Intersection Observer와 React ref를 사용하여 무한 스크롤 구현.
 * - 필터 버튼 클릭 시, 인자를 직접 넘겨 "두 번 클릭" 문제를 해결.
 *
 * @returns {JSX.Element} FoodListPage 컴포넌트
 */
const FoodListPage: React.FC = () => {
  // ------------------------------------
  // useParams를 이용하여 URL에서 동적 파라미터를 추출
  // 예를 들어, URL이 /foodrecipes/food 인 경우, type은 "food"가 됨
  // 이를 통해 동일 컴포넌트를 여러 타입에 맞게 재사용할 수 있습니다.
  // ------------------------------------
  const { type: routeType } = useParams<{ type: string }>();
  // 만약 URL에서 type 파라미터가 없다면, 기본값 "food" 사용
  const recipeType = routeType || "food";

  // -------------------- 상태 변수 --------------------
  const [foods, setFoods] = useState<FoodListResDto[]>([]);
  const [query, setQuery] = useState<string>("");
  const [submittedQuery, setSubmittedQuery] = useState<string>("");
  const [selectedFilterType, setSelectedFilterType] =
    useState<string>("카테고리");
  const [selectedCategory, setSelectedCategory] = useState<string>("");
  const [selectedCookingMethod, setSelectedCookingMethod] =
    useState<string>("");
  const [recommendedRecipes, setRecommendedRecipes] = useState<RecommendedFoodDto[]>([]);
  const [recommendationMessage, setRecommendationMessage] = useState<string>("AI 추천 레시피를 불러오는 중입니다.");

  // -------------------- 무한 스크롤 상태 --------------------
  const [page, setPage] = useState<number>(1);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const observerRef = useRef<IntersectionObserver | null>(null);
  const sentinelRef = useRef<HTMLDivElement | null>(null);

  // 페이지 이동을 위한 navigate hook
  const navigate = useNavigate();

  /**
   * 음식 레시피 목록을 API로부터 불러오는 함수
   *
   * @param {number} pageNumber - 불러올 페이지 번호
   * @param {string} [catParam] - 새로운 카테고리 값 (선택 사항)
   * @param {string} [methodParam] - 새로운 조리방법 값 (선택 사항)
   */
  const loadFoods = useCallback(
    async (pageNumber: number, catParam?: string, methodParam?: string) => {
      try {
        let finalCategory =
          catParam !== undefined ? catParam : selectedCategory;
        let finalMethod =
          methodParam !== undefined ? methodParam : selectedCookingMethod;

        // 필터 타입에 따라 필요 없는 필드를 빈 문자열로 설정
        if (selectedFilterType === "카테고리") {
          finalMethod = "";
        } else {
          finalCategory = "";
        }

        const response = await fetchRecipeList(
          submittedQuery,
          recipeType, // 동적 타입을 사용 (예: food, cocktail 등)
          finalCategory,
          finalMethod,
          pageNumber,
          20
        );
        console.log("loadFoods 응답:", response);

        if (pageNumber === 1) {
          setFoods(response);
        } else {
          setFoods((prev) => [...prev, ...response]);
        }

        setHasMore(response && response.length === 20);
      } catch (error) {
        console.error("음식 레시피 목록 조회 중 에러:", error);
      }
    },
    [
      submittedQuery,
      selectedCategory,
      selectedCookingMethod,
      selectedFilterType,
      recipeType,
    ]
  );

  /**
   * 검색 버튼 클릭 시 호출되는 함수
   * - 페이지를 초기화하고, 데이터를 다시 불러오며, Observer를 재설정함.
   */
  const handleSearch = useCallback(() => {
    setSubmittedQuery(query);
    setPage(1);
  }, [query]);

  /**
   * IntersectionObserver의 콜백 함수
   * - 감시 대상이 화면에 나타나면 다음 페이지를 로드함.
   *
   * @param {IntersectionObserverEntry[]} entries - 관찰 대상 항목 배열
   */
  const handleObserver = useCallback(
    (entries: IntersectionObserverEntry[]) => {
      const target = entries[0];
      if (target.isIntersecting && hasMore) {
        setPage((prev) => prev + 1);
      }
    },
    [hasMore]
  );

  /**
   * IntersectionObserver를 초기화 및 재설정하는 함수
   */
  const resetObserver = useCallback(() => {
    if (observerRef.current) {
      observerRef.current.disconnect();
    }
    const observer = new IntersectionObserver(handleObserver, {
      threshold: 0.1,
    });
    if (sentinelRef.current) {
      observer.observe(sentinelRef.current);
    }
    observerRef.current = observer;
  }, [handleObserver]);

  // 페이지 변경 시 추가 데이터 로드
  useEffect(() => {
    if (page > 1) {
      loadFoods(page);
    }
  }, [page, loadFoods]);

  // 컴포넌트 마운트 시 초기 설정
  useEffect(() => {
    resetObserver();
    loadFoods(1);
    return () => {
      if (observerRef.current) {
        observerRef.current.disconnect();
      }
    };
  }, [resetObserver, loadFoods]);

  useEffect(() => {
    const loadRecommendations = async () => {
      try {
        const response = await RecipeApi.fetchRecommendedFoodsByLikes();
        setRecommendedRecipes(response);
        setRecommendationMessage(
          response.length > 0
            ? ""
            : "좋아요한 음식 레시피가 쌓이면 취향 기반 AI 추천을 보여드릴게요."
        );
      } catch (error) {
        if (error instanceof AxiosError && error.response?.status === 401) {
          setRecommendationMessage("로그인 후 좋아요를 누르면 AI 추천 레시피를 받아볼 수 있어요.");
          return;
        }
        console.error("추천 레시피 조회 중 에러:", error);
        setRecommendationMessage("지금은 추천 레시피를 불러오지 못했어요.");
      }
    };

    loadRecommendations();
  }, []);

  /**
   * 레시피 상세 페이지로 이동하는 함수
   *
   * @param {string} id - 선택된 음식 레시피의 ID
   *
   * useParams를 사용하면 상세 페이지에서 URL 파라미터를 쉽게 추출할 수 있습니다.
   * 이로 인해 상세 페이지에서는 id와 type 등의 정보를 바로 사용할 수 있어
   * 라우트의 동적 처리가 쉬워지고, 코드의 가독성과 유지보수가 향상됩니다.
   */
  const handleSelectFood = (id: string) => {
    // 기존에는 /foodrecipes/{id}로 이동했으나,
    // 이제는 type을 포함하여 /foodrecipes/{type}/{id}로 이동합니다.
    navigate(`/foodrecipe/detail/${id}/food`);
  };

  // -------------------- 필터 옵션 데이터 --------------------
  const filterTypes = ["카테고리", "조리방법"];
  const categories = ["전체", "반찬", "국&찌개", "일품", "후식"];
  const cookingMethods = ["전체", "찌기", "끓이기", "굽기", "기타"];

  return (
    <div className="max-w-[1200px] mx-auto px-4 py-8">
      {/* 상단 영역 */}
      <header className="mb-8">
        <h1 className="text-2xl md:text-4xl font-bold mb-2 text-kakiBrown dark:text-softBeige">
          Food Recipes
        </h1>
        <p className="text-kakiBrown dark:text-softBeige">
          음식 레시피를 검색하고, 마음에 드는 레시피를 확인해보세요.
        </p>
      </header>

      {/* 검색 바 섹션 */}
      <section className="mb-12">
        <div className="flex flex-col md:flex-row max-w-md mx-auto space-y-2 md:space-y-0">
          <SearchSuggestion
            searchType="FOOD"
            query={query}
            onQueryChange={setQuery}
            onSearch={handleSearch}
            placeholder="Search food recipes..."
          />
          <button
            onClick={handleSearch}
            className="p-2 bg-warmOrange dark:bg-deepOrange text-white rounded md:rounded-l-none hover:bg-orange-600 dark:hover:bg-deepOrange/90"
          >
            Search
          </button>
        </div>
      </section>

      {/* 추천 레시피 섹션 */}
      <section className="mb-16">
        <h2 className="text-xl md:text-2xl font-bold mb-4 text-kakiBrown dark:text-softBeige">
          Recipes For You
        </h2>
        {recommendedRecipes.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {recommendedRecipes.map((item) => (
              <div
                key={item.id}
                className="border rounded overflow-hidden shadow border-kakiBrown dark:border-darkKaki cursor-pointer transition-transform hover:scale-105"
                onClick={() => handleSelectFood(item.id)}
              >
                <img
                  src={item.image || placeholder2}
                  alt={item.name}
                  className="w-full h-40 object-cover"
                />
                <div className="p-4">
                  <h3 className="text-lg font-semibold text-kakiBrown dark:text-softBeige">
                    {item.name}
                  </h3>
                  <p className="mt-2 text-sm text-kakiBrown dark:text-softBeige">
                    Category: {item.category || "기타"}
                  </p>
                  <p className="mt-1 text-sm text-kakiBrown dark:text-softBeige">
                    Likes: {item.like ?? 0}
                  </p>
                  <p className="mt-3 text-sm text-warmOrange dark:text-orange-300">
                    {item.reason}
                  </p>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="rounded-xl border border-dashed border-kakiBrown px-6 py-8 text-center text-kakiBrown dark:border-darkKaki dark:text-softBeige">
            {recommendationMessage}
          </div>
        )}
      </section>

      {/* 필터 옵션 섹션 */}
      <section className="mb-8 text-center">
        <h2 className="text-xl md:text-2xl font-bold mb-4 text-kakiBrown dark:text-softBeige">
          원하는 필터 유형을 선택하세요
        </h2>
        <div className="flex justify-center mb-4">
          <select
            value={selectedFilterType}
            onChange={(e) => {
              setSelectedFilterType(e.target.value);
              setSelectedCategory("");
              setSelectedCookingMethod("");
              setPage(1);
              loadFoods(1);
              resetObserver();
            }}
            className="p-2 border rounded border-kakiBrown dark:border-darkKaki"
          >
            {filterTypes.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-wrap justify-center gap-3">
          {selectedFilterType === "카테고리"
            ? categories.map((cat) => {
                const newCat = cat === "전체" ? "" : cat;
                return (
                  <button
                    key={cat}
                    onClick={() => {
                      setSelectedCategory(newCat);
                      setSelectedCookingMethod("");
                      setPage(1);
                      loadFoods(1, newCat, undefined);
                      resetObserver();
                    }}
                    className={`px-4 py-2 border rounded transition-colors ${
                      selectedCategory === cat ||
                      (cat === "전체" && selectedCategory === "")
                        ? "bg-warmOrange dark:bg-deepOrange text-white"
                        : "bg-white dark:bg-transparent text-kakiBrown dark:text-softBeige border-kakiBrown dark:border-darkKaki hover:bg-warmOrange dark:hover:bg-deepOrange"
                    }`}
                  >
                    {cat}
                  </button>
                );
              })
            : cookingMethods.map((method) => {
                const newMethod = method === "전체" ? "" : method;
                return (
                  <button
                    key={method}
                    onClick={() => {
                      setSelectedCookingMethod(newMethod);
                      setSelectedCategory("");
                      setPage(1);
                      loadFoods(1, undefined, newMethod);
                      resetObserver();
                    }}
                    className={`px-4 py-2 border rounded transition-colors ${
                      selectedCookingMethod === method ||
                      (method === "전체" && selectedCookingMethod === "")
                        ? "bg-warmOrange dark:bg-deepOrange text-white"
                        : "bg-white dark:bg-transparent text-kakiBrown dark:text-softBeige border-kakiBrown dark:border-darkKaki hover:bg-warmOrange dark:hover:bg-deepOrange"
                    }`}
                  >
                    {method}
                  </button>
                );
              })}
        </div>
      </section>

      {/* 레시피 목록 섹션 */}
      <section className="mb-16">
        <h2 className="text-xl md:text-2xl font-bold mb-4 text-kakiBrown dark:text-softBeige">
          Our Recipes
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          {foods.map((food) => (
            <div
              key={food.id}
              className="border border-kakiBrown dark:border-darkKaki rounded-lg overflow-hidden shadow hover:shadow-lg cursor-pointer transition-transform transform hover:scale-105"
              onClick={() => handleSelectFood(food.id)}
            >
              <img
                src={food.image || placeholder2}
                alt={food.name}
                className="w-full h-48 object-cover"
              />
              <div className="p-4">
                <h3 className="text-lg font-semibold text-kakiBrown dark:text-softBeige">
                  {food.name}
                </h3>
                <p className="text-kakiBrown dark:text-softBeige">
                  Category: {food.category}
                </p>
                <p className="text-kakiBrown dark:text-softBeige">
                  Likes: {food.like || 0}
                </p>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Sentinel 요소: Intersection Observer 감시 대상 */}
      {hasMore && <div ref={sentinelRef} style={{ height: "50px" }} />}
    </div>
  );
};

export default FoodListPage;
