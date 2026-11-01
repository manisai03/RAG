package com.aiinterview.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfExtractionResponse {

    private Long documentId;
    private String fileName;
    private int pageCount;
    private int characterCount;
    private String content;
    private LocalDateTime extractedAt;
}
