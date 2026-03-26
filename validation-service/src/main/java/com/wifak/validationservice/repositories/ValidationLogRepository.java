package com.wifak.validationservice.repositories;

import com.wifak.validationservice.entities.ValidationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ValidationLogRepository extends JpaRepository<ValidationLog, Long> {

    /** Historique complet d'une déclaration, du plus récent au plus ancien */
    List<ValidationLog> findByDeclarationIdOrderByDateActionDesc(Long declarationId);

    /** Toutes les actions d'un utilisateur */
    List<ValidationLog> findByEffectuePar(String username);

    /** Toutes les actions d'un type donné */
    List<ValidationLog> findByAction(String action);
}