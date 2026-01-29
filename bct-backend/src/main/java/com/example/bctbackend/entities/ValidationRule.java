package com.example.bctbackend.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;


    @Entity
    @Table(name = "validation_rules")
    public class ValidationRule {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "declaration_type_id", nullable = false)
        @JsonBackReference("declarationType-validationRules")
        private DeclarationType declarationType;

        @Column(nullable = false, length = 100)
        private String champConcerne;

        // ✅ FIX
        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 30)
        private TypeValidation typeValidation;

        @Column(length = 255)
        private String messageErreur;

        private boolean obligatoire = true;

        public enum TypeValidation {
            CHAMP_OBLIGATOIRE,
            FORMAT_DATE,
            FORMAT_MONTANT,
            LONGUEUR_MIN,
            LONGUEUR_MAX,
            VALEUR_NUMERIQUE,
            VALEUR_POSITIVE
        }



    // Constructors
    public ValidationRule() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DeclarationType getDeclarationType() { return declarationType; }
    public void setDeclarationType(DeclarationType declarationType) {
        this.declarationType = declarationType;
    }

    public String getChampConcerne() { return champConcerne; }
    public void setChampConcerne(String champConcerne) {
        this.champConcerne = champConcerne;
    }

    public TypeValidation getTypeValidation() { return typeValidation; }
    public void setTypeValidation(TypeValidation typeValidation) {
        this.typeValidation = typeValidation;
    }

    public String getMessageErreur() { return messageErreur; }
    public void setMessageErreur(String messageErreur) {
        this.messageErreur = messageErreur;
    }

    public boolean isObligatoire() { return obligatoire; }
    public void setObligatoire(boolean obligatoire) { this.obligatoire = obligatoire; }


}