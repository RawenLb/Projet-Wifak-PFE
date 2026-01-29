package com.example.bctbackend.service;

import com.example.bctbackend.entities.DeclarationType;
import com.example.bctbackend.entities.ValidationRule;
import com.example.bctbackend.repositories.DeclarationTypeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;

@Service
public class DeclarationTypeService {

    private final DeclarationTypeRepository repository;
    private final ObjectMapper objectMapper;

    public DeclarationTypeService(DeclarationTypeRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    /**
     * ✅ Créer un nouveau type de déclaration avec validations complètes
     */
    @Transactional
    public DeclarationType create(DeclarationType declarationType) {
        // ✅ 1. Vérifier l'unicité du code
        if (repository.findByCode(declarationType.getCode()).isPresent()) {
            throw new RuntimeException("❌ Un type de déclaration avec ce code existe déjà: " + declarationType.getCode());
        }

        // ✅ 2. Valider le code (format: lettres majuscules, chiffres et underscore)
        if (!declarationType.getCode().matches("^[A-Z0-9_]+$")) {
            throw new RuntimeException("❌ Le code doit contenir uniquement des lettres majuscules, chiffres et underscores");
        }

        // ✅ 3. Valider le template si présent
        if (declarationType.getTemplate() != null) {
            validateTemplate(declarationType);
        }

        // ✅ 4. Définir les métadonnées
        declarationType.setDateCreation(LocalDateTime.now());
        declarationType.setDerniereModification(LocalDateTime.now());
        declarationType.setCreePar(getCurrentUsername());
        declarationType.setModifiePar(getCurrentUsername());

        // ✅ 5. Gérer les règles de validation
        if (declarationType.getValidationRules() != null) {
            declarationType.getValidationRules().forEach(rule ->
                    rule.setDeclarationType(declarationType)
            );
        }

        // ✅ 6. Gérer le template
        if (declarationType.getTemplate() != null) {
            declarationType.getTemplate().setDeclarationType(declarationType);
        }

        return repository.save(declarationType);
    }

    /**
     * ✅ Lister tous les types de déclaration
     */
    public List<DeclarationType> getAll() {
        return repository.findAll();
    }

    /**
     * ✅ Récupérer un type par ID
     */
    public DeclarationType getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Type de déclaration introuvable avec ID: " + id));
    }

    /**
     * ✅ Mettre à jour un type de déclaration
     */
    @Transactional
    public DeclarationType update(Long id, DeclarationType updated) {
        DeclarationType existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Type de déclaration introuvable"));

        // ✅ Vérifier que le code n'est pas déjà utilisé par un autre type
        if (!existing.getCode().equals(updated.getCode())) {
            if (repository.findByCode(updated.getCode()).isPresent()) {
                throw new RuntimeException("❌ Un autre type de déclaration utilise déjà ce code: " + updated.getCode());
            }
        }

        // ✅ Valider le template si modifié
        if (updated.getTemplate() != null) {
            validateTemplate(updated);
        }

        // ✅ Mettre à jour les champs
        existing.setCode(updated.getCode());
        existing.setNom(updated.getNom());
        existing.setDescription(updated.getDescription());
        existing.setFormat(updated.getFormat());
        existing.setFrequence(updated.getFrequence());
        existing.setDateLimite(updated.getDateLimite());
        existing.setActif(updated.isActif());
        existing.setChampsObligatoires(updated.getChampsObligatoires());

        // ✅ Mettre à jour le template
        if (updated.getTemplate() != null) {
            if (existing.getTemplate() == null) {
                existing.setTemplate(updated.getTemplate());
                updated.getTemplate().setDeclarationType(existing);
            } else {
                existing.getTemplate().setTemplateContent(updated.getTemplate().getTemplateContent());
                existing.getTemplate().setVariablesDisponibles(updated.getTemplate().getVariablesDisponibles());
            }
        }

        // ✅ Mettre à jour les règles de validation
        if (updated.getValidationRules() != null) {
            // Supprimer les anciennes règles
            existing.getValidationRules().clear();

            // Ajouter les nouvelles règles
            updated.getValidationRules().forEach(rule -> {
                rule.setDeclarationType(existing);
                existing.getValidationRules().add(rule);
            });
        }

        existing.setDerniereModification(LocalDateTime.now());
        existing.setModifiePar(getCurrentUsername());

        return repository.save(existing);
    }

