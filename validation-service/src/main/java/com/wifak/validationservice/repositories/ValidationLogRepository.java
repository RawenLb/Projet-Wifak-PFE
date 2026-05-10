package com.wifak.validationservice.repositories;

import com.wifak.validationservice.entities.ValidationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ValidationLogRepository extends JpaRepository<ValidationLog, Long> {

    /** Historique complet d'une déclaration, du plus récent au plus ancien */
    List<ValidationLog> findByDeclarationIdOrderByDateActionDesc(Long declarationId);

    /** Toutes les actions d'un utilisateur */
    List<ValidationLog> findByEffectuePar(String username);

    /** Toutes les actions d'un type donné */
    List<ValidationLog> findByAction(String action);

    // ── Requêtes pour l'auditeur ──────────────────────────────────

    /** Tous les logs triés par date décroissante */
    List<ValidationLog> findAllByOrderByDateActionDesc();

    /** Logs filtrés par action et triés */
    List<ValidationLog> findByActionOrderByDateActionDesc(String action);

    /** Logs filtrés par utilisateur et triés */
    List<ValidationLog> findByEffectueParOrderByDateActionDesc(String effectuePar);

    /** Logs dans une plage de dates */
    List<ValidationLog> findByDateActionBetweenOrderByDateActionDesc(
            LocalDateTime from, LocalDateTime to);

    /** Logs par action dans une plage de dates */
    List<ValidationLog> findByActionAndDateActionBetweenOrderByDateActionDesc(
            String action, LocalDateTime from, LocalDateTime to);

    /** Logs par utilisateur dans une plage de dates */
    List<ValidationLog> findByEffectueParAndDateActionBetweenOrderByDateActionDesc(
            String effectuePar, LocalDateTime from, LocalDateTime to);

    /** Logs pour une liste de déclarations (pour filtrage croisé) */
    List<ValidationLog> findByDeclarationIdInOrderByDateActionDesc(List<Long> declarationIds);

    /** Compter les actions par type */
    long countByAction(String action);

    /** Compter les actions d'un utilisateur */
    long countByEffectuePar(String effectuePar);

    /** Logs filtrés dynamiquement */
    @Query("SELECT l FROM ValidationLog l WHERE " +
           "(:action IS NULL OR l.action = :action) AND " +
           "(:effectuePar IS NULL OR l.effectuePar = :effectuePar) AND " +
           "(:from IS NULL OR l.dateAction >= :from) AND " +
           "(:to IS NULL OR l.dateAction <= :to) " +
           "ORDER BY l.dateAction DESC")
    List<ValidationLog> findWithFilters(
            @Param("action")      String action,
            @Param("effectuePar") String effectuePar,
            @Param("from")        LocalDateTime from,
            @Param("to")          LocalDateTime to);

    /** Logs pour une liste de déclarations avec filtres */
    @Query("SELECT l FROM ValidationLog l WHERE " +
           "l.declarationId IN :declarationIds AND " +
           "(:action IS NULL OR l.action = :action) AND " +
           "(:effectuePar IS NULL OR l.effectuePar = :effectuePar) AND " +
           "(:from IS NULL OR l.dateAction >= :from) AND " +
           "(:to IS NULL OR l.dateAction <= :to) " +
           "ORDER BY l.dateAction DESC")
    List<ValidationLog> findByDeclarationIdsWithFilters(
            @Param("declarationIds") List<Long> declarationIds,
            @Param("action")         String action,
            @Param("effectuePar")    String effectuePar,
            @Param("from")           LocalDateTime from,
            @Param("to")             LocalDateTime to);

    /** Utilisateurs distincts ayant effectué des actions */
    @Query("SELECT DISTINCT l.effectuePar FROM ValidationLog l ORDER BY l.effectuePar")
    List<String> findDistinctEffectuePar();
}