package com.wifak.validationservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifak.validationservice.entities.Declaration;
import com.wifak.validationservice.entities.DeclarationType;
import com.wifak.validationservice.feign.MlServiceFeignClient;
import com.wifak.validationservice.service.DeclarationService;
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

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MlIntegrationController.class)
@ActiveProfiles("test")
@DisplayName("MlIntegrationController — Tests d'intégration")
class MlIntegrationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean MlServiceFeignClient mlClient;
    @MockBean DeclarationService declarationService;

    private Declaration declaration;

    @BeforeEach
    void setUp() {
        DeclarationType type = new DeclarationType();
        type.setCode("DECL001");

        declaration = new Declaration();
        declaration.setId(1L);
        declaration.setDeclarationType(type);
        declaration.setStatut(Declaration.DeclarationStatut.REJETEE);
        declaration.setPeriode("2025-01");
        declaration.setCommentaireRejet("Format incorrect");
    }
    // GET /api/ml/health
    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /health — ML service UP → 200")
    void health_up_ok() throws Exception {
        when(mlClient.healthCheck()).thenReturn(Map.of("status", "UP"));

        mockMvc.perform(get("/api/ml/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /health — ML service DOWN → 503")
    void health_down_returns503() throws Exception {
        when(mlClient.healthCheck()).thenThrow(new RuntimeException("Connection refused"));

        mockMvc.perform(get("/api/ml/health"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value("DOWN"));
    }
    // GET /api/ml/diagnostics
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /diagnostics — ADMIN → 200")
    void diagnostics_ok() throws Exception {
        when(mlClient.getDiagnostics()).thenReturn(Map.of("model", "loaded"));

        mockMvc.perform(get("/api/ml/diagnostics"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /diagnostics — erreur → 500")
    void diagnostics_error_returns500() throws Exception {
        when(mlClient.getDiagnostics()).thenThrow(new RuntimeException("ML error"));

        mockMvc.perform(get("/api/ml/diagnostics"))
            .andExpect(status().isInternalServerError());
    }
    // GET /api/ml/bf17/declaration/{id}/suggestions
    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /bf17/declaration/{id}/suggestions — avec commentaire rejet → 200")
    void getSuggestions_avecCommentaire_ok() throws Exception {
        when(declarationService.findById(1L)).thenReturn(declaration);
        java.util.Map<String, Object> mlResult = new java.util.HashMap<>();
        mlResult.put("cluster_label", "FORMAT_ERROR");
        when(mlClient.analyzeError(anyMap())).thenReturn(mlResult);

        mockMvc.perform(get("/api/ml/bf17/declaration/1/suggestions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.declaration_id").value(1));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /bf17/declaration/{id}/suggestions — sans commentaire rejet → 200 avec liste vide")
    void getSuggestions_sansCommentaire_ok() throws Exception {
        declaration.setCommentaireRejet(null);
        when(declarationService.findById(1L)).thenReturn(declaration);

        mockMvc.perform(get("/api/ml/bf17/declaration/1/suggestions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suggestions").isArray());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /bf17/declaration/{id}/suggestions — erreur service → 500")
    void getSuggestions_erreur_returns500() throws Exception {
        when(declarationService.findById(99L)).thenThrow(new RuntimeException("Not found"));

        mockMvc.perform(get("/api/ml/bf17/declaration/99/suggestions"))
            .andExpect(status().isInternalServerError());
    }
    // POST /api/ml/bf17/analyze-comment
    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /bf17/analyze-comment — commentaire valide → 200")
    void analyzeComment_ok() throws Exception {
        when(mlClient.analyzeError(anyMap())).thenReturn(Map.of("cluster_label", "FORMAT_ERROR"));

        mockMvc.perform(post("/api/ml/bf17/analyze-comment")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reject_comment\": \"Format incorrect\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /bf17/analyze-comment — commentaire vide → 400")
    void analyzeComment_vide_returns400() throws Exception {
        mockMvc.perform(post("/api/ml/bf17/analyze-comment")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reject_comment\": \"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /bf17/analyze-comment — erreur ML → 500")
    void analyzeComment_erreurML_returns500() throws Exception {
        when(mlClient.analyzeError(anyMap())).thenThrow(new RuntimeException("ML down"));

        mockMvc.perform(post("/api/ml/bf17/analyze-comment")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reject_comment\": \"Erreur format\"}"))
            .andExpect(status().isInternalServerError());
    }
    // GET /api/ml/bf17/clusters
    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /bf17/clusters → 200")
    void getClusters_ok() throws Exception {
        java.util.List<java.util.Map<String, Object>> clusters = new java.util.ArrayList<>();
        clusters.add(Map.of("label", (Object) "FORMAT_ERROR"));
        when(mlClient.getClusters()).thenReturn(clusters);

        mockMvc.perform(get("/api/ml/bf17/clusters"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /bf17/clusters — erreur → 500")
    void getClusters_erreur_returns500() throws Exception {
        when(mlClient.getClusters()).thenThrow(new RuntimeException("ML error"));

        mockMvc.perform(get("/api/ml/bf17/clusters"))
            .andExpect(status().isInternalServerError());
    }
    // GET /api/ml/bf17/stats
    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /bf17/stats → 200")
    void getStats_ok() throws Exception {
        when(mlClient.getClusteringStats()).thenReturn(Map.of("total", (Object) 10));

        mockMvc.perform(get("/api/ml/bf17/stats"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /bf17/stats — erreur → 500")
    void getStats_erreur_returns500() throws Exception {
        when(mlClient.getClusteringStats()).thenThrow(new RuntimeException("ML error"));

        mockMvc.perform(get("/api/ml/bf17/stats"))
            .andExpect(status().isInternalServerError());
    }
    // POST /api/ml/bf17/train
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /bf17/train — ADMIN → 200")
    void trainClustering_ok() throws Exception {
        when(mlClient.trainClustering()).thenReturn(Map.of("status", "trained"));

        mockMvc.perform(post("/api/ml/bf17/train").with(csrf()))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /bf17/train — erreur → 500")
    void trainClustering_erreur_returns500() throws Exception {
        when(mlClient.trainClustering()).thenThrow(new RuntimeException("Train failed"));

        mockMvc.perform(post("/api/ml/bf17/train").with(csrf()))
            .andExpect(status().isInternalServerError());
    }
    // POST /api/ml/train-all
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /train-all — ADMIN → 200")
    void trainAll_ok() throws Exception {
        when(mlClient.trainAll()).thenReturn(Map.of("status", "all trained"));

        mockMvc.perform(post("/api/ml/train-all").with(csrf()))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /train-all — erreur → 500")
    void trainAll_erreur_returns500() throws Exception {
        when(mlClient.trainAll()).thenThrow(new RuntimeException("Train failed"));

        mockMvc.perform(post("/api/ml/train-all").with(csrf()))
            .andExpect(status().isInternalServerError());
    }
}
