package com.wifak.validationservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifak.validationservice.dto.AiValidationResult;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OllamaService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String MODEL_NAME = "qwen2.5:3b";

    // Métadonnées interdites (présence hors <Ligne> entraîne rejet)
    private static final List<String> FORBIDDEN_METADATA = Arrays.asList(
            "id", "nom_fichier", "statut", "date_generation", "genere_par",
            "date_envoi", "frequence", "format"
    );

    // Taux de provision par classe de risque
    private static final Map<String, Double> TAUX_PROVISION = Map.of(
            "A", 0.05,   // 5%
            "B", 0.10,   // 10%
            "C", 0.20,   // 20%
            "D", 0.50    // 50%
    );

    private final RestTemplate restTemplate;

    public OllamaService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(120_000);
        this.restTemplate = new RestTemplate(factory);
    }

    @PostConstruct
    public void warmUp() {
        try {
            Map<String, Object> req = new HashMap<>();
            req.put("model", MODEL_NAME);
            req.put("prompt", "ping");
            req.put("stream", false);
            restTemplate.postForObject(OLLAMA_URL, req, String.class);
            log.info("🔥 Ollama ready");
        } catch (Exception e) {
            log.warn("Warmup failed: {}", e.getMessage());
        }
    }

    public AiValidationResult analyzeDeclaration(String contenu, String nomFichier) {
        log.info("🤖 Analyse fichier: {}", nomFichier);
        String format = detectFormat(nomFichier, contenu);
        log.info("📄 Format détecté: {}", format);

        // 1. Structure élémentaire
        List<String> errors = validateStructure(contenu, format);
        if (!errors.isEmpty()) return reject(errors);

        // 2. Métadonnées interdites (hors blocs de données)
        errors = checkForbiddenMetadata(contenu, format);
        if (!errors.isEmpty()) return reject(errors);

        // 3. Champs vides (balises auto-fermantes ou paires vides)
        errors = checkEmptyFields(contenu, format);
        if (!errors.isEmpty()) return reject(errors);

        // 4. Règles métier fortes (dates, provisions, impayés)
        errors = validateBusinessRules(contenu, format);
        if (!errors.isEmpty()) return reject(errors);

        // 5. Toutes les validations automatiques sont passées → appel IA pour notation fine
        return callAI(contenu, nomFichier, format);
    }

    // --------------------------------------------------------------
    // Détection du format
    // --------------------------------------------------------------
    private String detectFormat(String nomFichier, String contenu) {
        if (nomFichier != null) {
            String lower = nomFichier.toLowerCase();
            if (lower.endsWith(".xml")) return "XML";
            if (lower.endsWith(".csv")) return "CSV";
            if (lower.endsWith(".txt")) return "TXT";
        }
        String trimmed = contenu.trim();
        if (trimmed.startsWith("<")) return "XML";
        if (trimmed.contains(",") && trimmed.contains("\n")) return "CSV";
        return "TXT";
    }

    // --------------------------------------------------------------
    // Validation structurelle (XML bien formé, CSV non vide...)
    // --------------------------------------------------------------
    private List<String> validateStructure(String contenu, String format) {
        List<String> errors = new ArrayList<>();
        if (contenu == null || contenu.trim().isEmpty()) {
            errors.add("Fichier vide");
            return errors;
        }
        switch (format) {
            case "XML" -> {
                if (!isWellFormedXml(contenu))
                    errors.add("XML malformé (syntaxe invalide)");
            }
            case "CSV" -> {
                String[] lines = contenu.split("\n");
                if (lines.length < 2)
                    errors.add("CSV invalide : en-tête + au moins une ligne de données requis");
            }
            case "TXT" -> {
                if (contenu.trim().length() < 5)
                    errors.add("Fichier TXT trop court ou vide");
            }
        }
        return errors;
    }

    private boolean isWellFormedXml(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // --------------------------------------------------------------
    // Métadonnées interdites (hors blocs <Ligne>/<Donnees>)
    // --------------------------------------------------------------
    private List<String> checkForbiddenMetadata(String contenu, String format) {
        List<String> found = new ArrayList<>();
        switch (format) {
            case "XML" -> {
                for (String meta : FORBIDDEN_METADATA) {
                    Pattern p = Pattern.compile("<" + meta + "\\b[^>]*>", Pattern.CASE_INSENSITIVE);
                    Matcher m = p.matcher(contenu);
                    while (m.find()) {
                        int start = m.start();
                        String before = contenu.substring(0, start);
                        int lastLigneOpen = before.lastIndexOf("<Ligne");
                        int lastDonneesOpen = before.lastIndexOf("<Donnees");
                        int lastLigneClose = before.lastIndexOf("</Ligne>");
                        int lastDonneesClose = before.lastIndexOf("</Donnees>");
                        boolean insideData = (lastLigneOpen > lastLigneClose) || (lastDonneesOpen > lastDonneesClose);
                        if (!insideData) {
                            found.add(meta);
                            break;
                        }
                    }
                }
            }
            case "CSV" -> {
                String[] lines = contenu.split("\n");
                if (lines.length > 0) {
                    String header = lines[0].toLowerCase();
                    for (String meta : FORBIDDEN_METADATA) {
                        if (header.contains(meta))
                            found.add(meta);
                    }
                }
            }
            case "TXT" -> {
                String lower = contenu.toLowerCase();
                for (String meta : FORBIDDEN_METADATA) {
                    if (lower.contains(meta))
                        found.add(meta);
                }
            }
        }
        return found;
    }

    // --------------------------------------------------------------
    // Champs vides (balises auto-fermantes ou paires vides)
    // --------------------------------------------------------------
    private List<String> checkEmptyFields(String contenu, String format) {
        List<String> empty = new ArrayList<>();
        switch (format) {
            case "XML" -> {
                Pattern selfClosing = Pattern.compile("<([a-zA-Z_][\\w\\-]*)[^>]*?/>");
                Matcher m1 = selfClosing.matcher(contenu);
                while (m1.find())
                    empty.add("balise auto-fermante : <" + m1.group(1) + "/>");

                Pattern emptyPair = Pattern.compile("<([a-zA-Z_][\\w\\-]*)[^>]*>\\s*</\\1>");
                Matcher m2 = emptyPair.matcher(contenu);
                while (m2.find())
                    empty.add("balise vide : <" + m2.group(1) + "></" + m2.group(1) + ">");
            }
            case "CSV" -> {
                String[] lines = contenu.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    String[] cells = lines[i].split(",", -1);
                    for (int j = 0; j < cells.length; j++) {
                        if (cells[j].trim().isEmpty())
                            empty.add("ligne " + (i+1) + ", colonne " + (j+1) + " vide");
                    }
                }
            }
            case "TXT" -> {
                String[] lines = contenu.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    if (lines[i].trim().isEmpty())
                        empty.add("ligne " + (i+1) + " vide");
                }
            }
        }
        return empty;
    }

    // --------------------------------------------------------------
    // Règles métier avancées (dates, provisions, impayés)
    // --------------------------------------------------------------
    private List<String> validateBusinessRules(String contenu, String format) {
        List<String> errors = new ArrayList<>();
        if (!"XML".equals(format)) return errors;

        // 1. Présence des attributs/balises essentiels (code, période, dates)
        Map<String, String> essential = extractEssentialFields(contenu);
        for (String key : Arrays.asList("code", "periode", "date_debut", "date_fin")) {
            if (!essential.containsKey(key) || essential.get(key).isBlank())
                errors.add("Balise ou attribut essentiel manquant ou vide : " + key);
        }
        if (!errors.isEmpty()) return errors;

        // 2. Cohérence des dates
        try {
            LocalDate dateDebut = LocalDate.parse(essential.get("date_debut"));
            LocalDate dateFin   = LocalDate.parse(essential.get("date_fin"));
            if (dateDebut.isAfter(dateFin))
                errors.add("date_debut > date_fin");

            String periode = essential.get("periode");
            if (periode != null && periode.matches("\\d{4}-\\d{2}")) {
                LocalDate expectedStart = LocalDate.parse(periode + "-01");
                if (!dateDebut.equals(expectedStart))
                    errors.add("date_debut ne correspond pas à la période");
                int lastDay = expectedStart.lengthOfMonth();
                LocalDate expectedEnd = LocalDate.parse(periode + "-" + lastDay);
                if (!dateFin.equals(expectedEnd))
                    errors.add("date_fin ne correspond pas à la fin de la période");
            } else {
                errors.add("Période invalide : " + periode);
            }
        } catch (Exception e) {
            errors.add("Erreur de format de date : " + e.getMessage());
        }

        // 3. Validation des lignes (MontantImpaye ≤ MontantCredit, Provision suffisante)
        List<Map<String, String>> lignes = extractLignes(contenu);
        int idx = 0;
        for (Map<String, String> ligne : lignes) {
            idx++;
            String id = ligne.getOrDefault("IdClient", ligne.getOrDefault("idClient", "?"));
            Double credit = parseDouble(ligne.get("MontantCredit"));
            Double impaye = parseDouble(ligne.get("MontantImpaye"));
            Double provision = parseDouble(ligne.get("Provision"));
            String classe = ligne.get("ClasseRisque");

            if (credit != null && impaye != null && impaye > credit + 0.01) {
                errors.add(String.format("Ligne %d (%s) : MontantImpaye (%.2f) > MontantCredit (%.2f)",
                        idx, id, impaye, credit));
            }

            if (classe != null && impaye != null && provision != null) {
                Double taux = TAUX_PROVISION.get(classe.toUpperCase());
                if (taux != null) {
                    double mini = impaye * taux;
                    if (provision < mini - 0.01) {
                        errors.add(String.format("Ligne %d (%s) : Provision (%.2f) insuffisante pour classe %s (minimum %.2f = %.2f * %.0f%%)",
                                idx, id, provision, classe, mini, impaye, taux * 100));
                    }
                }
            }
        }
        return errors;
    }

    private Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.parseDouble(s.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, String> extractEssentialFields(String xml) {
        Map<String, String> result = new HashMap<>();

        // Attributs de la racine
        Pattern rootAttr = Pattern.compile("<(?![?!/])\\w+\\s+([^>]*)>", Pattern.DOTALL);
        Matcher rm = rootAttr.matcher(xml);
        if (rm.find()) {
            Pattern attr = Pattern.compile("(\\w+)\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
            Matcher am = attr.matcher(rm.group(1));
            while (am.find()) {
                String name = am.group(1).toLowerCase();
                String val = am.group(2);
                switch (name) {
                    case "code" -> result.put("code", val);
                    case "periode" -> result.put("periode", val);
                    case "datedebut" -> result.put("date_debut", val);
                    case "datefin" -> result.put("date_fin", val);
                }
            }
        }

        // Balises (hors <Ligne>) : code, periode, date_debut, date_fin
        for (String tag : Arrays.asList("code", "periode", "date_debut", "date_fin")) {
            if (!result.containsKey(tag)) {
                String value = extractTagOutsideLignes(xml, tag);
                if (value != null) result.put(tag, value);
            }
        }
        return result;
    }

    private String extractTagOutsideLignes(String xml, String tagName) {
        List<String> variants = switch (tagName) {
            case "code" -> Arrays.asList("code", "CodeDeclaration", "codeDeclaration", "CODE");
            case "periode" -> Arrays.asList("periode", "Periode", "PERIODE");
            case "date_debut" -> Arrays.asList("date_debut", "DateDebut", "dateDebut", "DATE_DEBUT");
            case "date_fin" -> Arrays.asList("date_fin", "DateFin", "dateFin", "DATE_FIN");
            default -> List.of(tagName);
        };
        for (String variant : variants) {
            Pattern p = Pattern.compile("<" + variant + "\\b[^>]*>(.*?)</" + variant + ">", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(xml);
            while (m.find()) {
                int start = m.start();
                String before = xml.substring(0, start);
                int lastLigneOpen = before.lastIndexOf("<Ligne");
                int lastLigneClose = before.lastIndexOf("</Ligne>");
                if (lastLigneOpen > lastLigneClose) continue; // à l'intérieur d'une ligne
                String val = m.group(1).trim();
                if (!val.isEmpty()) return val;
            }
        }
        return null;
    }

    private List<Map<String, String>> extractLignes(String xml) {
        List<Map<String, String>> lignes = new ArrayList<>();
        Pattern lignePattern = Pattern.compile("<Ligne\\b[^>]*>(.*?)</Ligne>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher m = lignePattern.matcher(xml);
        while (m.find()) {
            String content = m.group(1);
            Map<String, String> fields = new HashMap<>();
            Pattern tagPattern = Pattern.compile("<([a-zA-Z_][\\w\\-]*)\\b[^>]*>(.*?)</\\1>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher tm = tagPattern.matcher(content);
            while (tm.find()) {
                fields.put(tm.group(1), tm.group(2).trim());
            }
            lignes.add(fields);
        }
        return lignes;
    }

    // --------------------------------------------------------------
    // Appel IA (uniquement si toutes les règles métier sont satisfaites)
    // --------------------------------------------------------------
    private AiValidationResult callAI(String contenu, String nomFichier, String format) {
        String prompt = buildPrompt(contenu, nomFichier, format);
        Map<String, Object> request = new HashMap<>();
        request.put("model", MODEL_NAME);
        request.put("prompt", prompt);
        request.put("stream", false);
        request.put("options", Map.of("temperature", 0.0, "num_predict", 300, "top_p", 1.0));

        try {
            String raw = restTemplate.postForObject(OLLAMA_URL, request, String.class);
            String jsonText = extractResponse(raw);
            String cleanJson = extractJson(jsonText);
            log.debug("📦 JSON IA: {}", cleanJson);
            AiValidationResult result = objectMapper.readValue(cleanJson, AiValidationResult.class);
            // Forcer la cohérence score <-> valid/recommendation
            int threshold = switch (format) {
                case "XML" -> 70;
                case "CSV" -> 65;
                default -> 60;
            };
            boolean expectedValid = result.getScore() >= threshold;
            if (result.isValid() != expectedValid) {
                log.warn("Correction valid: {} -> {}", result.isValid(), expectedValid);
                result.setValid(expectedValid);
            }
            String expectedRecommendation = expectedValid ? "VALIDATE" : "REJECT";
            if (!expectedRecommendation.equals(result.getRecommendation())) {
                log.warn("Correction recommendation: {} -> {}", result.getRecommendation(), expectedRecommendation);
                result.setRecommendation(expectedRecommendation);
            }
            return result;
        } catch (Exception e) {
            log.error("❌ Erreur IA: {}", e.getMessage());
            // Fallback : fichier déjà valide selon règles métier
            AiValidationResult fallback = new AiValidationResult();
            fallback.setValid(true);
            fallback.setScore(85);
            fallback.setRecommendation("VALIDATE");
            fallback.setAnomalies(List.of("Analyse IA indisponible, validation basée sur règles métier"));
            return fallback;
        }
    }

    private String buildPrompt(String contenu, String nomFichier, String format) {
        String contenuTronque = contenu.length() > 2000 ? contenu.substring(0, 2000) + "\n...[tronqué]" : contenu;
        int threshold = switch (format) {
            case "XML" -> 70;
            case "CSV" -> 65;
            default -> 60;
        };
        String specific = switch (format) {
            case "XML" -> "Les règles de provision et d'impayé sont déjà vérifiées. Donne un score sur la qualité globale (ex: cohérence des montants, clarté).";
            case "CSV" -> "Vérifie la cohérence des colonnes, l'absence de cellules vides, la validité des nombres.";
            default -> "Vérifie la présence d'informations financières cohérentes.";
        };
        return """
            Tu es un expert BCT. Réponds uniquement par JSON.
            %s
            Fichier: %s
            Contenu: %s
            Seuil: %d
            Format JSON: {"valid": bool, "score": int, "anomalies": [string], "recommendation": "VALIDATE/REJECT"}
            Exemple correct: {"valid": true, "score": 85, "anomalies": [], "recommendation": "VALIDATE"}
            """.formatted(specific, nomFichier, contenuTronque, threshold);
    }

    private String extractResponse(String raw) {
        try {
            Map<?, ?> map = objectMapper.readValue(raw, Map.class);
            Object resp = map.get("response");
            return resp != null ? resp.toString() : raw;
        } catch (Exception e) {
            return raw;
        }
    }

    private String extractJson(String text) {
        if (text == null || text.isBlank()) throw new RuntimeException("Réponse IA vide");
        text = text.trim();
        text = text.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").trim();
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start == -1 || end == -1 || end <= start)
            throw new RuntimeException("JSON invalide reçu: " + text.substring(0, Math.min(200, text.length())));
        return text.substring(start, end + 1);
    }

    private AiValidationResult reject(List<String> anomalies) {
        AiValidationResult r = new AiValidationResult();
        r.setValid(false);
        r.setScore(0);
        r.setRecommendation("REJECT");
        r.setAnomalies(anomalies);
        return r;
    }
}