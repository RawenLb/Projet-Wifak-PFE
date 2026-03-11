package com.example.bctbackend.controller;

import com.example.bctbackend.dto.GenerateDeclarationRequest;
import com.example.bctbackend.entities.Declaration;
import com.example.bctbackend.service.DeclarationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
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

    // ══════════════════════════════════════════════════════════════
    // GENERATE
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<Declaration> generateDeclaration(
            @RequestBody GenerateDeclarationRequest request) {
        log.info("🚀 Génération déclaration — Type: {}, Période: {}",
                request.getDeclarationTypeId(), request.getPeriode());
        Declaration declaration = declarationService.generateDeclaration(
                request.getDeclarationTypeId(),
                request.getPeriode(),
                request.getDateDebut(),
                request.getDateFin()
        );
        log.info("✅ Déclaration générée — ID: {}", declaration.getId());
        return ResponseEntity.ok(declaration);
    }

    // ══════════════════════════════════════════════════════════════
    // READ
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<Declaration>> getMyDeclarations() {
        return ResponseEntity.ok(declarationService.getMyDeclarations());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<List<Declaration>> getAllDeclarations() {
        return ResponseEntity.ok(declarationService.getAllDeclarations());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Declaration> getDeclarationById(@PathVariable Long id) {
        return ResponseEntity.ok(declarationService.findById(id));
    }

    // ══════════════════════════════════════════════════════════════
    // DOWNLOAD — ✅ CORRIGÉ : ContentType dynamique selon l'extension
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ByteArrayResource> downloadDeclaration(@PathVariable Long id) {
        log.info("📥 Téléchargement déclaration ID: {}", id);

        Declaration declaration = declarationService.findById(id);

        if (declaration.getContenuFichier() == null) {
            return ResponseEntity.notFound().build();
        }

        byte[] content = declaration.getContenuFichier().getBytes(StandardCharsets.UTF_8);
        String filename = declaration.getNomFichier() != null
                ? declaration.getNomFichier()
                : "declaration";

        // ✅ Déduire le ContentType depuis l'extension du fichier
        MediaType mediaType = resolveMediaType(filename);

        log.info("📦 Envoi fichier: {} — ContentType: {}", filename, mediaType);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .contentLength(content.length)
                .body(new ByteArrayResource(content));
    }

    /**
     * ✅ Résout le MediaType selon l'extension du fichier.
     */
    private MediaType resolveMediaType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".csv"))  return MediaType.parseMediaType("text/csv; charset=UTF-8");
        if (lower.endsWith(".txt"))  return MediaType.parseMediaType("text/plain; charset=UTF-8");
        if (lower.endsWith(".json")) return MediaType.APPLICATION_JSON;
        if (lower.endsWith(".pdf"))  return MediaType.APPLICATION_PDF;
        return MediaType.APPLICATION_XML; // défaut XML
    }

    // ══════════════════════════════════════════════════════════════
    // WORKFLOW
    // ══════════════════════════════════════════════════════════════

    @PatchMapping("/{id}/submit")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<Declaration> submitForValidation(@PathVariable Long id) {
        log.info("📤 Soumission pour validation — ID: {}", id);
        return ResponseEntity.ok(declarationService.submitForValidation(id));
    }

    @PatchMapping("/{id}/validate")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Declaration> validateDeclaration(@PathVariable Long id) {
        log.info("✅ Validation déclaration — ID: {}", id);
        return ResponseEntity.ok(declarationService.validateDeclaration(id));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Declaration> rejectDeclaration(
            @PathVariable Long id,
            @RequestBody RejectRequest request) {
        log.info("❌ Rejet déclaration — ID: {}", id);
        return ResponseEntity.ok(
                declarationService.rejectDeclaration(id, request.getCommentaire()));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<List<Declaration>> getPendingDeclarations() {
        return ResponseEntity.ok(declarationService.getPendingDeclarations());
    }

    @PatchMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<Declaration> markAsSent(@PathVariable Long id) {
        log.info("📨 Marquage comme envoyée — ID: {}", id);
        return ResponseEntity.ok(declarationService.markAsSent(id));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<DeclarationService.DeclarationStats> getStats() {
        return ResponseEntity.ok(declarationService.getStats());
    }

    // ══════════════════════════════════════════════════════════════
    // INNER CLASS
    // ══════════════════════════════════════════════════════════════

    public static class RejectRequest {
        private String commentaire;

        public RejectRequest() {}

        public String getCommentaire()               { return commentaire; }
        public void setCommentaire(String c)         { this.commentaire = c; }
    }
}