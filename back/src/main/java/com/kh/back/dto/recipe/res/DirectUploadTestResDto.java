package com.kh.back.dto.recipe.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectUploadTestResDto {
    private String mode;
    private Long elapsedMs;
    private String result;
}
