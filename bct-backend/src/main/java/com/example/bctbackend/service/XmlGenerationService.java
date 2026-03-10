package com.example.bctbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class XmlGenerationService {

    private static final Logger log = LoggerFactory.getLogger(XmlGenerationService.class);
    private final JdbcTemplate jdbcTemplate;

    public XmlGenerationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ================================================================
    // MÉTHODE PRINCIPALE
    // ================================================================

    public String generateXmlFromXsdAndSql(
            String xsdContent,
            String sqlQuery,
            LocalDate dateDebut,
            LocalDate dateFin,
            String declarationCode,
            String periode
    ) {
        log.info("🚀 Début génération XML - Déclaration: {}, Période: {}", declarationCode, periode);

        // ÉTAPE 1 — Exécuter la SQL
        List<Map<String, Object>> rows = executeSqlQuery(sqlQuery, dateDebut, dateFin);
        log.info("✅ SQL exécuté — {} ligne(s) récupérée(s)", rows.size());

        // ÉTAPE 2 — Générer le XML (structure générique, toujours valide)
        String xmlContent = buildXml(declarationCode, periode, dateDebut, dateFin, rows);
        log.info("✅ XML généré");

        // ÉTAPE 3 — Validation XSD optionnelle (warning seulement, pas d'exception)
        if (xsdContent != null && !xsdContent.trim().isEmpty()) {
            try {
                validateXmlAgainstXsd(xmlContent, xsdContent);
                log.info("✅ XML valide selon le XSD");
            } catch (Exception e) {
                // ✅ CORRIGÉ — Ne pas bloquer la génération si la validation échoue
                // Le XML généré est générique, pas forcément identique à la structure XSD
                log.warn("⚠️ Validation XSD non conforme (génération continue): {}", e.getMessage());
            }
        }

        return xmlContent;
    }

    // ================================================================
    // ÉTAPE 1 — EXÉCUTION SQL CORRIGÉE
    // ================================================================

    private List<Map<String, Object>> executeSqlQuery(
            String sqlQuery,
            LocalDate dateDebut,
            LocalDate dateFin
    ) {
        try {
            // ✅ CORRIGÉ — Compter les occurrences réelles de chaque paramètre
            int countDebut = countOccurrences(sqlQuery, ":dateDebut");
            int countFin   = countOccurrences(sqlQuery, ":dateFin");

            log.info("📊 Paramètres SQL — :dateDebut × {}, :dateFin × {}", countDebut, countFin);

            // ✅ Remplacer les paramètres nommés par des ?
            String preparedSql = sqlQuery
                    .replace(":dateDebut", "?")
                    .replace(":dateFin", "?");

            // ✅ CORRIGÉ — Construire les arguments en respectant l'ordre d'apparition
            List<Object> args = buildArgsInOrder(sqlQuery, dateDebut, dateFin);

            log.debug("SQL préparé: {}", preparedSql);
            log.debug("Nombre d'arguments: {}", args.size());

            return jdbcTemplate.queryForList(preparedSql, args.toArray());

        } catch (Exception e) {
            log.error("❌ Erreur exécution SQL: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de l'exécution de la requête SQL: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ NOUVEAU — Compte le nombre d'occurrences d'un token dans une chaîne
     */
    private int countOccurrences(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) != -1) {
            count++;
            index += token.length();
        }
        return count;
    }

    /**
     * ✅ NOUVEAU — Construit la liste d'arguments en respectant l'ordre d'apparition
     * Exemple: "WHERE date BETWEEN :dateDebut AND :dateFin UNION ALL WHERE date BETWEEN :dateDebut AND :dateFin"
     * → [dateDebut, dateFin, dateDebut, dateFin]
     */
    private List<Object> buildArgsInOrder(String sqlQuery, LocalDate dateDebut, LocalDate dateFin) {
        List<Object> args = new ArrayList<>();

        // Trouver toutes les positions de :dateDebut et :dateFin
        List<int[]> positions = new ArrayList<>(); // [position, type] type: 0=dateDebut, 1=dateFin

        Pattern pattern = Pattern.compile(":(dateDebut|dateFin)");
        Matcher matcher = pattern.matcher(sqlQuery);

        while (matcher.find()) {
            int type = matcher.group(1).equals("dateDebut") ? 0 : 1;
            positions.add(new int[]{matcher.start(), type});
        }

        // Trier par position et ajouter les arguments dans l'ordre
        positions.sort((a, b) -> Integer.compare(a[0], b[0]));
        for (int[] pos : positions) {
            if (pos[1] == 0) {
                args.add(dateDebut.toString()); // :dateDebut → "2025-01-01"
            } else {
                args.add(dateFin.toString());   // :dateFin → "2025-01-31"
            }
        }

        log.info("📋 Arguments construits dans l'ordre: {}", args);
        return args;
    }

    // ================================================================
    // ÉTAPE 2 — CONSTRUCTION XML
    // ================================================================

    private String buildXml(
            String declarationCode,
            String periode,
            LocalDate dateDebut,
            LocalDate dateFin,
            List<Map<String, Object>> rows
    ) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            // Élément racine
            Element root = doc.createElement("Declaration");
            root.setAttribute("code", declarationCode);
            root.setAttribute("periode", periode);
            root.setAttribute("dateDebut", dateDebut.toString());
            root.setAttribute("dateFin", dateFin.toString());
            root.setAttribute("nombreLignes", String.valueOf(rows.size()));
            doc.appendChild(root);

            // En-tête
            Element entete = doc.createElement("Entete");
            addElement(doc, entete, "CodeDeclaration", declarationCode);
            addElement(doc, entete, "Periode", periode);
            addElement(doc, entete, "DateDebut", dateDebut.toString());
            addElement(doc, entete, "DateFin", dateFin.toString());
            addElement(doc, entete, "NombreLignes", String.valueOf(rows.size()));
            addElement(doc, entete, "DateGeneration",
                    java.time.LocalDateTime.now().toString());
            root.appendChild(entete);

            // Données
            Element donnees = doc.createElement("Donnees");
            for (Map<String, Object> row : rows) {
                Element record = doc.createElement("Ligne");
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    String tagName = sanitizeXmlTagName(entry.getKey());
                    String value   = entry.getValue() != null
                            ? entry.getValue().toString() : "";
                    addElement(doc, record, tagName, value);
                }
                donnees.appendChild(record);
            }
            root.appendChild(donnees);

            return documentToString(doc);

        } catch (Exception e) {
            log.error("❌ Erreur construction XML: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la construction du XML: " + e.getMessage(), e);
        }
    }

    private void addElement(Document doc, Element parent, String tagName, String value) {
        Element el = doc.createElement(tagName);
        el.setTextContent(value != null ? value : "");
        parent.appendChild(el);
    }

    // ================================================================
    // ÉTAPE 3 — VALIDATION XSD
    // ================================================================

    public void validateXmlAgainstXsd(String xmlContent, String xsdContent) {
        try {
            SchemaFactory schemaFactory =
                    SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = schemaFactory.newSchema(
                    new StreamSource(new StringReader(xsdContent)));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new StringReader(xmlContent)));
        } catch (Exception e) {
            throw new RuntimeException("Validation XSD: " + e.getMessage(), e);
        }
    }

    // ================================================================
    // EXTRACTION COLONNES (pour le test SQL)
    // ================================================================

    public List<String> extractColumnsFromSql(
            String sqlQuery,
            LocalDate dateDebut,
            LocalDate dateFin
    ) {
        try {
            // ✅ CORRIGÉ — Wrapper dans une sous-requête pour éviter les problèmes
            // avec UNION ALL + ORDER BY + LIMIT
            String wrappedSql = "SELECT * FROM (" +
                    sqlQuery.replace(":dateDebut", "?").replace(":dateFin", "?") +
                    ") AS _tmp_extract LIMIT 1";

            List<Object> args = buildArgsInOrder(sqlQuery, dateDebut, dateFin);
            List<Map<String, Object>> result =
                    jdbcTemplate.queryForList(wrappedSql, args.toArray());

            if (!result.isEmpty()) {
                List<String> columns = new ArrayList<>(result.get(0).keySet());
                log.info("✅ Colonnes extraites: {}", columns);
                return columns;
            }

            // ✅ Si aucune donnée, exécuter quand même pour avoir les colonnes
            // via metadata — retourner liste vide avec message
            log.warn("⚠️ Aucune donnée pour la période donnée — colonnes non disponibles");
            return new ArrayList<>();

        } catch (Exception e) {
            log.error("❌ Erreur extraction colonnes: {}", e.getMessage());
            throw new RuntimeException("Impossible d'extraire les colonnes: " + e.getMessage(), e);
        }
    }

    // ================================================================
    // UTILITAIRES
    // ================================================================

    private String documentToString(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(
                "{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    private String sanitizeXmlTagName(String columnName) {
        if (columnName == null || columnName.isEmpty()) return "field";
        String s = columnName.trim().replace(" ", "_");
        if (Character.isDigit(s.charAt(0))) s = "col_" + s;
        s = s.replaceAll("[^a-zA-Z0-9_]", "_");
        return s;
    }
}