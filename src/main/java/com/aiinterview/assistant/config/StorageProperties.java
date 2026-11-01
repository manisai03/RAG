package com.aiinterview.assistant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private String uploadDir = "./uploads";
    private String allowedContentType = "application/pdf";
    private long maxFileSizeBytes = 20971520;
}
