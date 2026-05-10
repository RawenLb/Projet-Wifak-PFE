package com.wifak.validationservice.dto;

import java.time.LocalDateTime;

/**
 * DTO enrichi pour l'auditeur.
 * Combine ValidationLog + informations de la déclaration associée.
 */
public class AuditLogDTO {

    private Long          id;
    private Long          declarationId;
    private String        declarationCode;    // ex: BCT_01
    private String        declarationNom;     // ex: Déclaration mensuelle crédit
    private String        declarationPeriode; // ex: 2025-01
    private String        declarationStatut;  // statut actuel de la déclaration

    private String        action;             // SUBMIT | VALIDATE | REJECT | SEND
    private String        statutAvant;
    private String        statutApres;
    private String        effectuePar;
    private String        commentaire;
    private LocalDateTime dateAction;

    public AuditLogDTO() {}

    // ─── Getters / Setters ───────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDeclarationId() { return declarationId; }
    public void setDeclarationId(Long declarationId) { this.declarationId = declarationId; }

    public String getDeclarationCode() { return declarationCode; }
    public void setDeclarationCode(String declarationCode) { this.declarationCode = declarationCode; }

    public String getDeclarationNom() { return declarationNom; }
    public void setDeclarationNom(String declarationNom) { this.declarationNom = declarationNom; }

    public String getDeclarationPeriode() { return declarationPeriode; }
    public void setDeclarationPeriode(String declarationPeriode) { this.declarationPeriode = declarationPeriode; }

    public String getDeclarationStatut() { return declarationStatut; }
    public void setDeclarationStatut(String declarationStatut) { this.declarationStatut = declarationStatut; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getStatutAvant() { return statutAvant; }
    public void setStatutAvant(String statutAvant) { this.statutAvant = statutAvant; }

    public String getStatutApres() { return statutApres; }
    public void setStatutApres(String statutApres) { this.statutApres = statutApres; }

    public String getEffectuePar() { return effectuePar; }
    public void setEffectuePar(String effectuePar) { this.effectuePar = effectuePar; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public LocalDateTime getDateAction() { return dateAction; }
    public void setDateAction(LocalDateTime dateAction) { this.dateAction = dateAction; }
}
