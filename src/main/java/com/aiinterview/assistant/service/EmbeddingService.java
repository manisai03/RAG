package com.aiinterview.assistant.service;

import com.aiinterview.assistant.config.OllamaAiProperties;
import com.aiinterview.assistant.dto.EmbeddingResponse;
import com.aiinterview.assistant.exception.AiServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final OllamaAiProperties ollamaAiProperties;

    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new AiServiceException("Text cannot be empty");
        }

        log.debug("Generating embedding for text length: {}", text.length());

        try {
            return embeddingModel.embed(text);
        } catch (Exception ex) {
            throw new AiServiceException("Failed to generate embedding from Ollama: " + ex.getMessage(), ex);
        }
    }

    public List<float[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new AiServiceException("At least one text is required for embedding");
        }

        log.debug("Generating embeddings for {} text(s) using Ollama", texts.size());

        try {
            return embeddingModel.embed(texts);
        } catch (Exception ex) {
            throw new AiServiceException("Failed to generate embeddings from Ollama: " + ex.getMessage(), ex);
        }
    }

    public EmbeddingResponse embedTexts(List<String> texts) {
        List<float[]> embeddings = embedAll(texts);
        int dimensions = embeddings.isEmpty() ? 0 : embeddings.getFirst().length;

        return EmbeddingResponse.builder()
                .model(ollamaAiProperties.getEmbeddingModel())
                .dimensions(dimensions)
                .embeddings(embeddings)
                .build();
    }
}
