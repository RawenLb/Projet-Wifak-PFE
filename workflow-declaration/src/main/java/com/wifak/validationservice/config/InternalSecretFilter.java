package com.wifak.validationservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtre qui authentifie les appels service-Ã -service via X-Internal-Secret.
 * UtilisÃ© par chat-service pour appeler les endpoints /api/admin/** de workflow-declaration.
 */
@Component
public class InternalSecretFilter extends OncePerRequestFilter {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    @Value("${app.internal-secret:wifak-internal-secret-2024}")
    private String internalSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String secret = request.getHeader(INTERNAL_SECRET_HEADER);

        if (internalSecret.equals(secret)) {
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            "internal-service",
                            null,
                            List.of(
                                new SimpleGrantedAuthority("ROLE_INTERNAL"),
                                new SimpleGrantedAuthority("ROLE_ADMIN")
                            )
                    );
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
