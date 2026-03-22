package com.example.bctbackend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/test/public/**").permitAll()  // ← ajouter cette ligne

                        // ✅ Toute la gestion des rôles est déléguée à @PreAuthorize
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter()))
                );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            // ✅ 1. realm_access.roles (rôles globaux Keycloak)
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess == null || !realmAccess.containsKey("roles")) {
                return List.of();
            }

            Collection<String> roles = (Collection<String>) realmAccess.get("roles");

            return roles.stream()
                    // ✅ Ajoute ROLE_ seulement si pas déjà présent
                    .map(role -> new SimpleGrantedAuthority(
                            role.startsWith("ROLE_") ? role : "ROLE_" + role
                    ))
                    .collect(Collectors.toList());
        });

        return converter;
    }
}