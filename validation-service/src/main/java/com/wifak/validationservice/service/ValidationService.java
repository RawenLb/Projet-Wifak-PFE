package com.wifak.validationservice.service;

import com.wifak.validationservice.client.DeclarationClient;
import com.wifak.validationservice.dto.DeclarationDTO;
import com.wifak.validationservice.dto.ValidationStatsDTO;
import com.wifak.validationservice.dto.jira.CreateJiraTicketRequest;
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

    private final DeclarationClient declarationClient;
    private final JiraIntegrationFeignClient jiraClient;
    private final ValidationLogRepository logRepository;

    public ValidationService(DeclarationClient declarationClient,
                             JiraIntegrationFeignClient jiraClient,
                             ValidationLogRepository logRepository) {
        this.declarationClient = declarationClient;
        this.jiraClient = jiraClient;
        this.logRepository = logRepository;
    }

    // ══════════════════════════════════════════════════════════════
    // 1. SOUMETTRE POUR VALIDATION — GENEREE | REJETEE → EN_VALIDATION
    // ══════════════════════════════════════════════════════════════
    public DeclarationDTO submitForValidation(Long declarationId) {
        String currentUser = getCurrentUsername();
        log.info("📤 submitForValidation — ID: {}, user: {}", declarationId, currentUser);

        DeclarationDTO decl = declarationClient.getById(declarationId);
        validateStatut(decl.getStatut(), "GENEREE", "REJETEE");

        String statutAvant = decl.getStatut();

        DeclarationDTO updated = declarationClient.updateStatut(
                declarationId, "EN_VALIDATION", null, null);

        saveLog(declarationId, "SUBMIT", statutAvant, "EN_VALIDATION", currentUser, null);

        // ── Cas 1 : déclaration GENEREE → créer le ticket (TO DO) puis transitionner IN PROGRESS
        if ("GENEREE".equals(statutAvant)) {
            try {
                // Création du ticket → démarre en IDEA, JiraIntegrationService le passe en TO DO
                CreateJiraTicketRequest jiraReq = new CreateJiraTicketRequest(declarationId, currentUser);
                jiraClient.createTicket(jiraReq);
                log.info("🎫 Ticket Jira créé (TO DO) pour déclaration {}", declarationId);
            } catch (Exception e) {
                log.warn("⚠️ Jira non disponible — ticket non créé pour déclaration {} : {}",
                        declarationId, e.getMessage());
            }

            // Transition TO DO → IN PROGRESS
            try {
                TransitionJiraTicketRequest req = new TransitionJiraTicketRequest(
                        declarationId, "EN_VALIDATION", null, currentUser);
                jiraClient.transitionTicket(req);
                log.info("🔄 Ticket Jira transitionné → IN PROGRESS pour déclaration {}", declarationId);
            } catch (Exception e) {
                log.warn("⚠️ Jira transition EN_VALIDATION échouée pour déclaration {} : {}",
                        declarationId, e.getMessage());
            }
        }

        // ── Cas 2 : déclaration REJETEE resoumise → transition REJETÉE → IN PROGRESS (id=3)
        if ("REJETEE".equals(statutAvant)) {
            try {
                TransitionJiraTicketRequest req = new TransitionJiraTicketRequest(
                        declarationId, "RESOUMISE", null, currentUser);
                jiraClient.transitionTicket(req);
                log.info("🔁 Ticket Jira resoumis → IN PROGRESS pour déclaration {}", declarationId);
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

        // Transition Jira → VALIDÉE (id=41) — NON BLOQUANT
        try {
            TransitionJiraTicketRequest req = new TransitionJiraTicketRequest(
                    declarationId, "VALIDEE", null, currentUser);
            jiraClient.transitionTicket(req);
            log.info("🔄 Ticket Jira transitionné → VALIDÉE pour déclaration {}", declarationId);
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

        // Transition Jira → REJETÉE (id=4) — NON BLOQUANT
        try {
            TransitionJiraTicketRequest req = new TransitionJiraTicketRequest(
                    declarationId, "REJETEE", commentaire, currentUser);
            jiraClient.transitionTicket(req);
            log.info("🔄 Ticket Jira transitionné → REJETÉE pour déclaration {}", declarationId);
        } catch (Exception e) {
            log.warn("⚠️ Jira sync échouée pour rejet déclaration {} : {}",
                    declarationId, e.getMessage());
        }

        return updated;
    }

    // ══════════════════════════════════════════════════════════════
    // 4. MARQUER COMME ENVOYÉE — VALIDEE → ENVOYEE
    // ══════════════════════════════════════════════════════════════
    public DeclarationDTO markAsSent(Long declarationId) {
        String currentUser = getCurrentUsername();
        log.info("📨 markAsSent — ID: {}, user: {}", declarationId, currentUser);

        DeclarationDTO decl = declarationClient.getById(declarationId);
        validateStatut(decl.getStatut(), "VALIDEE");

        DeclarationDTO updated = declarationClient.updateStatut(
                declarationId, "ENVOYEE", null, null);

        saveLog(declarationId, "SEND", decl.getStatut(), "ENVOYEE", currentUser, null);

        // Transition Jira → ENVOYÉE (id=2) — NON BLOQUANT
        try {
            TransitionJiraTicketRequest req = new TransitionJiraTicketRequest(
                    declarationId, "ENVOYEE", null, currentUser);
            jiraClient.transitionTicket(req);
            log.info("🔄 Ticket Jira transitionné → ENVOYÉE pour déclaration {}", declarationId);
        } catch (Exception e) {
            log.warn("⚠️ Jira sync échouée pour envoi déclaration {} : {}",
                    declarationId, e.getMessage());
        }

        return updated;
    }

    // ══════════════════════════════════════════════════════════════
    // 5. PENDING — déclarations EN_VALIDATION
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