package com.wifak.notificationservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtre de sÃƒÆ’Ã‚Â©curitÃƒÆ’Ã‚Â© inter-services.
 *
 * Les appels aux webhooks doivent contenir le header :
 *   X-Internal-Secret: <valeur configurÃƒÆ’Ã‚Â©e dans app.internal-secret>
 *
 * Les endpoints /actuator/** sont exemptÃƒÆ’Ã‚Â©s.
 */
public class InternalSecretFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalSecretFilter.class);
    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final String expectedSecret;

    public InternalSecretFilter(String expectedSecret) {
        this.expectedSecret = expectedSecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Exempter actuator
        if (path.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        String receivedSecret = request.getHeader(INTERNAL_SECRET_HEADER);

        if (receivedSecret == null || !receivedSecret.equals(expectedSecret)) {
            log.warn("ÃƒÂ°Ã…Â¸Ã…Â¡Ã‚Â« AccÃƒÆ’Ã‚Â¨s refusÃƒÆ’Ã‚Â© ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â secret manquant ou invalide [{}] {}",
                    request.getMethod(), path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"AccÃƒÆ’Ã‚Â¨s non autorisÃƒÆ’Ã‚Â© ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â header X-Internal-Secret invalide\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }
}