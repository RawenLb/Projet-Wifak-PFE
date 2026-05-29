package com.wifak.notificationservice.controller;

import com.wifak.notificationservice.dto.NotificationRequest;
import com.wifak.notificationservice.service.EmailService;
import com.wifak.notificationservice.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.context.Context;
/**
 * Webhook interne — appelé par le validation-service via Feign.
 *
 * Base URL : /api/notifications
 * Routé depuis API Gateway (8088) → notification-service (8085)
 *
 * Ces endpoints sont internes : ils ne nécessitent pas de JWT utilisateur
 * mais sont protégés par un secret partagé (X-Internal-Secret header).
 */
@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "http://localhost:4200")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;
    private final EmailService emailService;  // ← ajouter ça

    public NotificationController(NotificationService notificationService, EmailService emailService) {
        this.notificationService = notificationService;
        this.emailService = emailService;  // ← ajouter ça
    }
    // 1. DÉCLARATION EN ATTENTE → notifier les managers
    @PostMapping("/pending-validation")
    public ResponseEntity<Void> notifyPendingValidation(@RequestBody NotificationRequest request) {
        log.info("📥 [POST] /api/notifications/pending-validation — déclaration {}", request.getDeclarationId());
        notificationService.notifyManagerPendingValidation(request.getDeclarationId());
        return ResponseEntity.ok().build();
    }
    // 2. DÉCLARATION REJETÉE → notifier l'agent
    @PostMapping("/rejection")
    public ResponseEntity<Void> notifyRejection(@RequestBody NotificationRequest request) {
        log.info("📥 [POST] /api/notifications/rejection — déclaration {}", request.getDeclarationId());
        notificationService.notifyAgentDeclarationRejected(
                request.getDeclarationId(),
                request.getCommentaire()
        );
        return ResponseEntity.ok().build();
    }
    // 3. DÉCLENCHER MANUELLEMENT LA VÉRIFICATION DES ÉCHÉANCES
    //    (Pratique en développement / tests)
    @PostMapping("/check-deadlines")
    public ResponseEntity<Void> triggerDeadlineCheck() {
        log.info("📥 [POST] /api/notifications/check-deadlines — déclenchement manuel");
        notificationService.checkUpcomingDeadlines();
        return ResponseEntity.ok().build();
    }
    @PostMapping("/test-email")
    public ResponseEntity<String> testEmail(@RequestParam String to) {
        log.info("📥 [POST] /api/notifications/test-email → envoi à {}", to);
        Context ctx = new Context();
        ctx.setVariable("managerName", "Test Manager");
        ctx.setVariable("declarationCode", "BCT_TEST_001");
        ctx.setVariable("periode", "2024-Q1");
        ctx.setVariable("generePar", "agent.test");
        ctx.setVariable("appUrl", "http://localhost:4200");
        emailService.sendHtmlEmail(to, "Test Email Wifak", "pending-validation", ctx);
        return ResponseEntity.ok("Email envoyé à " + to);
    }

}