package com.kh.back.dto.recipe.res;

import com.kh.back.constant.ReactionType;
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
public class ReactionSummaryResDto {
    private String postId;
    private String type;
    private long likeCount;
    private long dislikeCount;
    private ReactionType currentUserReaction;
}
