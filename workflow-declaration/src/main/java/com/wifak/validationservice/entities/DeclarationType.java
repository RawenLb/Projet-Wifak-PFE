package com.wifak.validationservice.entities;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "declaration_types")
public class DeclarationType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    @Column(nullable = false)
    private String nom;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeclarationFormat format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeclarationFrequence frequence;

    private String dateLimite;
    private boolean actif = true;

    @Column(columnDefinition = "TEXT")
    private String champsObligatoires;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String xsdContent;

    @Column(length = 255)
    private String xsdFileName;

    @Column(columnDefinition = "TEXT")
    private String sqlQuery;

    @OneToOne(mappedBy = "declarationType", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("declarationType-template")
    private DeclarationTemplate template;

    @OneToMany(mappedBy = "declarationType", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("declarationType-validationRules")
    private List<ValidationRule> validationRules = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime dateCreation;

    @Column(nullable = true)
    private LocalDateTime derniereModification;

    private String creePar;
    private String modifiePar;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        derniereModification = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        derniereModification = LocalDateTime.now();
    }

    public void addValidationRule(ValidationRule rule) {
        validationRules.add(rule);
        rule.setDeclarationType(this);
    }

    public void clearValidationRules() {
        validationRules.forEach(r -> r.setDeclarationType(null));
        validationRules.clear();
    }

    public Long getId()                         { return id; }
    public String getCode()                     { return code; }
    public void setCode(String c)               { this.code = c; }
    public String getNom()                      { return nom; }
    public void setNom(String n)                { this.nom = n; }
    public String getDescription()              { return description; }
    public void setDescription(String d)        { this.description = d; }
    public DeclarationFormat getFormat()        { return format; }
    public void setFormat(DeclarationFormat f)  { this.format = f; }
    public DeclarationFrequence getFrequence()  { return frequence; }
    public void setFrequence(DeclarationFrequence f) { this.frequence = f; }
    public String getDateLimite()               { return dateLimite; }
    public void setDateLimite(String d)         { this.dateLimite = d; }
    public boolean isActif()                    { return actif; }
    public void setActif(boolean a)             { this.actif = a; }
    public String getChampsObligatoires()       { return champsObligatoires; }
    public void setChampsObligatoires(String c) { this.champsObligatoires = c; }
    public String getXsdContent()               { return xsdContent; }
    public void setXsdContent(String x)         { this.xsdContent = x; }
    public String getXsdFileName()              { return xsdFileName; }
    public void setXsdFileName(String x)        { this.xsdFileName = x; }
    public String getSqlQuery()                 { return sqlQuery; }
    public void setSqlQuery(String s)           { this.sqlQuery = s; }
    public DeclarationTemplate getTemplate()    { return template; }
    public void setTemplate(DeclarationTemplate t) {
        this.template = t;
        if (t != null) t.setDeclarationType(this);
    }
    public List<ValidationRule> getValidationRules() { return validationRules; }
    public LocalDateTime getDateCreation()      { return dateCreation; }
    public void setDateCreation(LocalDateTime d) { this.dateCreation = d; }
    public LocalDateTime getDerniereModification() { return derniereModification; }
    public void setDerniereModification(LocalDateTime d) { this.derniereModification = d; }
    public String getCreePar()                  { return creePar; }
    public void setCreePar(String c)            { this.creePar = c; }
    public String getModifiePar()               { return modifiePar; }
    public void setModifiePar(String m)         { this.modifiePar = m; }

    public enum DeclarationFormat { TXT, XML, CSV, JSON, PDF }
    public enum DeclarationFrequence { QUOTIDIENNE, JOURNALIERE, HEBDOMADAIRE, MENSUELLE, TRIMESTRIELLE, ANNUELLE }
}
