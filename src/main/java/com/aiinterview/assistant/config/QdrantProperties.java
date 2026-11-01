package com.aiinterview.assistant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.qdrant")
public class QdrantProperties {

    private int topK = 8;

    /** How many chunks to fetch from Qdrant before re-ranking (should be >= topK). */
    private int retrievalTopK = 30;

    private double similarityThreshold = 0.0;

    /** Share of the re-rank score that comes from question words found in the chunk (the rest is vector similarity). */
    private double keywordWeight = 0.3;

    /** Below this best vector score, the PDF is treated as not containing the answer. */
    private double minRelevanceScore = 0.0;

    /** At or above this best vector score, the PDF counts as relevant even without exact keyword matches. */
    private double strongRelevanceScore = 1.0;

    /** Number of chunks sent to Qdrant per batch during indexing. */
    private int indexBatchSize = 25;

    public static final String METADATA_DOCUMENT_ID = "documentId";

    public static final String METADATA_USER_ID = "userId";

    public static final String METADATA_CHUNK_INDEX = "chunkIndex";

    public static final String METADATA_FILE_NAME = "fileName";

    public static final String METADATA_PAGE_NUMBER = "pageNumber";
}
