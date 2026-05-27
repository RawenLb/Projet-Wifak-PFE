package com.wifak.validationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ActiveProfiles("test")
@DisplayName("FileStorageService — Tests unitaires")
class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        service = new FileStorageService();
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        service.init();
    }

    // ══════════════════════════════════════════════════════════════
    // storeFile
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("storeFile — fichier valide → retourne le nom unique")
    void storeFile_fichierValide_retourneNom() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.xml", "application/xml", "<xml/>".getBytes()
        );

        String result = service.storeFile(file, "decl");

        assertThat(result).startsWith("decl_").endsWith(".xml");
    }

    @Test
    @DisplayName("storeFile — fichier sans extension → retourne nom sans extension")
    void storeFile_sansExtension_retourneNomSansExtension() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "testfile", "text/plain", "content".getBytes()
        );

        String result = service.storeFile(file, "prefix");

        assertThat(result).startsWith("prefix_");
        assertThat(result).doesNotContain(".");
    }

    @Test
    @DisplayName("storeFile — fichier null → RuntimeException")
    void storeFile_fichierNull_throwsException() {
        assertThatThrownBy(() -> service.storeFile(null, "prefix"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("storeFile — nom avec .. → RuntimeException (path traversal)")
    void storeFile_pathTraversal_throwsException() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "../evil.xml", "application/xml", "<xml/>".getBytes()
        );

        assertThatThrownBy(() -> service.storeFile(file, "prefix"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Invalid filename");
    }

    // ══════════════════════════════════════════════════════════════
    // loadFileAsResource
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("loadFileAsResource — fichier existant → retourne la ressource")
    void loadFileAsResource_fichierExistant_retourneRessource() throws IOException {
        Path testFile = tempDir.resolve("test.xml");
        Files.write(testFile, "<xml/>".getBytes());

        var resource = service.loadFileAsResource("test.xml");

        assertThat(resource).isNotNull();
        assertThat(resource.exists()).isTrue();
    }

    @Test
    @DisplayName("loadFileAsResource — fichier inexistant → RuntimeException")
    void loadFileAsResource_fichierInexistant_throwsException() {
        assertThatThrownBy(() -> service.loadFileAsResource("nonexistent.xml"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("File not found");
    }

    // ══════════════════════════════════════════════════════════════
    // deleteFile
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("deleteFile — fichier existant → supprimé")
    void deleteFile_fichierExistant_supprime() throws IOException {
        Path testFile = tempDir.resolve("todelete.xml");
        Files.write(testFile, "<xml/>".getBytes());

        service.deleteFile("todelete.xml");

        assertThat(Files.exists(testFile)).isFalse();
    }

    @Test
    @DisplayName("deleteFile — fichier inexistant → pas d'exception")
    void deleteFile_fichierInexistant_pasException() {
        assertThatCode(() -> service.deleteFile("nonexistent.xml"))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("storeFile — fichier vide → RuntimeException")
    void storeFile_fichierVide_throwsException() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "empty.xml", "application/xml", new byte[0]
        );

        assertThatThrownBy(() -> service.storeFile(file, "prefix"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("storeFile — nom de fichier null → RuntimeException")
    void storeFile_nomNull_throwsException() {
        // MockMultipartFile avec originalFilename null → getOriginalFilename() retourne ""
        // On teste avec un nom contenant ".." pour déclencher l'exception
        MockMultipartFile file = new MockMultipartFile(
            "file", "../../evil.xml", "application/xml", "<xml/>".getBytes()
        );

        assertThatThrownBy(() -> service.storeFile(file, "prefix"))
            .isInstanceOf(RuntimeException.class);
    }
}
