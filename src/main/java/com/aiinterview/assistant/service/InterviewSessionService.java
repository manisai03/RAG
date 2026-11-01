package com.aiinterview.assistant.service;

import com.aiinterview.assistant.dto.InterviewSessionRequest;
import com.aiinterview.assistant.dto.InterviewSessionResponse;
import com.aiinterview.assistant.dto.UserAnswerRequest;
import com.aiinterview.assistant.dto.UserAnswerResponse;
import com.aiinterview.assistant.entity.*;
import com.aiinterview.assistant.exception.BadRequestException;
import com.aiinterview.assistant.exception.ResourceNotFoundException;
import com.aiinterview.assistant.repository.InterviewSessionRepository;
import com.aiinterview.assistant.repository.InterviewTopicRepository;
import com.aiinterview.assistant.repository.UserAnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class InterviewSessionService {

    private final InterviewSessionRepository sessionRepository;
    private final InterviewTopicRepository topicRepository;
    private final UserAnswerRepository userAnswerRepository;
    private final InterviewQuestionService questionService;

    public InterviewSessionResponse createSession(User user, InterviewSessionRequest request) {
        InterviewTopic topic = null;
        if (request.getTopicId() != null) {
            topic = topicRepository.findById(request.getTopicId())
                    .orElseThrow(() -> new ResourceNotFoundException("Topic not found: " + request.getTopicId()));
        }

        InterviewSession session = InterviewSession.builder()
                .user(user)
                .topic(topic)
                .title(request.getTitle())
                .status(SessionStatus.IN_PROGRESS)
                .build();

        return toResponse(sessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public List<InterviewSessionResponse> getUserSessions(User user) {
        return sessionRepository.findByUser(user).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InterviewSessionResponse getSession(User user, Long sessionId) {
        return toResponse(getOwnedSession(user, sessionId));
    }

    public UserAnswerResponse submitAnswer(User user, Long sessionId, UserAnswerRequest request) {
        InterviewSession session = getOwnedSession(user, sessionId);

        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new BadRequestException("Cannot submit answers to a completed session");
        }

        InterviewQuestion question = questionService.getQuestionEntity(request.getQuestionId());

        UserAnswer answer = UserAnswer.builder()
                .session(session)
                .question(question)
                .answerText(request.getAnswerText())
                .rating(calculateRating(request.getAnswerText()))
                .aiFeedback(generateFeedback(request.getAnswerText()))
                .build();

        UserAnswer savedAnswer = userAnswerRepository.save(answer);
        session.getAnswers().add(savedAnswer);

        return toAnswerResponse(savedAnswer);
    }

    public InterviewSessionResponse completeSession(User user, Long sessionId) {
        InterviewSession session = getOwnedSession(user, sessionId);

        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new BadRequestException("Session is already completed");
        }

        double averageScore = session.getAnswers().stream()
                .map(UserAnswer::getRating)
                .filter(rating -> rating != null)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        session.setScore((int) Math.round(averageScore));
        session.setFeedback("Session completed with an average score of " + session.getScore());

        return toResponse(sessionRepository.save(session));
    }

    private InterviewSession getOwnedSession(User user, Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Session not found: " + sessionId);
        }

        return session;
    }

    private int calculateRating(String answerText) {
        int lengthScore = Math.min(answerText.trim().length() / 20, 5);
        return Math.max(lengthScore, 1);
    }

    private String generateFeedback(String answerText) {
        if (answerText.trim().length() < 50) {
            return "Try to provide more detail and concrete examples in your answer.";
        }
        return "Good effort. Consider structuring your answer with context, action, and result.";
    }

    private InterviewSessionResponse toResponse(InterviewSession session) {
        return InterviewSessionResponse.builder()
                .id(session.getId())
                .title(session.getTitle())
                .topicName(session.getTopic() != null ? session.getTopic().getName() : null)
                .status(session.getStatus())
                .score(session.getScore())
                .feedback(session.getFeedback())
                .startedAt(session.getStartedAt())
                .completedAt(session.getCompletedAt())
                .build();
    }

    private UserAnswerResponse toAnswerResponse(UserAnswer answer) {
        return UserAnswerResponse.builder()
                .id(answer.getId())
                .questionId(answer.getQuestion().getId())
                .answerText(answer.getAnswerText())
                .rating(answer.getRating())
                .aiFeedback(answer.getAiFeedback())
                .submittedAt(answer.getSubmittedAt())
                .build();
    }
}
