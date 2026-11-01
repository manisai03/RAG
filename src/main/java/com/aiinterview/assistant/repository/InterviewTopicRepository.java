package com.aiinterview.assistant.repository;

import com.aiinterview.assistant.entity.InterviewTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InterviewTopicRepository extends JpaRepository<InterviewTopic, Long> {

    Optional<InterviewTopic> findByName(String name);

    boolean existsByName(String name);
}
