package com.wifak.validationservice.service;

import com.wifak.validationservice.client.NotificationClient;
import com.wifak.validationservice.entities.Declaration;
import com.wifak.validationservice.entities.DeclarationType;
import com.wifak.validationservice.feign.JiraIntegrationFeignClient;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.springframework.test.context.ActiveProfiles;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ActiveProfiles("test")
@DisplayName("ValidationService — Tests unitaires")
class ValidationServiceTest {

    @Mock private DeclarationService declarationService;
    @Mock private JiraIntegrationFeignClient jiraClient;
    @Mock private ValidationLogRepository logRepository;
    @Mock private NotificationClient notificationClient;

    @InjectMocks
    private ValidationService validationService;

    private Declaration genereeDeclaration;
    private Declaration enValidationDeclaration;
    private Declaration valideeDeclaration;

    @BeforeEach
    void setUp() {
        DeclarationType type = new DeclarationType();
        type.setCode("DECL001");

        genereeDeclaration = new Declaration();
        genereeDeclaration.setId(1L);
        genereeDeclaration.setDeclarationType(type);
        genereeDeclaration.setStatut(Declaration.DeclarationStatut.GENEREE);
        genereeDeclaration.setGenerePar("agent1");

        enValidationDeclaration = new Declaration();
        enValidationDeclaration.setId(2L);
        enValidationDeclaration.setDeclarationType(type);
        enValidationDeclaration.setStatut(Declaration.DeclarationStatut.EN_VALIDATION);
        enValidationDeclaration.setGenerePar("agent1");

        valideeDeclaration = new Declaration();
        valideeDeclaration.setId(3L);
        valideeDeclaration.setDeclarationType(type);
        valideeDeclaration.setStatut(Declaration.DeclarationStatut.VALIDEE);
        valideeDeclaration.setGenerePar("agent1");

        mockSecurityContext("agent1");
    }

    private void mockSecurityContext(String username) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(username);
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }
    // submitForValidation
    @Test
    @DisplayName("submitForValidation — statut GENEREE → EN_VALIDATION OK")
    void submit_generee_ok() {
        when(declarationService.findById(1L)).thenReturn(genereeDeclaration);
        when(declarationService.updateStatut(eq(1L), eq("EN_VALIDATION"), any(), any()))
            .thenReturn(enValidationDeclaration);
        when(logRepository.save(any())).thenReturn(null);
        when(jiraClient.ticketExists(1L)).thenReturn(false);

        Declaration result = validationService.submitForValidation(1L, null);

        assertThat(result).isNotNull();
        assertThat(result.getStatut()).isEqualTo(Declaration.DeclarationStatut.EN_VALIDATION);
        verify(declarationService).updateStatut(eq(1L), eq("EN_VALIDATION"), any(), any());
    }

    @Test
    @DisplayName("submitForValidation — mauvais propriétaire → IllegalStateException")
    void submit_mauvaisProprietaire_throwsException() {
        mockSecurityContext("autreAgent");
        when(declarationService.findById(1L)).thenReturn(genereeDeclaration);

        assertThatThrownBy(() -> validationService.submitForValidation(1L, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("propres déclarations");
    }

    @Test
    @DisplayName("submitForValidation — statut VALIDEE → IllegalStateException")
    void submit_statutInvalide_throwsException() {
        when(declarationService.findById(3L)).thenReturn(valideeDeclaration);

        assertThatThrownBy(() -> validationService.submitForValidation(3L, null))
            .isInstanceOf(IllegalStateException.class);
    }
    // validateDeclaration
    @Test
    @DisplayName("validateDeclaration — EN_VALIDATION → VALIDEE OK")
    void validate_enValidation_ok() {
        mockSecurityContext("manager1");
        when(declarationService.findById(2L)).thenReturn(enValidationDeclaration);
        when(declarationService.updateStatut(eq(2L), eq("VALIDEE"), any(), eq("manager1")))
            .thenReturn(valideeDeclaration);
        when(logRepository.save(any())).thenReturn(null);

        Declaration result = validationService.validateDeclaration(2L);

        assertThat(result).isNotNull();
        assertThat(result.getStatut()).isEqualTo(Declaration.DeclarationStatut.VALIDEE);
    }

    @Test
    @DisplayName("validateDeclaration — statut GENEREE → IllegalStateException")
    void validate_generee_throwsException() {
        when(declarationService.findById(1L)).thenReturn(genereeDeclaration);

        assertThatThrownBy(() -> validationService.validateDeclaration(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("EN_VALIDATION");
    }
    // rejectDeclaration
    @Test
    @DisplayName("rejectDeclaration — commentaire vide → IllegalArgumentException")
    void reject_commentaireVide_throwsException() {
        assertThatThrownBy(() -> validationService.rejectDeclaration(2L, ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("commentaire");
    }

    @Test
    @DisplayName("rejectDeclaration — commentaire null → IllegalArgumentException")
    void reject_commentaireNull_throwsException() {
        assertThatThrownBy(() -> validationService.rejectDeclaration(2L, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejectDeclaration — EN_VALIDATION avec commentaire → REJETEE OK")
    void reject_enValidation_avecCommentaire_ok() {
        mockSecurityContext("manager1");
        Declaration rejeteeDecl = new Declaration();
        rejeteeDecl.setId(2L);
        rejeteeDecl.setStatut(Declaration.DeclarationStatut.REJETEE);

        when(declarationService.findById(2L)).thenReturn(enValidationDeclaration);
        when(declarationService.updateStatut(eq(2L), eq("REJETEE"), eq("Format incorrect"), eq("manager1")))
            .thenReturn(rejeteeDecl);
        when(logRepository.save(any())).thenReturn(null);

        Declaration result = validationService.rejectDeclaration(2L, "Format incorrect");

        assertThat(result.getStatut()).isEqualTo(Declaration.DeclarationStatut.REJETEE);
    }
    // markAsSent
    @Test
    @DisplayName("markAsSent — VALIDEE → ENVOYEE OK")
    void markAsSent_validee_ok() {
        Declaration envoyeeDecl = new Declaration();
        envoyeeDecl.setId(3L);
        envoyeeDecl.setStatut(Declaration.DeclarationStatut.ENVOYEE);

        when(declarationService.findById(3L)).thenReturn(valideeDeclaration);
        when(declarationService.updateStatut(eq(3L), eq("ENVOYEE"), any(), any()))
            .thenReturn(envoyeeDecl);
        when(logRepository.save(any())).thenReturn(null);

        Declaration result = validationService.markAsSent(3L);

        assertThat(result.getStatut()).isEqualTo(Declaration.DeclarationStatut.ENVOYEE);
    }

    @Test
    @DisplayName("markAsSent — statut EN_VALIDATION → IllegalStateException")
    void markAsSent_enValidation_throwsException() {
        when(declarationService.findById(2L)).thenReturn(enValidationDeclaration);

        assertThatThrownBy(() -> validationService.markAsSent(2L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("VALIDEE");
    }
}
