package com.wifak.validationservice.service;

import com.wifak.validationservice.dto.AuditLogDTO;
import com.wifak.validationservice.dto.AuditStatsDTO;
import com.wifak.validationservice.entities.Declaration;
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
 * Utilise DeclarationService directement (même microservice workflow-declaration).
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final ValidationLogRepository logRepository;
    private final DeclarationService      declarationService;

    public AuditService(ValidationLogRepository logRepository,
                        DeclarationService declarationService) {
        this.logRepository     = logRepository;
        this.declarationService = declarationService;
    }

    // ══════════════════════════════════════════════════════════════
    // 1. TOUS LES LOGS (enrichis avec infos déclaration)
    // ══════════════════════════════════════════════════════════════
    public List<AuditLogDTO> getAllLogs() {
        log.info("📋 [AuditService] getAllLogs");
        List<ValidationLog> logs = logRepository.findAllByOrderByDateActionDesc();
        return enrichLogs(logs);
    }

    // ══════════════════════════════════════════════════════════════
    // 2. LOGS FILTRÉS
    // ══════════════════════════════════════════════════════════════
    public List<AuditLogDTO> searchLogs(String action, String effectuePar,
                                        LocalDateTime from, LocalDateTime to) {
        log.info("🔍 [AuditService] searchLogs — action={}, user={}", action, effectuePar);
        String actionParam      = (action      != null && !action.isBlank())      ? action.toUpperCase() : null;
        String effectueParParam = (effectuePar != null && !effectuePar.isBlank()) ? effectuePar          : null;
        List<ValidationLog> logs = logRepository.findWithFilters(actionParam, effectueParParam, from, to);
        return enrichLogs(logs);
    }

    // ══════════════════════════════════════════════════════════════
    // 3. LOGS D'UNE DÉCLARATION
    // ══════════════════════════════════════════════════════════════
    public List<AuditLogDTO> getLogsByDeclaration(Long declarationId) {
        log.info("📜 [AuditService] getLogsByDeclaration — id={}", declarationId);
        return enrichLogs(logRepository.findByDeclarationIdOrderByDateActionDesc(declarationId));
    }

    // ══════════════════════════════════════════════════════════════
    // 4. UTILISATEURS DISTINCTS
    // ══════════════════════════════════════════════════════════════
    public List<String> getDistinctUsers() {
        return logRepository.findDistinctEffectuePar();
    }

    // ══════════════════════════════════════════════════════════════
    // 5. STATISTIQUES COMPLÈTES
    // ══════════════════════════════════════════════════════════════
    public AuditStatsDTO getAuditStats() {
        log.info("📊 [AuditService] getAuditStats");
        AuditStatsDTO stats = new AuditStatsDTO();

        // Stats déclarations — appel direct au service local
        try {
            DeclarationService.DeclarationStats declStats = declarationService.getStats();
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

        // Stats logs
        List<ValidationLog> allLogs = logRepository.findAll();
        stats.setTotalLogs(allLogs.size());
        stats.setTotalSoumissions(allLogs.stream().filter(l -> "SUBMIT".equals(l.getAction())).count());
        stats.setTotalValidations(allLogs.stream().filter(l -> "VALIDATE".equals(l.getAction())).count());
        stats.setTotalRejets(allLogs.stream().filter(l -> "REJECT".equals(l.getAction())).count());
        stats.setTotalEnvois(allLogs.stream().filter(l -> "SEND".equals(l.getAction())).count());

        Map<String, Long> actionCounts = allLogs.stream()
                .collect(Collectors.groupingBy(ValidationLog::getAction, Collectors.counting()));
        stats.setActionCounts(actionCounts);

        List<AuditStatsDTO.UserActionCount> topAgents = allLogs.stream()
                .filter(l -> "SUBMIT".equals(l.getAction()))
                .collect(Collectors.groupingBy(ValidationLog::getEffectuePar, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> new AuditStatsDTO.UserActionCount(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        stats.setTopAgents(topAgents);

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
    private List<AuditLogDTO> enrichLogs(List<ValidationLog> logs) {
        if (logs.isEmpty()) return Collections.emptyList();

        Set<Long> declIds = logs.stream().map(ValidationLog::getDeclarationId).collect(Collectors.toSet());

        Map<Long, Declaration> declCache = new HashMap<>();
        for (Long id : declIds) {
            try {
                Declaration decl = declarationService.findById(id);
                if (decl != null) declCache.put(id, decl);
            } catch (Exception e) {
                log.debug("⚠️ Déclaration {} non trouvée: {}", id, e.getMessage());
            }
        }

        return logs.stream()
                .map(l -> toAuditLogDTO(l, declCache.get(l.getDeclarationId())))
                .collect(Collectors.toList());
    }

    private AuditLogDTO toAuditLogDTO(ValidationLog vlog, Declaration decl) {
        AuditLogDTO dto = new AuditLogDTO();
        dto.setId(vlog.getId());
        dto.setDeclarationId(vlog.getDeclarationId());
        dto.setAction(vlog.getAction());
        dto.setStatutAvant(vlog.getStatutAvant());
        dto.setStatutApres(vlog.getStatutApres());
        dto.setEffectuePar(vlog.getEffectuePar());
        dto.setCommentaire(vlog.getCommentaire());
        dto.setDateAction(vlog.getDateAction());

        if (decl != null) {
            dto.setDeclarationPeriode(decl.getPeriode());
            dto.setDeclarationStatut(decl.getStatut() != null ? decl.getStatut().name() : null);
            if (decl.getDeclarationType() != null) {
                dto.setDeclarationCode(decl.getDeclarationType().getCode());
                dto.setDeclarationNom(decl.getDeclarationType().getNom());
            }
        }
        return dto;
    }
}