    /**
     * ✅ Supprimer un type de déclaration
     */
    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Type de déclaration introuvable avec ID: " + id);
        }

        // TODO: Vérifier qu'aucune déclaration n'utilise ce type avant de supprimer
        // Exemple: if (declarationRepository.existsByDeclarationTypeId(id)) {
        //     throw new RuntimeException("Impossible de supprimer: des déclarations utilisent ce type");
        // }

        repository.deleteById(id);
    }

    /**
     * ✅ Activer/désactiver un type de déclaration
     */
    @Transactional
    public DeclarationType toggleStatus(Long id) {
        DeclarationType declarationType = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Type de déclaration introuvable"));

        declarationType.setActif(!declarationType.isActif());
        declarationType.setDerniereModification(LocalDateTime.now());
        declarationType.setModifiePar(getCurrentUsername());

        return repository.save(declarationType);
    }

    /**
     * ✅ Valider le template et les variables JSON
     */
    private void validateTemplate(DeclarationType declarationType) {
        if (declarationType.getTemplate() == null) {
            return;
        }

        String templateContent = declarationType.getTemplate().getTemplateContent();
        String variablesJson = declarationType.getTemplate().getVariablesDisponibles();

        // ✅ 1. Vérifier que le template n'est pas vide
        if (templateContent == null || templateContent.trim().isEmpty()) {
            throw new RuntimeException("❌ Le contenu du template ne peut pas être vide");
        }

        // ✅ 2. Valider le format JSON des variables
        if (variablesJson != null && !variablesJson.trim().isEmpty()) {
            try {
                objectMapper.readTree(variablesJson);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("❌ Format JSON invalide pour variablesDisponibles: " + e.getMessage());
            }
        }

        // ✅ 3. Extraire les variables du template
        List<String> templateVariables = extractVariablesFromTemplate(templateContent);

        // ✅ 4. Si des variables JSON sont définies, vérifier la cohérence
        if (variablesJson != null && !variablesJson.trim().isEmpty()) {
            try {
                var variablesMap = objectMapper.readValue(variablesJson, java.util.Map.class);

                // Vérifier que chaque variable JSON existe dans le template
                for (Object key : variablesMap.keySet()) {
                    String varName = key.toString();
                    if (!templateVariables.contains(varName)) {
                        System.out.println("⚠️ WARNING: Variable '" + varName + "' définie dans JSON mais absente du template");
                    }
                }

                // Vérifier que chaque variable du template est dans le JSON
                for (String templateVar : templateVariables) {
                    if (!variablesMap.containsKey(templateVar)) {
                        System.out.println("⚠️ WARNING: Variable '{{" + templateVar + "}}' présente dans le template mais absente du JSON");
                    }
                }

            } catch (JsonProcessingException e) {
                throw new RuntimeException("❌ Erreur lors de la validation des variables JSON: " + e.getMessage());
            }
        }

        // ✅ 5. Valider le format selon le type (XML, CSV, etc.)
        validateTemplateFormat(declarationType.getFormat(), templateContent);
    }

    /**
     * ✅ Extraire les variables du template (format {{VARIABLE}})
     */
    private List<String> extractVariablesFromTemplate(String template) {
        List<String> variables = new ArrayList<>();
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
     * ✅ Valider le format du template selon le type
     */
    private void validateTemplateFormat(DeclarationType.DeclarationFormat format, String templateContent) {
        switch (format) {
            case XML:
                // Vérifier que c'est du XML valide (basique)
                if (!templateContent.trim().startsWith("<") || !templateContent.trim().endsWith(">")) {
                    throw new RuntimeException("❌ Le template XML doit commencer par '<' et finir par '>'");
                }
                break;

            case JSON:
                // Vérifier que c'est du JSON valide après remplacement des variables
                String testJson = templateContent.replaceAll("\\{\\{[A-Z_0-9]+\\}\\}", "\"test\"");
                try {
                    objectMapper.readTree(testJson);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("❌ Le template JSON n'est pas valide: " + e.getMessage());
                }
                break;

            case CSV:
                // Vérifier qu'il y a au moins un séparateur (virgule ou point-virgule)
                if (!templateContent.contains(",") && !templateContent.contains(";")) {
                    throw new RuntimeException("❌ Le template CSV doit contenir des séparateurs (virgule ou point-virgule)");
                }
                break;

            case TXT:
            case PDF:
                // Pas de validation spécifique pour TXT et PDF
                break;
        }
    }

    /**
     * ✅ Récupérer les types actifs uniquement
     */
    public List<DeclarationType> getActiveTypes() {
        return repository.findAll().stream()
                .filter(DeclarationType::isActif)
                .toList();
    }

    /**
     * ✅ Rechercher par code
     */
    public DeclarationType findByCode(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Type de déclaration introuvable avec le code: " + code));
    }
}