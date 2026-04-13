package com.example.bctbackend.feign;

import com.example.bctbackend.config.FeignConfig;
import com.example.bctbackend.dto.CreateTicketRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "jira-integration-service", path = "/api/jira", configuration = FeignConfig.class  // ← AJOUTER
)

public interface JiraIntegrationFeignClient {

    @PostMapping("/tickets")
    Object createTicket(@RequestBody CreateTicketRequest req);
}