package com.wifak.notificationservice.client;

import com.wifak.notificationservice.config.FeignConfig;
import com.wifak.notificationservice.dto.DeclarationDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Feign Client vers le declaration-service (enregistré dans Eureka sous "bct-backend").
 * Utilisé pour lire les déclarations et leurs métadonnées.
 */
@FeignClient(
        name = "bct-backend",
        path = "/api/declarations",
        configuration = FeignConfig.class
)
public interface DeclarationClient {

    @GetMapping("/{id}")
    DeclarationDTO getById(@PathVariable("id") Long id);

    @GetMapping
    List<DeclarationDTO> getAll();
}