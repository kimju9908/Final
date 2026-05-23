package com.kh.back.service.search;

import com.kh.back.constant.SearchType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 검색 키워드 기록 및 인기 검색어 조회 서비스 (Redis Sorted Set 기반)
 *
 * Redis Key 구조:
 *   search:popular:{SEARCH_TYPE}:{YYYY-MM-DD}
 *   예) search:popular:FOOD:2025-04-13
 *       search:popular:COCKTAIL:2025-04-13
 *
 * Redis Value 구조:
 *   Sorted Set - member: keyword, score: 검색 횟수
 *   → ZINCRBY 로 횟수 증가, ZREVRANGE 로 top N 조회
 *
 * TTL: 7일 (오래된 데이터 자동 삭제)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchLogService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final int POPULAR_TOP_N = 10;
    private static final long TTL_DAYS = 7;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 검색 키워드 기록
     * - 검색 실행 시 호출 (빈 문자열은 무시)
     *
     * @param keyword    검색어
     * @param searchType FOOD 또는 COCKTAIL
     */
    public void recordSearch(String keyword, SearchType searchType) {
        if (keyword == null || keyword.isBlank() || searchType == null) {
            return;
        }
        String key = buildKey(searchType);
        try {
            redisTemplate.opsForZSet().incrementScore(key, keyword.trim(), 1);
            // TTL 갱신 (매 기록마다 갱신해도 성능 영향 미미)
            redisTemplate.expire(key, TTL_DAYS, TimeUnit.DAYS);
            log.debug("[SearchLogService] 기록 완료 - keyword={}, type={}", keyword, searchType);
        } catch (Exception e) {
            // 검색 기록 실패가 메인 검색 기능에 영향 주지 않도록 warn 처리
            log.warn("[SearchLogService] 검색 기록 실패 - keyword={}, type={}: {}", keyword, searchType, e.getMessage());
        }
    }

    /**
     * 오늘의 인기 검색어 top N 조회
     * - 오늘 날짜(KST) 기준 검색량 내림차순
     * - 데이터 없으면 빈 리스트 반환 (null 없음)
     *
     * @param searchType FOOD 또는 COCKTAIL
     * @return 인기 검색어 목록 (최대 10개)
     */
    public List<String> getPopularKeywords(SearchType searchType) {
        if (searchType == null) {
            return Collections.emptyList();
        }
        String key = buildKey(searchType);
        try {
            Set<Object> topKeywords = redisTemplate.opsForZSet()
                    .reverseRange(key, 0, POPULAR_TOP_N - 1);
            if (topKeywords == null || topKeywords.isEmpty()) {
                return Collections.emptyList();
            }
            List<String> result = new ArrayList<>(topKeywords.size());
            for (Object kw : topKeywords) {
                if (kw != null) {
                    result.add(String.valueOf(kw));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[SearchLogService] 인기 검색어 조회 실패 - type={}: {}", searchType, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Redis 키 생성
     * 형식: search:popular:{TYPE}:{YYYY-MM-DD}
     */
    private String buildKey(SearchType searchType) {
        String today = LocalDate.now(KST).toString(); // "2025-04-13"
        return "search:popular:" + searchType.name() + ":" + today;
    }
}
