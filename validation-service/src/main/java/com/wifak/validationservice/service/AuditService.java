package com.wifak.validationservice.service;

import com.wifak.validationservice.client.DeclarationClient;
import com.wifak.validationservice.dto.AuditLogDTO;
import com.wifak.validationservice.dto.AuditStatsDTO;
import com.wifak.validationservice.dto.DeclarationDTO;
import com.wifak.validationservice.entities.ValidationLog;
import com.wifak.validationservice.repositories.ValidationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service dédié à l'auditeur.
 * Fournit des vues agrégées et filtrées des logs + déclarations.
 * Toutes les opérations sont en lecture seule.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final ValidationLogRepository logRepository;
    private final DeclarationClient       declarationClient;

    public AuditService(ValidationLogRepository logRepository,
                        DeclarationClient declarationClient) {
        this.logRepository     = logRepository;
        this.declarationClient = declarationClient;
    }

    // ══════════════════════════════════════════════════════════════
    // 1. TOUS LES LOGS (enrichis avec infos déclaration)
    // ══════════════════════════════════════════════════════════════

    /**
     * Retourne tous les logs de traçabilité enrichis avec les métadonnées
     * de la déclaration associée (code, nom, période, statut actuel).
     */
    public List<AuditLogDTO> getAllLogs() {
        log.info("📋 [AuditService] getAllLogs");
        List<ValidationLog> logs = logRepository.findAllByOrderByDateActionDesc();
        return enrichLogs(logs);
    }

    // ══════════════════════════════════════════════════════════════
    // 2. LOGS FILTRÉS
    // ══════════════════════════════════════════════════════════════

    /**
     * Recherche filtrée des logs.
     *
     * @param action      SUBMIT | VALIDATE | REJECT | SEND (null = tous)
     * @param effectuePar username (null = tous)
     * @param from        date début (null = pas de borne)
     * @param to          date fin   (null = pas de borne)
     */
    public List<AuditLogDTO> searchLogs(String action, String effectuePar,
                                        LocalDateTime from, LocalDateTime to) {
        log.info("🔍 [AuditService] searchLogs — action={}, user={}, from={}, to={}",
                action, effectuePar, from, to);

        // Normaliser les chaînes vides en null pour la requête JPQL
        String actionParam      = (action      != null && !action.isBlank())      ? action.toUpperCase()  : null;
        String effectueParParam = (effectuePar != null && !effectuePar.isBlank()) ? effectuePar           : null;

        List<ValidationLog> logs = logRepository.findWithFilters(
                actionParam, effectueParParam, from, to);

        return enrichLogs(logs);
    }

    // ══════════════════════════════════════════════════════════════
    // 3. LOGS D'UNE DÉCLARATION
    // ══════════════════════════════════════════════════════════════

    public List<AuditLogDTO> getLogsByDeclaration(Long declarationId) {
        log.info("📜 [AuditService] getLogsByDeclaration — id={}", declarationId);
        List<ValidationLog> logs = logRepository.findByDeclarationIdOrderByDateActionDesc(declarationId);
        return enrichLogs(logs);
    }

    // ══════════════════════════════════════════════════════════════
    // 4. UTILISATEURS DISTINCTS
    // ══════════════════════════════════════════════════════════════

    public List<String> getDistinctUsers() {
        return logRepository.findDistinctEffectuePar();
    }

    // ══════════════════════════════════════════════════════════════
    // 5. STATISTIQUES COMPLÈTES POUR LE TABLEAU DE BORD
    // ══════════════════════════════════════════════════════════════

    public AuditStatsDTO getAuditStats() {
        log.info("📊 [AuditService] getAuditStats");

        AuditStatsDTO stats = new AuditStatsDTO();

        // ── Stats déclarations (via Feign → bct-backend) ──────────
        try {
            var declStats = declarationClient.getStats();
            stats.setTotalDeclarations(declStats.getTotal());
            stats.setGenerees(declStats.getGenerees());
            stats.setEnValidation(declStats.getEnValidation());
            stats.setValidees(declStats.getValidees());
            stats.setRejetees(declStats.getRejetees());
            stats.setEnvoyees(declStats.getEnvoyees());

            long total = declStats.getTotal();
            if (total > 0) {
                stats.setTauxValidation(
                        Math.round(((declStats.getValidees() + declStats.getEnvoyees()) * 100.0 / total) * 10.0) / 10.0);
                stats.setTauxRejet(
                        Math.round((declStats.getRejetees() * 100.0 / total) * 10.0) / 10.0);
            }
        } catch (Exception e) {
            log.warn("⚠️ Impossible de récupérer les stats déclarations: {}", e.getMessage());
        }

        // ── Stats logs ────────────────────────────────────────────
        List<ValidationLog> allLogs = logRepository.findAll();
        stats.setTotalLogs(allLogs.size());
        stats.setTotalSoumissions(allLogs.stream().filter(l -> "SUBMIT".equals(l.getAction())).count());
        stats.setTotalValidations(allLogs.stream().filter(l -> "VALIDATE".equals(l.getAction())).count());
        stats.setTotalRejets(allLogs.stream().filter(l -> "REJECT".equals(l.getAction())).count());
        stats.setTotalEnvois(allLogs.stream().filter(l -> "SEND".equals(l.getAction())).count());

        // ── Répartition par action ────────────────────────────────
        Map<String, Long> actionCounts = allLogs.stream()
                .collect(Collectors.groupingBy(ValidationLog::getAction, Collectors.counting()));
        stats.setActionCounts(actionCounts);

        // ── Top agents (SUBMIT) ───────────────────────────────────
        List<AuditStatsDTO.UserActionCount> topAgents = allLogs.stream()
                .filter(l -> "SUBMIT".equals(l.getAction()))
                .collect(Collectors.groupingBy(ValidationLog::getEffectuePar, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> new AuditStatsDTO.UserActionCount(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        stats.setTopAgents(topAgents);

        // ── Top managers (VALIDATE + REJECT) ─────────────────────
        List<AuditStatsDTO.UserActionCount> topManagers = allLogs.stream()
                .filter(l -> "VALIDATE".equals(l.getAction()) || "REJECT".equals(l.getAction()))
                .collect(Collectors.groupingBy(ValidationLog::getEffectuePar, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> new AuditStatsDTO.UserActionCount(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        stats.setTopManagers(topManagers);

        return stats;
    }

    // ══════════════════════════════════════════════════════════════
    // UTILITAIRES PRIVÉS
    // ══════════════════════════════════════════════════════════════

    /**
     * Enrichit une liste de ValidationLog avec les métadonnées des déclarations.
     * Utilise un cache local pour éviter les appels Feign redondants.
     */
    private List<AuditLogDTO> enrichLogs(List<ValidationLog> logs) {
        if (logs.isEmpty()) return Collections.emptyList();

        // Collecter les IDs uniques de déclarations
        Set<Long> declIds = logs.stream()
                .map(ValidationLog::getDeclarationId)
                .collect(Collectors.toSet());

        // Charger les déclarations en batch (une par une via Feign, avec cache)
        Map<Long, DeclarationDTO> declCache = new HashMap<>();
        for (Long id : declIds) {
            try {
                DeclarationDTO decl = declarationClient.getById(id);
                if (decl != null) declCache.put(id, decl);
            } catch (Exception e) {
                log.debug("⚠️ Déclaration {} non trouvée: {}", id, e.getMessage());
            }
        }

        // Construire les DTOs enrichis
        return logs.stream()
                .map(l -> toAuditLogDTO(l, declCache.get(l.getDeclarationId())))
                .collect(Collectors.toList());
    }

    private AuditLogDTO toAuditLogDTO(ValidationLog log, DeclarationDTO decl) {
        AuditLogDTO dto = new AuditLogDTO();
        dto.setId(log.getId());
        dto.setDeclarationId(log.getDeclarationId());
        dto.setAction(log.getAction());
        dto.setStatutAvant(log.getStatutAvant());
        dto.setStatutApres(log.getStatutApres());
        dto.setEffectuePar(log.getEffectuePar());
        dto.setCommentaire(log.getCommentaire());
        dto.setDateAction(log.getDateAction());

        if (decl != null) {
            dto.setDeclarationPeriode(decl.getPeriode());
            dto.setDeclarationStatut(decl.getStatut());
            if (decl.getDeclarationType() != null) {
                dto.setDeclarationCode(decl.getDeclarationType().getCode());
                dto.setDeclarationNom(decl.getDeclarationType().getNom());
            }
        }

        return dto;
    }
}
