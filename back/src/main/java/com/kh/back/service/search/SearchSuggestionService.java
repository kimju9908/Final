package com.kh.back.service.search;

import com.kh.back.constant.SearchType;
import com.kh.back.dto.search.SearchSuggestionResDto;
import com.kh.back.service.python.ElasticService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 검색 제안 서비스
 *
 * 동작 흐름:
 * 1. keyword 없음 → 오늘의 인기 검색어 (Redis Sorted Set)
 * 2. keyword 있음 → prefix 기반 자동완성 (Elasticsearch)
 *
 * searchType 에 따라 서로 다른 ES 인덱스에서 조회:
 * - FOOD     → recipe_food 인덱스
 * - COCKTAIL → recipe_cocktail 인덱스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchSuggestionService {

    private final SearchLogService searchLogService;
    private final ElasticService elasticService;

    /**
     * 검색 제안 반환
     *
     * @param keyword    검색어 (null 또는 빈 문자열이면 인기 검색어 반환)
     * @param searchType FOOD 또는 COCKTAIL
     * @return SearchSuggestionResDto
     */
    public SearchSuggestionResDto getSuggestions(String keyword, SearchType searchType) {
        if (searchType == null) {
            return SearchSuggestionResDto.builder()
                    .type("POPULAR")
                    .items(Collections.emptyList())
                    .build();
        }

        // 입력값 없음 → 인기 검색어
        if (keyword == null || keyword.isBlank()) {
            List<String> popular = searchLogService.getPopularKeywords(searchType);
            log.debug("[SearchSuggestionService] 인기 검색어 반환 - type={}, count={}", searchType, popular.size());
            return SearchSuggestionResDto.builder()
                    .type("POPULAR")
                    .items(popular)
                    .build();
        }

        // 입력값 있음 → 자동완성
        String esType = searchType == SearchType.FOOD ? "food" : "cocktail";
        List<String> autocomplete = elasticService.getAutocompleteSuggestions(keyword.trim(), esType);
        log.debug("[SearchSuggestionService] 자동완성 반환 - keyword={}, type={}, count={}", keyword, searchType, autocomplete.size());
        return SearchSuggestionResDto.builder()
                .type("AUTOCOMPLETE")
                .items(autocomplete)
                .build();
    }
}
