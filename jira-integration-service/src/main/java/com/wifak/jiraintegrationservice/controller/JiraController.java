package com.wifak.jiraintegrationservice.controller;

import com.wifak.jiraintegrationservice.dto.*;
import com.wifak.jiraintegrationservice.service.JiraIntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jira")
public class JiraController {

    private static final Logger log = LoggerFactory.getLogger(JiraController.class);

    private final JiraIntegrationService service;

    public JiraController(JiraIntegrationService service) {
        this.service = service;
    }

    // ================= CREATE =================
    @PostMapping("/tickets")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN', 'INTERNAL')")
    public ResponseEntity<JiraTicketResponseDTO> createTicket(
            @RequestBody CreateTicketRequest req) {

        log.info("ðŸŽ« Create ticket for declaration {}", req.getDeclarationId());

        JiraTicketResponseDTO response =
                service.createTicketForDeclaration(
                        req.getDeclarationId(),
                        req.getSubmittedBy()
                );

        return ResponseEntity.ok(response);
    }

    // ================= TRANSITION =================
    @PostMapping("/tickets/transition")
    @PreAuthorize("hasAnyRole('AGENT','MANAGER', 'ADMIN', 'INTERNAL')")
    public ResponseEntity<JiraTicketResponseDTO> transitionTicket(
            @RequestBody TransitionTicketRequest req) {

        log.info("ðŸ”„ Transition declaration {} â†’ {}",
                req.getDeclarationId(),
                req.getNewBctStatut());

        return ResponseEntity.ok(service.transitionTicket(req));
    }

    // ================= GET =================
    @GetMapping("/tickets/{declarationId}")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN', 'INTERNAL')")
    public ResponseEntity<JiraTicketResponseDTO> getTicket(
            @PathVariable Long declarationId) {

        log.info("ðŸ” Get ticket for declaration {}", declarationId);

        return service.findTicketForDeclaration(declarationId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.warn("âš ï¸ Ticket not found for declaration {}", declarationId);
                    return ResponseEntity.notFound().build(); // âœ… FIX
                });
    }

    // ================= EXISTS =================
    @GetMapping("/tickets/{declarationId}/exists")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN', 'INTERNAL')")
    public ResponseEntity<Boolean> ticketExists(
            @PathVariable Long declarationId) {

        return ResponseEntity.ok(
                service.ticketExistsForDeclaration(declarationId)
        );
    }

    // ================= WEBHOOK =================
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleJiraWebhook(
            @RequestBody(required = false) JiraWebhookPayload payload) {

        if (payload == null) {
            log.warn("âš ï¸ Webhook reÃ§u avec payload vide");
            return ResponseEntity.ok().build();
        }

        log.info("ðŸ“¨ Jira webhook event={} ticket={}",
                payload.getWebhookEvent(),
                payload.getIssueKey());

        if ("jira:issue_updated".equals(payload.getWebhookEvent())) {

            String newStatus = payload.extractNewStatus();

            if (newStatus != null) {
                log.info("ðŸ“Œ Status changed in Jira {} â†’ {}",
                        payload.getIssueKey(),
                        newStatus);
            }
        }

        return ResponseEntity.ok().build();
    }
}