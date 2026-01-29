package com.example.bctbackend.repositories;

import com.example.bctbackend.entities.DeclarationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DeclarationTemplateRepository extends JpaRepository<DeclarationTemplate, Long> {
    Optional<DeclarationTemplate> findByDeclarationTypeId(Long declarationTypeId);
}