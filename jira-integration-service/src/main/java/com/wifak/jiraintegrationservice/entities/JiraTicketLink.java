package com.wifak.jiraintegrationservice.entities;


import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stocke le lien entre une déclaration BCT et son ticket Jira correspondant.
 */
@Entity
@Table(name = "jira_ticket_links")
public class JiraTicketLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID de la déclaration dans le bct-backend (declaration-service) */
    @Column(nullable = false, unique = true)
    private Long declarationId;

    /** Clé du ticket Jira : ex. BCT-12 */
    @Column(nullable = false)
    private String jiraTicketKey;

    /** ID interne Jira du ticket */
    @Column(nullable = false)
    private String jiraTicketId;

    /** URL complète vers le ticket Jira */
    @Column(nullable = false)
    private String jiraTicketUrl;

    /** Statut Jira courant : TO_DO, IN_PROGRESS, VALIDEE, REJETEE, ENVOYEE */
    @Column(nullable = false)
    private String jiraStatus;

    /** Statut BCT au moment de la création du ticket */
    @Column(nullable = false)
    private String bctStatut;

    /** Username Keycloak de l'agent qui a soumis la déclaration */
    @Column(nullable = false)
    private String creePar;

    @Column(nullable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime derniereMiseAJour;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        derniereMiseAJour = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        derniereMiseAJour = LocalDateTime.now();
    }

    // ─── Getters / Setters ───────────────────────────────────────

    public Long getId() { return id; }

    public Long getDeclarationId() { return declarationId; }
    public void setDeclarationId(Long declarationId) { this.declarationId = declarationId; }

    public String getJiraTicketKey() { return jiraTicketKey; }
    public void setJiraTicketKey(String jiraTicketKey) { this.jiraTicketKey = jiraTicketKey; }

    public String getJiraTicketId() { return jiraTicketId; }
    public void setJiraTicketId(String jiraTicketId) { this.jiraTicketId = jiraTicketId; }

    public String getJiraTicketUrl() { return jiraTicketUrl; }
    public void setJiraTicketUrl(String jiraTicketUrl) { this.jiraTicketUrl = jiraTicketUrl; }

    public String getJiraStatus() { return jiraStatus; }
    public void setJiraStatus(String jiraStatus) { this.jiraStatus = jiraStatus; }

    public String getBctStatut() { return bctStatut; }
    public void setBctStatut(String bctStatut) { this.bctStatut = bctStatut; }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public LocalDateTime getDerniereMiseAJour() { return derniereMiseAJour; }
    public void setDerniereMiseAJour(LocalDateTime derniereMiseAJour) { this.derniereMiseAJour = derniereMiseAJour; }
}