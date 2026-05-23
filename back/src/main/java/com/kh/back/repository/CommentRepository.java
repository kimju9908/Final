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
}
