package com.aiinterview.assistant.repository;

import com.aiinterview.assistant.entity.InterviewSession;
import com.aiinterview.assistant.entity.SessionStatus;
import com.aiinterview.assistant.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    List<InterviewSession> findByUser(User user);

    List<InterviewSession> findByUserAndStatus(User user, SessionStatus status);
}
