package com.wifak.validationservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifak.validationservice.dto.GenerateDeclarationRequest;
import com.wifak.validationservice.dto.XsdSqlMappingRequest;
import com.wifak.validationservice.entities.Declaration;
import com.wifak.validationservice.entities.DeclarationType;
import com.wifak.validationservice.feign.JiraIntegrationFeignClient;
import com.wifak.validationservice.repositories.DeclarationRepository;
import com.wifak.validationservice.repositories.DeclarationTypeRepository;
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
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ActiveProfiles("test")
@DisplayName("DeclarationService — Tests étendus")
class DeclarationServiceExtendedTest {

    @Mock private DeclarationRepository declarationRepository;
    @Mock private DeclarationTypeRepository typeRepository;
    @Mock private XmlGenerationService xmlGenerationService;
    @Mock private CsvGenerationService csvGenerationService;
    @Mock private TxtGenerationService txtGenerationService;
    @Mock private JiraIntegrationFeignClient jiraClient;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private DeclarationService service;

    private DeclarationType xmlType;
    private DeclarationType csvType;
    private DeclarationType txtType;
    private Declaration genereeDeclaration;

    @BeforeEach
    void setUp() {
        xmlType = buildType("DECL_XML", DeclarationType.DeclarationFormat.XML);
        csvType = buildType("DECL_CSV", DeclarationType.DeclarationFormat.CSV);
        txtType = buildType("DECL_TXT", DeclarationType.DeclarationFormat.TXT);

        genereeDeclaration = new Declaration();
        genereeDeclaration.setId(1L);
        genereeDeclaration.setDeclarationType(xmlType);
        genereeDeclaration.setStatut(Declaration.DeclarationStatut.GENEREE);
        genereeDeclaration.setPeriode("2025-01");
        genereeDeclaration.setGenerePar("agent1");

        mockSecurityContext("agent1");
    }

    private DeclarationType buildType(String code, DeclarationType.DeclarationFormat format) {
        DeclarationType t = new DeclarationType();
        t.setCode(code);
        t.setNom("Type " + code);
        t.setActif(true);
        t.setSqlQuery("SELECT * FROM test");
        t.setFormat(format);
        t.setFrequence(DeclarationType.DeclarationFrequence.MENSUELLE);
        t.setXsdContent("<xs:schema/>");
        t.setXsdFileName("schema.xsd");
        return t;
    }

    private void mockSecurityContext(String username) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(username);
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    // ══════════════════════════════════════════════════════════════
    // getAllDeclarations
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getAllDeclarations — retourne toutes les déclarations")
    void getAllDeclarations_retourneTout() {
        when(declarationRepository.findAll()).thenReturn(List.of(genereeDeclaration));

        List<Declaration> result = service.getAllDeclarations();

        assertThat(result).hasSize(1);
        verify(declarationRepository).findAll();
    }

