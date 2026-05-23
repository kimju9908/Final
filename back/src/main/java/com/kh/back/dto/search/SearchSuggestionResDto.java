package com.kh.back.dto.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 검색 제안 응답 DTO
 *
 * 응답 예시 1 - 입력 없을 때 (인기 검색어):
 * {
 *   "type": "POPULAR",
 *   "items": ["김치찌개", "된장찌개", "계란말이"]
 * }
 *
 * 응답 예시 2 - 입력 시 (자동완성):
 * {
 *   "type": "AUTOCOMPLETE",
 *   "items": ["모히또", "모히또 무알콜", "모히또 라임"]
 * }
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchSuggestionResDto {

    /** "POPULAR" 또는 "AUTOCOMPLETE" */
    private String type;

    /** 검색어 목록 */
    private List<String> items;
}
