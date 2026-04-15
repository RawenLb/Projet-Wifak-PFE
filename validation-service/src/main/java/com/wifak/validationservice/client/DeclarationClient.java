package com.wifak.validationservice.client;

import com.wifak.validationservice.config.FeignConfig;
import com.wifak.validationservice.dto.DeclarationDTO;
import com.wifak.validationservice.dto.ValidationStatsDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(
        name = "bct-backend",
        configuration = FeignConfig.class
)
public interface DeclarationClient {

    @GetMapping("/api/declarations/{id}")
    DeclarationDTO getById(@PathVariable("id") Long id);

    @GetMapping("/api/declarations")
    List<DeclarationDTO> getAll();

    @GetMapping("/api/declarations/my")
    List<DeclarationDTO> getMy();

    @GetMapping("/api/declarations/stats")
    ValidationStatsDTO getStats();

    @PatchMapping("/api/declarations/{id}/statut")
    DeclarationDTO updateStatut(
            @PathVariable("id") Long id,
            @RequestParam("statut")                           String statut,
            @RequestParam(value = "commentaire", required = false) String commentaire,
            @RequestParam(value = "validePar",   required = false) String validePar
    );
}