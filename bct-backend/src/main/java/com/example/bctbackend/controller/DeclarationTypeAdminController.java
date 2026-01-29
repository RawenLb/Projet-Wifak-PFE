package com.example.bctbackend.controller;

import com.example.bctbackend.entities.DeclarationType;
import com.example.bctbackend.entities.ValidationRule;
import com.example.bctbackend.repositories.ValidationRuleRepository;
import com.example.bctbackend.service.DeclarationTypeService;
import com.example.bctbackend.service.PdfGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/declaration-types")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@CrossOrigin(origins = "http://localhost:4200")
public class DeclarationTypeAdminController {

    private static final Logger log = LoggerFactory.getLogger(DeclarationTypeAdminController.class);
    private final DeclarationTypeService service;
    private final ValidationRuleRepository validationRuleRepository;
    private final PdfGeneratorService pdfGeneratorService; // ✅ AJOUTÉ

    public DeclarationTypeAdminController(DeclarationTypeService service,
                                          ValidationRuleRepository validationRuleRepository,
                                          PdfGeneratorService pdfGeneratorService) {
        this.service = service;
        this.validationRuleRepository = validationRuleRepository;
        this.pdfGeneratorService = pdfGeneratorService; // ✅ AJOUTÉ
    }

    @PostMapping
    public ResponseEntity<DeclarationType> create(@RequestBody DeclarationType declarationType) {
        log.info("➕ Creating declaration type {}", declarationType.getCode());
        return ResponseEntity.ok(service.create(declarationType));
    }

    @GetMapping
    public ResponseEntity<List<DeclarationType>> getAll() {
        log.info("📋 Fetching all declaration types");
        return ResponseEntity.ok(service.getAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeclarationType> update(
            @PathVariable Long id,
            @RequestBody DeclarationType declarationType) {
        log.info("✏️ Updating declaration type {}", id);
        return ResponseEntity.ok(service.update(id, declarationType));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        log.info("🗑️ Deleting declaration type {}", id);
        service.delete(id);
        return ResponseEntity.ok(Map.of("message", "Declaration type deleted successfully"));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<DeclarationType> toggleStatus(@PathVariable Long id) {
        log.info("🔄 Toggling declaration type {}", id);
        return ResponseEntity.ok(service.toggleStatus(id));
    }

    @GetMapping("/{id}/validation-rules")
    public ResponseEntity<List<ValidationRule>> getValidationRules(@PathVariable Long id) {
        log.info("📋 Fetching validation rules for declaration type {}", id);
        List<ValidationRule> rules = validationRuleRepository.findByDeclarationTypeId(id);
        return ResponseEntity.ok(rules);
    }

    /**
     * ✅ FIXED: Télécharger le template avec génération PDF réelle
     */
    @GetMapping("/{id}/template")
    public ResponseEntity<Resource> downloadTemplate(@PathVariable Long id) {
        log.info("📥 Downloading template for declaration type {}", id);

        try {
            DeclarationType type = service.getAll().stream()
                    .filter(t -> t.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Type not found"));

            if (type.getTemplate() == null || type.getTemplate().getTemplateContent() == null) {
                return ResponseEntity.notFound().build();
            }

            String content = type.getTemplate().getTemplateContent();
            byte[] bytes;
            String extension;
            MediaType mediaType;

            // ✅ Gérer chaque format correctement
            switch (type.getFormat()) {
                case PDF:
                    // ✅ GÉNÉRATION DE PDF RÉEL
                    extension = "pdf";
                    mediaType = MediaType.APPLICATION_PDF;
                    String pdfTitle = "Template - " + type.getNom();
                    bytes = pdfGeneratorService.generatePdfFromText(content, pdfTitle);
                    log.info("✅ Real PDF generated for type {}", type.getCode());
                    break;

                case XML:
                    extension = "xml";
                    mediaType = MediaType.APPLICATION_XML;
                    bytes = content.getBytes(StandardCharsets.UTF_8);
                    break;

                case JSON:
                    extension = "json";
                    mediaType = MediaType.APPLICATION_JSON;
                    bytes = content.getBytes(StandardCharsets.UTF_8);
                    break;

                case CSV:
                    extension = "csv";
                    mediaType = MediaType.parseMediaType("text/csv");
                    bytes = content.getBytes(StandardCharsets.UTF_8);
                    break;

                case TXT:
                default:
                    extension = "txt";
                    mediaType = MediaType.TEXT_PLAIN;
                    bytes = content.getBytes(StandardCharsets.UTF_8);
                    break;
            }

            String filename = "template_" + type.getCode() + "." + extension;

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(mediaType)
                    .contentLength(bytes.length)
                    .body(new ByteArrayResource(bytes));

        } catch (Exception e) {
            log.error("❌ Error downloading template for type {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}