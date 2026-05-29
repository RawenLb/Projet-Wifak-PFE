package com.example.bctbackend.security;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakAdminConfig {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminConfig.class);

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.admin-client-id}")
    private String adminClientId;

    @Value("${keycloak.admin-client-secret:}")
    private String adminClientSecret;

    @Value("${keycloak.admin-username:}")
    private String adminUsername;

    @Value("${keycloak.admin-password:}")
    private String adminPassword;

    @Bean
    public Keycloak keycloak() {
        log.info("Initializing Keycloak Admin Client — server={}, realm={}, clientId={}",
                authServerUrl, realm, adminClientId);

        if (adminClientSecret != null && !adminClientSecret.isEmpty()) {
            log.info("Auth method: CLIENT_CREDENTIALS");
            return KeycloakBuilder.builder()
                    .serverUrl(authServerUrl)
                    .realm(realm)
                    .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                    .clientId(adminClientId)
                    .clientSecret(adminClientSecret)
                    .build();
        }

        if (adminUsername != null && !adminUsername.isEmpty()) {
            log.info("Auth method: PASSWORD (admin-cli), username={}", adminUsername);
            return KeycloakBuilder.builder()
                    .serverUrl(authServerUrl)
                    .realm("master")
                    .grantType(OAuth2Constants.PASSWORD)
                    .clientId(adminClientId)
                    .username(adminUsername)
                    .password(adminPassword)
                    .build();
        }

        throw new IllegalStateException(
                "Keycloak admin configuration missing: provide admin-client-secret or admin-username + admin-password");
    }
}
