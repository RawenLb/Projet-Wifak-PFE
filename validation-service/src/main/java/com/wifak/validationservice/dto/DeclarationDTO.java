package com.wifak.validationservice.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO qui reflète l'entité Declaration du declaration-service.
 * Ce service ne possède PAS la base de données des déclarations —
 * il lit et met à jour via le Feign Client.
 */
public class DeclarationDTO {

    private Long id;
    private String statut;           // BROUILLON, GENEREE, EN_VALIDATION, VALIDEE, REJETEE, ENVOYEE
    private String periode;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private String nomFichier;
    private String contenuFichier;   // ✅ AJOUT

    private String generePar;
    private String validePar;
    private String commentaireRejet;
    private LocalDateTime dateGeneration;
    private LocalDateTime dateValidation;
    private LocalDateTime dateEnvoi;
    private DeclarationTypeDTO declarationType;

    public DeclarationDTO() {}

    // ─── Getters / Setters ───────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getPeriode() { return periode; }
    public void setPeriode(String periode) { this.periode = periode; }
    public String getContenuFichier()               { return contenuFichier; }
    public void setContenuFichier(String c)         { this.contenuFichier = c; }
    public LocalDate getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

    public String getNomFichier() { return nomFichier; }
    public void setNomFichier(String nomFichier) { this.nomFichier = nomFichier; }

    public String getGenerePar() { return generePar; }
    public void setGenerePar(String generePar) { this.generePar = generePar; }

    public String getValidePar() { return validePar; }
    public void setValidePar(String validePar) { this.validePar = validePar; }

    public String getCommentaireRejet() { return commentaireRejet; }
    public void setCommentaireRejet(String commentaireRejet) { this.commentaireRejet = commentaireRejet; }

    public LocalDateTime getDateGeneration() { return dateGeneration; }
    public void setDateGeneration(LocalDateTime dateGeneration) { this.dateGeneration = dateGeneration; }

    public LocalDateTime getDateValidation() { return dateValidation; }
    public void setDateValidation(LocalDateTime dateValidation) { this.dateValidation = dateValidation; }

    public LocalDateTime getDateEnvoi() { return dateEnvoi; }
    public void setDateEnvoi(LocalDateTime dateEnvoi) { this.dateEnvoi = dateEnvoi; }

    public DeclarationTypeDTO getDeclarationType() { return declarationType; }
    public void setDeclarationType(DeclarationTypeDTO declarationType) { this.declarationType = declarationType; }

    // ─── Nested DTO ──────────────────────────────────────────────

    public static class DeclarationTypeDTO {
        private Long id;
        private String code;
        private String nom;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getNom() { return nom; }
        public void setNom(String nom) { this.nom = nom; }
    }
}