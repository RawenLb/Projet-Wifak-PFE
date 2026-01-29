package com.example.bctbackend.service;

import com.example.bctbackend.entities.DeclarationType;
import com.example.bctbackend.repositories.DeclarationTypeRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DeclarationTypeService {

    private final DeclarationTypeRepository repository;

    public DeclarationTypeService(DeclarationTypeRepository repository) {
        this.repository = repository;
    }

    // ✅ Jbed username mta3 utilisateur connecté
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    public DeclarationType create(DeclarationType declarationType) {
        if (repository.findByCode(declarationType.getCode()).isPresent()) {
            throw new RuntimeException("Declaration type with this code already exists");
        }

        // ✅ N7otou dates w username automatiquement
        declarationType.setDateCreation(LocalDateTime.now());
        declarationType.setDerniereModification(LocalDateTime.now());
        declarationType.setCreePar(getCurrentUsername());
        declarationType.setModifiePar(getCurrentUsername());

        return repository.save(declarationType);
    }

    public List<DeclarationType> getAll() {
        return repository.findAll();
    }

    public DeclarationType update(Long id, DeclarationType updated) {
        DeclarationType existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Declaration type not found"));

        existing.setCode(updated.getCode());
        existing.setNom(updated.getNom());
        existing.setDescription(updated.getDescription());  // ✅ JDID
        existing.setFormat(updated.getFormat());
        existing.setFrequence(updated.getFrequence());
        existing.setDateLimite(updated.getDateLimite());
        existing.setActif(updated.isActif());
        existing.setChampsObligatoires(updated.getChampsObligatoires());  // ✅ JDID
        existing.setTemplate(updated.getTemplate());  // ✅ JDID

        // ✅ N7adethhou date modification w username
        existing.setDerniereModification(LocalDateTime.now());
        existing.setModifiePar(getCurrentUsername());

        return repository.save(existing);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Declaration type not found");
        }
        repository.deleteById(id);
    }

    public DeclarationType toggleStatus(Long id) {
        DeclarationType declarationType = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Declaration type not found"));

        declarationType.setActif(!declarationType.isActif());
        declarationType.setDerniereModification(LocalDateTime.now());  // ✅ JDID
        declarationType.setModifiePar(getCurrentUsername());  // ✅ JDID

        return repository.save(declarationType);
    }
}