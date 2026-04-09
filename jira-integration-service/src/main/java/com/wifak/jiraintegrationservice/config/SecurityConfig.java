package com.wifak.jiraintegrationservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration de sécurité pour jira-integration-service.
 *
 * Règles :
 * - POST /api/jira/webhook → PUBLIC (appels Jira Cloud sans JWT)
 * - GET  /api/jira/tickets/{id}/exists → accessible via JWT ou X-Internal-Secret
 * - Tout le reste → JWT Keycloak requis
 */// SecurityConfig.java
import com.wifak.jiraintegrationservice.config.InternalSecretFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Value("${app.internal-secret:wifak-internal-secret-2024}")
    private String internalSecret;

    private final InternalSecretFilter internalSecretFilter;

    public SecurityConfig(InternalSecretFilter internalSecretFilter) {
        this.internalSecretFilter = internalSecretFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // ✅ Register BEFORE BearerTokenAuthenticationFilter
                .addFilterBefore(internalSecretFilter, BearerTokenAuthenticationFilter.class)

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/jira/webhook").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username"); // ✅ cohérence avec validation-service
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter()); // ✅ bon package
        return converter;
    }
}