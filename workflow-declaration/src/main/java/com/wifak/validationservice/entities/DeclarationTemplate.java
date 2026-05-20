package com.wifak.validationservice.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

@Entity
@Table(name = "declaration_templates")
public class DeclarationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "declaration_type_id", nullable = false)
    @JsonBackReference("declarationType-template")
    private DeclarationType declarationType;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String templateContent;

    @Column(columnDefinition = "TEXT")
    private String variablesDisponibles;

    public Long getId()                         { return id; }
    public DeclarationType getDeclarationType() { return declarationType; }
    public void setDeclarationType(DeclarationType d) { this.declarationType = d; }
    public String getTemplateContent()          { return templateContent; }
    public void setTemplateContent(String t)    { this.templateContent = t; }
    public String getVariablesDisponibles()     { return variablesDisponibles; }
    public void setVariablesDisponibles(String v) { this.variablesDisponibles = v; }
}
