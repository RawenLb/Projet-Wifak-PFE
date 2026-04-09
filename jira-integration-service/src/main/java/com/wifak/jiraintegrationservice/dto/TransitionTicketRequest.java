package com.wifak.jiraintegrationservice.dto;

public class TransitionTicketRequest {
    private Long declarationId;
    private String newBctStatut;    // EN_VALIDATION, VALIDEE, REJETEE, ENVOYEE
    private String commentaire;     // optionnel — obligatoire si REJETEE
    private String effectuePar;     // username du manager

    public Long getDeclarationId() { return declarationId; }
    public void setDeclarationId(Long declarationId) { this.declarationId = declarationId; }
    public String getNewBctStatut() { return newBctStatut; }
    public void setNewBctStatut(String newBctStatut) { this.newBctStatut = newBctStatut; }
    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
    public String getEffectuePar() { return effectuePar; }
    public void setEffectuePar(String effectuePar) { this.effectuePar = effectuePar; }
}