
package com.kh.back.controller;

import com.kh.back.dto.recommend.RecommendedCocktailResDto;
import com.kh.back.dto.recommend.RecommendedFoodResDto;
import com.kh.back.service.RecommendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/recommend")
@RequiredArgsConstructor
public class RecommendController {
	private final RecommendService recommendService;

	@GetMapping("/food/likes")
	public ResponseEntity<List<RecommendedFoodResDto>> recommendFoodsByLikes(
			Authentication authentication
	) {
		return ResponseEntity.ok(recommendService.recommendFoodsByLikes(authentication));
	}

	@GetMapping("/cocktail/likes")
	public ResponseEntity<List<RecommendedCocktailResDto>> recommendCocktailsByLikes(
			Authentication authentication
	) {
		return ResponseEntity.ok(recommendService.recommendCocktailsByLikes(authentication));
	}
}
