package com.wifak.validationservice.feign;
// ════════════════════════════════════════════════════════════════
// Fichier : validation-service/src/main/java/com/wifak/validationservice/feign/JiraIntegrationFeignClient.java
// ════════════════════════════════════════════════════════════════

import com.wifak.validationservice.dto.jira.CreateJiraTicketRequest;
import com.wifak.validationservice.dto.jira.JiraTicketResponseDTO;
import com.wifak.validationservice.dto.jira.TransitionJiraTicketRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign Client vers jira-integration-service.
 * ⚠️ Toujours encapsuler les appels dans try/catch — NON BLOQUANT.
 */
@FeignClient(
        name = "jira-integration-service",
        configuration = com.wifak.validationservice.config.FeignConfig.class
)
public interface JiraIntegrationFeignClient {

    @PostMapping("/api/jira/tickets")
    JiraTicketResponseDTO createTicket(@RequestBody CreateJiraTicketRequest req);

    @PostMapping("/api/jira/tickets/transition")
    JiraTicketResponseDTO transitionTicket(@RequestBody TransitionJiraTicketRequest req);
}