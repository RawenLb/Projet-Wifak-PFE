package com.wifak.notificationservice.service;

import com.wifak.notificationservice.client.DeclarationClient;
import com.wifak.notificationservice.client.KeycloakUserClient;
import com.wifak.notificationservice.dto.DeclarationDTO;
import com.wifak.notificationservice.dto.UserEmailDTO;
import com.wifak.notificationservice.entities.NotificationLog;
import com.wifak.notificationservice.repositories.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

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

    // ══════════════════════════════════════════════════════════════
    // 1. NOTIFICATION MANAGER — déclaration en attente de validation
    //    Appelée depuis le validation-service via REST (webhook interne)
    // ══════════════════════════════════════════════════════════════

    public void notifyManagerPendingValidation(Long declarationId) {
        log.info("📧 [PENDING] Notification manager — déclaration {}", declarationId);

        try {
            DeclarationDTO decl = declarationClient.getById(declarationId);

            // Récupère tous les managers depuis Keycloak
            List<UserEmailDTO> managers = keycloakUserClient.getUsersByRole("ROLE_MANAGER");

            if (managers.isEmpty()) {
                log.warn("⚠️  Aucun manager trouvé dans Keycloak pour la déclaration {}", declarationId);
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
                ctx.setVariable("appUrl", "http://localhost:4200/declarations/" + declarationId);

                emailService.sendHtmlEmail(
                        manager.getEmail(),
                        "📋 Déclaration en attente de validation — " + buildDeclarationCode(decl),
                        "pending-validation",
                        ctx
                );

                saveLog(declarationId, "PENDING_VALIDATION", manager.getEmail(), "OK", null);
            }

        } catch (Exception e) {
            log.error("❌ Erreur notification manager (déclaration {}) : {}", declarationId, e.getMessage(), e);
            saveLog(declarationId, "PENDING_VALIDATION", "managers", "ERROR", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 2. NOTIFICATION AGENT — déclaration rejetée
    //    Appelée depuis le validation-service via REST (webhook interne)
    // ══════════════════════════════════════════════════════════════

    public void notifyAgentDeclarationRejected(Long declarationId, String commentaireRejet) {
        log.info("📧 [REJET] Notification agent — déclaration {}", declarationId);

        try {
            DeclarationDTO decl = declarationClient.getById(declarationId);
            String agentUsername = decl.getGenerePar();

            UserEmailDTO agent = keycloakUserClient.getUserByUsername(agentUsername);

            if (agent == null || agent.getEmail() == null || agent.getEmail().isBlank()) {
                log.warn("⚠️  Email introuvable pour l'agent '{}' (déclaration {})", agentUsername, declarationId);
                return;
            }

            Context ctx = new Context();
            ctx.setVariable("agentName", agent.getFirstName());
            ctx.setVariable("declarationCode", buildDeclarationCode(decl));
            ctx.setVariable("periode", decl.getPeriode());
            ctx.setVariable("commentaireRejet", commentaireRejet != null ? commentaireRejet : "Aucun commentaire fourni.");
            ctx.setVariable("rejectedBy", decl.getValidePar());
            ctx.setVariable("appUrl", "http://localhost:4200/declarations/" + declarationId);

            emailService.sendHtmlEmail(
                    agent.getEmail(),
                    "❌ Déclaration rejetée — Action requise — " + buildDeclarationCode(decl),
                    "declaration-rejected",
                    ctx
            );

            saveLog(declarationId, "REJECTION", agent.getEmail(), "OK", null);

        } catch (Exception e) {
            log.error("❌ Erreur notification rejet (déclaration {}) : {}", declarationId, e.getMessage(), e);
            saveLog(declarationId, "REJECTION", "agent", "ERROR", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 3. ALERTE ÉCHÉANCE — tâche planifiée tous les jours à 8h00
    //    Vérifie les déclarations à échéance dans 2 jours ou moins
    // ══════════════════════════════════════════════════════════════

    @Scheduled(cron = "0 0 8 * * *")   // tous les jours à 08:00
    public void checkUpcomingDeadlines() {
        log.info("⏰ [SCHEDULER] Vérification des échéances — {}", LocalDate.now());

        try {
            List<DeclarationDTO> all = declarationClient.getAll();

            List<DeclarationDTO> nearDeadline = all.stream()
                    .filter(d -> d.getDateFin() != null)
                    .filter(d -> !isTerminal(d.getStatut()))          // exclut ENVOYEE / VALIDEE
                    .filter(d -> {
                        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), d.getDateFin());
                        return daysLeft >= 0 && daysLeft <= 2;        // aujourd'hui, demain ou après-demain
                    })
                    .toList();

            log.info("⚠️  {} déclaration(s) proche(s) de l'échéance", nearDeadline.size());

            for (DeclarationDTO decl : nearDeadline) {
                sendDeadlineAlert(decl);
            }

        } catch (Exception e) {
            log.error("❌ Erreur lors de la vérification des échéances : {}", e.getMessage(), e);
        }
    }

    // ── Helper : envoi de l'alerte d'échéance ────────────────────

    private void sendDeadlineAlert(DeclarationDTO decl) {
        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), decl.getDateFin());
        String agentUsername = decl.getGenerePar();

        try {
            UserEmailDTO agent = keycloakUserClient.getUserByUsername(agentUsername);
            if (agent == null || agent.getEmail() == null || agent.getEmail().isBlank()) {
                log.warn("⚠️  Email introuvable pour '{}' (déclaration {})", agentUsername, decl.getId());
                return;
            }

            Context ctx = new Context();
            ctx.setVariable("agentName", agent.getFirstName());
            ctx.setVariable("declarationCode", buildDeclarationCode(decl));
            ctx.setVariable("periode", decl.getPeriode());
            ctx.setVariable("dateFin", decl.getDateFin());
            ctx.setVariable("daysLeft", daysLeft);
            ctx.setVariable("statut", decl.getStatut());
            ctx.setVariable("appUrl", "http://localhost:4200/declarations/" + decl.getId());

            String subject = daysLeft == 0
                    ? "🔴 URGENT — Échéance aujourd'hui — " + buildDeclarationCode(decl)
                    : "⚠️ Échéance dans " + daysLeft + " jour(s) — " + buildDeclarationCode(decl);

            emailService.sendHtmlEmail(agent.getEmail(), subject, "deadline-alert", ctx);

            saveLog(decl.getId(), "DEADLINE_ALERT", agent.getEmail(), "OK", "J+" + daysLeft);

        } catch (Exception e) {
            log.error("❌ Erreur alerte échéance (déclaration {}) : {}", decl.getId(), e.getMessage(), e);
            saveLog(decl.getId(), "DEADLINE_ALERT", agentUsername, "ERROR", e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

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
            log.error("⚠️  Impossible de sauvegarder le log de notification : {}", e.getMessage());
        }
    }
}