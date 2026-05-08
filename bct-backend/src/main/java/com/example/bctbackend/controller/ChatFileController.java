package com.example.bctbackend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatFileController {

    private static final Logger log = LoggerFactory.getLogger(ChatFileController.class);

    @Value("${file.chat-upload-dir:uploads/templates/chat}")
    private String chatUploadDir;

    private static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp",
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "text/plain",
        "application/zip",
        "audio/webm", "audio/ogg", "audio/mpeg", "audio/wav"
    );

    private static final java.util.Set<String> IMAGE_TYPES = java.util.Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    /**
     * POST /api/chat/upload
     */
    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ROLE_AGENT','ROLE_MANAGER')")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file
    ) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Empty file"));
        }
        if (file.getSize() > 20 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "File too large (max 20MB)"));
        }

        String contentType = file.getContentType();
        if (contentType == null) contentType = "application/octet-stream";

        // Normalize audio/webm;codecs=opus → audio/webm
        if (contentType.contains(";")) {
            contentType = contentType.split(";")[0].trim();
        }

        if (!ALLOWED_TYPES.contains(contentType)) {
            log.warn("[ChatFile] Rejected type: {}", contentType);
            return ResponseEntity.badRequest().body(Map.of("error", "File type not allowed: " + contentType));
        }

        try {
            Path uploadPath = Paths.get(chatUploadDir);
            Files.createDirectories(uploadPath);

            String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "file";

            // Determine extension
            String ext = "";
            int dotIdx = originalName.lastIndexOf('.');
            if (dotIdx >= 0) {
                ext = originalName.substring(dotIdx);
            } else {
                // Derive extension from content type for voice messages
                ext = switch (contentType) {
                    case "audio/webm" -> ".webm";
                    case "audio/ogg"  -> ".ogg";
                    case "audio/mpeg" -> ".mp3";
                    case "audio/wav"  -> ".wav";
                    case "image/jpeg" -> ".jpg";
                    case "image/png"  -> ".png";
                    case "image/gif"  -> ".gif";
                    case "image/webp" -> ".webp";
                    default           -> "";
                };
            }

            String storedName = UUID.randomUUID().toString() + ext;
            Path targetPath = uploadPath.resolve(storedName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // Absolute URL so frontend can use it directly
            String fileUrl = "http://localhost:8082/api/chat/files/" + storedName;

            // Determine message type
            String msgType = IMAGE_TYPES.contains(contentType) ? "IMAGE"
                : contentType.startsWith("audio/") ? "VOICE"
                : "FILE";

            log.info("[ChatFile] Uploaded: {} → {} ({})", originalName, storedName, msgType);

            return ResponseEntity.ok(Map.of(
                "url",      fileUrl,
                "fileName", originalName,
                "type",     msgType
            ));

        } catch (IOException e) {
            log.error("[ChatFile] Upload failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    /**
     * GET /api/chat/files/{filename}
     * Serves uploaded chat files.
     * No auth required — files are identified by UUID names (security by obscurity).
     * The upload endpoint is still protected.
     */
    @GetMapping("/files/{filename}")
    public ResponseEntity<org.springframework.core.io.Resource> serveFile(
            @PathVariable String filename
    ) {
        try {
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                return ResponseEntity.badRequest().build();
            }

            Path filePath = Paths.get(chatUploadDir, filename);
            org.springframework.core.io.Resource resource =
                new org.springframework.core.io.UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = Files.probeContentType(filePath);
            if (contentType == null) contentType = "application/octet-stream";

            return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
                .header("Cache-Control", "max-age=86400")
                .body(resource);

        } catch (Exception e) {
            log.error("[ChatFile] Serve failed for {}: {}", filename, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
