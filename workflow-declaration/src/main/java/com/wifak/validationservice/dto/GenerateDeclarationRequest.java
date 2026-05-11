package com.wifak.validationservice.dto;

import java.time.LocalDate;

public class GenerateDeclarationRequest {

    private Long declarationTypeId;
    private String periode;
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

    public Long getDeclarationTypeId()              { return declarationTypeId; }
    public void setDeclarationTypeId(Long v)        { this.declarationTypeId = v; }
    public String getPeriode()                      { return periode; }
    public void setPeriode(String v)                { this.periode = v; }
    public LocalDate getDateDebut()                 { return dateDebut; }
    public void setDateDebut(LocalDate v)           { this.dateDebut = v; }
    public LocalDate getDateFin()                   { return dateFin; }
    public void setDateFin(LocalDate v)             { this.dateFin = v; }
}
