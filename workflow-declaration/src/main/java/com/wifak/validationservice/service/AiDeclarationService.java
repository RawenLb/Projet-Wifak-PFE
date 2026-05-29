package com.wifak.validationservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifak.validationservice.dto.AiValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

/**
 * AiDeclarationService Ã¢â‚¬â€ Validation 100% rÃƒÂ¨gles mÃƒÂ©tier locale (sans IA / sans cloud)
 * Banque Wifak Ã¢â‚¬â€ Plateforme de gestion des dÃƒÂ©clarations rÃƒÂ©glementaires BCT
 *
 * Conforme au Cahier des Charges (BF10, BF15, BF16, BF17) :
 *  - BF10 : ContrÃƒÂ´le de conformitÃƒÂ© (structure + mÃƒÂ©tier)
 *  - BF15 : DÃƒÂ©tection des anomalies par rÃƒÂ¨gles BCT
 *  - BF16 : Score de risque calculÃƒÂ© localement
 *  - BF17 : Aide ÃƒÂ  la correction via templates de motifs de rejet
 *
 * Ã¢Å“â€¦ ZÃƒÂ©ro dÃƒÂ©pendance externe Ã¢â‚¬â€ InstantanÃƒÂ© Ã¢â‚¬â€ SÃƒÂ©curisÃƒÂ© (donnÃƒÂ©es jamais transmises)
 * Seuil de validation : score Ã¢â€°Â¥ 70 Ã¢â€ â€™ VALIDÃƒâ€°E | score < 70 Ã¢â€ â€™ REJETÃƒâ€°E
 */
@Service
public class AiDeclarationService {

