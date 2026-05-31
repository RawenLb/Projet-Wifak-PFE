package com.wifak.validationservice.dto;

import com.wifak.validationservice.dto.jira.CreateJiraTicketRequest;
import com.wifak.validationservice.dto.jira.JiraTicketResponseDTO;
import com.wifak.validationservice.dto.jira.TransitionJiraTicketRequest;
import com.wifak.validationservice.entities.Declaration;
import com.wifak.validationservice.entities.DeclarationTemplate;
import com.wifak.validationservice.entities.DeclarationType;
import com.wifak.validationservice.entities.ValidationLog;
import com.wifak.validationservice.entities.ValidationRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DTOs et Entités — Tests getters/setters")
class DtoGetterSetterTest {
    // AiValidationResult
    @Test
    void aiValidationResult_gettersSetters() {
        AiValidationResult r = new AiValidationResult();
        r.setValid(true);
        r.setScore(95);
        r.setAnomalies(List.of("anomalie1"));
        r.setRecommendation("Corriger le format");

        assertThat(r.isValid()).isTrue();
        assertThat(r.getScore()).isEqualTo(95);
        assertThat(r.getAnomalies()).hasSize(1);
        assertThat(r.getRecommendation()).isEqualTo("Corriger le format");
    }
    // AuditLogDTO
    @Test
    void auditLogDTO_gettersSetters() {
        AuditLogDTO dto = new AuditLogDTO();
        dto.setId(1L);
        dto.setDeclarationId(2L);
        dto.setDeclarationCode("DECL001");
        dto.setDeclarationNom("Déclaration Test");
        dto.setDeclarationPeriode("2025-01");
        dto.setDeclarationStatut("GENEREE");
        dto.setAction("SUBMIT");
        dto.setStatutAvant("GENEREE");
        dto.setStatutApres("EN_VALIDATION");
        dto.setEffectuePar("agent1");
        dto.setCommentaire("test");
        dto.setDateAction(LocalDateTime.now());

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getDeclarationId()).isEqualTo(2L);
        assertThat(dto.getDeclarationCode()).isEqualTo("DECL001");
        assertThat(dto.getDeclarationNom()).isEqualTo("Déclaration Test");
        assertThat(dto.getDeclarationPeriode()).isEqualTo("2025-01");
        assertThat(dto.getDeclarationStatut()).isEqualTo("GENEREE");
        assertThat(dto.getAction()).isEqualTo("SUBMIT");
        assertThat(dto.getStatutAvant()).isEqualTo("GENEREE");
        assertThat(dto.getStatutApres()).isEqualTo("EN_VALIDATION");
        assertThat(dto.getEffectuePar()).isEqualTo("agent1");
        assertThat(dto.getCommentaire()).isEqualTo("test");
        assertThat(dto.getDateAction()).isNotNull();
    }
    // AuditStatsDTO
    @Test
    void auditStatsDTO_gettersSetters() {
        AuditStatsDTO dto = new AuditStatsDTO();
        dto.setTotalDeclarations(10L);
        dto.setTotalLogs(25L);
        dto.setGenerees(3L);
        dto.setEnValidation(2L);
        dto.setValidees(2L);
        dto.setRejetees(1L);
        dto.setEnvoyees(2L);
        dto.setTotalSoumissions(5L);
        dto.setTotalValidations(4L);
        dto.setTotalRejets(1L);
        dto.setTotalEnvois(2L);
        dto.setTauxValidation(80.0);
        dto.setTauxRejet(10.0);
        dto.setActionCounts(Map.of("SUBMIT", 5L));

        assertThat(dto.getTotalDeclarations()).isEqualTo(10L);
        assertThat(dto.getTotalLogs()).isEqualTo(25L);
        assertThat(dto.getGenerees()).isEqualTo(3L);
        assertThat(dto.getEnValidation()).isEqualTo(2L);
        assertThat(dto.getValidees()).isEqualTo(2L);
        assertThat(dto.getRejetees()).isEqualTo(1L);
        assertThat(dto.getEnvoyees()).isEqualTo(2L);
        assertThat(dto.getTauxValidation()).isEqualTo(80.0);
        assertThat(dto.getTauxRejet()).isEqualTo(10.0);
        assertThat(dto.getActionCounts()).containsKey("SUBMIT");

        // UserActionCount inner class
        AuditStatsDTO.UserActionCount uac = new AuditStatsDTO.UserActionCount("agent1", 5L);
        uac.setUsername("agent1");
        uac.setCount(5L);
        assertThat(uac.getUsername()).isEqualTo("agent1");
        assertThat(uac.getCount()).isEqualTo(5L);
        dto.setTopAgents(List.of(uac));
        dto.setTopManagers(List.of(uac));
        assertThat(dto.getTopAgents()).hasSize(1);
        assertThat(dto.getTopManagers()).hasSize(1);
    }
    // GenerateDeclarationRequest
    @Test
    void generateDeclarationRequest_gettersSetters() {
        GenerateDeclarationRequest req = new GenerateDeclarationRequest();
        req.setDeclarationTypeId(1L);
        req.setPeriode("2025-01");
        req.setDateDebut(LocalDate.of(2025, 1, 1));
        req.setDateFin(LocalDate.of(2025, 1, 31));

        assertThat(req.getDeclarationTypeId()).isEqualTo(1L);
        assertThat(req.getPeriode()).isEqualTo("2025-01");
        assertThat(req.getDateDebut()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(req.getDateFin()).isEqualTo(LocalDate.of(2025, 1, 31));

        // Constructor
        GenerateDeclarationRequest req2 = new GenerateDeclarationRequest(
            2L, "2025-02", LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28));
        assertThat(req2.getDeclarationTypeId()).isEqualTo(2L);
    }
    // RejectRequest
    @Test
    void rejectRequest_gettersSetters() {
        RejectRequest req = new RejectRequest();
        req.setCommentaire("Format incorrect");
        assertThat(req.getCommentaire()).isEqualTo("Format incorrect");
    }
    // ValidationStatsDTO
    @Test
    void validationStatsDTO_gettersSetters() {
        ValidationStatsDTO dto = new ValidationStatsDTO();
        dto.setTotal(10L);
        dto.setEnValidation(3L);
        dto.setValidees(4L);
        dto.setRejetees(2L);
        dto.setEnvoyees(1L);
        dto.setGenerees(0L);

        assertThat(dto.getTotal()).isEqualTo(10L);
        assertThat(dto.getEnValidation()).isEqualTo(3L);
        assertThat(dto.getValidees()).isEqualTo(4L);
        assertThat(dto.getRejetees()).isEqualTo(2L);
        assertThat(dto.getEnvoyees()).isEqualTo(1L);
    }
    // DeclarationDTO
    @Test
    void declarationDTO_gettersSetters() {
        DeclarationDTO dto = new DeclarationDTO();
        dto.setId(1L);
        dto.setStatut("GENEREE");
        dto.setPeriode("2025-01");
        dto.setDateDebut(LocalDate.of(2025, 1, 1));
        dto.setDateFin(LocalDate.of(2025, 1, 31));
        dto.setNomFichier("decl.xml");
        dto.setContenuFichier("<xml/>");
        dto.setGenerePar("agent1");
        dto.setValidePar("manager1");
        dto.setCommentaireRejet("Erreur");
        dto.setDateGeneration(LocalDateTime.now());
        dto.setDateValidation(LocalDateTime.now());
        dto.setDateEnvoi(LocalDateTime.now());

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getStatut()).isEqualTo("GENEREE");
        assertThat(dto.getPeriode()).isEqualTo("2025-01");
        assertThat(dto.getNomFichier()).isEqualTo("decl.xml");
        assertThat(dto.getContenuFichier()).isEqualTo("<xml/>");
        assertThat(dto.getGenerePar()).isEqualTo("agent1");
        assertThat(dto.getValidePar()).isEqualTo("manager1");
        assertThat(dto.getCommentaireRejet()).isEqualTo("Erreur");
        assertThat(dto.getDateGeneration()).isNotNull();
        assertThat(dto.getDateValidation()).isNotNull();
        assertThat(dto.getDateEnvoi()).isNotNull();

        // Inner class DeclarationTypeDTO
        DeclarationDTO.DeclarationTypeDTO typeDto = new DeclarationDTO.DeclarationTypeDTO();
        typeDto.setId(1L);
        typeDto.setCode("DECL001");
        typeDto.setNom("Test");
        typeDto.setFormat("XML");
        typeDto.setFrequence("MENSUELLE");
        dto.setDeclarationType(typeDto);

        assertThat(dto.getDeclarationType().getCode()).isEqualTo("DECL001");
        assertThat(dto.getDeclarationTypeName()).isEqualTo("DECL001"); // retourne le code
        assertThat(dto.getFileFormat()).isEqualTo("XML");
        assertThat(dto.getFrequence()).isEqualTo("MENSUELLE");
    }
    // Jira DTOs
    @Test
    void createJiraTicketRequest_gettersSetters() {
        CreateJiraTicketRequest req = new CreateJiraTicketRequest();
        req.setDeclarationId(1L);
        req.setSubmittedBy("agent1");

        assertThat(req.getDeclarationId()).isEqualTo(1L);
        assertThat(req.getSubmittedBy()).isEqualTo("agent1");
    }

    @Test
    void createTicketRequest_gettersSetters() {
        com.wifak.validationservice.dto.CreateTicketRequest req =
            new com.wifak.validationservice.dto.CreateTicketRequest(1L, "agent1");

        assertThat(req.getDeclarationId()).isEqualTo(1L);
        assertThat(req.getSubmittedBy()).isEqualTo("agent1");
    }

    @Test
    void transitionJiraTicketRequest_gettersSetters() {
        TransitionJiraTicketRequest req = new TransitionJiraTicketRequest(1L, "VALIDEE", null, "manager1");

        assertThat(req.getDeclarationId()).isEqualTo(1L);
        assertThat(req.getNewBctStatut()).isEqualTo("VALIDEE");
        assertThat(req.getCommentaire()).isNull();
        assertThat(req.getEffectuePar()).isEqualTo("manager1");

        req.setDeclarationId(2L);
        req.setNewBctStatut("REJETEE");
        req.setCommentaire("Erreur");
        req.setEffectuePar("manager2");
        assertThat(req.getNewBctStatut()).isEqualTo("REJETEE");
        assertThat(req.getCommentaire()).isEqualTo("Erreur");
    }

    @Test
    void jiraTicketResponseDTO_gettersSetters() {
        JiraTicketResponseDTO dto = new JiraTicketResponseDTO();
        dto.setDeclarationId(1L);
        dto.setJiraTicketKey("BCT-123");
        dto.setJiraTicketUrl("https://jira.example.com/BCT-123");
        dto.setJiraStatus("OPEN");
        dto.setMessage("Created");

        assertThat(dto.getDeclarationId()).isEqualTo(1L);
        assertThat(dto.getJiraTicketKey()).isEqualTo("BCT-123");
        assertThat(dto.getJiraTicketUrl()).isEqualTo("https://jira.example.com/BCT-123");
        assertThat(dto.getJiraStatus()).isEqualTo("OPEN");
        assertThat(dto.getMessage()).isEqualTo("Created");
    }
    // ValidationLog entity
    @Test
    void validationLog_gettersSetters() {
        ValidationLog log = new ValidationLog();
        log.setDeclarationId(2L);
        log.setAction("SUBMIT");
        log.setStatutAvant("GENEREE");
        log.setStatutApres("EN_VALIDATION");
        log.setEffectuePar("agent1");
        log.setCommentaire("test");
        log.setDateAction(LocalDateTime.now());

        assertThat(log.getDeclarationId()).isEqualTo(2L);
        assertThat(log.getAction()).isEqualTo("SUBMIT");
        assertThat(log.getStatutAvant()).isEqualTo("GENEREE");
        assertThat(log.getStatutApres()).isEqualTo("EN_VALIDATION");
        assertThat(log.getEffectuePar()).isEqualTo("agent1");
        assertThat(log.getCommentaire()).isEqualTo("test");
        assertThat(log.getDateAction()).isNotNull();
    }
    // ValidationRule entity
    @Test
    void validationRule_gettersSetters() {
        ValidationRule rule = new ValidationRule();
        rule.setId(1L);
        rule.setChampConcerne("montant");
        rule.setMessageErreur("Montant invalide");
        rule.setObligatoire(true);

        assertThat(rule.getId()).isEqualTo(1L);
        assertThat(rule.getChampConcerne()).isEqualTo("montant");
        assertThat(rule.getMessageErreur()).isEqualTo("Montant invalide");
        assertThat(rule.isObligatoire()).isTrue();
    }
    // XsdSqlMappingRequest
    @Test
    void xsdSqlMappingRequest_gettersSetters() {
        XsdSqlMappingRequest req = new XsdSqlMappingRequest();
        XsdSqlMappingRequest.FieldMapping fm = new XsdSqlMappingRequest.FieldMapping();
        fm.setXsdFieldName("field1");
        fm.setXsdFieldPath("/root/field1");
        fm.setXsdType("string");
        fm.setSqlColumn("col1");
        fm.setRequired(true);
        fm.setSource(XsdSqlMappingRequest.MappingSource.SQL);
        fm.setStaticValue("val");

        req.setMappings(List.of(fm));

        assertThat(req.getMappings()).hasSize(1);
        assertThat(fm.getXsdFieldName()).isEqualTo("field1");
        assertThat(fm.getXsdFieldPath()).isEqualTo("/root/field1");
        assertThat(fm.getXsdType()).isEqualTo("string");
        assertThat(fm.getSqlColumn()).isEqualTo("col1");
        assertThat(fm.isRequired()).isTrue();
        assertThat(fm.getSource()).isEqualTo(XsdSqlMappingRequest.MappingSource.SQL);
        assertThat(fm.getStaticValue()).isEqualTo("val");
        assertThat(fm.toString()).isNotNull();

        // MappingSource enum
        for (XsdSqlMappingRequest.MappingSource s : XsdSqlMappingRequest.MappingSource.values()) {
            assertThat(s.toJson()).isNotNull();
        }
    }
    // Declaration entity
    @Test
    void declaration_gettersSetters() {
        Declaration d = new Declaration();
        d.setId(1L);
        d.setPeriode("2025-01");
        d.setStatut(Declaration.DeclarationStatut.GENEREE);
        d.setGenerePar("agent1");
        d.setContenuFichier("<xml/>");
        d.setNomFichier("decl.xml");
        d.setDateGeneration(LocalDateTime.now());
        d.setDateDebut(LocalDate.of(2025, 1, 1));
        d.setDateFin(LocalDate.of(2025, 1, 31));
        d.setCommentaireRejet("test");
        d.setValidePar("manager1");
        d.setDateValidation(LocalDateTime.now());
        d.setDateEnvoi(LocalDateTime.now());
        d.setSqlQueryUsed("SELECT *");
        d.setXsdFileNameUsed("schema.xsd");
        d.setMappingJson("{}");

        assertThat(d.getId()).isEqualTo(1L);
        assertThat(d.getPeriode()).isEqualTo("2025-01");
        assertThat(d.getStatut()).isEqualTo(Declaration.DeclarationStatut.GENEREE);
        assertThat(d.getGenerePar()).isEqualTo("agent1");
        assertThat(d.getContenuFichier()).isEqualTo("<xml/>");
        assertThat(d.getNomFichier()).isEqualTo("decl.xml");
        assertThat(d.getDateGeneration()).isNotNull();
        assertThat(d.getCommentaireRejet()).isEqualTo("test");
        assertThat(d.getValidePar()).isEqualTo("manager1");
        assertThat(d.getDateValidation()).isNotNull();
        assertThat(d.getDateEnvoi()).isNotNull();
        assertThat(d.getSqlQueryUsed()).isEqualTo("SELECT *");
        assertThat(d.getXsdFileNameUsed()).isEqualTo("schema.xsd");
        assertThat(d.getMappingJson()).isEqualTo("{}");
    }

    @Test
    void declaration_tousLesStatuts() {
        for (Declaration.DeclarationStatut s : Declaration.DeclarationStatut.values()) {
            assertThat(s.name()).isNotNull();
        }
    }

    @Test
    @DisplayName("Declaration — dateDebut et dateFin avec @JsonFormat yyyy-MM-dd")
    void declaration_dateDebutDateFin_jsonFormat() {
        Declaration d = new Declaration();
        d.setDateDebut(LocalDate.of(2026, 5, 1));
        d.setDateFin(LocalDate.of(2026, 5, 31));

        // Vérifier que les getters retournent les bonnes valeurs
        assertThat(d.getDateDebut()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(d.getDateFin()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(d.getDateDebut().getYear()).isEqualTo(2026);
        assertThat(d.getDateFin().getMonthValue()).isEqualTo(5);
        assertThat(d.getDateFin().getDayOfMonth()).isEqualTo(31);
    }
    // DeclarationDTO — branches des méthodes helper
    @Test
    void declarationDTO_helperMethods_nullDeclarationType() {
        DeclarationDTO dto = new DeclarationDTO();
        dto.setNomFichier("decl.xml");

        // declarationType null → retourne "UNKNOWN"
        assertThat(dto.getDeclarationTypeName()).isEqualTo("UNKNOWN");
        // declarationType null → fallback sur extension du fichier
        assertThat(dto.getFileFormat()).isEqualTo("XML");
        // declarationType null → retourne "UNKNOWN"
        assertThat(dto.getFrequence()).isEqualTo("UNKNOWN");
    }

    @Test
    void declarationDTO_helperMethods_typeWithNullCode() {
        DeclarationDTO dto = new DeclarationDTO();
        DeclarationDTO.DeclarationTypeDTO typeDto = new DeclarationDTO.DeclarationTypeDTO();
        typeDto.setCode(null);
        typeDto.setNom("Déclaration mensuelle");
        typeDto.setFormat(null);
        typeDto.setFrequence(null);
        dto.setDeclarationType(typeDto);
        dto.setNomFichier("decl.csv");

        // code null → retourne le nom
        assertThat(dto.getDeclarationTypeName()).isEqualTo("Déclaration mensuelle");
        // format null → fallback sur extension
        assertThat(dto.getFileFormat()).isEqualTo("CSV");
        // frequence null → retourne "UNKNOWN"
        assertThat(dto.getFrequence()).isEqualTo("UNKNOWN");
    }

    @Test
    void declarationDTO_helperMethods_nomFichierSansExtension() {
        DeclarationDTO dto = new DeclarationDTO();
        dto.setNomFichier("decl");

        // nomFichier sans extension → retourne "UNKNOWN"
        assertThat(dto.getFileFormat()).isEqualTo("UNKNOWN");
    }

    @Test
    void declarationDTO_helperMethods_nomFichierNull() {
        DeclarationDTO dto = new DeclarationDTO();
        dto.setNomFichier(null);

        assertThat(dto.getFileFormat()).isEqualTo("UNKNOWN");
    }
    // XsdSqlMappingRequest — branches null
    @Test
    void xsdSqlMappingRequest_nullValues() {
        XsdSqlMappingRequest.FieldMapping fm = new XsdSqlMappingRequest.FieldMapping();
        // source null → retourne NONE
        fm.setSource(null);
        assertThat(fm.getSource()).isEqualTo(XsdSqlMappingRequest.MappingSource.NONE);
        // sqlColumn null → retourne ""
        fm.setSqlColumn(null);
        assertThat(fm.getSqlColumn()).isEqualTo("");
        // staticValue null → retourne ""
        fm.setStaticValue(null);
        assertThat(fm.getStaticValue()).isEqualTo("");
    }
    // DeclarationTemplate entity
    @Test
    void declarationTemplate_gettersSetters() {
        DeclarationTemplate tpl = new DeclarationTemplate();
        tpl.setTemplateContent("<xml>{{CODE}}</xml>");
        tpl.setVariablesDisponibles("CODE,DATE,MONTANT");

        DeclarationType type = new DeclarationType();
        type.setCode("DECL001");
        tpl.setDeclarationType(type);

        assertThat(tpl.getTemplateContent()).isEqualTo("<xml>{{CODE}}</xml>");
        assertThat(tpl.getVariablesDisponibles()).isEqualTo("CODE,DATE,MONTANT");
        assertThat(tpl.getDeclarationType().getCode()).isEqualTo("DECL001");
        assertThat(tpl.getId()).isNull();
    }
    // DeclarationType entity — méthodes et enums
    @Test
    void declarationType_gettersSetters() {
        DeclarationType type = new DeclarationType();
        type.setCode("DECL001");
        type.setNom("Déclaration mensuelle");
        type.setDescription("Description test");
        type.setFormat(DeclarationType.DeclarationFormat.XML);
        type.setFrequence(DeclarationType.DeclarationFrequence.MENSUELLE);
        type.setDateLimite("2025-01-31");
        type.setActif(true);
        type.setChampsObligatoires("montant,date");
        type.setXsdContent("<xs:schema/>");
        type.setXsdFileName("schema.xsd");
        type.setSqlQuery("SELECT * FROM test");
        type.setDateCreation(java.time.LocalDateTime.now());
        type.setDerniereModification(java.time.LocalDateTime.now());
        type.setCreePar("admin");
        type.setModifiePar("admin");

        assertThat(type.getCode()).isEqualTo("DECL001");
        assertThat(type.getNom()).isEqualTo("Déclaration mensuelle");
        assertThat(type.getDescription()).isEqualTo("Description test");
        assertThat(type.getFormat()).isEqualTo(DeclarationType.DeclarationFormat.XML);
        assertThat(type.getFrequence()).isEqualTo(DeclarationType.DeclarationFrequence.MENSUELLE);
        assertThat(type.getDateLimite()).isEqualTo("2025-01-31");
        assertThat(type.isActif()).isTrue();
        assertThat(type.getChampsObligatoires()).isEqualTo("montant,date");
        assertThat(type.getXsdContent()).isEqualTo("<xs:schema/>");
        assertThat(type.getXsdFileName()).isEqualTo("schema.xsd");
        assertThat(type.getSqlQuery()).isEqualTo("SELECT * FROM test");
        assertThat(type.getCreePar()).isEqualTo("admin");
        assertThat(type.getModifiePar()).isEqualTo("admin");
        assertThat(type.getDateCreation()).isNotNull();
        assertThat(type.getDerniereModification()).isNotNull();
        assertThat(type.getValidationRules()).isEmpty();
    }

    @Test
    void declarationType_addAndClearValidationRules() {
        DeclarationType type = new DeclarationType();
        ValidationRule rule = new ValidationRule();
        rule.setChampConcerne("montant");
        type.addValidationRule(rule);

        assertThat(type.getValidationRules()).hasSize(1);
        assertThat(rule.getDeclarationType()).isEqualTo(type);

        type.clearValidationRules();
        assertThat(type.getValidationRules()).isEmpty();
        assertThat(rule.getDeclarationType()).isNull();
    }

    @Test
    void declarationType_setTemplate_bidirectionnel() {
        DeclarationType type = new DeclarationType();
        DeclarationTemplate tpl = new DeclarationTemplate();
        tpl.setTemplateContent("<xml/>");
        type.setTemplate(tpl);

        assertThat(type.getTemplate()).isEqualTo(tpl);
        assertThat(tpl.getDeclarationType()).isEqualTo(type);

        type.setTemplate(null);
        assertThat(type.getTemplate()).isNull();
    }

    @Test
    void declarationType_tousLesFormats() {
        for (DeclarationType.DeclarationFormat f : DeclarationType.DeclarationFormat.values()) {
            assertThat(f.name()).isNotNull();
        }
    }

    @Test
    void declarationType_toutesLesFrequences() {
        for (DeclarationType.DeclarationFrequence f : DeclarationType.DeclarationFrequence.values()) {
            assertThat(f.name()).isNotNull();
        }
    }
}
