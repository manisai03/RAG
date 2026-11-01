package com.aiinterview.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatAskRequest {

    @NotBlank
    private String question;

    /** When null, the latest uploaded document is used for RAG. */
    private Long documentId;

    /**
     * When false, use general interview AI (Ollama) without PDF context.
     * When null or true, use RAG if the user has uploaded documents; otherwise general chat.
     */
    private Boolean useDocuments;

    /** Earlier messages of this chat, oldest first; only the most recent few are used. */
    @Builder.Default
    private List<ChatTurn> history = new ArrayList<>();
}
