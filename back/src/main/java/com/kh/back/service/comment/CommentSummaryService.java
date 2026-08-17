package com.kh.back.service.comment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kh.back.dto.comment.CommentSummaryResDto;
import com.kh.back.entity.Comment;
import com.kh.back.entity.RecipeCommentSummary;
import com.kh.back.repository.CommentRepository;
import com.kh.back.repository.RecipeCommentSummaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 레시피 댓글 AI 요약 서비스
 *
 * 동작 규칙:
 * - 댓글 수 < THRESHOLD(10)          → 요약 없음
 * - 댓글 수 >= 10, 요약본 없음        → 최초 요약 생성 후 저장
 * - 저장된 요약본 존재                → 그대로 반환
 * - 요약 이후 REFRESH_INTERVAL(20)개 이상 새 댓글 누적
 *   → [기존 요약 + 새 댓글]을 Claude에 전달해 재요약 후 갱신
 */
@Slf4j
@Service
public class CommentSummaryService {

    private static final int THRESHOLD = 10;         // 최초 요약 생성 기준 댓글 수
    private static final int REFRESH_INTERVAL = 20;  // 재요약 기준 신규 댓글 수
    private static final int MAX_COMMENTS_PER_CALL = 50; // 1회 요약에 보낼 최대 댓글 수

    private final CommentRepository commentRepository;
    private final RecipeCommentSummaryRepository summaryRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${anthropic.api.key}")
    private String anthropicApiKey;

    public CommentSummaryService(CommentRepository commentRepository,
                                 RecipeCommentSummaryRepository summaryRepository,
                                 RestTemplate restTemplate,
                                 ObjectMapper objectMapper) {
        this.commentRepository = commentRepository;
        this.summaryRepository = summaryRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CommentSummaryResDto getSummary(String recipeId) {
        long count = commentRepository.countByRecipeIdAndDeletedFalse(recipeId);

        // 기준 미만 → 요약 제공 안 함
        if (count < THRESHOLD) {
            return CommentSummaryResDto.builder()
                    .summary(null)
                    .commentCount(count)
                    .build();
        }

        RecipeCommentSummary record = summaryRepository.findByRecipeId(recipeId).orElse(null);

        try {
            if (record == null) {
                // 최초 요약: 최신 댓글 전체(최대 50개)로 생성
                List<Comment> comments = fetchLatestComments(recipeId, (int) Math.min(count, MAX_COMMENTS_PER_CALL));
                String summary = requestFirstSummary(comments);
                record = RecipeCommentSummary.builder()
                        .recipeId(recipeId)
                        .summary(summary)
                        .summarizedCount(count)
                        .build();
                summaryRepository.save(record);
                log.info("[CommentSummary] 최초 요약 생성 - recipeId={}, count={}", recipeId, count);

            } else if (count >= record.getSummarizedCount() + REFRESH_INTERVAL) {
                // 갱신: 기존 요약 + 요약 이후 쌓인 새 댓글만 전달
                int newCount = (int) Math.min(count - record.getSummarizedCount(), MAX_COMMENTS_PER_CALL);
                List<Comment> newComments = fetchLatestComments(recipeId, newCount);
                String summary = requestRefreshSummary(record.getSummary(), newComments);
                record.setSummary(summary);
                record.setSummarizedCount(count);
                summaryRepository.save(record);
                log.info("[CommentSummary] 요약 갱신 - recipeId={}, count={}, newComments={}", recipeId, count, newCount);
            }
        } catch (Exception e) {
            // 요약 실패해도 기존 요약본이 있으면 그대로 반환 (메인 기능에 영향 없음)
            log.error("[CommentSummary] 요약 생성 실패 - recipeId={}: {}", recipeId, e.getMessage());
        }

        return CommentSummaryResDto.builder()
                .summary(record != null ? record.getSummary() : null)
                .commentCount(count)
                .updatedAt(record != null ? record.getUpdatedAt() : null)
                .build();
    }

    /** 최신 댓글 N개 조회 (createdAt 내림차순) */
    private List<Comment> fetchLatestComments(String recipeId, int size) {
        return commentRepository.findByRecipeIdAndDeletedFalseOrderByCreatedAtDesc(
                recipeId, PageRequest.of(0, size));
    }

    /** 최초 요약 요청 */
    private String requestFirstSummary(List<Comment> comments) throws Exception {
        String prompt =
                "다음은 요리 레시피에 달린 사용자 댓글들이야. 전체적인 반응을 2~3문장으로 한국어로 요약해줘. " +
                "맛 평가, 난이도, 개선 의견 위주로 정리하고, 요약문만 출력해.\n\n" +
                joinComments(comments);
        return callClaude(prompt);
    }

    /** 기존 요약 + 새 댓글 재요약 요청 */
    private String requestRefreshSummary(String previousSummary, List<Comment> newComments) throws Exception {
        String prompt =
                "다음은 요리 레시피 댓글의 기존 요약과 그 이후 새로 달린 댓글들이야. " +
                "둘을 종합해서 최신 반응이 반영된 요약을 2~3문장으로 한국어로 작성해줘. 요약문만 출력해.\n\n" +
                "[기존 요약]\n" + previousSummary + "\n\n" +
                "[새 댓글]\n" + joinComments(newComments);
        return callClaude(prompt);
    }

    /** 댓글 목록을 프롬프트용 문자열로 변환 */
    private String joinComments(List<Comment> comments) {
        return comments.stream()
                .map(c -> "- " + c.getContent())
                .collect(Collectors.joining("\n"));
    }

    /** Claude API 호출 */
    private String callClaude(String prompt) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", anthropicApiKey);
        headers.set("anthropic-version", "2023-06-01");

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", "claude-haiku-4-5-20251001",
                "max_tokens", 512,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        ));

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "https://api.anthropic.com/v1/messages", entity, String.class);

        Map<String, Object> result = objectMapper.readValue(
                response.getBody(), new TypeReference<Map<String, Object>>() {});

        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        return ((String) content.get(0).get("text")).trim();
    }
}
