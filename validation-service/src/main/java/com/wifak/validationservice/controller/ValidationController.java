package com.wifak.validationservice.controller;

import com.wifak.validationservice.dto.DeclarationDTO;
import com.wifak.validationservice.dto.RejectRequest;
import com.wifak.validationservice.dto.ValidationStatsDTO;
import com.wifak.validationservice.entities.ValidationLog;
import com.wifak.validationservice.service.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Microservice de validation — expose le workflow complet des déclarations BCT.
 *
 * Base URL : /api/validation
 * Routé depuis l'API Gateway (8088) → validation-service (8084)
 */
@RestController
@RequestMapping("/api/validation")
@CrossOrigin(origins = "http://localhost:4200")
public class ValidationController {

    private static final Logger log = LoggerFactory.getLogger(ValidationController.class);

    private final ValidationService validationService;

    public ValidationController(ValidationService validationService) {
        this.validationService = validationService;
    }

    // ══════════════════════════════════════════════════════════════
    // 1. SOUMETTRE POUR VALIDATION
    //    GENEREE | REJETEE → EN_VALIDATION
    //    Rôle : AGENT
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<DeclarationDTO> submitForValidation(@PathVariable Long id) {
        log.info("📤 [POST] /api/validation/{}/submit", id);
        return ResponseEntity.ok(validationService.submitForValidation(id));
    }

    // ══════════════════════════════════════════════════════════════
    // 2. VALIDER
    //    EN_VALIDATION → VALIDEE
    //    Rôle : MANAGER
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<DeclarationDTO> validateDeclaration(@PathVariable Long id) {
        log.info("✅ [POST] /api/validation/{}/validate", id);
        return ResponseEntity.ok(validationService.validateDeclaration(id));
    }

    // ══════════════════════════════════════════════════════════════
    // 3. REJETER
    //    EN_VALIDATION → REJETEE  (commentaire obligatoire)
    //    Rôle : MANAGER
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<DeclarationDTO> rejectDeclaration(
            @PathVariable Long id,
            @RequestBody RejectRequest request) {
        log.info("❌ [POST] /api/validation/{}/reject", id);
        return ResponseEntity.ok(validationService.rejectDeclaration(id, request.getCommentaire()));
    }

    // ══════════════════════════════════════════════════════════════
    // 4. MARQUER COMME ENVOYÉE
    //    VALIDEE → ENVOYEE
    //    Rôles : AGENT, MANAGER, ADMIN
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<DeclarationDTO> markAsSent(@PathVariable Long id) {
        log.info("📨 [POST] /api/validation/{}/send", id);
        return ResponseEntity.ok(validationService.markAsSent(id));
    }

    // ══════════════════════════════════════════════════════════════
    // 5. LISTE DES DÉCLARATIONS EN ATTENTE
    //    Rôle : MANAGER
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/pending")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<List<DeclarationDTO>> getPendingDeclarations() {
        log.info("📋 [GET] /api/validation/pending");
        return ResponseEntity.ok(validationService.getPendingDeclarations());
    }

    // ══════════════════════════════════════════════════════════════
    // 6. STATISTIQUES
    //    Rôles : MANAGER, ADMIN
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<ValidationStatsDTO> getStats() {
        log.info("📊 [GET] /api/validation/stats");
        return ResponseEntity.ok(validationService.getStats());
    }

    // ══════════════════════════════════════════════════════════════
    // 7. HISTORIQUE DES ACTIONS SUR UNE DÉCLARATION
    //    Rôles : AGENT, MANAGER, ADMIN
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<ValidationLog>> getHistory(@PathVariable Long id) {
        log.info("📜 [GET] /api/validation/{}/history", id);
        return ResponseEntity.ok(validationService.getHistory(id));
    }
}