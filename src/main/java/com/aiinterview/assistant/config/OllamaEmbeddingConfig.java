package com.aiinterview.assistant.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class OllamaEmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingModel ollamaEmbeddingModel(OllamaApi ollamaApi, OllamaAiProperties properties) {
        EmbeddingModel ollama = OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaOptions.builder()
                        .model(properties.getEmbeddingModel())
                        .build())
                .build();
        return new TaskPrefixEmbeddingModel(
                ollama,
                properties.getEmbeddingDocumentPrefix(),
                properties.getEmbeddingQueryPrefix()
        );
    }
}
