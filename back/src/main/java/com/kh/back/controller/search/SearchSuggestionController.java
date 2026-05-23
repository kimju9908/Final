package com.kh.back.controller.search;

import com.kh.back.constant.SearchType;
import com.kh.back.dto.search.SearchSuggestionResDto;
import com.kh.back.service.search.SearchSuggestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 검색 제안 API 컨트롤러
 *
 * ── 인기 검색어 (입력 없을 때) ───────────────────────────────────────
 * GET /api/search/suggestions?searchType=FOOD
 * GET /api/search/suggestions?searchType=COCKTAIL
 * → 오늘 날짜 기준 해당 타입의 인기 검색어 top 10 반환
 *
 * ── 자동완성 (입력 시작 후) ──────────────────────────────────────────
 * GET /api/search/suggestions?searchType=FOOD&keyword=김치
 * GET /api/search/suggestions?searchType=COCKTAIL&keyword=moj
 * → prefix 기반 자동완성 결과 반환 (해당 타입 ES 인덱스만 조회)
 *
 * 응답 형식:
 * { "type": "POPULAR",      "items": ["김치찌개", "된장찌개", ...] }
 * { "type": "AUTOCOMPLETE", "items": ["모히또", "모히또 라임", ...] }
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:3000")
public class SearchSuggestionController {

    private final SearchSuggestionService searchSuggestionService;

    /**
     * 검색 제안 조회
     *
     * @param searchType FOOD 또는 COCKTAIL (필수)
     * @param keyword    검색어 (없으면 인기 검색어, 있으면 자동완성)
     */
    @GetMapping("/suggestions")
    public ResponseEntity<SearchSuggestionResDto> getSuggestions(
            @RequestParam SearchType searchType,
            @RequestParam(defaultValue = "") String keyword
    ) {
        log.debug("[SearchSuggestionController] searchType={}, keyword={}", searchType, keyword);
        SearchSuggestionResDto result = searchSuggestionService.getSuggestions(keyword, searchType);
        return ResponseEntity.ok(result);
    }
}
