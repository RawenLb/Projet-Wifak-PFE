package com.wifak.validationservice.repositories;

import com.wifak.validationservice.entities.ValidationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ValidationRuleRepository extends JpaRepository<ValidationRule, Long> {
    List<ValidationRule> findByDeclarationTypeId(Long declarationTypeId);
}
