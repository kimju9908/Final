
package com.kh.back.service;


import com.kh.back.constant.ReactionType;
import com.kh.back.dto.recommend.RecommendedCocktailResDto;
import com.kh.back.dto.recommend.RecommendedFoodResDto;
import com.kh.back.dto.recipe.res.CocktailListResDto;
import com.kh.back.dto.recipe.res.CocktailResDto;
import com.kh.back.dto.recipe.res.FoodListResDto;
import com.kh.back.dto.recipe.res.FoodResDto;
import com.kh.back.entity.Reaction;
import com.kh.back.entity.member.Member;
import com.kh.back.repository.ReactionRepository;
import com.kh.back.service.member.MemberService;
import com.kh.back.service.python.ElasticService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class RecommendService {
	private final ElasticService elasticService;
	private final MemberService memberService;
	private final ReactionRepository reactionRepository;

	public List<RecommendedCocktailResDto> recommendCocktailsByLikes(Authentication authentication) {
		Member member = Optional.ofNullable(memberService.convertAuthToEntity(authentication))
				.orElseThrow(() -> new AccessDeniedException("추천 기능은 로그인 후 이용할 수 있습니다."));

		List<Reaction> likedReactions = reactionRepository.findByMemberAndReactionTypeAndContentType(member, ReactionType.LIKE, "cocktail");
		log.info("[recommendCocktailsByLikes] memberId={}, likedReactionCount={}", member.getMemberId(), likedReactions.size());
		if (likedReactions.isEmpty()) {
			return List.of();
		}

		Set<String> likedRecipeIds = likedReactions.stream()
				.map(Reaction::getPostId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());

		List<CocktailResDto> likedCocktails = likedRecipeIds.stream()
				.map(postId -> elasticService.detail(postId, "cocktail"))
				.filter(CocktailResDto.class::isInstance)
				.map(CocktailResDto.class::cast)
				.collect(Collectors.toList());
		log.info("[recommendCocktailsByLikes] memberId={}, likedCocktailDetailCount={}", member.getMemberId(), likedCocktails.size());

		if (likedCocktails.isEmpty()) {
			return List.of();
		}
		Map<String, Long> categoryWeights = likedCocktails.stream()
				.map(CocktailResDto::getCategory)
				.filter(category -> category != null && !category.isBlank())
				.collect(Collectors.groupingBy(category -> category, Collectors.counting()));
		Map<String, Long> ingredientWeights = likedCocktails.stream()
				.flatMap(cocktail -> cocktail.getIngredients().stream())
				.map(ingredient -> ingredient.getIngredient())
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(name -> !name.isBlank())
				.map(String::toLowerCase)
				.collect(Collectors.groupingBy(name -> name, Collectors.counting()));
		Map<Long, Long> authorWeights = likedCocktails.stream()
				.map(CocktailResDto::getAuthor)
				.filter(author -> author > 0)
				.collect(Collectors.groupingBy(author -> author, Collectors.counting()));
		Map<String, RecommendedCocktailResDto> candidates = new LinkedHashMap<>();
		categoryWeights.keySet().forEach(category ->
				Optional.ofNullable(elasticService.search("", "cocktail", category, "", 1, 40))
						.orElse(List.of())
						.stream()
						.filter(CocktailListResDto.class::isInstance)
						.map(CocktailListResDto.class::cast)
						.forEach(recipe -> mergeCocktailCandidate(candidates, recipe, categoryWeights, ingredientWeights, authorWeights, likedRecipeIds))
		);
		authorWeights.keySet().forEach(authorId ->
				elasticService.getUserRecipes(authorId, 1, 24).stream()
						.map(recipe -> recipe.get("id"))
						.filter(String.class::isInstance)
						.map(String.class::cast)
						.filter(recipeId -> !likedRecipeIds.contains(recipeId))
						.map(recipeId -> elasticService.detail(recipeId, "cocktail"))
						.filter(CocktailResDto.class::isInstance)
						.map(CocktailResDto.class::cast)
						.forEach(recipe -> mergeCocktailCandidate(candidates, recipe, categoryWeights, ingredientWeights, authorWeights, likedRecipeIds))
		);
		Optional.ofNullable(elasticService.search("", "cocktail", "", "", 1, 80))
				.orElse(List.of())
				.stream()
				.filter(CocktailListResDto.class::isInstance)
				.map(CocktailListResDto.class::cast)
				.forEach(recipe -> mergeCocktailCandidate(candidates, recipe, categoryWeights, ingredientWeights, authorWeights, likedRecipeIds));

		log.info("[recommendCocktailsByLikes] memberId={}, categoryWeights={}, ingredientWeights={}, authorWeights={}, candidateCount={}",
				member.getMemberId(), categoryWeights, ingredientWeights, authorWeights, candidates.size());

		return candidates.values().stream()
				.sorted(Comparator.comparingDouble(RecommendedCocktailResDto::getRecommendationScore).reversed()
						.thenComparing(Comparator.comparingLong(RecommendedCocktailResDto::getLike).reversed())
						.thenComparing(RecommendedCocktailResDto::getName, Comparator.nullsLast(String::compareTo)))
				.limit(6)
				.collect(Collectors.toList());
	}

	public List<RecommendedFoodResDto> recommendFoodsByLikes(Authentication authentication) {
		Member member = Optional.ofNullable(memberService.convertAuthToEntity(authentication))
				.orElseThrow(() -> new AccessDeniedException("추천 기능은 로그인 후 이용할 수 있습니다."));

		List<Reaction> likedReactions = reactionRepository.findByMemberAndReactionTypeAndContentType(member, ReactionType.LIKE, "food");
		log.info("[recommendFoodsByLikes] memberId={}, likedReactionCount={}", member.getMemberId(), likedReactions.size());
		if (likedReactions.isEmpty()) {
			return List.of();
		}
		Set<String> likedRecipeIds = likedReactions.stream()
				.map(Reaction::getPostId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());

		List<FoodResDto> likedRecipes = likedRecipeIds.stream()
				.map(postId -> elasticService.detail(postId, "food"))
				.filter(FoodResDto.class::isInstance)
				.map(FoodResDto.class::cast)
				.collect(Collectors.toList());
		log.info("[recommendFoodsByLikes] memberId={}, likedRecipeDetailCount={}", member.getMemberId(), likedRecipes.size());

		if (likedRecipes.isEmpty()) {
			return List.of();
		}
		Map<String, Long> categoryWeights = likedRecipes.stream()
				.map(FoodResDto::getCategory)
				.filter(category -> category != null && !category.isBlank())
				.collect(Collectors.groupingBy(category -> category, Collectors.counting()));

		Map<Integer, Long> authorWeights = likedRecipes.stream()
				.map(FoodResDto::getAuthor)
				.filter(author -> author > 0)
				.collect(Collectors.groupingBy(author -> author, Collectors.counting()));

		Map<String, RecommendedFoodResDto> candidates = new LinkedHashMap<>();

		categoryWeights.keySet().forEach(category ->
				Optional.ofNullable(elasticService.search("", "food", category, "", 1, 24))
						.orElse(List.of())
						.stream()
						.filter(FoodListResDto.class::isInstance)
						.map(FoodListResDto.class::cast)
						.forEach(recipe -> mergeCandidate(candidates, recipe, categoryWeights, authorWeights, likedRecipeIds))
		);
		authorWeights.keySet().forEach(authorId ->
				elasticService.getUserRecipes((long) authorId, 1, 24).stream()
						.map(recipe -> recipe.get("id"))
						.filter(String.class::isInstance)
						.map(String.class::cast)
						.filter(recipeId -> !likedRecipeIds.contains(recipeId))
						.map(recipeId -> elasticService.detail(recipeId, "food"))
						.filter(FoodResDto.class::isInstance)
						.map(FoodResDto.class::cast)
						.forEach(recipe -> mergeCandidate(candidates, recipe, categoryWeights, authorWeights, likedRecipeIds))
		);

		if (candidates.isEmpty()) {
			log.info("[recommendFoodsByLikes] memberId={}, no merged candidates found. Fallback by category search.", member.getMemberId());
			addFallbackCategoryCandidates(candidates, categoryWeights, likedRecipeIds);
		}

		log.info("[recommendFoodsByLikes] memberId={}, categoryWeights={}, authorWeights={}, candidateCount={}",
				member.getMemberId(), categoryWeights, authorWeights, candidates.size());

		return candidates.values().stream()
				.sorted(Comparator.comparingDouble(RecommendedFoodResDto::getRecommendationScore).reversed()
						.thenComparing(Comparator.comparingInt(RecommendedFoodResDto::getLike).reversed())
						.thenComparing(RecommendedFoodResDto::getName, Comparator.nullsLast(String::compareTo)))
				.limit(6)
				.collect(Collectors.toList());
	}

	private void mergeCandidate(
			Map<String, RecommendedFoodResDto> candidates,
			FoodListResDto recipe,
			Map<String, Long> categoryWeights,
			Map<Integer, Long> authorWeights,
			Set<String> likedRecipeIds
	) {
		if (recipe == null || recipe.getId() == null || likedRecipeIds.contains(recipe.getId())) {
			return;
		}

		FoodResDto detail = Optional.ofNullable((FoodResDto) elasticService.detail(recipe.getId(), "food")).orElse(null);
		if (detail == null || likedRecipeIds.contains(detail.getId())) {
			return;
		}

		mergeCandidate(candidates, detail, categoryWeights, authorWeights, likedRecipeIds);
	}

	private void mergeCandidate(
			Map<String, RecommendedFoodResDto> candidates,
			FoodResDto recipe,
			Map<String, Long> categoryWeights,
			Map<Integer, Long> authorWeights,
			Set<String> likedRecipeIds
	) {
		if (recipe == null || recipe.getId() == null || likedRecipeIds.contains(recipe.getId())) {
			return;
		}

		long matchedCategoryCount = categoryWeights.getOrDefault(recipe.getCategory(), 0L);
		long matchedAuthorCount = authorWeights.getOrDefault(recipe.getAuthor(), 0L);
		if (matchedCategoryCount == 0 && matchedAuthorCount == 0) {
			return;
		}

		double score = (matchedCategoryCount * 3.0) + (matchedAuthorCount * 4.0) + (recipe.getLike() * 0.05);
		String reason = buildReason(recipe.getCategory(), matchedCategoryCount, matchedAuthorCount);

		RecommendedFoodResDto newCandidate = RecommendedFoodResDto.builder()
				.id(recipe.getId())
				.name(recipe.getName())
				.image(recipe.getImage())
				.category(recipe.getCategory())
				.like(recipe.getLike())
				.author(recipe.getAuthor())
				.reason(reason)
				.recommendationScore(score)
				.build();

		candidates.merge(
				recipe.getId(),
				newCandidate,
				(existing, incoming) -> existing.getRecommendationScore() >= incoming.getRecommendationScore() ? existing : incoming
		);
	}

	private void addFallbackCategoryCandidates(
			Map<String, RecommendedFoodResDto> candidates,
			Map<String, Long> categoryWeights,
			Set<String> likedRecipeIds
	) {
		categoryWeights.entrySet().stream()
				.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
				.forEach(entry -> Optional.ofNullable(elasticService.search("", "food", entry.getKey(), "", 1, 60))
						.orElse(List.of())
						.stream()
						.filter(FoodListResDto.class::isInstance)
						.map(FoodListResDto.class::cast)
						.filter(recipe -> recipe.getId() != null && !likedRecipeIds.contains(recipe.getId()))
						.forEach(recipe -> candidates.putIfAbsent(
								recipe.getId(),
								RecommendedFoodResDto.builder()
										.id(recipe.getId())
										.name(recipe.getName())
										.image(recipe.getImage())
										.category(recipe.getCategory())
										.like(recipe.getLike() == null ? 0 : recipe.getLike().intValue())
										.author(0)
										.reason("좋아요한 " + entry.getKey() + " 스타일과 비슷해요")
										.recommendationScore((entry.getValue() * 3.0) + ((recipe.getLike() == null ? 0 : recipe.getLike()) * 0.05))
										.build()
						)));
	}

	private String buildReason(String category, long matchedCategoryCount, long matchedAuthorCount) {
		List<String> reasons = new java.util.ArrayList<>();
		if (matchedCategoryCount > 0 && category != null && !category.isBlank()) {
			reasons.add("좋아요한 " + category + " 스타일과 비슷해요");
		}
		if (matchedAuthorCount > 0) {
			reasons.add("자주 좋아요한 작성자의 레시피예요");
		}
		return String.join(" · ", reasons);
	}

	private void mergeCocktailCandidate(
			Map<String, RecommendedCocktailResDto> candidates,
			CocktailListResDto recipe,
			Map<String, Long> categoryWeights,
			Map<String, Long> ingredientWeights,
			Map<Long, Long> authorWeights,
			Set<String> likedRecipeIds
	) {
		if (recipe == null || recipe.getId() == null || likedRecipeIds.contains(recipe.getId())) {
			return;
		}

		CocktailResDto detail = Optional.ofNullable((CocktailResDto) elasticService.detail(recipe.getId(), "cocktail")).orElse(null);
		if (detail == null || likedRecipeIds.contains(detail.getId())) {
			return;
		}

		mergeCocktailCandidate(candidates, detail, categoryWeights, ingredientWeights, authorWeights, likedRecipeIds);
	}

	private void mergeCocktailCandidate(
			Map<String, RecommendedCocktailResDto> candidates,
			CocktailResDto recipe,
			Map<String, Long> categoryWeights,
			Map<String, Long> ingredientWeights,
			Map<Long, Long> authorWeights,
			Set<String> likedRecipeIds
	) {
		if (recipe == null || recipe.getId() == null || likedRecipeIds.contains(recipe.getId())) {
			return;
		}

		long matchedCategoryCount = categoryWeights.getOrDefault(recipe.getCategory(), 0L);
		long matchedAuthorCount = authorWeights.getOrDefault(recipe.getAuthor(), 0L);
		long matchedIngredientScore = Optional.ofNullable(recipe.getIngredients())
				.orElse(List.of())
				.stream()
				.map(ingredient -> ingredient.getIngredient())
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(name -> !name.isBlank())
				.map(String::toLowerCase)
				.mapToLong(name -> ingredientWeights.getOrDefault(name, 0L))
				.sum();

		if (matchedCategoryCount == 0 && matchedAuthorCount == 0 && matchedIngredientScore == 0) {
			return;
		}

		double score = (matchedCategoryCount * 3.0) + (matchedIngredientScore * 2.5) + (matchedAuthorCount * 4.0) + (recipe.getLike() * 0.05);
		String reason = buildCocktailReason(recipe.getCategory(), matchedCategoryCount, matchedIngredientScore, matchedAuthorCount);

		RecommendedCocktailResDto newCandidate = RecommendedCocktailResDto.builder()
				.id(recipe.getId())
				.name(recipe.getName())
				.image(recipe.getImage())
				.category(recipe.getCategory())
				.like(recipe.getLike())
				.author(recipe.getAuthor())
				.reason(reason)
				.recommendationScore(score)
				.build();

		candidates.merge(
				recipe.getId(),
				newCandidate,
				(existing, incoming) -> existing.getRecommendationScore() >= incoming.getRecommendationScore() ? existing : incoming
		);
	}

	private String buildCocktailReason(String category, long matchedCategoryCount, long matchedIngredientScore, long matchedAuthorCount) {
		List<String> reasons = new java.util.ArrayList<>();
		if (matchedCategoryCount > 0 && category != null && !category.isBlank()) {
			reasons.add("좋아요한 " + category + " 카테고리와 비슷해요");
		}
		if (matchedIngredientScore > 0) {
			reasons.add("좋아하는 재료 조합이 겹쳐요");
		}
		if (matchedAuthorCount > 0) {
			reasons.add("자주 좋아요한 작성자의 칵테일이에요");
		}
		return String.join(" · ", reasons);
	}
}
