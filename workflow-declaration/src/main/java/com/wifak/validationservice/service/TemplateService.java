package com.wifak.validationservice.service;

import com.wifak.validationservice.entities.DeclarationTemplate;
import com.wifak.validationservice.repositories.DeclarationTemplateRepository;
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

    public String getFileExtension(Long declarationTypeId) {
        DeclarationTemplate template = templateRepository.findByDeclarationTypeId(declarationTypeId)
                .orElseThrow(() -> new RuntimeException("Template introuvable pour l'ID: " + declarationTypeId));
        return template.getDeclarationType().getFormat().name().toLowerCase();
    }

    public String generateFile(Long declarationTypeId, Map<String, String> data) {
        DeclarationTemplate template = templateRepository.findByDeclarationTypeId(declarationTypeId)
                .orElseThrow(() -> new RuntimeException("Template introuvable"));
        String content = template.getTemplateContent();
        if (content == null || content.trim().isEmpty())
            throw new RuntimeException("Le contenu du template est vide");

        for (String var : extractVariablesFromTemplate(content)) {
            content = content.replace("{{" + var + "}}", data.getOrDefault(var, ""));
        }
        return content;
    }

    public boolean validateTemplateData(Long declarationTypeId, Map<String, String> data) {
        List<String> missing = new ArrayList<>();
        for (String var : getRequiredVariables(declarationTypeId)) {
            if (!data.containsKey(var) || data.get(var) == null || data.get(var).trim().isEmpty())
                missing.add(var);
        }
        if (!missing.isEmpty())
            throw new RuntimeException("Champs obligatoires manquants: " + String.join(", ", missing));
        return true;
    }

    private List<String> extractVariablesFromTemplate(String template) {
        List<String> variables = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\{\\{([A-Za-z0-9_]+)\\}\\}").matcher(template);
        while (matcher.find()) {
            String varName = matcher.group(1);
            if (!variables.contains(varName)) variables.add(varName);
        }
        return variables;
    }

    public List<String> getRequiredVariables(Long declarationTypeId) {
        DeclarationTemplate template = templateRepository.findByDeclarationTypeId(declarationTypeId)
                .orElseThrow(() -> new RuntimeException("Template introuvable"));
        return extractVariablesFromTemplate(template.getTemplateContent());
    }

    public String previewTemplate(Long declarationTypeId) {
        DeclarationTemplate template = templateRepository.findByDeclarationTypeId(declarationTypeId)
                .orElseThrow(() -> new RuntimeException("Template introuvable"));
        String content = template.getTemplateContent();
        for (String var : extractVariablesFromTemplate(content))
            content = content.replace("{{" + var + "}}", "[VALEUR_" + var + "]");
        return content;
    }
}
