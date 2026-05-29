package com.wifak.validationservice.service;

import com.wifak.validationservice.entities.Declaration;
import com.wifak.validationservice.entities.DeclarationType;
import com.wifak.validationservice.feign.JiraIntegrationFeignClient;
import com.wifak.validationservice.repositories.DeclarationRepository;
import com.wifak.validationservice.repositories.DeclarationTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.springframework.test.context.ActiveProfiles;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ActiveProfiles("test")
@DisplayName("DeclarationService — Tests unitaires")
class DeclarationServiceTest {

    @Mock private DeclarationRepository declarationRepository;
    @Mock private DeclarationTypeRepository typeRepository;
    @Mock private XmlGenerationService xmlGenerationService;
    @Mock private CsvGenerationService csvGenerationService;
    @Mock private TxtGenerationService txtGenerationService;
    @Mock private JiraIntegrationFeignClient jiraClient;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private DeclarationService declarationService;

    private DeclarationType activeType;
    private Declaration genereeDeclaration;

    @BeforeEach
    void setUp() {
        activeType = new DeclarationType();
        activeType.setCode("DECL001");
        activeType.setNom("Déclaration Test");
        activeType.setActif(true);
        activeType.setSqlQuery("SELECT * FROM test");
        activeType.setFormat(DeclarationType.DeclarationFormat.XML);

        genereeDeclaration = new Declaration();
        genereeDeclaration.setId(1L);
        genereeDeclaration.setDeclarationType(activeType);
        genereeDeclaration.setStatut(Declaration.DeclarationStatut.GENEREE);
        genereeDeclaration.setPeriode("2025-01");
        genereeDeclaration.setGenerePar("agent1");

        mockSecurityContext("agent1");
    }

