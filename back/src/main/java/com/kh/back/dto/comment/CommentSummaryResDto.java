package com.kh.back.dto.comment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentSummaryResDto {
    /** AI 요약 내용 (댓글 수가 기준 미만이면 null) */
    private String summary;
    /** 현재 댓글 수 */
    private long commentCount;
    /** 요약 갱신 시각 */
    private LocalDateTime updatedAt;
}
