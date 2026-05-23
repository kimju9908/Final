package com.kh.back.controller.recipe;

import com.kh.back.constant.SearchType;
import com.kh.back.dto.recipe.res.FoodListResDto;
import com.kh.back.dto.recipe.res.FoodResDto;
import com.kh.back.service.recipe.FoodService;
import com.kh.back.service.search.SearchLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 음식 레시피 관련 API
 * Elasticsearch(Flask)에서 음식 레시피 데이터를 조회
 */
@RestController
@RequestMapping("/api/foodrecipes")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class FoodController {

    private final FoodService foodService;
    private final SearchLogService searchLogService;  // 검색 기록용

    /**
     * 음식 레시피 검색 API
     * 검색 실행 시 Redis에 검색 키워드 기록 → 인기 검색어 집계에 활용
     *
     * 예) GET /api/foodrecipes/search?q=김치&category=반찬&cookingMethod=&page=1&size=10
     */
    @GetMapping("/search")
    public ResponseEntity<List<FoodListResDto>> searchFoodRecipes(
            @RequestParam(name = "q", required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "") String category,
            @RequestParam(required = false, defaultValue = "") String cookingMethod,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        // 검색어가 있을 때만 기록 (빈 문자열 전체검색은 기록하지 않음)
        if (!q.isBlank()) {
            searchLogService.recordSearch(q, SearchType.FOOD);
        }

        List<FoodListResDto> result = foodService.searchFoodRecipes(q, category, cookingMethod, page, size);
        return ResponseEntity.ok(result);
    }

    /**
     * 음식 레시피 상세 조회
     * 예) GET /api/foodrecipes/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<FoodResDto> getFoodRecipeDetail(@PathVariable String id) {
        FoodResDto detail = foodService.getFoodRecipeDetail(id);
        return ResponseEntity.ok(detail);
    }
}
