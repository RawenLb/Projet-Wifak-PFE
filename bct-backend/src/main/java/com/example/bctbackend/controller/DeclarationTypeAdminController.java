package com.example.bctbackend.controller;

import com.example.bctbackend.entities.DeclarationType;
import com.example.bctbackend.entities.ValidationRule;
import com.example.bctbackend.repositories.ValidationRuleRepository;
import com.example.bctbackend.service.DeclarationTypeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/declaration-types")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@CrossOrigin(origins = "http://localhost:4200") // ✅ IMPORTANT: Ajouter CORS
public class DeclarationTypeAdminController {

    private static final Logger log = LoggerFactory.getLogger(DeclarationTypeAdminController.class);
    private final DeclarationTypeService service;
    private final ValidationRuleRepository validationRuleRepository; // ✅ AJOUTÉ

    public DeclarationTypeAdminController(DeclarationTypeService service,
                                          ValidationRuleRepository validationRuleRepository) {
        this.service = service;
        this.validationRuleRepository = validationRuleRepository; // ✅ AJOUTÉ
    }

    // ➕ Créer
    @PostMapping
    public ResponseEntity<DeclarationType> create(@RequestBody DeclarationType declarationType) {
        log.info("➕ Creating declaration type {}", declarationType.getCode());
        return ResponseEntity.ok(service.create(declarationType));
    }

    // 📋 Lister tous
    @GetMapping
    public ResponseEntity<List<DeclarationType>> getAll() {
        log.info("📋 Fetching all declaration types");
        return ResponseEntity.ok(service.getAll());
    }

    // ✏️ Modifier
    @PutMapping("/{id}")
    public ResponseEntity<DeclarationType> update(
            @PathVariable Long id,
            @RequestBody DeclarationType declarationType) {
        log.info("✏️ Updating declaration type {}", id);
        return ResponseEntity.ok(service.update(id, declarationType));
    }

    // 🗑️ Supprimer
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        log.info("🗑️ Deleting declaration type {}", id);
        service.delete(id);
        return ResponseEntity.ok(Map.of("message", "Declaration type deleted successfully"));
    }

    // 🔄 Toggle statut
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<DeclarationType> toggleStatus(@PathVariable Long id) {
        log.info("🔄 Toggling declaration type {}", id);
        return ResponseEntity.ok(service.toggleStatus(id));
    }

    // ✅ NOUVEAU: Récupérer les règles de validation
    @GetMapping("/{id}/validation-rules")
    public ResponseEntity<List<ValidationRule>> getValidationRules(@PathVariable Long id) {
        log.info("📋 Fetching validation rules for declaration type {}", id);
        List<ValidationRule> rules = validationRuleRepository.findByDeclarationTypeId(id);
        return ResponseEntity.ok(rules);
    }

    // ✅ NOUVEAU: Télécharger le template
    @GetMapping("/{id}/template")
    public ResponseEntity<Resource> downloadTemplate(@PathVariable Long id) throws IOException {
        log.info("📥 Downloading template for declaration type {}", id);

        DeclarationType type = service.getAll().stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Type not found"));

        if (type.getTemplate() == null || type.getTemplate().getTemplateContent() == null) {
            return ResponseEntity.notFound().build();
        }

        // ✅ Convertir le contenu du template en Resource
        String content = type.getTemplate().getTemplateContent();
        byte[] bytes = content.getBytes();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"template_" + type.getCode() + ".xml\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(new org.springframework.core.io.ByteArrayResource(bytes));
    }
}