package com.aiinterview.assistant.dto;

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
public class ChatAskResponse {

    private String answer;

    private Long documentId;

    private String documentFileName;

    /** True when answering with general AI (no PDF / RAG). */
    private boolean generalChat;

    /** True when RAG had no match but general knowledge was used instead. */
    private boolean ragFallback;

    /** PDF passages the answer is based on; empty for general answers. */
    @Builder.Default
    private List<SourceReference> sources = new ArrayList<>();
}
