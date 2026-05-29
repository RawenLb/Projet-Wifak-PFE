package com.wifak.validationservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class XsdAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(XsdAnalyzerService.class);
    private static final int MAX_XSD_DEPTH = 10;
    private static final String XS_NS = "http://www.w3.org/2001/XMLSchema";

    /**
     * Champs gÃ©nÃ©rÃ©s automatiquement par le backend dans l'en-tÃªte XML.
     * Ils ne doivent PAS apparaÃ®tre dans le panneau de mapping utilisateur.
     */
    private static final Set<String> AUTO_HEADER_FIELDS = new HashSet<>(Arrays.asList(
            "codedeclaration", "periode", "datedebut", "datefin",
            "nombreligne", "nombreslignes", "nombresdeligne",
            "dategeneration", "datecreation"
    ));

    private boolean isAutoHeaderField(String fieldName) {
        if (fieldName == null) return false;
        String normalized = fieldName.toLowerCase()
                .replace("_", "").replace("-", "").replace(" ", "");
        return AUTO_HEADER_FIELDS.contains(normalized);
    }
    // RÃ‰SULTAT
    public static class XsdFieldInfo {
        private String  name;
        private String  path;
        private String  type;
        private boolean required;
        private String  defaultValue;
        private int     maxOccurs;

        public XsdFieldInfo(String name, String path, String type,
                            boolean required, String defaultValue, int maxOccurs) {
            this.name         = name;
            this.path         = path;
            this.type         = type;
            this.required     = required;
            this.defaultValue = defaultValue;
            this.maxOccurs    = maxOccurs;
        }

        public String  getName()         { return name; }
        public String  getPath()         { return path; }
        public String  getType()         { return type; }
        public boolean isRequired()      { return required; }
        public String  getDefaultValue() { return defaultValue; }
        public int     getMaxOccurs()    { return maxOccurs; }
    }

    public static class MappingAnalysisResult {
        private List<XsdFieldInfo>    xsdFields;
        private List<String>          sqlColumns;
        private Map<String, String>   autoMapped;
        private List<String>          unmappedXsdFields;
        private List<String>          unmappedSqlColumns;
        private int                   compatibilityScore;
        private String                summary;

        public List<XsdFieldInfo>  getXsdFields()          { return xsdFields; }
        public void setXsdFields(List<XsdFieldInfo> v)     { xsdFields = v; }
        public List<String> getSqlColumns()                 { return sqlColumns; }
        public void setSqlColumns(List<String> v)           { sqlColumns = v; }
        public Map<String, String> getAutoMapped()          { return autoMapped; }
        public void setAutoMapped(Map<String, String> v)    { autoMapped = v; }
        public List<String> getUnmappedXsdFields()          { return unmappedXsdFields; }
        public void setUnmappedXsdFields(List<String> v)    { unmappedXsdFields = v; }
        public List<String> getUnmappedSqlColumns()         { return unmappedSqlColumns; }
        public void setUnmappedSqlColumns(List<String> v)   { unmappedSqlColumns = v; }
        public int  getCompatibilityScore()                 { return compatibilityScore; }
        public void setCompatibilityScore(int v)            { compatibilityScore = v; }
        public String getSummary()                          { return summary; }
        public void setSummary(String v)                    { summary = v; }
    }
    // POINT D'ENTRÃ‰E
    public MappingAnalysisResult analyzeCompatibility(String xsdContent, List<String> sqlColumns) {
        log.info("ðŸ” Analyse XSD â€” {} colonnes SQL disponibles", sqlColumns.size());

        List<XsdFieldInfo> allFields  = parseXsdFields(xsdContent);

        // âœ… Filtrer les champs d'en-tÃªte auto-gÃ©rÃ©s par le backend
        List<XsdFieldInfo> xsdFields = allFields.stream()
                .filter(f -> !isAutoHeaderField(f.getName()))
                .collect(Collectors.toList());

        log.info("ðŸ“‹ {} champs XSD mappables ({} ignorÃ©s car auto-gÃ©nÃ©rÃ©s)",
                xsdFields.size(), allFields.size() - xsdFields.size());

        Map<String, String> autoMapped    = buildAutoMapping(xsdFields, sqlColumns);
        List<String>        unmappedXsd   = computeUnmappedXsd(xsdFields, autoMapped);
        List<String>        unmappedSql   = computeUnmappedSql(sqlColumns, autoMapped);
        int                 score         = computeScore(xsdFields, autoMapped);
        String              summary       = buildSummary(xsdFields, autoMapped, score);

        MappingAnalysisResult result = new MappingAnalysisResult();
        result.setXsdFields(xsdFields);
        result.setSqlColumns(new ArrayList<>(sqlColumns));
        result.setAutoMapped(autoMapped);
        result.setUnmappedXsdFields(unmappedXsd);
        result.setUnmappedSqlColumns(unmappedSql);
        result.setCompatibilityScore(score);
        result.setSummary(summary);
        return result;
    }
    // âœ… PARSING XSD â€” robuste avec ou sans prologue <?xml?>
    private List<XsdFieldInfo> parseXsdFields(String xsdContent) {
        List<XsdFieldInfo> fields = new ArrayList<>();
        if (xsdContent == null || xsdContent.trim().isEmpty()) {
            log.warn("âš ï¸ XSD vide ou null");
            return fields;
        }

        try {
            Document doc = parseXml(xsdContent);

            // RÃ©cupÃ©rer tous les xs:complexType nommÃ©s pour rÃ©solution des rÃ©fÃ©rences
            Map<String, Element> namedComplexTypes = collectNamedComplexTypes(doc);
            log.debug("ðŸ—‚ï¸ {} complexType(s) nommÃ©(s) trouvÃ©s", namedComplexTypes.size());

            // Trouver l'Ã©lÃ©ment racine (xs:element de niveau schema)
            NodeList topElements = doc.getElementsByTagNameNS(XS_NS, "element");
            for (int i = 0; i < topElements.getLength(); i++) {
                Element el = (Element) topElements.item(i);
                // Ne traiter que les Ã©lÃ©ments directs enfants de xs:schema
                if (el.getParentNode() instanceof Element) {
                    Element parent = (Element) el.getParentNode();
                    String parentLocal = parent.getLocalName();
                    if ("schema".equals(parentLocal)) {
                        String rootName = el.getAttribute("name");
                        String typeName = el.getAttribute("type");
                        if (!typeName.isEmpty()) {
                            // RÃ©soudre via complexType nommÃ©
                            String typeNameLocal = stripNsPrefix(typeName);
                            Element complexType = namedComplexTypes.get(typeNameLocal);
                            if (complexType != null) {
                                collectFieldsFromComplexType(
                                        complexType, rootName, namedComplexTypes, fields, 0);
                            }
                        } else {
                            // complexType inline
                            Element inlineType = getFirstChildElement(el, "complexType");
                            if (inlineType != null) {
                                collectFieldsFromComplexType(
                                        inlineType, rootName, namedComplexTypes, fields, 0);
                            }
                        }
                        break; // Traiter seulement le premier Ã©lÃ©ment racine
                    }
                }
            }

        } catch (Exception e) {
            log.error("âŒ Erreur parsing XSD : {}", e.getMessage(), e);
        }

        return fields;
    }

    /**
     * âœ… Parse le XML en supprimant le prologue <?xml...?> si prÃ©sent.
     * C'est le correctif principal pour l'erreur
     * "La cible de l'instruction de traitement correspondant Ã  [xX][mM][lL] n'est pas autorisÃ©e."
     */
    private Document parseXml(String xsdContent) throws Exception {
        String content = xsdContent.trim();

        // âœ… Supprimer le prologue <?xml ... ?> s'il est prÃ©sent
        if (content.startsWith("<?xml") || content.startsWith("<?XML")) {
            int prologEnd = content.indexOf("?>");
            if (prologEnd != -1) {
                content = content.substring(prologEnd + 2).trim();
                log.debug("ðŸ”§ Prologue XML supprimÃ© avant parsing");
            }
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        // SÃ©curitÃ© : dÃ©sactiver les entitÃ©s externes (XXE)
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder builder = factory.newDocumentBuilder();

        // Supprimer les messages d'erreur du parser vers stderr
        builder.setErrorHandler(null);

        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return builder.parse(new ByteArrayInputStream(bytes));
    }
    // COLLECTE DES COMPLEXTYPE NOMMÃ‰S
    private Map<String, Element> collectNamedComplexTypes(Document doc) {
        Map<String, Element> map = new LinkedHashMap<>();
        NodeList cts = doc.getElementsByTagNameNS(XS_NS, "complexType");
        for (int i = 0; i < cts.getLength(); i++) {
            Element ct = (Element) cts.item(i);
            String name = ct.getAttribute("name");
            if (!name.isEmpty()) {
                map.put(name, ct);
            }
        }
        return map;
    }
    // COLLECTE RÃ‰CURSIVE DES CHAMPS
    private void collectFieldsFromComplexType(Element complexType,
                                              String parentPath,
                                              Map<String, Element> namedTypes,
                                              List<XsdFieldInfo> fields,
                                              int depth) {
        if (depth > MAX_XSD_DEPTH) return; // Protection anti-boucle infinie

        // Chercher xs:sequence, xs:all, xs:choice
        for (String groupTag : new String[]{"sequence", "all", "choice"}) {
            Element group = getFirstChildElement(complexType, groupTag);
            if (group != null) {
                collectFieldsFromGroup(group, parentPath, namedTypes, fields, depth);
                return;
            }
        }
    }

    private void collectFieldsFromGroup(Element group,
                                        String parentPath,
                                        Map<String, Element> namedTypes,
                                        List<XsdFieldInfo> fields,
                                        int depth) {
        NodeList children = group.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element)) continue;
            Element child = (Element) children.item(i);
            String localName = child.getLocalName();
            if (localName == null) continue;

            switch (localName) {
                case "element":
                    processElement(child, parentPath, namedTypes, fields, depth);
                    break;
                case "sequence":
                case "all":
                case "choice":
                    collectFieldsFromGroup(child, parentPath, namedTypes, fields, depth);
                    break;
                default:
                    break;
            }
        }
    }

    private void processElement(Element el,
                                String parentPath,
                                Map<String, Element> namedTypes,
                                List<XsdFieldInfo> fields,
                                int depth) {
        String name      = el.getAttribute("name");
        String typeName  = el.getAttribute("type");
        String minOccurs = el.getAttribute("minOccurs");
        String maxOcc    = el.getAttribute("maxOccurs");
        String defVal    = el.getAttribute("default");

        if (name.isEmpty()) return;

        String path      = parentPath.isEmpty() ? name : parentPath + "/" + name;
        boolean required = !"0".equals(minOccurs);
        int     maxO     = "unbounded".equals(maxOcc) ? Integer.MAX_VALUE
                : (maxOcc.isEmpty() ? 1 : parseIntSafe(maxOcc, 1));

        // RÃ©soudre le type
        String resolvedType = resolveSimpleTypeName(typeName);

        // VÃ©rifier si c'est un type complexe rÃ©fÃ©rencÃ©
        if (!typeName.isEmpty()) {
            String localTypeName = stripNsPrefix(typeName);
            Element namedType = namedTypes.get(localTypeName);
            if (namedType != null) {
                // C'est un sous-type complexe â†’ descendre rÃ©cursivement
                collectFieldsFromComplexType(namedType, path, namedTypes, fields, depth + 1);
                return;
            }
        }

        // Chercher un complexType ou simpleType inline
        Element inlineComplex = getFirstChildElement(el, "complexType");
        if (inlineComplex != null) {
            collectFieldsFromComplexType(inlineComplex, path, namedTypes, fields, depth + 1);
            return;
        }

        Element inlineSimple = getFirstChildElement(el, "simpleType");
        if (inlineSimple != null) {
            // Extraire le type de base de la restriction
            Element restriction = getFirstChildElement(inlineSimple, "restriction");
            if (restriction != null) {
                String base = stripNsPrefix(restriction.getAttribute("base"));
                resolvedType = base.isEmpty() ? "string" : base;
            }
        }

        // Champ feuille â†’ ajouter Ã  la liste
        fields.add(new XsdFieldInfo(name, path, resolvedType, required,
                defVal.isEmpty() ? null : defVal, maxO));
        log.debug("  âœ… Champ: {} [{}] requis={}", path, resolvedType, required);
    }
    // AUTO-MAPPING XSD â†” SQL
    private Map<String, String> buildAutoMapping(List<XsdFieldInfo> xsdFields,
                                                 List<String> sqlColumns) {
        Map<String, String> mapping = new LinkedHashMap<>();

        // Index SQL normalisÃ© â†’ nom original
        Map<String, String> sqlIndex = new LinkedHashMap<>();
        for (String col : sqlColumns) {
            sqlIndex.put(normalize(col), col);
        }

        for (XsdFieldInfo field : xsdFields) {
            String normField = normalize(field.getName());

            // 1) Correspondance exacte normalisÃ©e
            if (sqlIndex.containsKey(normField)) {
                mapping.put(field.getName(), sqlIndex.get(normField));
                continue;
            }

            // 2) Recherche partielle (XSD âŠ‚ SQL ou SQL âŠ‚ XSD)
            String best = null;
            for (Map.Entry<String, String> entry : sqlIndex.entrySet()) {
                String normSql = entry.getKey();
                if (normSql.contains(normField) || normField.contains(normSql)) {
                    best = entry.getValue();
                    break;
                }
            }
            if (best != null) {
                mapping.put(field.getName(), best);
            }
        }

        log.info("ðŸ”— Auto-mapping : {}/{} champs mappÃ©s automatiquement",
                mapping.size(), xsdFields.size());
        return mapping;
    }
    // HELPERS CALCUL
    private List<String> computeUnmappedXsd(List<XsdFieldInfo> xsdFields,
                                            Map<String, String> autoMapped) {
        List<String> unmapped = new ArrayList<>();
        for (XsdFieldInfo f : xsdFields) {
            if (!autoMapped.containsKey(f.getName())) {
                unmapped.add(f.getName());
            }
        }
        return unmapped;
    }

    private List<String> computeUnmappedSql(List<String> sqlColumns,
                                            Map<String, String> autoMapped) {
        Set<String> usedSql = new HashSet<>(autoMapped.values());
        List<String> unmapped = new ArrayList<>();
        for (String col : sqlColumns) {
            if (!usedSql.contains(col)) {
                unmapped.add(col);
            }
        }
        return unmapped;
    }

    private int computeScore(List<XsdFieldInfo> xsdFields,
                             Map<String, String> autoMapped) {
        if (xsdFields.isEmpty()) return 0;
        long requiredTotal  = xsdFields.stream().filter(XsdFieldInfo::isRequired).count();
        long requiredMapped = xsdFields.stream()
                .filter(f -> f.isRequired() && autoMapped.containsKey(f.getName()))
                .count();
        if (requiredTotal == 0) {
            // Pas de champs obligatoires : score basÃ© sur tous les champs
            return (int) Math.round((double) autoMapped.size() / xsdFields.size() * 100);
        }
        return (int) Math.round((double) requiredMapped / requiredTotal * 100);
    }

    private String buildSummary(List<XsdFieldInfo> xsdFields,
                                Map<String, String> autoMapped,
                                int score) {
        long required = xsdFields.stream().filter(XsdFieldInfo::isRequired).count();
        long requiredMapped = xsdFields.stream()
                .filter(f -> f.isRequired() && autoMapped.containsKey(f.getName()))
                .count();

        if (score >= 80) {
            return String.format("Excellente compatibilitÃ© â€” %d/%d champs obligatoires mappÃ©s automatiquement.",
                    requiredMapped, required);
        } else if (score >= 50) {
            return String.format("CompatibilitÃ© partielle â€” %d/%d champs obligatoires mappÃ©s. VÃ©rifiez les champs manquants.",
                    requiredMapped, required);
        } else {
            return String.format("Faible compatibilitÃ© â€” seulement %d/%d champs obligatoires mappÃ©s. Configurez les mappings manuellement.",
                    requiredMapped, required);
        }
    }
    // UTILITAIRES
    /** Normalise un nom de champ pour la comparaison : minuscule, sans sÃ©parateurs. */
    private String normalize(String name) {
        if (name == null) return "";
        return name.toLowerCase()
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");
    }

    /** Supprime le prÃ©fixe d'espace de noms (ex: xs:string â†’ string). */
    private String stripNsPrefix(String typeName) {
        if (typeName == null) return "";
        int colon = typeName.lastIndexOf(':');
        return colon >= 0 ? typeName.substring(colon + 1) : typeName;
    }

    /** RÃ©sout les types XSD simples en noms lisibles. */
    private String resolveSimpleTypeName(String rawType) {
        String t = stripNsPrefix(rawType);
        switch (t) {
            case "string":          return "string";
            case "integer":
            case "int":
            case "long":            return "integer";
            case "decimal":
            case "float":
            case "double":          return "decimal";
            case "date":            return "date";
            case "dateTime":        return "dateTime";
            case "boolean":         return "boolean";
            default:                return t.isEmpty() ? "string" : t;
        }
    }

    /** RÃ©cupÃ¨re le premier Ã©lÃ©ment enfant direct d'un localName donnÃ©. */
    private Element getFirstChildElement(Element parent, String localName) {
        if (parent == null || localName == null) return null;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                Element el = (Element) children.item(i);
                if (localName.equals(el.getLocalName())) {
                    return el;
                }
            }
        }
        return null;
    }

    private int parseIntSafe(String s, int defaultVal) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return defaultVal; }
    }
}
