package com.wifak.validationservice.dto.jira;
// ════════════════════════════════════════════════════════════════
// Fichier : validation-service/src/main/java/com/wifak/validationservice/dto/jira/TransitionJiraTicketRequest.java
// ════════════════════════════════════════════════════════════════

public class TransitionJiraTicketRequest {
    private Long declarationId;
    private String newBctStatut;  // EN_VALIDATION | VALIDEE | REJETEE | ENVOYEE
    private String commentaire;   // obligatoire si REJETEE
    private String effectuePar;   // username manager

    public TransitionJiraTicketRequest() {}

    public TransitionJiraTicketRequest(Long declarationId, String newBctStatut,
                                       String commentaire, String effectuePar) {
        this.declarationId = declarationId;
        this.newBctStatut = newBctStatut;
        this.commentaire = commentaire;
        this.effectuePar = effectuePar;
    }

    public Long getDeclarationId() { return declarationId; }
    public void setDeclarationId(Long declarationId) { this.declarationId = declarationId; }
    public String getNewBctStatut() { return newBctStatut; }
    public void setNewBctStatut(String newBctStatut) { this.newBctStatut = newBctStatut; }
    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
    public String getEffectuePar() { return effectuePar; }
    public void setEffectuePar(String effectuePar) { this.effectuePar = effectuePar; }
}