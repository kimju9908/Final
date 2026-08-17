package com.kh.back.service.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kh.back.dto.search.ChatRecipeResDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ChatRecipeService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String fastapiBaseUrl = "http://localhost:5001";

    @Value("${anthropic.api.key}")
    private String anthropicApiKey;

    public ChatRecipeService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public ChatRecipeResDto chat(String message, String type) {
        try {
            // 1. Claude API로 재료 추출
            List<String> ingredients = extractIngredients(message);

            if (ingredients == null || ingredients.isEmpty()) {
                return ChatRecipeResDto.builder()
                        .ingredients(Collections.emptyList())
                        .recipes(Collections.emptyList())
                        .message("재료를 찾을 수 없었어요. 예) '계란이랑 두부로 만들 수 있는거 뭐야?'")
                        .build();
            }

            // 2. FastAPI에 재료 전달 → ES 검색
            URI uri = new URI(fastapiBaseUrl + "/chat/recipe");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String body = objectMapper.writeValueAsString(Map.of("ingredients", ingredients, "type", type));
            HttpEntity<String> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(uri, entity, String.class);
            Map<String, Object> result = objectMapper.readValue(
                    response.getBody(), new TypeReference<Map<String, Object>>() {});

            return ChatRecipeResDto.builder()
                    .ingredients((List<String>) result.get("ingredients"))
                    .recipes((List<Map<String, Object>>) result.get("recipes"))
                    .message((String) result.get("message"))
                    .build();

        } catch (Exception e) {
            log.error("[ChatRecipeService] 오류: {}", e.getMessage());
            return ChatRecipeResDto.builder()
                    .message("레시피 추천 중 오류가 발생했습니다.")
                    .build();
        }
    }

    private List<String> extractIngredients(String message) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", anthropicApiKey);
            headers.set("anthropic-version", "2023-06-01");

            String prompt = String.format(
                "다음 문장에서 음식 재료명만 추출해서 JSON 배열로 반환해줘. " +
                "재료가 없으면 빈 배열 []을 반환해. 다른 말은 절대 하지 마.\n" +
                "문장: %s\n" +
                "응답 형식: [\"재료1\", \"재료2\"]", message
            );

            String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", "claude-haiku-4-5-20251001",
                "max_tokens", 256,
                "messages", List.of(Map.of("role", "user", "content", prompt))
            ));

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                "https://api.anthropic.com/v1/messages", entity, String.class);

            Map<String, Object> result = objectMapper.readValue(
                response.getBody(), new TypeReference<Map<String, Object>>() {});

            List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
            String text = (String) content.get(0).get("text");

            return objectMapper.readValue(text.trim(), new TypeReference<List<String>>() {});

        } catch (Exception e) {
            log.error("[ChatRecipeService] Claude API 오류: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
