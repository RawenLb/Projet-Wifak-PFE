package com.wifak.validationservice.repositories;

import com.wifak.validationservice.entities.DeclarationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeclarationTypeRepository extends JpaRepository<DeclarationType, Long> {
    Optional<DeclarationType> findByCode(String code);
}
