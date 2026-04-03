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
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN', 'INTERNAL')")
    public ResponseEntity<Declaration> getDeclarationById(@PathVariable Long id) {
        return ResponseEntity.ok(declarationService.findById(id));
    }

    // ══════════════════════════════════════════════════════════════
    // DOWNLOAD
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

        MediaType mediaType = resolveMediaType(filename);

        log.info("📦 Envoi fichier: {} — ContentType: {}", filename, mediaType);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .contentLength(content.length)
                .body(new ByteArrayResource(content));
    }

    private MediaType resolveMediaType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".csv"))  return MediaType.parseMediaType("text/csv; charset=UTF-8");
        if (lower.endsWith(".txt"))  return MediaType.parseMediaType("text/plain; charset=UTF-8");
        if (lower.endsWith(".json")) return MediaType.APPLICATION_JSON;
        if (lower.endsWith(".pdf"))  return MediaType.APPLICATION_PDF;
        return MediaType.APPLICATION_XML;
    }

    // ══════════════════════════════════════════════════════════════
    // STATS (lecture seule — la logique workflow est dans validation-service)
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<DeclarationService.DeclarationStats> getStats() {
        return ResponseEntity.ok(declarationService.getStats());
    }

    // ══════════════════════════════════════════════════════════════
    // ENDPOINT INTERNE — appelé par validation-service via Feign
    // NE PAS exposer directement côté Angular
    // ══════════════════════════════════════════════════════════════

    /**
     * Met à jour le statut d'une déclaration.
     * Invoqué exclusivement par le validation-service via Feign Client.
     *
     * @param id          ID de la déclaration
     * @param statut      Nouveau statut : EN_VALIDATION | VALIDEE | REJETEE | ENVOYEE
     * @param commentaire Commentaire de rejet (obligatoire si statut = REJETEE)
     * @param validePar   Username du manager (obligatoire si statut = VALIDEE | REJETEE)
     */
    @PostMapping("/{id}/statut")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Declaration> updateStatut(
            @PathVariable Long id,
            @RequestParam String statut,
            @RequestParam(required = false) String commentaire,
            @RequestParam(required = false) String validePar) {

        log.info("🔄 [INTERNE] Mise à jour statut — ID: {} → statut: {}", id, statut);
        Declaration updated = declarationService.updateStatut(id, statut, commentaire, validePar);
        log.info("✅ [INTERNE] Statut mis à jour — ID: {}", id);
        return ResponseEntity.ok(updated);
    }


    // ══════════════════════════════════════════════════════════════
    // INNER CLASS
    // ══════════════════════════════════════════════════════════════

    public static class RejectRequest {
        private String commentaire;

        public RejectRequest() {}

        public String getCommentaire()           { return commentaire; }
        public void setCommentaire(String c)     { this.commentaire = c; }
    }

    // ══════════════════════════════════════════════════════════════
    // WORKFLOW — SUPPRIMÉ (migré vers validation-service)
    // Les endpoints suivants ont été retirés :
    //   PATCH /{id}/submit   → POST /api/validation/{id}/submit
    //   PATCH /{id}/validate → POST /api/validation/{id}/validate
    //   PATCH /{id}/reject   → POST /api/validation/{id}/reject
    //   PATCH /{id}/send     → POST /api/validation/{id}/send
    //   GET   /pending       → GET  /api/validation/pending
    // ══════════════════════════════════════════════════════════════
}