    // ══════════════════════════════════════════════════════════════
    // getMyDeclarations
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getMyDeclarations — retourne les déclarations de l'utilisateur courant")
    void getMyDeclarations_retourneLesMiennes() {
        when(declarationRepository.findByGenerePar("agent1")).thenReturn(List.of(genereeDeclaration));

        List<Declaration> result = service.getMyDeclarations();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGenerePar()).isEqualTo("agent1");
    }

    @Test
    @DisplayName("getMyDeclarations — aucune déclaration → liste vide")
    void getMyDeclarations_listeVide() {
        when(declarationRepository.findByGenerePar("agent1")).thenReturn(List.of());

        List<Declaration> result = service.getMyDeclarations();

        assertThat(result).isEmpty();
    }

    // ══════════════════════════════════════════════════════════════
    // notifyJiraTicketCreation
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("notifyJiraTicketCreation — Jira disponible → ticket créé")
    void notifyJira_disponible_ok() {
        when(jiraClient.createTicket(any())).thenReturn(null);

        assertThatCode(() -> service.notifyJiraTicketCreation(1L, "agent1"))
            .doesNotThrowAnyException();

        verify(jiraClient).createTicket(any());
    }

    @Test
    @DisplayName("notifyJiraTicketCreation — Jira indisponible → warn sans exception")
    void notifyJira_indisponible_pasException() {
        when(jiraClient.createTicket(any())).thenThrow(new RuntimeException("Jira down"));

        assertThatCode(() -> service.notifyJiraTicketCreation(1L, "agent1"))
            .doesNotThrowAnyException();
    }

    // ══════════════════════════════════════════════════════════════
    // generateAndSave — formats CSV et TXT
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("generateAndSave — format CSV → génère via CsvGenerationService")
    void generateAndSave_csv_ok() {
        when(typeRepository.findById(2L)).thenReturn(Optional.of(csvType));
        when(csvGenerationService.generateCsvFromSql(anyString(), any(), any(), anyString(), anyString()))
            .thenReturn("col1,col2\nval1,val2");
        when(declarationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Declaration result = service.generateAndSave(2L, "2025-01",
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertThat(result).isNotNull();
        assertThat(result.getNomFichier()).endsWith(".csv");
        verify(csvGenerationService).generateCsvFromSql(anyString(), any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("generateAndSave — format TXT → génère via TxtGenerationService")
    void generateAndSave_txt_ok() {
        when(typeRepository.findById(3L)).thenReturn(Optional.of(txtType));
        when(txtGenerationService.generateTxtFromSql(anyString(), any(), any(), anyString(), anyString()))
            .thenReturn("ligne1\nligne2");
        when(declarationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Declaration result = service.generateAndSave(3L, "2025-01",
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertThat(result).isNotNull();
        assertThat(result.getNomFichier()).endsWith(".txt");
        verify(txtGenerationService).generateTxtFromSql(anyString(), any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("generateAndSave — format XML → génère via XmlGenerationService")
    void generateAndSave_xml_ok() {
        when(typeRepository.findById(1L)).thenReturn(Optional.of(xmlType));
        when(xmlGenerationService.generateXmlFromXsdAndSql(anyString(), anyString(), any(), any(), anyString(), anyString()))
            .thenReturn("<xml>content</xml>");
        when(declarationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Declaration result = service.generateAndSave(1L, "2025-01",
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertThat(result).isNotNull();
        assertThat(result.getNomFichier()).endsWith(".xml");
    }

    @Test
    @DisplayName("generateAndSave — type introuvable → RuntimeException")
    void generateAndSave_typeIntrouvable_throwsException() {
        when(typeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateAndSave(99L, "2025-01",
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("99");
    }

    // ══════════════════════════════════════════════════════════════
    // generateAndSaveWithMapping
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("generateAndSaveWithMapping — XML avec mapping → sauvegarde avec mappingJson")
    void generateAndSaveWithMapping_xml_ok() throws Exception {
        XsdSqlMappingRequest.FieldMapping mapping = new XsdSqlMappingRequest.FieldMapping();
        mapping.setXsdFieldName("field1");
        mapping.setSqlColumn("col1");

        when(typeRepository.findById(1L)).thenReturn(Optional.of(xmlType));
        when(xmlGenerationService.generateXmlFromMapping(anyString(), anyString(), any(), any(),
            anyString(), anyString(), anyList())).thenReturn("<xml>mapped</xml>");
        when(objectMapper.writeValueAsString(anyList())).thenReturn("[{\"xsdField\":\"field1\"}]");
        when(declarationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Declaration result = service.generateAndSaveWithMapping(1L, "2025-01",
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31), List.of(mapping));

        assertThat(result).isNotNull();
        assertThat(result.getMappingJson()).isNotNull();
    }

    @Test
    @DisplayName("generateAndSaveWithMapping — CSV avec mapping → génération standard (mapping ignoré)")
    void generateAndSaveWithMapping_csv_generationStandard() throws Exception {
        XsdSqlMappingRequest.FieldMapping mapping = new XsdSqlMappingRequest.FieldMapping();

        when(typeRepository.findById(2L)).thenReturn(Optional.of(csvType));
        when(csvGenerationService.generateCsvFromSql(anyString(), any(), any(), anyString(), anyString()))
            .thenReturn("col1,col2");
        when(objectMapper.writeValueAsString(anyList())).thenReturn("[]");
        when(declarationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Declaration result = service.generateAndSaveWithMapping(2L, "2025-01",
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31), List.of(mapping));

        assertThat(result).isNotNull();
        verify(csvGenerationService).generateCsvFromSql(anyString(), any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("generateAndSaveWithMapping — sérialisation mapping échoue → warn sans exception")
    void generateAndSaveWithMapping_serializationEchoue_pasException() throws Exception {
        XsdSqlMappingRequest.FieldMapping mapping = new XsdSqlMappingRequest.FieldMapping();

        when(typeRepository.findById(1L)).thenReturn(Optional.of(xmlType));
        when(xmlGenerationService.generateXmlFromMapping(anyString(), anyString(), any(), any(),
            anyString(), anyString(), anyList())).thenReturn("<xml>mapped</xml>");
        when(objectMapper.writeValueAsString(anyList())).thenThrow(new RuntimeException("JSON error"));
        when(declarationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> service.generateAndSaveWithMapping(1L, "2025-01",
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31), List.of(mapping)))
            .doesNotThrowAnyException();
    }

    // ══════════════════════════════════════════════════════════════
    // patchContent — cas REJETEE
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("patchContent — statut REJETEE → mise à jour OK")
    void patchContent_rejetee_ok() {
        genereeDeclaration.setStatut(Declaration.DeclarationStatut.REJETEE);
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));
        when(declarationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Declaration result = service.patchContent(1L, "<xml>corrected</xml>");

        assertThat(result.getStatut()).isEqualTo(Declaration.DeclarationStatut.GENEREE);
        assertThat(result.getCommentaireRejet()).isNull();
    }

    @Test
    @DisplayName("patchContent — statut VALIDEE → RuntimeException")
    void patchContent_validee_throwsException() {
        genereeDeclaration.setStatut(Declaration.DeclarationStatut.VALIDEE);
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));

        assertThatThrownBy(() -> service.patchContent(1L, "<xml/>"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("VALIDEE");
    }

    // ══════════════════════════════════════════════════════════════
    // updateStatut — cas VALIDEE, ENVOYEE
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("updateStatut — VALIDEE → dateValidation et validePar définis")
    void updateStatut_validee_ok() {
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));
        when(declarationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Declaration result = service.updateStatut(1L, "VALIDEE", null, "manager1");

        assertThat(result.getStatut()).isEqualTo(Declaration.DeclarationStatut.VALIDEE);
        assertThat(result.getValidePar()).isEqualTo("manager1");
        assertThat(result.getDateValidation()).isNotNull();
    }

    @Test
    @DisplayName("updateStatut — VALIDEE sans validePar → utilise getCurrentUsername")
    void updateStatut_validee_sansValidePar_utiliseCourant() {
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));
        when(declarationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Declaration result = service.updateStatut(1L, "VALIDEE", null, null);

        assertThat(result.getValidePar()).isEqualTo("agent1");
    }

    @Test
    @DisplayName("updateStatut — ENVOYEE → dateEnvoi définie")
    void updateStatut_envoyee_ok() {
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));
        when(declarationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Declaration result = service.updateStatut(1L, "ENVOYEE", null, null);

        assertThat(result.getStatut()).isEqualTo(Declaration.DeclarationStatut.ENVOYEE);
        assertThat(result.getDateEnvoi()).isNotNull();
    }

    @Test
    @DisplayName("updateStatut — EN_VALIDATION → statut mis à jour")
    void updateStatut_enValidation_ok() {
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));
        when(declarationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Declaration result = service.updateStatut(1L, "EN_VALIDATION", null, null);

        assertThat(result.getStatut()).isEqualTo(Declaration.DeclarationStatut.EN_VALIDATION);
    }

    @Test
    @DisplayName("updateStatut — REJETEE sans validePar → utilise getCurrentUsername")
    void updateStatut_rejetee_sansValidePar_utiliseCourant() {
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));
        when(declarationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Declaration result = service.updateStatut(1L, "REJETEE", "Erreur format", null);

        assertThat(result.getValidePar()).isEqualTo("agent1");
        assertThat(result.getCommentaireRejet()).isEqualTo("Erreur format");
    }

    // ══════════════════════════════════════════════════════════════
    // updateDeclaration
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("updateDeclaration — GENEREE → régénère et sauvegarde")
    void updateDeclaration_generee_ok() {
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));
        when(typeRepository.findById(1L)).thenReturn(Optional.of(xmlType));
        when(xmlGenerationService.generateXmlFromXsdAndSql(anyString(), anyString(), any(), any(), anyString(), anyString()))
            .thenReturn("<xml>updated</xml>");
        when(declarationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GenerateDeclarationRequest req = new GenerateDeclarationRequest();
        req.setDeclarationTypeId(1L);
        req.setPeriode("2025-02");
        req.setDateDebut(LocalDate.of(2025, 2, 1));
        req.setDateFin(LocalDate.of(2025, 2, 28));

        Declaration result = service.updateDeclaration(1L, req);

        assertThat(result).isNotNull();
        assertThat(result.getPeriode()).isEqualTo("2025-02");
        assertThat(result.getStatut()).isEqualTo(Declaration.DeclarationStatut.GENEREE);
    }

    @Test
    @DisplayName("updateDeclaration — REJETEE → régénère OK")
    void updateDeclaration_rejetee_ok() {
        genereeDeclaration.setStatut(Declaration.DeclarationStatut.REJETEE);
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));
        when(typeRepository.findById(1L)).thenReturn(Optional.of(xmlType));
        when(xmlGenerationService.generateXmlFromXsdAndSql(anyString(), anyString(), any(), any(), anyString(), anyString()))
            .thenReturn("<xml>updated</xml>");
        when(declarationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GenerateDeclarationRequest req = new GenerateDeclarationRequest();
        req.setDeclarationTypeId(1L);
        req.setPeriode("2025-01");
        req.setDateDebut(LocalDate.of(2025, 1, 1));
        req.setDateFin(LocalDate.of(2025, 1, 31));

        assertThatCode(() -> service.updateDeclaration(1L, req)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("updateDeclaration — EN_VALIDATION → RuntimeException")
    void updateDeclaration_enValidation_throwsException() {
        genereeDeclaration.setStatut(Declaration.DeclarationStatut.EN_VALIDATION);
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));

        GenerateDeclarationRequest req = new GenerateDeclarationRequest();
        req.setDeclarationTypeId(1L);
        req.setPeriode("2025-01");
        req.setDateDebut(LocalDate.of(2025, 1, 1));
        req.setDateFin(LocalDate.of(2025, 1, 31));

        assertThatThrownBy(() -> service.updateDeclaration(1L, req))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("EN_VALIDATION");
    }

    @Test
    @DisplayName("updateDeclaration — avec mappingJson existant → réutilise le mapping")
    void updateDeclaration_avecMappingJson_reutiliseMapping() throws Exception {
        genereeDeclaration.setMappingJson("[{\"xsdField\":\"field1\",\"sqlColumn\":\"col1\"}]");
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));
        when(typeRepository.findById(1L)).thenReturn(Optional.of(xmlType));

        XsdSqlMappingRequest.FieldMapping fm = new XsdSqlMappingRequest.FieldMapping();
        fm.setXsdFieldName("field1");
        fm.setSqlColumn("col1");
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenReturn(List.of(fm));
        when(xmlGenerationService.generateXmlFromMapping(anyString(), anyString(), any(), any(),
            anyString(), anyString(), anyList())).thenReturn("<xml>remapped</xml>");
        when(declarationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GenerateDeclarationRequest req = new GenerateDeclarationRequest();
        req.setDeclarationTypeId(1L);
        req.setPeriode("2025-01");
        req.setDateDebut(LocalDate.of(2025, 1, 1));
        req.setDateFin(LocalDate.of(2025, 1, 31));

        Declaration result = service.updateDeclaration(1L, req);

        assertThat(result).isNotNull();
        verify(xmlGenerationService).generateXmlFromMapping(anyString(), anyString(), any(), any(),
            anyString(), anyString(), anyList());
    }

    @Test
    @DisplayName("updateDeclaration — mappingJson invalide → génération générique")
    void updateDeclaration_mappingJsonInvalide_generationGenerique() throws Exception {
        genereeDeclaration.setMappingJson("invalid-json");
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));
        when(typeRepository.findById(1L)).thenReturn(Optional.of(xmlType));
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenThrow(new RuntimeException("JSON parse error"));
        when(xmlGenerationService.generateXmlFromXsdAndSql(anyString(), anyString(), any(), any(), anyString(), anyString()))
            .thenReturn("<xml>generic</xml>");
        when(declarationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GenerateDeclarationRequest req = new GenerateDeclarationRequest();
        req.setDeclarationTypeId(1L);
        req.setPeriode("2025-01");
        req.setDateDebut(LocalDate.of(2025, 1, 1));
        req.setDateFin(LocalDate.of(2025, 1, 31));

        assertThatCode(() -> service.updateDeclaration(1L, req)).doesNotThrowAnyException();
        verify(xmlGenerationService).generateXmlFromXsdAndSql(anyString(), anyString(), any(), any(), anyString(), anyString());
    }

    // ══════════════════════════════════════════════════════════════
    // deleteDeclaration — cas ENVOYEE
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("deleteDeclaration — ENVOYEE → RuntimeException")
    void deleteDeclaration_envoyee_throwsException() {
        genereeDeclaration.setStatut(Declaration.DeclarationStatut.ENVOYEE);
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));

        assertThatThrownBy(() -> service.deleteDeclaration(1L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("ENVOYEE");
    }
}
