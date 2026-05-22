package com.wifak.validationservice.service;

import com.wifak.validationservice.client.NotificationClient;
import com.wifak.validationservice.dto.AiValidationResult;
import com.wifak.validationservice.dto.DeclarationDTO;
import com.wifak.validationservice.dto.ValidationStatsDTO;
import com.wifak.validationservice.dto.jira.TransitionJiraTicketRequest;
import com.wifak.validationservice.entities.Declaration;
import com.wifak.validationservice.entities.ValidationLog;
import com.wifak.validationservice.feign.JiraIntegrationFeignClient;
import com.wifak.validationservice.repositories.ValidationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Workflow de validation des déclarations.
 * Utilise DeclarationService directement (même microservice).
 */
@Service
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    private final DeclarationService         declarationService;
    private final JiraIntegrationFeignClient jiraClient;
    private final ValidationLogRepository   logRepository;
    private final NotificationClient        notificationClient;

    @Autowired
    private AiDeclarationService aiDeclarationService;

    public ValidationService(DeclarationService declarationService,
                             JiraIntegrationFeignClient jiraClient,
                             ValidationLogRepository logRepository,
                             NotificationClient notificationClient) {
        this.declarationService   = declarationService;
        this.jiraClient           = jiraClient;
        this.logRepository        = logRepository;
        this.notificationClient   = notificationClient;
    }

    // ══════════════════════════════════════════════════════════════
    // 1. SOUMETTRE — GENEREE | REJETEE → EN_VALIDATION
    // ══════════════════════════════════════════════════════════════
    public Declaration submitForValidation(Long declarationId, String correctionComment) {
        String currentUser = getCurrentUsername();
        log.info("📤 submitForValidation — ID: {}, user: {}", declarationId, currentUser);

        Declaration decl = declarationService.findById(declarationId);
        validateStatut(decl.getStatut().name(), "GENEREE", "REJETEE");

        if (!currentUser.equals(decl.getGenerePar()))
            throw new IllegalStateException("Vous ne pouvez soumettre que vos propres déclarations");

        String statutAvant = decl.getStatut().name();
        Declaration updated = declarationService.updateStatut(declarationId, "EN_VALIDATION", null, null);
        saveLog(declarationId, "SUBMIT", statutAvant, "EN_VALIDATION", currentUser, correctionComment);

        if ("GENEREE".equals(statutAvant)) {
            try {
                Boolean exists = jiraClient.ticketExists(declarationId);
                if (Boolean.TRUE.equals(exists)) {
                    jiraClient.transitionTicket(new TransitionJiraTicketRequest(declarationId, "EN_VALIDATION", null, currentUser));
                }
            } catch (Exception e) { log.warn("⚠️ Jira transition échouée: {}", e.getMessage()); }
        }

        if ("REJETEE".equals(statutAvant)) {
            try {
                jiraClient.transitionTicket(new TransitionJiraTicketRequest(declarationId, "RESOUMISE", null, currentUser));
            } catch (Exception e) { log.warn("⚠️ Jira resoumission échouée: {}", e.getMessage()); }
        }

        // Notifier les managers qu'une déclaration est en attente de validation
        try {
            notificationClient.notifyPendingValidation(Map.of("declarationId", declarationId));
            log.info("📧 Notification managers envoyée pour déclaration {}", declarationId);
        } catch (Exception e) {
            log.warn("⚠️ Notification pending-validation échouée: {}", e.getMessage());
        }

        return updated;
    }

    // ══════════════════════════════════════════════════════════════
    // 2. VALIDER — EN_VALIDATION → VALIDEE
    // ══════════════════════════════════════════════════════════════
    public Declaration validateDeclaration(Long declarationId) {
        String currentUser = getCurrentUsername();
        log.info("✅ validateDeclaration — ID: {}, manager: {}", declarationId, currentUser);

        Declaration decl = declarationService.findById(declarationId);
        validateStatut(decl.getStatut().name(), "EN_VALIDATION");

        Declaration updated = declarationService.updateStatut(declarationId, "VALIDEE", null, currentUser);
        saveLog(declarationId, "VALIDATE", decl.getStatut().name(), "VALIDEE", currentUser, null);

        try {
            jiraClient.transitionTicket(new TransitionJiraTicketRequest(declarationId, "VALIDEE", null, currentUser));
        } catch (Exception e) { log.warn("⚠️ Jira sync échouée: {}", e.getMessage()); }

        return updated;
    }

    // ══════════════════════════════════════════════════════════════
    // 3. REJETER — EN_VALIDATION → REJETEE
    // ══════════════════════════════════════════════════════════════
    public Declaration rejectDeclaration(Long declarationId, String commentaire) {
        String currentUser = getCurrentUsername();
        log.info("❌ rejectDeclaration — ID: {}, manager: {}", declarationId, currentUser);

        if (commentaire == null || commentaire.isBlank())
            throw new IllegalArgumentException("Le commentaire de rejet est obligatoire");

        Declaration decl = declarationService.findById(declarationId);
        validateStatut(decl.getStatut().name(), "EN_VALIDATION");

        Declaration updated = declarationService.updateStatut(declarationId, "REJETEE", commentaire, currentUser);
        saveLog(declarationId, "REJECT", decl.getStatut().name(), "REJETEE", currentUser, commentaire);

        try {
            jiraClient.transitionTicket(new TransitionJiraTicketRequest(declarationId, "REJETEE", commentaire, currentUser));
        } catch (Exception e) { log.warn("⚠️ Jira sync échouée: {}", e.getMessage()); }

        // Notifier l'agent que sa déclaration a été rejetée
        try {
            notificationClient.notifyRejection(Map.of(
                "declarationId", declarationId,
                "commentaire", commentaire != null ? commentaire : ""
            ));
            log.info("📧 Notification rejet envoyée pour déclaration {}", declarationId);
        } catch (Exception e) {
            log.warn("⚠️ Notification rejection échouée: {}", e.getMessage());
        }

        return updated;
    }

    // ══════════════════════════════════════════════════════════════
    // 4. ENVOYER — VALIDEE → ENVOYEE
    // ══════════════════════════════════════════════════════════════
    public Declaration markAsSent(Long declarationId) {
        String currentUser = getCurrentUsername();
        log.info("📨 markAsSent — ID: {}, user: {}", declarationId, currentUser);

        Declaration decl = declarationService.findById(declarationId);
        validateStatut(decl.getStatut().name(), "VALIDEE");

        Declaration updated = declarationService.updateStatut(declarationId, "ENVOYEE", null, null);
        saveLog(declarationId, "SEND", decl.getStatut().name(), "ENVOYEE", currentUser, null);

        try {
            jiraClient.transitionTicket(new TransitionJiraTicketRequest(declarationId, "ENVOYEE", null, currentUser));
        } catch (Exception e) { log.warn("⚠️ Jira sync échouée: {}", e.getMessage()); }

        return updated;
    }

    // ══════════════════════════════════════════════════════════════
    // 5. PENDING
    // ══════════════════════════════════════════════════════════════
    public List<Declaration> getPendingDeclarations() {
        return declarationService.getAllDeclarations().stream()
                .filter(d -> d.getStatut() == Declaration.DeclarationStatut.EN_VALIDATION)
                .collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════════════════════
    // 6. STATS
    // ══════════════════════════════════════════════════════════════
    public DeclarationService.DeclarationStats getStats() {
        return declarationService.getStats();
    }

    // ══════════════════════════════════════════════════════════════
    // 7. HISTORY
    // ══════════════════════════════════════════════════════════════
    public List<ValidationLog> getHistory(Long declarationId) {
        return logRepository.findByDeclarationIdOrderByDateActionDesc(declarationId);
    }

    // ══════════════════════════════════════════════════════════════
    // 8. AI ANALYSIS
    // ══════════════════════════════════════════════════════════════
    public AiValidationResult analyzeWithAi(Long declarationId) {
        log.info("🤖 analyzeWithAi — ID: {}", declarationId);
        Declaration decl = declarationService.findById(declarationId);
        return aiDeclarationService.analyzeDeclaration(
                decl.getContenuFichier(),
                decl.getNomFichier()
        );
    }

    // ══════════════════════════════════════════════════════════════
    // 9. AI SUMMARY
    // ══════════════════════════════════════════════════════════════
    public Map<String, Object> getAiSummary(Long declarationId) {
        log.info("📊 getAiSummary — ID: {}", declarationId);
        Declaration decl = declarationService.findById(declarationId);
        return aiDeclarationService.buildAiSummary(
                decl.getContenuFichier(),
                decl.getNomFichier()
        );
    }

    // ══════════════════════════════════════════════════════════════
    // 10. COMPARAISON PÉRIODE PRÉCÉDENTE
    // ══════════════════════════════════════════════════════════════
    public Map<String, Object> compareWithPrevious(Long declarationId, Long previousDeclarationId) {
        log.info("📈 compareWithPrevious — ID: {} vs {}", declarationId, previousDeclarationId);
        Declaration curr = declarationService.findById(declarationId);
        String prevContent = null;
        try {
            Declaration prev = declarationService.findById(previousDeclarationId);
            prevContent = prev.getContenuFichier();
        } catch (Exception e) {
            log.warn("⚠️ Déclaration précédente {} introuvable: {}", previousDeclarationId, e.getMessage());
        }
        return aiDeclarationService.compareWithPrevious(curr.getContenuFichier(), prevContent);
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
        for (String s : allowed) if (s.equals(current)) return;
        throw new IllegalStateException(
                String.format("Statut invalide '%s'. Attendu : %s", current, String.join(" ou ", allowed)));
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
