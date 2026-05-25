package com.wifak.validationservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifak.validationservice.dto.GenerateDeclarationRequest;
import com.wifak.validationservice.entities.Declaration;
import com.wifak.validationservice.entities.DeclarationType;
import com.wifak.validationservice.repositories.DeclarationTypeRepository;
import com.wifak.validationservice.service.DeclarationService;
import com.wifak.validationservice.service.XmlGenerationService;
import com.wifak.validationservice.service.XsdAnalyzerService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeclarationController.class)
@ActiveProfiles("test")
@DisplayName("DeclarationController — Tests d'intégration")
class DeclarationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean DeclarationService declarationService;
    @MockBean XsdAnalyzerService xsdAnalyzerService;
    @MockBean XmlGenerationService xmlGenerationService;
    @MockBean DeclarationTypeRepository typeRepository;

    private Declaration declaration;
    private DeclarationType type;

    @BeforeEach
    void setUp() {
        type = new DeclarationType();
        type.setCode("DECL001");
        type.setNom("Test");
        type.setFormat(DeclarationType.DeclarationFormat.XML);
        type.setFrequence(DeclarationType.DeclarationFrequence.MENSUELLE);
        type.setActif(true);

        declaration = new Declaration();
        declaration.setId(1L);
        declaration.setDeclarationType(type);
        declaration.setStatut(Declaration.DeclarationStatut.GENEREE);
        declaration.setPeriode("2025-01");
        declaration.setNomFichier("declaration_DECL001_202501.xml");
        declaration.setContenuFichier("<xml>test</xml>");
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/declarations
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET / — retourne toutes les déclarations")
    void getAllDeclarations_ok() throws Exception {
        when(declarationService.getAllDeclarations()).thenReturn(List.of(declaration));

        mockMvc.perform(get("/api/declarations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].statut").value("GENEREE"));
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/declarations/my
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /my — retourne les déclarations de l'agent")
    void getMyDeclarations_ok() throws Exception {
        when(declarationService.getMyDeclarations()).thenReturn(List.of(declaration));

        mockMvc.perform(get("/api/declarations/my"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].periode").value("2025-01"));
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/declarations/{id}
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /{id} — retourne une déclaration par ID")
    void getById_ok() throws Exception {
        when(declarationService.findById(1L)).thenReturn(declaration);

        mockMvc.perform(get("/api/declarations/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /{id} — ID inexistant → 500")
    void getById_inexistant_error() throws Exception {
        when(declarationService.findById(99L))
            .thenThrow(new RuntimeException("Déclaration introuvable: 99"));

        mockMvc.perform(get("/api/declarations/99"))
            .andExpect(status().is5xxServerError());
    }

    // ══════════════════════════════════════════════════════════════
    // POST /api/declarations/generate
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /generate — génère une déclaration")
    void generate_ok() throws Exception {
        when(declarationService.generateAndSave(anyLong(), anyString(), any(), any()))
            .thenReturn(declaration);
        doNothing().when(declarationService).notifyJiraTicketCreation(anyLong(), anyString());

        GenerateDeclarationRequest req = new GenerateDeclarationRequest();
        req.setDeclarationTypeId(1L);
        req.setPeriode("2025-01");
        req.setDateDebut(LocalDate.of(2025, 1, 1));
        req.setDateFin(LocalDate.of(2025, 1, 31));

        mockMvc.perform(post("/api/declarations/generate")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1));
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/declarations/stats
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /stats — retourne les statistiques")
    void getStats_ok() throws Exception {
        DeclarationService.DeclarationStats stats = new DeclarationService.DeclarationStats();
        stats.setTotal(5L);
        stats.setGenerees(3L);
        when(declarationService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/api/declarations/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(5));
    }

    // ══════════════════════════════════════════════════════════════
    // DELETE /api/declarations/{id}
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("DELETE /{id} — supprime une déclaration")
    void delete_ok() throws Exception {
        doNothing().when(declarationService).deleteDeclaration(1L);

        mockMvc.perform(delete("/api/declarations/1").with(csrf()))
            .andExpect(status().isNoContent());
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/declarations/{id}/download
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /{id}/download — télécharge le fichier")
    void download_ok() throws Exception {
        when(declarationService.findById(1L)).thenReturn(declaration);

        mockMvc.perform(get("/api/declarations/1/download"))
            .andExpect(status().isOk())
            .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /{id}/download — contenu vide → 404")
    void download_contenuVide_notFound() throws Exception {
        declaration.setContenuFichier(null);
        when(declarationService.findById(1L)).thenReturn(declaration);

        mockMvc.perform(get("/api/declarations/1/download"))
            .andExpect(status().isNotFound());
    }
}
