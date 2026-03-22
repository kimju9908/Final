package com.kh.back.service.comment;

import com.kh.back.dto.comment.CommentCreateReqDto;
import com.kh.back.dto.comment.CommentResDto;
import com.kh.back.dto.comment.ReplyCreateReqDto;
import com.kh.back.entity.Comment;
import com.kh.back.entity.member.Member;
import com.kh.back.repository.CommentRepository;
import com.kh.back.repository.member.MemberRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;

    private final MemberRepository memberRepository;

    @Transactional
    public boolean addComment(Long memberId, String recipeId, CommentCreateReqDto request) {
        try {
            Member member = getMember(memberId);
            Comment comment = new Comment();
            comment.setMember(member);
            comment.setRecipeId(recipeId);
            comment.setContent(request.getContent());
            commentRepository.save(comment);
            return true;
        } catch (Exception e) {
            log.error("[addComment] failed. memberId={}, recipeId={}, message={}", memberId, recipeId, e.getMessage());
            return false;
        }
    }

    @Transactional
    public boolean addReply(Long memberId, Long parentCommentId, ReplyCreateReqDto request) {
        Comment parentComment = getComment(parentCommentId, "부모 댓글을 찾을 수 없습니다.");
        Member member = getMember(memberId);
        Comment reply = new Comment();
        reply.setRecipeId(parentComment.getRecipeId());
        reply.setContent(request.getContent());
        reply.setMember(member);
        reply.setParentComment(parentComment);
        parentComment.getReplies().add(reply);
        commentRepository.save(parentComment);
        return true;
    }

    public Page<CommentResDto> getCommentsByRecipeId(String recipeId, Pageable pageable) {
        Page<Comment> commentsPage = commentRepository.findByRecipeIdAndParentCommentIsNull(recipeId, pageable);
        return commentsPage.map(CommentResDto::fromEntity);
    }

    @Transactional
    public void deleteComment(Long memberId, Long commentId) {
        Comment comment = getComment(commentId, "댓글을 찾을 수 없습니다.");
        if (!comment.getMember().getMemberId().equals(memberId)) {
            throw new RuntimeException("댓글 삭제 권한이 없습니다.");
        }
        commentRepository.delete(comment);
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원이 존재하지 않습니다."));
    }

    private Comment getComment(Long commentId, String message) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException(message));
    }
}
