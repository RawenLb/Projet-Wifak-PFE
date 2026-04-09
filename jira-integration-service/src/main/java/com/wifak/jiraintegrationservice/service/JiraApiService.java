package com.wifak.jiraintegrationservice.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class JiraApiService {

    private static final Logger log = LoggerFactory.getLogger(JiraApiService.class);

    @Value("${jira.base-url}")
    private String jiraBaseUrl;

    @Value("${jira.user-email}")
    private String userEmail;

    @Value("${jira.api-token}")
    private String apiToken;

    private final RestTemplate restTemplate = new RestTemplate();

    // ✅ Vérifie la config au démarrage et log un avertissement clair si credentials suspects
    @PostConstruct
    public void validateConfig() {
        if (userEmail == null || userEmail.isBlank()) {
            log.error("❌ jira.user-email est vide ou non configuré !");
        } else {
            log.info("✅ Jira user-email configuré : {}", userEmail.trim());
        }
        if (apiToken == null || apiToken.isBlank()) {
            log.error("❌ jira.api-token est vide ou non configuré !");
        } else {
            log.info("✅ Jira api-token configuré : longueur={}", apiToken.trim().length());
        }
        if (jiraBaseUrl == null || jiraBaseUrl.isBlank()) {
            log.error("❌ jira.base-url est vide ou non configuré !");
        } else {
            log.info("✅ Jira base-url : {}", jiraBaseUrl.trim());
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();

        // ✅ FIX : trim() pour supprimer espaces/retours à la ligne invisibles
        //         StandardCharsets.UTF_8 pour un encodage Base64 déterministe
        String credentials = userEmail.trim() + ":" + apiToken.trim();
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        headers.set("Authorization", "Basic " + encoded);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json");
        return headers;
    }

    // ================= CREATE =================
    public Map<String, Object> createTicket(String summary, String description) {

        Map<String, Object> fields = new HashMap<>();
        fields.put("project", Map.of("key", "BCT"));
        fields.put("summary", summary);
        fields.put("description", Map.of(
                "type", "doc",
                "version", 1,
                "content", List.of(
                        Map.of(
                                "type", "paragraph",
                                "content", List.of(
                                        Map.of("type", "text", "text", description)
                                )
                        )
                )
        ));
        fields.put("issuetype", Map.of("name", "Task"));

        HttpEntity<?> entity = new HttpEntity<>(Map.of("fields", fields), buildHeaders());

        log.debug("📡 Appel Jira CREATE : {}/rest/api/3/issue", jiraBaseUrl.trim());

        ResponseEntity<Map> response = restTemplate.exchange(
                jiraBaseUrl.trim() + "/rest/api/3/issue",
                HttpMethod.POST,
                entity,
                Map.class
        );

        return response.getBody();
    }

    // ================= TRANSITION =================
    public void transitionTicket(String ticketId, String transitionId) {

        Map<String, Object> body = Map.of(
                "transition", Map.of("id", transitionId)
        );

        HttpEntity<?> entity = new HttpEntity<>(body, buildHeaders());

        log.debug("📡 Appel Jira TRANSITION : ticketId={} transitionId={}", ticketId, transitionId);

        restTemplate.exchange(
                jiraBaseUrl.trim() + "/rest/api/3/issue/" + ticketId + "/transitions",
                HttpMethod.POST,
                entity,
                Map.class
        );
    }

    // ================= COMMENT =================
    public void addComment(String ticketId, String comment) {

        Map<String, Object> body = Map.of(
                "body", Map.of(
                        "type", "doc",
                        "version", 1,
                        "content", List.of(
                                Map.of(
                                        "type", "paragraph",
                                        "content", List.of(
                                                Map.of("type", "text", "text", comment)
                                        )
                                )
                        )
                )
        );

        HttpEntity<?> entity = new HttpEntity<>(body, buildHeaders());

        restTemplate.exchange(
                jiraBaseUrl.trim() + "/rest/api/3/issue/" + ticketId + "/comment",
                HttpMethod.POST,
                entity,
                Map.class
        );
    }

    // ================= MAPPING BCT → JIRA TRANSITION ID =================
    // Workflow Jira "Déclarations BCT" :
    //  1 : Début         → IDEA         (Create — à la création)
    //  5 : Tous états    → TO DO        (GENEREE)
    // 31 : Tous états    → IN PROGRESS  (EN_VALIDATION)
    //  4 : IN PROGRESS   → REJETÉE      (REJETEE)
    //  3 : REJETÉE       → IN PROGRESS  (REJETEE resoumise → EN_VALIDATION)
    // 41 : Tous états    → VALIDÉE      (VALIDEE)
    //  2 : VALIDÉE       → ENVOYÉE      (ENVOYEE)
    public String mapBctStatutToTransitionId(String statut) {
        return switch (statut.toUpperCase()) {
            case "GENEREE"       -> "5";
            case "EN_VALIDATION" -> "31";
            case "REJETEE"       -> "4";
            case "RESOUMISE"     -> "3";
            case "VALIDEE"       -> "41";
            case "ENVOYEE"       -> "2";
            default -> throw new IllegalArgumentException("Statut BCT inconnu: " + statut);
        };
    }
}