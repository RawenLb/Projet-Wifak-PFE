package com.wifak.validationservice.client;

import com.wifak.validationservice.config.NotificationFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Feign Client vers le notification-service (port 8085).
 *
 * Appelé après chaque transition de statut pour déclencher
 * l'envoi automatique d'emails aux utilisateurs concernés.
 */
@FeignClient(
        name = "notification-service",
        path = "/api/notifications",
        configuration = NotificationFeignConfig.class
)
public interface NotificationClient {

    /**
     * Notifie les managers qu'une déclaration est en attente de validation.
     * Déclenché après SUBMIT (GENEREE/REJETEE → EN_VALIDATION).
     */
    @PostMapping("/pending-validation")
    void notifyPendingValidation(@RequestBody Map<String, Object> payload);

    /**
     * Notifie l'agent que sa déclaration a été rejetée.
     * Déclenché après REJECT (EN_VALIDATION → REJETEE).
     */
    @PostMapping("/rejection")
    void notifyRejection(@RequestBody Map<String, Object> payload);
}