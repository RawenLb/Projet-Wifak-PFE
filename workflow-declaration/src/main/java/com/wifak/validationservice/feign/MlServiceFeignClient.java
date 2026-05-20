// ════════════════════════════════════════════════════════════════════
// MlServiceFeignClient.java
// Client Feign vers le ML Service FastAPI (port 8090)
// BF17 — Clustering erreurs + suggestions de correction
// ════════════════════════════════════════════════════════════════════
package com.wifak.validationservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Client Feign vers le ML Service FastAPI.
 *
 * Configuration application.yml :
 *   ml:
 *     service:
 *       url: http://localhost:8090
 *   feign:
 *     client:
 *       config:
 *         ml-service:
 *           connectTimeout: 5000
 *           readTimeout:    20000
 */
@FeignClient(
        name = "ml-service",
        url  = "${ml.service.url:http://localhost:8090}"
)
public interface MlServiceFeignClient {

    // ── Santé ─────────────────────────────────────────────────────────

    @GetMapping("/health")
    Map<String, Object> healthCheck();

    @GetMapping("/diagnostics")
    Map<String, Object> getDiagnostics();

    // ── BF17 — Analyse d'un commentaire de rejet ─────────────────────

    /**
     * POST /bf17/analyze
     *
     * Body attendu :
     *   {
     *     "reject_comment":        "Le montant brut est négatif...",
     *     "declaration_type_code": "BCT_05",   // optionnel
     *     "top_k":                 5
     *   }
     *
     * Retourne : cluster, keywords, suggestions[], success_rate, message
     */
    @PostMapping("/bf17/analyze")
    Map<String, Object> analyzeError(@RequestBody Map<String, Object> request);

    /**
     * GET /bf17/clusters
     * Tous les clusters avec leur label, mots-clés, statistiques.
     */
    @GetMapping("/bf17/clusters")
    List<Map<String, Object>> getClusters();

    /**
     * GET /bf17/stats
     * Statistiques globales BF17 (nb commentaires, taux résolution...).
     */
    @GetMapping("/bf17/stats")
    Map<String, Object> getClusteringStats();

    /**
     * POST /bf17/train
     * Lance le ré-entraînement en arrière-plan.
     */
    @PostMapping("/bf17/train")
    Map<String, Object> trainClustering();

    /**
     * POST /train-all
     * Alias global (compatibilité bouton dashboard).
     */
    @PostMapping("/train-all")
    Map<String, Object> trainAll();
}