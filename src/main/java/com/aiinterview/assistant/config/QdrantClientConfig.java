package com.aiinterview.assistant.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Spring AI 1.0 defaults to a Qdrant gRPC client that enforces server version compatibility.
 * Recent Qdrant Docker images (e.g. 1.18) fail upserts when paired with client 1.13 unless
 * {@code checkCompatibility} is disabled or the server version is aligned.
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.vectorstore.qdrant", name = "host")
public class QdrantClientConfig {

    @Bean
    public QdrantClient qdrantClient(QdrantVectorStoreProperties properties) {
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(
                properties.getHost(),
                properties.getPort(),
                properties.isUseTls(),
                false
        );
        if (StringUtils.hasText(properties.getApiKey())) {
            builder.withApiKey(properties.getApiKey());
        }
        return new QdrantClient(builder.build());
    }
}
