package com.example.bctbackend.entities;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "declaration_types")
public class DeclarationType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    private String nom;

    @Enumerated(EnumType.STRING)
    private DeclarationFormat format;

    @Enumerated(EnumType.STRING)
    private DeclarationFrequence frequence;

    private LocalDate dateLimite;

    private boolean actif = true;

    // Constructors
    public DeclarationType() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public DeclarationFormat getFormat() { return format; }
    public void setFormat(DeclarationFormat format) { this.format = format; }

    public DeclarationFrequence getFrequence() { return frequence; }
    public void setFrequence(DeclarationFrequence frequence) { this.frequence = frequence; }

    public LocalDate getDateLimite() { return dateLimite; }
    public void setDateLimite(LocalDate dateLimite) { this.dateLimite = dateLimite; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    // Enums
    public enum DeclarationFormat {
        TXT, XML
    }

    public enum DeclarationFrequence {
        JOURNALIERE, MENSUELLE, TRIMESTRIELLE
    }
}