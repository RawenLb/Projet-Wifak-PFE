package com.example.bctbackend.service;

import com.example.bctbackend.entities.DeclarationTemplate;
import com.example.bctbackend.repositories.DeclarationTemplateRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TemplateService {

    private final DeclarationTemplateRepository templateRepository;

    public TemplateService(DeclarationTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    /**
     * ✅ Générer fichier à partir du template et des données
     *
     * @param declarationTypeId - ID du type de déclaration
     * @param data - Map contenant les variables et leurs valeurs (ex: {"CODE": "BCT_01", "MONTANT": "5000"})
     * @return String - Contenu du fichier généré (XML/TXT/CSV)
     */
    public String generateFile(Long declarationTypeId, Map<String, String> data) {
        DeclarationTemplate template = templateRepository
                .findByDeclarationTypeId(declarationTypeId)
                .orElseThrow(() -> new RuntimeException("Template introuvable pour le type de déclaration ID: " + declarationTypeId));

        String content = template.getTemplateContent();

        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("Le template est vide pour le type de déclaration ID: " + declarationTypeId);
        }

        // ✅ 1. Extraire toutes les variables requises du template
        List<String> requiredVariables = extractVariablesFromTemplate(content);

        // ✅ 2. Vérifier que toutes les variables nécessaires sont fournies
        List<String> missingVariables = new ArrayList<>();
        for (String var : requiredVariables) {
            if (!data.containsKey(var) || data.get(var) == null) {
                missingVariables.add(var);
            }
        }

        if (!missingVariables.isEmpty()) {
            throw new RuntimeException("❌ Variables manquantes dans les données: " + String.join(", ", missingVariables));
        }

        // ✅ 3. Remplacer toutes les variables par leurs valeurs
        for (Map.Entry<String, String> entry : data.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            content = content.replace(placeholder, value);
        }

        // ✅ 4. Vérifier qu'il ne reste plus de variables non remplacées
        List<String> remainingVariables = extractVariablesFromTemplate(content);
        if (!remainingVariables.isEmpty()) {
            throw new RuntimeException("⚠️ Variables non remplacées dans le template: " + String.join(", ", remainingVariables));
        }

        return content;
    }

    /**
     * ✅ Extraire toutes les variables du template (format {{VARIABLE}})
     *
     * @param template - Contenu du template
     * @return List<String> - Liste des noms de variables trouvées
     */
    private List<String> extractVariablesFromTemplate(String template) {
        List<String> variables = new ArrayList<>();

        // Pattern pour capturer {{VARIABLE_NAME}}
        Pattern pattern = Pattern.compile("\\{\\{([A-Z_0-9]+)\\}\\}");
        Matcher matcher = pattern.matcher(template);

        while (matcher.find()) {
            String varName = matcher.group(1);
            if (!variables.contains(varName)) {
                variables.add(varName);
            }
        }

        return variables;
    }

    /**
     * ✅ Valider que les données fournies correspondent au template
     *
     * @param declarationTypeId - ID du type de déclaration
     * @param data - Map des données à valider
     * @return boolean - true si valide, sinon exception
     */
    public boolean validateTemplateData(Long declarationTypeId, Map<String, String> data) {
        DeclarationTemplate template = templateRepository
                .findByDeclarationTypeId(declarationTypeId)
                .orElseThrow(() -> new RuntimeException("Template introuvable pour validation"));

        String content = template.getTemplateContent();

        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("Le template est vide");
        }

        // ✅ Extraire les variables requises
        List<String> requiredVariables = extractVariablesFromTemplate(content);

        // ✅ Vérifier que toutes les variables requises sont présentes
        List<String> missingVariables = new ArrayList<>();
        for (String var : requiredVariables) {
            if (!data.containsKey(var) || data.get(var) == null || data.get(var).trim().isEmpty()) {
                missingVariables.add(var);
            }
        }

        if (!missingVariables.isEmpty()) {
            throw new RuntimeException("Variables manquantes ou vides: " + String.join(", ", missingVariables));
        }

        // ✅ Vérifier qu'il n'y a pas de variables en trop dans les données
        List<String> extraVariables = new ArrayList<>();
        for (String dataKey : data.keySet()) {
            if (!requiredVariables.contains(dataKey)) {
                extraVariables.add(dataKey);
            }
        }

        if (!extraVariables.isEmpty()) {
            // Juste un warning, pas bloquant
            System.out.println("⚠️ Variables fournies mais non utilisées dans le template: " + String.join(", ", extraVariables));
        }

        return true;
    }

    /**
     * ✅ Obtenir la liste des variables requises pour un type de déclaration
     *
     * @param declarationTypeId - ID du type de déclaration
     * @return List<String> - Liste des variables requises
     */
    public List<String> getRequiredVariables(Long declarationTypeId) {
        DeclarationTemplate template = templateRepository
                .findByDeclarationTypeId(declarationTypeId)
                .orElseThrow(() -> new RuntimeException("Template introuvable"));

        String content = template.getTemplateContent();

        if (content == null || content.trim().isEmpty()) {
            return new ArrayList<>();
        }

        return extractVariablesFromTemplate(content);
    }

    /**
     * ✅ Prévisualiser le template avec des données exemple
     *
     * @param declarationTypeId - ID du type de déclaration
     * @return String - Template avec des données exemple
     */
    public String previewTemplate(Long declarationTypeId) {
        DeclarationTemplate template = templateRepository
                .findByDeclarationTypeId(declarationTypeId)
                .orElseThrow(() -> new RuntimeException("Template introuvable"));

        String content = template.getTemplateContent();
        List<String> variables = extractVariablesFromTemplate(content);

        // Remplacer chaque variable par un exemple
        for (String var : variables) {
            String placeholder = "{{" + var + "}}";
            String exampleValue = "[EXEMPLE_" + var + "]";
            content = content.replace(placeholder, exampleValue);
        }

        return content;
    }
}