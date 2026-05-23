package com.kh.back.dto.recipe.res;

import com.kh.back.dto.python.SearchResDto;
import lombok.*;

import java.util.List;

@Getter @Setter @ToString
@NoArgsConstructor @AllArgsConstructor
public class CocktailResDto extends SearchResDto {
	private String glass;
	private String preparation;
	private String image;
	private String category;
	private float abv;
	private String garnish;
	private List<CocktailIngResDto> ingredients;
}
