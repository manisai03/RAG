package com.aiinterview.assistant.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

/**
 * Adds task prefixes for embedding models trained with them (e.g. nomic-embed-text):
 * stored chunks get the document prefix, search queries get the query prefix.
 * The text saved in the vector store stays unprefixed.
 */
public class TaskPrefixEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final String documentPrefix;
    private final String queryPrefix;

    public TaskPrefixEmbeddingModel(EmbeddingModel delegate, String documentPrefix, String queryPrefix) {
        this.delegate = delegate;
        this.documentPrefix = documentPrefix != null ? documentPrefix : "";
        this.queryPrefix = queryPrefix != null ? queryPrefix : "";
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return delegate.call(request);
    }

    @Override
    public float[] embed(String text) {
        return delegate.embed(queryPrefix + text);
    }

    @Override
    public float[] embed(Document document) {
        return delegate.embed(documentPrefix + document.getText());
    }

    @Override
    public List<float[]> embed(List<Document> documents, EmbeddingOptions options, BatchingStrategy batchingStrategy) {
        List<Document> prefixed = documents.stream()
                .map(document -> Document.builder()
                        .id(document.getId())
                        .text(documentPrefix + document.getText())
                        .metadata(document.getMetadata())
                        .build())
                .toList();
        return delegate.embed(prefixed, options, batchingStrategy);
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }
}
