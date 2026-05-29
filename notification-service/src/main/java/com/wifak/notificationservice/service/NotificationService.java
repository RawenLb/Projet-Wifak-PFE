package com.wifak.notificationservice.service;

import com.wifak.notificationservice.client.DeclarationClient;
import com.wifak.notificationservice.client.KeycloakUserClient;
import com.wifak.notificationservice.dto.DeclarationDTO;
import com.wifak.notificationservice.dto.UserEmailDTO;
import com.wifak.notificationservice.entities.NotificationLog;
import com.wifak.notificationservice.repositories.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Value("${app.frontend-url:http://localhost:4200}")
    private String appFrontendUrl;

    private final EmailService emailService;
    private final DeclarationClient declarationClient;
    private final KeycloakUserClient keycloakUserClient;
    private final NotificationLogRepository notificationLogRepository;

    public NotificationService(EmailService emailService,
                               DeclarationClient declarationClient,
                               KeycloakUserClient keycloakUserClient,
                               NotificationLogRepository notificationLogRepository) {
        this.emailService = emailService;
        this.declarationClient = declarationClient;
        this.keycloakUserClient = keycloakUserClient;
        this.notificationLogRepository = notificationLogRepository;
    }
    // 1. NOTIFICATION MANAGER â€” dÃ©claration en attente de validation
    //    AppelÃ©e depuis le validation-service via REST (webhook interne)
    public void notifyManagerPendingValidation(Long declarationId) {
        log.info("ðŸ“§ [PENDING] Notification manager â€” dÃ©claration {}", declarationId);

        try {
            DeclarationDTO decl = declarationClient.getById(declarationId);

            // RÃ©cupÃ¨re tous les managers depuis Keycloak
            List<UserEmailDTO> managers = keycloakUserClient.getUsersByRole("ROLE_MANAGER");

            if (managers.isEmpty()) {
                log.warn("âš ï¸  Aucun manager trouvÃ© dans Keycloak pour la dÃ©claration {}", declarationId);
                return;
            }

            for (UserEmailDTO manager : managers) {
                if (manager.getEmail() == null || manager.getEmail().isBlank()) continue;

                Context ctx = new Context();
                ctx.setVariable("managerName", manager.getFirstName());
                ctx.setVariable("declarationCode", buildDeclarationCode(decl));
                ctx.setVariable("periode", decl.getPeriode());
                ctx.setVariable("generePar", decl.getGenerePar());
                ctx.setVariable("dateGeneration", decl.getDateGeneration());
                ctx.setVariable("appUrl", appFrontendUrl + "/declarations/" + declarationId);

                emailService.sendHtmlEmail(
                        manager.getEmail(),
                        "ðŸ“‹ DÃ©claration en attente de validation â€” " + buildDeclarationCode(decl),
                        "pending-validation",
                        ctx
                );

                saveLog(declarationId, "PENDING_VALIDATION", manager.getEmail(), "OK", null);
            }

        } catch (Exception e) {
            log.error("âŒ Erreur notification manager (dÃ©claration {}) : {}", declarationId, e.getMessage(), e);
            saveLog(declarationId, "PENDING_VALIDATION", "managers", "ERROR", e.getMessage());
        }
    }
    // 2. NOTIFICATION AGENT â€” dÃ©claration rejetÃ©e
    //    AppelÃ©e depuis le validation-service via REST (webhook interne)
    public void notifyAgentDeclarationRejected(Long declarationId, String commentaireRejet) {
        log.info("ðŸ“§ [REJET] Notification agent â€” dÃ©claration {}", declarationId);

        try {
            DeclarationDTO decl = declarationClient.getById(declarationId);
            String agentUsername = decl.getGenerePar();

            UserEmailDTO agent = keycloakUserClient.getUserByUsername(agentUsername);

            if (agent == null || agent.getEmail() == null || agent.getEmail().isBlank()) {
                log.warn("âš ï¸  Email introuvable pour l'agent '{}' (dÃ©claration {})", agentUsername, declarationId);
                return;
            }

            Context ctx = new Context();
            ctx.setVariable("agentName", agent.getFirstName());
            ctx.setVariable("declarationCode", buildDeclarationCode(decl));
            ctx.setVariable("periode", decl.getPeriode());
            ctx.setVariable("commentaireRejet", commentaireRejet != null ? commentaireRejet : "Aucun commentaire fourni.");
            ctx.setVariable("rejectedBy", decl.getValidePar());
            ctx.setVariable("appUrl", appFrontendUrl + "/declarations/" + declarationId);

            emailService.sendHtmlEmail(
                    agent.getEmail(),
                    "âŒ DÃ©claration rejetÃ©e â€” Action requise â€” " + buildDeclarationCode(decl),
                    "declaration-rejected",
                    ctx
            );

            saveLog(declarationId, "REJECTION", agent.getEmail(), "OK", null);

        } catch (Exception e) {
            log.error("âŒ Erreur notification rejet (dÃ©claration {}) : {}", declarationId, e.getMessage(), e);
            saveLog(declarationId, "REJECTION", "agent", "ERROR", e.getMessage());
        }
    }
    // 3. ALERTE Ã‰CHÃ‰ANCE â€” tÃ¢che planifiÃ©e tous les jours Ã  8h00
    //    VÃ©rifie les dÃ©clarations Ã  Ã©chÃ©ance dans 2 jours ou moins
    @Scheduled(cron = "0 0 8 * * *")   // tous les jours Ã  08:00
    public void checkUpcomingDeadlines() {
        log.info("â° [SCHEDULER] VÃ©rification des Ã©chÃ©ances â€” {}", LocalDate.now());

        try {
            List<DeclarationDTO> all = declarationClient.getAll();

            List<DeclarationDTO> nearDeadline = all.stream()
                    .filter(d -> d.getDateFin() != null)
                    .filter(d -> !isTerminal(d.getStatut()))          // exclut ENVOYEE / VALIDEE
                    .filter(d -> {
                        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), d.getDateFin());
                        return daysLeft >= 0 && daysLeft <= 2;        // aujourd'hui, demain ou aprÃ¨s-demain
                    })
                    .toList();

            log.info("âš ï¸  {} dÃ©claration(s) proche(s) de l'Ã©chÃ©ance", nearDeadline.size());

            for (DeclarationDTO decl : nearDeadline) {
                sendDeadlineAlert(decl);
            }

        } catch (Exception e) {
            log.error("âŒ Erreur lors de la vÃ©rification des Ã©chÃ©ances : {}", e.getMessage(), e);
        }
    }

    // â”€â”€ Helper : envoi de l'alerte d'Ã©chÃ©ance â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void sendDeadlineAlert(DeclarationDTO decl) {
        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), decl.getDateFin());
        String agentUsername = decl.getGenerePar();

        try {
            UserEmailDTO agent = keycloakUserClient.getUserByUsername(agentUsername);
            if (agent == null || agent.getEmail() == null || agent.getEmail().isBlank()) {
                log.warn("âš ï¸  Email introuvable pour '{}' (dÃ©claration {})", agentUsername, decl.getId());
                return;
            }

            Context ctx = new Context();
            ctx.setVariable("agentName", agent.getFirstName());
            ctx.setVariable("declarationCode", buildDeclarationCode(decl));
            ctx.setVariable("periode", decl.getPeriode());
            ctx.setVariable("dateFin", decl.getDateFin());
            ctx.setVariable("daysLeft", daysLeft);
            ctx.setVariable("statut", decl.getStatut());
            ctx.setVariable("appUrl", appFrontendUrl + "/declarations/" + decl.getId());

            String subject = daysLeft == 0
                    ? "ðŸ”´ URGENT â€” Ã‰chÃ©ance aujourd'hui â€” " + buildDeclarationCode(decl)
                    : "âš ï¸ Ã‰chÃ©ance dans " + daysLeft + " jour(s) â€” " + buildDeclarationCode(decl);

            emailService.sendHtmlEmail(agent.getEmail(), subject, "deadline-alert", ctx);

            saveLog(decl.getId(), "DEADLINE_ALERT", agent.getEmail(), "OK", "J+" + daysLeft);

        } catch (Exception e) {
            log.error("âŒ Erreur alerte Ã©chÃ©ance (dÃ©claration {}) : {}", decl.getId(), e.getMessage(), e);
            saveLog(decl.getId(), "DEADLINE_ALERT", agentUsername, "ERROR", e.getMessage());
        }
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private String buildDeclarationCode(DeclarationDTO decl) {
        if (decl.getDeclarationType() != null && decl.getDeclarationType().getCode() != null) {
            return decl.getDeclarationType().getCode() + "_" + decl.getId();
        }
        return "BCT_" + decl.getId();
    }

    private boolean isTerminal(String statut) {
        return "ENVOYEE".equals(statut) || "VALIDEE".equals(statut);
    }

    private void saveLog(Long declarationId, String type, String recipient, String statut, String detail) {
        try {
            NotificationLog entry = new NotificationLog();
            entry.setDeclarationId(declarationId);
            entry.setNotificationType(type);
            entry.setRecipient(recipient);
            entry.setStatut(statut);
            entry.setDetail(detail);
            notificationLogRepository.save(entry);
        } catch (Exception e) {
            log.error("âš ï¸  Impossible de sauvegarder le log de notification : {}", e.getMessage());
        }
    }
}