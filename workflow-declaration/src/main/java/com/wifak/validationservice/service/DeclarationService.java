package com.wifak.validationservice.service;

import com.wifak.validationservice.dto.CreateTicketRequest;
import com.wifak.validationservice.dto.GenerateDeclarationRequest;
import com.wifak.validationservice.dto.XsdSqlMappingRequest;
import com.wifak.validationservice.entities.Declaration;
import com.wifak.validationservice.entities.DeclarationType;
import com.wifak.validationservice.feign.JiraIntegrationFeignClient;
import com.wifak.validationservice.repositories.DeclarationRepository;
import com.wifak.validationservice.repositories.DeclarationTypeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DeclarationService {

    private static final Logger log = LoggerFactory.getLogger(DeclarationService.class);

    private final DeclarationRepository      declarationRepository;
    private final DeclarationTypeRepository  typeRepository;
    private final XmlGenerationService       xmlGenerationService;
    private final CsvGenerationService       csvGenerationService;
    private final TxtGenerationService       txtGenerationService;
    private final JiraIntegrationFeignClient jiraClient;
    private final ObjectMapper               objectMapper;

    public DeclarationService(
            DeclarationRepository declarationRepository,
            DeclarationTypeRepository typeRepository,
            XmlGenerationService xmlGenerationService,
            CsvGenerationService csvGenerationService,
            TxtGenerationService txtGenerationService,
            JiraIntegrationFeignClient jiraClient,
            ObjectMapper objectMapper
    ) {
        this.declarationRepository = declarationRepository;
        this.typeRepository        = typeRepository;
        this.xmlGenerationService  = xmlGenerationService;
        this.csvGenerationService  = csvGenerationService;
        this.txtGenerationService  = txtGenerationService;
        this.jiraClient            = jiraClient;
        this.objectMapper          = objectMapper;
    }

    // ── Auth helper ────────────────────────────────────────────────
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    // ── Génération de contenu ──────────────────────────────────────

    /**
     * Génération SANS mapping (CSV, TXT, XML générique).
     */
    private String generateFileContent(DeclarationType type,
                                       LocalDate dateDebut, LocalDate dateFin,
                                       String periode) {
        switch (type.getFormat()) {
            case CSV:
                log.info("⚙️ Génération CSV via SQL...");
                return csvGenerationService.generateCsvFromSql(
                        type.getSqlQuery(), dateDebut, dateFin, type.getCode(), periode);
            case TXT:
                log.info("⚙️ Génération TXT via SQL...");
                return txtGenerationService.generateTxtFromSql(
                        type.getSqlQuery(), dateDebut, dateFin, type.getCode(), periode);
            case XML:
            default:
                log.info("⚙️ Génération XML générique (sans mapping)...");
                return xmlGenerationService.generateXmlFromXsdAndSql(
                        type.getXsdContent(), type.getSqlQuery(),
                        dateDebut, dateFin, type.getCode(), periode);
        }
    }

    /**
     * ✅ NOUVEAU — Génération XML AVEC mapping XSD ↔ SQL.
     * Les mappings JSON sont sérialisés et stockés dans la déclaration.
     */
    private String generateFileContentWithMapping(
            DeclarationType type,
            LocalDate dateDebut, LocalDate dateFin,
            String periode,
            List<XsdSqlMappingRequest.FieldMapping> mappings
    ) {
        if (type.getFormat() != DeclarationType.DeclarationFormat.XML) {
            // Pour CSV/TXT : mapping non applicable, génération standard
            return generateFileContent(type, dateDebut, dateFin, periode);
        }
        log.info("⚙️ Génération XML avec mapping ({} champs)...", mappings.size());
        return xmlGenerationService.generateXmlFromMapping(
                type.getXsdContent(), type.getSqlQuery(),
                dateDebut, dateFin, type.getCode(), periode,
                mappings
        );
    }

    // ── Résolution extension / nom de fichier ──────────────────────

    private String resolveExtension(DeclarationType.DeclarationFormat format) {
        switch (format) {
            case CSV: return "csv";
            case TXT: return "txt";
            default:  return "xml";
        }
    }

    private String buildFilename(String code, String periode, String extension) {
        String safePeriode = (periode != null) ? periode.replace("-", "") : "unknown";
        return String.format("declaration_%s_%s.%s", code, safePeriode, extension);
    }

    private void validateType(DeclarationType type) {
        if (!type.isActif()) {
            throw new RuntimeException("Ce type de déclaration est inactif : " + type.getCode());
        }
        if (type.getSqlQuery() == null || type.getSqlQuery().trim().isEmpty()) {
            throw new RuntimeException(
                    "La requête SQL n'est pas configurée pour le type « " + type.getCode() +
                            " ». Veuillez contacter l'administrateur.");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // GENERATE — sans mapping (CSV, TXT, XML générique)
    // ══════════════════════════════════════════════════════════════

    @Transactional
    public Declaration generateAndSave(Long typeId, String periode,
                                       LocalDate dateDebut, LocalDate dateFin) {
        log.info("🚀 Génération sans mapping — Type: {}, Période: {}", typeId, periode);

        DeclarationType type = typeRepository.findById(typeId)
                .orElseThrow(() -> new RuntimeException("Type introuvable: " + typeId));
        validateType(type);

        String fileContent   = generateFileContent(type, dateDebut, dateFin, periode);
        String fileExtension = resolveExtension(type.getFormat());
        String filename      = buildFilename(type.getCode(), periode, fileExtension);

        Declaration declaration = buildDeclaration(type, periode, dateDebut, dateFin, fileContent, filename);
        // Pas de mapping → mappingJson = null
        declaration.setMappingJson(null);

        Declaration saved = declarationRepository.save(declaration);
        log.info("✅ Déclaration sauvegardée — ID: {}, Fichier: {}", saved.getId(), filename);
        return saved;
    }

    @Transactional
    public Declaration patchContent(Long id, String newContent) {
        log.info("✏️ patchContent — ID: {}, taille: {} chars", id, newContent.length());

        Declaration declaration = findById(id);

        if (declaration.getStatut() != Declaration.DeclarationStatut.GENEREE &&
                declaration.getStatut() != Declaration.DeclarationStatut.REJETEE) {
            throw new RuntimeException(
                    "Impossible de modifier une déclaration au statut « " +
                            declaration.getStatut() + " »");
        }

        declaration.setContenuFichier(newContent);
        declaration.setDateGeneration(java.time.LocalDateTime.now());
        declaration.setGenerePar(getCurrentUsername());
        // Remettre en GENEREE après correction
        declaration.setStatut(Declaration.DeclarationStatut.GENEREE);
        declaration.setCommentaireRejet(null);
        declaration.setValidePar(null);
        declaration.setDateValidation(null);

        Declaration saved = declarationRepository.save(declaration);
        log.info("✅ Contenu patché — ID: {}", saved.getId());
        return saved;
    }


    // ══════════════════════════════════════════════════════════════
    // ✅ GENERATE WITH MAPPING — XML avec mapping XSD ↔ SQL
    // ══════════════════════════════════════════════════════════════

    @Transactional
    public Declaration generateAndSaveWithMapping(
            Long typeId,
            String periode,
            LocalDate dateDebut,
            LocalDate dateFin,
            List<XsdSqlMappingRequest.FieldMapping> mappings
    ) {
        log.info("🚀 Génération avec mapping — Type: {}, Période: {}, {} mappings",
                typeId, periode, mappings.size());

        DeclarationType type = typeRepository.findById(typeId)
                .orElseThrow(() -> new RuntimeException("Type introuvable: " + typeId));
        validateType(type);

        String fileContent   = generateFileContentWithMapping(type, dateDebut, dateFin, periode, mappings);
        String fileExtension = resolveExtension(type.getFormat());
        String filename      = buildFilename(type.getCode(), periode, fileExtension);

        Declaration declaration = buildDeclaration(type, periode, dateDebut, dateFin, fileContent, filename);

        // ✅ Sérialiser et stocker le mapping JSON pour traçabilité / ré-génération
        try {
            String mappingJson = objectMapper.writeValueAsString(mappings);
            declaration.setMappingJson(mappingJson);
            log.info("📋 Mapping JSON stocké ({} caractères)", mappingJson.length());
        } catch (Exception e) {
            log.warn("⚠️ Impossible de sérialiser le mapping: {}", e.getMessage());
        }

        Declaration saved = declarationRepository.save(declaration);
        log.info("✅ Déclaration avec mapping sauvegardée — ID: {}, Fichier: {}", saved.getId(), filename);
        return saved;
    }

    // ── Builder commun ─────────────────────────────────────────────

    private Declaration buildDeclaration(DeclarationType type, String periode,
                                         LocalDate dateDebut, LocalDate dateFin,
                                         String fileContent, String filename) {
        Declaration declaration = new Declaration();
        declaration.setDeclarationType(type);
        declaration.setPeriode(periode);
        declaration.setDateDebut(dateDebut);
        declaration.setDateFin(dateFin);
        declaration.setStatut(Declaration.DeclarationStatut.GENEREE);
        declaration.setContenuFichier(fileContent);
        declaration.setDateGeneration(LocalDateTime.now());
        declaration.setGenerePar(getCurrentUsername());
        declaration.setSqlQueryUsed(type.getSqlQuery());
        declaration.setXsdFileNameUsed(type.getXsdFileName());
        declaration.setNomFichier(filename);
        return declaration;
    }

    // ══════════════════════════════════════════════════════════════
    // NOTIFY JIRA
    // ══════════════════════════════════════════════════════════════

    public void notifyJiraTicketCreation(Long declarationId, String username) {
        try {
            jiraClient.createTicket(new CreateTicketRequest(declarationId, username));
            log.info("🎫 Ticket Jira créé pour déclaration {}", declarationId);
        } catch (Exception e) {
            log.warn("⚠️ Jira non disponible — ticket non créé: {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // QUERIES
    // ══════════════════════════════════════════════════════════════

    public List<Declaration> getMyDeclarations() {
        String username = getCurrentUsername();
        log.info("👤 getMyDeclarations — username='{}'", username);
        List<Declaration> list = declarationRepository.findByGenerePar(username);
        log.info("📋 {} déclaration(s) pour '{}'", list.size(), username);
        return list;
    }

    public List<Declaration> getAllDeclarations() {
        return declarationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Declaration findById(Long id) {
        return declarationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Déclaration introuvable: " + id));
    }

    // ══════════════════════════════════════════════════════════════
    // UPDATE
    // ══════════════════════════════════════════════════════════════

    @Transactional
    public Declaration updateDeclaration(Long id, GenerateDeclarationRequest request) {
        log.info("✏️ updateDeclaration — ID: {}", id);

        Declaration declaration = findById(id);

        if (declaration.getStatut() != Declaration.DeclarationStatut.GENEREE &&
                declaration.getStatut() != Declaration.DeclarationStatut.REJETEE) {
            throw new RuntimeException(
                    "Impossible de modifier une déclaration au statut « " +
                            declaration.getStatut() + " ». Seules GENEREE ou REJETEE sont modifiables.");
        }

        DeclarationType type = typeRepository.findById(request.getDeclarationTypeId())
                .orElseThrow(() -> new RuntimeException("Type introuvable: " + request.getDeclarationTypeId()));
        validateType(type);

        // ✅ Si la déclaration avait un mapping, le réutiliser pour la ré-génération
        String fileContent;
        if (declaration.getMappingJson() != null && !declaration.getMappingJson().isEmpty()
                && type.getFormat() == DeclarationType.DeclarationFormat.XML) {
            try {
                List<XsdSqlMappingRequest.FieldMapping> mappings = objectMapper.readValue(
                        declaration.getMappingJson(),
                        new TypeReference<List<XsdSqlMappingRequest.FieldMapping>>() {}
                );
                log.info("🔄 Ré-utilisation du mapping existant ({} entrées)", mappings.size());
                fileContent = generateFileContentWithMapping(
                        type, request.getDateDebut(), request.getDateFin(),
                        request.getPeriode(), mappings);
            } catch (Exception e) {
                log.warn("⚠️ Impossible de désérialiser le mapping, génération générique: {}", e.getMessage());
                fileContent = generateFileContent(type, request.getDateDebut(), request.getDateFin(), request.getPeriode());
            }
        } else {
            fileContent = generateFileContent(type, request.getDateDebut(), request.getDateFin(), request.getPeriode());
        }

        String fileExtension = resolveExtension(type.getFormat());
        String filename      = buildFilename(type.getCode(), request.getPeriode(), fileExtension);

        declaration.setDeclarationType(type);
        declaration.setPeriode(request.getPeriode());
        declaration.setDateDebut(request.getDateDebut());
        declaration.setDateFin(request.getDateFin());
        declaration.setContenuFichier(fileContent);
        declaration.setDateGeneration(LocalDateTime.now());
        declaration.setSqlQueryUsed(type.getSqlQuery());
        declaration.setXsdFileNameUsed(type.getXsdFileName());
        declaration.setNomFichier(filename);
        declaration.setGenerePar(getCurrentUsername());
        declaration.setStatut(Declaration.DeclarationStatut.GENEREE);
        declaration.setCommentaireRejet(null);
        declaration.setValidePar(null);
        declaration.setDateValidation(null);

        Declaration saved = declarationRepository.save(declaration);
        log.info("✅ Déclaration mise à jour — ID: {}", saved.getId());
        return saved;
    }

    // ══════════════════════════════════════════════════════════════
    // DELETE
    // ══════════════════════════════════════════════════════════════

    @Transactional
    public void deleteDeclaration(Long id) {
        log.info("🗑️ deleteDeclaration — ID: {}", id);
        Declaration declaration = findById(id);

        if (declaration.getStatut() == Declaration.DeclarationStatut.EN_VALIDATION ||
                declaration.getStatut() == Declaration.DeclarationStatut.VALIDEE ||
                declaration.getStatut() == Declaration.DeclarationStatut.ENVOYEE) {
            throw new RuntimeException(
                    "Impossible de supprimer une déclaration au statut « " +
                            declaration.getStatut() + " ».");
        }

        declarationRepository.delete(declaration);
        log.info("✅ Déclaration supprimée — ID: {}", id);
    }

    // ══════════════════════════════════════════════════════════════
    // UPDATE STATUT
    // ══════════════════════════════════════════════════════════════

    @Transactional
    public Declaration updateStatut(Long id, String nouveauStatut,
                                    String commentaire, String validePar) {
        log.info("🔄 updateStatut — ID: {}, nouveauStatut: {}", id, nouveauStatut);
        Declaration declaration = findById(id);

        Declaration.DeclarationStatut statut;
        try {
            statut = Declaration.DeclarationStatut.valueOf(nouveauStatut.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Statut invalide : « " + nouveauStatut + " »");
        }

        declaration.setStatut(statut);

        switch (statut) {
            case EN_VALIDATION:
                log.info("📤 Déclaration {} soumise pour validation", id);
                break;
            case VALIDEE:
                declaration.setDateValidation(LocalDateTime.now());
                declaration.setValidePar(validePar != null ? validePar : getCurrentUsername());
                break;
            case REJETEE:
                if (commentaire == null || commentaire.trim().isEmpty()) {
                    throw new RuntimeException("Un commentaire est obligatoire pour rejeter.");
                }
                declaration.setCommentaireRejet(commentaire.trim());
                declaration.setValidePar(validePar != null ? validePar : getCurrentUsername());
                declaration.setDateValidation(LocalDateTime.now());
                break;
            case ENVOYEE:
                declaration.setDateEnvoi(LocalDateTime.now());
                break;
            default:
                log.warn("⚠️ Statut inattendu: {}", statut);
        }

        return declarationRepository.save(declaration);
    }

    // ══════════════════════════════════════════════════════════════
    // STATS
    // ══════════════════════════════════════════════════════════════

    public DeclarationStats getStats() {
        DeclarationStats stats = new DeclarationStats();
        stats.setTotal(declarationRepository.count());
        stats.setGenerees(declarationRepository.countByStatut(Declaration.DeclarationStatut.GENEREE));
        stats.setEnValidation(declarationRepository.countByStatut(Declaration.DeclarationStatut.EN_VALIDATION));
        stats.setValidees(declarationRepository.countByStatut(Declaration.DeclarationStatut.VALIDEE));
        stats.setRejetees(declarationRepository.countByStatut(Declaration.DeclarationStatut.REJETEE));
        stats.setEnvoyees(declarationRepository.countByStatut(Declaration.DeclarationStatut.ENVOYEE));
        return stats;
    }

    public static class DeclarationStats {
        private long total, generees, enValidation, validees, rejetees, envoyees;
        public long getTotal()              { return total; }
        public void setTotal(long v)        { total = v; }
        public long getGenerees()           { return generees; }
        public void setGenerees(long v)     { generees = v; }
        public long getEnValidation()       { return enValidation; }
        public void setEnValidation(long v) { enValidation = v; }
        public long getValidees()           { return validees; }
        public void setValidees(long v)     { validees = v; }
        public long getRejetees()           { return rejetees; }
        public void setRejetees(long v)     { rejetees = v; }
        public long getEnvoyees()           { return envoyees; }
        public void setEnvoyees(long v)     { envoyees = v; }
    }
}
