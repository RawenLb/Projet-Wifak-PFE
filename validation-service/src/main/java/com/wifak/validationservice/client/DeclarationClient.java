package com.wifak.validationservice.client;

import com.wifak.validationservice.config.FeignConfig;
import com.wifak.validationservice.dto.DeclarationDTO;
import com.wifak.validationservice.dto.ValidationStatsDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Feign Client vers le declaration-service (port 8082).
 *
 * Ce client permet au validation-service de :
 *   1. Lire les déclarations (GET)
 *   2. Mettre à jour leur statut (PATCH /statut)  ← endpoint interne à ajouter dans declaration-service
 *
 * Le JWT est automatiquement propagé via FeignConfig.
 */
@FeignClient(
        name = "bct-backend",        // nom enregistré dans Eureka
        path = "/api/declarations",
        configuration = FeignConfig.class
)
public interface DeclarationClient {

    // ── Lecture ──────────────────────────────────────────────────

    @GetMapping("/{id}")
    DeclarationDTO getById(@PathVariable("id") Long id);

    @GetMapping
    List<DeclarationDTO> getAll();

    @GetMapping("/my")
    List<DeclarationDTO> getMy();

    // ── Mise à jour de statut (endpoint interne) ─────────────────
    /**
     * Endpoint à ajouter dans le declaration-service :
     * PATCH /api/declarations/{id}/statut
     *
     * Paramètres query :
     *   - statut      : nouveau statut (ex: EN_VALIDATION)
     *   - commentaire : optionnel, pour rejet
     *   - validePar   : optionnel, username du valideur
     */
    @PostMapping("/{id}/statut")
    DeclarationDTO updateStatut(
            @PathVariable("id") Long id,
            @RequestParam("statut") String statut,
            @RequestParam(value = "commentaire", required = false) String commentaire,
            @RequestParam(value = "validePar", required = false) String validePar
    );

    // ── Stats (lecture depuis declaration-service) ────────────────
    @GetMapping("/stats")
    ValidationStatsDTO getStats();
}