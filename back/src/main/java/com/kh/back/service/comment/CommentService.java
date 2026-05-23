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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        if (parentComment.isDeleted()) {
            throw new RuntimeException("삭제된 댓글에는 답글을 작성할 수 없습니다.");
        }
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
        List<Comment> parentComments = commentsPage.getContent();
        if (parentComments.isEmpty()) {
            return commentsPage.map(comment -> buildCommentDto(comment, Collections.emptyList()));
        }

        List<Long> parentCommentIds = parentComments.stream()
                .map(Comment::getCommentId)
                .toList();

        List<Comment> replies = commentRepository.findByParentCommentCommentIdInOrderByCreatedAtAsc(parentCommentIds);
        Map<Long, List<Comment>> repliesByParentId = replies.stream()
                .collect(Collectors.groupingBy(reply -> reply.getParentComment().getCommentId()));

        return commentsPage.map(comment -> buildCommentDto(
                comment,
                repliesByParentId.getOrDefault(comment.getCommentId(), Collections.emptyList())
        ));
    }

    @Transactional
    public void deleteComment(Long memberId, Long commentId) {
        Comment comment = getComment(commentId, "댓글을 찾을 수 없습니다.");
        if (!comment.getMember().getMemberId().equals(memberId)) {
            throw new RuntimeException("댓글 삭제 권한이 없습니다.");
        }
        if (comment.isDeleted()) {
            return;
        }
        comment.setDeleted(true);
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원이 존재하지 않습니다."));
    }

    private Comment getComment(Long commentId, String message) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException(message));
    }

    private CommentResDto buildCommentDto(Comment comment, List<Comment> replies) {
        return CommentResDto.builder()
                .memberId(comment.getMember().getMemberId())
                .commentId(comment.getCommentId())
                .nickName(comment.getMember().getNickName())
                .content(comment.getContent())
                .parentCommentId(comment.getParentComment() != null ? comment.getParentComment().getCommentId() : null)
                .isReply(comment.getParentComment() != null)
                .deleted(comment.isDeleted())
                .replies(replies.stream()
                        .map(reply -> CommentResDto.builder()
                                .memberId(reply.getMember().getMemberId())
                                .commentId(reply.getCommentId())
                                .nickName(reply.getMember().getNickName())
                                .content(reply.getContent())
                                .parentCommentId(reply.getParentComment() != null ? reply.getParentComment().getCommentId() : null)
                                .isReply(true)
                                .deleted(reply.isDeleted())
                                .replies(Collections.emptyList())
                                .build())
                        .toList())
                .build();
    }
}
