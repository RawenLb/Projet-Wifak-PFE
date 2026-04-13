package com.wifak.validationservice.service;

import com.wifak.validationservice.client.DeclarationClient;
import com.wifak.validationservice.dto.DeclarationDTO;
import com.wifak.validationservice.dto.ValidationStatsDTO;
import com.wifak.validationservice.dto.jira.TransitionJiraTicketRequest;
import com.wifak.validationservice.entities.ValidationLog;
import com.wifak.validationservice.feign.JiraIntegrationFeignClient;
import com.wifak.validationservice.repositories.ValidationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    private final DeclarationClient          declarationClient;
    private final JiraIntegrationFeignClient jiraClient;
    private final ValidationLogRepository   logRepository;

    public ValidationService(DeclarationClient declarationClient,
                             JiraIntegrationFeignClient jiraClient,
                             ValidationLogRepository logRepository) {
        this.declarationClient = declarationClient;
        this.jiraClient        = jiraClient;
        this.logRepository     = logRepository;
    }

    // ══════════════════════════════════════════════════════════════
    // 1. SOUMETTRE — GENEREE | REJETEE → EN_VALIDATION
    // ══════════════════════════════════════════════════════════════
    public DeclarationDTO submitForValidation(Long declarationId) {
        String currentUser = getCurrentUsername();
        log.info("📤 submitForValidation — ID: {}, user: {}", declarationId, currentUser);

        DeclarationDTO decl = declarationClient.getById(declarationId);
        validateStatut(decl.getStatut(), "GENEREE", "REJETEE");

        // ✅ Vérification ownership AVANT toute modification
        if (!currentUser.equals(decl.getGenerePar())) {
            throw new IllegalStateException("Vous ne pouvez soumettre que vos propres déclarations");
        }

        String statutAvant = decl.getStatut();

        DeclarationDTO updated = declarationClient.updateStatut(
                declarationId, "EN_VALIDATION", null, null);

        saveLog(declarationId, "SUBMIT", statutAvant, "EN_VALIDATION", currentUser, null);

        // ── Cas 1 : GENEREE → ticket déjà en TO DO depuis la génération → transition TO DO → IN PROGRESS
        if ("GENEREE".equals(statutAvant)) {
            try {
                Boolean exists = jiraClient.ticketExists(declarationId);
                if (Boolean.TRUE.equals(exists)) {
                    TransitionJiraTicketRequest req = new TransitionJiraTicketRequest(
                            declarationId, "EN_VALIDATION", null, currentUser);
                    jiraClient.transitionTicket(req);
                    log.info("🔄 Ticket Jira TO DO → IN PROGRESS pour déclaration {}", declarationId);
                } else {
                    log.warn("⚠️ Ticket Jira introuvable pour déclaration {} — transition ignorée", declarationId);
                }
            } catch (Exception e) {
                log.warn("⚠️ Jira transition échouée pour déclaration {} : {}",
                        declarationId, e.getMessage());
            }
        }

        // ── Cas 2 : REJETEE resoumise → REJETÉE → IN PROGRESS
        if ("REJETEE".equals(statutAvant)) {
            try {
                TransitionJiraTicketRequest req = new TransitionJiraTicketRequest(
                        declarationId, "RESOUMISE", null, currentUser);
                jiraClient.transitionTicket(req);
                log.info("🔁 Ticket Jira REJETÉE → IN PROGRESS pour déclaration {}", declarationId);
            } catch (Exception e) {
                log.warn("⚠️ Jira resoumission échouée pour déclaration {} : {}",
                        declarationId, e.getMessage());
            }
        }

        return updated;
    }

    // ══════════════════════════════════════════════════════════════
    // 2. VALIDER — EN_VALIDATION → VALIDEE
    // ══════════════════════════════════════════════════════════════
    public DeclarationDTO validateDeclaration(Long declarationId) {
        String currentUser = getCurrentUsername();
        log.info("✅ validateDeclaration — ID: {}, manager: {}", declarationId, currentUser);

        DeclarationDTO decl = declarationClient.getById(declarationId);
        validateStatut(decl.getStatut(), "EN_VALIDATION");

        DeclarationDTO updated = declarationClient.updateStatut(
                declarationId, "VALIDEE", null, currentUser);

        saveLog(declarationId, "VALIDATE", decl.getStatut(), "VALIDEE", currentUser, null);

        try {
            TransitionJiraTicketRequest req = new TransitionJiraTicketRequest(
                    declarationId, "VALIDEE", null, currentUser);
            jiraClient.transitionTicket(req);
            log.info("🔄 Ticket Jira IN PROGRESS → VALIDÉE pour déclaration {}", declarationId);
        } catch (Exception e) {
            log.warn("⚠️ Jira sync échouée pour validation déclaration {} : {}",
                    declarationId, e.getMessage());
        }

        return updated;
    }

    // ══════════════════════════════════════════════════════════════
    // 3. REJETER — EN_VALIDATION → REJETEE
    // ══════════════════════════════════════════════════════════════
    public DeclarationDTO rejectDeclaration(Long declarationId, String commentaire) {
        String currentUser = getCurrentUsername();
        log.info("❌ rejectDeclaration — ID: {}, manager: {}", declarationId, currentUser);

        if (commentaire == null || commentaire.isBlank()) {
            throw new IllegalArgumentException("Le commentaire de rejet est obligatoire");
        }

        DeclarationDTO decl = declarationClient.getById(declarationId);
        validateStatut(decl.getStatut(), "EN_VALIDATION");

        DeclarationDTO updated = declarationClient.updateStatut(
                declarationId, "REJETEE", commentaire, currentUser);

        saveLog(declarationId, "REJECT", decl.getStatut(), "REJETEE", currentUser, commentaire);

        try {
            TransitionJiraTicketRequest req = new TransitionJiraTicketRequest(
                    declarationId, "REJETEE", commentaire, currentUser);
            jiraClient.transitionTicket(req);
            log.info("🔄 Ticket Jira IN PROGRESS → REJETÉE pour déclaration {}", declarationId);
        } catch (Exception e) {
            log.warn("⚠️ Jira sync échouée pour rejet déclaration {} : {}",
                    declarationId, e.getMessage());
        }

        return updated;
    }

    // ══════════════════════════════════════════════════════════════
    // 4. ENVOYER — VALIDEE → ENVOYEE
    // ══════════════════════════════════════════════════════════════
    public DeclarationDTO markAsSent(Long declarationId) {
        String currentUser = getCurrentUsername();
        log.info("📨 markAsSent — ID: {}, user: {}", declarationId, currentUser);

        DeclarationDTO decl = declarationClient.getById(declarationId);
        validateStatut(decl.getStatut(), "VALIDEE");

        DeclarationDTO updated = declarationClient.updateStatut(
                declarationId, "ENVOYEE", null, null);

        saveLog(declarationId, "SEND", decl.getStatut(), "ENVOYEE", currentUser, null);

        try {
            TransitionJiraTicketRequest req = new TransitionJiraTicketRequest(
                    declarationId, "ENVOYEE", null, currentUser);
            jiraClient.transitionTicket(req);
            log.info("🔄 Ticket Jira VALIDÉE → ENVOYÉE pour déclaration {}", declarationId);
        } catch (Exception e) {
            log.warn("⚠️ Jira sync échouée pour envoi déclaration {} : {}",
                    declarationId, e.getMessage());
        }

        return updated;
    }

    // ══════════════════════════════════════════════════════════════
    // 5. PENDING
    // ══════════════════════════════════════════════════════════════
    public List<DeclarationDTO> getPendingDeclarations() {
        return declarationClient.getAll().stream()
                .filter(d -> "EN_VALIDATION".equals(d.getStatut()))
                .toList();
    }

    // ══════════════════════════════════════════════════════════════
    // 6. STATS
    // ══════════════════════════════════════════════════════════════
    public ValidationStatsDTO getStats() {
        return declarationClient.getStats();
    }

    // ══════════════════════════════════════════════════════════════
    // 7. HISTORY
    // ══════════════════════════════════════════════════════════════
    public List<ValidationLog> getHistory(Long declarationId) {
        return logRepository.findByDeclarationIdOrderByDateActionDesc(declarationId);
    }

    // ══════════════════════════════════════════════════════════════
    // UTILITAIRES PRIVÉS
    // ══════════════════════════════════════════════════════════════
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new RuntimeException("Utilisateur non authentifié");
        return auth.getName();
    }

    private void validateStatut(String current, String... allowed) {
        for (String s : allowed) {
            if (s.equals(current)) return;
        }
        throw new IllegalStateException(
                String.format("Statut invalide '%s'. Attendu : %s",
                        current, String.join(" ou ", allowed)));
    }

    private void saveLog(Long declarationId, String action, String avant, String apres,
                         String effectuePar, String commentaire) {
        ValidationLog vlog = new ValidationLog();
        vlog.setDeclarationId(declarationId);
        vlog.setAction(action);
        vlog.setStatutAvant(avant);
        vlog.setStatutApres(apres);
        vlog.setEffectuePar(effectuePar);
        vlog.setCommentaire(commentaire);
        logRepository.save(vlog);
    }
}