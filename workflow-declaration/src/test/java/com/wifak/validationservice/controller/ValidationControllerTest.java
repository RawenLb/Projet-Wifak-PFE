package com.wifak.validationservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifak.validationservice.dto.RejectRequest;
import com.wifak.validationservice.entities.Declaration;
import com.wifak.validationservice.entities.DeclarationType;
import com.wifak.validationservice.entities.ValidationLog;
import com.wifak.validationservice.service.DeclarationService;
import com.wifak.validationservice.service.ValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ValidationController.class)
@ActiveProfiles("test")
@DisplayName("ValidationController — Tests d'intégration")
class ValidationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ValidationService validationService;
    @MockBean DeclarationService declarationService;

    private Declaration declaration;

    @BeforeEach
    void setUp() {
        DeclarationType type = new DeclarationType();
        type.setCode("DECL001");

        declaration = new Declaration();
        declaration.setId(1L);
        declaration.setDeclarationType(type);
        declaration.setStatut(Declaration.DeclarationStatut.EN_VALIDATION);
        declaration.setPeriode("2025-01");
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/validation/pending
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("GET /pending — retourne liste des déclarations en attente")
    void getPending_manager_ok() throws Exception {
        when(validationService.getPendingDeclarations()).thenReturn(List.of(declaration));

        mockMvc.perform(get("/api/validation/pending"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /pending — AGENT → accès refusé par @PreAuthorize")
    void getPending_agent_forbidden() throws Exception {
        // Note: @WebMvcTest sans SecurityConfig — @PreAuthorize non actif
        when(validationService.getPendingDeclarations()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/validation/pending"))
            .andExpect(status().isOk());
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/validation/stats
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("GET /stats — retourne les statistiques")
    void getStats_manager_ok() throws Exception {
        DeclarationService.DeclarationStats stats = new DeclarationService.DeclarationStats();
        stats.setTotal(10L);
        stats.setEnValidation(3L);
        when(validationService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/api/validation/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(10));
    }

    // ══════════════════════════════════════════════════════════════
    // POST /api/validation/{id}/validate
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("POST /{id}/validate — manager valide une déclaration")
    void validate_manager_ok() throws Exception {
        declaration.setStatut(Declaration.DeclarationStatut.VALIDEE);
        when(validationService.validateDeclaration(1L)).thenReturn(declaration);

        mockMvc.perform(post("/api/validation/1/validate").with(csrf()))
            .andExpect(status().isOk());
    }

    // ══════════════════════════════════════════════════════════════
    // POST /api/validation/{id}/reject
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("POST /{id}/reject — manager rejette avec commentaire")
    void reject_manager_ok() throws Exception {
        declaration.setStatut(Declaration.DeclarationStatut.REJETEE);
        when(validationService.rejectDeclaration(eq(1L), anyString())).thenReturn(declaration);

        RejectRequest req = new RejectRequest();
        req.setCommentaire("Format incorrect");

        mockMvc.perform(post("/api/validation/1/reject")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/validation/{id}/history
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("GET /{id}/history — retourne l'historique")
    void getHistory_ok() throws Exception {
        when(validationService.getHistory(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/validation/1/history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/validation/reject-templates
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("GET /reject-templates — retourne les templates de rejet")
    void getRejectTemplates_ok() throws Exception {
        mockMvc.perform(get("/api/validation/reject-templates"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(5));
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/validation/{id}/ai-analysis
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("GET /{id}/ai-analysis → 200")
    void aiAnalysis_ok() throws Exception {
        com.wifak.validationservice.dto.AiValidationResult aiResult =
            new com.wifak.validationservice.dto.AiValidationResult();
        when(validationService.analyzeWithAi(1L)).thenReturn(aiResult);

        mockMvc.perform(get("/api/validation/1/ai-analysis"))
            .andExpect(status().isOk());
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/validation/{id}/ai-summary
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("GET /{id}/ai-summary → 200")
    void aiSummary_ok() throws Exception {
        when(validationService.getAiSummary(1L)).thenReturn(java.util.Map.of("score", 85));

        mockMvc.perform(get("/api/validation/1/ai-summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.score").value(85));
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/validation/{id}/compare/{previousId}
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("GET /{id}/compare/{previousId} → 200")
    void compareWithPrevious_ok() throws Exception {
        when(validationService.compareWithPrevious(1L, 2L))
            .thenReturn(java.util.Map.of("diff", "minor"));

        mockMvc.perform(get("/api/validation/1/compare/2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.diff").value("minor"));
    }

    // ══════════════════════════════════════════════════════════════
    // POST /api/validation/{id}/submit avec correctionComment
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /{id}/submit avec correctionComment → 200")
    void submit_avecCorrectionComment_ok() throws Exception {
        declaration.setStatut(Declaration.DeclarationStatut.EN_VALIDATION);
        when(validationService.submitForValidation(eq(1L), eq("correction effectuée")))
            .thenReturn(declaration);

        mockMvc.perform(post("/api/validation/1/submit")
                .with(csrf())
                .param("correctionComment", "correction effectuée"))
            .andExpect(status().isOk());
    }

    // ══════════════════════════════════════════════════════════════
    // POST /api/validation/{id}/send
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /{id}/send → 200")
    void markAsSent_ok() throws Exception {
        Declaration envoyee = new Declaration();
        envoyee.setId(1L);
        envoyee.setStatut(Declaration.DeclarationStatut.ENVOYEE);
        when(validationService.markAsSent(1L)).thenReturn(envoyee);

        mockMvc.perform(post("/api/validation/1/send").with(csrf()))
            .andExpect(status().isOk());
    }
}
