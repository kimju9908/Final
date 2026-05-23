package com.kh.back.dto.recipe.res;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kh.back.dto.python.SearchListResDto;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class FoodListResDto extends SearchListResDto {
    @JsonProperty("image")
    @JsonAlias("ATT_FILE_NO_MAIN")
    private String image;

    @JsonProperty("category")
    @JsonAlias("RCP_PAT2")
    private String category;
}
