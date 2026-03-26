package com.example.bctbackend.service;

import com.example.bctbackend.entities.Declaration;
import com.example.bctbackend.entities.DeclarationType;
import com.example.bctbackend.repositories.DeclarationRepository;
import com.example.bctbackend.repositories.DeclarationTypeRepository;
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
@Transactional
public class DeclarationService {

    private static final Logger log = LoggerFactory.getLogger(DeclarationService.class);

    private final DeclarationRepository declarationRepository;
    private final DeclarationTypeRepository typeRepository;
    private final XmlGenerationService xmlGenerationService;
    private final CsvGenerationService csvGenerationService;
    private final TxtGenerationService txtGenerationService;

    public DeclarationService(
            DeclarationRepository declarationRepository,
            DeclarationTypeRepository typeRepository,
            XmlGenerationService xmlGenerationService,
            CsvGenerationService csvGenerationService,
            TxtGenerationService txtGenerationService
    ) {
        this.declarationRepository = declarationRepository;
        this.typeRepository        = typeRepository;
        this.xmlGenerationService  = xmlGenerationService;
        this.csvGenerationService  = csvGenerationService;
        this.txtGenerationService  = txtGenerationService;
    }

    // ══════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    // ══════════════════════════════════════════════════════════════
    // GENERATE
    // ══════════════════════════════════════════════════════════════

    public Declaration generateDeclaration(
            Long typeId,
            String periode,
            LocalDate dateDebut,
            LocalDate dateFin
    ) {
        log.info("🚀 Début génération déclaration — Type: {}, Période: {}", typeId, periode);

        DeclarationType type = typeRepository.findById(typeId)
                .orElseThrow(() -> new RuntimeException("Type de déclaration introuvable: " + typeId));

        log.info("📋 Type trouvé: {} ({}) — Format: {}", type.getNom(), type.getCode(), type.getFormat());

        if (!type.isActif()) {
            throw new RuntimeException("Ce type de déclaration est inactif");
        }

        if (type.getSqlQuery() == null || type.getSqlQuery().trim().isEmpty()) {
            throw new RuntimeException(
                    "La requête SQL n'est pas configurée pour ce type de déclaration. " +
                            "Veuillez contacter l'administrateur.");
        }

        String fileContent;
        String fileExtension;

        DeclarationType.DeclarationFormat format = type.getFormat();

        switch (format) {
            case CSV:
                log.info("⚙️ Génération CSV via SQL...");
                fileContent   = csvGenerationService.generateCsvFromSql(
                        type.getSqlQuery(), dateDebut, dateFin, type.getCode(), periode);
                fileExtension = "csv";
                break;
            case TXT:
                log.info("⚙️ Génération TXT via SQL...");
                fileContent   = txtGenerationService.generateTxtFromSql(
                        type.getSqlQuery(), dateDebut, dateFin, type.getCode(), periode);
                fileExtension = "txt";
                break;
            case XML:
            default:
                log.info("⚙️ Génération XML via XSD + SQL...");
                fileContent   = xmlGenerationService.generateXmlFromXsdAndSql(
                        type.getXsdContent(), type.getSqlQuery(),
                        dateDebut, dateFin, type.getCode(), periode);
                fileExtension = "xml";
                break;
        }

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

        String filename = String.format("declaration_%s_%s.%s",
                type.getCode(), periode.replace("-", ""), fileExtension);
        declaration.setNomFichier(filename);

        Declaration saved = declarationRepository.save(declaration);
        log.info("✅ Déclaration générée avec succès — ID: {}, Fichier: {}", saved.getId(), filename);

        return saved;
    }

    // ══════════════════════════════════════════════════════════════
    // QUERIES
    // ══════════════════════════════════════════════════════════════

    public List<Declaration> getMyDeclarations() {
        String username = getCurrentUsername();
        log.info("👤 getMyDeclarations — username='{}'", username);
        List<Declaration> list = declarationRepository.findByGenerePar(username);
        log.info("📋 {} déclaration(s) trouvée(s) pour '{}'", list.size(), username);
        return list;
    }

