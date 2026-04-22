package com.example.bctbackend.service;

import com.example.bctbackend.dto.XsdSqlMappingRequest;
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
import java.util.stream.Collectors;

@Service
public class XmlGenerationService {

    private static final Logger log = LoggerFactory.getLogger(XmlGenerationService.class);
    private final JdbcTemplate jdbcTemplate;

    public XmlGenerationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ════════════════════════════════════════════════════════════
    // GÉNÉRATION SANS MAPPING (mode générique)
    // ════════════════════════════════════════════════════════════

    public String generateXmlFromXsdAndSql(
            String xsdContent,
            String sqlQuery,
            LocalDate dateDebut,
            LocalDate dateFin,
            String declarationCode,
            String periode
    ) {
        log.info("🚀 Génération XML générique — Déclaration: {}, Période: {}", declarationCode, periode);

        List<Map<String, Object>> rows = executeSqlQuery(sqlQuery, dateDebut, dateFin);
        log.info("✅ SQL exécuté — {} ligne(s)", rows.size());

        String xmlContent = buildXml(declarationCode, periode, dateDebut, dateFin, rows);
        log.info("✅ XML générique généré");

        validateOptional(xmlContent, xsdContent);
        return xmlContent;
    }

    // ════════════════════════════════════════════════════════════
    // ✅ GÉNÉRATION AVEC MAPPING XSD ↔ SQL
    // ════════════════════════════════════════════════════════════

    public String generateXmlFromMapping(
            String xsdContent,
            String sqlQuery,
            LocalDate dateDebut,
            LocalDate dateFin,
            String declarationCode,
            String periode,
            List<XsdSqlMappingRequest.FieldMapping> mappings
    ) {
        log.info("🚀 Génération XML avec mapping — Déclaration: {}, Période: {}, {} mappings",
                declarationCode, periode, mappings.size());

        // ── Journaliser les mappings reçus pour diagnostic ─────────
        mappings.forEach(m -> log.debug("  📌 Mapping reçu: {}", m));

        // 1. Exécuter la SQL
        List<Map<String, Object>> rows = executeSqlQuery(sqlQuery, dateDebut, dateFin);
        log.info("✅ {} ligne(s) récupérée(s)", rows.size());

        // ✅ 2. Filtrer avec getSource() null-safe (via le DTO corrigé)
        List<XsdSqlMappingRequest.FieldMapping> staticMappings = mappings.stream()
                .filter(m -> m.getSource() == XsdSqlMappingRequest.MappingSource.STATIC)
                .collect(Collectors.toList());

        List<XsdSqlMappingRequest.FieldMapping> sqlMappings = mappings.stream()
                .filter(m -> m.getSource() == XsdSqlMappingRequest.MappingSource.SQL)
                .collect(Collectors.toList());

        log.info("📋 Mappings — {} statiques, {} SQL, {} ignorés",
                staticMappings.size(), sqlMappings.size(),
                mappings.size() - staticMappings.size() - sqlMappings.size());

        // 3. Construire le XML
        String xmlContent = buildXmlWithMapping(
                declarationCode, periode, dateDebut, dateFin,
                rows, staticMappings, sqlMappings
        );
        log.info("✅ XML avec mapping généré ({} caractères)", xmlContent.length());

        // 4. Validation XSD optionnelle (warning seulement)
        validateOptional(xmlContent, xsdContent);
        return xmlContent;
    }

    // ════════════════════════════════════════════════════════════
    // CONSTRUCTION XML AVEC MAPPING — null-safe
    // ════════════════════════════════════════════════════════════

