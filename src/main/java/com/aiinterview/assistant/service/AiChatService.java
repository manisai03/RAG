package com.aiinterview.assistant.service;

import com.aiinterview.assistant.config.OllamaAiProperties;
import com.aiinterview.assistant.dto.ChatResponse;
import com.aiinterview.assistant.dto.ChatTurn;
import com.aiinterview.assistant.exception.AiServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final ChatClient ollamaChatClient;
    private final OllamaAiProperties ollamaAiProperties;

    public ChatResponse generateResponse(String userMessage) {
        return generateResponse(userMessage, null);
    }

    public ChatResponse generateResponse(String userMessage, String systemPrompt) {
        return generateResponse(userMessage, systemPrompt, null);
    }

    public ChatResponse generateResponse(String userMessage, String systemPrompt, Double temperatureOverride) {
        return generateResponse(userMessage, systemPrompt, temperatureOverride, List.of());
    }

    public ChatResponse generateResponse(
            String userMessage,
            String systemPrompt,
            Double temperatureOverride,
            List<ChatTurn> history
    ) {
        if (!StringUtils.hasText(userMessage)) {
            throw new AiServiceException("Message cannot be empty");
        }

        log.debug("Generating Ollama chat response for message length: {}", userMessage.length());

        try {
            ChatClient.ChatClientRequestSpec request = ollamaChatClient.prompt();
            if (StringUtils.hasText(systemPrompt)) {
                request = request.system(systemPrompt);
            }
            List<Message> earlierMessages = toMessages(history);
            if (!earlierMessages.isEmpty()) {
                request = request.messages(earlierMessages);
            }

            String responseContent = request
                    .user(userMessage)
                    .options(buildChatOptions(temperatureOverride))
                    .call()
                    .content();

            return ChatResponse.builder()
                    .response(responseContent)
                    .model(ollamaAiProperties.getChatModel())
                    .build();
        } catch (Exception ex) {
            throw new AiServiceException("Failed to generate response from Ollama: " + ex.getMessage(), ex);
        }
    }

    private List<Message> toMessages(List<ChatTurn> history) {
        if (history == null) {
            return List.of();
        }
        return history.stream()
                .filter(turn -> StringUtils.hasText(turn.getContent()))
                .map(turn -> turn.isUser()
                        ? (Message) new UserMessage(turn.getContent())
                        : new AssistantMessage(turn.getContent()))
                .toList();
    }

    private OllamaOptions buildChatOptions(Double temperatureOverride) {
        double temperature = temperatureOverride != null
                ? temperatureOverride
                : ollamaAiProperties.getTemperature();
        return OllamaOptions.builder()
                .model(ollamaAiProperties.getChatModel())
                .temperature(temperature)
                .numCtx(ollamaAiProperties.getNumCtx())
                .build();
    }
}
