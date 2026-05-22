package com.wifak.validationservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    @Value("${file.upload-dir:uploads/templates}")
    private String uploadDir;

    private Path fileStorageLocation;

    @PostConstruct
    public void init() {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
            log.info("✅ Upload directory: {}", this.fileStorageLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory!", e);
        }
    }

    public String storeFile(MultipartFile file, String prefix) {
        if (file == null || file.isEmpty()) throw new RuntimeException("File is empty");
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.contains(".."))
                throw new RuntimeException("Invalid filename: " + originalFilename);
            int dotIndex = originalFilename.lastIndexOf(".");
            String extension = dotIndex >= 0 ? originalFilename.substring(dotIndex) : "";
            String uniqueFilename = prefix + "_" + UUID.randomUUID() + extension;
            Files.copy(file.getInputStream(), fileStorageLocation.resolve(uniqueFilename),
                    StandardCopyOption.REPLACE_EXISTING);
            return uniqueFilename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }

    public Resource loadFileAsResource(String filename) {
        try {
            Path filePath = fileStorageLocation.resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) return resource;
            throw new RuntimeException("File not found: " + filename);
        } catch (MalformedURLException e) {
            throw new RuntimeException("File not found: " + filename, e);
        }
    }

    public void deleteFile(String filename) {
        try {
            Files.deleteIfExists(fileStorageLocation.resolve(filename).normalize());
        } catch (IOException e) {
            log.error("❌ Failed to delete file: {}", filename, e);
        }
    }
}
