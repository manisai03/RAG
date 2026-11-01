package com.aiinterview.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagAnswerResponse {

    private String question;
    private String answer;
    private String model;
    private int chunksRetrieved;
    private int topK;
    private List<ChunkSearchResult> sources;

    /** False when PDF had no usable chunks; caller may answer from general model knowledge. */
    @Builder.Default
    private boolean documentGrounded = true;
}
