package com.aiinterview.assistant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    private String systemPrompt = """
            You are a document-grounded assistant. The document excerpts you are given are the only source of truth.
            The document may be newer than your training data; when they disagree, follow the document.
            Keep names, numbers and terms exactly as written, and cite excerpts as [Source N].
            If the excerpts do not contain the answer, say you do not have enough information.
            """;

    /** Lower temperature for RAG answers (more faithful to retrieved text). */
    private double temperature = 0.1;

    private String noContextAnswer = """
            I could not find relevant information in your indexed documents to answer this question.
            Please upload a PDF and index it first using POST /api/rag/documents/{documentId}/index.
            """;

    /** When true, Chat uses the general LLM if the PDF has no relevant text for the question. */
    private boolean fallbackToGeneral = true;

    private String generalFallbackPreamble = """
            I could not find information about this in your selected PDF (%s).
            Here is a general interview answer from the AI model:
            
            """;

    private int maxContextCharacters = 8000;

    /** Upper bound on chunks loaded for whole-document summaries and practice questions. */
    private int fullDocumentMaxChunks = 200;

    /** Characters of document text summarized per model call in a whole-document summary. */
    private int summaryBatchCharacters = 10000;

    /** Longer documents are sampled evenly so a summary needs at most this many batch calls (plus one merge). */
    private int summaryMaxBatches = 4;

    /** Max characters sent to the model per Theme I subsection. */
    private int sectionSummaryMaxContextCharacters = 8000;

    /** Earlier chat messages used to understand follow-up questions. */
    private int historyMaxTurns = 4;

    private int historyTurnMaxCharacters = 600;

    private String questionGenerationPrompt = """
            Using ONLY the document excerpts below, generate 10 interview practice questions
            that a candidate could study from this material.
            Rules:
            - Number the list 1 to 10.
            - Each question must be answerable from the document text provided.
            - Cover different parts of the document; use wording aligned with it and do not invent topics.
            - Do not provide answers.
            """;
}
