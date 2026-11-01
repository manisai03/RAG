package com.aiinterview.assistant.controller;

import com.aiinterview.assistant.dto.DocumentIndexResponse;
import com.aiinterview.assistant.dto.RagAnswerResponse;
import com.aiinterview.assistant.dto.RagQuestionRequest;
import com.aiinterview.assistant.dto.VectorSearchRequest;
import com.aiinterview.assistant.dto.VectorSearchResponse;
import com.aiinterview.assistant.entity.User;
import com.aiinterview.assistant.dto.DocumentResponse;
import com.aiinterview.assistant.service.DocumentService;
import com.aiinterview.assistant.service.DocumentVectorStoreService;
import com.aiinterview.assistant.service.RetrievalAugmentedGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class RagController {

    private final DocumentVectorStoreService documentVectorStoreService;
    private final RetrievalAugmentedGenerationService retrievalAugmentedGenerationService;
    private final DocumentService documentService;

    @PostMapping("/ask")
    public ResponseEntity<RagAnswerResponse> ask(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody RagQuestionRequest request
    ) {
        return ResponseEntity.ok(retrievalAugmentedGenerationService.ask(
                user,
                request.getQuestion(),
                request.getDocumentId()
        ));
    }

    @PostMapping("/documents/latest/questions")
    public ResponseEntity<RagAnswerResponse> questionsFromLatestDocument(@AuthenticationPrincipal User user) {
        DocumentResponse latest = documentService.getLatestDocument(user);
        return ResponseEntity.ok(retrievalAugmentedGenerationService.generatePracticeQuestions(user, latest.getId()));
    }

    @PostMapping("/documents/{documentId}/questions")
    public ResponseEntity<RagAnswerResponse> questionsFromDocument(
            @AuthenticationPrincipal User user,
            @PathVariable Long documentId
    ) {
        documentService.getDocument(user, documentId);
        return ResponseEntity.ok(retrievalAugmentedGenerationService.generatePracticeQuestions(user, documentId));
    }

    @PostMapping("/documents/{documentId}/index")
    public ResponseEntity<DocumentIndexResponse> indexDocument(
            @AuthenticationPrincipal User user,
            @PathVariable Long documentId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentVectorStoreService.indexDocument(user, documentId));
    }

    @PostMapping("/search")
    public ResponseEntity<VectorSearchResponse> search(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody VectorSearchRequest request
    ) {
        return ResponseEntity.ok(documentVectorStoreService.searchChunks(
                user,
                request.getQuery(),
                request.getDocumentId()
        ));
    }
}
