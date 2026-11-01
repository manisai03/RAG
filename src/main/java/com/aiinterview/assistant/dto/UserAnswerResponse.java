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
public class UserAnswerResponse {

    private Long id;
    private Long questionId;
    private String answerText;
    private Integer rating;
    private String aiFeedback;
    private LocalDateTime submittedAt;
}
