package com.wifak.validationservice.dto.jira;
// Fichier : validation-service/src/main/java/com/wifak/validationservice/dto/jira/CreateJiraTicketRequest.java
public class CreateJiraTicketRequest {
    private Long declarationId;
    private String submittedBy; // username Keycloak de l'agent

    public CreateJiraTicketRequest() {}

    public CreateJiraTicketRequest(Long declarationId, String submittedBy) {
        this.declarationId = declarationId;
        this.submittedBy = submittedBy;
    }

    public Long getDeclarationId() { return declarationId; }
    public void setDeclarationId(Long declarationId) { this.declarationId = declarationId; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
}