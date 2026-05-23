package com.kh.back.service.recipe;

import com.kh.back.dto.python.SearchListResDto;
import com.kh.back.dto.python.SearchResDto;
import com.kh.back.dto.recipe.res.CocktailListResDto;
import com.kh.back.dto.recipe.res.CocktailResDto;
import com.kh.back.dto.recipe.res.FoodListResDto;
import com.kh.back.dto.recipe.res.FoodResDto;
import com.kh.back.service.python.ElasticService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 통합 레시피 서비스
 * - 음식과 칵테일 레시피 검색 및 상세 조회를 처리합니다.
 */
@Service
@RequiredArgsConstructor
public class RecipeService {

    private final ElasticService elasticService;
    private final RecipePostService recipePostService;

    /**
     * 레시피 검색
     *
     * @param q             검색어
     * @param type          "food" 또는 "cocktail"
     * @param category      카테고리 필터
     * @param cookingMethod 조리방법 필터 (음식에만 사용)
     * @param page          페이지 번호
     * @param size          페이지 당 항목 수
     * @return 해당 타입에 맞는 레시피 리스트
     */
    public List<?> searchRecipes(String q, String type, String category, String cookingMethod, int page, int size) {
        // ElasticService의 search 메서드에 모든 파라미터 전달
        List<SearchListResDto> rawList = elasticService.search(q, type, category, cookingMethod, page, size);
        if (rawList == null) {
            return Collections.emptyList();
        }
        if ("food".equalsIgnoreCase(type)) {
            return rawList.stream()
                    .map(item -> (FoodListResDto) item)
                    .collect(Collectors.toList());
        } else if ("cocktail".equalsIgnoreCase(type)) {
            return rawList.stream()
                    .map(item -> (CocktailListResDto) item)
                    .collect(Collectors.toList());
        } else {
            return rawList;
        }
    }

    /**
     * 레시피 상세 조회
     *
     * @param id   레시피 문서의 ID
     * @param type "food" 또는 "cocktail"
     * @return 상세 레시피 데이터
     */
    public Object getRecipeDetail(String id, String type) {
        SearchResDto res = elasticService.detail(id, type);
        if ("food".equalsIgnoreCase(type)) {
            return (FoodResDto) res;
        } else if ("cocktail".equalsIgnoreCase(type)) {
            return (CocktailResDto) res;
        } else {
            return res;
        }
    }

    /**
     * 특정 유저가 작성한 레시피 목록 조회
     *
     * @param memberId 유저 ID
     * @param page     페이지 번호
     * @param size     페이지 당 항목 수
     * @return 레시피 목록 (id, title, content_type)
     */
    /**
     * 특정 유저가 작성한 레시피 목록 조회 (DB 기반)
     * - author 정보는 ES가 아닌 DB(RecipePost)에서 관리
     */
    public List<Map<String, Object>> getUserRecipes(Long memberId, int page, int size) {
        return recipePostService.getUserRecipes(memberId, page, size);
    }
}
