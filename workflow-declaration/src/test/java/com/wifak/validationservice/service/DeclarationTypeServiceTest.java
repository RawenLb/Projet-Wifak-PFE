package com.wifak.validationservice.service;

import com.wifak.validationservice.entities.DeclarationType;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ActiveProfiles("test")
@DisplayName("DeclarationTypeService — Tests unitaires")
class DeclarationTypeServiceTest {

    @Mock private DeclarationTypeRepository repository;
    @InjectMocks private DeclarationTypeService service;

    private DeclarationType type;

    @BeforeEach
    void setUp() {
        type = new DeclarationType();
        type.setCode("DECL001");
        type.setNom("Déclaration Test");
        type.setActif(true);
        type.setFormat(DeclarationType.DeclarationFormat.XML);
        type.setFrequence(DeclarationType.DeclarationFrequence.MENSUELLE);
        type.setDateCreation(LocalDateTime.now());
        type.setDerniereModification(LocalDateTime.now());
        type.setCreePar("admin");

        mockSecurityContext("admin");
    }

    private void mockSecurityContext(String username) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(username);
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }
    // create
    @Test
    @DisplayName("create — code unique → sauvegarde OK")
    void create_codeUnique_ok() {
        when(repository.findByCode("DECL001")).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(type);

        DeclarationType result = service.create(type);

        assertThat(result).isNotNull();
        verify(repository).save(type);
    }

    @Test
    @DisplayName("create — code déjà existant → RuntimeException")
    void create_codeExistant_throwsException() {
        when(repository.findByCode("DECL001")).thenReturn(Optional.of(type));

        assertThatThrownBy(() -> service.create(type))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("existe déjà");
    }
    // getAll
    @Test
    @DisplayName("getAll — retourne tous les types")
    void getAll_retourneTous() {
        when(repository.findAll()).thenReturn(List.of(type));

        var result = service.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("DECL001");
    }
    // getById
    @Test
    @DisplayName("getById — ID existant → retourne le type")
    void getById_existant_ok() {
        when(repository.findById(1L)).thenReturn(Optional.of(type));

        var result = service.getById(1L);

        assertThat(result.getCode()).isEqualTo("DECL001");
    }

    @Test
    @DisplayName("getById — ID inexistant → RuntimeException")
    void getById_inexistant_throwsException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("99");
    }
    // delete
    @Test
    @DisplayName("delete — ID existant → suppression OK")
    void delete_existant_ok() {
        when(repository.existsById(1L)).thenReturn(true);
        doNothing().when(repository).deleteById(1L);

        assertThatCode(() -> service.delete(1L)).doesNotThrowAnyException();
        verify(repository).deleteById(1L);
    }

    @Test
    @DisplayName("delete — ID inexistant → RuntimeException")
    void delete_inexistant_throwsException() {
        when(repository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(99L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("introuvable");
    }
    // toggleStatus
    @Test
    @DisplayName("toggleStatus — actif → inactif")
    void toggleStatus_actifVersInactif() {
        type.setActif(true);
        when(repository.findById(1L)).thenReturn(Optional.of(type));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeclarationType result = service.toggleStatus(1L);

        assertThat(result.isActif()).isFalse();
    }

    @Test
    @DisplayName("toggleStatus — inactif → actif")
    void toggleStatus_inactifVersActif() {
        type.setActif(false);
        when(repository.findById(1L)).thenReturn(Optional.of(type));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeclarationType result = service.toggleStatus(1L);

        assertThat(result.isActif()).isTrue();
    }
    // saveXsd
    @Test
    @DisplayName("saveXsd — sauvegarde le contenu XSD")
    void saveXsd_ok() {
        when(repository.findById(1L)).thenReturn(Optional.of(type));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeclarationType result = service.saveXsd(1L, "schema.xsd", "<xs:schema/>");

        assertThat(result.getXsdFileName()).isEqualTo("schema.xsd");
        assertThat(result.getXsdContent()).isEqualTo("<xs:schema/>");
    }
    // saveSqlQuery
    @Test
    @DisplayName("saveSqlQuery — sauvegarde la requête SQL")
    void saveSqlQuery_ok() {
        when(repository.findById(1L)).thenReturn(Optional.of(type));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeclarationType result = service.saveSqlQuery(1L, "SELECT * FROM test");

        assertThat(result.getSqlQuery()).isEqualTo("SELECT * FROM test");
    }
    // update
    @Test
    @DisplayName("update — met à jour tous les champs")
    void update_ok() {
        when(repository.findById(1L)).thenReturn(Optional.of(type));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeclarationType updated = new DeclarationType();
        updated.setCode("DECL002");
        updated.setNom("Nouveau nom");
        updated.setFormat(DeclarationType.DeclarationFormat.CSV);
        updated.setFrequence(DeclarationType.DeclarationFrequence.ANNUELLE);
        updated.setActif(false);

        DeclarationType result = service.update(1L, updated);

        assertThat(result.getCode()).isEqualTo("DECL002");
        assertThat(result.getNom()).isEqualTo("Nouveau nom");
        assertThat(result.isActif()).isFalse();
        verify(repository).save(any());
    }

    @Test
    @DisplayName("update — ID inexistant → RuntimeException")
    void update_inexistant_throwsException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, type))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("introuvable");
    }

    @Test
    @DisplayName("update — champs null corrigés automatiquement")
    void update_corrigeNullAuditFields() {
        type.setDateCreation(null);
        type.setDerniereModification(null);
        type.setCreePar(null);
        when(repository.findById(1L)).thenReturn(Optional.of(type));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeclarationType result = service.update(1L, type);

        assertThat(result.getDateCreation()).isNotNull();
        assertThat(result.getDerniereModification()).isNotNull();
        assertThat(result.getCreePar()).isNotNull();
    }
    // fixNullAuditFields — testé via toggleStatus avec champs null
    @Test
    @DisplayName("toggleStatus — corrige les champs null automatiquement")
    void toggleStatus_corrigeNullAuditFields() {
        type.setDateCreation(null);
        type.setDerniereModification(null);
        type.setCreePar(null);

        when(repository.findById(1L)).thenReturn(Optional.of(type));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeclarationType result = service.toggleStatus(1L);

        assertThat(result.getDateCreation()).isNotNull();
        assertThat(result.getDerniereModification()).isNotNull();
        assertThat(result.getCreePar()).isNotNull();
    }
}
