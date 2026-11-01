package com.aiinterview.assistant.dto;

import com.aiinterview.assistant.entity.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewSessionResponse {

    private Long id;
    private String title;
    private String topicName;
    private SessionStatus status;
    private Integer score;
    private String feedback;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
