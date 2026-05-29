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
 * MlIntegrationController Ã¢â‚¬â€ BF17
 * Pont entre Angular et ML Service FastAPI.
 * Utilise DeclarationService directement (mÃƒÂªme microservice workflow-declaration).
 */
@RestController
@RequestMapping("/api/ml")
public class MlIntegrationController {

    private static final Logger log = LoggerFactory.getLogger(MlIntegrationController.class);
    private static final String ERROR_KEY = "error";

    private final MlServiceFeignClient mlClient;
    private final DeclarationService   declarationService;

    public MlIntegrationController(MlServiceFeignClient mlClient,
                                   DeclarationService declarationService) {
        this.mlClient          = mlClient;
        this.declarationService = declarationService;
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ HEALTH Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        try {
            return ResponseEntity.ok(mlClient.healthCheck());
        } catch (Exception e) {
            log.error("Ã¢ÂÅ’ ML Service inaccessible : {}", e.getMessage());
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
            return ResponseEntity.internalServerError().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ BF17 Ã¢â‚¬â€ Suggestions depuis une dÃƒÂ©claration existante Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    @GetMapping("/bf17/declaration/{id}/suggestions")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> getSuggestionsForDeclaration(@PathVariable Long id) {
        log.info("Ã°Å¸â€Å½ [BF17] Suggestions pour dÃƒÂ©claration {}", id);
        try {
            Declaration decl = declarationService.findById(id);
            String rejectComment = decl.getCommentaireRejet();

            if (rejectComment == null || rejectComment.isBlank()) {
                return ResponseEntity.ok(Map.of(
                        "message",     "Cette dÃƒÂ©claration n'a pas encore de commentaire de rejet.",
                        "suggestions", List.of()
                ));
            }

            // RÃƒÂ©cupÃƒÂ©rer le code du type de dÃƒÂ©claration
            String typeCode = decl.getDeclarationType() != null
                    ? decl.getDeclarationType().getCode()
                    : "UNKNOWN";

            Map<String, Object> mlRequest = new HashMap<>();
            mlRequest.put("reject_comment",        rejectComment);
            mlRequest.put("declaration_type_code", typeCode);
            mlRequest.put("top_k",                 5);

            Map<String, Object> result = mlClient.analyzeError(mlRequest);

            // Enrichissement avec les mÃƒÂ©tadonnÃƒÂ©es de la dÃƒÂ©claration
            result.put("declaration_id",   id);
            result.put("declaration_type", typeCode);
            result.put("periode",          decl.getPeriode());
            result.put("statut",           decl.getStatut() != null ? decl.getStatut().name() : null);

            log.info("Ã¢Å“â€¦ [BF17] Analyse terminÃƒÂ©e pour dÃƒÂ©claration {} Ã¢â‚¬â€ cluster : {}",
                    id, result.get("cluster_label"));
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Ã¢ÂÅ’ [BF17] getSuggestionsForDeclaration({}) : {}", id, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error",   e.getMessage(),
                    "details", "VÃƒÂ©rifiez que le ML Service est dÃƒÂ©marrÃƒÂ© (port 8090)"
            ));
        }
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ BF17 Ã¢â‚¬â€ Analyse commentaire manuel Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    @PostMapping("/bf17/analyze-comment")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> analyzeComment(@RequestBody Map<String, Object> body) {
        String comment = String.valueOf(body.getOrDefault("reject_comment", ""));
        if (comment.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Le champ 'reject_comment' est obligatoire"));
        }
        try {
            return ResponseEntity.ok(mlClient.analyzeError(body));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ BF17 Ã¢â‚¬â€ Clusters & Stats Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    @GetMapping("/bf17/clusters")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> getClusters() {
        try { return ResponseEntity.ok(mlClient.getClusters()); }
        catch (Exception e) { return ResponseEntity.internalServerError().body(Map.of(ERROR_KEY, e.getMessage())); }
    }

    @GetMapping("/bf17/stats")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> getStats() {
        try { return ResponseEntity.ok(mlClient.getClusteringStats()); }
        catch (Exception e) { return ResponseEntity.internalServerError().body(Map.of(ERROR_KEY, e.getMessage())); }
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬ BF17 Ã¢â‚¬â€ EntraÃƒÂ®nement (ADMIN) Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    @PostMapping("/bf17/train")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> trainClustering() {
        try { return ResponseEntity.ok(mlClient.trainClustering()); }
        catch (Exception e) { return ResponseEntity.internalServerError().body(Map.of(ERROR_KEY, e.getMessage())); }
    }

    @PostMapping("/train-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> trainAll() {
        try { return ResponseEntity.ok(mlClient.trainAll()); }
        catch (Exception e) { return ResponseEntity.internalServerError().body(Map.of(ERROR_KEY, e.getMessage())); }
    }
}
