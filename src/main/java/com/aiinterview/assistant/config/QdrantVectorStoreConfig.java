package com.aiinterview.assistant.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Qdrant connection and collection settings are configured via
 * {@code spring.ai.vectorstore.qdrant.*} in application.yml.
 * Spring AI auto-configuration creates the {@link org.springframework.ai.vectorstore.VectorStore} bean.
 */
@Configuration
@EnableConfigurationProperties(QdrantProperties.class)
public class QdrantVectorStoreConfig {
}
