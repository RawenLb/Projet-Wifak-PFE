package com.example.bctbackend.security;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakAdminConfig {

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
        System.out.println("========================================");
        System.out.println("🔧 Initializing Keycloak Admin Client");
        System.out.println("========================================");
        System.out.println("Server URL: " + authServerUrl);
        System.out.println("Target Realm: " + realm);
        System.out.println("Admin Client ID: " + adminClientId);

        // ⚠️ OPTION 1: Service Account (Client Credentials)
        if (adminClientSecret != null && !adminClientSecret.isEmpty()) {
            System.out.println("Auth Method: CLIENT_CREDENTIALS (Service Account)");
            System.out.println("========================================");

            return KeycloakBuilder.builder()
                    .serverUrl(authServerUrl)
                    .realm(realm)  // Use target realm directly
                    .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                    .clientId(adminClientId)
                    .clientSecret(adminClientSecret)
                    .build();
        }

        // ⚠️ OPTION 2: Username/Password (admin-cli)
        else if (adminUsername != null && !adminUsername.isEmpty()) {
            System.out.println("Auth Method: PASSWORD (admin-cli)");
            System.out.println("Admin Username: " + adminUsername);
            System.out.println("========================================");

            return KeycloakBuilder.builder()
                    .serverUrl(authServerUrl)
                    .realm("master")  // Admin credentials are in master realm
                    .grantType(OAuth2Constants.PASSWORD)
                    .clientId(adminClientId)
                    .username(adminUsername)
                    .password(adminPassword)
                    .build();
        }

        else {
            throw new IllegalStateException(
                    "Keycloak admin configuration missing! " +
                            "Provide either (admin-client-secret) or (admin-username + admin-password)"
            );
        }
    }
}