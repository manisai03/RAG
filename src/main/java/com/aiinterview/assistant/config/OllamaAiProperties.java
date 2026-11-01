package com.aiinterview.assistant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai")
public class OllamaAiProperties {

    private String systemPrompt = """
            You are an AI Interview Preparation Assistant.
            Provide clear, accurate, and practical guidance for technical and behavioral interviews.
            Use concise explanations and actionable advice.
            """;

    private String chatModel = "llama3.2";

    private String embeddingModel = "nomic-embed-text";

    /** nomic-embed-text is trained with these task prefixes; leave empty for models that do not use them. */
    private String embeddingDocumentPrefix = "search_document: ";

    private String embeddingQueryPrefix = "search_query: ";

    private double temperature = 0.7;

    /** Ollama context window in tokens. Prompts longer than this are silently cut from the start. */
    private int numCtx = 8192;
}
