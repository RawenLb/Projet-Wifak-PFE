package com.wifak.validationservice.service;

import com.wifak.validationservice.dto.AuditStatsDTO;
import com.wifak.validationservice.entities.Declaration;
import com.wifak.validationservice.entities.DeclarationType;
import com.wifak.validationservice.entities.ValidationLog;
import com.wifak.validationservice.repositories.ValidationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ActiveProfiles("test")
@DisplayName("AuditService — Tests unitaires")
class AuditServiceTest {

    @Mock private ValidationLogRepository logRepository;
    @Mock private DeclarationService declarationService;

    @InjectMocks
    private AuditService auditService;

    private ValidationLog submitLog;
    private ValidationLog validateLog;
    private ValidationLog rejectLog;
    private Declaration declaration;

    @BeforeEach
    void setUp() {
        DeclarationType type = new DeclarationType();
        type.setCode("DECL001");
        type.setNom("Déclaration Test");

        declaration = new Declaration();
        declaration.setId(1L);
        declaration.setDeclarationType(type);
        declaration.setStatut(Declaration.DeclarationStatut.VALIDEE);
        declaration.setPeriode("2025-01");

        submitLog = new ValidationLog();
        submitLog.setDeclarationId(1L);
        submitLog.setAction("SUBMIT");
        submitLog.setStatutAvant("GENEREE");
        submitLog.setStatutApres("EN_VALIDATION");
        submitLog.setEffectuePar("agent1");
        submitLog.setDateAction(LocalDateTime.now());

        validateLog = new ValidationLog();
        validateLog.setDeclarationId(1L);
        validateLog.setAction("VALIDATE");
        validateLog.setStatutAvant("EN_VALIDATION");
        validateLog.setStatutApres("VALIDEE");
        validateLog.setEffectuePar("manager1");
        validateLog.setDateAction(LocalDateTime.now());

        rejectLog = new ValidationLog();
        rejectLog.setDeclarationId(1L);
        rejectLog.setAction("REJECT");
        rejectLog.setStatutAvant("EN_VALIDATION");
        rejectLog.setStatutApres("REJETEE");
        rejectLog.setEffectuePar("manager1");
        rejectLog.setCommentaire("Format incorrect");
        rejectLog.setDateAction(LocalDateTime.now());
    }

    // ══════════════════════════════════════════════════════════════
    // getAllLogs
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getAllLogs — retourne liste vide si aucun log")
    void getAllLogs_listeVide() {
        when(logRepository.findAllByOrderByDateActionDesc()).thenReturn(Collections.emptyList());

        var result = auditService.getAllLogs();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAllLogs — enrichit les logs avec les infos déclaration")
    void getAllLogs_enrichitAvecDeclaration() {
        when(logRepository.findAllByOrderByDateActionDesc()).thenReturn(List.of(submitLog));
        when(declarationService.findById(1L)).thenReturn(declaration);

        var result = auditService.getAllLogs();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDeclarationCode()).isEqualTo("DECL001");
        assertThat(result.get(0).getAction()).isEqualTo("SUBMIT");
    }

    @Test
    @DisplayName("getAllLogs — gère déclaration introuvable sans exception")
    void getAllLogs_declarationIntrouvable_pasException() {
        when(logRepository.findAllByOrderByDateActionDesc()).thenReturn(List.of(submitLog));
        when(declarationService.findById(1L)).thenThrow(new RuntimeException("Not found"));

        assertThatCode(() -> auditService.getAllLogs()).doesNotThrowAnyException();
    }

    // ══════════════════════════════════════════════════════════════
    // getLogsByDeclaration
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getLogsByDeclaration — retourne les logs d'une déclaration")
    void getLogsByDeclaration_retourneLogs() {
        when(logRepository.findByDeclarationIdOrderByDateActionDesc(1L))
            .thenReturn(List.of(submitLog, validateLog));
        when(declarationService.findById(1L)).thenReturn(declaration);

        var result = auditService.getLogsByDeclaration(1L);

        assertThat(result).hasSize(2);
    }

