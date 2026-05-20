package com.wifak.validationservice.dto;

/**
 * Body de la requête de rejet — le commentaire est obligatoire.
 */
public class RejectRequest {

    private String commentaire;

    public RejectRequest() {}

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
}