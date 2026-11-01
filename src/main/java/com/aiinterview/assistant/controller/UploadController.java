package com.aiinterview.assistant.controller;

import com.aiinterview.assistant.dto.DocumentResponse;
import com.aiinterview.assistant.dto.PdfExtractionResponse;
import com.aiinterview.assistant.entity.User;
import com.aiinterview.assistant.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class UploadController {

    private final DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.uploadDocument(user, file, description));
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getUserDocuments(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(documentService.getUserDocuments(user));
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentResponse> getDocument(
            @AuthenticationPrincipal User user,
            @PathVariable Long documentId
    ) {
        return ResponseEntity.ok(documentService.getDocument(user, documentId));
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<Resource> downloadDocument(
            @AuthenticationPrincipal User user,
            @PathVariable Long documentId
    ) {
        Resource resource = documentService.downloadDocument(user, documentId);
        String fileName = documentService.getOriginalFileName(user, documentId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    @GetMapping("/{documentId}/extract")
    public ResponseEntity<PdfExtractionResponse> extractDocumentText(
            @AuthenticationPrincipal User user,
            @PathVariable Long documentId
    ) {
        return ResponseEntity.ok(documentService.extractDocumentText(user, documentId));
    }

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PdfExtractionResponse> extractUploadedPdf(
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(documentService.extractUploadedPdfText(file));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @AuthenticationPrincipal User user,
            @PathVariable Long documentId
    ) {
        documentService.deleteDocument(user, documentId);
        return ResponseEntity.noContent().build();
    }
}
