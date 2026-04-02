import React, { useEffect, useState, useRef, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { fetchRecipeList } from "../../api/RecipeListApi";
import RecipeApi from "../../api/RecipeApi";
import placeholder from "./style/placeholder.jpg";
import placeholder2 from "./style/placeholder2.png";
import { CocktailListResDto } from "../../api/dto/CotailListResDto";
import { CocktailResDto, RecommendedCocktailDto } from "../../api/dto/RecipeDto";
import { AxiosError } from "axios";

// 기존 CocktailListResDto에 image 필드가 없을 경우, CocktailResDto의 image 필드를 사용하도록 타입 병합
type CocktailForList = CocktailListResDto & { image: string };

const CocktailListPage: React.FC = () => {
  const [cocktails, setCocktails] = useState<CocktailForList[]>([]);
  const [query, setQuery] = useState<string>("");
  const [selectedCategory, setSelectedCategory] = useState<string>("");
  const [recommendedCocktails, setRecommendedCocktails] = useState<RecommendedCocktailDto[]>([]);
  const [recommendationMessage, setRecommendationMessage] = useState<string>("AI 추천 칵테일을 불러오는 중입니다.");

  const [page, setPage] = useState<number>(1);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const observerRef = useRef<IntersectionObserver | null>(null);
  const sentinelRef = useRef<HTMLDivElement | null>(null);
  const navigate = useNavigate();

  // 칵테일 목록을 불러오는 함수
  const loadCocktails = useCallback(
    async (pageNumber: number, catParam?: string) => {
      try {
        const categoryUsed =
          catParam !== undefined ? catParam : selectedCategory;
        const response = await fetchRecipeList(
          query,
          "cocktail",
          categoryUsed,
          "",
          pageNumber,
          20
        );
        console.log("loadCocktails 응답:", response);
        // response는 CocktailListResDto[] 타입으로 받아오지만,
        // image 필드는 RecipeApi의 상세 조회를 통해 채울 예정이므로, 우선 빈 문자열을 할당
        const listWithImage: CocktailForList[] = response.map((item) => ({
          ...item,
          image: item.image ? item.image : "", // image가 없으면 빈 문자열로 설정
        }));

        if (pageNumber === 1) {
          setCocktails(listWithImage);
        } else {
          setCocktails((prev) => [...prev, ...listWithImage]);
        }
        setHasMore(response && response.length === 20);
      } catch (error) {
        console.error("칵테일 목록 조회 중 에러:", error);
      }
    },
    [query, selectedCategory]
  );

  // 칵테일 목록 로드 후, image가 없는 항목들에 대해 RecipeApi.fetchCocktail으로 이미지 업데이트
  useEffect(() => {
    const updateImages = async () => {
      const updatedCocktails = await Promise.all(
        cocktails.map(async (cocktail) => {
          if (!cocktail.image) {
            try {
              const detail: CocktailResDto = await RecipeApi.fetchCocktail(
                cocktail.id,
                "cocktail"
              );
              return { ...cocktail, image: detail.image };
            } catch (err) {
              console.error(`이미지 업데이트 실패 (ID: ${cocktail.id})`, err);
              return { ...cocktail, image: placeholder2 };
            }
          }
          return cocktail;
        })
      );
      setCocktails(updatedCocktails);
    };

    if (cocktails.length > 0) {
      updateImages();
    }
    // 단, cocktails가 변경될 때마다 호출되지 않도록 주의 (한번만 업데이트하도록 별도 플래그를 사용할 수도 있음)
  }, [cocktails]);

  const fetchCocktailsData = useCallback(async () => {
    setPage(1);
    await loadCocktails(1, selectedCategory);
    resetObserver();
  }, [loadCocktails, selectedCategory]);

  const handleObserver = useCallback(
    (entries: IntersectionObserverEntry[]) => {
      const target = entries[0];
      if (target.isIntersecting && hasMore) {
        setPage((prev) => prev + 1);
      }
    },
    [hasMore]
  );

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

  useEffect(() => {
    if (page > 1) {
      loadCocktails(page, selectedCategory);
    }
  }, [page, loadCocktails, selectedCategory]);

  useEffect(() => {
    resetObserver();
    loadCocktails(1, selectedCategory);
    return () => {
      if (observerRef.current) {
        observerRef.current.disconnect();
      }
    };
  }, [resetObserver, loadCocktails, selectedCategory]);

  useEffect(() => {
    const loadRecommendations = async () => {
      try {
        const response = await RecipeApi.fetchRecommendedCocktailsByLikes();
        setRecommendedCocktails(response);
        setRecommendationMessage(
          response.length > 0
            ? ""
            : "좋아요한 칵테일이 쌓이면 취향 기반 AI 추천을 보여드릴게요."
        );
      } catch (error) {
        if (error instanceof AxiosError && error.response?.status === 401) {
          setRecommendationMessage("로그인 후 좋아요를 누르면 AI 추천 칵테일을 받아볼 수 있어요.");
          return;
        }
        console.error("추천 칵테일 조회 중 에러:", error);
        setRecommendationMessage("지금은 추천 칵테일을 불러오지 못했어요.");
      }
    };

    loadRecommendations();
  }, []);

  const handleSelectCocktail = (id: string) => {
    navigate(`/cocktailrecipe/detail/${id}/cocktail`);
  };

  const categories = [
    "전체",
    "식전 칵테일",
    "올 데이 칵테일",
    "롱드링크",
    "디저트 칵테일",
    "스파클링 칵테일",
    "식후 칵테일",
    "핫 드링크",
  ];

  return (
    <div className="max-w-[1200px] mx-auto px-4 py-8">
      <header className="mb-8">
        <h1 className="text-2xl md:text-4xl font-bold mb-2 text-kakiBrown dark:text-softBeige">
          Cocktail Recipes
        </h1>
        <p className="text-kakiBrown dark:text-softBeige">
          칵테일 레시피를 검색하고, 마음에 드는 레시피를 확인해보세요.
        </p>
      </header>

      <section className="mb-12">
        <div className="flex flex-col md:flex-row max-w-md mx-auto space-y-2 md:space-y-0">
          <input
            type="text"
            placeholder="Search cocktails..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className="flex-1 p-2 border border-kakiBrown dark:border-darkKaki rounded md:rounded-r-none focus:outline-none"
          />
          <button
            onClick={() => {
              setPage(1);
              loadCocktails(1, selectedCategory);
              resetObserver();
            }}
            className="p-2 bg-warmOrange dark:bg-deepOrange text-white rounded md:rounded-l-none hover:bg-orange-600 dark:hover:bg-deepOrange/90"
          >
            Search
          </button>
        </div>
      </section>

      <section className="mb-16">
        <h2 className="text-xl md:text-2xl font-bold mb-4 text-kakiBrown dark:text-softBeige">
            Cocktails For You
        </h2>
        {recommendedCocktails.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {recommendedCocktails.map((item) => (
              <div
                key={item.id}
                className="border rounded overflow-hidden shadow border-kakiBrown dark:border-darkKaki cursor-pointer transition-transform hover:scale-105"
                onClick={() => handleSelectCocktail(item.id)}
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
                    카테고리: {item.category || "기타"}
                  </p>
                  <p className="mt-1 text-sm text-kakiBrown dark:text-softBeige">
                    좋아요: {item.like ?? 0}
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

      <section className="mb-8 text-center">
        <h2 className="text-xl md:text-2xl font-bold mb-4 text-kakiBrown dark:text-softBeige">
          원하는 카테고리를 선택하세요
        </h2>
        <div className="flex flex-wrap justify-center gap-3">
          {categories.map((cat) => {
            const newCat = cat === "전체" ? "" : cat;
            return (
              <button
                key={cat}
                onClick={() => {
                  setSelectedCategory(newCat);
                  setPage(1);
                  loadCocktails(1, newCat);
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
          })}
        </div>
      </section>

      <section className="mb-16">
        <h2 className="text-xl md:text-2xl font-bold mb-4 text-kakiBrown dark:text-softBeige">
          Our Recipes
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          {cocktails.map((cocktail) => (
            <div
              key={cocktail.id}
              className="border border-kakiBrown dark:border-darkKaki rounded-lg overflow-hidden shadow hover:shadow-lg cursor-pointer transition-transform transform hover:scale-105"
              onClick={() => handleSelectCocktail(cocktail.id)}
            >
              <img
                src={cocktail.image || placeholder2}
                alt={cocktail.name}
                className="w-full h-48 object-cover"
              />
              <div className="p-4">
                <h3 className="text-lg font-semibold text-kakiBrown dark:text-softBeige">
                  {cocktail.name}
                </h3>
                <p className="text-kakiBrown dark:text-softBeige">
                  Category: {cocktail.category}
                </p>
                <p className="text-kakiBrown dark:text-softBeige">
                  Likes: {cocktail.like || 0}
                </p>
              </div>
            </div>
          ))}
        </div>
      </section>

      {hasMore && <div ref={sentinelRef} style={{ height: "50px" }} />}
    </div>
  );
};

export default CocktailListPage;
