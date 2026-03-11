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
    private final CsvGenerationService csvGenerationService;   // ✅ NOUVEAU
    private final TxtGenerationService txtGenerationService;   // ✅ NOUVEAU

    public DeclarationService(
            DeclarationRepository declarationRepository,
            DeclarationTypeRepository typeRepository,
            XmlGenerationService xmlGenerationService,
            CsvGenerationService csvGenerationService,         // ✅ NOUVEAU
            TxtGenerationService txtGenerationService          // ✅ NOUVEAU
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
    // GENERATE — ✅ CORRIGÉ : dispatche selon le format du type
    // ══════════════════════════════════════════════════════════════

    /**
     * Génère une déclaration en choisissant le bon service selon le format
     * (XML, CSV, TXT, JSON, PDF).
     */
    public Declaration generateDeclaration(
            Long typeId,
            String periode,
            LocalDate dateDebut,
            LocalDate dateFin
    ) {
        log.info("🚀 Début génération déclaration — Type: {}, Période: {}", typeId, periode);

        // 1. Récupérer le type de déclaration
        DeclarationType type = typeRepository.findById(typeId)
                .orElseThrow(() -> new RuntimeException("Type de déclaration introuvable: " + typeId));

        log.info("📋 Type trouvé: {} ({}) — Format: {}", type.getNom(), type.getCode(), type.getFormat());

        // 2. Vérifier que le type est actif
        if (!type.isActif()) {
            throw new RuntimeException("Ce type de déclaration est inactif");
        }

        // 3. Vérifier que la requête SQL est configurée
        if (type.getSqlQuery() == null || type.getSqlQuery().trim().isEmpty()) {
            throw new RuntimeException(
                    "La requête SQL n'est pas configurée pour ce type de déclaration. " +
                            "Veuillez contacter l'administrateur."
            );
        }

        // ✅ 4. Générer le contenu selon le FORMAT
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

        // 5. Créer l'entité Declaration
        Declaration declaration = new Declaration();
        declaration.setDeclarationType(type);
        declaration.setPeriode(periode);
        declaration.setDateDebut(dateDebut);
        declaration.setDateFin(dateFin);
        declaration.setStatut(Declaration.DeclarationStatut.GENEREE);
        declaration.setContenuFichier(fileContent);
        declaration.setDateGeneration(LocalDateTime.now());
        declaration.setGenerePar(getCurrentUsername());

        // Snapshot pour traçabilité
        declaration.setSqlQueryUsed(type.getSqlQuery());
        declaration.setXsdFileNameUsed(type.getXsdFileName());

        // ✅ 6. Nom du fichier avec la bonne extension selon le format
        String filename = String.format("declaration_%s_%s.%s",
                type.getCode(), periode.replace("-", ""), fileExtension);
        declaration.setNomFichier(filename);

        // 7. Sauvegarder
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
    // WORKFLOW
    // ══════════════════════════════════════════════════════════════

    public Declaration submitForValidation(Long id) {
        log.info("📤 Soumission pour validation — ID: {}", id);

        Declaration declaration = findById(id);
        String currentUser = getCurrentUsername();

        if (!declaration.getGenerePar().equals(currentUser)) {
            throw new RuntimeException("Vous ne pouvez soumettre que vos propres déclarations");
        }

        if (declaration.getStatut() != Declaration.DeclarationStatut.GENEREE &&
                declaration.getStatut() != Declaration.DeclarationStatut.REJETEE) {
            throw new RuntimeException(
                    "Seules les déclarations générées ou rejetées peuvent être soumises");
        }

        declaration.setStatut(Declaration.DeclarationStatut.EN_VALIDATION);
        return declarationRepository.save(declaration);
    }

    public Declaration validateDeclaration(Long id) {
        log.info("✅ Validation déclaration — ID: {}", id);

        Declaration declaration = findById(id);

        if (declaration.getStatut() != Declaration.DeclarationStatut.EN_VALIDATION) {
            throw new RuntimeException("Cette déclaration n'est pas en attente de validation");
        }

        declaration.setStatut(Declaration.DeclarationStatut.VALIDEE);
        declaration.setDateValidation(LocalDateTime.now());
        declaration.setValidePar(getCurrentUsername());
        return declarationRepository.save(declaration);
    }

    public Declaration rejectDeclaration(Long id, String commentaire) {
        log.info("❌ Rejet déclaration — ID: {}", id);

        Declaration declaration = findById(id);

        if (declaration.getStatut() != Declaration.DeclarationStatut.EN_VALIDATION) {
            throw new RuntimeException("Cette déclaration n'est pas en attente de validation");
        }

        if (commentaire == null || commentaire.trim().isEmpty()) {
            throw new RuntimeException(
                    "Un commentaire est obligatoire pour rejeter une déclaration");
        }

        declaration.setStatut(Declaration.DeclarationStatut.REJETEE);
        declaration.setCommentaireRejet(commentaire);
        declaration.setValidePar(getCurrentUsername());
        declaration.setDateValidation(LocalDateTime.now());
        return declarationRepository.save(declaration);
    }

    public List<Declaration> getPendingDeclarations() {
        return declarationRepository.findByStatut(Declaration.DeclarationStatut.EN_VALIDATION);
    }

    public Declaration markAsSent(Long id) {
        log.info("📨 Marquage comme envoyée — ID: {}", id);

        Declaration declaration = findById(id);

        if (declaration.getStatut() != Declaration.DeclarationStatut.VALIDEE) {
            throw new RuntimeException("Seules les déclarations validées peuvent être envoyées");
        }

        declaration.setStatut(Declaration.DeclarationStatut.ENVOYEE);
        declaration.setDateEnvoi(LocalDateTime.now());
        return declarationRepository.save(declaration);
    }

    // ══════════════════════════════════════════════════════════════
    // STATS — Dashboard
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

        public long getTotal()           { return total; }
        public void setTotal(long v)     { total = v; }
        public long getGenerees()        { return generees; }
        public void setGenerees(long v)  { generees = v; }
        public long getEnValidation()    { return enValidation; }
        public void setEnValidation(long v) { enValidation = v; }
        public long getValidees()        { return validees; }
        public void setValidees(long v)  { validees = v; }
        public long getRejetees()        { return rejetees; }
        public void setRejetees(long v)  { rejetees = v; }
        public long getEnvoyees()        { return envoyees; }
        public void setEnvoyees(long v)  { envoyees = v; }
    }
}