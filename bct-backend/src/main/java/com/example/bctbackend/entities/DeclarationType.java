package com.example.bctbackend.entities;

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

    @OneToOne(mappedBy = "declarationType", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("declarationType-template")
    private DeclarationTemplate template;

    @OneToMany(
            mappedBy = "declarationType",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @JsonManagedReference("declarationType-validationRules")
    private List<ValidationRule> validationRules = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime dateCreation;

    @Column(nullable = false)
    private LocalDateTime derniereModification;

    private String creePar;
    private String modifiePar;

    /* ================= LIFECYCLE ================= */

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        derniereModification = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        derniereModification = LocalDateTime.now();
    }

    /* ================= HELPERS ================= */

    public void addValidationRule(ValidationRule rule) {
        validationRules.add(rule);
        rule.setDeclarationType(this);
    }

    public void clearValidationRules() {
        validationRules.forEach(r -> r.setDeclarationType(null));
        validationRules.clear();
    }

    /* ================= GETTERS / SETTERS ================= */

    public Long getId() { return id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public DeclarationFormat getFormat() { return format; }
    public void setFormat(DeclarationFormat format) { this.format = format; }

    public DeclarationFrequence getFrequence() { return frequence; }
    public void setFrequence(DeclarationFrequence frequence) { this.frequence = frequence; }

    public String getDateLimite() { return dateLimite; }
    public void setDateLimite(String dateLimite) { this.dateLimite = dateLimite; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public String getChampsObligatoires() { return champsObligatoires; }
    public void setChampsObligatoires(String champsObligatoires) {
        this.champsObligatoires = champsObligatoires;
    }

    public DeclarationTemplate getTemplate() { return template; }
    public void setTemplate(DeclarationTemplate template) {
        this.template = template;
        if (template != null) {
            template.setDeclarationType(this);
        }
    }

    public List<ValidationRule> getValidationRules() { return validationRules; }

    public LocalDateTime getDateCreation() { return dateCreation; }

    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }

    public LocalDateTime getDerniereModification() { return derniereModification; }
    public void setDerniereModification(LocalDateTime derniereModification) {
        this.derniereModification = derniereModification;
    }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }

    public String getModifiePar() { return modifiePar; }
    public void setModifiePar(String modifiePar) { this.modifiePar = modifiePar; }

    /* ================= ENUMS ================= */

    public enum DeclarationFormat {
        TXT, XML, CSV, JSON, PDF
    }

    public enum DeclarationFrequence { QUOTIDIENNE, JOURNALIERE, HEBDOMADAIRE, MENSUELLE, TRIMESTRIELLE, ANNUELLE }
}
