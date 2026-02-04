package com.example.bctbackend.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "declarations")
public class Declaration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "declaration_type_id", nullable = false)
    private DeclarationType declarationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeclarationStatut statut = DeclarationStatut.BROUILLON;

    @Column(nullable = false)
    private String periode; // Ex: "2026-01" pour janvier 2026

    private String nomFichier;

    @Column(columnDefinition = "TEXT")
    private String contenuFichier;

    private LocalDateTime dateGeneration;
    private LocalDateTime dateValidation;
    private LocalDateTime dateEnvoi;

    private String generePar;
    private String validePar;

    @Column(columnDefinition = "TEXT")
    private String commentaireRejet;

    public enum DeclarationStatut {
        BROUILLON,      // Agent vient de créer
        GENEREE,        // Fichier généré
        EN_VALIDATION,  // Soumis au manager
        VALIDEE,        // Manager a validé
        REJETEE,        // Manager a rejeté
        ENVOYEE         // Envoyé à la BCT
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DeclarationType getDeclarationType() {
        return declarationType;
    }

    public void setDeclarationType(DeclarationType declarationType) {
        this.declarationType = declarationType;
    }

    public DeclarationStatut getStatut() {
        return statut;
    }

    public void setStatut(DeclarationStatut statut) {
        this.statut = statut;
    }

    public String getPeriode() {
        return periode;
    }

    public void setPeriode(String periode) {
        this.periode = periode;
    }

    public String getNomFichier() {
        return nomFichier;
    }

    public void setNomFichier(String nomFichier) {
        this.nomFichier = nomFichier;
    }

    public String getContenuFichier() {
        return contenuFichier;
    }

    public void setContenuFichier(String contenuFichier) {
        this.contenuFichier = contenuFichier;
    }

    public LocalDateTime getDateGeneration() {
        return dateGeneration;
    }

    public void setDateGeneration(LocalDateTime dateGeneration) {
        this.dateGeneration = dateGeneration;
    }

    public LocalDateTime getDateValidation() {
        return dateValidation;
    }

    public void setDateValidation(LocalDateTime dateValidation) {
        this.dateValidation = dateValidation;
    }

    public LocalDateTime getDateEnvoi() {
        return dateEnvoi;
    }

    public void setDateEnvoi(LocalDateTime dateEnvoi) {
        this.dateEnvoi = dateEnvoi;
    }

    public String getGenerePar() {
        return generePar;
    }

    public void setGenerePar(String generePar) {
        this.generePar = generePar;
    }

    public String getValidePar() {
        return validePar;
    }

    public void setValidePar(String validePar) {
        this.validePar = validePar;
    }

    public String getCommentaireRejet() {
        return commentaireRejet;
    }

    public void setCommentaireRejet(String commentaireRejet) {
        this.commentaireRejet = commentaireRejet;
    }
// Getters/Setters...
}