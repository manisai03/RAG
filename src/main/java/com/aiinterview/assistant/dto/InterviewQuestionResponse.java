package com.aiinterview.assistant.dto;

import com.aiinterview.assistant.entity.DifficultyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewQuestionResponse {

    private Long id;
    private String questionText;
    private DifficultyLevel difficulty;
    private String topicName;
}
