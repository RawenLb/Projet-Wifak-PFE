package com.wifak.chatservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    private final JwtDecoder jwtDecoder;

    public JwtHandshakeInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String query = request.getURI().getQuery();
        if (query == null) { log.warn("[Chat WS] Handshake rejected — no query string"); return false; }

        String token = Arrays.stream(query.split("&"))
            .filter(p -> p.startsWith("token="))
            .map(p -> p.substring(6))
            .findFirst().orElse(null);

        if (token == null || token.isBlank()) {
            log.warn("[Chat WS] Handshake rejected — missing token"); return false;
        }

        try {
            token = java.net.URLDecoder.decode(token, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {}

        try {
            Jwt jwt = jwtDecoder.decode(token);
            String userId = jwt.getSubject();
            String username = jwt.getClaimAsString("preferred_username");

            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            List<String> rawRoles = Collections.emptyList();
            if (realmAccess != null && realmAccess.get("roles") instanceof List<?> list) {
                rawRoles = list.stream().filter(r -> r instanceof String).map(r -> (String) r).toList();
            }

            String primaryRole = null;
            for (String r : rawRoles) {
                String upper = r.toUpperCase();
                if (upper.equals("ROLE_MANAGER") || upper.equals("MANAGER")) { primaryRole = "MANAGER"; break; }
                if (upper.equals("ROLE_AGENT")   || upper.equals("AGENT"))   { primaryRole = "AGENT"; }
            }
            if (primaryRole == null) {
                for (String r : rawRoles) {
                    String upper = r.toUpperCase();
                    if (upper.equals("ROLE_ADMIN") || upper.equals("ADMIN")) { primaryRole = "ADMIN"; break; }
                }
            }

            if (primaryRole == null) {
                log.warn("[Chat WS] Handshake rejected — no chat role for user {}", username); return false;
            }

            attributes.put("userId",   userId);
            attributes.put("username", username != null ? username : "Unknown");
            attributes.put("role",     primaryRole);
            attributes.put("roles",    rawRoles);
            log.info("[Chat WS] Handshake accepted: {} ({}) role={}", username, userId, primaryRole);
            return true;

        } catch (JwtException e) {
            log.warn("[Chat WS] Handshake rejected — invalid JWT: {}", e.getMessage()); return false;
        } catch (Exception e) {
            log.error("[Chat WS] Handshake error: {}", e.getMessage(), e); return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}
}
