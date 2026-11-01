package com.aiinterview.assistant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.chunking")
public class TextChunkingProperties {

    private int chunkSize = 1000;

    /** Characters repeated from the end of one chunk at the start of the next, so boundary sentences keep context. */
    private int chunkOverlap = 150;

    /** Reject indexing when chunk count exceeds this (protects Ollama/Qdrant and request time). */
    private int maxChunksPerDocument = 2000;
}
