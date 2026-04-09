package com.wifak.jiraintegrationservice.dto;

public class CreateTicketRequest {
    private Long declarationId;
    private String submittedBy;  // username Keycloak de l'agent

    public Long getDeclarationId() { return declarationId; }
    public void setDeclarationId(Long declarationId) { this.declarationId = declarationId; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
}