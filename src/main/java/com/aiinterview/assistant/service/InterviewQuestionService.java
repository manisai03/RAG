package com.aiinterview.assistant.service;

import com.aiinterview.assistant.dto.InterviewQuestionResponse;
import com.aiinterview.assistant.entity.InterviewQuestion;
import com.aiinterview.assistant.entity.InterviewTopic;
import com.aiinterview.assistant.exception.ResourceNotFoundException;
import com.aiinterview.assistant.repository.InterviewQuestionRepository;
import com.aiinterview.assistant.repository.InterviewTopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewQuestionService {

    private final InterviewQuestionRepository questionRepository;
    private final InterviewTopicRepository topicRepository;

    public List<InterviewQuestionResponse> getQuestionsByTopic(Long topicId) {
        InterviewTopic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic not found: " + topicId));

        return questionRepository.findByTopic(topic).stream()
                .map(this::toResponse)
                .toList();
    }

    public InterviewQuestionResponse getQuestionById(Long questionId) {
        InterviewQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));
        return toResponse(question);
    }

    InterviewQuestion getQuestionEntity(Long questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));
    }

    private InterviewQuestionResponse toResponse(InterviewQuestion question) {
        return InterviewQuestionResponse.builder()
                .id(question.getId())
                .questionText(question.getQuestionText())
                .difficulty(question.getDifficulty())
                .topicName(question.getTopic().getName())
                .build();
    }
}
