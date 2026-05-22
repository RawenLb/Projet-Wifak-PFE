package com.wifak.validationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ActiveProfiles("test")
@DisplayName("XsdAnalyzerService — Tests unitaires")
class XsdAnalyzerServiceTest {

    private XsdAnalyzerService service;

    private static final String SIMPLE_XSD = """
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
          <xs:element name="Declaration">
            <xs:complexType>
              <xs:sequence>
                <xs:element name="CodeDeclaration" type="xs:string" minOccurs="1"/>
                <xs:element name="Periode" type="xs:string" minOccurs="1"/>
                <xs:element name="Montant" type="xs:decimal" minOccurs="0"/>
                <xs:element name="NomClient" type="xs:string" minOccurs="1"/>
              </xs:sequence>
            </xs:complexType>
          </xs:element>
        </xs:schema>
        """;

    @BeforeEach
    void setUp() {
        service = new XsdAnalyzerService();
    }

    // ══════════════════════════════════════════════════════════════
    // analyzeCompatibility
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("analyzeCompatibility — XSD valide avec colonnes SQL → résultat non null")
    void analyzeCompatibility_xsdValide_retourneResultat() {
        List<String> sqlColumns = List.of("montant", "nom_client", "date_operation");

        var result = service.analyzeCompatibility(SIMPLE_XSD, sqlColumns);

        assertThat(result).isNotNull();
        assertThat(result.getXsdFields()).isNotEmpty();
        assertThat(result.getSqlColumns()).hasSize(3);
    }

    @Test
    @DisplayName("analyzeCompatibility — XSD null → liste vide")
    void analyzeCompatibility_xsdNull_listeVide() {
        var result = service.analyzeCompatibility(null, List.of("col1"));

        assertThat(result.getXsdFields()).isEmpty();
    }

    @Test
    @DisplayName("analyzeCompatibility — XSD vide → liste vide")
    void analyzeCompatibility_xsdVide_listeVide() {
        var result = service.analyzeCompatibility("", List.of("col1"));

        assertThat(result.getXsdFields()).isEmpty();
    }

    @Test
    @DisplayName("analyzeCompatibility — champs auto-header filtrés")
    void analyzeCompatibility_champsAutoHeaderFiltres() {
        List<String> sqlColumns = List.of("montant");

        var result = service.analyzeCompatibility(SIMPLE_XSD, sqlColumns);

        // CodeDeclaration et Periode sont des champs auto-header → filtrés
        boolean hasCodeDeclaration = result.getXsdFields().stream()
            .anyMatch(f -> f.getName().equalsIgnoreCase("CodeDeclaration"));
        boolean hasPeriode = result.getXsdFields().stream()
            .anyMatch(f -> f.getName().equalsIgnoreCase("Periode"));

        assertThat(hasCodeDeclaration).isFalse();
        assertThat(hasPeriode).isFalse();
    }

    @Test
    @DisplayName("analyzeCompatibility — auto-mapping exact → mappé correctement")
    void analyzeCompatibility_autoMappingExact() {
        List<String> sqlColumns = List.of("Montant", "NomClient");

        var result = service.analyzeCompatibility(SIMPLE_XSD, sqlColumns);

        assertThat(result.getAutoMapped()).containsKey("Montant");
        assertThat(result.getAutoMapped()).containsKey("NomClient");
    }

    @Test
    @DisplayName("analyzeCompatibility — score 100% si tous les champs obligatoires mappés")
    void analyzeCompatibility_score100_tousObligatoiresMappés() {
        // NomClient est obligatoire (minOccurs=1), Montant est optionnel
        List<String> sqlColumns = List.of("NomClient", "Montant");

        var result = service.analyzeCompatibility(SIMPLE_XSD, sqlColumns);

        assertThat(result.getCompatibilityScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("analyzeCompatibility — score 0% si aucun champ obligatoire mappé")
    void analyzeCompatibility_score0_aucunObligatoireMappe() {
        List<String> sqlColumns = List.of("colonne_inconnue");

        var result = service.analyzeCompatibility(SIMPLE_XSD, sqlColumns);

        assertThat(result.getCompatibilityScore()).isEqualTo(0);
    }

    @Test
    @DisplayName("analyzeCompatibility — unmappedXsdFields contient les champs non mappés")
    void analyzeCompatibility_unmappedXsdFields() {
        List<String> sqlColumns = List.of("Montant"); // NomClient non fourni

        var result = service.analyzeCompatibility(SIMPLE_XSD, sqlColumns);

        assertThat(result.getUnmappedXsdFields()).contains("NomClient");
    }

    @Test
    @DisplayName("analyzeCompatibility — unmappedSqlColumns contient les colonnes non utilisées")
    void analyzeCompatibility_unmappedSqlColumns() {
        List<String> sqlColumns = List.of("NomClient", "colonne_inutilisee");

        var result = service.analyzeCompatibility(SIMPLE_XSD, sqlColumns);

        assertThat(result.getUnmappedSqlColumns()).contains("colonne_inutilisee");
    }

    @Test
    @DisplayName("analyzeCompatibility — summary excellent si score >= 80")
    void analyzeCompatibility_summaryExcellent() {
        List<String> sqlColumns = List.of("NomClient", "Montant");

        var result = service.analyzeCompatibility(SIMPLE_XSD, sqlColumns);

        assertThat(result.getSummary()).containsIgnoringCase("Excellente");
    }

    @Test
    @DisplayName("analyzeCompatibility — XSD avec prologue XML → parsé correctement")
    void analyzeCompatibility_avecPrologueXml() {
        String xsdAvecPrologue = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + SIMPLE_XSD;
        List<String> sqlColumns = List.of("NomClient");

        var result = service.analyzeCompatibility(xsdAvecPrologue, sqlColumns);

        assertThat(result.getXsdFields()).isNotEmpty();
    }

    @Test
    @DisplayName("analyzeCompatibility — auto-mapping partiel (SQL ⊂ XSD)")
    void analyzeCompatibility_autoMappingPartiel() {
        // "nom" est contenu dans "NomClient"
        List<String> sqlColumns = List.of("nom");

        var result = service.analyzeCompatibility(SIMPLE_XSD, sqlColumns);

        // Le mapping partiel doit trouver NomClient → nom
        assertThat(result.getAutoMapped()).isNotEmpty();
    }
}
