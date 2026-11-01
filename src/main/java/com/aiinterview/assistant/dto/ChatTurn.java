package com.aiinterview.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One earlier message in the chat, sent by the client so follow-up questions have context. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatTurn {

    /** "user" or "assistant". */
    private String role;

    private String content;

    public boolean isUser() {
        return "user".equalsIgnoreCase(role);
    }
}
