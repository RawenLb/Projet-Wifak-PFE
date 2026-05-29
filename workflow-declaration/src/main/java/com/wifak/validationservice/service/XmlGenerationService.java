package com.wifak.validationservice.service;

import com.wifak.validationservice.dto.XsdSqlMappingRequest;
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
import java.util.*;
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
    // GÉNÉRATION SANS MAPPING (mode générique)
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
    // ✅ GÉNÉRATION AVEC MAPPING XSD ↔ SQL — CORRIGÉE
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

        mappings.forEach(m -> log.debug("  📌 Mapping: {}", m));

        // 1. Exécuter la SQL
        List<Map<String, Object>> rows = executeSqlQuery(sqlQuery, dateDebut, dateFin);
        log.info("✅ {} ligne(s) récupérée(s)", rows.size());

        // ── 2. Séparer mappings statiques / dynamiques / ignorés ──
        List<XsdSqlMappingRequest.FieldMapping> staticMappings = mappings.stream()
                .filter(m -> m.getSource() == XsdSqlMappingRequest.MappingSource.STATIC
                        && !m.getStaticValue().isEmpty())
                .collect(Collectors.toList());

        List<XsdSqlMappingRequest.FieldMapping> sqlMappings = mappings.stream()
                .filter(m -> m.getSource() == XsdSqlMappingRequest.MappingSource.SQL
                        && !m.getSqlColumn().isEmpty())
                .collect(Collectors.toList());

        log.info("📋 Mappings effectifs — {} statiques, {} SQL, {} ignorés",
                staticMappings.size(), sqlMappings.size(),
                mappings.size() - staticMappings.size() - sqlMappings.size());

        // 3. Construire le XML
        String xmlContent = buildXmlWithMapping(
                declarationCode, periode, dateDebut, dateFin,
                rows, staticMappings, sqlMappings
        );
        log.info("✅ XML avec mapping généré ({} caractères)", xmlContent.length());

        // 4. Validation XSD optionnelle
        validateOptional(xmlContent, xsdContent);
        return xmlContent;
    }
    // ✅ CONSTRUCTION XML AVEC MAPPING — structure correcte
    //
    //  <Declaration code="..." periode="...">
    //    <Entete>
    //      <CodeDeclaration>...</CodeDeclaration>
    //      <!-- champs statiques ici -->
    //    </Entete>
    //    <Donnees>
    //      <Ligne>
    //        <!-- champs SQL par ligne -->
    //      </Ligne>
    //      ...
    //    </Donnees>
    //  </Declaration>
    private static final Set<String> AUTO_HEADER_FIELD_NAMES = new HashSet<>(Arrays.asList(
            "CodeDeclaration", "Periode", "DateDebut", "DateFin",
            "NombreLignes", "DateGeneration"
    ));
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
            factory.setValidating(false);
            factory.setFeature("http://xml.org/sax/features/namespaces", false);
            factory.setFeature("http://xml.org/sax/features/validation", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            // ── Racine ─────────────────────────────────────────
            Element root = doc.createElement("Declaration");
            root.setAttribute("code",    safeStr(declarationCode));
            root.setAttribute("periode", safeStr(periode));
            doc.appendChild(root);

            // ── En-tête (toujours auto-généré) ─────────────────────
            Element entete = doc.createElement("Entete");
            addElement(doc, entete, "CodeDeclaration", safeStr(declarationCode));
            addElement(doc, entete, "Periode",         safeStr(periode));
            addElement(doc, entete, "DateDebut",       dateDebut != null ? dateDebut.toString() : "");
            addElement(doc, entete, "DateFin",         dateFin   != null ? dateFin.toString()   : "");
            addElement(doc, entete, "NombreLignes",    String.valueOf(rows.size()));
            addElement(doc, entete, "DateGeneration",  java.time.LocalDateTime.now().toString());

            // ✅ Champs STATIQUES — exclure les champs d'en-tête (déjà ajoutés ci-dessus)
            for (XsdSqlMappingRequest.FieldMapping sm : staticMappings) {
                if (AUTO_HEADER_FIELD_NAMES.contains(sm.getXsdFieldName())) {
                    log.debug("⏭️ Champ auto ignoré dans static: {}", sm.getXsdFieldName());
                    continue;
                }
                String tag   = sanitizeXmlTagName(sm.getXsdFieldName());
                String value = sm.getStaticValue();
                addElement(doc, entete, tag, value);
            }
            root.appendChild(entete);

            // ── Données ─────────────────────────────────────────────
            Element donnees = doc.createElement("Donnees");
            for (Map<String, Object> row : rows) {
                Element ligne = doc.createElement("Ligne");

                // ✅ Champs SQL — exclure également les champs auto
                for (XsdSqlMappingRequest.FieldMapping fm : sqlMappings) {
                    if (AUTO_HEADER_FIELD_NAMES.contains(fm.getXsdFieldName())) continue;

                    String col = fm.getSqlColumn();
                    Object val = row.get(col);
                    if (val == null) {
                        val = row.entrySet().stream()
                                .filter(e -> col.equalsIgnoreCase(e.getKey()))
                                .map(Map.Entry::getValue)
                                .filter(v -> v != null)
                                .findFirst()
                                .orElse(null);
                    }
                    addElement(doc, ligne, sanitizeXmlTagName(fm.getXsdFieldName()),
                            val != null ? val.toString() : "");
                }
                donnees.appendChild(ligne);
            }
            root.appendChild(donnees);

            return documentToString(doc);

        } catch (Exception e) {
            log.error("❌ Erreur construction XML avec mapping: {}", e.getMessage(), e);
            throw new RuntimeException("Erreur lors de la construction du XML: " + e.getMessage(), e);
        }}
    // CONSTRUCTION XML GÉNÉRIQUE (sans mapping)
    private String buildXml(
            String declarationCode,
            String periode,
            LocalDate dateDebut,
            LocalDate dateFin,
            List<Map<String, Object>> rows
    ) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // ✅ Fix XXE — désactiver les entités externes (CWE-611)
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
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
            addElement(doc, entete, "DateGeneration",
                    java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));            root.appendChild(entete);

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
    // EXÉCUTION SQL
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
    // EXTRACTION COLONNES
    public List<String> extractColumnsFromSql(String sqlQuery, LocalDate dateDebut, LocalDate dateFin) {
        try {
            String dateDébutStr = dateDebut != null ? dateDebut.toString() : "2000-01-01";
            String dateFinStr   = dateFin   != null ? dateFin.toString()   : "2099-12-31";

            // ✅ Résoudre les paramètres nommés en valeurs littérales
            String resolvedSql = sqlQuery
                    .replace(":dateDebut", "'" + dateDébutStr + "'")
                    .replace(":dateFin",   "'" + dateFinStr   + "'");

            // ✅ Supprimer le ORDER BY final — MySQL interdit ORDER BY dans une sous-requête
            // sans LIMIT, et LIMIT 0 avec ORDER BY cause aussi des erreurs selon la version
            String sqlWithoutOrderBy = resolvedSql.replaceAll("(?i)\\s+ORDER\\s+BY\\s+[^)]+$", "").trim();

            // Essai 1 : LIMIT 1 sans ORDER BY (le plus compatible)
            String wrappedSql = "SELECT * FROM (" + sqlWithoutOrderBy + ") AS _tmp_extract LIMIT 1";
            try {
                List<Map<String, Object>> result = jdbcTemplate.queryForList(wrappedSql);
                if (!result.isEmpty()) {
                    List<String> columns = new ArrayList<>(result.get(0).keySet());
                    log.info("✅ Colonnes extraites: {}", columns);
                    return columns;
                }
            } catch (Exception e1) {
                log.warn("⚠️ Tentative 1 échouée: {}", e1.getMessage());
            }

            // Essai 2 : plage de dates large pour garantir au moins une ligne
            String wideSql = sqlQuery
                    .replace(":dateDebut", "'2000-01-01'")
                    .replace(":dateFin",   "'2099-12-31'")
                    .replaceAll("(?i)\\s+ORDER\\s+BY\\s+[^)]+$", "").trim();
            String wideFallback = "SELECT * FROM (" + wideSql + ") AS _tmp_extract LIMIT 1";
            List<Map<String, Object>> wideResult = jdbcTemplate.queryForList(wideFallback);
            if (!wideResult.isEmpty()) {
                List<String> columns = new ArrayList<>(wideResult.get(0).keySet());
                log.info("✅ Colonnes extraites (plage large): {}", columns);
                return columns;
            }

            log.warn("⚠️ Aucune donnée — colonnes non disponibles");
            return new ArrayList<>();

        } catch (Exception e) {
            log.error("❌ Erreur extraction colonnes: {}", e.getMessage(), e);
            throw new RuntimeException("Impossible d'extraire les colonnes: " + e.getMessage(), e);
        }
    }
    // VALIDATION XSD (optionnelle)
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
            // ✅ Fix XXE — désactiver l'accès aux entités externes (CWE-611)
            schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Schema schema = schemaFactory.newSchema(new StreamSource(new StringReader(xsdContent)));
            Validator validator = schema.newValidator();
            // ✅ Fix XXE — désactiver les external entities sur le validateur
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.validate(new StreamSource(new StringReader(xmlContent)));
        } catch (Exception e) {
            throw new RuntimeException("Validation XSD: " + e.getMessage(), e);
        }
    }
    // UTILITAIRES
    private void addElement(Document doc, Element parent, String tagName, String value) {
        Element el = doc.createElement(tagName);
        el.setTextContent(value != null ? value : "");
        parent.appendChild(el);
    }

    private String documentToString(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        // ✅ Fix XXE — désactiver l'accès aux ressources externes (CWE-611)
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT,   "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    private String sanitizeXmlTagName(String columnName) {
        if (columnName == null || columnName.trim().isEmpty()) return "field";
        String s = columnName.trim().replace(" ", "_");
        if (Character.isDigit(s.charAt(0))) s = "col_" + s;
        s = s.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
        return s.isEmpty() ? "field" : s;
    }

    private String safeStr(String s) {
        return s != null ? s : "";
    }
}
