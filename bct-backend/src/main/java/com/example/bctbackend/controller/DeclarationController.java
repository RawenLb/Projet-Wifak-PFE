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

    /**
     * ✅ Générer une nouvelle déclaration (Agent métier)
     * BF4 - Génération automatique
     */
    @PostMapping("/generate")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT', 'ROLE_ADMIN')")
    public ResponseEntity<Declaration> generateDeclaration(
            @RequestBody GenerateDeclarationRequest request
    ) {
        log.info("🚀 Génération déclaration - Type: {}, Période: {}",
                request.getDeclarationTypeId(),
                request.getPeriode()
        );

        Declaration declaration = declarationService.generateDeclaration(
                request.getDeclarationTypeId(),
                request.getPeriode(),
                request.getData()
        );

        log.info("✅ Déclaration générée avec succès - ID: {}", declaration.getId());
        return ResponseEntity.ok(declaration);
    }

    /**
     * ✅ Récupérer toutes les déclarations de l'utilisateur connecté
     */
    @GetMapping("/my")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT', 'ROLE_MANAGER', 'ROLE_ADMIN')")
    public ResponseEntity<List<Declaration>> getMyDeclarations() {
        log.info("📋 Récupération de mes déclarations");
        List<Declaration> declarations = declarationService.getMyDeclarations();
        return ResponseEntity.ok(declarations);
    }

    /**
     * ✅ Récupérer toutes les déclarations (Admin/Manager)
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGER', 'ROLE_ADMIN')")
    public ResponseEntity<List<Declaration>> getAllDeclarations() {
        log.info("📋 Récupération de toutes les déclarations");
        List<Declaration> declarations = declarationService.getAllDeclarations();
        return ResponseEntity.ok(declarations);
    }

    /**
     * ✅ Récupérer une déclaration par ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT', 'ROLE_MANAGER', 'ROLE_ADMIN')")
    public ResponseEntity<Declaration> getDeclarationById(@PathVariable Long id) {
        log.info("🔍 Récupération déclaration ID: {}", id);
        Declaration declaration = declarationService.findById(id);
        return ResponseEntity.ok(declaration);
    }

    /**
     * ✅ Télécharger le fichier d'une déclaration
     * BF7 - Consultation et téléchargement
     */
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT', 'ROLE_MANAGER', 'ROLE_ADMIN')")
    public ResponseEntity<ByteArrayResource> downloadDeclaration(@PathVariable Long id) {
        log.info("📥 Téléchargement déclaration ID: {}", id);

        Declaration declaration = declarationService.findById(id);

        if (declaration.getContenuFichier() == null) {
            return ResponseEntity.notFound().build();
        }

        byte[] content = declaration.getContenuFichier().getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(content);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + declaration.getNomFichier() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(content.length)
                .body(resource);
    }

    /**
     * ✅ Soumettre pour validation (Agent → Manager)
     * BF5 - Workflow de validation
     */
    @PatchMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('ROLE_AGENT')")
    public ResponseEntity<Declaration> submitForValidation(@PathVariable Long id) {
        log.info("📤 Soumission pour validation - ID: {}", id);
        Declaration declaration = declarationService.submitForValidation(id);
        log.info("✅ Déclaration soumise avec succès");
        return ResponseEntity.ok(declaration);
    }

    /**
     * ✅ Valider une déclaration (Manager)
     * BF5 - Workflow de validation
     */
    @PatchMapping("/{id}/validate")
    @PreAuthorize("hasAuthority('ROLE_MANAGER')")
    public ResponseEntity<Declaration> validateDeclaration(@PathVariable Long id) {
        log.info("✅ Validation déclaration - ID: {}", id);
        Declaration declaration = declarationService.validateDeclaration(id);
        log.info("✅ Déclaration validée avec succès");
        return ResponseEntity.ok(declaration);
    }

    /**
     * ✅ Rejeter une déclaration avec commentaire (Manager)
     * BF5 - Workflow de validation
     */
    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('ROLE_MANAGER')")
    public ResponseEntity<Declaration> rejectDeclaration(
            @PathVariable Long id,
            @RequestBody RejectRequest request
    ) {
        log.info("❌ Rejet déclaration - ID: {}", id);
        Declaration declaration = declarationService.rejectDeclaration(id, request.getCommentaire());
        log.info("✅ Déclaration rejetée avec succès");
        return ResponseEntity.ok(declaration);
    }

    /**
     * ✅ Récupérer les déclarations en attente de validation (Manager)
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('ROLE_MANAGER')")
    public ResponseEntity<List<Declaration>> getPendingDeclarations() {
        log.info("📋 Récupération déclarations en attente");
        List<Declaration> declarations = declarationService.getPendingDeclarations();
        return ResponseEntity.ok(declarations);
    }

    /**
     * ✅ Marquer comme envoyée (Admin/Manager)
     */
    @PatchMapping("/{id}/send")
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGER', 'ROLE_ADMIN')")
    public ResponseEntity<Declaration> markAsSent(@PathVariable Long id) {
        log.info("📨 Marquage comme envoyée - ID: {}", id);
        Declaration declaration = declarationService.markAsSent(id);
        log.info("✅ Déclaration marquée comme envoyée");
        return ResponseEntity.ok(declaration);
    }

    // ========== DTOs ==========

    /**
     * DTO pour le rejet de déclaration
     */
    public static class RejectRequest {
        private String commentaire;

        public RejectRequest() {}

        public String getCommentaire() {
            return commentaire;
        }

        public void setCommentaire(String commentaire) {
            this.commentaire = commentaire;
        }
    }
}