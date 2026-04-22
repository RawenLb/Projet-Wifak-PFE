// ════════════════════════════════════════════════════════════════════
// MlIntegrationController.java — BF17
// Pont entre Angular et ML Service FastAPI
// ════════════════════════════════════════════════════════════════════
package com.wifak.validationservice.controller;

import com.wifak.validationservice.client.DeclarationClient;
import com.wifak.validationservice.dto.DeclarationDTO;
import com.wifak.validationservice.feign.MlServiceFeignClient;
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
 * ══════════════════════════════════════════════════════════════════
 * Routes exposées (préfixe /api/ml) :
 *
 *   GET  /health                                → état ML Service
 *   GET  /diagnostics                           → diagnostic BD + modèle
 *   GET  /bf17/declaration/{id}/suggestions     → suggestions depuis BD
 *   POST /bf17/analyze-comment                  → analyse commentaire libre
 *   GET  /bf17/clusters                         → tous les clusters
 *   GET  /bf17/stats                            → statistiques globales
 *   POST /bf17/train                            → ré-entraînement (ADMIN)
 *   POST /train-all                             → alias train (ADMIN)
 * ══════════════════════════════════════════════════════════════════
 */
@RestController
@RequestMapping("/api/ml")
@CrossOrigin(origins = "http://localhost:4200")
public class MlIntegrationController {

    private static final Logger log = LoggerFactory.getLogger(MlIntegrationController.class);

    private final MlServiceFeignClient mlClient;
    private final DeclarationClient    declarationClient;

    public MlIntegrationController(
            MlServiceFeignClient mlClient,
            DeclarationClient    declarationClient
    ) {
        this.mlClient          = mlClient;
        this.declarationClient = declarationClient;
    }

    // ══════════════════════════════════════════════════════════════
    // HEALTH & DIAGNOSTIC
    // ══════════════════════════════════════════════════════════════

    /**
     * Vérifie l'état du ML Service.
     * Retourne 503 si le service est inaccessible.
     */
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

    /**
     * Diagnostic complet : BD + modèle + statistiques.
     */
    @GetMapping("/diagnostics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> diagnostics() {
        try {
            return ResponseEntity.ok(mlClient.getDiagnostics());
        } catch (Exception e) {
            log.error("❌ /diagnostics : {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // BF17 — Suggestions depuis une déclaration existante
    // ══════════════════════════════════════════════════════════════

    /**
     * Récupère automatiquement le commentaire de rejet d'une déclaration
     * depuis la BD, puis l'analyse via BF17.
     *
     * Exemple de réponse :
     *   "Dans 80% des cas similaires, la correction appliquée a été :
     *    ajustement du champ MontantImpaye (classe D → taux 50%)"
     */
    @GetMapping("/bf17/declaration/{id}/suggestions")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> getSuggestionsForDeclaration(@PathVariable Long id) {
        log.info("🔎 [BF17] Suggestions pour déclaration {}", id);
        try {
            DeclarationDTO decl          = declarationClient.getById(id);
            String         rejectComment = decl.getCommentaireRejet();

            // La déclaration n'a pas encore été rejetée
            if (rejectComment == null || rejectComment.isBlank()) {
                return ResponseEntity.ok(Map.of(
                        "message",     "Cette déclaration n'a pas encore de commentaire de rejet.",
                        "suggestions", List.of()
                ));
            }

            // Construction de la requête vers le ML Service
            Map<String, Object> mlRequest = new HashMap<>();
            mlRequest.put("reject_comment",        rejectComment);
            mlRequest.put("declaration_type_code", decl.getDeclarationTypeName());
            mlRequest.put("top_k",                 5);

            Map<String, Object> result = mlClient.analyzeError(mlRequest);

            // Enrichissement avec les métadonnées de la déclaration
            result.put("declaration_id",   id);
            result.put("declaration_type", decl.getDeclarationTypeName());
            result.put("periode",          decl.getPeriode());
            result.put("statut",           decl.getStatut());

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

    // ══════════════════════════════════════════════════════════════
    // BF17 — Analyse d'un commentaire saisi manuellement
    // ══════════════════════════════════════════════════════════════

    /**
     * Analyse un commentaire de rejet fourni manuellement.
     *
     * Body attendu :
     *   {
     *     "reject_comment":        "Le montant brut est négatif...",
     *     "declaration_type_code": "BCT_05",  // optionnel
     *     "top_k":                 5
     *   }
     */
    @PostMapping("/bf17/analyze-comment")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> analyzeComment(@RequestBody Map<String, Object> body) {
        String comment = String.valueOf(body.getOrDefault("reject_comment", ""));
        if (comment.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Le champ 'reject_comment' est obligatoire"
            ));
        }
        log.info("🔎 [BF17] Analyse commentaire : {}...",
                comment.substring(0, Math.min(60, comment.length())));
        try {
            return ResponseEntity.ok(mlClient.analyzeError(body));
        } catch (Exception e) {
            log.error("❌ [BF17] analyzeComment : {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // BF17 — Clusters et statistiques
    // ══════════════════════════════════════════════════════════════

    /**
     * Retourne tous les clusters identifiés avec leurs statistiques.
     * Utilisé par le tableau de bord manager.
     */
    @GetMapping("/bf17/clusters")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> getClusters() {
        try {
            return ResponseEntity.ok(mlClient.getClusters());
        } catch (Exception e) {
            log.error("❌ [BF17] getClusters : {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Retourne les statistiques globales BF17 :
     * - nb commentaires analysés
     * - nb clusters
     * - taux de résolution global
     * - date du dernier entraînement
     */
    @GetMapping("/bf17/stats")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> getStats() {
        try {
            return ResponseEntity.ok(mlClient.getClusteringStats());
        } catch (Exception e) {
            log.error("❌ [BF17] getStats : {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // BF17 — Entraînement manuel (ADMIN only)
    // ══════════════════════════════════════════════════════════════

    /**
     * Lance le ré-entraînement BF17 en arrière-plan.
     * Réservé aux administrateurs.
     */
    @PostMapping("/bf17/train")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> trainClustering() {
        log.info("🏋️ [BF17] Ré-entraînement lancé par admin");
        try {
            return ResponseEntity.ok(mlClient.trainClustering());
        } catch (Exception e) {
            log.error("❌ [BF17] trainClustering : {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Alias train-all → bf17/train (compatibilité bouton global dashboard).
     */
    @PostMapping("/train-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> trainAll() {
        log.info("🏋️ [BF17] train-all lancé");
        try {
            return ResponseEntity.ok(mlClient.trainAll());
        } catch (Exception e) {
            log.error("❌ [BF17] trainAll : {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}