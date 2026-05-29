package com.wifak.validationservice.controller;

import com.wifak.validationservice.entities.DeclarationType;
import com.wifak.validationservice.entities.ValidationRule;
import com.wifak.validationservice.repositories.ValidationRuleRepository;
import com.wifak.validationservice.service.DeclarationTypeService;
import com.wifak.validationservice.service.PdfGeneratorService;
import com.wifak.validationservice.service.XmlGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/declaration-types")
public class DeclarationTypeAdminController {

    private static final Logger log = LoggerFactory.getLogger(DeclarationTypeAdminController.class);
    private static final String ERROR_KEY = "error";

    // Mots-clés SQL dangereux — protection contre les injections SQL
    private static final String[] SQL_FORBIDDEN_KEYWORDS =
        {"DROP", "DELETE", "INSERT", "UPDATE", "TRUNCATE", "ALTER", "CREATE", "EXEC", "--", ";"};

    private final DeclarationTypeService service;
    private final ValidationRuleRepository validationRuleRepository;
    private final PdfGeneratorService pdfGeneratorService;
    private final XmlGenerationService xmlGenerationService;

    public DeclarationTypeAdminController(
            DeclarationTypeService service,
            ValidationRuleRepository validationRuleRepository,
            PdfGeneratorService pdfGeneratorService,
            XmlGenerationService xmlGenerationService
    ) {
        this.service = service;
        this.validationRuleRepository = validationRuleRepository;
        this.pdfGeneratorService = pdfGeneratorService;
        this.xmlGenerationService = xmlGenerationService;
    }

    // ========== CRUD ==========

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeclarationType> create(@RequestBody DeclarationType declarationType) {
        log.info("➕ Création type déclaration: {}", declarationType.getCode());
        return ResponseEntity.ok(service.create(declarationType));
    }

