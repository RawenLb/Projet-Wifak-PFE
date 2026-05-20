package com.wifak.chatservice.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    @Value("${app.internal-secret}")  // pas de valeur par défaut
    private String internalSecret;

    @Bean
    public RequestInterceptor feignRequestInterceptor() {
        return requestTemplate -> {
            // Appel service-à-service : on envoie uniquement le secret interne.
            // On ne transmet PAS le JWT utilisateur pour éviter que
            // BearerTokenAuthenticationFilter n'écrase ROLE_INTERNAL côté destinataire.
            requestTemplate.header("X-Internal-Secret", internalSecret);
        };
    }
}