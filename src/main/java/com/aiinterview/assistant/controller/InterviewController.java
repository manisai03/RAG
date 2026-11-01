package com.aiinterview.assistant.controller;

import com.aiinterview.assistant.dto.InterviewQuestionResponse;
import com.aiinterview.assistant.dto.InterviewSessionRequest;
import com.aiinterview.assistant.dto.InterviewSessionResponse;
import com.aiinterview.assistant.dto.UserAnswerRequest;
import com.aiinterview.assistant.dto.UserAnswerResponse;
import com.aiinterview.assistant.entity.User;
import com.aiinterview.assistant.service.InterviewQuestionService;
import com.aiinterview.assistant.service.InterviewSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewSessionService sessionService;
    private final InterviewQuestionService questionService;

    @PostMapping("/sessions")
    public ResponseEntity<InterviewSessionResponse> createSession(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody InterviewSessionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.createSession(user, request));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<InterviewSessionResponse>> getSessions(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(sessionService.getUserSessions(user));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<InterviewSessionResponse> getSession(
            @AuthenticationPrincipal User user,
            @PathVariable Long sessionId
    ) {
        return ResponseEntity.ok(sessionService.getSession(user, sessionId));
    }

    @PostMapping("/sessions/{sessionId}/answers")
    public ResponseEntity<UserAnswerResponse> submitAnswer(
            @AuthenticationPrincipal User user,
            @PathVariable Long sessionId,
            @Valid @RequestBody UserAnswerRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.submitAnswer(user, sessionId, request));
    }

    @PostMapping("/sessions/{sessionId}/complete")
    public ResponseEntity<InterviewSessionResponse> completeSession(
            @AuthenticationPrincipal User user,
            @PathVariable Long sessionId
    ) {
        return ResponseEntity.ok(sessionService.completeSession(user, sessionId));
    }

    @GetMapping("/topics/{topicId}/questions")
    public ResponseEntity<List<InterviewQuestionResponse>> getQuestionsByTopic(@PathVariable Long topicId) {
        return ResponseEntity.ok(questionService.getQuestionsByTopic(topicId));
    }
}
