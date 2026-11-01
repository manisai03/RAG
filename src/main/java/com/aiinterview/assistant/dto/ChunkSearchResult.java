package com.aiinterview.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChunkSearchResult {

    private String id;
    private String content;
    private Double score;
    private Long documentId;
    private Integer chunkIndex;
    private Integer pageNumber;
    private String fileName;
    private Map<String, Object> metadata;
}
