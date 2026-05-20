package com.wifak.validationservice.dto;

public class CreateTicketRequest {
    private Long declarationId;
    private String submittedBy;

    public CreateTicketRequest() {}

    public CreateTicketRequest(Long declarationId, String submittedBy) {
        this.declarationId = declarationId;
        this.submittedBy = submittedBy;
    }

    public Long getDeclarationId()          { return declarationId; }
    public void setDeclarationId(Long v)    { this.declarationId = v; }
    public String getSubmittedBy()          { return submittedBy; }
    public void setSubmittedBy(String v)    { this.submittedBy = v; }
}
