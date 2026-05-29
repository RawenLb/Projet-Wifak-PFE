package com.wifak.validationservice.service;

import com.wifak.validationservice.client.NotificationClient;
import com.wifak.validationservice.dto.AiValidationResult;
import com.wifak.validationservice.dto.jira.TransitionJiraTicketRequest;
import com.wifak.validationservice.entities.Declaration;
import com.wifak.validationservice.entities.DeclarationType;
import com.wifak.validationservice.entities.ValidationLog;
import com.wifak.validationservice.feign.JiraIntegrationFeignClient;
import com.wifak.validationservice.repositories.ValidationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ActiveProfiles("test")
@DisplayName("ValidationService — Tests étendus")
class ValidationServiceExtendedTest {

    @Mock private DeclarationService         declarationService;
    @Mock private JiraIntegrationFeignClient jiraClient;
    @Mock private ValidationLogRepository   logRepository;
    @Mock private NotificationClient        notificationClient;
    @Mock private AiDeclarationService      aiDeclarationService;

    @InjectMocks
    private ValidationService validationService;

    private Declaration genereeDeclaration;
    private Declaration enValidationDeclaration;
    private Declaration valideeDeclaration;
    private Declaration rejeteeDeclaration;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(validationService, "aiDeclarationService", aiDeclarationService);

        DeclarationType type = new DeclarationType();
        type.setCode("DECL001");

        genereeDeclaration = buildDeclaration(1L, Declaration.DeclarationStatut.GENEREE, "agent1");
        enValidationDeclaration = buildDeclaration(2L, Declaration.DeclarationStatut.EN_VALIDATION, "agent1");
        valideeDeclaration = buildDeclaration(3L, Declaration.DeclarationStatut.VALIDEE, "agent1");
        rejeteeDeclaration = buildDeclaration(4L, Declaration.DeclarationStatut.REJETEE, "agent1");
        rejeteeDeclaration.setCommentaireRejet("Format incorrect");

        genereeDeclaration.setDeclarationType(type);
        enValidationDeclaration.setDeclarationType(type);
        valideeDeclaration.setDeclarationType(type);
        rejeteeDeclaration.setDeclarationType(type);

