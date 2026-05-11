package com.wifak.validationservice.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO qui reflète l'entité Declaration du declaration-service.
 */
public class DeclarationDTO {

    private Long id;
    private String statut;
    private String periode;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private String nomFichier;
    private String contenuFichier;

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

    public String getContenuFichier() { return contenuFichier; }
    public void setContenuFichier(String c) { this.contenuFichier = c; }

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

    // ─── Méthodes helper utilisées par MlIntegrationController ──

    /**
     * Retourne le nom/code du type de déclaration.
     * Utilisé par MlIntegrationController pour construire les requêtes ML.
     */
    public String getDeclarationTypeName() {
        if (declarationType == null) return "UNKNOWN";
        // Préférer le code (ex: "BCT_01") au nom long
        return declarationType.getCode() != null ? declarationType.getCode() : declarationType.getNom();
    }

    /**
     * Retourne le format du fichier (XML, CSV, JSON...).
     * Extrait depuis le nom du fichier si non renseigné directement.
     */
    public String getFileFormat() {
        if (declarationType != null && declarationType.getFormat() != null) {
            return declarationType.getFormat();
        }
        // Fallback : déduire depuis l'extension du nom de fichier
        if (nomFichier != null && nomFichier.contains(".")) {
            return nomFichier.substring(nomFichier.lastIndexOf('.') + 1).toUpperCase();
        }
        return "UNKNOWN";
    }

    /**
     * Retourne la fréquence de la déclaration (MENSUELLE, TRIMESTRIELLE, etc.).
     */
    public String getFrequence() {
        if (declarationType != null && declarationType.getFrequence() != null) {
            return declarationType.getFrequence();
        }
        return "UNKNOWN";
    }

    // ─── Nested DTO ──────────────────────────────────────────────

    public static class DeclarationTypeDTO {
        private Long id;
        private String code;
        private String nom;
        private String format;      // XML, CSV, JSON
        private String frequence;   // MENSUELLE, TRIMESTRIELLE, JOURNALIERE

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getNom() { return nom; }
        public void setNom(String nom) { this.nom = nom; }

        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }

        public String getFrequence() { return frequence; }
        public void setFrequence(String frequence) { this.frequence = frequence; }
    }
}