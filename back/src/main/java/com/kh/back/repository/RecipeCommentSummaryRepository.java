package com.kh.back.repository;

import com.kh.back.entity.RecipeCommentSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecipeCommentSummaryRepository extends JpaRepository<RecipeCommentSummary, Long> {
    Optional<RecipeCommentSummary> findByRecipeId(String recipeId);
}
