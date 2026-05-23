package com.kh.back.controller.recipe;

import com.kh.back.constant.SearchType;
import com.kh.back.dto.recipe.res.CocktailListResDto;
import com.kh.back.dto.recipe.res.CocktailResDto;
import com.kh.back.service.recipe.CocktailService;
import com.kh.back.service.search.SearchLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 칵테일 관련 API
 * Elasticsearch(Flask)에서 데이터 조회
 */
@RestController
@RequestMapping("/api/cocktails")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class CocktailController {

    private final CocktailService cocktailService;
    private final SearchLogService searchLogService;  // 검색 기록용

    /**
     * 칵테일 검색
     * 검색 실행 시 Redis에 검색 키워드 기록 → 인기 검색어 집계에 활용
     *
     * 예) GET /api/cocktails/search?q=모히또&category=롱드링크&page=1&size=10
     */
    @GetMapping("/search")
    public ResponseEntity<List<CocktailListResDto>> searchCocktails(
            @RequestParam(name = "q", required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "") String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        // 검색어가 있을 때만 기록
        if (!q.isBlank()) {
            searchLogService.recordSearch(q, SearchType.COCKTAIL);
        }

        List<CocktailListResDto> result = cocktailService.searchCocktails(q, category, page, size);
        return ResponseEntity.ok(result);
    }

    /**
     * 칵테일 상세 조회
     * 예) GET /api/cocktails/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<CocktailResDto> getCocktailDetail(@PathVariable String id) {
        CocktailResDto detail = cocktailService.getCocktailDetail(id);
        return ResponseEntity.ok(detail);
    }
}
