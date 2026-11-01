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
public class VectorSearchResponse {

    private String query;
    private int topK;
    private int resultsCount;
    private List<ChunkSearchResult> chunks;
}
