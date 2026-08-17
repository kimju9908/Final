package com.kh.back.controller.comment;

import com.kh.back.dto.comment.CommentCreateReqDto;
import com.kh.back.dto.comment.CommentResDto;
import com.kh.back.dto.comment.CommentSummaryResDto;
import com.kh.back.dto.comment.ReplyCreateReqDto;
import com.kh.back.service.comment.CommentService;
import com.kh.back.service.comment.CommentSummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping
public class CommentController {
    private final CommentService commentService;
    private final CommentSummaryService commentSummaryService;

    @Autowired
    public CommentController(CommentService commentService, CommentSummaryService commentSummaryService) {
        this.commentService = commentService;
        this.commentSummaryService = commentSummaryService;
    }
    // 특정 레시피의 댓글 목록 조회
    @GetMapping("/recipes/{recipeId}/comments")
    public ResponseEntity<Page<CommentResDto>> getRecipeComments(
            @PathVariable String recipeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("commentId").descending());
        Page<CommentResDto> commentPage = commentService.getCommentsByRecipeId(recipeId, pageable);
        return ResponseEntity.ok(commentPage);
    }

    // 레시피 댓글 AI 요약 조회 (댓글 10개 이상일 때 생성, 20개마다 갱신)
    @GetMapping("/recipes/{recipeId}/comments/summary")
    public ResponseEntity<CommentSummaryResDto> getCommentSummary(@PathVariable String recipeId) {
        return ResponseEntity.ok(commentSummaryService.getSummary(recipeId));
    }

    // 레시피 하위 자원으로 댓글 생성
    @PostMapping("/recipes/{recipeId}/comments")
    public ResponseEntity<Boolean> createComment(Authentication authentication,
                                                 @PathVariable String recipeId,
                                                 @RequestBody CommentCreateReqDto request) {
        Long memberId = Long.parseLong(authentication.getName());
        boolean isSaved = commentService.addComment(memberId, recipeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(isSaved);
    }

    // 댓글의 하위 자원으로 대댓글 생성
    @PostMapping("/comments/{commentId}/replies")
    public ResponseEntity<Boolean> createReply(Authentication authentication,
                                               @PathVariable Long commentId,
                                               @RequestBody ReplyCreateReqDto request) {
        Long memberId = Long.parseLong(authentication.getName());
        boolean isSaved = commentService.addReply(memberId, commentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(isSaved);
    }

    // 댓글/대댓글 모두 Comment 자원이므로 단일 DELETE 엔드포인트로 처리
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(Authentication authentication,
                                              @PathVariable Long commentId) {
        Long memberId = Long.parseLong(authentication.getName());
        commentService.deleteComment(memberId, commentId);
        return ResponseEntity.noContent().build();
    }
}
