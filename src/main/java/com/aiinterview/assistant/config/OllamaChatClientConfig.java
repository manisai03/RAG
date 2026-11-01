package com.aiinterview.assistant.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OllamaAiProperties.class)
public class OllamaChatClientConfig {

    @Bean
    public ChatClient ollamaChatClient(OllamaChatModel ollamaChatModel, OllamaAiProperties properties) {
        return ChatClient.builder(ollamaChatModel)
                .defaultSystem(properties.getSystemPrompt())
                .defaultOptions(OllamaOptions.builder()
                        .model(properties.getChatModel())
                        .temperature(properties.getTemperature())
                        .build())
                .build();
    }
}
