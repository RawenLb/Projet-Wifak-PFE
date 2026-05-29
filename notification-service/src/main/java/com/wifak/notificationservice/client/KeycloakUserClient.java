package com.wifak.notificationservice.client;

import com.wifak.notificationservice.dto.UserEmailDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Client Keycloak Admin REST API — logique identique à KeycloakAdminConfig du bct-backend.
 *
 * Si admin-client-secret est renseigné  → client_credentials vers /{realm}
 * Si admin-username est renseigné       → password grant    vers /master
 *
 * Config via variables d'environnement (voir application.yml) :
 *   admin-client-id:     ${KEYCLOAK_ADMIN_CLIENT_ID}
 *   admin-client-secret: ${KEYCLOAK_ADMIN_CLIENT_SECRET}
 *   admin-username:      ${KEYCLOAK_ADMIN_USERNAME}
 *   admin-password:      ${KEYCLOAK_ADMIN_PASSWORD}
 *
 * → Le secret étant renseigné, c'est client_credentials qui sera utilisé
 *   (même priorité que dans KeycloakAdminConfig du bct-backend).
 */
@Component
public class KeycloakUserClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakUserClient.class);

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

    private final RestTemplate restTemplate = new RestTemplate();
    // Token admin — même logique que KeycloakAdminConfig bct-backend
    private String getAdminToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", adminClientId);

        String tokenUrl;

        // OPTION 1 : client_credentials → realm cible (même logique que bct-backend)
        if (adminClientSecret != null && !adminClientSecret.isBlank()) {
            tokenUrl = authServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";
            body.add("grant_type",    "client_credentials");
            body.add("client_secret", adminClientSecret);
            log.debug("🔑 Keycloak token : client_credentials → realm '{}'", realm);
        }
        // OPTION 2 : password grant → realm master
        else if (adminUsername != null && !adminUsername.isBlank()) {
            tokenUrl = authServerUrl + "/realms/master/protocol/openid-connect/token";
            body.add("grant_type", "password");
            body.add("username",   adminUsername);
            body.add("password",   adminPassword);
            log.debug("🔑 Keycloak token : password grant → realm 'master' (user={})", adminUsername);
        }
        else {
            throw new IllegalStateException(
                    "Configuration Keycloak admin manquante : " +
                            "renseigner admin-client-secret ou admin-username + admin-password"
            );
        }

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(tokenUrl, request, Map.class);

            if (response == null || !response.containsKey("access_token")) {
                throw new RuntimeException("Réponse Keycloak invalide — access_token absent");
            }

            return (String) response.get("access_token");

        } catch (Exception e) {
            log.error("❌ Échec obtention token admin Keycloak [{}] : {}", tokenUrl, e.getMessage());
            throw new RuntimeException("Échec authentification Keycloak admin : " + e.getMessage());
        }
    }
    // Récupérer un utilisateur par username
    public UserEmailDTO getUserByUsername(String username) {
        try {
            String token = getAdminToken();
            String url = authServerUrl + "/admin/realms/" + realm
                    + "/users?username=" + username + "&exact=true";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), List.class
            );

            List<Map<String, Object>> users = response.getBody();
            if (users == null || users.isEmpty()) {
                log.warn("⚠️  Utilisateur '{}' introuvable dans Keycloak (realm: {})", username, realm);
                return null;
            }

            return mapToUserEmailDTO(users.get(0));

        } catch (Exception e) {
            log.error("❌ Erreur Keycloak getUserByUsername('{}') : {}", username, e.getMessage());
            return null;
        }
    }
    // Récupérer tous les utilisateurs d'un rôle realm
    public List<UserEmailDTO> getUsersByRole(String roleName) {
        try {
            String token = getAdminToken();
            String url = authServerUrl + "/admin/realms/" + realm
                    + "/roles/" + roleName + "/users";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), List.class
            );

            List<Map<String, Object>> users = response.getBody();
            if (users == null) return List.of();

            List<UserEmailDTO> result = users.stream()
                    .map(this::mapToUserEmailDTO)
                    .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                    .toList();

            log.info("👥 Rôle '{}' → {} utilisateur(s) avec email trouvé(s)", roleName, result.size());
            return result;

        } catch (Exception e) {
            log.error("❌ Erreur Keycloak getUsersByRole('{}') : {}", roleName, e.getMessage());
            return List.of();
        }
    }
    // Mapper Map Keycloak → DTO
    @SuppressWarnings("unchecked")
    private UserEmailDTO mapToUserEmailDTO(Map<String, Object> user) {
        UserEmailDTO dto = new UserEmailDTO();
        dto.setId((String) user.get("id"));
        dto.setUsername((String) user.get("username"));
        dto.setEmail((String) user.get("email"));
        dto.setFirstName((String) user.getOrDefault("firstName", ""));
        dto.setLastName((String)  user.getOrDefault("lastName",  ""));
        return dto;
    }
}