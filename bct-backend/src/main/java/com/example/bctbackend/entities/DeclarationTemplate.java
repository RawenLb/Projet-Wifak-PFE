package com.example.bctbackend.entities;

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

    @Column(columnDefinition = "TEXT", nullable = false)
    private String templateContent;

    @Column(columnDefinition = "TEXT")
    private String variablesDisponibles;

    public DeclarationType getDeclarationType() { return declarationType; }
    public void setDeclarationType(DeclarationType declarationType) {
        this.declarationType = declarationType;
    }

    public String getTemplateContent() { return templateContent; }
    public void setTemplateContent(String templateContent) {
        this.templateContent = templateContent;
    }

    public String getVariablesDisponibles() { return variablesDisponibles; }
    public void setVariablesDisponibles(String variablesDisponibles) {
        this.variablesDisponibles = variablesDisponibles;
    }
}