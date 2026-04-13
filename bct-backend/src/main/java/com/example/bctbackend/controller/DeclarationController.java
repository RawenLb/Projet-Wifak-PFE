package com.example.bctbackend.controller;

import com.example.bctbackend.dto.GenerateDeclarationRequest;
import com.example.bctbackend.entities.Declaration;
import com.example.bctbackend.service.DeclarationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    // ══════════════════════════════════════════════════════════════
    // POST /api/declarations/generate
    // ══════════════════════════════════════════════════════════════
    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<Declaration> generateDeclaration(
            @RequestBody GenerateDeclarationRequest req) {

        log.info("🚀 Génération — Type: {}, Période: {}", req.getDeclarationTypeId(), req.getPeriode());

        // ✅ STEP 1 : save inside @Transactional — commits on return
        Declaration saved = declarationService.generateAndSave(
                req.getDeclarationTypeId(),
                req.getPeriode(),
                req.getDateDebut(),
                req.getDateFin()
        );

        // ✅ STEP 2 : notify Jira AFTER commit — no active transaction here
        declarationService.notifyJiraTicketCreation(saved.getId(), getCurrentUsername());

        log.info("✅ Déclaration générée — ID: {}", saved.getId());
        return ResponseEntity.ok(saved);
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/declarations/my
    // ══════════════════════════════════════════════════════════════
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<List<Declaration>> getMyDeclarations() {
        return ResponseEntity.ok(declarationService.getMyDeclarations());
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/declarations
    // ══════════════════════════════════════════════════════════════
    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<List<Declaration>> getAllDeclarations() {
        return ResponseEntity.ok(declarationService.getAllDeclarations());
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/declarations/{id}
    // ══════════════════════════════════════════════════════════════
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN', 'INTERNAL')")
    public ResponseEntity<Declaration> getDeclarationById(@PathVariable Long id) {
        return ResponseEntity.ok(declarationService.findById(id));
    }

    // ══════════════════════════════════════════════════════════════
    // PUT /api/declarations/{id}
    // ══════════════════════════════════════════════════════════════
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<Declaration> updateDeclaration(
            @PathVariable Long id,
            @RequestBody GenerateDeclarationRequest req) {

        log.info("✏️ Update declaration — ID: {}", id);
        return ResponseEntity.ok(declarationService.updateDeclaration(id, req));
    }

    // ══════════════════════════════════════════════════════════════
    // DELETE /api/declarations/{id}
    // ══════════════════════════════════════════════════════════════
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<Void> deleteDeclaration(@PathVariable Long id) {
        log.info("🗑️ Delete declaration — ID: {}", id);
        declarationService.deleteDeclaration(id);
        return ResponseEntity.noContent().build();
    }

    // ══════════════════════════════════════════════════════════════
    // PATCH /api/declarations/{id}/statut — appelé par validation-service
    // ══════════════════════════════════════════════════════════════
    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'INTERNAL','AGENT')")
    public ResponseEntity<Declaration> updateStatut(
            @PathVariable Long id,
            @RequestParam String statut,
            @RequestParam(required = false) String commentaire,
            @RequestParam(required = false) String validePar) {

        log.info("🔄 updateStatut — ID: {}, statut: {}", id, statut);
        return ResponseEntity.ok(
                declarationService.updateStatut(id, statut, commentaire, validePar));
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/declarations/stats
    // ══════════════════════════════════════════════════════════════
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<DeclarationService.DeclarationStats> getStats() {
        return ResponseEntity.ok(declarationService.getStats());
    }
}