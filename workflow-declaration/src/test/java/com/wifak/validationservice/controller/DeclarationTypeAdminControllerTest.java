package com.wifak.validationservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifak.validationservice.entities.DeclarationType;
import com.wifak.validationservice.repositories.ValidationRuleRepository;
import com.wifak.validationservice.service.DeclarationTypeService;
import com.wifak.validationservice.service.PdfGeneratorService;
import com.wifak.validationservice.service.XmlGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeclarationTypeAdminController.class)
@ActiveProfiles("test")
@DisplayName("DeclarationTypeAdminController — Tests d'intégration")
class DeclarationTypeAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean DeclarationTypeService service;
    @MockBean ValidationRuleRepository validationRuleRepository;
    @MockBean PdfGeneratorService pdfGeneratorService;
    @MockBean XmlGenerationService xmlGenerationService;

    private DeclarationType type;

    @BeforeEach
    void setUp() {
        type = new DeclarationType();
        type.setCode("DECL001");
        type.setNom("Déclaration Test");
        type.setActif(true);
        type.setFormat(DeclarationType.DeclarationFormat.XML);
        type.setFrequence(DeclarationType.DeclarationFrequence.MENSUELLE);
    }

    // ══════════════════════════════════════════════════════════════
    // POST /api/admin/declaration-types
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST / — crée un type de déclaration → 200")
    void create_ok() throws Exception {
        when(service.create(any())).thenReturn(type);

        mockMvc.perform(post("/api/admin/declaration-types")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(type)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("DECL001"));
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/admin/declaration-types
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET / — retourne tous les types → 200")
    void getAll_ok() throws Exception {
        when(service.getAll()).thenReturn(List.of(type));

        mockMvc.perform(get("/api/admin/declaration-types"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("DECL001"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET / — AGENT peut lire → 200")
    void getAll_agent_ok() throws Exception {
        when(service.getAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/declaration-types"))
            .andExpect(status().isOk());
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/admin/declaration-types/{id}
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /{id} — retourne le type → 200")
    void getById_ok() throws Exception {
        when(service.getById(1L)).thenReturn(type);

        mockMvc.perform(get("/api/admin/declaration-types/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("DECL001"));
    }

    // ══════════════════════════════════════════════════════════════
    // PUT /api/admin/declaration-types/{id}
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /{id} — met à jour le type → 200")
    void update_ok() throws Exception {
        when(service.update(eq(1L), any())).thenReturn(type);

        mockMvc.perform(put("/api/admin/declaration-types/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(type)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("DECL001"));
    }

    // ══════════════════════════════════════════════════════════════
    // DELETE /api/admin/declaration-types/{id}
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /{id} — supprime le type → 200")
    void delete_ok() throws Exception {
        doNothing().when(service).delete(1L);

        mockMvc.perform(delete("/api/admin/declaration-types/1").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());
    }

    // ══════════════════════════════════════════════════════════════
    // PATCH /api/admin/declaration-types/{id}/toggle
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PATCH /{id}/toggle — bascule le statut → 200")
    void toggleStatus_ok() throws Exception {
        type.setActif(false);
        when(service.toggleStatus(1L)).thenReturn(type);

        mockMvc.perform(patch("/api/admin/declaration-types/1/toggle").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.actif").value(false));
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/admin/declaration-types/{id}/validation-rules
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /{id}/validation-rules — retourne les règles → 200")
    void getValidationRules_ok() throws Exception {
        when(validationRuleRepository.findByDeclarationTypeId(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/declaration-types/1/validation-rules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    // ══════════════════════════════════════════════════════════════
    // POST /api/admin/declaration-types/{id}/xsd
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /{id}/xsd — upload XSD valide → 200")
    void uploadXsd_ok() throws Exception {
        when(service.saveXsd(eq(1L), anyString(), anyString())).thenReturn(type);

        MockMultipartFile file = new MockMultipartFile(
            "file", "schema.xsd", "application/xml", "<xs:schema/>".getBytes()
        );

        mockMvc.perform(multipart("/api/admin/declaration-types/1/xsd")
                .file(file)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("XSD uploadé avec succès"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /{id}/xsd — fichier non .xsd → 400")
    void uploadXsd_mauvaisFormat_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "schema.xml", "application/xml", "<xs:schema/>".getBytes()
        );

        mockMvc.perform(multipart("/api/admin/declaration-types/1/xsd")
                .file(file)
                .with(csrf()))
            .andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/admin/declaration-types/{id}/xsd/download
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /{id}/xsd/download — XSD présent → 200 avec fichier")
    void downloadXsd_ok() throws Exception {
        type.setXsdContent("<xs:schema/>");
        type.setXsdFileName("schema.xsd");
        when(service.getById(1L)).thenReturn(type);

        mockMvc.perform(get("/api/admin/declaration-types/1/xsd/download"))
            .andExpect(status().isOk())
            .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /{id}/xsd/download — XSD absent → 404")
    void downloadXsd_absent_notFound() throws Exception {
        type.setXsdContent(null);
        when(service.getById(1L)).thenReturn(type);

        mockMvc.perform(get("/api/admin/declaration-types/1/xsd/download"))
            .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════
    // PATCH /api/admin/declaration-types/{id}/sql
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PATCH /{id}/sql — SQL SELECT valide → 200")
    void saveSqlQuery_ok() throws Exception {
        when(service.saveSqlQuery(eq(1L), anyString())).thenReturn(type);

        mockMvc.perform(patch("/api/admin/declaration-types/1/sql")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sqlQuery\": \"SELECT * FROM test\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Requête SQL sauvegardée avec succès"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PATCH /{id}/sql — SQL vide → 400")
    void saveSqlQuery_vide_returns400() throws Exception {
        mockMvc.perform(patch("/api/admin/declaration-types/1/sql")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sqlQuery\": \"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PATCH /{id}/sql — SQL ne commence pas par SELECT → 400")
    void saveSqlQuery_nonSelect_returns400() throws Exception {
        mockMvc.perform(patch("/api/admin/declaration-types/1/sql")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sqlQuery\": \"DELETE FROM test\"}"))
            .andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════════════════════════
    // POST /api/admin/declaration-types/{id}/sql/test
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /{id}/sql/test — SQL valide → 200 avec colonnes")
    void testSqlQuery_ok() throws Exception {
        type.setSqlQuery("SELECT * FROM test");
        when(service.getById(1L)).thenReturn(type);
        when(xmlGenerationService.extractColumnsFromSql(anyString(), any(), any()))
            .thenReturn(List.of("col1", "col2"));

        mockMvc.perform(post("/api/admin/declaration-types/1/sql/test")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dateDebut\": \"2025-01-01\", \"dateFin\": \"2025-01-31\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /{id}/sql/test — SQL null → 400")
    void testSqlQuery_sqlNull_returns400() throws Exception {
        type.setSqlQuery(null);
        when(service.getById(1L)).thenReturn(type);

        mockMvc.perform(post("/api/admin/declaration-types/1/sql/test")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dateDebut\": \"2025-01-01\", \"dateFin\": \"2025-01-31\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /{id}/sql/test — SQL avec DROP → 400 (injection bloquée)")
    void testSqlQuery_sqlDrop_returns400() throws Exception {
        type.setSqlQuery("SELECT * FROM test; DROP TABLE test");
        when(service.getById(1L)).thenReturn(type);

        mockMvc.perform(post("/api/admin/declaration-types/1/sql/test")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dateDebut\": \"2025-01-01\", \"dateFin\": \"2025-01-31\"}"))
            .andExpect(status().isBadRequest());
    }
}
