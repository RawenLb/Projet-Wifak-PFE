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
    private final XmlGenerationService xmlGenerationService; // ✅ NOUVEAU

    public DeclarationService(
            DeclarationRepository declarationRepository,
            DeclarationTypeRepository typeRepository,
            XmlGenerationService xmlGenerationService // ✅ NOUVEAU — remplace TemplateService
    ) {
        this.declarationRepository = declarationRepository;
        this.typeRepository = typeRepository;
        this.xmlGenerationService = xmlGenerationService;
    }

    /**
     * ✅ Récupérer le nom d'utilisateur connecté
     */
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    /**
     * ✅ MODIFIÉ — Générer une déclaration
     * Maintenant utilise XSD + SQL au lieu de template manuel
     * BF4 - Génération automatique des fichiers
     */
    public Declaration generateDeclaration(
            Long typeId,
            String periode,
            LocalDate dateDebut, // ✅ NOUVEAU
            LocalDate dateFin    // ✅ NOUVEAU
    ) {
        log.info("🚀 Début génération déclaration - Type: {}, Période: {}", typeId, periode);

        // 1. Récupérer le type de déclaration
        DeclarationType type = typeRepository.findById(typeId)
                .orElseThrow(() -> new RuntimeException("Type de déclaration introuvable: " + typeId));

        log.info("📋 Type trouvé: {} ({})", type.getNom(), type.getCode());

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

        // 4. ✅ NOUVEAU — Générer le XML via XSD + SQL
        log.info("⚙️ Génération XML via XSD + SQL...");
        String xmlContent = xmlGenerationService.generateXmlFromXsdAndSql(
                type.getXsdContent(),   // XSD pour la structure et la validation
                type.getSqlQuery(),     // SQL pour récupérer les données
                dateDebut,
                dateFin,
                type.getCode(),
                periode
        );

        // 5. Créer l'entité Declaration
        Declaration declaration = new Declaration();
        declaration.setDeclarationType(type);
        declaration.setPeriode(periode);
        declaration.setDateDebut(dateDebut);
        declaration.setDateFin(dateFin);
        declaration.setStatut(Declaration.DeclarationStatut.GENEREE);
        declaration.setContenuFichier(xmlContent);
        declaration.setDateGeneration(LocalDateTime.now());
        declaration.setGenerePar(getCurrentUsername());

        // ✅ NOUVEAU — Snapshot pour traçabilité
        declaration.setSqlQueryUsed(type.getSqlQuery());
        declaration.setXsdFileNameUsed(type.getXsdFileName());

        // 6. Générer le nom du fichier
        String filename = String.format(
                "declaration_%s_%s.xml",
                type.getCode(),
                periode.replace("-", "")
        );
        declaration.setNomFichier(filename);

        // 7. Sauvegarder
        Declaration saved = declarationRepository.save(declaration);
        log.info("✅ Déclaration générée avec succès - ID: {}, Fichier: {}", saved.getId(), filename);

        return saved;
    }

    /**
     * ✅ Récupérer mes déclarations (utilisateur connecté)
     */
    public List<Declaration> getMyDeclarations() {
        String username = getCurrentUsername();
        log.info("👤 getMyDeclarations — username='{}'", username); // ✅ Log de diagnostic
        List<Declaration> list = declarationRepository.findByGenerePar(username);
        log.info("📋 {} déclaration(s) trouvée(s) pour '{}'", list.size(), username);
        return list;
    }

    /**
     * ✅ Récupérer toutes les déclarations (Admin/Manager)
     */
    public List<Declaration> getAllDeclarations() {
        return declarationRepository.findAll();
    }

    /**
     * ✅ Trouver une déclaration par ID
     */
    public Declaration findById(Long id) {
        return declarationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Déclaration introuvable: " + id));
    }

    /**
     * ✅ Soumettre pour validation (Agent → Manager)
     * BF5 - Workflow de validation
     */
    public Declaration submitForValidation(Long id) {
        log.info("📤 Soumission pour validation - ID: {}", id);

        Declaration declaration = findById(id);
        String currentUser = getCurrentUsername();

        if (!declaration.getGenerePar().equals(currentUser)) {
            throw new RuntimeException("Vous ne pouvez soumettre que vos propres déclarations");
        }

        if (declaration.getStatut() != Declaration.DeclarationStatut.GENEREE &&
                declaration.getStatut() != Declaration.DeclarationStatut.REJETEE) {
            throw new RuntimeException("Seules les déclarations générées ou rejetées peuvent être soumises");
        }

        declaration.setStatut(Declaration.DeclarationStatut.EN_VALIDATION);
        Declaration saved = declarationRepository.save(declaration);
        log.info("✅ Déclaration soumise avec succès");
        return saved;
    }

    /**
     * ✅ Valider une déclaration (Manager)
     * BF5 - Workflow de validation
     */
    public Declaration validateDeclaration(Long id) {
        log.info("✅ Validation déclaration - ID: {}", id);

        Declaration declaration = findById(id);

        if (declaration.getStatut() != Declaration.DeclarationStatut.EN_VALIDATION) {
            throw new RuntimeException("Cette déclaration n'est pas en attente de validation");
        }

        declaration.setStatut(Declaration.DeclarationStatut.VALIDEE);
        declaration.setDateValidation(LocalDateTime.now());
        declaration.setValidePar(getCurrentUsername());

        Declaration saved = declarationRepository.save(declaration);
        log.info("✅ Déclaration validée par: {}", getCurrentUsername());
        return saved;
    }

    /**
     * ✅ Rejeter une déclaration avec commentaire (Manager)
     * BF5 - Workflow de validation
     */
    public Declaration rejectDeclaration(Long id, String commentaire) {
        log.info("❌ Rejet déclaration - ID: {}", id);

        Declaration declaration = findById(id);

        if (declaration.getStatut() != Declaration.DeclarationStatut.EN_VALIDATION) {
            throw new RuntimeException("Cette déclaration n'est pas en attente de validation");
        }

        if (commentaire == null || commentaire.trim().isEmpty()) {
            throw new RuntimeException("Un commentaire est obligatoire pour rejeter une déclaration");
        }

        declaration.setStatut(Declaration.DeclarationStatut.REJETEE);
        declaration.setCommentaireRejet(commentaire);
        declaration.setValidePar(getCurrentUsername());
        declaration.setDateValidation(LocalDateTime.now());

        Declaration saved = declarationRepository.save(declaration);
        log.info("✅ Déclaration rejetée par: {}", getCurrentUsername());
        return saved;
    }

    /**
     * ✅ Récupérer les déclarations en attente de validation
     */
    public List<Declaration> getPendingDeclarations() {
        return declarationRepository.findByStatut(Declaration.DeclarationStatut.EN_VALIDATION);
    }

    /**
     * ✅ Marquer comme envoyée à la BCT
     */
    public Declaration markAsSent(Long id) {
        log.info("📨 Marquage comme envoyée - ID: {}", id);

        Declaration declaration = findById(id);

        if (declaration.getStatut() != Declaration.DeclarationStatut.VALIDEE) {
            throw new RuntimeException("Seules les déclarations validées peuvent être envoyées");
        }

        declaration.setStatut(Declaration.DeclarationStatut.ENVOYEE);
        declaration.setDateEnvoi(LocalDateTime.now());

        Declaration saved = declarationRepository.save(declaration);
        log.info("✅ Déclaration marquée comme envoyée");
        return saved;
    }

    /**
     * ✅ Récupérer les statistiques (Dashboard)
     * BF11 - Dashboard
     */
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

    // ========== DTO Stats ==========
    public static class DeclarationStats {
        private long total;
        private long generees;
        private long enValidation;
        private long validees;
        private long rejetees;
        private long envoyees;

        public long getTotal() { return total; }
        public void setTotal(long total) { this.total = total; }
        public long getGenerees() { return generees; }
        public void setGenerees(long generees) { this.generees = generees; }
        public long getEnValidation() { return enValidation; }
        public void setEnValidation(long enValidation) { this.enValidation = enValidation; }
        public long getValidees() { return validees; }
        public void setValidees(long validees) { this.validees = validees; }
        public long getRejetees() { return rejetees; }
        public void setRejetees(long rejetees) { this.rejetees = rejetees; }
        public long getEnvoyees() { return envoyees; }
        public void setEnvoyees(long envoyees) { this.envoyees = envoyees; }
    }
}