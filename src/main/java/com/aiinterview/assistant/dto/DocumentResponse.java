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
public class DocumentResponse {

    private Long id;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String description;
    private String uploadedByEmail;
    private LocalDateTime uploadedAt;
}
