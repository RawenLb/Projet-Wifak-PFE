package com.wifak.validationservice.controller;

import com.wifak.validationservice.entities.Declaration;
import com.wifak.validationservice.feign.MlServiceFeignClient;
import com.wifak.validationservice.service.DeclarationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MlIntegrationController — BF17
 * Pont entre Angular et ML Service FastAPI.
 * Utilise DeclarationService directement (même microservice workflow-declaration).
 */
@RestController
@RequestMapping("/api/ml")
@CrossOrigin(origins = "http://localhost:4200")
public class MlIntegrationController {

    private static final Logger log = LoggerFactory.getLogger(MlIntegrationController.class);

    private final MlServiceFeignClient mlClient;
    private final DeclarationService   declarationService;

    public MlIntegrationController(MlServiceFeignClient mlClient,
                                   DeclarationService declarationService) {
        this.mlClient          = mlClient;
        this.declarationService = declarationService;
    }

    // ── HEALTH ────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        try {
            return ResponseEntity.ok(mlClient.healthCheck());
        } catch (Exception e) {
            log.error("❌ ML Service inaccessible : {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of(
                    "status", "DOWN",
                    "error",  e.getMessage(),
                    "hint",   "Lancez le ML Service : python run.py (port 8090)"
            ));
        }
    }

    @GetMapping("/diagnostics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> diagnostics() {
        try {
            return ResponseEntity.ok(mlClient.getDiagnostics());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── BF17 — Suggestions depuis une déclaration existante ───────

    @GetMapping("/bf17/declaration/{id}/suggestions")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> getSuggestionsForDeclaration(@PathVariable Long id) {
        log.info("🔎 [BF17] Suggestions pour déclaration {}", id);
        try {
            Declaration decl = declarationService.findById(id);
            String rejectComment = decl.getCommentaireRejet();

            if (rejectComment == null || rejectComment.isBlank()) {
                return ResponseEntity.ok(Map.of(
                        "message",     "Cette déclaration n'a pas encore de commentaire de rejet.",
                        "suggestions", List.of()
                ));
            }

            // Récupérer le code du type de déclaration
            String typeCode = decl.getDeclarationType() != null
                    ? decl.getDeclarationType().getCode()
                    : "UNKNOWN";

            Map<String, Object> mlRequest = new HashMap<>();
            mlRequest.put("reject_comment",        rejectComment);
            mlRequest.put("declaration_type_code", typeCode);
            mlRequest.put("top_k",                 5);

            Map<String, Object> result = mlClient.analyzeError(mlRequest);

            // Enrichissement avec les métadonnées de la déclaration
            result.put("declaration_id",   id);
            result.put("declaration_type", typeCode);
            result.put("periode",          decl.getPeriode());
            result.put("statut",           decl.getStatut() != null ? decl.getStatut().name() : null);

            log.info("✅ [BF17] Analyse terminée pour déclaration {} — cluster : {}",
                    id, result.get("cluster_label"));
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("❌ [BF17] getSuggestionsForDeclaration({}) : {}", id, e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "error",   e.getMessage(),
                    "details", "Vérifiez que le ML Service est démarré (port 8090)"
            ));
        }
    }

    // ── BF17 — Analyse commentaire manuel ─────────────────────────

    @PostMapping("/bf17/analyze-comment")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> analyzeComment(@RequestBody Map<String, Object> body) {
        String comment = String.valueOf(body.getOrDefault("reject_comment", ""));
        if (comment.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Le champ 'reject_comment' est obligatoire"));
        }
        try {
            return ResponseEntity.ok(mlClient.analyzeError(body));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── BF17 — Clusters & Stats ───────────────────────────────────

    @GetMapping("/bf17/clusters")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> getClusters() {
        try { return ResponseEntity.ok(mlClient.getClusters()); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @GetMapping("/bf17/stats")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> getStats() {
        try { return ResponseEntity.ok(mlClient.getClusteringStats()); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    // ── BF17 — Entraînement (ADMIN) ───────────────────────────────

    @PostMapping("/bf17/train")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> trainClustering() {
        try { return ResponseEntity.ok(mlClient.trainClustering()); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }

    @PostMapping("/train-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> trainAll() {
        try { return ResponseEntity.ok(mlClient.trainAll()); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("error", e.getMessage())); }
    }
}
