package com.wifak.validationservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifak.validationservice.dto.AiValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * AiDeclarationService — Validation 100% règles métier locale (sans IA / sans cloud)
 * Banque Wifak — Plateforme de gestion des déclarations réglementaires BCT
 *
 * Conforme au Cahier des Charges (BF10, BF15, BF16, BF17) :
 *  - BF10 : Contrôle de conformité (structure + métier)
 *  - BF15 : Détection des anomalies par règles BCT
 *  - BF16 : Score de risque calculé localement
 *  - BF17 : Aide à la correction via templates de motifs de rejet
 *
 * ✅ Zéro dépendance externe — Instantané — Sécurisé (données jamais transmises)
 * Seuil de validation : score ≥ 70 → VALIDÉE | score < 70 → REJETÉE
 */
@Service
public class AiDeclarationService {

    private static final Logger log = LoggerFactory.getLogger(AiDeclarationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Taux de provision réglementaires BCT par classe de risque ──
    private static final Map<String, Double> TAUX_PROVISION = Map.of(
            "A", 0.05, "B", 0.10, "C", 0.20, "D", 0.50
    );

    // ── Valeurs autorisées par type de déclaration ──
    private static final Set<String> TYPES_RISQUE      = Set.of("CHANGE", "TAUX");
    private static final Set<String> TYPES_OPERATION   = Set.of("CREDIT", "DEPOT", "VIREMENT", "GARANTIE", "EMISSION");
    private static final Set<String> STATUTS_OPERATION = Set.of("ACTIF", "CLOTURE", "EN_COURS", "CONTENTIEUX");
    private static final Set<String> CLASSES_RISQUE    = Set.of("A", "B", "C", "D");
    private static final Set<String> TYPES_CLIENT      = Set.of("ENTREPRISE", "PME", "TPE", "GE", "STARTUP");
    private static final Set<String> TYPES_CONTREP     = Set.of("ENTREPRISE", "BANQUE", "ETAT", "PARTICULIER");
    private static final Set<String> TYPES_ENGAGEMENT  = Set.of("BILAN", "HORS_BILAN");

    // ── Templates de motifs de rejet (BF17) ──
    public static final List<Map<String, String>> REJECT_TEMPLATES = List.of(
            Map.of("id", "DONNEES_MANQUANTES_OU_VIDES",
                    "label", "Données manquantes ou vides",
                    "text",  "Les lignes de la déclaration contiennent des champs obligatoires vides ou nuls. " +
                            "Selon le schéma XSD BCT, tous les champs requis doivent contenir des valeurs réelles " +
                            "récupérées depuis la base de données. Veuillez régénérer la déclaration après vérification " +
                            "des données sources."),
            Map.of("id", "PROVISION_INSUFFISANTE",
                    "label", "Provision insuffisante",
                    "text",  "Les provisions déclarées sont inférieures au minimum réglementaire BCT pour la classe " +
                            "de risque indiquée. Taux en vigueur : A=5%, B=10%, C=20%, D=50%. Veuillez recalculer " +
                            "et soumettre à nouveau."),
            Map.of("id", "MONTANT_IMPAYE_INCOHERENT",
                    "label", "Montant impayé incohérent",
                    "text",  "Le montant impayé déclaré dépasse le montant du crédit accordé. Veuillez vérifier " +
                            "les données de la ligne concernée et corriger avant de resoumettre."),
            Map.of("id", "CHAMP_OBLIGATOIRE_MANQUANT",
                    "label", "Champ obligatoire manquant",
                    "text",  "Des champs obligatoires selon le schéma XSD BCT sont absents dans le fichier soumis. " +
                            "Veuillez compléter tous les champs requis et resoumettre."),
            Map.of("id", "PERIODE_INCORRECTE",
                    "label", "Période incorrecte",
                    "text",  "Les dates de début et/ou de fin ne correspondent pas à la période déclarée. " +
                            "La date de début doit être le 1er du mois et la date de fin le dernier jour du mois."),
            Map.of("id", "FORMAT_XML_INVALIDE",
                    "label", "Format XML invalide",
                    "text",  "Le fichier XML soumis est malformé ou ne respecte pas le schéma XSD défini par la BCT. " +
                            "Veuillez valider votre fichier avant toute soumission."),
            Map.of("id", "VARIATION_ANORMALE",
                    "label", "Variation anormale des montants",
                    "text",  "Une variation supérieure à 50% des montants déclarés par rapport à la période précédente " +
                            "a été détectée. Veuillez justifier cette évolution ou corriger si erreur de saisie."),
            Map.of("id", "DONNEES_NON_REELLES",
                    "label", "Données non réelles / données de test",
                    "text",  "Le fichier semble contenir des données de test, fictives ou incomplètes. " +
                            "Les déclarations BCT doivent contenir des données réelles extraites de la base de données " +
                            "selon le type de déclaration concerné.")
    );

    // ══════════════════════════════════════════════════════════════════════════
    // POINT D'ENTRÉE PRINCIPAL — BF10 + BF15
    // Validation complète par règles métier — résultat instantané
    // ══════════════════════════════════════════════════════════════════════════
    public AiValidationResult analyzeDeclaration(String contenu, String nomFichier) {
        log.info("⚡ [BF10/BF15] Validation règles métier: {}", nomFichier);
        String format = detectFormat(nomFichier, contenu);
        log.info("📄 Format détecté: {}", format);

        // Étape 1 : Validation structurelle
        List<String> structureErrors = validateStructureOnly(contenu, format);
        if (!structureErrors.isEmpty()) {
            log.warn("❌ Structure invalide: {}", structureErrors);
            return reject(structureErrors, 0);
        }

        // Étape 2 : Validation métier complète
        return validateBusinessRules(contenu, nomFichier, format);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VALIDATION MÉTIER PRINCIPALE
    // ══════════════════════════════════════════════════════════════════════════
    private AiValidationResult validateBusinessRules(String contenu, String nomFichier, String format) {
        String typeDeclaration = detectTypeDeclaration(nomFichier, contenu);
        log.info("📋 Type déclaration détecté: {}", typeDeclaration);

        List<String> anomalies = new ArrayList<>();
        int score = 100;

        // ── 1. Validation de l'en-tête ──
        Map<String, String> entete = extractEssentialFields(contenu);
        List<String> enteteErrors = validateEntete(entete, contenu);
        anomalies.addAll(enteteErrors);
        score -= enteteErrors.size() * 10;

        // ── 2. Extraction et validation des lignes ──
        List<Map<String, String>> lignes = extractLignes(contenu);

        // Vérification du nombre de lignes vs NombreLignes déclaré
        String nombreLignesDeclare = extractTagOutsideLignes(contenu, "nombrelignes");
        if (nombreLignesDeclare != null) {
            try {
                int declared = Integer.parseInt(nombreLignesDeclare.trim());
                if (declared != lignes.size()) {
                    anomalies.add(String.format(
                            "Incohérence du nombre de lignes : en-tête déclare %d ligne(s) mais %d ligne(s) trouvée(s) dans <Donnees>",
                            declared, lignes.size()));
                    score -= 15;
                }
            } catch (NumberFormatException ignored) {}
        }

        if (lignes.isEmpty()) {
            anomalies.add("Aucune ligne de données trouvée dans <Donnees> — la déclaration est vide");
            score -= 30;
        } else {
            // ── 3. Validation par type de déclaration ──
            List<String> lignesErrors = switch (typeDeclaration) {
                case "BCT_01" -> validateBCT01(lignes);
                case "BCT_02" -> validateBCT02(lignes);
                case "BCT_03" -> validateBCT03(lignes);
                case "BCT_04" -> validateBCT04(lignes);
                case "BCT_05", "BCT-05" -> validateBCT05(lignes);
                default -> validateGeneric(lignes);
            };
            anomalies.addAll(lignesErrors);
            // Pénalité proportionnelle : chaque anomalie sur lignes réduit le score
            score -= (int) Math.min(60, lignesErrors.size() * 8);
        }

        // ── 4. Détection de données fictives / de test ──
        List<String> fictifErrors = detectFictiveData(lignes, typeDeclaration);
        anomalies.addAll(fictifErrors);
        score -= fictifErrors.size() * 15;

        // Score final borné entre 0 et 100
        score = Math.max(0, Math.min(100, score));

        boolean valid = score >= 70;
        String recommendation = valid ? "VALIDATE" : "REJECT";

        log.info("✅ Validation terminée — Score: {}/100, Décision: {}, Anomalies: {}",
                score, recommendation, anomalies.size());

        AiValidationResult result = new AiValidationResult();
        result.setValid(valid);
        result.setScore(score);
        result.setRecommendation(recommendation);
        result.setAnomalies(anomalies.isEmpty()
                ? List.of("✅ Aucune anomalie détectée — déclaration conforme aux règles BCT")
                : anomalies);
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VALIDATION EN-TÊTE
    // ══════════════════════════════════════════════════════════════════════════
    private List<String> validateEntete(Map<String, String> entete, String contenu) {
        List<String> errors = new ArrayList<>();

        String periode  = entete.get("periode");
        String debut    = entete.get("date_debut");
        String fin      = entete.get("date_fin");
        String code     = entete.get("code");

        if (isBlankOrFictive(code)) {
            errors.add("En-tête : CodeDeclaration absent ou vide");
        }
        if (isBlankOrFictive(periode)) {
            errors.add("En-tête : Période absente ou vide (format attendu : YYYY-MM)");
        }
        if (isBlankOrFictive(debut)) {
            errors.add("En-tête : DateDebut absente ou vide");
        } else if (!debut.matches("\\d{4}-\\d{2}-01")) {
            errors.add("En-tête : DateDebut '" + debut + "' doit être le 1er jour du mois (format : YYYY-MM-01)");
        }
        if (isBlankOrFictive(fin)) {
            errors.add("En-tête : DateFin absente ou vide");
        } else {
            // Vérifier que la date de fin est cohérente avec la période
            if (periode != null && !periode.isBlank()) {
                String[] parts = periode.split("-");
                if (parts.length == 2 && !fin.startsWith(periode)) {
                    errors.add("En-tête : DateFin '" + fin + "' ne correspond pas à la période '" + periode + "'");
                }
            }
        }

        // Cohérence début / fin
        if (debut != null && fin != null && !debut.isBlank() && !fin.isBlank()) {
            if (debut.compareTo(fin) > 0) {
                errors.add("En-tête : DateDebut '" + debut + "' est postérieure à DateFin '" + fin + "'");
            }
        }

        return errors;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BCT_01 — Risques de Change et de Taux
    // ══════════════════════════════════════════════════════════════════════════
    private List<String> validateBCT01(List<Map<String, String>> lignes) {
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();

        for (int i = 0; i < lignes.size(); i++) {
            Map<String, String> l = lignes.get(i);
            int num = i + 1;
            String id = l.get("IdClient");

            errors.addAll(checkRequired(num, l, "IdClient", "NomClient", "TypeRisque",
                    "MontantExposition", "Devise", "DateEcheance", "TauxApplique"));

            if (!isBlank(id)) {
                if (!ids.add(id))
                    errors.add("Ligne " + num + " : IdClient '" + id + "' en double (doit être unique)");
            }
            checkEnum(num, l, "TypeRisque", TYPES_RISQUE, errors);
            checkPositiveAmount(num, l, "MontantExposition", errors);
            checkPositiveAmount(num, l, "TauxApplique", errors);
            checkDateFormat(num, l, "DateEcheance", errors);
        }
        return errors;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BCT_02 — Positions de Change
    // ══════════════════════════════════════════════════════════════════════════
    private List<String> validateBCT02(List<Map<String, String>> lignes) {
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < lignes.size(); i++) {
            Map<String, String> l = lignes.get(i);
            int num = i + 1;

            errors.addAll(checkRequired(num, l, "DatePosition", "Devise",
                    "PositionAchat", "PositionVente", "PositionNette", "LimiteAuthorisee"));

            checkNonNegativeAmount(num, l, "PositionAchat", errors);
            checkNonNegativeAmount(num, l, "PositionVente", errors);
            checkPositiveAmount(num, l, "LimiteAuthorisee", errors);
            checkDateFormat(num, l, "DatePosition", errors);

            // Vérifier PositionNette = PositionAchat - PositionVente
            Double achat  = parseDouble(l.get("PositionAchat"));
            Double vente  = parseDouble(l.get("PositionVente"));
            Double nette  = parseDouble(l.get("PositionNette"));
            if (achat != null && vente != null && nette != null) {
                double expected = achat - vente;
                if (Math.abs(nette - expected) > 0.01) {
                    errors.add(String.format(
                            "Ligne %d : PositionNette (%.2f) ≠ PositionAchat - PositionVente (%.2f)",
                            num, nette, expected));
                }
            }
        }
        return errors;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BCT_03 — Grandes Expositions
    // ══════════════════════════════════════════════════════════════════════════
    private List<String> validateBCT03(List<Map<String, String>> lignes) {
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();

        for (int i = 0; i < lignes.size(); i++) {
            Map<String, String> l = lignes.get(i);
            int num = i + 1;
            String id = l.get("IdContrepartie");

            errors.addAll(checkRequired(num, l, "IdContrepartie", "NomContrepartie",
                    "TypeContrepartie", "MontantExposition", "PourcentageFP", "TypeEngagement"));

            if (!isBlank(id)) {
                if (!ids.add(id))
                    errors.add("Ligne " + num + " : IdContrepartie '" + id + "' en double");
            }
            checkEnum(num, l, "TypeContrepartie", TYPES_CONTREP, errors);
            checkEnum(num, l, "TypeEngagement",   TYPES_ENGAGEMENT, errors);
            checkPositiveAmount(num, l, "MontantExposition", errors);

            // PourcentageFP doit être entre 0 et 100
            Double pct = parseDouble(l.get("PourcentageFP"));
            if (pct != null && (pct <= 0 || pct > 100)) {
                errors.add("Ligne " + num + " : PourcentageFP (" + pct + ") doit être entre 0 et 100");
            }
        }
        return errors;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BCT_04 — Opérations Bancaires Trimestrielles
    // ══════════════════════════════════════════════════════════════════════════
    private List<String> validateBCT04(List<Map<String, String>> lignes) {
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();

        for (int i = 0; i < lignes.size(); i++) {
            Map<String, String> l = lignes.get(i);
            int num = i + 1;
            String id = l.get("IdClient");

            errors.addAll(checkRequired(num, l, "IdClient", "NomClient", "TypeOperation",
                    "MontantOperation", "DateOperation", "Devise", "StatutOperation"));

            if (!isBlank(id)) {
                if (!ids.add(id))
                    errors.add("Ligne " + num + " : IdClient '" + id + "' en double");
            }
            checkEnum(num, l, "TypeOperation",   TYPES_OPERATION, errors);
            checkEnum(num, l, "StatutOperation", STATUTS_OPERATION, errors);
            checkPositiveAmount(num, l, "MontantOperation", errors);
            checkDateFormat(num, l, "DateOperation", errors);
        }
        return errors;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BCT_05 — Crédits Accordés aux Entreprises
    // ══════════════════════════════════════════════════════════════════════════
    private List<String> validateBCT05(List<Map<String, String>> lignes) {
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();

        for (int i = 0; i < lignes.size(); i++) {
            Map<String, String> l = lignes.get(i);
            int num = i + 1;
            String id = l.get("IdClient");

            errors.addAll(checkRequired(num, l, "IdClient", "NomClient", "MontantCredit",
                    "MontantImpaye", "ClasseRisque", "Provision", "DureeRetard",
                    "TypeClient", "DateClassification"));

            if (!isBlank(id)) {
                if (!ids.add(id))
                    errors.add("Ligne " + num + " : IdClient '" + id + "' en double (doit être unique)");
            }

            checkEnum(num, l, "ClasseRisque", CLASSES_RISQUE, errors);
            checkEnum(num, l, "TypeClient",   TYPES_CLIENT, errors);
            checkPositiveAmount(num, l, "MontantCredit", errors);
            checkDateFormat(num, l, "DateClassification", errors);

            Double credit    = parseDouble(l.get("MontantCredit"));
            Double impaye    = parseDouble(l.get("MontantImpaye"));
            Double provision = parseDouble(l.get("Provision"));
            String classe    = l.get("ClasseRisque");

            // Règle critique : MontantImpaye ≤ MontantCredit
            if (credit != null && impaye != null && impaye > credit + 0.01) {
                errors.add(String.format(
                        "Ligne %d [%s] CRITIQUE : MontantImpaye (%.2f TND) > MontantCredit (%.2f TND) — incohérence mathématique",
                        num, id != null ? id : "?", impaye, credit));
            }

            // Règle provision : Provision ≥ MontantImpaye × taux(classe)
            if (classe != null && impaye != null && provision != null) {
                Double taux = TAUX_PROVISION.get(classe.toUpperCase());
                if (taux != null) {
                    double mini = impaye * taux;
                    if (provision < mini - 0.01) {
                        errors.add(String.format(
                                "Ligne %d [%s] : Provision insuffisante (%.2f TND) < minimum réglementaire BCT (%.2f TND) pour classe %s (taux %.0f%%)",
                                num, id != null ? id : "?", provision, mini, classe, taux * 100));
                    }
                }
            }

            // DureeRetard doit être ≥ 0
            Double retard = parseDouble(l.get("DureeRetard"));
            if (retard != null && retard < 0) {
                errors.add("Ligne " + num + " : DureeRetard (" + retard.intValue() + ") ne peut pas être négatif");
            }

            // Impayé doit être ≥ 0
            if (impaye != null && impaye < 0) {
                errors.add("Ligne " + num + " : MontantImpaye (" + impaye + ") ne peut pas être négatif");
            }
        }
        return errors;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VALIDATION GÉNÉRIQUE (type inconnu)
    // ══════════════════════════════════════════════════════════════════════════
    private List<String> validateGeneric(List<Map<String, String>> lignes) {
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < lignes.size(); i++) {
            Map<String, String> l = lignes.get(i);
            int num = i + 1;
            long emptyFields = l.values().stream().filter(this::isBlank).count();
            if (emptyFields > 0) {
                errors.add("Ligne " + num + " : " + emptyFields + " champ(s) vide(s) détecté(s)");
            }
        }
        return errors;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DÉTECTION DE DONNÉES FICTIVES / DE TEST
    // ══════════════════════════════════════════════════════════════════════════
    private static final Set<String> MOTS_FICTIFS = Set.of(
            "test", "null", "n/a", "na", "xxx", "yyy", "zzz", "demo",
            "exemple", "example", "fictif", "fake", "dummy", "toto", "titi",
            "aaa", "bbb", "ccc", "foo", "bar", "sample"
    );

    private List<String> detectFictiveData(List<Map<String, String>> lignes, String type) {
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < lignes.size(); i++) {
            Map<String, String> l = lignes.get(i);
            int num = i + 1;
            for (Map.Entry<String, String> entry : l.entrySet()) {
                String val = entry.getValue();
                if (val != null && MOTS_FICTIFS.contains(val.toLowerCase().trim())) {
                    errors.add("Ligne " + num + " : Champ '" + entry.getKey() +
                            "' contient une valeur fictive/de test ('" + val + "')");
                }
            }
            // Détecter si tous les montants sont à 0
            String[] montantFields = {"MontantCredit", "MontantImpaye", "MontantOperation",
                    "MontantExposition", "PositionAchat", "PositionVente"};
            boolean allZero = Arrays.stream(montantFields)
                    .map(f -> parseDouble(l.get(f)))
                    .filter(Objects::nonNull)
                    .allMatch(v -> v == 0.0);
            long montantCount = Arrays.stream(montantFields)
                    .map(f -> parseDouble(l.get(f)))
                    .filter(Objects::nonNull)
                    .count();
            if (montantCount > 0 && allZero) {
                errors.add("Ligne " + num + " : Tous les montants sont à zéro — données probablement fictives ou non initialisées");
            }
        }
        return errors;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BF15 — RÉSUMÉ ANALYTIQUE (pour le tableau de bord)
    // ══════════════════════════════════════════════════════════════════════════
    public Map<String, Object> buildAiSummary(String contenu, String nomFichier) {
        Map<String, Object> summary = new LinkedHashMap<>();
        String format = detectFormat(nomFichier, contenu);

        if (!"XML".equals(format)) {
            summary.put("format", format);
            summary.put("message", "Résumé analytique disponible uniquement pour les fichiers XML BCT");
            return summary;
        }

        Map<String, String> essentials = extractEssentialFields(contenu);
        summary.put("periode",        essentials.getOrDefault("periode", "—"));
        summary.put("dateDebut",       essentials.getOrDefault("date_debut", "—"));
        summary.put("dateFin",         essentials.getOrDefault("date_fin", "—"));
        summary.put("codeDeclaration", essentials.getOrDefault("code", "—"));

        List<Map<String, String>> lignes = extractLignes(contenu);
        summary.put("nombreLignes", lignes.size());

        double totalCredit = 0, totalImpaye = 0, totalProvision = 0;
        Map<String, Integer> classeCount = new LinkedHashMap<>();
        List<Map<String, Object>> anomalies = new ArrayList<>();

        int idx = 0;
        for (Map<String, String> ligne : lignes) {
            idx++;
            Double credit    = parseDouble(ligne.get("MontantCredit"));
            Double impaye    = parseDouble(ligne.get("MontantImpaye"));
            Double provision = parseDouble(ligne.get("Provision"));
            String classe    = ligne.get("ClasseRisque");
            String idClient  = ligne.getOrDefault("IdClient", ligne.getOrDefault("idClient", "L" + idx));

            if (credit    != null) totalCredit    += credit;
            if (impaye    != null) totalImpaye    += impaye;
            if (provision != null) totalProvision += provision;
            if (classe    != null) classeCount.merge(classe.toUpperCase(), 1, Integer::sum);

            if (credit != null && impaye != null && impaye > credit + 0.01) {
                anomalies.add(buildAnomalie(idx, idClient, "MONTANT_IMPAYE_INCOHERENT", "CRITIQUE",
                        String.format("Impayé (%.0f TND) > Crédit (%.0f TND)", impaye, credit)));
            }
            if (classe != null && impaye != null && provision != null) {
                Double taux = TAUX_PROVISION.get(classe.toUpperCase());
                if (taux != null) {
                    double mini = impaye * taux;
                    if (provision < mini - 0.01) {
                        anomalies.add(buildAnomalie(idx, idClient, "PROVISION_INSUFFISANTE", "MAJEURE",
                                String.format("Provision %.0f TND < minimum %.0f TND (classe %s, taux %.0f%%)",
                                        provision, mini, classe, taux * 100)));
                    }
                }
            }
        }

        summary.put("totalMontantCredit",  totalCredit);
        summary.put("totalMontantImpaye",  totalImpaye);
        summary.put("totalProvision",       totalProvision);
        summary.put("tauxImpayeGlobal",     totalCredit > 0 ? (totalImpaye / totalCredit) * 100 : 0.0);
        summary.put("repartitionClasses",   classeCount);
        summary.put("anomaliesDetaillees",  anomalies);
        summary.put("nombreAnomaliesCritiques", anomalies.stream().filter(a -> "CRITIQUE".equals(a.get("severity"))).count());
        summary.put("nombreAnomaliesMajeures",  anomalies.stream().filter(a -> "MAJEURE".equals(a.get("severity"))).count());

        int riskScore = computeRiskScore(anomalies, totalCredit, totalImpaye);
        summary.put("riskScore", riskScore);
        summary.put("riskLevel", riskScore >= 70 ? "FAIBLE" : riskScore >= 40 ? "MOYEN" : "ELEVE");

        return summary;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BF16 — SCORE DE RISQUE
    // ══════════════════════════════════════════════════════════════════════════
    private int computeRiskScore(List<Map<String, Object>> anomalies, double totalCredit, double totalImpaye) {
        int score = 100;
        long critiques = anomalies.stream().filter(a -> "CRITIQUE".equals(a.get("severity"))).count();
        long majeures  = anomalies.stream().filter(a -> "MAJEURE".equals(a.get("severity"))).count();
        score -= (int)(critiques * 25);
        score -= (int)(majeures  * 10);
        if (totalCredit > 0) {
            double tauxImpaye = totalImpaye / totalCredit;
            if (tauxImpaye > 0.50) score -= 20;
            else if (tauxImpaye > 0.30) score -= 15;
        }
        return Math.max(0, Math.min(100, score));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BF15 — COMPARAISON AVEC PÉRIODE PRÉCÉDENTE
    // ══════════════════════════════════════════════════════════════════════════
    public Map<String, Object> compareWithPrevious(String contenuCurrent, String contenuPrevious) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (contenuPrevious == null || contenuPrevious.isBlank()) {
            result.put("available", false);
            result.put("message", "Aucune déclaration précédente disponible pour comparaison");
            return result;
        }
        Map<String, Object> curr = buildAiSummary(contenuCurrent, "current.xml");
        Map<String, Object> prev = buildAiSummary(contenuPrevious, "previous.xml");
        result.put("available",         true);
        result.put("variationCredit",    computeVariation(curr, prev, "totalMontantCredit"));
        result.put("variationImpaye",    computeVariation(curr, prev, "totalMontantImpaye"));
        result.put("variationProvision", computeVariation(curr, prev, "totalProvision"));
        result.put("variationLignes",    computeVariation(curr, prev, "nombreLignes"));
        result.put("alerteVariation",    isVariationAnormale(curr, prev));
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BF17 — TEMPLATES DE MOTIFS DE REJET
    // ══════════════════════════════════════════════════════════════════════════
    public List<Map<String, String>> getRejectTemplates() {
        return REJECT_TEMPLATES;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS — Vérification des champs
    // ══════════════════════════════════════════════════════════════════════════
    private List<String> checkRequired(int num, Map<String, String> ligne, String... fields) {
        List<String> errors = new ArrayList<>();
        for (String field : fields) {
            String val = ligne.get(field);
            if (isBlank(val)) {
                errors.add("Ligne " + num + " : Champ obligatoire '" + field + "' absent ou vide");
            }
        }
        return errors;
    }

    private void checkEnum(int num, Map<String, String> ligne, String field, Set<String> allowed, List<String> errors) {
        String val = ligne.get(field);
        if (!isBlank(val) && !allowed.contains(val.toUpperCase().trim())) {
            errors.add("Ligne " + num + " : '" + field + "' = '" + val +
                    "' invalide. Valeurs autorisées : " + String.join(", ", allowed));
        }
    }

    private void checkPositiveAmount(int num, Map<String, String> ligne, String field, List<String> errors) {
        String val = ligne.get(field);
        if (!isBlank(val)) {
            Double d = parseDouble(val);
            if (d == null) {
                errors.add("Ligne " + num + " : '" + field + "' = '" + val + "' n'est pas un nombre valide");
            } else if (d <= 0) {
                errors.add("Ligne " + num + " : '" + field + "' (" + d + ") doit être > 0");
            }
        }
    }

    private void checkNonNegativeAmount(int num, Map<String, String> ligne, String field, List<String> errors) {
        String val = ligne.get(field);
        if (!isBlank(val)) {
            Double d = parseDouble(val);
            if (d == null) {
                errors.add("Ligne " + num + " : '" + field + "' = '" + val + "' n'est pas un nombre valide");
            } else if (d < 0) {
                errors.add("Ligne " + num + " : '" + field + "' (" + d + ") ne peut pas être négatif");
            }
        }
    }

    private void checkDateFormat(int num, Map<String, String> ligne, String field, List<String> errors) {
        String val = ligne.get(field);
        if (!isBlank(val) && !val.matches("\\d{4}-\\d{2}-\\d{2}")) {
            errors.add("Ligne " + num + " : '" + field + "' = '" + val + "' — format attendu : YYYY-MM-DD");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean isBlankOrFictive(String s) {
        if (isBlank(s)) return true;
        return MOTS_FICTIFS.contains(s.trim().toLowerCase());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PRÉ-VALIDATION STRUCTURELLE
    // ══════════════════════════════════════════════════════════════════════════
    private List<String> validateStructureOnly(String contenu, String format) {
        List<String> errors = new ArrayList<>();
        if (contenu == null || contenu.trim().isEmpty()) {
            errors.add("Fichier vide — aucune donnée à analyser");
            return errors;
        }
        if ("XML".equals(format) && !isWellFormedXml(contenu)) {
            errors.add("XML malformé — syntaxe invalide (balises non fermées, caractères interdits, etc.)");
        }
        if ("CSV".equals(format) && contenu.split("\n").length < 2) {
            errors.add("CSV invalide — au moins une ligne d'en-tête et une ligne de données requises");
        }
        return errors;
    }

    private boolean isWellFormedXml(String xml) {
        try {
            javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DÉTECTION FORMAT ET TYPE
    // ══════════════════════════════════════════════════════════════════════════
    private String detectFormat(String nomFichier, String contenu) {
        if (nomFichier != null) {
            String lower = nomFichier.toLowerCase();
            if (lower.endsWith(".xml")) return "XML";
            if (lower.endsWith(".csv")) return "CSV";
            if (lower.endsWith(".txt")) return "TXT";
        }
        String trimmed = contenu != null ? contenu.trim() : "";
        if (trimmed.startsWith("<"))                         return "XML";
        if (trimmed.contains(",") && trimmed.contains("\n")) return "CSV";
        return "TXT";
    }

    private String detectTypeDeclaration(String nomFichier, String contenu) {
        if (nomFichier != null) {
            String upper = nomFichier.toUpperCase();
            if (upper.contains("BCT_01") || upper.contains("BCT-01")) return "BCT_01";
            if (upper.contains("BCT_02") || upper.contains("BCT-02")) return "BCT_02";
            if (upper.contains("BCT_03") || upper.contains("BCT-03")) return "BCT_03";
            if (upper.contains("BCT_04") || upper.contains("BCT-04")) return "BCT_04";
            if (upper.contains("BCT_05") || upper.contains("BCT-05")) return "BCT_05";
        }
        Pattern p = Pattern.compile("<CodeDeclaration>(.*?)</CodeDeclaration>", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(contenu);
        if (m.find()) return m.group(1).trim();
        Pattern pAttr = Pattern.compile("code=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher mAttr = pAttr.matcher(contenu.substring(0, Math.min(500, contenu.length())));
        if (mAttr.find()) return mAttr.group(1).trim();
        return "INCONNU";
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UTILITAIRES — Comparaison périodes
    // ══════════════════════════════════════════════════════════════════════════
    private double computeVariation(Map<String, Object> curr, Map<String, Object> prev, String key) {
        try {
            double c = toDouble(curr.get(key));
            double p = toDouble(prev.get(key));
            if (p == 0) return c > 0 ? 100.0 : 0.0;
            return ((c - p) / Math.abs(p)) * 100.0;
        } catch (Exception e) { return 0; }
    }

    private boolean isVariationAnormale(Map<String, Object> curr, Map<String, Object> prev) {
        return Math.abs(computeVariation(curr, prev, "totalMontantCredit")) > 50
                || Math.abs(computeVariation(curr, prev, "totalMontantImpaye")) > 50;
    }

    private double toDouble(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return 0; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UTILITAIRES — Extraction XML
    // ══════════════════════════════════════════════════════════════════════════
    private Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s.replace(",", ".")); }
        catch (NumberFormatException e) { return null; }
    }

    private Map<String, String> extractEssentialFields(String xml) {
        Map<String, String> result = new HashMap<>();
        Pattern rootAttr = Pattern.compile("<(?![?!/])\\w+\\s+([^>]*)>", Pattern.DOTALL);
        Matcher rm = rootAttr.matcher(xml);
        if (rm.find()) {
            Pattern attr = Pattern.compile("(\\w+)\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
            Matcher am = attr.matcher(rm.group(1));
            while (am.find()) {
                switch (am.group(1).toLowerCase()) {
                    case "code"      -> result.put("code",       am.group(2));
                    case "periode"   -> result.put("periode",    am.group(2));
                    case "datedebut" -> result.put("date_debut", am.group(2));
                    case "datefin"   -> result.put("date_fin",   am.group(2));
                }
            }
        }
        for (String tag : Arrays.asList("code", "periode", "date_debut", "date_fin")) {
            if (!result.containsKey(tag)) {
                String value = extractTagOutsideLignes(xml, tag);
                if (value != null) result.put(tag, value);
            }
        }
        return result;
    }

    private String extractTagOutsideLignes(String xml, String tagName) {
        List<String> variants = switch (tagName.toLowerCase()) {
            case "code"        -> Arrays.asList("code", "CodeDeclaration", "codeDeclaration");
            case "periode"     -> Arrays.asList("periode", "Periode");
            case "date_debut"  -> Arrays.asList("date_debut", "DateDebut", "dateDebut");
            case "date_fin"    -> Arrays.asList("date_fin", "DateFin", "dateFin");
            case "nombrelignes"-> Arrays.asList("nombreLignes", "NombreLignes", "nombre_lignes");
            default            -> List.of(tagName);
        };
        for (String variant : variants) {
            Pattern p = Pattern.compile("<" + variant + "\\b[^>]*>(.*?)</" + variant + ">",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(xml);
            while (m.find()) {
                int start = m.start();
                String before = xml.substring(0, start);
                if (before.lastIndexOf("<Ligne") > before.lastIndexOf("</Ligne>")) continue;
                String val = m.group(1).trim();
                if (!val.isEmpty()) return val;
            }
        }
        return null;
    }

    private List<Map<String, String>> extractLignes(String xml) {
        List<Map<String, String>> lignes = new ArrayList<>();
        Pattern lignePattern = Pattern.compile("<Ligne\\b[^>]*>(.*?)</Ligne>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher m = lignePattern.matcher(xml);
        while (m.find()) {
            Map<String, String> fields = new HashMap<>();
            Pattern tagPattern = Pattern.compile("<([a-zA-Z_][\\w\\-]*)\\b[^>]*>(.*?)</\\1>",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher tm = tagPattern.matcher(m.group(1));
            while (tm.find()) fields.put(tm.group(1), tm.group(2).trim());
            lignes.add(fields);
        }
        return lignes;
    }

    private Map<String, Object> buildAnomalie(int ligne, String client, String type, String severity, String detail) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("ligne",    ligne);
        a.put("client",   client);
        a.put("type",     type);
        a.put("severity", severity);
        a.put("detail",   detail);
        return a;
    }

    private AiValidationResult reject(List<String> anomalies, int score) {
        AiValidationResult r = new AiValidationResult();
        r.setValid(false);
        r.setScore(score);
        r.setRecommendation("REJECT");
        r.setAnomalies(anomalies);
        return r;
    }
}