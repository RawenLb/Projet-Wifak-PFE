package com.example.bctbackend.service;

import com.example.bctbackend.entities.DeclarationType;
import com.example.bctbackend.repositories.DeclarationTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DeclarationTypeService {

    private static final Logger log = LoggerFactory.getLogger(DeclarationTypeService.class);

    private final DeclarationTypeRepository repository;

    public DeclarationTypeService(DeclarationTypeRepository repository) {
        this.repository = repository;
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    /**
     * ✅ Utilitaire — corrige les champs NOT NULL null pour les anciennes lignes en base
     */
    private void fixNullAuditFields(DeclarationType type) {
        if (type.getDateCreation() == null) {
            log.warn("⚠️ dateCreation null pour type ID={}, correction automatique", type.getId());
            type.setDateCreation(LocalDateTime.now());
        }
        if (type.getDerniereModification() == null) {
            type.setDerniereModification(LocalDateTime.now());
        }
        if (type.getCreePar() == null) {
            type.setCreePar(getCurrentUsername());
        }
    }

    public DeclarationType create(DeclarationType declarationType) {
        if (repository.findByCode(declarationType.getCode()).isPresent()) {
            throw new RuntimeException("Un type avec ce code existe déjà");
        }
        // @PrePersist s'en occupe, mais on sécurise aussi ici
        declarationType.setDateCreation(LocalDateTime.now());
        declarationType.setDerniereModification(LocalDateTime.now());
        declarationType.setCreePar(getCurrentUsername());
        declarationType.setModifiePar(getCurrentUsername());
        return repository.save(declarationType);
    }

    public List<DeclarationType> getAll() {
        return repository.findAll();
    }

    public DeclarationType getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Type de déclaration introuvable: " + id));
    }

    public DeclarationType update(Long id, DeclarationType updated) {
        DeclarationType existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Type de déclaration introuvable"));

        // ✅ Corriger les champs null avant update
        fixNullAuditFields(existing);

        existing.setCode(updated.getCode());
        existing.setNom(updated.getNom());
        existing.setDescription(updated.getDescription());
        existing.setFormat(updated.getFormat());
        existing.setFrequence(updated.getFrequence());
        existing.setDateLimite(updated.getDateLimite());
        existing.setActif(updated.isActif());
        existing.setChampsObligatoires(updated.getChampsObligatoires());
        existing.setTemplate(updated.getTemplate());
        existing.setDerniereModification(LocalDateTime.now());
        existing.setModifiePar(getCurrentUsername());

        return repository.save(existing);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Type de déclaration introuvable");
        }
        repository.deleteById(id);
    }

    public DeclarationType toggleStatus(Long id) {
        DeclarationType declarationType = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Type de déclaration introuvable"));

        // ✅ Corriger les champs null avant update
        fixNullAuditFields(declarationType);

        declarationType.setActif(!declarationType.isActif());
        declarationType.setDerniereModification(LocalDateTime.now());
        declarationType.setModifiePar(getCurrentUsername());
        return repository.save(declarationType);
    }

    /**
     * ✅ Sauvegarder le contenu XSD uploadé par l'agent
     */
    public DeclarationType saveXsd(Long id, String xsdFileName, String xsdContent) {
        DeclarationType type = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Type de déclaration introuvable: " + id));

        // ✅ CORRECTION PRINCIPALE — réparer les champs null avant le save
        fixNullAuditFields(type);

        type.setXsdFileName(xsdFileName);
        type.setXsdContent(xsdContent);
        type.setDerniereModification(LocalDateTime.now());
        type.setModifiePar(getCurrentUsername());

        log.info("✅ XSD sauvegardé pour type ID={}: {}", id, xsdFileName);
        return repository.save(type);
    }

    /**
     * ✅ Sauvegarder la requête SQL saisie par l'agent
     */
    public DeclarationType saveSqlQuery(Long id, String sqlQuery) {
        DeclarationType type = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Type de déclaration introuvable: " + id));

        // ✅ CORRECTION PRINCIPALE — réparer les champs null avant le save
        fixNullAuditFields(type);

        type.setSqlQuery(sqlQuery);
        type.setDerniereModification(LocalDateTime.now());
        type.setModifiePar(getCurrentUsername());

        log.info("✅ SQL sauvegardé pour type ID={}", id);
        return repository.save(type);
    }
}