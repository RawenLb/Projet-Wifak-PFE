package com.wifak.notificationservice.dto;

/**
 * Body des requêtes webhook internes.
 */
public class NotificationRequest {

    private Long declarationId;
    private String commentaire;   // utilisé pour le rejet

    public NotificationRequest() {}

    public Long getDeclarationId() { return declarationId; }
    public void setDeclarationId(Long declarationId) { this.declarationId = declarationId; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
}