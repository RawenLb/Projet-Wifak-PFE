package com.wifak.validationservice.dto.jira;
// Fichier : validation-service/src/main/java/com/wifak/validationservice/dto/jira/JiraTicketResponseDTO.java
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraTicketResponseDTO {
    private Long declarationId;
    private String jiraTicketKey;
    private String jiraTicketUrl;
    private String jiraStatus;
    private String message;

    public Long getDeclarationId() { return declarationId; }
    public void setDeclarationId(Long declarationId) { this.declarationId = declarationId; }
    public String getJiraTicketKey() { return jiraTicketKey; }
    public void setJiraTicketKey(String jiraTicketKey) { this.jiraTicketKey = jiraTicketKey; }
    public String getJiraTicketUrl() { return jiraTicketUrl; }
    public void setJiraTicketUrl(String jiraTicketUrl) { this.jiraTicketUrl = jiraTicketUrl; }
    public String getJiraStatus() { return jiraStatus; }
    public void setJiraStatus(String jiraStatus) { this.jiraStatus = jiraStatus; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}