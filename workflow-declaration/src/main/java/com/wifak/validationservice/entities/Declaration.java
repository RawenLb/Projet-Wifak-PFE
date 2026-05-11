package com.wifak.validationservice.entities;

import jakarta.persistence.*;
import java.time.LocalDate;
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
    private String periode;

    private LocalDate dateDebut;
    private LocalDate dateFin;

    private String nomFichier;

    @Column(columnDefinition = "LONGTEXT")
    private String contenuFichier;

    @Column(columnDefinition = "TEXT")
    private String sqlQueryUsed;

    private String xsdFileNameUsed;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String mappingJson;

    private LocalDateTime dateGeneration;
    private LocalDateTime dateValidation;
    private LocalDateTime dateEnvoi;

    private String generePar;
    private String validePar;

    @Column(columnDefinition = "TEXT")
    private String commentaireRejet;

    public enum DeclarationStatut {
        BROUILLON, GENEREE, EN_VALIDATION, VALIDEE, REJETEE, ENVOYEE
    }

    public Long getId()                         { return id; }
    public void setId(Long id)                  { this.id = id; }
    public DeclarationType getDeclarationType() { return declarationType; }
    public void setDeclarationType(DeclarationType d) { this.declarationType = d; }
    public DeclarationStatut getStatut()        { return statut; }
    public void setStatut(DeclarationStatut s)  { this.statut = s; }
    public String getPeriode()                  { return periode; }
    public void setPeriode(String p)            { this.periode = p; }
    public LocalDate getDateDebut()             { return dateDebut; }
    public void setDateDebut(LocalDate d)       { this.dateDebut = d; }
    public LocalDate getDateFin()               { return dateFin; }
    public void setDateFin(LocalDate d)         { this.dateFin = d; }
    public String getNomFichier()               { return nomFichier; }
    public void setNomFichier(String n)         { this.nomFichier = n; }
    public String getContenuFichier()           { return contenuFichier; }
    public void setContenuFichier(String c)     { this.contenuFichier = c; }
    public String getSqlQueryUsed()             { return sqlQueryUsed; }
    public void setSqlQueryUsed(String s)       { this.sqlQueryUsed = s; }
    public String getXsdFileNameUsed()          { return xsdFileNameUsed; }
    public void setXsdFileNameUsed(String v)    { this.xsdFileNameUsed = v; }
    public String getMappingJson()              { return mappingJson; }
    public void setMappingJson(String m)        { this.mappingJson = m; }
    public LocalDateTime getDateGeneration()    { return dateGeneration; }
    public void setDateGeneration(LocalDateTime v) { this.dateGeneration = v; }
    public LocalDateTime getDateValidation()    { return dateValidation; }
    public void setDateValidation(LocalDateTime v) { this.dateValidation = v; }
    public LocalDateTime getDateEnvoi()         { return dateEnvoi; }
    public void setDateEnvoi(LocalDateTime v)   { this.dateEnvoi = v; }
    public String getGenerePar()                { return generePar; }
    public void setGenerePar(String g)          { this.generePar = g; }
    public String getValidePar()                { return validePar; }
    public void setValidePar(String v)          { this.validePar = v; }
    public String getCommentaireRejet()         { return commentaireRejet; }
    public void setCommentaireRejet(String v)   { this.commentaireRejet = v; }
}
