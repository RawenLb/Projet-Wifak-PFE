package com.wifak.validationservice.repositories;

import com.wifak.validationservice.entities.DeclarationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeclarationTemplateRepository extends JpaRepository<DeclarationTemplate, Long> {
    Optional<DeclarationTemplate> findByDeclarationTypeId(Long declarationTypeId);
}
