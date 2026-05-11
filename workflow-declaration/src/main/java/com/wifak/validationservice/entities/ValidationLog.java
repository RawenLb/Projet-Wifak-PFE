package com.wifak.validationservice.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Trace chaque action du workflow de validation.
 * Stocké dans la base wifak_validation (propre à ce microservice).
 */
@Entity
@Table(name = "validation_logs")
public class ValidationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID de la déclaration dans le declaration-service */
    @Column(nullable = false)
    private Long declarationId;

    /**
     * Action effectuée : SUBMIT, VALIDATE, REJECT, SEND
     */
    @Column(nullable = false, length = 30)
    private String action;

    /** Statut avant l'action */
    @Column(length = 30)
    private String statutAvant;

    /** Statut après l'action */
    @Column(length = 30)
    private String statutApres;

    /** Utilisateur qui a effectué l'action (username Keycloak) */
    @Column(nullable = false)
    private String effectuePar;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    @Column(nullable = false)
    private LocalDateTime dateAction;

    @PrePersist
    protected void onCreate() {
        dateAction = LocalDateTime.now();
    }

    // ─── Getters / Setters ───────────────────────────────────────

    public Long getId() { return id; }

    public Long getDeclarationId() { return declarationId; }
    public void setDeclarationId(Long declarationId) { this.declarationId = declarationId; }

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