    // ══════════════════════════════════════════════════════════════
    // getDistinctUsers
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getDistinctUsers — retourne les utilisateurs distincts")
    void getDistinctUsers_retourneUtilisateurs() {
        when(logRepository.findDistinctEffectuePar()).thenReturn(List.of("agent1", "manager1"));

        var result = auditService.getDistinctUsers();

        assertThat(result).containsExactlyInAnyOrder("agent1", "manager1");
    }

    // ══════════════════════════════════════════════════════════════
    // getAuditStats
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getAuditStats — calcule les taux correctement")
    void getAuditStats_calculeLesTaux() {
        DeclarationService.DeclarationStats declStats = new DeclarationService.DeclarationStats();
        declStats.setTotal(10L);
        declStats.setValidees(6L);
        declStats.setEnvoyees(2L);
        declStats.setRejetees(2L);
        declStats.setGenerees(0L);
        declStats.setEnValidation(0L);

        when(declarationService.getStats()).thenReturn(declStats);
        when(logRepository.findAll()).thenReturn(List.of(submitLog, validateLog, rejectLog));

        AuditStatsDTO stats = auditService.getAuditStats();

        assertThat(stats.getTotalDeclarations()).isEqualTo(10L);
        assertThat(stats.getTauxValidation()).isEqualTo(80.0); // (6+2)/10 * 100
        assertThat(stats.getTauxRejet()).isEqualTo(20.0);      // 2/10 * 100
        assertThat(stats.getTotalLogs()).isEqualTo(3);
        assertThat(stats.getTotalSoumissions()).isEqualTo(1L);
        assertThat(stats.getTotalValidations()).isEqualTo(1L);
        assertThat(stats.getTotalRejets()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getAuditStats — total zéro → taux à 0")
    void getAuditStats_totalZero_tauxZero() {
        DeclarationService.DeclarationStats declStats = new DeclarationService.DeclarationStats();
        declStats.setTotal(0L);

        when(declarationService.getStats()).thenReturn(declStats);
        when(logRepository.findAll()).thenReturn(Collections.emptyList());

        AuditStatsDTO stats = auditService.getAuditStats();

        assertThat(stats.getTauxValidation()).isEqualTo(0.0);
        assertThat(stats.getTauxRejet()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("getAuditStats — topAgents triés par nombre de soumissions")
    void getAuditStats_topAgentsTries() {
        ValidationLog submit2 = new ValidationLog();
        submit2.setDeclarationId(2L);
        submit2.setAction("SUBMIT");
        submit2.setEffectuePar("agent1");
        submit2.setDateAction(LocalDateTime.now());

        DeclarationService.DeclarationStats declStats = new DeclarationService.DeclarationStats();
        when(declarationService.getStats()).thenReturn(declStats);
        when(logRepository.findAll()).thenReturn(List.of(submitLog, submit2, validateLog));

        AuditStatsDTO stats = auditService.getAuditStats();

        assertThat(stats.getTopAgents()).isNotEmpty();
        assertThat(stats.getTopAgents().get(0).getUsername()).isEqualTo("agent1");
        assertThat(stats.getTopAgents().get(0).getCount()).isEqualTo(2L);
    }

    // ══════════════════════════════════════════════════════════════
    // searchLogs
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("searchLogs — filtre par action")
    void searchLogs_filtreParAction() {
        when(logRepository.findWithFilters("SUBMIT", null, null, null))
            .thenReturn(List.of(submitLog));
        when(declarationService.findById(1L)).thenReturn(declaration);

        var result = auditService.searchLogs("submit", null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAction()).isEqualTo("SUBMIT");
    }

    @Test
    @DisplayName("searchLogs — action vide → null passé au repository")
    void searchLogs_actionVide_nullPasseAuRepo() {
        when(logRepository.findWithFilters(null, null, null, null))
            .thenReturn(Collections.emptyList());

        var result = auditService.searchLogs("", null, null, null);

        assertThat(result).isEmpty();
        verify(logRepository).findWithFilters(null, null, null, null);
    }
}
