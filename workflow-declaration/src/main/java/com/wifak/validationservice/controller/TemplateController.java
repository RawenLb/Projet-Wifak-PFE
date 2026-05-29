package com.wifak.validationservice.controller;

import com.wifak.validationservice.service.TemplateService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    /**
     * âœ… GÃ©nÃ©rer un fichier Ã  partir d'un template
     *
     * POST /api/templates/generate
     * Body: {
     *   "declarationTypeId": 1,
     *   "data": {
     *     "CODE": "BCT_01",
     *     "DATE": "2026-01-29",
     *     "MONTANT": "5000"
     *   }
     * }
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateFile(@RequestBody GenerateFileRequest request) {
        try {
            templateService.validateTemplateData(request.getDeclarationTypeId(), request.getData());

            String fileContent = templateService.generateFile(
                    request.getDeclarationTypeId(),
                    request.getData()
            );

            // âœ… MODIFICATION ICI : Utiliser le service
            String extension = templateService.getFileExtension(request.getDeclarationTypeId());
            MediaType mediaType = determineMediaType(extension);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "declaration_" + timestamp + "." + extension;

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(mediaType)
                    .body(fileContent);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            error.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * âœ… GÃ©nÃ©rer et sauvegarder un fichier sur le serveur
     *
     * POST /api/templates/generate-and-save
     */
    @PostMapping("/generate-and-save")
    public ResponseEntity<?> generateAndSaveFile(@RequestBody GenerateFileRequest request) {
        try {
            // âœ… Valider les donnÃ©es
            templateService.validateTemplateData(request.getDeclarationTypeId(), request.getData());

            // âœ… GÃ©nÃ©rer le contenu
            String fileContent = templateService.generateFile(
                    request.getDeclarationTypeId(),
                    request.getData()
            );

            // âœ… CrÃ©er le dossier uploads s'il n'existe pas
            Path uploadsDir = Paths.get("uploads");
            if (!Files.exists(uploadsDir)) {
                Files.createDirectories(uploadsDir);
            }

            // âœ… Sauvegarder le fichier
            String extension = determineFileExtension(request.getDeclarationTypeId());
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "declaration_" + timestamp + "." + extension;
            Path filePath = uploadsDir.resolve(fileName);

            Files.write(filePath, fileContent.getBytes());

            // âœ… Retourner les informations du fichier
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Fichier sauvegardÃ© avec succÃ¨s");
            response.put("fileName", fileName);
            response.put("filePath", filePath.toString());
            response.put("fileSize", fileContent.length());
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Erreur lors de la sauvegarde du fichier: " + e.getMessage());
            error.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            error.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * âœ… Obtenir la liste des variables requises pour un type de dÃ©claration
     *
     * GET /api/templates/{declarationTypeId}/variables
     */
    @GetMapping("/{declarationTypeId}/variables")
    public ResponseEntity<?> getRequiredVariables(@PathVariable Long declarationTypeId) {
        try {
            List<String> variables = templateService.getRequiredVariables(declarationTypeId);

            Map<String, Object> response = new HashMap<>();
            response.put("declarationTypeId", declarationTypeId);
            response.put("variables", variables);
            response.put("count", variables.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    /**
     * âœ… PrÃ©visualiser un template avec des donnÃ©es exemple
     *
     * GET /api/templates/{declarationTypeId}/preview
     */
    @GetMapping("/{declarationTypeId}/preview")
    public ResponseEntity<?> previewTemplate(@PathVariable Long declarationTypeId) {
        try {
            String preview = templateService.previewTemplate(declarationTypeId);

            Map<String, Object> response = new HashMap<>();
            response.put("declarationTypeId", declarationTypeId);
            response.put("preview", preview);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    /**
     * âœ… Valider des donnÃ©es par rapport au template (sans gÃ©nÃ©rer le fichier)
     *
     * POST /api/templates/validate
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateData(@RequestBody GenerateFileRequest request) {
        try {
            boolean isValid = templateService.validateTemplateData(
                    request.getDeclarationTypeId(),
                    request.getData()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("valid", isValid);
            response.put("message", "Les donnÃ©es sont valides");
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("valid", false);
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * DÃ©terminer l'extension du fichier selon le type de dÃ©claration
     */
    private String determineFileExtension(Long declarationTypeId) {
        // Cette mÃ©thode n'est plus nÃ©cessaire si tu utilises le service
        return templateService.getFileExtension(declarationTypeId);
    }

    /**
     * DÃ©terminer le MediaType selon l'extension
     */
    private MediaType determineMediaType(String extension) {
        return switch (extension.toLowerCase()) {
            case "xml" -> MediaType.APPLICATION_XML;
            case "json" -> MediaType.APPLICATION_JSON;
            case "csv" -> MediaType.parseMediaType("text/csv");
            case "txt" -> MediaType.TEXT_PLAIN;
            case "pdf" -> MediaType.APPLICATION_PDF;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    // ========== DTOs ==========

    /**
     * DTO pour les requÃªtes de gÃ©nÃ©ration de fichier
     */
    public static class GenerateFileRequest {
        private Long declarationTypeId;
        private Map<String, String> data;

        public GenerateFileRequest() {}

        public Long getDeclarationTypeId() {
            return declarationTypeId;
        }

        public void setDeclarationTypeId(Long declarationTypeId) {
            this.declarationTypeId = declarationTypeId;
        }

        public Map<String, String> getData() {
            return data;
        }

        public void setData(Map<String, String> data) {
            this.data = data;
        }
    }
}