    private String buildXmlWithMapping(
            String declarationCode,
            String periode,
            LocalDate dateDebut,
            LocalDate dateFin,
            List<Map<String, Object>> rows,
            List<XsdSqlMappingRequest.FieldMapping> staticMappings,
            List<XsdSqlMappingRequest.FieldMapping> sqlMappings
    ) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // ✅ Désactiver la validation DTD pour éviter des NPE internes
            factory.setValidating(false);
            factory.setFeature("http://xml.org/sax/features/namespaces", false);
            factory.setFeature("http://xml.org/sax/features/validation", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            // ── Racine ────────────────────────────────────────────
            Element root = doc.createElement("Declaration");
            root.setAttribute("code",    safeStr(declarationCode));
            root.setAttribute("periode", safeStr(periode));
            doc.appendChild(root);

            // ── En-tête ───────────────────────────────────────────
            Element entete = doc.createElement("Entete");
            addElement(doc, entete, "CodeDeclaration", safeStr(declarationCode));
            addElement(doc, entete, "Periode",         safeStr(periode));
            addElement(doc, entete, "DateDebut",       dateDebut != null ? dateDebut.toString() : "");
            addElement(doc, entete, "DateFin",         dateFin   != null ? dateFin.toString()   : "");
            addElement(doc, entete, "NombreLignes",    String.valueOf(rows.size()));
            addElement(doc, entete, "DateGeneration",  java.time.LocalDateTime.now().toString());

            // Champs statiques → en-tête
            for (XsdSqlMappingRequest.FieldMapping sm : staticMappings) {
                String tag   = sanitizeXmlTagName(sm.getXsdFieldName());
                String value = sm.getStaticValue(); // getStaticValue() ne retourne jamais null
                addElement(doc, entete, tag, value);
                log.debug("📌 Statique en-tête : {} = '{}'", tag, value);
            }
            root.appendChild(entete);

            // ── Données : une Ligne par enregistrement SQL ─────────
            Element donnees = doc.createElement("Donnees");

            for (Map<String, Object> row : rows) {
                Element ligne = doc.createElement("Ligne");

                for (XsdSqlMappingRequest.FieldMapping fm : sqlMappings) {
                    String col = fm.getSqlColumn(); // getSqlColumn() ne retourne jamais null
                    if (col.isEmpty()) {
                        log.warn("⚠️ Champ SQL '{}' sans colonne définie — ignoré", fm.getXsdFieldName());
                        continue;
                    }

                    // Recherche de la valeur — exacte puis insensible à la casse
                    Object val = row.get(col);
                    if (val == null) {
                        val = row.entrySet().stream()
                                .filter(e -> col.equalsIgnoreCase(e.getKey()))
                                .map(Map.Entry::getValue)
                                .findFirst()
                                .orElse(null);
                    }

                    String tag   = sanitizeXmlTagName(fm.getXsdFieldName());
                    String value = val != null ? val.toString() : "";
                    addElement(doc, ligne, tag, value);
                }

                donnees.appendChild(ligne);
            }

            root.appendChild(donnees);

            String xml = documentToString(doc);
            log.debug("📄 Extrait XML généré :\n{}", xml.length() > 500 ? xml.substring(0, 500) + "..." : xml);
            return xml;

        } catch (Exception e) {
            log.error("❌ Erreur construction XML avec mapping: {}", e.getMessage(), e);
            throw new RuntimeException("Erreur lors de la construction du XML: " + e.getMessage(), e);
        }
    }

    // ════════════════════════════════════════════════════════════
    // CONSTRUCTION XML GÉNÉRIQUE (sans mapping)
    // ════════════════════════════════════════════════════════════

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

            Element root = doc.createElement("Declaration");
            root.setAttribute("code",         safeStr(declarationCode));
            root.setAttribute("periode",      safeStr(periode));
            root.setAttribute("dateDebut",    dateDebut != null ? dateDebut.toString() : "");
            root.setAttribute("dateFin",      dateFin   != null ? dateFin.toString()   : "");
            root.setAttribute("nombreLignes", String.valueOf(rows.size()));
            doc.appendChild(root);

            Element entete = doc.createElement("Entete");
            addElement(doc, entete, "CodeDeclaration", safeStr(declarationCode));
            addElement(doc, entete, "Periode",         safeStr(periode));
            addElement(doc, entete, "DateDebut",       dateDebut != null ? dateDebut.toString() : "");
            addElement(doc, entete, "DateFin",         dateFin   != null ? dateFin.toString()   : "");
            addElement(doc, entete, "NombreLignes",    String.valueOf(rows.size()));
            addElement(doc, entete, "DateGeneration",  java.time.LocalDateTime.now().toString());
            root.appendChild(entete);

            Element donnees = doc.createElement("Donnees");
            for (Map<String, Object> row : rows) {
                Element record = doc.createElement("Ligne");
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    String tagName = sanitizeXmlTagName(entry.getKey());
                    String value   = entry.getValue() != null ? entry.getValue().toString() : "";
                    addElement(doc, record, tagName, value);
                }
                donnees.appendChild(record);
            }
            root.appendChild(donnees);

            return documentToString(doc);

        } catch (Exception e) {
            log.error("❌ Erreur construction XML: {}", e.getMessage(), e);
            throw new RuntimeException("Erreur lors de la construction du XML: " + e.getMessage(), e);
        }
    }

    // ════════════════════════════════════════════════════════════
    // EXÉCUTION SQL
    // ════════════════════════════════════════════════════════════

    private List<Map<String, Object>> executeSqlQuery(
            String sqlQuery,
            LocalDate dateDebut,
            LocalDate dateFin
    ) {
        try {
            String preparedSql = sqlQuery
                    .replace(":dateDebut", "?")
                    .replace(":dateFin",   "?");

            List<Object> args = buildArgsInOrder(sqlQuery, dateDebut, dateFin);
            log.debug("SQL préparé: {}", preparedSql);
            return jdbcTemplate.queryForList(preparedSql, args.toArray());

        } catch (Exception e) {
            log.error("❌ Erreur exécution SQL: {}", e.getMessage(), e);
            throw new RuntimeException("Erreur lors de l'exécution de la requête SQL: " + e.getMessage(), e);
        }
    }

    private List<Object> buildArgsInOrder(String sqlQuery, LocalDate dateDebut, LocalDate dateFin) {
        List<Object>  args      = new ArrayList<>();
        List<int[]>   positions = new ArrayList<>();
        Pattern       pattern   = Pattern.compile(":(dateDebut|dateFin)");
        Matcher       matcher   = pattern.matcher(sqlQuery);
        while (matcher.find()) {
            positions.add(new int[]{ matcher.start(), "dateDebut".equals(matcher.group(1)) ? 0 : 1 });
        }
        positions.sort((a, b) -> Integer.compare(a[0], b[0]));
        for (int[] pos : positions) {
            args.add(pos[1] == 0
                    ? (dateDebut != null ? dateDebut.toString() : "")
                    : (dateFin   != null ? dateFin.toString()   : ""));
        }
        return args;
    }

    // ════════════════════════════════════════════════════════════
    // EXTRACTION COLONNES (pour l'analyse mapping)
    // ════════════════════════════════════════════════════════════

    public List<String> extractColumnsFromSql(String sqlQuery, LocalDate dateDebut, LocalDate dateFin) {
        try {
            String wrappedSql = "SELECT * FROM (" +
                    sqlQuery.replace(":dateDebut", "?").replace(":dateFin", "?") +
                    ") AS _tmp_extract LIMIT 1";
            List<Object> args = buildArgsInOrder(sqlQuery, dateDebut, dateFin);
            List<Map<String, Object>> result = jdbcTemplate.queryForList(wrappedSql, args.toArray());
            if (!result.isEmpty()) {
                List<String> columns = new ArrayList<>(result.get(0).keySet());
                log.info("✅ Colonnes extraites: {}", columns);
                return columns;
            }
            log.warn("⚠️ Aucune donnée — colonnes non disponibles");
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("❌ Erreur extraction colonnes: {}", e.getMessage(), e);
            throw new RuntimeException("Impossible d'extraire les colonnes: " + e.getMessage(), e);
        }
    }

    // ════════════════════════════════════════════════════════════
    // VALIDATION XSD (optionnelle)
    // ════════════════════════════════════════════════════════════

    private void validateOptional(String xmlContent, String xsdContent) {
        if (xsdContent == null || xsdContent.trim().isEmpty()) return;
        try {
            validateXmlAgainstXsd(xmlContent, xsdContent);
            log.info("✅ XML valide selon le XSD");
        } catch (Exception e) {
            log.warn("⚠️ Validation XSD non conforme (génération continue): {}", e.getMessage());
        }
    }

    public void validateXmlAgainstXsd(String xmlContent, String xsdContent) {
        try {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = schemaFactory.newSchema(new StreamSource(new StringReader(xsdContent)));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new StringReader(xmlContent)));
        } catch (Exception e) {
            throw new RuntimeException("Validation XSD: " + e.getMessage(), e);
        }
    }

    // ════════════════════════════════════════════════════════════
    // UTILITAIRES
    // ════════════════════════════════════════════════════════════

    private void addElement(Document doc, Element parent, String tagName, String value) {
        Element el = doc.createElement(tagName);
        el.setTextContent(value != null ? value : "");
        parent.appendChild(el);
    }

    private String documentToString(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT,   "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    /**
     * Assainit un nom de colonne SQL pour en faire un nom d'élément XML valide.
     * Règles XML : commence par lettre ou _, pas d'espaces, pas de caractères spéciaux.
     */
    private String sanitizeXmlTagName(String columnName) {
        if (columnName == null || columnName.trim().isEmpty()) return "field";
        String s = columnName.trim().replace(" ", "_");
        if (Character.isDigit(s.charAt(0))) s = "col_" + s;
        s = s.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
        return s.isEmpty() ? "field" : s;
    }

    /** Retourne une chaîne vide si la valeur est null. */
    private String safeStr(String s) {
        return s != null ? s : "";
    }
}