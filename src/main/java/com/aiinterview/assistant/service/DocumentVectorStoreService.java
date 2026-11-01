package com.aiinterview.assistant.service;

import com.aiinterview.assistant.config.QdrantProperties;
import com.aiinterview.assistant.config.RagProperties;
import com.aiinterview.assistant.config.TextChunkingProperties;
import com.aiinterview.assistant.dto.ChunkSearchResult;
import com.aiinterview.assistant.dto.DocumentIndexResponse;
import com.aiinterview.assistant.dto.PdfExtractionResponse;
import com.aiinterview.assistant.dto.VectorSearchResponse;
import com.aiinterview.assistant.entity.User;
import com.aiinterview.assistant.exception.BadRequestException;
import com.aiinterview.assistant.exception.ResourceNotFoundException;
import com.aiinterview.assistant.exception.VectorStoreException;
import com.aiinterview.assistant.repository.DocumentRepository;
import com.aiinterview.assistant.service.TextChunkingService.TextChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentVectorStoreService {

    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;
    private final PdfTextExtractionService pdfTextExtractionService;
    private final TextChunkingService textChunkingService;
    private final FileStorageService fileStorageService;
    private final QdrantProperties qdrantProperties;
    private final RagProperties ragProperties;
    private final TextChunkingProperties textChunkingProperties;
    private final SyllabusThemeTextService syllabusThemeTextService;

    public DocumentIndexResponse indexDocument(User user, Long documentId) {
        com.aiinterview.assistant.entity.Document document = getOwnedDocument(user, documentId);

        List<TextChunk> chunks = textChunkingService.chunkPages(extractPages(document));
        if (chunks.isEmpty()) {
            throw new BadRequestException("No text content found in document to index");
        }

        int maxChunks = textChunkingProperties.getMaxChunksPerDocument();
        if (chunks.size() > maxChunks) {
            throw new BadRequestException(
                    "This PDF is too large to index (%d text chunks). Maximum is %d. "
                            + "Use a shorter document or split the PDF into smaller files."
                            .formatted(chunks.size(), maxChunks));
        }

        log.info("Indexing document {}: {} chunks (this may take several minutes)", documentId, chunks.size());

        deleteDocumentEmbeddings(documentId);
        int indexedCount = storeChunks(document, user.getId(), chunks);

        log.info("Indexed {} chunks for document {} in Qdrant", indexedCount, documentId);

        return DocumentIndexResponse.builder()
                .documentId(documentId)
                .fileName(document.getOriginalFileName())
                .chunksIndexed(indexedCount)
                .message("Document embeddings stored successfully in Qdrant")
                .build();
    }

    public int storeChunks(com.aiinterview.assistant.entity.Document document, Long userId, List<TextChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new BadRequestException("Chunks cannot be empty");
        }

        List<Document> aiDocuments = new ArrayList<>();

        for (int index = 0; index < chunks.size(); index++) {
            TextChunk chunk = chunks.get(index);
            String chunkId = buildChunkId(document.getId(), index);
            Map<String, Object> metadata = buildChunkMetadata(document, userId, index, chunk.pageNumber());
            aiDocuments.add(new Document(chunkId, chunk.text(), metadata));
        }

        int batchSize = Math.max(1, qdrantProperties.getIndexBatchSize());
        int stored = 0;

        try {
            for (int offset = 0; offset < aiDocuments.size(); offset += batchSize) {
                int end = Math.min(offset + batchSize, aiDocuments.size());
                List<Document> batch = aiDocuments.subList(offset, end);
                vectorStore.add(batch);
                stored += batch.size();
                log.debug("Stored embedding batch {}-{} of {}", offset + 1, end, aiDocuments.size());
            }
            return stored;
        } catch (Exception ex) {
            log.error("Failed to store document embeddings in Qdrant after {} of {} chunks",
                    stored, aiDocuments.size(), ex);
            throw new VectorStoreException("Failed to store document embeddings in Qdrant", ex);
        }
    }

    @Transactional(readOnly = true)
    public VectorSearchResponse searchChunks(User user, String query) {
        return searchChunks(user, query, null);
    }

    @Transactional(readOnly = true)
    public VectorSearchResponse searchChunks(User user, String query, Long documentId) {
        if (!StringUtils.hasText(query)) {
            throw new BadRequestException("Search query cannot be empty");
        }

        if (documentId != null) {
            getOwnedDocument(user, documentId);
        }

        try {
            String filterExpression = buildFilterExpression(user.getId(), documentId);
            Map<String, ChunkSearchResult> merged = new LinkedHashMap<>();

            for (String searchQuery : buildSearchQueries(query)) {
                for (Document doc : similaritySearch(searchQuery, filterExpression)) {
                    ChunkSearchResult chunk = toChunkSearchResult(doc);
                    merged.merge(chunk.getId(), chunk, (existing, candidate) ->
                            score(candidate) > score(existing) ? candidate : existing);
                }
            }

            List<ChunkSearchResult> chunks = rerank(new ArrayList<>(merged.values()), query).stream()
                    .limit(qdrantProperties.getTopK())
                    .toList();

            log.debug("RAG search for '{}': {} chunks after merge and re-rank (documentId={}, best score={})",
                    truncate(query, 60), chunks.size(), documentId,
                    chunks.isEmpty() ? "-" : String.format("%.3f", score(chunks.getFirst())));

            return VectorSearchResponse.builder()
                    .query(query)
                    .topK(qdrantProperties.getTopK())
                    .resultsCount(chunks.size())
                    .chunks(chunks)
                    .build();
        } catch (VectorStoreException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new VectorStoreException("Failed to search embeddings in Qdrant", ex);
        }
    }

    public void deleteDocumentEmbeddings(Long documentId) {
        try {
            FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
            vectorStore.delete(filterBuilder.eq(QdrantProperties.METADATA_DOCUMENT_ID, documentId.toString()).build());
            log.debug("Deleted Qdrant embeddings for document {}", documentId);
        } catch (Exception ex) {
            log.warn("Could not delete existing embeddings for document {}: {}", documentId, ex.getMessage());
        }
    }

    /**
     * All chunks of a document in reading order, for whole-document summaries where top-K search would
     * miss sections. When {@code themeOnly} is set and the PDF has syllabus theme markers, only that theme's
     * text is used.
     */
    @Transactional(readOnly = true)
    public Optional<List<ChunkSearchResult>> loadAllDocumentChunks(
            User user,
            Long documentId,
            boolean themeOnly,
            String query
    ) {
        com.aiinterview.assistant.entity.Document document = getOwnedDocument(user, documentId);
        List<String> pages = extractPages(document);

        List<TextChunk> chunks = textChunkingService.chunkPages(pages);
        if (themeOnly) {
            String fullText = String.join("\n\n", pages);
            String themeText = syllabusThemeTextService.narrowToTheme(fullText, query);
            if (StringUtils.hasText(themeText) && themeText.length() < fullText.length()) {
                log.info("Narrowed document {} from {} to {} characters for theme summary",
                        documentId, fullText.length(), themeText.length());
                chunks = textChunkingService.chunkPages(List.of(themeText)).stream()
                        .map(chunk -> new TextChunk(chunk.text(), null))
                        .toList();
            }
        }

        if (chunks.isEmpty()) {
            return Optional.empty();
        }

        int maxChunks = ragProperties.getFullDocumentMaxChunks();
        if (chunks.size() > maxChunks) {
            log.info("Document {} has {} chunks; summarizing the first {}", documentId, chunks.size(), maxChunks);
            chunks = chunks.subList(0, maxChunks);
        }

        List<ChunkSearchResult> results = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            results.add(ChunkSearchResult.builder()
                    .id(buildChunkId(documentId, index))
                    .content(chunks.get(index).text())
                    .pageNumber(chunks.get(index).pageNumber())
                    .documentId(documentId)
                    .chunkIndex(index)
                    .fileName(document.getOriginalFileName())
                    .build());
        }
        return Optional.of(results);
    }

    /**
     * Theme I text grouped by syllabus sub-heading (maps, lithosphere, etc.). Empty unless the PDF actually
     * contains at least three of those headings.
     */
    @Transactional(readOnly = true)
    public Optional<Map<String, List<ChunkSearchResult>>> loadThemeOneSections(
            User user,
            Long documentId,
            String query
    ) {
        if (!syllabusThemeTextService.isThemeOneQuery(query)) {
            return Optional.empty();
        }

        com.aiinterview.assistant.entity.Document document = getOwnedDocument(user, documentId);
        PdfExtractionResponse extraction = pdfTextExtractionService.extractFromPath(
                fileStorageService.resolvePath(document.getFilePath()),
                document.getOriginalFileName(),
                document.getId()
        );

        if (!StringUtils.hasText(extraction.getContent())) {
            return Optional.empty();
        }

        String themeText = syllabusThemeTextService.narrowToTheme(extraction.getContent(), query);
        Map<String, String> sectionTexts = syllabusThemeTextService.splitThemeOneIntoSections(themeText);
        if (sectionTexts.size() < 3) {
            log.info("Theme I headings not found in document {} ({} sections)", documentId, sectionTexts.size());
            return Optional.empty();
        }

        log.info("Theme I split into {} syllabus sections for document {}", sectionTexts.size(), documentId);

        Map<String, List<ChunkSearchResult>> sectionChunks = new LinkedHashMap<>();
        int sectionIndex = 0;
        for (Map.Entry<String, String> entry : sectionTexts.entrySet()) {
            List<String> chunks = textChunkingService.chunkText(entry.getValue());
            List<ChunkSearchResult> results = new ArrayList<>(chunks.size());
            for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
                results.add(ChunkSearchResult.builder()
                        .id(buildChunkId(documentId, sectionIndex * 1000 + chunkIndex))
                        .content(chunks.get(chunkIndex))
                        .documentId(documentId)
                        .chunkIndex(sectionIndex * 1000 + chunkIndex)
                        .fileName(document.getOriginalFileName())
                        .build());
            }
            sectionChunks.put(entry.getKey(), results);
            sectionIndex++;
        }

        return Optional.of(sectionChunks);
    }

    private List<String> extractPages(com.aiinterview.assistant.entity.Document document) {
        return pdfTextExtractionService.extractPages(
                fileStorageService.resolvePath(document.getFilePath()),
                document.getOriginalFileName()
        );
    }

    private com.aiinterview.assistant.entity.Document getOwnedDocument(User user, Long documentId) {
        return documentRepository.findByIdAndUploadedBy(documentId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
    }

    /**
     * Qdrant requires UUID point IDs. Use a deterministic UUID per document chunk so re-indexing
     * replaces the same points and metadata remains the source of truth for document/chunk identity.
     */
    private String buildChunkId(Long documentId, int chunkIndex) {
        String key = "doc-" + documentId + "-chunk-" + chunkIndex;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private Map<String, Object> buildChunkMetadata(
            com.aiinterview.assistant.entity.Document document,
            Long userId,
            int chunkIndex,
            Integer pageNumber
    ) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(QdrantProperties.METADATA_DOCUMENT_ID, document.getId().toString());
        metadata.put(QdrantProperties.METADATA_USER_ID, userId.toString());
        metadata.put(QdrantProperties.METADATA_CHUNK_INDEX, String.valueOf(chunkIndex));
        metadata.put(QdrantProperties.METADATA_FILE_NAME, document.getOriginalFileName());
        if (pageNumber != null) {
            metadata.put(QdrantProperties.METADATA_PAGE_NUMBER, String.valueOf(pageNumber));
        }
        return metadata;
    }

    private List<Document> similaritySearch(String query, String filterExpression) {
        int fetchSize = Math.max(qdrantProperties.getTopK(), qdrantProperties.getRetrievalTopK());
        SearchRequest.Builder searchBuilder = SearchRequest.builder()
                .query(query)
                .topK(fetchSize);

        if (qdrantProperties.getSimilarityThreshold() > 0) {
            searchBuilder.similarityThreshold(qdrantProperties.getSimilarityThreshold());
        }
        if (filterExpression != null) {
            searchBuilder.filterExpression(filterExpression);
        }
        return vectorStore.similaritySearch(searchBuilder.build());
    }

    /**
     * The question as asked, plus a keywords-only version so filler words ("can you explain...") do not
     * dilute the embedding.
     */
    private List<String> buildSearchQueries(String query) {
        List<String> queries = new ArrayList<>();
        queries.add(query);

        String keywords = String.join(" ", QuestionTerms.extract(query));
        if (StringUtils.hasText(keywords) && !keywords.equalsIgnoreCase(query.trim())) {
            queries.add(keywords);
        }
        return queries;
    }

    /**
     * Orders by a blend of vector similarity and the share of question words that appear as whole words
     * in the chunk.
     */
    private List<ChunkSearchResult> rerank(List<ChunkSearchResult> chunks, String query) {
        Set<String> terms = QuestionTerms.extract(query);
        double keywordWeight = terms.isEmpty() ? 0.0 : qdrantProperties.getKeywordWeight();

        return chunks.stream()
                .sorted(Comparator.comparingDouble((ChunkSearchResult chunk) -> {
                    double keywordShare = terms.isEmpty()
                            ? 0.0
                            : (double) QuestionTerms.countMatches(chunk.getContent(), terms) / terms.size();
                    return (1 - keywordWeight) * score(chunk) + keywordWeight * keywordShare;
                }).reversed())
                .toList();
    }

    private double score(ChunkSearchResult chunk) {
        return chunk.getScore() != null ? chunk.getScore() : 0.0;
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String buildFilterExpression(Long userId, Long documentId) {
        if (documentId != null) {
            return QdrantProperties.METADATA_DOCUMENT_ID + " == '" + documentId + "' && "
                    + QdrantProperties.METADATA_USER_ID + " == '" + userId + "'";
        }
        return QdrantProperties.METADATA_USER_ID + " == '" + userId + "'";
    }

    private ChunkSearchResult toChunkSearchResult(Document document) {
        Map<String, Object> metadata = document.getMetadata() != null ? document.getMetadata() : Map.of();

        Long docId = parseLongMetadata(metadata.get(QdrantProperties.METADATA_DOCUMENT_ID));
        Integer chunkIndex = parseIntMetadata(metadata.get(QdrantProperties.METADATA_CHUNK_INDEX));
        Integer pageNumber = parseIntMetadata(metadata.get(QdrantProperties.METADATA_PAGE_NUMBER));
        String fileName = metadata.get(QdrantProperties.METADATA_FILE_NAME) != null
                ? metadata.get(QdrantProperties.METADATA_FILE_NAME).toString()
                : null;

        return ChunkSearchResult.builder()
                .id(document.getId())
                .content(document.getText())
                .score(document.getScore())
                .documentId(docId)
                .chunkIndex(chunkIndex)
                .pageNumber(pageNumber)
                .fileName(fileName)
                .metadata(metadata)
                .build();
    }

    private Long parseLongMetadata(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer parseIntMetadata(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
