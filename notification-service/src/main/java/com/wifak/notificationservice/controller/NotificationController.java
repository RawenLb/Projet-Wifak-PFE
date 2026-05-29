package com.wifak.notificationservice.controller;

import com.wifak.notificationservice.dto.NotificationRequest;
import com.wifak.notificationservice.service.EmailService;
import com.wifak.notificationservice.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.context.Context;
/**
 * Webhook interne Ã¢â‚¬â€ appelÃƒÂ© par le validation-service via Feign.
 *
 * Base URL : /api/notifications
 * RoutÃƒÂ© depuis API Gateway (8088) Ã¢â€ â€™ notification-service (8085)
 *
 * Ces endpoints sont internes : ils ne nÃƒÂ©cessitent pas de JWT utilisateur
 * mais sont protÃƒÂ©gÃƒÂ©s par un secret partagÃƒÂ© (X-Internal-Secret header).
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;
    private final EmailService emailService;  // Ã¢â€ Â ajouter ÃƒÂ§a

    public NotificationController(NotificationService notificationService, EmailService emailService) {
        this.notificationService = notificationService;
        this.emailService = emailService;  // Ã¢â€ Â ajouter ÃƒÂ§a
    }
    // 1. DÃƒâ€°CLARATION EN ATTENTE Ã¢â€ â€™ notifier les managers
    @PostMapping("/pending-validation")
    public ResponseEntity<Void> notifyPendingValidation(@RequestBody NotificationRequest request) {
        log.info("Ã°Å¸â€œÂ¥ [POST] /api/notifications/pending-validation Ã¢â‚¬â€ dÃƒÂ©claration {}", request.getDeclarationId());
        notificationService.notifyManagerPendingValidation(request.getDeclarationId());
        return ResponseEntity.ok().build();
    }
    // 2. DÃƒâ€°CLARATION REJETÃƒâ€°E Ã¢â€ â€™ notifier l'agent
    @PostMapping("/rejection")
    public ResponseEntity<Void> notifyRejection(@RequestBody NotificationRequest request) {
        log.info("Ã°Å¸â€œÂ¥ [POST] /api/notifications/rejection Ã¢â‚¬â€ dÃƒÂ©claration {}", request.getDeclarationId());
        notificationService.notifyAgentDeclarationRejected(
                request.getDeclarationId(),
                request.getCommentaire()
        );
        return ResponseEntity.ok().build();
    }
    // 3. DÃƒâ€°CLENCHER MANUELLEMENT LA VÃƒâ€°RIFICATION DES Ãƒâ€°CHÃƒâ€°ANCES
    //    (Pratique en dÃƒÂ©veloppement / tests)
    @PostMapping("/check-deadlines")
    public ResponseEntity<Void> triggerDeadlineCheck() {
        log.info("Ã°Å¸â€œÂ¥ [POST] /api/notifications/check-deadlines Ã¢â‚¬â€ dÃƒÂ©clenchement manuel");
        notificationService.checkUpcomingDeadlines();
        return ResponseEntity.ok().build();
    }
    @PostMapping("/test-email")
    public ResponseEntity<String> testEmail(@RequestParam String to) {
        log.info("Ã°Å¸â€œÂ¥ [POST] /api/notifications/test-email Ã¢â€ â€™ envoi ÃƒÂ  {}", to);
        Context ctx = new Context();
        ctx.setVariable("managerName", "Test Manager");
        ctx.setVariable("declarationCode", "BCT_TEST_001");
        ctx.setVariable("periode", "2024-Q1");
        ctx.setVariable("generePar", "agent.test");
        ctx.setVariable("appUrl", "http://localhost:4200");
        emailService.sendHtmlEmail(to, "Test Email Wifak", "pending-validation", ctx);
        return ResponseEntity.ok("Email envoyÃƒÂ© ÃƒÂ  " + to);
    }

}