package com.kh.back.controller.search;

import com.kh.back.dto.search.ChatRecipeResDto;
import com.kh.back.service.search.ChatRecipeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:3000")
public class ChatRecipeController {

    private final ChatRecipeService chatRecipeService;

    @PostMapping("/recipe")
    public ResponseEntity<ChatRecipeResDto> chatRecipe(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        String type = body.getOrDefault("type", "food");
        log.debug("[ChatRecipeController] message={}, type={}", message, type);
        return ResponseEntity.ok(chatRecipeService.chat(message, type));
    }
}
