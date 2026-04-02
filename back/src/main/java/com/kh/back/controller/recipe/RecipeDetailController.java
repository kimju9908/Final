package com.kh.back.controller.recipe;

import com.kh.back.dto.recipe.request.AddCocktailRecipeDto;
import com.kh.back.dto.recipe.request.AddFoodRecipeDto;
import com.kh.back.dto.recipe.request.ReactionUpdateReqDto;
import com.kh.back.dto.recipe.res.CocktailResDto;
import com.kh.back.dto.recipe.res.DirectUploadTestResDto;
import com.kh.back.dto.recipe.res.FoodResDto;
import com.kh.back.dto.recipe.res.ReactionSummaryResDto;
import com.kh.back.service.member.MemberService;
import com.kh.back.service.recipe.AddCocktailRecipeService;
import com.kh.back.service.recipe.AddFoodRecipeService;
import com.kh.back.service.recipe.RecipeService;
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
    private RecipeService recipeQueryService;

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

    // 성능 비교용: Flask를 거치지 않고 Spring이 직접 Elasticsearch에 색인
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
        Object recipeDetail = recipeQueryService.getRecipeDetail(requestDto.getPostId(), requestDto.getType());
        ReactionSummaryResDto response = redisService.updateReaction(
                authentication,
                requestDto.getPostId(),
                requestDto.getType(),
                requestDto.getRequestedReaction(),
                extractLikeCount(recipeDetail),
                extractDislikeCount(recipeDetail)
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reaction-summary")
    public ResponseEntity<ReactionSummaryResDto> getReactionSummary(
            Authentication authentication,
            @RequestParam String postId,
            @RequestParam String type
    ) {
        Object recipeDetail = recipeQueryService.getRecipeDetail(postId, type);
        ReactionSummaryResDto response = redisService.getReactionSummary(
                authentication,
                postId,
                type,
                extractLikeCount(recipeDetail),
                extractDislikeCount(recipeDetail)
        );
        return ResponseEntity.ok(response);
    }

    private long extractLikeCount(Object recipeDetail) {
        if (recipeDetail instanceof FoodResDto foodResDto) {
            return foodResDto.getLike();
        }
        if (recipeDetail instanceof CocktailResDto cocktailResDto) {
            return cocktailResDto.getLike();
        }
        return 0L;
    }

    private long extractDislikeCount(Object recipeDetail) {
        if (recipeDetail instanceof FoodResDto foodResDto) {
            return foodResDto.getDislike();
        }
        if (recipeDetail instanceof CocktailResDto cocktailResDto) {
            return cocktailResDto.getDislike();
        }
        return 0L;
    }
}
