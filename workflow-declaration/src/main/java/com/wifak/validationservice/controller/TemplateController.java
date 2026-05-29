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

    private static final String ERROR_KEY = "error";
    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    /**
     * Ã¢Å“â€¦ GÃƒÂ©nÃƒÂ©rer un fichier ÃƒÂ  partir d'un template
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

            // Ã¢Å“â€¦ MODIFICATION ICI : Utiliser le service
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
            error.put(ERROR_KEY, e.getMessage());
            error.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * Ã¢Å“â€¦ GÃƒÂ©nÃƒÂ©rer et sauvegarder un fichier sur le serveur
     *
     * POST /api/templates/generate-and-save
     */
    @PostMapping("/generate-and-save")
    public ResponseEntity<?> generateAndSaveFile(@RequestBody GenerateFileRequest request) {
        try {
            // Ã¢Å“â€¦ Valider les donnÃƒÂ©es
            templateService.validateTemplateData(request.getDeclarationTypeId(), request.getData());

            // Ã¢Å“â€¦ GÃƒÂ©nÃƒÂ©rer le contenu
            String fileContent = templateService.generateFile(
                    request.getDeclarationTypeId(),
                    request.getData()
            );

            // Ã¢Å“â€¦ CrÃƒÂ©er le dossier uploads s'il n'existe pas
            Path uploadsDir = Paths.get("uploads");
            if (!Files.exists(uploadsDir)) {
                Files.createDirectories(uploadsDir);
            }

            // Ã¢Å“â€¦ Sauvegarder le fichier
            String extension = determineFileExtension(request.getDeclarationTypeId());
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "declaration_" + timestamp + "." + extension;
            Path filePath = uploadsDir.resolve(fileName);

            Files.write(filePath, fileContent.getBytes());

            // Ã¢Å“â€¦ Retourner les informations du fichier
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Fichier sauvegardÃƒÂ© avec succÃƒÂ¨s");
            response.put("fileName", fileName);
            response.put("filePath", filePath.toString());
            response.put("fileSize", fileContent.length());
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, "Erreur lors de la sauvegarde du fichier: " + e.getMessage());
            error.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put(ERROR_KEY, e.getMessage());
            error.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * Ã¢Å“â€¦ Obtenir la liste des variables requises pour un type de dÃƒÂ©claration
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
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    /**
     * Ã¢Å“â€¦ PrÃƒÂ©visualiser un template avec des donnÃƒÂ©es exemple
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
            error.put(ERROR_KEY, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    /**
     * Ã¢Å“â€¦ Valider des donnÃƒÂ©es par rapport au template (sans gÃƒÂ©nÃƒÂ©rer le fichier)
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
            response.put("message", "Les donnÃƒÂ©es sont valides");
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("valid", false);
            response.put(ERROR_KEY, e.getMessage());
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * DÃƒÂ©terminer l'extension du fichier selon le type de dÃƒÂ©claration
     */
    private String determineFileExtension(Long declarationTypeId) {
        // Cette mÃƒÂ©thode n'est plus nÃƒÂ©cessaire si tu utilises le service
        return templateService.getFileExtension(declarationTypeId);
    }

    /**
     * DÃƒÂ©terminer le MediaType selon l'extension
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
     * DTO pour les requÃƒÂªtes de gÃƒÂ©nÃƒÂ©ration de fichier
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
