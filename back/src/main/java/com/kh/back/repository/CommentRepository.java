package com.kh.back.repository;




import com.kh.back.entity.Comment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    @EntityGraph(attributePaths = {"member"})
    Page<Comment> findByRecipeIdAndParentCommentIsNull(String recipeId, Pageable pageable);

    @EntityGraph(attributePaths = {"member", "parentComment"})
    List<Comment> findByParentCommentCommentIdInOrderByCreatedAtAsc(List<Long> parentCommentIds);

    /** 삭제되지 않은 댓글 수 (AI 요약 기준 판단용) */
    long countByRecipeIdAndDeletedFalse(String recipeId);

    /** 최신 댓글 N개 조회 (AI 요약용, Pageable로 개수 제한) */
    List<Comment> findByRecipeIdAndDeletedFalseOrderByCreatedAtDesc(String recipeId, Pageable pageable);
}
