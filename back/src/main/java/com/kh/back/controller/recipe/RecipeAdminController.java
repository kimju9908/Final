package com.kh.back.controller.recipe;

import com.kh.back.service.python.ElasticService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 레시피 인덱스 관리 컨트롤러 (관리자용)
 *
 * 사용 방법:
 * POST /admin/recipe/reset-indexes
 * → ES의 recipe_food, recipe_cocktail 인덱스를 삭제하고
 *   food.json / cocktail.json 원본 데이터로 재색인합니다.
 *   (like, dislike, author 는 ES에 저장되지 않습니다)
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/admin/recipe")
public class RecipeAdminController {

    private final ElasticService elasticService;

    /**
     * ES 레시피 인덱스 전체 리셋 + 재색인
     *
     * 1. recipe_food, recipe_cocktail 인덱스 삭제
     * 2. 새 매핑(food_mapping.json, cocktail_mapping.json)으로 인덱스 재생성
     * 3. food.json, cocktail.json 원본 데이터 재색인
     *    - like, dislike, author 필드는 ES에 저장하지 않음 (DB에서 관리)
     */
    @PostMapping("/reset-indexes")
    public ResponseEntity<String> resetIndexes() {
        log.info("[RecipeAdminController] 레시피 인덱스 리셋 요청");
        String result = elasticService.resetAndReimportRecipeIndexes();
        return ResponseEntity.ok(result);
    }
}