    // ✅ AGENT + MANAGER peuvent lire les types
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT', 'MANAGER')")
    public ResponseEntity<List<DeclarationType>> getAll() {
        log.info("📋 Récupération de tous les types");
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT', 'MANAGER')")
    public ResponseEntity<DeclarationType> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeclarationType> update(
            @PathVariable Long id,
            @RequestBody DeclarationType declarationType) {
        log.info("✏️ Mise à jour type déclaration: {}", id);
        return ResponseEntity.ok(service.update(id, declarationType));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        log.info("🗑️ Suppression type déclaration: {}", id);
        service.delete(id);
        return ResponseEntity.ok(Map.of("message", "Type de déclaration supprimé avec succès"));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeclarationType> toggleStatus(@PathVariable Long id) {
        log.info("🔄 Toggle statut type déclaration: {}", id);
        return ResponseEntity.ok(service.toggleStatus(id));
    }

    @GetMapping("/{id}/validation-rules")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT', 'MANAGER')")
    public ResponseEntity<List<ValidationRule>> getValidationRules(@PathVariable Long id) {
        return ResponseEntity.ok(validationRuleRepository.findByDeclarationTypeId(id));
    }

    // ========== XSD ==========

    @PostMapping("/{id}/xsd")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<Map<String, String>> uploadXsd(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file
    ) {
        log.info("📤 Upload XSD pour type {}: {}", id, file.getOriginalFilename());
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".xsd")) {
                return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Le fichier doit être un fichier .xsd"));
            }
            String xsdContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            service.saveXsd(id, originalFilename, xsdContent);
            log.info("✅ XSD uploadé avec succès: {}", originalFilename);
            return ResponseEntity.ok(Map.of("message", "XSD uploadé avec succès", "fileName", originalFilename));
        } catch (Exception e) {
            log.error("❌ Erreur upload XSD: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(ERROR_KEY, "Erreur lors de l'upload du XSD: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/xsd/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT', 'MANAGER')")
    public ResponseEntity<ByteArrayResource> downloadXsd(@PathVariable Long id) {
        log.info("📥 Téléchargement XSD pour type: {}", id);
        DeclarationType type = service.getById(id);
        if (type.getXsdContent() == null || type.getXsdContent().trim().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        byte[] content = type.getXsdContent().getBytes(StandardCharsets.UTF_8);
        String filename = type.getXsdFileName() != null ? type.getXsdFileName() : "schema.xsd";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/xml"))
                .contentLength(content.length)
                .body(new ByteArrayResource(content));
    }

    // ========== SQL ==========

    @PatchMapping("/{id}/sql")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<Map<String, String>> saveSqlQuery(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        log.info("💾 Sauvegarde SQL pour type: {}", id);
        String sqlQuery = body.get("sqlQuery");
        if (sqlQuery == null || sqlQuery.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "La requête SQL ne peut pas être vide"));
        }
        if (!sqlQuery.trim().toUpperCase().startsWith("SELECT")) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "La requête SQL doit commencer par SELECT"));
        }
        service.saveSqlQuery(id, sqlQuery);
        log.info("✅ SQL sauvegardé pour type: {}", id);
        return ResponseEntity.ok(Map.of("message", "Requête SQL sauvegardée avec succès"));
    }

    @PostMapping("/{id}/sql/test")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<?> testSqlQuery(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        log.info("🧪 Test SQL pour type: {}", id);
        try {
            LocalDate dateDebut = LocalDate.parse(body.get("dateDebut"));
            LocalDate dateFin = LocalDate.parse(body.get("dateFin"));
            DeclarationType type = service.getById(id);
            String sqlQuery = type.getSqlQuery();
            if (sqlQuery == null || sqlQuery.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Aucune requête SQL configurée pour ce type"));
            }
            // Validation de sécurité : seules les requêtes SELECT sont autorisées
            String normalizedQuery = sqlQuery.trim().toUpperCase();
            if (!normalizedQuery.startsWith("SELECT")) {
                return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Seules les requêtes SELECT sont autorisées"));
            }
            // Bloquer les instructions dangereuses dans la requête
            for (String keyword : SQL_FORBIDDEN_KEYWORDS) {
                if (normalizedQuery.contains(keyword)) {
                    log.warn("⚠️ Requête SQL refusée — mot-clé interdit détecté: {}", keyword);
                    return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Requête SQL non autorisée"));
                }
            }
            List<String> columns = xmlGenerationService.extractColumnsFromSql(sqlQuery, dateDebut, dateFin);
            log.info("✅ Test SQL réussi — colonnes: {}", columns);
            return ResponseEntity.ok(Map.of("success", true, "colonnesDisponibles", columns, "message", "Requête SQL valide"));
        } catch (Exception e) {
            log.error("❌ Erreur test SQL: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Erreur lors du test SQL: " + e.getMessage()));
        }
    }

    // ========== Template ==========

    @GetMapping("/{id}/template")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT', 'MANAGER')")
    public ResponseEntity<Resource> downloadTemplate(@PathVariable Long id) {
        log.info("📥 Téléchargement template pour type: {}", id);
        try {
            DeclarationType type = service.getAll().stream()
                    .filter(t -> t.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Type introuvable"));
            if (type.getTemplate() == null || type.getTemplate().getTemplateContent() == null) {
                return ResponseEntity.notFound().build();
            }
            String content = type.getTemplate().getTemplateContent();
            byte[] bytes;
            String extension;
            MediaType mediaType;
            switch (type.getFormat()) {
                case PDF:
                    extension = "pdf"; mediaType = MediaType.APPLICATION_PDF;
                    bytes = pdfGeneratorService.generatePdfFromText(content, "Template - " + type.getNom()); break;
                case XML:
                    extension = "xml"; mediaType = MediaType.APPLICATION_XML;
                    bytes = content.getBytes(StandardCharsets.UTF_8); break;
                case JSON:
                    extension = "json"; mediaType = MediaType.APPLICATION_JSON;
                    bytes = content.getBytes(StandardCharsets.UTF_8); break;
                case CSV:
                    extension = "csv"; mediaType = MediaType.parseMediaType("text/csv");
                    bytes = content.getBytes(StandardCharsets.UTF_8); break;
                default:
                    extension = "txt"; mediaType = MediaType.TEXT_PLAIN;
                    bytes = content.getBytes(StandardCharsets.UTF_8); break;
            }
            String filename = "template_" + type.getCode() + "." + extension;
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(mediaType).contentLength(bytes.length)
                    .body(new ByteArrayResource(bytes));
        } catch (Exception e) {
            log.error("❌ Erreur téléchargement template: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }


}
