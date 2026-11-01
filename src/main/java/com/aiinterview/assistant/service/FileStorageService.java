package com.aiinterview.assistant.service;

import com.aiinterview.assistant.config.StorageProperties;
import com.aiinterview.assistant.exception.FileStorageException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final StorageProperties storageProperties;
    private Path uploadRoot;

    @PostConstruct
    public void init() {
        uploadRoot = Paths.get(storageProperties.getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException ex) {
            throw new FileStorageException("Could not create upload directory: " + uploadRoot, ex);
        }
    }

    public String store(MultipartFile file, Long userId) {
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = getFileExtension(originalFileName);
        String storedFileName = UUID.randomUUID() + extension;

        Path userDirectory = uploadRoot.resolve(String.valueOf(userId));
        Path targetLocation = userDirectory.resolve(storedFileName).normalize();

        if (!targetLocation.startsWith(uploadRoot)) {
            throw new FileStorageException("Invalid file path detected");
        }

        try {
            Files.createDirectories(userDirectory);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return uploadRoot.relativize(targetLocation).toString().replace("\\", "/");
        } catch (IOException ex) {
            throw new FileStorageException("Failed to store file: " + originalFileName, ex);
        }
    }

    public Resource loadAsResource(String relativePath) {
        Path filePath = resolvePath(relativePath);

        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new FileStorageException("File not found: " + relativePath);
            }

            return resource;
        } catch (MalformedURLException ex) {
            throw new FileStorageException("File not found: " + relativePath, ex);
        }
    }

    public Path resolvePath(String relativePath) {
        Path filePath = uploadRoot.resolve(relativePath).normalize();

        if (!filePath.startsWith(uploadRoot)) {
            throw new FileStorageException("Invalid file path detected");
        }

        if (!Files.exists(filePath)) {
            throw new FileStorageException("File not found: " + relativePath);
        }

        return filePath;
    }

    public void delete(String relativePath) {
        try {
            Path filePath = uploadRoot.resolve(relativePath).normalize();

            if (!filePath.startsWith(uploadRoot)) {
                throw new FileStorageException("Invalid file path detected");
            }

            if (Files.deleteIfExists(filePath)) {
                log.info("Deleted file {}", filePath);
            } else {
                log.warn("File already absent on disk: {}", filePath);
            }
        } catch (IOException ex) {
            throw new FileStorageException("Failed to delete file: " + relativePath, ex);
        }
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) {
            return "";
        }
        return fileName.substring(dotIndex).toLowerCase();
    }
}
