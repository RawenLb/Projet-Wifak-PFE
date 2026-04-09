package com.wifak.jiraintegrationservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/**
 * Payload reçu depuis Jira via webhook.
 * Événements configurés : jira:issue_updated, jira:issue_created
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraWebhookPayload {

    private String webhookEvent;          // ex: "jira:issue_updated"
    private Map<String, Object> issue;    // contient key, id, fields.status, etc.
    private Map<String, Object> changelog;// contient items (transitions de statut)

    public String getWebhookEvent() { return webhookEvent; }
    public void setWebhookEvent(String webhookEvent) { this.webhookEvent = webhookEvent; }

    public Map<String, Object> getIssue() { return issue; }
    public void setIssue(Map<String, Object> issue) { this.issue = issue; }

    public Map<String, Object> getChangelog() { return changelog; }
    public void setChangelog(Map<String, Object> changelog) { this.changelog = changelog; }

    /** Retourne la clé du ticket Jira (ex: BCT-12) depuis le payload */
    public String getIssueKey() {
        if (issue == null) return null;
        return (String) issue.get("key");
    }

    /** Extrait le nouveau statut depuis le changelog */
    @SuppressWarnings("unchecked")
    public String extractNewStatus() {
        if (changelog == null) return null;
        Object itemsObj = changelog.get("items");
        if (itemsObj instanceof java.util.List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> map) {
                    if ("status".equals(map.get("field"))) {
                        return (String) map.get("toString");
                    }
                }
            }
        }
        return null;
    }
}