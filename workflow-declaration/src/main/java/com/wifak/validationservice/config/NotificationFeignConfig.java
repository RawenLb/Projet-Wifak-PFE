package com.wifak.validationservice.config;

import feign.RequestInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration Feign pour les appels vers le notification-service.
 *
 * Utilise un secret partagé (X-Internal-Secret) au lieu du JWT utilisateur,
 * car le notification-service est un service interne machine-to-machine.
 *
 * ⚠️ Ne pas confondre avec FeignConfig (qui propage le JWT vers declaration-service).
 */
@Configuration
public class NotificationFeignConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationFeignConfig.class);

    @Value("${app.internal-secret:wifak-internal-secret-2024}")
    private String internalSecret;

    @Bean
    public RequestInterceptor notificationSecretInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("X-Internal-Secret", internalSecret);
            log.debug("🔑 Feign → notification-service : header X-Internal-Secret ajouté");
        };
    }
}