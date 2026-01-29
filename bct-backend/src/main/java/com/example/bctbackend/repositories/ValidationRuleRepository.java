package com.example.bctbackend.repositories;

import com.example.bctbackend.entities.ValidationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ValidationRuleRepository extends JpaRepository<ValidationRule, Long> {
    // ✅ Jbedli kol rules mta3 declaration type m3ayen
    List<ValidationRule> findByDeclarationTypeId(Long declarationTypeId);
}