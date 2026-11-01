package com.aiinterview.assistant.repository;

import com.aiinterview.assistant.entity.DifficultyLevel;
import com.aiinterview.assistant.entity.InterviewQuestion;
import com.aiinterview.assistant.entity.InterviewTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {

    List<InterviewQuestion> findByTopic(InterviewTopic topic);

    List<InterviewQuestion> findByTopicAndDifficulty(InterviewTopic topic, DifficultyLevel difficulty);
}
