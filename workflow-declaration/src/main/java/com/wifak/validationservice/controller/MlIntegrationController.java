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
 * MlIntegrationController â€” BF17
 * Pont entre Angular et ML Service FastAPI.
 * Utilise DeclarationService directement (mÃªme microservice workflow-declaration).
 */
@RestController
@RequestMapping("/api/ml")
public class MlIntegrationController {

    private static final Logger log = LoggerFactory.getLogger(MlIntegrationController.class);

    private final MlServiceFeignClient mlClient;
    private final DeclarationService   declarationService;

    public MlIntegrationController(MlServiceFeignClient mlClient,
                                   DeclarationService declarationService) {
        this.mlClient          = mlClient;
        this.declarationService = declarationService;
    }

    // â”€â”€ HEALTH â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        try {
            return ResponseEntity.ok(mlClient.healthCheck());
        } catch (Exception e) {
            log.error("âŒ ML Service inaccessible : {}", e.getMessage());
            return ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
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
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // â”€â”€ BF17 â€” Suggestions depuis une dÃ©claration existante â”€â”€â”€â”€â”€â”€â”€

    @GetMapping("/bf17/declaration/{id}/suggestions")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> getSuggestionsForDeclaration(@PathVariable Long id) {
        log.info("ðŸ”Ž [BF17] Suggestions pour dÃ©claration {}", id);
        try {
            Declaration decl = declarationService.findById(id);
            String rejectComment = decl.getCommentaireRejet();

            if (rejectComment == null || rejectComment.isBlank()) {
                return ResponseEntity.ok(Map.of(
                        "message",     "Cette dÃ©claration n'a pas encore de commentaire de rejet.",
                        "suggestions", List.of()
                ));
            }

            // RÃ©cupÃ©rer le code du type de dÃ©claration
            String typeCode = decl.getDeclarationType() != null
                    ? decl.getDeclarationType().getCode()
                    : "UNKNOWN";

            Map<String, Object> mlRequest = new HashMap<>();
            mlRequest.put("reject_comment",        rejectComment);
            mlRequest.put("declaration_type_code", typeCode);
            mlRequest.put("top_k",                 5);

            Map<String, Object> result = mlClient.analyzeError(mlRequest);

            // Enrichissement avec les mÃ©tadonnÃ©es de la dÃ©claration
            result.put("declaration_id",   id);
            result.put("declaration_type", typeCode);
            result.put("periode",          decl.getPeriode());
            result.put("statut",           decl.getStatut() != null ? decl.getStatut().name() : null);

            log.info("âœ… [BF17] Analyse terminÃ©e pour dÃ©claration {} â€” cluster : {}",
                    id, result.get("cluster_label"));
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("âŒ [BF17] getSuggestionsForDeclaration({}) : {}", id, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error",   e.getMessage(),
                    "details", "VÃ©rifiez que le ML Service est dÃ©marrÃ© (port 8090)"
            ));
        }
    }

    // â”€â”€ BF17 â€” Analyse commentaire manuel â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // â”€â”€ BF17 â€” Clusters & Stats â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @GetMapping("/bf17/clusters")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> getClusters() {
        try { return ResponseEntity.ok(mlClient.getClusters()); }
        catch (Exception e) { return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage())); }
    }

    @GetMapping("/bf17/stats")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> getStats() {
        try { return ResponseEntity.ok(mlClient.getClusteringStats()); }
        catch (Exception e) { return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage())); }
    }

    // â”€â”€ BF17 â€” EntraÃ®nement (ADMIN) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @PostMapping("/bf17/train")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> trainClustering() {
        try { return ResponseEntity.ok(mlClient.trainClustering()); }
        catch (Exception e) { return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage())); }
    }

    @PostMapping("/train-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> trainAll() {
        try { return ResponseEntity.ok(mlClient.trainAll()); }
        catch (Exception e) { return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage())); }
    }
}
