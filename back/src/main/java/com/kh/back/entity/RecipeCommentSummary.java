package com.kh.back.entity;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 레시피 댓글 AI 요약본
 * - 댓글이 기준 개수(10개) 이상 쌓이면 Claude API로 요약 생성
 * - 이후 갱신 주기(20개)만큼 새 댓글이 쌓이면 [기존 요약 + 새 댓글]로 재요약
 * - recipe 테이블과 분리하여 상세 페이지에서만 조회
 */
@Entity
@Table(name = "recipe_comment_summary")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeCommentSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "summary_id")
    private Long id;

    /** ES 레시피 문서 ID (Comment.recipeId 와 동일한 값) */
    @Column(name = "recipe_id", nullable = false, unique = true, length = 100)
    private String recipeId;

    /** AI 요약 내용 */
    @Column(name = "summary", nullable = false, length = 1000)
    private String summary;

    /** 요약 시점의 댓글 수 (갱신 기준 판단용) */
    @Column(name = "summarized_count", nullable = false)
    private long summarizedCount;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
