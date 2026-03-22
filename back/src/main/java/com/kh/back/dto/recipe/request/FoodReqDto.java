package com.kh.back.dto.recipe.request;

import lombok.*;

/**
 * 음식 레시피 생성/수정 요청 DTO
 * 클라이언트에서 전달한 음식 레시피 정보를 담는 객체
 * 매핑:
 *  - name          ← food.json의 RCP_NM (레시피 명)
 *  - cookingMethod ← food.json의 RCP_WAY2 (조리 방법)
 *  - category      ← food.json의 RCP_PAT2 (카테고리)
 *  - description   ← food.json의 RCP_NA_TIP (설명)
 *  - image         ← food.json의 ATT_FILE_NO_MAIN (메인 이미지 URL)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FoodReqDto {
    private String name;
    private String cookingMethod;
    private String category;
    private String description;
    private String image;
    private Long like;
    private Long dislike;
}
