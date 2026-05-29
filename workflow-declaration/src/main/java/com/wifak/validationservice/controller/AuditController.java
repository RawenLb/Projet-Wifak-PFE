package com.wifak.validationservice.controller;

import com.wifak.validationservice.dto.AuditLogDTO;
import com.wifak.validationservice.dto.AuditStatsDTO;
import com.wifak.validationservice.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Contrôleur dédié à l'espace auditeur.
 * Tous les endpoints sont en lecture seule (GET uniquement).
 * Accessible uniquement avec le rôle AUDITOR (ou ADMIN).
 *
 * Routes exposées via Gateway : /api/audit/**
 */
@RestController
@RequestMapping("/api/audit")
@CrossOrigin(origins = "http://localhost:4200")
public class AuditController {

    private static final Logger log = LoggerFactory.getLogger(AuditController.class);

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }
    // 1. TOUS LES LOGS (journal de traçabilité complet)
    //    GET /api/audit/logs
    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN')")
    public ResponseEntity<List<AuditLogDTO>> getAllLogs() {
        log.info("📋 [GET] /api/audit/logs");
        return ResponseEntity.ok(auditService.getAllLogs());
    }
    // 2. LOGS FILTRÉS (recherche avancée)
    //    GET /api/audit/logs/search?action=VALIDATE&effectuePar=john&from=2025-01-01&to=2025-12-31
    @GetMapping("/logs/search")
    @PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN')")
    public ResponseEntity<List<AuditLogDTO>> searchLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String effectuePar,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        log.info("🔍 [GET] /api/audit/logs/search — action={}, user={}, from={}, to={}",
                action, effectuePar, from, to);

        LocalDateTime fromDt = from != null ? from.atStartOfDay()          : null;
        LocalDateTime toDt   = to   != null ? to.atTime(LocalTime.MAX)     : null;

        return ResponseEntity.ok(auditService.searchLogs(action, effectuePar, fromDt, toDt));
    }
    // 3. LOGS D'UNE DÉCLARATION SPÉCIFIQUE
    //    GET /api/audit/logs/declaration/{id}
    @GetMapping("/logs/declaration/{declarationId}")
    @PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN')")
    public ResponseEntity<List<AuditLogDTO>> getLogsByDeclaration(
            @PathVariable Long declarationId) {
        log.info("📜 [GET] /api/audit/logs/declaration/{}", declarationId);
        return ResponseEntity.ok(auditService.getLogsByDeclaration(declarationId));
    }
    // 4. LISTE DES UTILISATEURS DISTINCTS (pour les filtres)
    //    GET /api/audit/users
    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN')")
    public ResponseEntity<List<String>> getDistinctUsers() {
        log.info("👥 [GET] /api/audit/users");
        return ResponseEntity.ok(auditService.getDistinctUsers());
    }
    // 5. STATISTIQUES COMPLÈTES POUR LE TABLEAU DE BORD
    //    GET /api/audit/stats
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN')")
    public ResponseEntity<AuditStatsDTO> getAuditStats() {
        log.info("📊 [GET] /api/audit/stats");
        return ResponseEntity.ok(auditService.getAuditStats());
    }
}
