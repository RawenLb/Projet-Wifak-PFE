package com.wifak.jiraintegrationservice.dto;

/**
 * DTO de réponse pour les opérations sur les tickets Jira.
 */
public class JiraTicketResponseDTO {

    private Long declarationId;
    private String jiraTicketKey;   // ex: BCT-12
    private String jiraTicketUrl;   // ex: https://domaine.atlassian.net/browse/BCT-12
    private String jiraStatus;      // IN_PROGRESS | VALIDÉE | REJETÉE | ENVOYÉE
    private String message;

    public JiraTicketResponseDTO() {}

    public JiraTicketResponseDTO(Long declarationId, String jiraTicketKey,
                                 String jiraTicketUrl, String jiraStatus, String message) {
        this.declarationId  = declarationId;
        this.jiraTicketKey  = jiraTicketKey;
        this.jiraTicketUrl  = jiraTicketUrl;
        this.jiraStatus     = jiraStatus;
        this.message        = message;
    }

    // ─── Getters / Setters ────────────────────────────────────────────

    public Long getDeclarationId()           { return declarationId; }
    public void setDeclarationId(Long v)     { this.declarationId = v; }

    public String getJiraTicketKey()         { return jiraTicketKey; }
    public void setJiraTicketKey(String v)   { this.jiraTicketKey = v; }

    public String getJiraTicketUrl()         { return jiraTicketUrl; }
    public void setJiraTicketUrl(String v)   { this.jiraTicketUrl = v; }

    public String getJiraStatus()            { return jiraStatus; }
    public void setJiraStatus(String v)      { this.jiraStatus = v; }

    public String getMessage()               { return message; }
    public void setMessage(String v)         { this.message = v; }
}