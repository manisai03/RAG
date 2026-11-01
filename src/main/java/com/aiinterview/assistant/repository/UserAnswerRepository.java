package com.aiinterview.assistant.repository;

import com.aiinterview.assistant.entity.InterviewSession;
import com.aiinterview.assistant.entity.UserAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserAnswerRepository extends JpaRepository<UserAnswer, Long> {

    List<UserAnswer> findBySession(InterviewSession session);
}
