package com.example.bctbackend.entities;

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

    /**
     * ✅ NOUVEAU — Mapping XSD ↔ SQL sérialisé en JSON.
     * Permet de :
     *  - tracer quel champ XSD est mappé sur quelle colonne SQL
     *  - distinguer les champs statiques des champs dynamiques
     *  - ré-générer la déclaration avec le même mapping
     *
     * Null pour CSV/TXT ou XML sans mapping explicite.
     */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String mappingJson;

    private LocalDateTime dateGeneration;
    private LocalDateTime dateValidation;
    private LocalDateTime dateEnvoi;

    private String generePar;
    private String validePar;

    @Column(columnDefinition = "TEXT")
    private String commentaireRejet;

    // ══════════════════════════════════════════════════════════════
    // ENUM STATUT
    // ══════════════════════════════════════════════════════════════

    public enum DeclarationStatut {
        BROUILLON,
        GENEREE,
        EN_VALIDATION,
        VALIDEE,
        REJETEE,
        ENVOYEE
    }

    // ══════════════════════════════════════════════════════════════
    // GETTERS / SETTERS
    // ══════════════════════════════════════════════════════════════

    public Long getId()                         { return id; }
    public void setId(Long id)                  { this.id = id; }

    public DeclarationType getDeclarationType() { return declarationType; }
    public void setDeclarationType(DeclarationType declarationType) {
        this.declarationType = declarationType;
    }

    public DeclarationStatut getStatut()                { return statut; }
    public void setStatut(DeclarationStatut statut)     { this.statut = statut; }

    public String getPeriode()                          { return periode; }
    public void setPeriode(String periode)              { this.periode = periode; }

    public LocalDate getDateDebut()                     { return dateDebut; }
    public void setDateDebut(LocalDate dateDebut)       { this.dateDebut = dateDebut; }

    public LocalDate getDateFin()                       { return dateFin; }
    public void setDateFin(LocalDate dateFin)           { this.dateFin = dateFin; }

    public String getNomFichier()                       { return nomFichier; }
    public void setNomFichier(String nomFichier)        { this.nomFichier = nomFichier; }

    public String getContenuFichier()                   { return contenuFichier; }
    public void setContenuFichier(String contenuFichier){ this.contenuFichier = contenuFichier; }

    public String getSqlQueryUsed()                     { return sqlQueryUsed; }
    public void setSqlQueryUsed(String sqlQueryUsed)    { this.sqlQueryUsed = sqlQueryUsed; }

    public String getXsdFileNameUsed()                  { return xsdFileNameUsed; }
    public void setXsdFileNameUsed(String v)            { this.xsdFileNameUsed = v; }

    public String getMappingJson()                      { return mappingJson; }
    public void setMappingJson(String mappingJson)      { this.mappingJson = mappingJson; }

    public LocalDateTime getDateGeneration()            { return dateGeneration; }
    public void setDateGeneration(LocalDateTime v)      { this.dateGeneration = v; }

    public LocalDateTime getDateValidation()            { return dateValidation; }
    public void setDateValidation(LocalDateTime v)      { this.dateValidation = v; }

    public LocalDateTime getDateEnvoi()                 { return dateEnvoi; }
    public void setDateEnvoi(LocalDateTime v)           { this.dateEnvoi = v; }

    public String getGenerePar()                        { return generePar; }
    public void setGenerePar(String generePar)          { this.generePar = generePar; }

    public String getValidePar()                        { return validePar; }
    public void setValidePar(String validePar)          { this.validePar = validePar; }

    public String getCommentaireRejet()                 { return commentaireRejet; }
    public void setCommentaireRejet(String v)           { this.commentaireRejet = v; }
}