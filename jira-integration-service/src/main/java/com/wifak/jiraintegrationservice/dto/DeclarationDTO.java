package com.wifak.jiraintegrationservice.dto;

// Re-export all DTOs as public classes for use across the service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

// ══════════════════════════════════════════════════════════════
// DeclarationDTO
// ══════════════════════════════════════════════════════════════
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeclarationDTO {
    private Long id;
    private String statut;
    private String periode;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private String nomFichier;
    private LocalDateTime dateGeneration;
    private LocalDateTime dateValidation;
    private String generePar;
    private String validePar;
    private String commentaireRejet;
    private DeclarationTypeDTO declarationType;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getPeriode() { return periode; }
    public void setPeriode(String periode) { this.periode = periode; }
    public LocalDate getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }
    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }
    public String getNomFichier() { return nomFichier; }
    public void setNomFichier(String nomFichier) { this.nomFichier = nomFichier; }
    public LocalDateTime getDateGeneration() { return dateGeneration; }
    public void setDateGeneration(LocalDateTime dateGeneration) { this.dateGeneration = dateGeneration; }
    public LocalDateTime getDateValidation() { return dateValidation; }
    public void setDateValidation(LocalDateTime dateValidation) { this.dateValidation = dateValidation; }
    public String getGenerePar() { return generePar; }
    public void setGenerePar(String generePar) { this.generePar = generePar; }
    public String getValidePar() { return validePar; }
    public void setValidePar(String validePar) { this.validePar = validePar; }
    public String getCommentaireRejet() { return commentaireRejet; }
    public void setCommentaireRejet(String commentaireRejet) { this.commentaireRejet = commentaireRejet; }
    public DeclarationTypeDTO getDeclarationType() { return declarationType; }
    public void setDeclarationType(DeclarationTypeDTO declarationType) { this.declarationType = declarationType; }
}