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
 * Webhook interne â€” appelÃ© par le validation-service via Feign.
 *
 * Base URL : /api/notifications
 * RoutÃ© depuis API Gateway (8088) â†’ notification-service (8085)
 *
 * Ces endpoints sont internes : ils ne nÃ©cessitent pas de JWT utilisateur
 * mais sont protÃ©gÃ©s par un secret partagÃ© (X-Internal-Secret header).
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;
    private final EmailService emailService;  // â† ajouter Ã§a

    public NotificationController(NotificationService notificationService, EmailService emailService) {
        this.notificationService = notificationService;
        this.emailService = emailService;  // â† ajouter Ã§a
    }
    // 1. DÃ‰CLARATION EN ATTENTE â†’ notifier les managers
    @PostMapping("/pending-validation")
    public ResponseEntity<Void> notifyPendingValidation(@RequestBody NotificationRequest request) {
        log.info("ðŸ“¥ [POST] /api/notifications/pending-validation â€” dÃ©claration {}", request.getDeclarationId());
        notificationService.notifyManagerPendingValidation(request.getDeclarationId());
        return ResponseEntity.ok().build();
    }
    // 2. DÃ‰CLARATION REJETÃ‰E â†’ notifier l'agent
    @PostMapping("/rejection")
    public ResponseEntity<Void> notifyRejection(@RequestBody NotificationRequest request) {
        log.info("ðŸ“¥ [POST] /api/notifications/rejection â€” dÃ©claration {}", request.getDeclarationId());
        notificationService.notifyAgentDeclarationRejected(
                request.getDeclarationId(),
                request.getCommentaire()
        );
        return ResponseEntity.ok().build();
    }
    // 3. DÃ‰CLENCHER MANUELLEMENT LA VÃ‰RIFICATION DES Ã‰CHÃ‰ANCES
    //    (Pratique en dÃ©veloppement / tests)
    @PostMapping("/check-deadlines")
    public ResponseEntity<Void> triggerDeadlineCheck() {
        log.info("ðŸ“¥ [POST] /api/notifications/check-deadlines â€” dÃ©clenchement manuel");
        notificationService.checkUpcomingDeadlines();
        return ResponseEntity.ok().build();
    }
    @PostMapping("/test-email")
    public ResponseEntity<String> testEmail(@RequestParam String to) {
        log.info("ðŸ“¥ [POST] /api/notifications/test-email â†’ envoi Ã  {}", to);
        Context ctx = new Context();
        ctx.setVariable("managerName", "Test Manager");
        ctx.setVariable("declarationCode", "BCT_TEST_001");
        ctx.setVariable("periode", "2024-Q1");
        ctx.setVariable("generePar", "agent.test");
        ctx.setVariable("appUrl", "http://localhost:4200");
        emailService.sendHtmlEmail(to, "Test Email Wifak", "pending-validation", ctx);
        return ResponseEntity.ok("Email envoyÃ© Ã  " + to);
    }

}