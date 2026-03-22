package com.kh.back.dto.recommend;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedCocktailResDto {
    private String id;
    private String name;
    private String image;
    private String category;
    private long like;
    private long author;
    private String reason;
    private double recommendationScore;
}
