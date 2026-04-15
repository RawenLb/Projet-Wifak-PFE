package com.example.bctbackend.controller;

import com.example.bctbackend.dto.GenerateDeclarationRequest;
import com.example.bctbackend.entities.Declaration;
import com.example.bctbackend.service.DeclarationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/declarations")
@CrossOrigin(origins = "http://localhost:4200")
public class DeclarationController {

    private static final Logger log = LoggerFactory.getLogger(DeclarationController.class);

    private final DeclarationService declarationService;

    public DeclarationController(DeclarationService declarationService) {
        this.declarationService = declarationService;
    }

    // ─────────────────────────────────────────────
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    // ─────────────────────────────────────────────
    // GENERATE
    // ─────────────────────────────────────────────
    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<Declaration> generateDeclaration(
            @RequestBody GenerateDeclarationRequest req) {

        log.info("🚀 Génération — Type: {}, Période: {}", req.getDeclarationTypeId(), req.getPeriode());

        Declaration saved = declarationService.generateAndSave(
                req.getDeclarationTypeId(),
                req.getPeriode(),
                req.getDateDebut(),
                req.getDateFin()
        );

        declarationService.notifyJiraTicketCreation(saved.getId(), getCurrentUsername());

        return ResponseEntity.ok(saved);
    }

    // ─────────────────────────────────────────────
    // GET MY DECLARATIONS
    // ─────────────────────────────────────────────
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<List<Declaration>> getMyDeclarations() {
        return ResponseEntity.ok(declarationService.getMyDeclarations());
    }

    // ─────────────────────────────────────────────
    // GET ALL
    // ─────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<List<Declaration>> getAllDeclarations() {
        return ResponseEntity.ok(declarationService.getAllDeclarations());
    }

    // ─────────────────────────────────────────────
    // GET BY ID
    // ─────────────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN', 'INTERNAL')")
    public ResponseEntity<Declaration> getDeclarationById(@PathVariable Long id) {
        return ResponseEntity.ok(declarationService.findById(id));
    }

    // ─────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<Declaration> updateDeclaration(
            @PathVariable Long id,
            @RequestBody GenerateDeclarationRequest req) {

        log.info("✏️ Update declaration — ID: {}", id);
        return ResponseEntity.ok(declarationService.updateDeclaration(id, req));
    }

    // ─────────────────────────────────────────────
    // DELETE
    // ─────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<Void> deleteDeclaration(@PathVariable Long id) {
        log.info("🗑️ Delete declaration — ID: {}", id);
        declarationService.deleteDeclaration(id);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────
    // UPDATE STATUT (validation-service)
    // ─────────────────────────────────────────────
    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'INTERNAL','AGENT')")
    public ResponseEntity<Declaration> updateStatut(
            @PathVariable Long id,
            @RequestParam String statut,
            @RequestParam(required = false) String commentaire,
            @RequestParam(required = false) String validePar) {

        log.info("🔄 updateStatut — ID: {}, statut: {}", id, statut);

        return ResponseEntity.ok(
                declarationService.updateStatut(id, statut, commentaire, validePar)
        );
    }

    // ─────────────────────────────────────────────
    // STATS
    // ─────────────────────────────────────────────
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<DeclarationService.DeclarationStats> getStats() {
        return ResponseEntity.ok(declarationService.getStats());
    }

    // ─────────────────────────────────────────────
    // 📥 DOWNLOAD (FIX 404 — AJOUT IMPORTANT)
    // ─────────────────────────────────────────────
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN', 'INTERNAL')")
    public ResponseEntity<byte[]> downloadDeclaration(@PathVariable Long id) {

        log.info("📥 Download déclaration — ID: {}", id);

        Declaration d = declarationService.findById(id);

        if (d.getContenuFichier() == null || d.getContenuFichier().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        byte[] fileBytes = d.getContenuFichier().getBytes();

        String filename = (d.getNomFichier() != null)
                ? d.getNomFichier()
                : "declaration_" + id;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, resolveContentType(filename))
                .body(fileBytes);
    }

    // ─────────────────────────────────────────────
    // Helper MIME TYPE
    // ─────────────────────────────────────────────
    private String resolveContentType(String filename) {
        String lower = filename.toLowerCase();

        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".pdf")) return "application/pdf";

        return "application/xml";
    }
}