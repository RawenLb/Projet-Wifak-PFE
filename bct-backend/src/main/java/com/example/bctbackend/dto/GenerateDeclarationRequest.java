package com.example.bctbackend.dto;

import java.time.LocalDate;

/**
 * ✅ DTO pour la génération de déclaration
 * MODIFIÉ: Plus de data manuelle — la data vient automatiquement de la DB via SQL
 */
public class GenerateDeclarationRequest {

    private Long declarationTypeId;

    // Période au format "2025-04"
    private String periode;

    // ✅ NOUVEAU — Dates pour la requête SQL (remplace Map<String, String> data)
    private LocalDate dateDebut;
    private LocalDate dateFin;

    public GenerateDeclarationRequest() {}

    public GenerateDeclarationRequest(Long declarationTypeId, String periode,
                                      LocalDate dateDebut, LocalDate dateFin) {
        this.declarationTypeId = declarationTypeId;
        this.periode = periode;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
    }

    public Long getDeclarationTypeId() { return declarationTypeId; }
    public void setDeclarationTypeId(Long declarationTypeId) {
        this.declarationTypeId = declarationTypeId;
    }

    public String getPeriode() { return periode; }
    public void setPeriode(String periode) { this.periode = periode; }

    public LocalDate getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

    @Override
    public String toString() {
        return "GenerateDeclarationRequest{" +
                "declarationTypeId=" + declarationTypeId +
                ", periode='" + periode + '\'' +
                ", dateDebut=" + dateDebut +
                ", dateFin=" + dateFin +
                '}';
    }
}