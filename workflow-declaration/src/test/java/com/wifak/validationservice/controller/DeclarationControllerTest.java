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
        type.setXsdContent("<xs:schema/>");
        type.setSqlQuery("SELECT * FROM test");

        declaration = new Declaration();
        declaration.setId(1L);
        declaration.setDeclarationType(type);
        declaration.setStatut(Declaration.DeclarationStatut.GENEREE);
        declaration.setPeriode("2025-01");
        declaration.setNomFichier("declaration_DECL001_202501.xml");
        declaration.setContenuFichier("<xml>test</xml>");
    }
    // GET /api/declarations
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
    // GET /api/declarations/my
    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /my — retourne les déclarations de l'agent")
    void getMyDeclarations_ok() throws Exception {
        when(declarationService.getMyDeclarations()).thenReturn(List.of(declaration));

        mockMvc.perform(get("/api/declarations/my"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].periode").value("2025-01"));
    }
    // GET /api/declarations/{id}
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
    // POST /api/declarations/generate
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
    // GET /api/declarations/stats
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
    // DELETE /api/declarations/{id}
    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("DELETE /{id} — supprime une déclaration")
    void delete_ok() throws Exception {
        doNothing().when(declarationService).deleteDeclaration(1L);

        mockMvc.perform(delete("/api/declarations/1").with(csrf()))
            .andExpect(status().isNoContent());
    }
    // GET /api/declarations/{id}/download
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
    // PUT /api/declarations/{id}
    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("PUT /{id} — met à jour une déclaration → 200")
    void update_ok() throws Exception {
        when(declarationService.updateDeclaration(eq(1L), any())).thenReturn(declaration);

        GenerateDeclarationRequest req = new GenerateDeclarationRequest();
        req.setDeclarationTypeId(1L);
        req.setPeriode("2025-02");
        req.setDateDebut(java.time.LocalDate.of(2025, 2, 1));
        req.setDateFin(java.time.LocalDate.of(2025, 2, 28));

        mockMvc.perform(put("/api/declarations/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1));
    }
    // PATCH /api/declarations/{id}/statut
    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("PATCH /{id}/statut — met à jour le statut → 200")
    void updateStatut_ok() throws Exception {
        declaration.setStatut(Declaration.DeclarationStatut.EN_VALIDATION);
        when(declarationService.updateStatut(eq(1L), eq("EN_VALIDATION"), any(), any()))
            .thenReturn(declaration);

        mockMvc.perform(patch("/api/declarations/1/statut")
                .with(csrf())
                .param("statut", "EN_VALIDATION"))
            .andExpect(status().isOk());
    }
    // PATCH /api/declarations/{id}/content
    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("PATCH /{id}/content — GENEREE avec contenu valide → 200")
    void patchContent_ok() throws Exception {
        when(declarationService.findById(1L)).thenReturn(declaration);
        when(declarationService.patchContent(eq(1L), anyString())).thenReturn(declaration);

        mockMvc.perform(patch("/api/declarations/1/content")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"xmlContent\": \"<xml>corrected</xml>\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("PATCH /{id}/content — contenu vide → 400")
    void patchContent_videReturns400() throws Exception {
        when(declarationService.findById(1L)).thenReturn(declaration);

        mockMvc.perform(patch("/api/declarations/1/content")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"xmlContent\": \"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("PATCH /{id}/content — statut EN_VALIDATION → 400")
    void patchContent_enValidation_returns400() throws Exception {
        declaration.setStatut(Declaration.DeclarationStatut.EN_VALIDATION);
        when(declarationService.findById(1L)).thenReturn(declaration);

        mockMvc.perform(patch("/api/declarations/1/content")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"xmlContent\": \"<xml>test</xml>\"}"))
            .andExpect(status().isBadRequest());
    }
    // POST /api/declarations/analyze-mapping
    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /analyze-mapping — XML avec XSD et SQL → 200 avec analyse")
    void analyzeMapping_xml_ok() throws Exception {
        when(typeRepository.findById(1L)).thenReturn(java.util.Optional.of(type));
        when(xmlGenerationService.extractColumnsFromSql(anyString(), any(), any()))
            .thenReturn(java.util.List.of("col1", "col2"));

        XsdAnalyzerService.MappingAnalysisResult analysis = mock(XsdAnalyzerService.MappingAnalysisResult.class);
        when(analysis.getXsdFields()).thenReturn(java.util.List.of());
        when(analysis.getSqlColumns()).thenReturn(java.util.List.of("col1", "col2"));
        when(analysis.getAutoMapped()).thenReturn(java.util.Map.of());
        when(analysis.getUnmappedXsdFields()).thenReturn(java.util.List.of());
        when(analysis.getUnmappedSqlColumns()).thenReturn(java.util.List.of());
        when(analysis.getCompatibilityScore()).thenReturn(80);
        when(analysis.getSummary()).thenReturn("OK");
        when(xsdAnalyzerService.analyzeCompatibility(anyString(), anyList())).thenReturn(analysis);

        mockMvc.perform(post("/api/declarations/analyze-mapping")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"declarationTypeId\": 1, \"dateDebut\": \"2025-01-01\", \"dateFin\": \"2025-01-31\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applicable").value(true));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /analyze-mapping — format CSV → applicable=false")
    void analyzeMapping_csv_notApplicable() throws Exception {
        type.setFormat(DeclarationType.DeclarationFormat.CSV);
        when(typeRepository.findById(1L)).thenReturn(java.util.Optional.of(type));

        mockMvc.perform(post("/api/declarations/analyze-mapping")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"declarationTypeId\": 1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applicable").value(false));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /analyze-mapping — XSD absent → 400")
    void analyzeMapping_xsdAbsent_returns400() throws Exception {
        type.setXsdContent(null);
        when(typeRepository.findById(1L)).thenReturn(java.util.Optional.of(type));

        mockMvc.perform(post("/api/declarations/analyze-mapping")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"declarationTypeId\": 1}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /analyze-mapping — SQL absent → 400")
    void analyzeMapping_sqlAbsent_returns400() throws Exception {
        type.setSqlQuery(null);
        when(typeRepository.findById(1L)).thenReturn(java.util.Optional.of(type));

        mockMvc.perform(post("/api/declarations/analyze-mapping")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"declarationTypeId\": 1}"))
            .andExpect(status().isBadRequest());
    }
    // POST /api/declarations/generate-with-mapping
    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /generate-with-mapping — mapping valide → 200")
    void generateWithMapping_ok() throws Exception {
        when(declarationService.generateAndSaveWithMapping(anyLong(), anyString(), any(), any(), anyList()))
            .thenReturn(declaration);
        doNothing().when(declarationService).notifyJiraTicketCreation(anyLong(), anyString());

        String body = "{\"declarationTypeId\":1,\"periode\":\"2025-01\"," +
            "\"dateDebut\":\"2025-01-01\",\"dateFin\":\"2025-01-31\"," +
            "\"mappings\":[{\"xsdFieldName\":\"field1\",\"sqlColumn\":\"col1\",\"required\":false}]}";

        mockMvc.perform(post("/api/declarations/generate-with-mapping")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /generate-with-mapping — mapping vide → 400")
    void generateWithMapping_mappingVide_returns400() throws Exception {
        String body = "{\"declarationTypeId\":1,\"periode\":\"2025-01\"," +
            "\"dateDebut\":\"2025-01-01\",\"dateFin\":\"2025-01-31\",\"mappings\":[]}";

        mockMvc.perform(post("/api/declarations/generate-with-mapping")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }
    // Branches supplémentaires — DeclarationController
    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /analyze-mapping — type introuvable → 500")
    void analyzeMapping_typeIntrouvable_returns500() throws Exception {
        when(typeRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(post("/api/declarations/analyze-mapping")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"declarationTypeId\": 99}"))
            .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /analyze-mapping — extractColumns échoue → continue avec liste vide")
    void analyzeMapping_extractColumnsEchoue_continueAvecListeVide() throws Exception {
        when(typeRepository.findById(1L)).thenReturn(java.util.Optional.of(type));
        when(xmlGenerationService.extractColumnsFromSql(anyString(), any(), any()))
            .thenThrow(new RuntimeException("SQL error"));

        XsdAnalyzerService.MappingAnalysisResult analysis = mock(XsdAnalyzerService.MappingAnalysisResult.class);
        when(analysis.getXsdFields()).thenReturn(java.util.List.of());
        when(analysis.getSqlColumns()).thenReturn(java.util.List.of());
        when(analysis.getAutoMapped()).thenReturn(java.util.Map.of());
        when(analysis.getUnmappedXsdFields()).thenReturn(java.util.List.of());
        when(analysis.getUnmappedSqlColumns()).thenReturn(java.util.List.of());
        when(analysis.getCompatibilityScore()).thenReturn(0);
        when(analysis.getSummary()).thenReturn("No columns");
        when(xsdAnalyzerService.analyzeCompatibility(anyString(), anyList())).thenReturn(analysis);

        mockMvc.perform(post("/api/declarations/analyze-mapping")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"declarationTypeId\": 1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applicable").value(true));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /generate-with-mapping — champ obligatoire non mappé → 400")
    void generateWithMapping_champObligatoireNonMappe_returns400() throws Exception {
        String body = "{\"declarationTypeId\":1,\"periode\":\"2025-01\"," +
            "\"dateDebut\":\"2025-01-01\",\"dateFin\":\"2025-01-31\"," +
            "\"mappings\":[{\"xsdFieldName\":\"field1\",\"sqlColumn\":null,\"required\":true,\"source\":\"NONE\"}]}";

        mockMvc.perform(post("/api/declarations/generate-with-mapping")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /generate-with-mapping — erreur service → 500")
    void generateWithMapping_erreurService_returns500() throws Exception {
        when(declarationService.generateAndSaveWithMapping(anyLong(), anyString(), any(), any(), anyList()))
            .thenThrow(new RuntimeException("Generation failed"));

        String body = "{\"declarationTypeId\":1,\"periode\":\"2025-01\"," +
            "\"dateDebut\":\"2025-01-01\",\"dateFin\":\"2025-01-31\"," +
            "\"mappings\":[{\"xsdFieldName\":\"field1\",\"sqlColumn\":\"col1\",\"required\":false,\"source\":\"SQL\"}]}";

        mockMvc.perform(post("/api/declarations/generate-with-mapping")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /{id}/download — fichier CSV → Content-Type text/csv")
    void download_csv_contentType() throws Exception {
        declaration.setNomFichier("declaration_DECL001_202501.csv");
        when(declarationService.findById(1L)).thenReturn(declaration);

        mockMvc.perform(get("/api/declarations/1/download"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "text/csv"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /{id}/download — fichier TXT → Content-Type text/plain")
    void download_txt_contentType() throws Exception {
        declaration.setNomFichier("declaration_DECL001_202501.txt");
        when(declarationService.findById(1L)).thenReturn(declaration);

        mockMvc.perform(get("/api/declarations/1/download"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "text/plain"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("PATCH /{id}/content — erreur service → 500")
    void patchContent_erreurService_returns500() throws Exception {
        when(declarationService.findById(1L)).thenReturn(declaration);
        when(declarationService.patchContent(eq(1L), anyString()))
            .thenThrow(new RuntimeException("Patch failed"));

        mockMvc.perform(patch("/api/declarations/1/content")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"xmlContent\": \"<xml>test</xml>\"}"))
            .andExpect(status().isInternalServerError());
    }
}
