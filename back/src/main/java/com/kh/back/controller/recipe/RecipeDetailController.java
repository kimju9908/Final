package com.kh.back.controller.recipe;

import com.kh.back.constant.ReactionType;
import com.kh.back.dto.recipe.request.AddCocktailRecipeDto;
import com.kh.back.dto.recipe.request.AddFoodRecipeDto;
import com.kh.back.dto.recipe.request.ReactionUpdateReqDto;
import com.kh.back.dto.recipe.res.DirectUploadTestResDto;
import com.kh.back.dto.recipe.res.ReactionSummaryResDto;
import com.kh.back.service.action.ReActionService;
import com.kh.back.service.member.MemberService;
import com.kh.back.service.recipe.AddCocktailRecipeService;
import com.kh.back.service.recipe.AddFoodRecipeService;
import com.kh.back.service.redis.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping("/recipe")
public class RecipeDetailController {
    @Autowired
    private RedisService redisService;
    @Autowired
    private AddFoodRecipeService recipeService;
    @Autowired
    private AddCocktailRecipeService cocktailRecipeService;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private MemberService memberService;
    @Autowired
    private ReActionService reActionService;

    @PostMapping("/save-recipe")
    public ResponseEntity<String> saveRecipe(Authentication authentication, @ModelAttribute AddFoodRecipeDto recipeRequest) {
        Long memberId = Long.parseLong(authentication.getName());
        String jsonData = recipeService.saveRecipe(memberId, recipeRequest);
        return ResponseEntity.ok(jsonData);
    }

    @PostMapping("/update-recipe")
    public ResponseEntity<String> updateRecipe(Authentication authentication, @ModelAttribute AddFoodRecipeDto recipeRequest) {
        Long memberId = Long.parseLong(authentication.getName());
        String jsonData = recipeService.updateRecipe(memberId, recipeRequest);
        return ResponseEntity.ok(jsonData);
    }

    @PostMapping("/save-cocktail-recipe")
    public ResponseEntity<String> saveCocktailRecipe(Authentication authentication, @ModelAttribute AddCocktailRecipeDto recipeRequest) {
        Long memberId = Long.parseLong(authentication.getName());
        String jsonData = cocktailRecipeService.saveCocktailRecipe(memberId, recipeRequest);
        return ResponseEntity.ok(jsonData);
    }

    @PostMapping("/update-cocktail-recipe")
    public ResponseEntity<String> updateCocktailRecipe(Authentication authentication, @ModelAttribute AddCocktailRecipeDto recipeRequest) {
        Long memberId = Long.parseLong(authentication.getName());
        String jsonData = cocktailRecipeService.updateCocktailRecipe(memberId, recipeRequest);
        return ResponseEntity.ok(jsonData);
    }

    @PostMapping("/test/save-recipe-direct")
    public ResponseEntity<DirectUploadTestResDto> saveRecipeDirect(Authentication authentication, @ModelAttribute AddFoodRecipeDto recipeRequest) {
        Long memberId = Long.parseLong(authentication.getName());
        DirectUploadTestResDto response = recipeService.saveRecipeDirect(memberId, recipeRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/test/update-recipe-direct")
    public ResponseEntity<DirectUploadTestResDto> updateRecipeDirect(Authentication authentication, @ModelAttribute AddFoodRecipeDto recipeRequest) {
        Long memberId = Long.parseLong(authentication.getName());
        DirectUploadTestResDto response = recipeService.updateRecipeDirect(memberId, recipeRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/test/save-cocktail-recipe-direct")
    public ResponseEntity<DirectUploadTestResDto> saveCocktailRecipeDirect(Authentication authentication, @ModelAttribute AddCocktailRecipeDto recipeRequest) {
        Long memberId = Long.parseLong(authentication.getName());
        DirectUploadTestResDto response = cocktailRecipeService.saveCocktailRecipeDirect(memberId, recipeRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/test/update-cocktail-recipe-direct")
    public ResponseEntity<DirectUploadTestResDto> updateCocktailRecipeDirect(Authentication authentication, @ModelAttribute AddCocktailRecipeDto recipeRequest) {
        Long memberId = Long.parseLong(authentication.getName());
        DirectUploadTestResDto response = cocktailRecipeService.updateCocktailRecipeDirect(memberId, recipeRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reaction")
    public ResponseEntity<ReactionSummaryResDto> updateReaction(
            Authentication authentication,
            @RequestBody ReactionUpdateReqDto requestDto
    ) {
        long likeCount = reActionService.getReactionCount(requestDto.getPostId(), requestDto.getType(), ReactionType.LIKE);
        long dislikeCount = reActionService.getReactionCount(requestDto.getPostId(), requestDto.getType(), ReactionType.DISLIKE);
        ReactionSummaryResDto response = redisService.updateReaction(
                authentication,
                requestDto.getPostId(),
                requestDto.getType(),
                requestDto.getRequestedReaction(),
                likeCount,
                dislikeCount
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reaction-summary")
    public ResponseEntity<ReactionSummaryResDto> getReactionSummary(
            Authentication authentication,
            @RequestParam String postId,
            @RequestParam String type
    ) {
        long likeCount = reActionService.getReactionCount(postId, type, ReactionType.LIKE);
        long dislikeCount = reActionService.getReactionCount(postId, type, ReactionType.DISLIKE);
        ReactionSummaryResDto response = redisService.getReactionSummary(
                authentication,
                postId,
                type,
                likeCount,
                dislikeCount
        );
        return ResponseEntity.ok(response);
    }
}
