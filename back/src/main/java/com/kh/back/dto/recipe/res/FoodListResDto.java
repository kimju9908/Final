package com.kh.back.dto.recipe.res;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kh.back.dto.python.SearchListResDto;
import lombok.*;

/**
 * 음식 레시피 목록 응답 DTO
 * - 입력 시 JSON의 "ATT_FILE_NO_MAIN"과 "RCP_PAT2"를 각각 image, category로 매핑하고,
 *   출력 시에는 "image", "category"라는 키로 반환합니다.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class FoodListResDto extends SearchListResDto {
    // 입력은 "ATT_FILE_NO_MAIN"을 받지만, 출력은 "image"로 보냄
    @JsonProperty("image")
    @JsonAlias("ATT_FILE_NO_MAIN")
    private String image;

    private Long like;
    @JsonProperty("dislike")
    @JsonAlias({"dislike", "report"})
    private Long dislike;

    // 입력은 "RCP_PAT2"를 받지만, 출력은 "category"로 보냄
    @JsonProperty("category")
    @JsonAlias("RCP_PAT2")
    private String category;
}
