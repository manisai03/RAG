package com.aiinterview.assistant.service;

import com.aiinterview.assistant.dto.PdfExtractionResponse;
import com.aiinterview.assistant.exception.PdfProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PdfTextExtractionService {

    public PdfExtractionResponse extractFromPath(Path pdfPath, String fileName) {
        validatePdfPath(pdfPath);

        log.info("Extracting text from PDF: {}", pdfPath.getFileName());

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            return buildResponse(document, fileName, null);
        } catch (IOException ex) {
            throw new PdfProcessingException("Failed to read PDF file: " + fileName, ex);
        }
    }

    public PdfExtractionResponse extractFromMultipart(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new PdfProcessingException("PDF file is empty or missing");
        }

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded.pdf";
        log.info("Extracting text from uploaded PDF: {}", fileName);

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            return buildResponse(document, fileName, null);
        } catch (IOException ex) {
            throw new PdfProcessingException("Failed to extract text from uploaded PDF: " + fileName, ex);
        }
    }

    public PdfExtractionResponse extractFromPath(Path pdfPath, String fileName, Long documentId) {
        PdfExtractionResponse response = extractFromPath(pdfPath, fileName);
        response.setDocumentId(documentId);
        return response;
    }

    /**
     * Extracts text page by page (index 0 = page 1), keeping line breaks so headings and lists survive chunking.
     */
    public List<String> extractPages(Path pdfPath, String fileName) {
        validatePdfPath(pdfPath);

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            if (document.isEncrypted()) {
                throw new PdfProcessingException("Cannot extract text from password-protected PDF: " + fileName);
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<String> pages = new ArrayList<>(document.getNumberOfPages());
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(normalizeExtractedText(stripper.getText(document)));
            }
            log.debug("Extracted {} pages from {}", pages.size(), fileName);
            return pages;
        } catch (IOException ex) {
            throw new PdfProcessingException("Failed to read PDF file: " + fileName, ex);
        }
    }

    private PdfExtractionResponse buildResponse(PDDocument document, String fileName, Long documentId)
            throws IOException {
        if (document.isEncrypted()) {
            throw new PdfProcessingException("Cannot extract text from password-protected PDF: " + fileName);
        }

        int pageCount = document.getNumberOfPages();
        if (pageCount == 0) {
            throw new PdfProcessingException("PDF contains no pages: " + fileName);
        }

        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        String rawContent = stripper.getText(document);
        String normalizedContent = normalizeExtractedText(rawContent);

        log.debug("Extracted {} characters from {} pages in {}", normalizedContent.length(), pageCount, fileName);

        return PdfExtractionResponse.builder()
                .documentId(documentId)
                .fileName(fileName)
                .pageCount(pageCount)
                .characterCount(normalizedContent.length())
                .content(normalizedContent)
                .extractedAt(LocalDateTime.now())
                .build();
    }

    private void validatePdfPath(Path pdfPath) {
        if (pdfPath == null || !Files.exists(pdfPath)) {
            throw new PdfProcessingException("PDF file not found at path: " + pdfPath);
        }

        if (!Files.isReadable(pdfPath)) {
            throw new PdfProcessingException("PDF file is not readable: " + pdfPath);
        }

        if (!pdfPath.getFileName().toString().toLowerCase().endsWith(".pdf")) {
            throw new PdfProcessingException("File is not a PDF: " + pdfPath.getFileName());
        }
    }

    private String normalizeExtractedText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        return text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t\\f\\u00A0]+", " ")
                .replaceAll("(?m)^[ \\t]+", "")
                .replaceAll("(?m)[ \\t]+$", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
