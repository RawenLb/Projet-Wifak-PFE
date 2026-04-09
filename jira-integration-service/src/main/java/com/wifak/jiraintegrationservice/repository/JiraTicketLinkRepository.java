package com.wifak.jiraintegrationservice.repository;

import com.wifak.jiraintegrationservice.entities.JiraTicketLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository pour JiraTicketLink.
 * ✅ Méthodes utilisées dans JiraIntegrationService.
 */
@Repository
public interface JiraTicketLinkRepository extends JpaRepository<JiraTicketLink, Long> {

    /**
     * Retourne le lien ticket pour une déclaration donnée.
     * ✅ Retourne Optional — pas d'exception si absent.
     */
    Optional<JiraTicketLink> findByDeclarationId(Long declarationId);

    /**
     * Vérifie l'existence d'un ticket pour une déclaration.
     * Utilisé pour l'idempotence lors de la création.
     */
    boolean existsByDeclarationId(Long declarationId);
}