        mockSecurityContext("agent1");
    }

    private Declaration buildDeclaration(Long id, Declaration.DeclarationStatut statut, String generePar) {
        Declaration d = new Declaration();
        d.setId(id);
        d.setStatut(statut);
        d.setGenerePar(generePar);
        d.setPeriode("2025-01");
        d.setContenuFichier("<xml>content</xml>");
        d.setNomFichier("decl_" + id + ".xml");
        return d;
    }

    private void mockSecurityContext(String username) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(username);
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }
    // submitForValidation — branches Jira et notification
    @Test
    @DisplayName("submitForValidation — ticket Jira existant → transition appelée")
    void submit_jiraTicketExistant_transitionAppelee() {
        when(declarationService.findById(1L)).thenReturn(genereeDeclaration);
        when(declarationService.updateStatut(eq(1L), eq("EN_VALIDATION"), any(), any()))
            .thenReturn(enValidationDeclaration);
        when(logRepository.save(any())).thenReturn(null);
        when(jiraClient.ticketExists(1L)).thenReturn(true);
        // notifyPendingValidation est void — pas besoin de doNothing() explicite avec LENIENT

        validationService.submitForValidation(1L, "correction effectuée");

        verify(jiraClient).transitionTicket(any(TransitionJiraTicketRequest.class));
    }

    @Test
    @DisplayName("submitForValidation — Jira échoue → warn sans exception")
    void submit_jiraEchoue_pasException() {
        when(declarationService.findById(1L)).thenReturn(genereeDeclaration);
        when(declarationService.updateStatut(eq(1L), eq("EN_VALIDATION"), any(), any()))
            .thenReturn(enValidationDeclaration);
        when(logRepository.save(any())).thenReturn(null);
        when(jiraClient.ticketExists(1L)).thenThrow(new RuntimeException("Jira down"));

        assertThatCode(() -> validationService.submitForValidation(1L, null))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("submitForValidation — notification échoue → warn sans exception")
    void submit_notificationEchoue_pasException() {
        when(declarationService.findById(1L)).thenReturn(genereeDeclaration);
        when(declarationService.updateStatut(eq(1L), eq("EN_VALIDATION"), any(), any()))
            .thenReturn(enValidationDeclaration);
        when(logRepository.save(any())).thenReturn(null);
        when(jiraClient.ticketExists(1L)).thenReturn(false);
        doThrow(new RuntimeException("Notification down"))
            .when(notificationClient).notifyPendingValidation(anyMap());

        assertThatCode(() -> validationService.submitForValidation(1L, null))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("submitForValidation — statut REJETEE → transition RESOUMISE")
    void submit_rejetee_transitionResoumise() {
        when(declarationService.findById(4L)).thenReturn(rejeteeDeclaration);
        when(declarationService.updateStatut(eq(4L), eq("EN_VALIDATION"), any(), any()))
            .thenReturn(enValidationDeclaration);
        when(logRepository.save(any())).thenReturn(null);
        // jiraClient.transitionTicket et notificationClient.notifyPendingValidation sont void
        // avec LENIENT strictness, pas besoin de doNothing() explicite

        validationService.submitForValidation(4L, "correction effectuée");

        verify(jiraClient).transitionTicket(argThat(r ->
            "RESOUMISE".equals(((TransitionJiraTicketRequest) r).getNewBctStatut())
        ));
    }
    // validateDeclaration — Jira échoue
    @Test
    @DisplayName("validateDeclaration — Jira échoue → warn sans exception")
    void validate_jiraEchoue_pasException() {
        mockSecurityContext("manager1");
        when(declarationService.findById(2L)).thenReturn(enValidationDeclaration);
        when(declarationService.updateStatut(eq(2L), eq("VALIDEE"), any(), eq("manager1")))
            .thenReturn(valideeDeclaration);
        when(logRepository.save(any())).thenReturn(null);
        doThrow(new RuntimeException("Jira down")).when(jiraClient).transitionTicket(any());

        assertThatCode(() -> validationService.validateDeclaration(2L))
            .doesNotThrowAnyException();
    }
    // rejectDeclaration — notification et Jira
    @Test
    @DisplayName("rejectDeclaration — notification échoue → warn sans exception")
    void reject_notificationEchoue_pasException() {
        mockSecurityContext("manager1");
        Declaration rejetee = buildDeclaration(2L, Declaration.DeclarationStatut.REJETEE, "agent1");
        when(declarationService.findById(2L)).thenReturn(enValidationDeclaration);
        when(declarationService.updateStatut(eq(2L), eq("REJETEE"), anyString(), eq("manager1")))
            .thenReturn(rejetee);
        when(logRepository.save(any())).thenReturn(null);
        // jiraClient.transitionTicket est void — pas de doNothing() nécessaire avec LENIENT
        doThrow(new RuntimeException("Notification down"))
            .when(notificationClient).notifyRejection(anyMap());

        assertThatCode(() -> validationService.rejectDeclaration(2L, "Erreur format"))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejectDeclaration — Jira échoue → warn sans exception")
    void reject_jiraEchoue_pasException() {
        mockSecurityContext("manager1");
        Declaration rejetee = buildDeclaration(2L, Declaration.DeclarationStatut.REJETEE, "agent1");
        when(declarationService.findById(2L)).thenReturn(enValidationDeclaration);
        when(declarationService.updateStatut(eq(2L), eq("REJETEE"), anyString(), eq("manager1")))
            .thenReturn(rejetee);
        when(logRepository.save(any())).thenReturn(null);
        doThrow(new RuntimeException("Jira down")).when(jiraClient).transitionTicket(any());

        assertThatCode(() -> validationService.rejectDeclaration(2L, "Erreur format"))
            .doesNotThrowAnyException();
    }
    // markAsSent — Jira échoue
    @Test
    @DisplayName("markAsSent — Jira échoue → warn sans exception")
    void markAsSent_jiraEchoue_pasException() {
        Declaration envoyee = buildDeclaration(3L, Declaration.DeclarationStatut.ENVOYEE, "agent1");
        when(declarationService.findById(3L)).thenReturn(valideeDeclaration);
        when(declarationService.updateStatut(eq(3L), eq("ENVOYEE"), any(), any()))
            .thenReturn(envoyee);
        when(logRepository.save(any())).thenReturn(null);
        doThrow(new RuntimeException("Jira down")).when(jiraClient).transitionTicket(any());

        assertThatCode(() -> validationService.markAsSent(3L))
            .doesNotThrowAnyException();
    }
    // getPendingDeclarations
    @Test
    @DisplayName("getPendingDeclarations — filtre EN_VALIDATION")
    void getPendingDeclarations_filtreEnValidation() {
        when(declarationService.getAllDeclarations())
            .thenReturn(List.of(genereeDeclaration, enValidationDeclaration, valideeDeclaration));

        List<Declaration> result = validationService.getPendingDeclarations();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatut()).isEqualTo(Declaration.DeclarationStatut.EN_VALIDATION);
    }

    @Test
    @DisplayName("getPendingDeclarations — aucune EN_VALIDATION → liste vide")
    void getPendingDeclarations_aucune_listeVide() {
        when(declarationService.getAllDeclarations())
            .thenReturn(List.of(genereeDeclaration, valideeDeclaration));

        List<Declaration> result = validationService.getPendingDeclarations();

        assertThat(result).isEmpty();
    }
    // getStats
    @Test
    @DisplayName("getStats — délègue à declarationService")
    void getStats_delegue() {
        DeclarationService.DeclarationStats stats = new DeclarationService.DeclarationStats();
        stats.setTotal(10L);
        when(declarationService.getStats()).thenReturn(stats);

        DeclarationService.DeclarationStats result = validationService.getStats();

        assertThat(result.getTotal()).isEqualTo(10L);
        verify(declarationService).getStats();
    }
    // getHistory
    @Test
    @DisplayName("getHistory — retourne les logs triés")
    void getHistory_retourneLogs() {
        ValidationLog log1 = new ValidationLog();
        log1.setAction("SUBMIT");
        when(logRepository.findByDeclarationIdOrderByDateActionDesc(1L))
            .thenReturn(List.of(log1));

        List<ValidationLog> result = validationService.getHistory(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAction()).isEqualTo("SUBMIT");
    }
    // analyzeWithAi
    @Test
    @DisplayName("analyzeWithAi — délègue à aiDeclarationService")
    void analyzeWithAi_delegue() {
        AiValidationResult aiResult = new AiValidationResult();
        when(declarationService.findById(1L)).thenReturn(genereeDeclaration);
        when(aiDeclarationService.analyzeDeclaration(anyString(), anyString())).thenReturn(aiResult);

        AiValidationResult result = validationService.analyzeWithAi(1L);

        assertThat(result).isNotNull();
        verify(aiDeclarationService).analyzeDeclaration("<xml>content</xml>", "decl_1.xml");
    }
    // getAiSummary
    @Test
    @DisplayName("getAiSummary — délègue à aiDeclarationService")
    void getAiSummary_delegue() {
        Map<String, Object> summary = Map.of("score", 85);
        when(declarationService.findById(1L)).thenReturn(genereeDeclaration);
        when(aiDeclarationService.buildAiSummary(anyString(), anyString())).thenReturn(summary);

        Map<String, Object> result = validationService.getAiSummary(1L);

        assertThat(result).containsKey("score");
    }
    // compareWithPrevious
    @Test
    @DisplayName("compareWithPrevious — déclaration précédente trouvée")
    void compareWithPrevious_precedenteTrouvee() {
        Declaration prev = buildDeclaration(5L, Declaration.DeclarationStatut.VALIDEE, "agent1");
        prev.setContenuFichier("<xml>old</xml>");

        when(declarationService.findById(1L)).thenReturn(genereeDeclaration);
        when(declarationService.findById(5L)).thenReturn(prev);
        when(aiDeclarationService.compareWithPrevious(anyString(), anyString()))
            .thenReturn(Map.of("diff", "minor"));

        Map<String, Object> result = validationService.compareWithPrevious(1L, 5L);

        assertThat(result).containsKey("diff");
        verify(aiDeclarationService).compareWithPrevious("<xml>content</xml>", "<xml>old</xml>");
    }

    @Test
    @DisplayName("compareWithPrevious — déclaration précédente introuvable → compare avec null")
    void compareWithPrevious_precedenteIntrouvable_compareAvecNull() {
        when(declarationService.findById(1L)).thenReturn(genereeDeclaration);
        when(declarationService.findById(99L)).thenThrow(new RuntimeException("Not found"));
        when(aiDeclarationService.compareWithPrevious(anyString(), isNull()))
            .thenReturn(Map.of("diff", "no previous"));

        Map<String, Object> result = validationService.compareWithPrevious(1L, 99L);

        assertThat(result).containsKey("diff");
        verify(aiDeclarationService).compareWithPrevious("<xml>content</xml>", null);
    }
}
