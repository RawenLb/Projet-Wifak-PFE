package com.wifak.validationservice.feign;

import com.wifak.validationservice.dto.CreateTicketRequest;
import com.wifak.validationservice.dto.jira.TransitionJiraTicketRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "jira-integration-service", path = "/api/jira")
public interface JiraIntegrationFeignClient {

    // ✅ Créer un ticket lors de la génération d'une déclaration
    @PostMapping("/tickets")
    Object createTicket(@RequestBody CreateTicketRequest req);

    // ✅ FIX : vérifier l'existence avant de tenter une transition
    @GetMapping("/tickets/{declarationId}/exists")
    Boolean ticketExists(@PathVariable("declarationId") Long declarationId);

    // Transition de statut Jira
    @PostMapping("/tickets/transition")
    Object transitionTicket(@RequestBody TransitionJiraTicketRequest req);

}