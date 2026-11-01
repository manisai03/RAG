package com.aiinterview.assistant.service;

import com.aiinterview.assistant.config.StorageProperties;
import com.aiinterview.assistant.dto.DocumentResponse;
import com.aiinterview.assistant.dto.PdfExtractionResponse;
import com.aiinterview.assistant.entity.Document;
import com.aiinterview.assistant.entity.User;
import com.aiinterview.assistant.exception.BadRequestException;
import com.aiinterview.assistant.exception.ResourceNotFoundException;
import com.aiinterview.assistant.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class DocumentService {

    private static final String PDF_EXTENSION = ".pdf";

    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final PdfTextExtractionService pdfTextExtractionService;
    private final DocumentVectorStoreService documentVectorStoreService;
    private final StorageProperties storageProperties;

    public DocumentResponse uploadDocument(User user, MultipartFile file, String description) {
        validatePdfFile(file);

        String relativePath = fileStorageService.store(file, user.getId());
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String storedFileName = relativePath.substring(relativePath.lastIndexOf('/') + 1);

        Document document = Document.builder()
                .originalFileName(originalFileName)
                .storedFileName(storedFileName)
                .filePath(relativePath)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .description(description)
                .uploadedBy(user)
                .build();

        return toResponse(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public DocumentResponse getLatestDocument(User user) {
        return findLatestDocument(user)
                .orElseThrow(() -> new ResourceNotFoundException("No documents uploaded yet"));
    }

    @Transactional(readOnly = true)
    public Optional<DocumentResponse> findLatestDocument(User user) {
        return documentRepository.findFirstByUploadedByOrderByUploadedAtDesc(user)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getUserDocuments(User user) {
        return documentRepository.findByUploadedByOrderByUploadedAtDesc(user).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(User user, Long documentId) {
        return toResponse(getOwnedDocument(user, documentId));
    }

    @Transactional(readOnly = true)
    public Resource downloadDocument(User user, Long documentId) {
        Document document = getOwnedDocument(user, documentId);
        return fileStorageService.loadAsResource(document.getFilePath());
    }

    @Transactional(readOnly = true)
    public String getOriginalFileName(User user, Long documentId) {
        return getOwnedDocument(user, documentId).getOriginalFileName();
    }

    @Transactional(readOnly = true)
    public PdfExtractionResponse extractDocumentText(User user, Long documentId) {
        Document document = getOwnedDocument(user, documentId);
        Path pdfPath = fileStorageService.resolvePath(document.getFilePath());

        return pdfTextExtractionService.extractFromPath(
                pdfPath,
                document.getOriginalFileName(),
                document.getId()
        );
    }

    @Transactional(readOnly = true)
    public PdfExtractionResponse extractUploadedPdfText(MultipartFile file) {
        validatePdfFile(file);
        return pdfTextExtractionService.extractFromMultipart(file);
    }

    public void deleteDocument(User user, Long documentId) {
        Document document = getOwnedDocument(user, documentId);
        String relativePath = document.getFilePath();
        documentRepository.delete(document);
        documentRepository.flush();
        fileStorageService.delete(relativePath);
        documentVectorStoreService.deleteDocumentEmbeddings(documentId);
    }

    private Document getOwnedDocument(User user, Long documentId) {
        return documentRepository.findByIdAndUploadedBy(documentId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
    }

    private void validatePdfFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please select a PDF file to upload");
        }

        if (file.getSize() > storageProperties.getMaxFileSizeBytes()) {
            throw new BadRequestException("File size exceeds the maximum allowed limit of "
                    + (storageProperties.getMaxFileSizeBytes() / (1024 * 1024)) + " MB");
        }

        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        if (!originalFileName.toLowerCase().endsWith(PDF_EXTENSION)) {
            throw new BadRequestException("Only PDF files are allowed");
        }

        String contentType = file.getContentType();
        if (contentType != null
                && !contentType.equalsIgnoreCase(storageProperties.getAllowedContentType())
                && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            throw new BadRequestException("Invalid file type. Only PDF files are allowed");
        }
    }

    private DocumentResponse toResponse(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .originalFileName(document.getOriginalFileName())
                .contentType(document.getContentType())
                .fileSize(document.getFileSize())
                .description(document.getDescription())
                .uploadedByEmail(document.getUploadedBy().getEmail())
                .uploadedAt(document.getUploadedAt())
                .build();
    }
}
