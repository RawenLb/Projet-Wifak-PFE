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
     * ✅ Récupérer l'extension dynamique (csv, xml, etc.) depuis la DB
     */
    public String getFileExtension(Long declarationTypeId) {
        DeclarationTemplate template = templateRepository
                .findByDeclarationTypeId(declarationTypeId)
                .orElseThrow(() -> new RuntimeException("Template introuvable pour l'ID: " + declarationTypeId));

        // On récupère le format défini dans DeclarationType (ex: CSV -> "csv")
        return template.getDeclarationType().getFormat().name().toLowerCase();
    }

    /**
     * ✅ Générer le contenu du fichier
     */
    public String generateFile(Long declarationTypeId, Map<String, String> data) {
        DeclarationTemplate template = templateRepository
                .findByDeclarationTypeId(declarationTypeId)
                .orElseThrow(() -> new RuntimeException("Template introuvable"));

        String content = template.getTemplateContent();

        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("Le contenu du template est vide");
        }

        // 1. Extraire les variables {{VAR}}
        List<String> requiredVariables = extractVariablesFromTemplate(content);

        // 2. Remplacer les variables par les données reçues
        for (String var : requiredVariables) {
            String placeholder = "{{" + var + "}}";
            String value = data.getOrDefault(var, "");

            // Si la variable est obligatoire mais vide dans les data
            if (value == null || value.trim().isEmpty()) {
                // Optionnel: on peut soit laisser vide, soit lever une erreur
                value = "";
            }
            content = content.replace(placeholder, value);
        }

        return content;
    }

    /**
     * ✅ Validation stricte des données
     */
    public boolean validateTemplateData(Long declarationTypeId, Map<String, String> data) {
        List<String> requiredVariables = getRequiredVariables(declarationTypeId);
        List<String> missingVariables = new ArrayList<>();

        for (String var : requiredVariables) {
            if (!data.containsKey(var) || data.get(var) == null || data.get(var).trim().isEmpty()) {
                missingVariables.add(var);
            }
        }

        if (!missingVariables.isEmpty()) {
            throw new RuntimeException("Champs obligatoires manquants: " + String.join(", ", missingVariables));
        }

        return true;
    }

    /**
     * ✅ Extraction via Regex
     */
    private List<String> extractVariablesFromTemplate(String template) {
        List<String> variables = new ArrayList<>();
        // Accepte les lettres, chiffres et underscores
        Pattern pattern = Pattern.compile("\\{\\{([A-Za-z0-9_]+)\\}\\}");
        Matcher matcher = pattern.matcher(template);

        while (matcher.find()) {
            String varName = matcher.group(1);
            if (!variables.contains(varName)) {
                variables.add(varName);
            }
        }
        return variables;
    }

    public List<String> getRequiredVariables(Long declarationTypeId) {
        DeclarationTemplate template = templateRepository
                .findByDeclarationTypeId(declarationTypeId)
                .orElseThrow(() -> new RuntimeException("Template introuvable"));

        return extractVariablesFromTemplate(template.getTemplateContent());
    }

    public String previewTemplate(Long declarationTypeId) {
        DeclarationTemplate template = templateRepository
                .findByDeclarationTypeId(declarationTypeId)
                .orElseThrow(() -> new RuntimeException("Template introuvable"));

        String content = template.getTemplateContent();
        List<String> variables = extractVariablesFromTemplate(content);

        for (String var : variables) {
            content = content.replace("{{" + var + "}}", "[VALEUR_" + var + "]");
        }
        return content;
    }
}