package com.aiinterview.assistant.controller;

import com.aiinterview.assistant.dto.ChatRequest;
import com.aiinterview.assistant.dto.ChatResponse;
import com.aiinterview.assistant.dto.EmbeddingRequest;
import com.aiinterview.assistant.dto.EmbeddingResponse;
import com.aiinterview.assistant.service.AiChatService;
import com.aiinterview.assistant.service.EmbeddingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiChatService aiChatService;
    private final EmbeddingService embeddingService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = aiChatService.generateResponse(request.getMessage(), request.getSystemPrompt());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/embeddings")
    public ResponseEntity<EmbeddingResponse> embed(@Valid @RequestBody EmbeddingRequest request) {
        return ResponseEntity.ok(embeddingService.embedTexts(request.getTexts()));
    }
}