    public List<Declaration> getAllDeclarations() {
        return declarationRepository.findAll();
    }

    public Declaration findById(Long id) {
        return declarationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Déclaration introuvable: " + id));
    }

    // ══════════════════════════════════════════════════════════════
    // UPDATE STATUT — appelé par validation-service via Feign
    // ══════════════════════════════════════════════════════════════

    /**
     * Met à jour le statut d'une déclaration.
     * Cette méthode est le seul point d'écriture du statut dans ce service.
     * Elle est appelée exclusivement par le validation-service via l'endpoint interne
     * PATCH /api/declarations/{id}/statut
     *
     * @param id            ID de la déclaration
     * @param nouveauStatut Valeur de l'enum DeclarationStatut en String
     * @param commentaire   Commentaire de rejet (si REJETEE)
     * @param validePar     Username du valideur (si VALIDEE ou REJETEE)
     */
    public Declaration updateStatut(Long id, String nouveauStatut,
                                    String commentaire, String validePar) {
        log.info("🔄 updateStatut — ID: {}, nouveauStatut: {}", id, nouveauStatut);

        Declaration declaration = findById(id);

        // Convertir le String en enum (lève IllegalArgumentException si invalide)
        Declaration.DeclarationStatut statut;
        try {
            statut = Declaration.DeclarationStatut.valueOf(nouveauStatut.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Statut invalide : " + nouveauStatut +
                    ". Valeurs acceptées : GENEREE, EN_VALIDATION, VALIDEE, REJETEE, ENVOYEE");
        }

        declaration.setStatut(statut);

        switch (statut) {
            case EN_VALIDATION:
                // Pas de champs supplémentaires nécessaires
                log.info("📤 Déclaration {} soumise pour validation", id);
                break;

            case VALIDEE:
                declaration.setDateValidation(LocalDateTime.now());
                declaration.setValidePar(validePar != null ? validePar : getCurrentUsername());
                log.info("✅ Déclaration {} validée par {}", id, declaration.getValidePar());
                break;

            case REJETEE:
                if (commentaire == null || commentaire.trim().isEmpty()) {
                    throw new RuntimeException(
                            "Un commentaire est obligatoire pour rejeter une déclaration");
                }
                declaration.setCommentaireRejet(commentaire.trim());
                declaration.setValidePar(validePar != null ? validePar : getCurrentUsername());
                declaration.setDateValidation(LocalDateTime.now());
                log.info("❌ Déclaration {} rejetée par {} — motif: {}",
                        id, declaration.getValidePar(), commentaire);
                break;

            case ENVOYEE:
                declaration.setDateEnvoi(LocalDateTime.now());
                log.info("📨 Déclaration {} marquée comme envoyée", id);
                break;

            default:
                log.warn("⚠️ updateStatut appelé avec statut inattendu: {}", statut);
                break;
        }

        return declarationRepository.save(declaration);
    }


    // ══════════════════════════════════════════════════════════════
    // STATS — Dashboard (lecture seule, utilisée par validation-service aussi)
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

    // ══════════════════════════════════════════════════════════════
    // DTO STATS
    // ══════════════════════════════════════════════════════════════

    public static class DeclarationStats {
        private long total;
        private long generees;
        private long enValidation;
        private long validees;
        private long rejetees;
        private long envoyees;

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


    // ══════════════════════════════════════════════════════════════
    // MÉTHODES SUPPRIMÉES — migrées vers validation-service
    // ══════════════════════════════════════════════════════════════
    // submitForValidation()  → ValidationService.submitForValidation()
    // validateDeclaration()  → ValidationService.validateDeclaration()
    // rejectDeclaration()    → ValidationService.rejectDeclaration()
    // markAsSent()           → ValidationService.markAsSent()
    // getPendingDeclarations() → ValidationService.getPendingDeclarations()
}