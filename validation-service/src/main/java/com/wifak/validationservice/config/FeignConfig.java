package com.wifak.validationservice.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {

    private static final Logger log = LoggerFactory.getLogger(FeignConfig.class);

    @Bean
    public RequestInterceptor jwtRequestInterceptor() {
        return requestTemplate -> {

            // ── Stratégie 1 : depuis SecurityContextHolder (thread courant) ──
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getCredentials() instanceof Jwt jwt) {
                log.debug("🔑 Feign JWT propagé depuis SecurityContext (sub: {})", jwt.getSubject());
                requestTemplate.header("Authorization", "Bearer " + jwt.getTokenValue());
                return;
            }

            // ── Stratégie 2 : depuis le header HTTP entrant (fallback thread Feign) ──
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String bearerHeader = request.getHeader("Authorization");
                if (bearerHeader != null && bearerHeader.startsWith("Bearer ")) {
                    log.debug("🔑 Feign JWT propagé depuis HttpServletRequest header");
                    requestTemplate.header("Authorization", bearerHeader);
                    return;
                }
            }

            log.warn("⚠️ Feign : aucun JWT disponible pour la requête vers declaration-service");
        };
    }
}