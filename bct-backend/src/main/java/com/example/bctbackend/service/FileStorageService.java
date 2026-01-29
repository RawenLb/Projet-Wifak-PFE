package com.example.bctbackend.service;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
            log.info("✅ Upload directory created: {}", this.fileStorageLocation);
        } catch (IOException e) {
            log.error("❌ Could not create upload directory!", e);
            throw new RuntimeException("Could not create upload directory!", e);
        }
    }

    /**
     * ✅ Stocker un fichier (PDF, etc.)
     */
    public String storeFile(MultipartFile file, String prefix) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }

        try {
            // Nettoyer le nom du fichier
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.contains("..")) {
                throw new RuntimeException("Invalid filename: " + originalFilename);
            }

            // Créer un nom unique
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String uniqueFilename = prefix + "_" + UUID.randomUUID().toString() + extension;

            // Copier le fichier
            Path targetLocation = this.fileStorageLocation.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            log.info("✅ File stored: {}", uniqueFilename);
            return uniqueFilename;

        } catch (IOException e) {
            log.error("❌ Failed to store file", e);
            throw new RuntimeException("Failed to store file", e);
        }
    }

    /**
     * ✅ Charger un fichier
     */
    public Resource loadFileAsResource(String filename) {
        try {
            Path filePath = this.fileStorageLocation.resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("File not found or not readable: " + filename);
            }
        } catch (MalformedURLException e) {
            log.error("❌ File not found: {}", filename, e);
            throw new RuntimeException("File not found: " + filename, e);
        }
    }

    /**
     * ✅ Supprimer un fichier
     */
    public void deleteFile(String filename) {
        try {
            Path filePath = this.fileStorageLocation.resolve(filename).normalize();
            Files.deleteIfExists(filePath);
            log.info("✅ File deleted: {}", filename);
        } catch (IOException e) {
            log.error("❌ Failed to delete file: {}", filename, e);
        }
    }
}