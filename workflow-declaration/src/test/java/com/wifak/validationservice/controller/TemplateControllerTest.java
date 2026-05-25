package com.wifak.validationservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifak.validationservice.service.TemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TemplateController.class)
@ActiveProfiles("test")
@DisplayName("TemplateController — Tests d'intégration")
class TemplateControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean TemplateService templateService;

    // ══════════════════════════════════════════════════════════════
    // POST /api/templates/generate
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /generate — données valides → 200 avec contenu")
    void generate_ok() throws Exception {
        when(templateService.validateTemplateData(eq(1L), anyMap())).thenReturn(true);
        when(templateService.generateFile(eq(1L), anyMap())).thenReturn("<xml>content</xml>");
        when(templateService.getFileExtension(1L)).thenReturn("xml");

        Map<String, Object> request = Map.of(
            "declarationTypeId", 1,
            "data", Map.of("CODE", "BCT_01")
        );

        mockMvc.perform(post("/api/templates/generate")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /generate — erreur validation → 400")
    void generate_erreurValidation_returns400() throws Exception {
        when(templateService.validateTemplateData(eq(1L), anyMap()))
            .thenThrow(new RuntimeException("Variable manquante: CODE"));

        Map<String, Object> request = Map.of(
            "declarationTypeId", 1,
            "data", Map.of()
        );

        mockMvc.perform(post("/api/templates/generate")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    // ══════════════════════════════════════════════════════════════
    // POST /api/templates/generate-and-save
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /generate-and-save — données valides → 200")
    void generateAndSave_ok() throws Exception {
        when(templateService.validateTemplateData(eq(1L), anyMap())).thenReturn(true);
        when(templateService.generateFile(eq(1L), anyMap())).thenReturn("<xml>content</xml>");
        when(templateService.getFileExtension(1L)).thenReturn("xml");

        Map<String, Object> request = Map.of(
            "declarationTypeId", 1,
            "data", Map.of("CODE", "BCT_01")
        );

        mockMvc.perform(post("/api/templates/generate-and-save")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.fileName").exists());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /generate-and-save — erreur → 400")
    void generateAndSave_erreur_returns400() throws Exception {
        when(templateService.validateTemplateData(eq(1L), anyMap()))
            .thenThrow(new RuntimeException("Type introuvable"));

        Map<String, Object> request = Map.of(
            "declarationTypeId", 1,
            "data", Map.of()
        );

        mockMvc.perform(post("/api/templates/generate-and-save")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/templates/{declarationTypeId}/variables
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /{id}/variables — retourne les variables → 200")
    void getVariables_ok() throws Exception {
        when(templateService.getRequiredVariables(1L)).thenReturn(List.of("CODE", "DATE", "MONTANT"));

        mockMvc.perform(get("/api/templates/1/variables"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.variables").isArray())
            .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /{id}/variables — type introuvable → 404")
    void getVariables_introuvable_returns404() throws Exception {
        when(templateService.getRequiredVariables(99L))
            .thenThrow(new RuntimeException("Type introuvable"));

        mockMvc.perform(get("/api/templates/99/variables"))
            .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/templates/{declarationTypeId}/preview
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /{id}/preview — retourne la prévisualisation → 200")
    void preview_ok() throws Exception {
        when(templateService.previewTemplate(1L)).thenReturn("<xml>preview</xml>");

        mockMvc.perform(get("/api/templates/1/preview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.preview").value("<xml>preview</xml>"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /{id}/preview — type introuvable → 404")
    void preview_introuvable_returns404() throws Exception {
        when(templateService.previewTemplate(99L))
            .thenThrow(new RuntimeException("Type introuvable"));

        mockMvc.perform(get("/api/templates/99/preview"))
            .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════
    // POST /api/templates/validate
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /validate — données valides → 200 valid=true")
    void validate_ok() throws Exception {
        when(templateService.validateTemplateData(eq(1L), anyMap())).thenReturn(true);

        Map<String, Object> request = Map.of(
            "declarationTypeId", 1,
            "data", Map.of("CODE", "BCT_01")
        );

        mockMvc.perform(post("/api/templates/validate")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /validate — données invalides → 400 valid=false")
    void validate_invalide_returns400() throws Exception {
        when(templateService.validateTemplateData(eq(1L), anyMap()))
            .thenThrow(new RuntimeException("Variable manquante"));

        Map<String, Object> request = Map.of(
            "declarationTypeId", 1,
            "data", Map.of()
        );

        mockMvc.perform(post("/api/templates/validate")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.valid").value(false));
    }
}