    private static final Logger log = LoggerFactory.getLogger(AiDeclarationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Ã¢â€â‚¬Ã¢â€â‚¬ Seuils de validation Ã¢â€â‚¬Ã¢â€â‚¬
    private static final int SCORE_INITIAL          = 100;
    private static final int SCORE_MIN              = 0;
    private static final int SCORE_VALIDATION_SEUIL = 70;
    private static final int PENALITE_ENTETE        = 10;
    private static final int PENALITE_LIGNES_MAX    = 60;
    private static final int PENALITE_LIGNES_UNIT   = 8;
    private static final int PENALITE_FICTIF        = 15;
    private static final int PENALITE_VIDE          = 30;
    private static final int PENALITE_INCOHERENCE   = 15;
    private static final int PENALITE_CRITIQUE      = 25;
    private static final int PENALITE_MAJEURE       = 10;
    private static final int PENALITE_TAUX_ELEVE    = 20;
    private static final int PENALITE_TAUX_MOYEN    = 15;
    private static final double TAUX_IMPAYE_ELEVE   = 0.50;
    private static final double TAUX_IMPAYE_MOYEN   = 0.30;
    private static final double TOLERANCE_MONTANT   = 0.01;
    private static final int RISK_SCORE_FAIBLE      = 70;
    private static final int RISK_SCORE_MOYEN       = 40;

    // Ã¢â€â‚¬Ã¢â€â‚¬ Taux de provision rÃƒÂ©glementaires BCT par classe de risque Ã¢â€â‚¬Ã¢â€â‚¬
    private static final Map<String, Double> TAUX_PROVISION = Map.of(
            "A", 0.05, "B", 0.10, "C", 0.20, "D", 0.50
    );

    // Ã¢â€â‚¬Ã¢â€â‚¬ Valeurs autorisÃƒÂ©es par type de dÃƒÂ©claration Ã¢â€â‚¬Ã¢â€â‚¬
    private static final Set<String> TYPES_RISQUE      = Set.of("CHANGE", "TAUX");
    private static final Set<String> TYPES_OPERATION   = Set.of("CREDIT", "DEPOT", "VIREMENT", "GARANTIE", "EMISSION");
    private static final Set<String> STATUTS_OPERATION = Set.of("ACTIF", "CLOTURE", "EN_COURS", "CONTENTIEUX");
    private static final Set<String> CLASSES_RISQUE    = Set.of("A", "B", "C", "D");
    private static final Set<String> TYPES_CLIENT      = Set.of("ENTREPRISE", "PME", "TPE", "GE", "STARTUP");
    private static final Set<String> TYPES_CONTREP     = Set.of("ENTREPRISE", "BANQUE", "ETAT", "PARTICULIER");
    private static final Set<String> TYPES_ENGAGEMENT  = Set.of("BILAN", "HORS_BILAN");
    // POINT D'ENTRÃƒâ€°E PRINCIPAL Ã¢â‚¬â€ BF10 + BF15
    // Validation complÃƒÂ¨te par rÃƒÂ¨gles mÃƒÂ©tier Ã¢â‚¬â€ rÃƒÂ©sultat instantanÃƒÂ©
    public AiValidationResult analyzeDeclaration(String contenu, String nomFichier) {
        log.info("Ã¢Å¡Â¡ [BF10/BF15] Validation rÃƒÂ¨gles mÃƒÂ©tier: {}", nomFichier);
        String format = detectFormat(nomFichier, contenu);
        log.info("Ã°Å¸â€œâ€ž Format dÃƒÂ©tectÃƒÂ©: {}", format);

        // Ãƒâ€°tape 1 : Validation structurelle
        List<String> structureErrors = validateStructureOnly(contenu, format);
        if (!structureErrors.isEmpty()) {
            log.warn("Ã¢ÂÅ’ Structure invalide: {}", structureErrors);
            return reject(structureErrors, 0);
        }

        // Ãƒâ€°tape 2 : Validation mÃƒÂ©tier complÃƒÂ¨te
        return validateBusinessRules(contenu, nomFichier, format);
    }
    // VALIDATION MÃƒâ€°TIER PRINCIPALE
    private AiValidationResult validateBusinessRules(String contenu, String nomFichier, String format) {
        String typeDeclaration = detectTypeDeclaration(nomFichier, contenu);
        log.info("Ã°Å¸â€œâ€¹ Type dÃƒÂ©claration dÃƒÂ©tectÃƒÂ©: {}", typeDeclaration);

        List<String> anomalies = new ArrayList<>();
        int score = SCORE_INITIAL;

        // Ã¢â€â‚¬Ã¢â€â‚¬ 1. Validation de l'en-tÃƒÂªte Ã¢â€â‚¬Ã¢â€â‚¬
        Map<String, String> entete = extractEssentialFields(contenu);
        List<String> enteteErrors = validateEntete(entete, contenu);
        anomalies.addAll(enteteErrors);
        score -= enteteErrors.size() * PENALITE_ENTETE;

        // Ã¢â€â‚¬Ã¢â€â‚¬ 2. Extraction et validation des lignes Ã¢â€â‚¬Ã¢â€â‚¬
        List<Map<String, String>> lignes = extractLignes(contenu);

        // VÃƒÂ©rification du nombre de lignes vs NombreLignes dÃƒÂ©clarÃƒÂ©
        String nombreLignesDeclare = extractTagOutsideLignes(contenu, "nombrelignes");
        if (nombreLignesDeclare != null) {
            try {
                int declared = Integer.parseInt(nombreLignesDeclare.trim());
                if (declared != lignes.size()) {
                    anomalies.add(String.format(
                            "IncohÃƒÂ©rence du nombre de lignes : en-tÃƒÂªte dÃƒÂ©clare %d ligne(s) mais %d ligne(s) trouvÃƒÂ©e(s) dans <Donnees>",
                            declared, lignes.size()));
                    score -= PENALITE_INCOHERENCE;
                }
            } catch (NumberFormatException e) { log.debug("Non-numeric NombreLignes, skipping: {}", e.getMessage()); }
        }

        if (lignes.isEmpty()) {
            anomalies.add("Aucune ligne de donnÃƒÂ©es trouvÃƒÂ©e dans <Donnees> Ã¢â‚¬â€ la dÃƒÂ©claration est vide");
            score -= PENALITE_VIDE;
        } else {
            // Ã¢â€â‚¬Ã¢â€â‚¬ 3. Validation par type de dÃƒÂ©claration Ã¢â€â‚¬Ã¢â€â‚¬
            List<String> lignesErrors = switch (typeDeclaration) {
                case "BCT_01" -> validateBCT01(lignes);
                case "BCT_02" -> validateBCT02(lignes);
                case "BCT_03" -> validateBCT03(lignes);
                case "BCT_04" -> validateBCT04(lignes);
                case "BCT_05", "BCT-05" -> validateBCT05(lignes);
                default -> validateGeneric(lignes);
            };
            anomalies.addAll(lignesErrors);
            // PÃƒÂ©nalitÃƒÂ© proportionnelle : chaque anomalie sur lignes rÃƒÂ©duit le score
            score -= (int) Math.min(PENALITE_LIGNES_MAX, lignesErrors.size() * PENALITE_LIGNES_UNIT);
        }

        // Ã¢â€â‚¬Ã¢â€â‚¬ 4. DÃƒÂ©tection de donnÃƒÂ©es fictives / de test Ã¢â€â‚¬Ã¢â€â‚¬
        List<String> fictifErrors = detectFictiveData(lignes, typeDeclaration);
        anomalies.addAll(fictifErrors);
        score -= fictifErrors.size() * PENALITE_FICTIF;

        // Score final bornÃƒÂ© entre 0 et 100
        score = Math.max(SCORE_MIN, Math.min(SCORE_INITIAL, score));

        boolean valid = score >= SCORE_VALIDATION_SEUIL;
        String recommendation = valid ? "VALIDATE" : "REJECT";

        log.info("Ã¢Å“â€¦ Validation terminÃƒÂ©e Ã¢â‚¬â€ Score: {}/100, DÃƒÂ©cision: {}, Anomalies: {}",
                score, recommendation, anomalies.size());

        AiValidationResult result = new AiValidationResult();
        result.setValid(valid);
        result.setScore(score);
        result.setRecommendation(recommendation);
        result.setAnomalies(anomalies.isEmpty()
                ? List.of("Ã¢Å“â€¦ Aucune anomalie dÃƒÂ©tectÃƒÂ©e Ã¢â‚¬â€ dÃƒÂ©claration conforme aux rÃƒÂ¨gles BCT")
                : anomalies);
        return result;
    }
    // VALIDATION EN-TÃƒÅ TE
    private List<String> validateEntete(Map<String, String> entete, String contenu) {
        List<String> errors = new ArrayList<>();

        String periode  = entete.get("periode");
        String debut    = entete.get("date_debut");
        String fin      = entete.get("date_fin");
        String code     = entete.get("code");

        if (isBlankOrFictive(code)) {
            errors.add("En-tÃƒÂªte : CodeDeclaration absent ou vide");
        }
        if (isBlankOrFictive(periode)) {
            errors.add("En-tÃƒÂªte : PÃƒÂ©riode absente ou vide (format attendu : YYYY-MM)");
        }
        if (isBlankOrFictive(debut)) {
            errors.add("En-tÃƒÂªte : DateDebut absente ou vide");
        } else if (!debut.matches("\\d{4}-\\d{2}-01")) {
            errors.add("En-tÃƒÂªte : DateDebut '" + debut + "' doit ÃƒÂªtre le 1er jour du mois (format : YYYY-MM-01)");
        }
        if (isBlankOrFictive(fin)) {
            errors.add("En-tÃƒÂªte : DateFin absente ou vide");
        } else {
            // VÃƒÂ©rifier que la date de fin est cohÃƒÂ©rente avec la pÃƒÂ©riode
            if (periode != null && !periode.isBlank()) {
                String[] parts = periode.split("-");
                if (parts.length == 2 && !fin.startsWith(periode)) {
                    errors.add("En-tÃƒÂªte : DateFin '" + fin + "' ne correspond pas ÃƒÂ  la pÃƒÂ©riode '" + periode + "'");
                }
            }
        }

        // CohÃƒÂ©rence dÃƒÂ©but / fin
        if (debut != null && fin != null && !debut.isBlank() && !fin.isBlank()) {
            if (debut.compareTo(fin) > 0) {
                errors.add("En-tÃƒÂªte : DateDebut '" + debut + "' est postÃƒÂ©rieure ÃƒÂ  DateFin '" + fin + "'");
            }
        }

        return errors;
    }
    // BCT_01 Ã¢â‚¬â€ Risques de Change et de Taux
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
                    errors.add("Ligne " + num + " : IdClient '" + id + "' en double (doit ÃƒÂªtre unique)");
            }
            checkEnum(num, l, "TypeRisque", TYPES_RISQUE, errors);
            checkPositiveAmount(num, l, "MontantExposition", errors);
            checkPositiveAmount(num, l, "TauxApplique", errors);
            checkDateFormat(num, l, "DateEcheance", errors);
        }
        return errors;
    }
    // BCT_02 Ã¢â‚¬â€ Positions de Change
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

            // VÃƒÂ©rifier PositionNette = PositionAchat - PositionVente
            Double achat  = parseDouble(l.get("PositionAchat"));
            Double vente  = parseDouble(l.get("PositionVente"));
            Double nette  = parseDouble(l.get("PositionNette"));
            if (achat != null && vente != null && nette != null) {
                double expected = achat - vente;
                if (Math.abs(nette - expected) > 0.01) {
                    errors.add(String.format(
                            "Ligne %d : PositionNette (%.2f) Ã¢â€°Â  PositionAchat - PositionVente (%.2f)",
                            num, nette, expected));
                }
            }
        }
        return errors;
    }
    // BCT_03 Ã¢â‚¬â€ Grandes Expositions
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

            // PourcentageFP doit ÃƒÂªtre entre 0 et 100
            Double pct = parseDouble(l.get("PourcentageFP"));
            if (pct != null && (pct <= 0 || pct > 100)) {
                errors.add("Ligne " + num + " : PourcentageFP (" + pct + ") doit ÃƒÂªtre entre 0 et 100");
            }
        }
        return errors;
    }
    // BCT_04 Ã¢â‚¬â€ OpÃƒÂ©rations Bancaires Trimestrielles
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
    // BCT_05 Ã¢â‚¬â€ CrÃƒÂ©dits AccordÃƒÂ©s aux Entreprises
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
                    errors.add("Ligne " + num + " : IdClient '" + id + "' en double (doit ÃƒÂªtre unique)");
            }

            checkEnum(num, l, "ClasseRisque", CLASSES_RISQUE, errors);
            checkEnum(num, l, "TypeClient",   TYPES_CLIENT, errors);
            checkPositiveAmount(num, l, "MontantCredit", errors);
            checkDateFormat(num, l, "DateClassification", errors);

            Double credit    = parseDouble(l.get("MontantCredit"));
            Double impaye    = parseDouble(l.get("MontantImpaye"));
            Double provision = parseDouble(l.get("Provision"));
            String classe    = l.get("ClasseRisque");

            // RÃƒÂ¨gle critique : MontantImpaye Ã¢â€°Â¤ MontantCredit
            if (credit != null && impaye != null && impaye > credit + TOLERANCE_MONTANT) {
                errors.add(String.format(
                        "Ligne %d [%s] CRITIQUE : MontantImpaye (%.2f TND) > MontantCredit (%.2f TND) Ã¢â‚¬â€ incohÃƒÂ©rence mathÃƒÂ©matique",
                        num, id != null ? id : "?", impaye, credit));
            }

            // RÃƒÂ¨gle provision : Provision Ã¢â€°Â¥ MontantImpaye Ãƒâ€” taux(classe)
            if (classe != null && impaye != null && provision != null) {
                Double taux = TAUX_PROVISION.get(classe.toUpperCase());
                if (taux != null) {
                    double mini = impaye * taux;
                    if (provision < mini - TOLERANCE_MONTANT) {
                        errors.add(String.format(
                                "Ligne %d [%s] : Provision insuffisante (%.2f TND) < minimum rÃƒÂ©glementaire BCT (%.2f TND) pour classe %s (taux %.0f%%)",
                                num, id != null ? id : "?", provision, mini, classe, taux * 100));
                    }
                }
            }

            // DureeRetard doit ÃƒÂªtre Ã¢â€°Â¥ 0
            Double retard = parseDouble(l.get("DureeRetard"));
            if (retard != null && retard < 0) {
                errors.add("Ligne " + num + " : DureeRetard (" + retard.intValue() + ") ne peut pas ÃƒÂªtre nÃƒÂ©gatif");
            }

            // ImpayÃƒÂ© doit ÃƒÂªtre Ã¢â€°Â¥ 0
            if (impaye != null && impaye < 0) {
                errors.add("Ligne " + num + " : MontantImpaye (" + impaye + ") ne peut pas ÃƒÂªtre nÃƒÂ©gatif");
            }
        }
        return errors;
    }
    // VALIDATION GÃƒâ€°NÃƒâ€°RIQUE (type inconnu)
    private List<String> validateGeneric(List<Map<String, String>> lignes) {
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < lignes.size(); i++) {
            Map<String, String> l = lignes.get(i);
            int num = i + 1;
            long emptyFields = l.values().stream().filter(this::isBlank).count();
            if (emptyFields > 0) {
                errors.add("Ligne " + num + " : " + emptyFields + " champ(s) vide(s) dÃƒÂ©tectÃƒÂ©(s)");
            }
        }
        return errors;
    }
    // DÃƒâ€°TECTION DE DONNÃƒâ€°ES FICTIVES / DE TEST
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
            // DÃƒÂ©tecter si tous les montants sont ÃƒÂ  0
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
                errors.add("Ligne " + num + " : Tous les montants sont ÃƒÂ  zÃƒÂ©ro Ã¢â‚¬â€ donnÃƒÂ©es probablement fictives ou non initialisÃƒÂ©es");
            }
        }
        return errors;
    }
    // BF15 Ã¢â‚¬â€ RÃƒâ€°SUMÃƒâ€° ANALYTIQUE (pour le tableau de bord)
    public Map<String, Object> buildAiSummary(String contenu, String nomFichier) {
        Map<String, Object> summary = new LinkedHashMap<>();
        String format = detectFormat(nomFichier, contenu);

        if (!"XML".equals(format)) {
            summary.put("format", format);
            summary.put("message", "RÃƒÂ©sumÃƒÂ© analytique disponible uniquement pour les fichiers XML BCT");
            return summary;
        }

        Map<String, String> essentials = extractEssentialFields(contenu);
        summary.put("periode",        essentials.getOrDefault("periode", "Ã¢â‚¬â€"));
        summary.put("dateDebut",       essentials.getOrDefault("date_debut", "Ã¢â‚¬â€"));
        summary.put("dateFin",         essentials.getOrDefault("date_fin", "Ã¢â‚¬â€"));
        summary.put("codeDeclaration", essentials.getOrDefault("code", "Ã¢â‚¬â€"));

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

            if (credit != null && impaye != null && impaye > credit + TOLERANCE_MONTANT) {
                anomalies.add(buildAnomalie(idx, idClient, "MONTANT_IMPAYE_INCOHERENT", "CRITIQUE",
                        String.format("ImpayÃƒÂ© (%.0f TND) > CrÃƒÂ©dit (%.0f TND)", impaye, credit)));
            }
            if (classe != null && impaye != null && provision != null) {
                Double taux = TAUX_PROVISION.get(classe.toUpperCase());
                if (taux != null) {
                    double mini = impaye * taux;
                    if (provision < mini - TOLERANCE_MONTANT) {
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
        summary.put("riskLevel", riskScore >= RISK_SCORE_FAIBLE ? "FAIBLE" : riskScore >= RISK_SCORE_MOYEN ? "MOYEN" : "ELEVE");

        return summary;
    }
    // BF16 Ã¢â‚¬â€ SCORE DE RISQUE
    private int computeRiskScore(List<Map<String, Object>> anomalies, double totalCredit, double totalImpaye) {
        int score = SCORE_INITIAL;
        long critiques = anomalies.stream().filter(a -> "CRITIQUE".equals(a.get("severity"))).count();
        long majeures  = anomalies.stream().filter(a -> "MAJEURE".equals(a.get("severity"))).count();
        score -= (int)(critiques * PENALITE_CRITIQUE);
        score -= (int)(majeures  * PENALITE_MAJEURE);
        if (totalCredit > 0) {
            double tauxImpaye = totalImpaye / totalCredit;
            if (tauxImpaye > TAUX_IMPAYE_ELEVE) score -= PENALITE_TAUX_ELEVE;
            else if (tauxImpaye > TAUX_IMPAYE_MOYEN) score -= PENALITE_TAUX_MOYEN;
        }
        return Math.max(SCORE_MIN, Math.min(SCORE_INITIAL, score));
    }
    // BF15 Ã¢â‚¬â€ COMPARAISON AVEC PÃƒâ€°RIODE PRÃƒâ€°CÃƒâ€°DENTE
    public Map<String, Object> compareWithPrevious(String contenuCurrent, String contenuPrevious) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (contenuPrevious == null || contenuPrevious.isBlank()) {
            result.put("available", false);
            result.put("message", "Aucune dÃƒÂ©claration prÃƒÂ©cÃƒÂ©dente disponible pour comparaison");
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
    // BF17 Ã¢â‚¬â€ TEMPLATES DE MOTIFS DE REJET
    // HELPERS Ã¢â‚¬â€ VÃƒÂ©rification des champs
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
                    "' invalide. Valeurs autorisÃƒÂ©es : " + String.join(", ", allowed));
        }
    }

    private void checkPositiveAmount(int num, Map<String, String> ligne, String field, List<String> errors) {
        String val = ligne.get(field);
        if (!isBlank(val)) {
            Double d = parseDouble(val);
            if (d == null) {
                errors.add("Ligne " + num + " : '" + field + "' = '" + val + "' n'est pas un nombre valide");
            } else if (d <= 0) {
                errors.add("Ligne " + num + " : '" + field + "' (" + d + ") doit ÃƒÂªtre > 0");
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
                errors.add("Ligne " + num + " : '" + field + "' (" + d + ") ne peut pas ÃƒÂªtre nÃƒÂ©gatif");
            }
        }
    }

    private void checkDateFormat(int num, Map<String, String> ligne, String field, List<String> errors) {
        String val = ligne.get(field);
        if (!isBlank(val) && !val.matches("\\d{4}-\\d{2}-\\d{2}")) {
            errors.add("Ligne " + num + " : '" + field + "' = '" + val + "' Ã¢â‚¬â€ format attendu : YYYY-MM-DD");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean isBlankOrFictive(String s) {
        if (isBlank(s)) return true;
        return MOTS_FICTIFS.contains(s.trim().toLowerCase());
    }
    // PRÃƒâ€°-VALIDATION STRUCTURELLE
    private List<String> validateStructureOnly(String contenu, String format) {
        List<String> errors = new ArrayList<>();
        if (contenu == null || contenu.trim().isEmpty()) {
            errors.add("Fichier vide Ã¢â‚¬â€ aucune donnÃƒÂ©e ÃƒÂ  analyser");
            return errors;
        }
        if ("XML".equals(format) && !isWellFormedXml(contenu)) {
            errors.add("XML malformÃƒÂ© Ã¢â‚¬â€ syntaxe invalide (balises non fermÃƒÂ©es, caractÃƒÂ¨res interdits, etc.)");
        }
        if ("CSV".equals(format) && contenu.split("\n").length < 2) {
            errors.add("CSV invalide Ã¢â‚¬â€ au moins une ligne d'en-tÃƒÂªte et une ligne de donnÃƒÂ©es requises");
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
    // DÃƒâ€°TECTION FORMAT ET TYPE
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
    // UTILITAIRES Ã¢â‚¬â€ Comparaison pÃƒÂ©riodes
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
    // UTILITAIRES Ã¢â‚¬â€ Extraction XML
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