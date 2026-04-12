package com.kh.back.service.python;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DirectElasticIndexService {

    private static final Map<String, String> INDEX_BY_TYPE = Map.of(
            "food", "recipe_food",
            "cocktail", "recipe_cocktail"
    );

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.elasticsearch.direct-base-url:http://localhost:9200}")
    private String elasticsearchBaseUrl;

    public String uploadRecipe(Map<String, Object> recipeData) {
        String indexName = resolveIndexName(recipeData.get("type"));
        String jsonBody = toJson(recipeData);

        try {
            URI uri = new URI(elasticsearchBaseUrl + "/" + indexName + "/_doc");
            HttpEntity<String> requestEntity = new HttpEntity<>(jsonBody, createJsonHeaders());
            ResponseEntity<String> response = restTemplate.postForEntity(uri, requestEntity, String.class);
            log.info("[uploadRecipeDirect] index={}, response={}", indexName, response.getBody());
            return response.getBody();
        } catch (Exception e) {
            log.error("[uploadRecipeDirect] failed. index={}, message={}", indexName, e.getMessage());
            throw new RuntimeException("Spring direct ES upload failed: " + e.getMessage(), e);
        }
    }

    public String updateRecipe(Map<String, Object> recipeData) {
        String indexName = resolveIndexName(recipeData.get("type"));
        Object updateId = recipeData.get("updateId");
        if (updateId == null || String.valueOf(updateId).isBlank()) {
            throw new IllegalArgumentException("updateId is required for direct ES update.");
        }

        Map<String, Object> updateDoc = new HashMap<>(recipeData);
        updateDoc.remove("updateId");

        String jsonBody = toJson(Map.of("doc", updateDoc));

        try {
            String encodedId = URLEncoder.encode(String.valueOf(updateId), StandardCharsets.UTF_8);
            URI uri = new URI(elasticsearchBaseUrl + "/" + indexName + "/_update/" + encodedId);
            HttpEntity<String> requestEntity = new HttpEntity<>(jsonBody, createJsonHeaders());
            ResponseEntity<String> response = restTemplate.postForEntity(uri, requestEntity, String.class);
            log.info("[updateRecipeDirect] index={}, updateId={}, response={}", indexName, updateId, response.getBody());
            return response.getBody();
        } catch (Exception e) {
            log.error("[updateRecipeDirect] failed. index={}, updateId={}, message={}", indexName, updateId, e.getMessage());
            throw new RuntimeException("Spring direct ES update failed: " + e.getMessage(), e);
        }
    }

    private HttpHeaders createJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String resolveIndexName(Object typeValue) {
        if (typeValue == null) {
            throw new IllegalArgumentException("type is required for direct ES indexing.");
        }

        String type = String.valueOf(typeValue);
        String indexName = INDEX_BY_TYPE.get(type);
        if (indexName == null) {
            throw new IllegalArgumentException("Unsupported type for direct ES indexing: " + type);
        }
        return indexName;
    }

    private String toJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize request body for direct ES indexing.", e);
        }
    }
}
