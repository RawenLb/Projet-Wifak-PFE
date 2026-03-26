package com.wifak.validationservice.service;

import com.wifak.validationservice.client.DeclarationClient;
import com.wifak.validationservice.dto.DeclarationDTO;
import com.wifak.validationservice.dto.ValidationStatsDTO;
import com.wifak.validationservice.entities.ValidationLog;
import com.wifak.validationservice.exceptions.DeclarationNotFoundException;
import com.wifak.validationservice.exceptions.ValidationException;
import com.wifak.validationservice.repositories.ValidationLogRepository;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    // Constantes des statuts (reflètent l'enum Declaration.DeclarationStatut)
    private static final String GENEREE       = "GENEREE";
    private static final String EN_VALIDATION = "EN_VALIDATION";
    private static final String VALIDEE       = "VALIDEE";
    private static final String REJETEE       = "REJETEE";
    private static final String ENVOYEE       = "ENVOYEE";

    private final DeclarationClient declarationClient;
    private final ValidationLogRepository logRepository;

    public ValidationService(DeclarationClient declarationClient,
                             ValidationLogRepository logRepository) {
        this.declarationClient = declarationClient;
        this.logRepository     = logRepository;
    }

    // ══════════════════════════════════════════════════════════════
    // HELPER — utilisateur courant depuis JWT
    // ══════════════════════════════════════════════════════════════

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    // ══════════════════════════════════════════════════════════════
    // HELPER — récupère la déclaration via Feign (avec 404 propre)
    // ══════════════════════════════════════════════════════════════

    private DeclarationDTO fetchDeclaration(Long id) {
        try {
            return declarationClient.getById(id);
        } catch (FeignException.NotFound e) {
            throw new DeclarationNotFoundException(id);
        } catch (FeignException e) {
            log.error("❌ Erreur Feign lors de la lecture de la déclaration {}: {}", id, e.getMessage());
            throw new RuntimeException("Impossible de contacter le declaration-service : " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // HELPER — appelle Feign pour changer le statut
    // ══════════════════════════════════════════════════════════════

    private DeclarationDTO changeStatut(Long id, String nouveauStatut,
                                        String commentaire, String validePar) {
        try {
            return declarationClient.updateStatut(id, nouveauStatut, commentaire, validePar);
        } catch (FeignException e) {
            log.error("❌ Erreur Feign lors de la mise à jour du statut: {}", e.getMessage());
            throw new RuntimeException("Impossible de mettre à jour le statut : " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // HELPER — sauvegarde une trace dans wifak_validation
    // ══════════════════════════════════════════════════════════════

    private void saveLog(Long declarationId, String action,
                         String statutAvant, String statutApres,
                         String commentaire) {
        ValidationLog entry = new ValidationLog();
        entry.setDeclarationId(declarationId);
        entry.setAction(action);
        entry.setStatutAvant(statutAvant);
        entry.setStatutApres(statutApres);
        entry.setEffectuePar(getCurrentUsername());
        entry.setCommentaire(commentaire);
        logRepository.save(entry);
        log.info("📝 Log: {} → {} par {} (déclaration {})",
                statutAvant, statutApres, getCurrentUsername(), declarationId);
    }

    // ══════════════════════════════════════════════════════════════
    // 1. SOUMETTRE POUR VALIDATION  (GENEREE ou REJETEE → EN_VALIDATION)
    // ══════════════════════════════════════════════════════════════

    public DeclarationDTO submitForValidation(Long id) {
        log.info("📤 Soumission pour validation — ID: {}", id);

        DeclarationDTO decl = fetchDeclaration(id);
        String currentUser = getCurrentUsername();

        // ✅ LOG TEMPORAIRE — retirer après vérification
        log.info("🔍 currentUser='{}' | generePar='{}'", currentUser, decl.getGenerePar());

        if (!currentUser.equals(decl.getGenerePar())) {
            throw new ValidationException(
                    "Vous ne pouvez soumettre que vos propres déclarations");
        }

        // Statuts autorisés à soumettre
        String statutActuel = decl.getStatut();
        if (!GENEREE.equals(statutActuel) && !REJETEE.equals(statutActuel)) {
            throw new ValidationException(
                    "Seules les déclarations GENEREE ou REJETEE peuvent être soumises. " +
                            "Statut actuel : " + statutActuel);
        }

        DeclarationDTO updated = changeStatut(id, EN_VALIDATION, null, null);
        saveLog(id, "SUBMIT", statutActuel, EN_VALIDATION, null);

        log.info("✅ Déclaration {} soumise pour validation", id);
        return updated;
    }

    // ══════════════════════════════════════════════════════════════
    // 2. VALIDER  (EN_VALIDATION → VALIDEE)
    // ══════════════════════════════════════════════════════════════

    public DeclarationDTO validateDeclaration(Long id) {
        log.info("✅ Validation déclaration — ID: {}", id);

        DeclarationDTO decl = fetchDeclaration(id);

        if (!EN_VALIDATION.equals(decl.getStatut())) {
            throw new ValidationException(
                    "Cette déclaration n'est pas en attente de validation. " +
                            "Statut actuel : " + decl.getStatut());
        }

        String validePar = getCurrentUsername();
        DeclarationDTO updated = changeStatut(id, VALIDEE, null, validePar);
        saveLog(id, "VALIDATE", EN_VALIDATION, VALIDEE, null);

        log.info("✅ Déclaration {} validée par {}", id, validePar);
        return updated;
    }

    // ══════════════════════════════════════════════════════════════
    // 3. REJETER  (EN_VALIDATION → REJETEE)
    // ══════════════════════════════════════════════════════════════

    public DeclarationDTO rejectDeclaration(Long id, String commentaire) {
        log.info("❌ Rejet déclaration — ID: {}", id);

        if (commentaire == null || commentaire.trim().isEmpty()) {
            throw new ValidationException(
                    "Un commentaire est obligatoire pour rejeter une déclaration");
        }

        DeclarationDTO decl = fetchDeclaration(id);

        if (!EN_VALIDATION.equals(decl.getStatut())) {
            throw new ValidationException(
                    "Cette déclaration n'est pas en attente de validation. " +
                            "Statut actuel : " + decl.getStatut());
        }

        String validePar = getCurrentUsername();
        DeclarationDTO updated = changeStatut(id, REJETEE, commentaire.trim(), validePar);
        saveLog(id, "REJECT", EN_VALIDATION, REJETEE, commentaire.trim());

        log.info("❌ Déclaration {} rejetée par {} — motif: {}", id, validePar, commentaire);
        return updated;
    }

    // ══════════════════════════════════════════════════════════════
    // 4. MARQUER COMME ENVOYÉE  (VALIDEE → ENVOYEE)
    // ══════════════════════════════════════════════════════════════

    public DeclarationDTO markAsSent(Long id) {
        log.info("📨 Marquage comme envoyée — ID: {}", id);

        DeclarationDTO decl = fetchDeclaration(id);

        if (!VALIDEE.equals(decl.getStatut())) {
            throw new ValidationException(
                    "Seules les déclarations VALIDEE peuvent être envoyées. " +
                            "Statut actuel : " + decl.getStatut());
        }

        DeclarationDTO updated = changeStatut(id, ENVOYEE, null, null);
        saveLog(id, "SEND", VALIDEE, ENVOYEE, null);

        log.info("📨 Déclaration {} marquée comme envoyée", id);
        return updated;
    }

    // ══════════════════════════════════════════════════════════════
    // 5. LISTE DES DÉCLARATIONS EN ATTENTE  (pour le Manager)
    // ══════════════════════════════════════════════════════════════

    public List<DeclarationDTO> getPendingDeclarations() {
        log.info("📋 Récupération des déclarations EN_VALIDATION");
        try {
            return declarationClient.getAll()
                    .stream()
                    .filter(d -> EN_VALIDATION.equals(d.getStatut()))
                    .toList();
        } catch (FeignException e) {
            log.error("❌ Erreur lors de la récupération des déclarations pending: {}", e.getMessage());
            throw new RuntimeException("Impossible de récupérer les déclarations : " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 6. STATS  (lecture depuis declaration-service)
    // ══════════════════════════════════════════════════════════════

    public ValidationStatsDTO getStats() {
        log.info("📊 Récupération des statistiques");
        try {
            return declarationClient.getStats();
        } catch (FeignException e) {
            log.error("❌ Erreur récupération stats: {}", e.getMessage());
            throw new RuntimeException("Impossible de récupérer les statistiques : " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 7. HISTORIQUE  (depuis wifak_validation — local)
    // ══════════════════════════════════════════════════════════════

    public List<ValidationLog> getHistory(Long declarationId) {
        log.info("📜 Historique validation — déclaration ID: {}", declarationId);
        return logRepository.findByDeclarationIdOrderByDateActionDesc(declarationId);
    }
}