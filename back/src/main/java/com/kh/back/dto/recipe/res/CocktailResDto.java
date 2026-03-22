package com.kh.back.dto.recipe.res;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kh.back.dto.python.SearchResDto;
import lombok.*;

import java.util.List;

@Getter @Setter @ToString
@NoArgsConstructor @AllArgsConstructor
public class CocktailResDto extends SearchResDto {
	private String glass;
	private String preparation;     // 설명 (혹은 preparation, garnishing을 위한 필드로 확장 가능)
	private String image;           // 이미지 (keyword 필드)
	private String category;        // 카테고리 (keyword 필드)
	private float abv;              // 알콜 도수 (float)
	private String garnish;         // 장식 (text 필드)
	private long like;              // 좋아요 수 (long 필드)
	@JsonProperty("dislike")
	@JsonAlias({"dislike", "report"})
	private long dislike;           // 싫어요 수 (long 필드)
	private long author;            // 작성자 ID (long 필드)
	private List<CocktailIngResDto> ingredients;  // 재료 목록 (nested 필드)
}
