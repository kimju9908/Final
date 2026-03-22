package com.kh.back.dto.recipe.res;


import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kh.back.dto.python.SearchListResDto;
import lombok.*;

@Getter @Setter @ToString
@NoArgsConstructor
@AllArgsConstructor
public class CocktailListResDto extends SearchListResDto {
	private Long like;
	@JsonProperty("dislike")
	@JsonAlias({"dislike", "report"})
	private Long dislike;
	private String category;
}
