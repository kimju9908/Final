package com.kh.back.dto.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRecipeResDto {
    private List<String> ingredients;
    private List<Map<String, Object>> recipes;
    private String message;
}
