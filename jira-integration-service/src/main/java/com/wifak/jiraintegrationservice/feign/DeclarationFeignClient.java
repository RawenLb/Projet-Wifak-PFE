package com.wifak.jiraintegrationservice.feign;

import com.wifak.jiraintegrationservice.dto.DeclarationDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign Client vers workflow-declaration pour récupérer les détails d'une déclaration.
 */
@FeignClient(name = "workflow-declaration", configuration = com.wifak.jiraintegrationservice.config.FeignConfig.class)
public interface DeclarationFeignClient {

    /**
     * Récupère une déclaration par son ID.
     * Endpoint : GET /api/declarations/{id}
     */
    @GetMapping("/api/declarations/{id}")
    DeclarationDTO getDeclarationById(@PathVariable("id") Long id);
}