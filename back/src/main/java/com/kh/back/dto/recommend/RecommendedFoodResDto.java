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
public class RecommendedFoodResDto {
    private String id;
    private String name;
    private String image;
    private String category;
    private int like;
    private int author;
    private String reason;
    private double recommendationScore;
}
