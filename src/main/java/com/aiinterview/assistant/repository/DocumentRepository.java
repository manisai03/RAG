package com.aiinterview.assistant.repository;

import com.aiinterview.assistant.entity.Document;
import com.aiinterview.assistant.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByUploadedByOrderByUploadedAtDesc(User uploadedBy);

    Optional<Document> findByIdAndUploadedBy(Long id, User uploadedBy);

    Optional<Document> findFirstByUploadedByOrderByUploadedAtDesc(User uploadedBy);
}
