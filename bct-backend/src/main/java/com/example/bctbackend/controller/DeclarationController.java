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
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;   // ← ICI
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/declarations")
@CrossOrigin(
        origins = "http://localhost:4200",
        methods = {
                RequestMethod.GET,
                RequestMethod.POST,
                RequestMethod.PUT,
                RequestMethod.DELETE,
                RequestMethod.PATCH,
                RequestMethod.OPTIONS
        },
        allowedHeaders = "*",
        allowCredentials = "true"
)
public class DeclarationController {

    private static final Logger log = LoggerFactory.getLogger(DeclarationController.class);
    private final DeclarationService declarationService;

    public DeclarationController(DeclarationService declarationService) {
        this.declarationService = declarationService;
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<Declaration> generateDeclaration(
            @RequestBody GenerateDeclarationRequest request) {
        log.info("🚀 Génération — Type: {}, Période: {}",
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
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN', 'INTERNAL')")
    public ResponseEntity<Declaration> getDeclarationById(@PathVariable Long id) {
        return ResponseEntity.ok(declarationService.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<Declaration> updateDeclaration(
            @PathVariable Long id,
            @RequestBody GenerateDeclarationRequest request) {
        log.info("✏️ Mise à jour — ID: {}", id);
        Declaration updated = declarationService.updateDeclaration(id, request);
        log.info("✅ Déclaration mise à jour — ID: {}", id);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<Void> deleteDeclaration(@PathVariable Long id) {
        log.info("🗑️ Suppression — ID: {}", id);
        declarationService.deleteDeclaration(id);
        log.info("✅ Déclaration supprimée — ID: {}", id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ByteArrayResource> downloadDeclaration(@PathVariable Long id) {
        log.info("📥 Téléchargement — ID: {}", id);
        Declaration declaration = declarationService.findById(id);
        if (declaration.getContenuFichier() == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] content  = declaration.getContenuFichier().getBytes(StandardCharsets.UTF_8);
        String filename = declaration.getNomFichier() != null
                ? declaration.getNomFichier() : "declaration";
        MediaType mediaType = resolveMediaType(filename);
        log.info("📦 Fichier: {} — ContentType: {}", filename, mediaType);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .contentLength(content.length)
                .body(new ByteArrayResource(content));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<DeclarationService.DeclarationStats> getStats() {
        return ResponseEntity.ok(declarationService.getStats());
    }

    @PostMapping("/{id}/statut")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN', 'INTERNAL')")
    public ResponseEntity<Declaration> updateStatut(
            @PathVariable Long id,
            @RequestParam String statut,
            @RequestParam(required = false) String commentaire,
            @RequestParam(required = false) String validePar) {
        log.info("🔄 [INTERNE] updateStatut — ID: {} → {}", id, statut);
        Declaration updated = declarationService.updateStatut(id, statut, commentaire, validePar);
        log.info("✅ [INTERNE] Statut mis à jour — ID: {}", id);
        return ResponseEntity.ok(updated);
    }

    private MediaType resolveMediaType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".csv"))  return MediaType.parseMediaType("text/csv; charset=UTF-8");
        if (lower.endsWith(".txt"))  return MediaType.parseMediaType("text/plain; charset=UTF-8");
        if (lower.endsWith(".json")) return MediaType.APPLICATION_JSON;
        if (lower.endsWith(".pdf"))  return MediaType.APPLICATION_PDF;
        return MediaType.APPLICATION_XML;
    }

    public static class RejectRequest {
        private String commentaire;
        public RejectRequest() {}
        public String getCommentaire()       { return commentaire; }
        public void setCommentaire(String c) { this.commentaire = c; }
    }
}