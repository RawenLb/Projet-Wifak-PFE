package com.example.bctbackend.service;

import com.example.bctbackend.entities.DeclarationType;
import com.example.bctbackend.repositories.DeclarationTypeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeclarationTypeService {

    private final DeclarationTypeRepository repository;

    public DeclarationTypeService(DeclarationTypeRepository repository) {
        this.repository = repository;
    }

    public DeclarationType create(DeclarationType declarationType) {
        if (repository.findByCode(declarationType.getCode()).isPresent()) {
            throw new RuntimeException("Declaration type with this code already exists");
        }
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
        existing.setFormat(updated.getFormat());
        existing.setFrequence(updated.getFrequence());
        existing.setDateLimite(updated.getDateLimite());
        existing.setActif(updated.isActif());

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
        return repository.save(declarationType);
    }
}