    private void mockSecurityContext(String username) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(username);
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }
    // validateType
    @Test
    @DisplayName("validateType — type inactif → RuntimeException")
    void validateType_inactif_throwsException() {
        activeType.setActif(false);
        when(typeRepository.findById(1L)).thenReturn(Optional.of(activeType));

        assertThatThrownBy(() ->
            declarationService.generateAndSave(1L, "2025-01",
                LocalDate.of(2025,1,1), LocalDate.of(2025,1,31))
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("inactif");
    }

    @Test
    @DisplayName("validateType — SQL null → RuntimeException")
    void validateType_sqlNull_throwsException() {
        activeType.setSqlQuery(null);
        when(typeRepository.findById(1L)).thenReturn(Optional.of(activeType));

        assertThatThrownBy(() ->
            declarationService.generateAndSave(1L, "2025-01",
                LocalDate.of(2025,1,1), LocalDate.of(2025,1,31))
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("SQL");
    }

    @Test
    @DisplayName("validateType — SQL vide → RuntimeException")
    void validateType_sqlVide_throwsException() {
        activeType.setSqlQuery("   ");
        when(typeRepository.findById(1L)).thenReturn(Optional.of(activeType));

        assertThatThrownBy(() ->
            declarationService.generateAndSave(1L, "2025-01",
                LocalDate.of(2025,1,1), LocalDate.of(2025,1,31))
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("SQL");
    }
    // deleteDeclaration
    @Test
    @DisplayName("deleteDeclaration — EN_VALIDATION → RuntimeException")
    void deleteDeclaration_enValidation_throwsException() {
        genereeDeclaration.setStatut(Declaration.DeclarationStatut.EN_VALIDATION);
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));

        assertThatThrownBy(() -> declarationService.deleteDeclaration(1L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("EN_VALIDATION");
    }

    @Test
    @DisplayName("deleteDeclaration — VALIDEE → RuntimeException")
    void deleteDeclaration_validee_throwsException() {
        genereeDeclaration.setStatut(Declaration.DeclarationStatut.VALIDEE);
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));

        assertThatThrownBy(() -> declarationService.deleteDeclaration(1L))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("deleteDeclaration — GENEREE → suppression OK")
    void deleteDeclaration_generee_ok() {
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));
        doNothing().when(declarationRepository).delete(genereeDeclaration);

        assertThatCode(() -> declarationService.deleteDeclaration(1L))
            .doesNotThrowAnyException();
        verify(declarationRepository).delete(genereeDeclaration);
    }
    // patchContent
    @Test
    @DisplayName("patchContent — EN_VALIDATION → RuntimeException")
    void patchContent_enValidation_throwsException() {
        genereeDeclaration.setStatut(Declaration.DeclarationStatut.EN_VALIDATION);
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));

        assertThatThrownBy(() -> declarationService.patchContent(1L, "<xml/>"))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("patchContent — GENEREE → mise à jour OK")
    void patchContent_generee_ok() {
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));
        when(declarationRepository.save(any())).thenReturn(genereeDeclaration);

        Declaration result = declarationService.patchContent(1L, "<xml>new</xml>");

        assertThat(result).isNotNull();
        verify(declarationRepository).save(any());
    }
    // updateStatut
    @Test
    @DisplayName("updateStatut — REJETEE sans commentaire → RuntimeException")
    void updateStatut_rejetee_sansCommentaire_throwsException() {
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));

        assertThatThrownBy(() ->
            declarationService.updateStatut(1L, "REJETEE", null, "manager1")
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("commentaire");
    }

    @Test
    @DisplayName("updateStatut — REJETEE avec commentaire → OK")
    void updateStatut_rejetee_avecCommentaire_ok() {
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));
        when(declarationRepository.save(any())).thenReturn(genereeDeclaration);

        Declaration result = declarationService.updateStatut(1L, "REJETEE", "Erreur format", "manager1");

        assertThat(result).isNotNull();
        verify(declarationRepository).save(any());
    }

    @Test
    @DisplayName("updateStatut — statut invalide → RuntimeException")
    void updateStatut_statutInvalide_throwsException() {
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));

        assertThatThrownBy(() ->
            declarationService.updateStatut(1L, "STATUT_INEXISTANT", null, null)
        ).isInstanceOf(RuntimeException.class);
    }
    // getStats
    @Test
    @DisplayName("getStats — retourne les compteurs corrects")
    void getStats_retourneCompteurs() {
        when(declarationRepository.count()).thenReturn(10L);
        when(declarationRepository.countByStatut(Declaration.DeclarationStatut.GENEREE)).thenReturn(3L);
        when(declarationRepository.countByStatut(Declaration.DeclarationStatut.EN_VALIDATION)).thenReturn(2L);
        when(declarationRepository.countByStatut(Declaration.DeclarationStatut.VALIDEE)).thenReturn(2L);
        when(declarationRepository.countByStatut(Declaration.DeclarationStatut.REJETEE)).thenReturn(1L);
        when(declarationRepository.countByStatut(Declaration.DeclarationStatut.ENVOYEE)).thenReturn(2L);

        DeclarationService.DeclarationStats stats = declarationService.getStats();

        assertThat(stats.getTotal()).isEqualTo(10L);
        assertThat(stats.getGenerees()).isEqualTo(3L);
        assertThat(stats.getEnValidation()).isEqualTo(2L);
        assertThat(stats.getValidees()).isEqualTo(2L);
        assertThat(stats.getRejetees()).isEqualTo(1L);
        assertThat(stats.getEnvoyees()).isEqualTo(2L);
    }
    // findById
    @Test
    @DisplayName("findById — ID inexistant → RuntimeException")
    void findById_inexistant_throwsException() {
        when(declarationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> declarationService.findById(99L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("99");
    }

    @Test
    @DisplayName("findById — ID existant → retourne la déclaration")
    void findById_existant_retourneDeclaration() {
        when(declarationRepository.findById(1L)).thenReturn(Optional.of(genereeDeclaration));

        Declaration result = declarationService.findById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }
}
