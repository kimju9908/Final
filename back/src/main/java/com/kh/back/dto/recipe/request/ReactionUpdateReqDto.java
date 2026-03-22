package com.kh.back.dto.recipe.request;

import com.kh.back.constant.ReactionType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReactionUpdateReqDto {
    private String postId;
    private String type;
    private ReactionType requestedReaction;
}
