-- ══════════════════════════════════════════════════════════════════
-- Seed BF17 — Données réelles BCT Banque Wifak
-- Cycle complet REJECT → SUBMIT (avec correction) → VALIDATE
-- Compatible avec les types : BCT_01, BCT_002, BCT_04, BCT-05, BCT-06, BCT-07
-- ══════════════════════════════════════════════════════════════════
USE wifak_validation;

-- ══════════════════════════════════════════════════════════════════
-- BLOC 1 — BCT_01 : Risques de Change et de Taux
-- Erreurs typiques : montant négatif, taux hors plage, devise vide
-- ══════════════════════════════════════════════════════════════════

-- Cycle 1
INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(101, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'MontantExposition négatif sur 3 lignes (CPT0001, CPT0002, CPT0005). Les montants d''exposition ne peuvent pas être négatifs selon les règles BCT.',
 '2026-05-01 09:15:00'),
(101, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction appliquée : montants négatifs convertis en valeur absolue pour CPT0001 (-250000 → 250000), CPT0002 (-180000 → 180000), CPT0005 (-95000 → 95000). Vérification effectuée sur toutes les lignes.',
 '2026-05-01 10:30:00'),
(101, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-05-01 11:00:00');

-- Cycle 2
INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(102, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'TauxApplique négatif ou nul sur lignes 4, 7, 12. Le taux d''intérêt doit être positif et compris entre 0.1% et 25% selon la politique BCT.',
 '2026-05-02 08:45:00'),
(102, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : taux négatifs remplacés par le taux de référence BCT (4.25%). Ligne 4 : -1.5% → 4.25%, Ligne 7 : 0% → 4.25%, Ligne 12 : -0.8% → 4.25%. Fichier XML régénéré.',
 '2026-05-02 10:00:00'),
(102, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-05-02 10:30:00');

-- Cycle 3
INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(103, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'Code devise invalide "XXX" détecté sur 5 enregistrements. Seuls les codes ISO 4217 sont acceptés : TND, EUR, USD, GBP, CHF, JPY.',
 '2026-05-03 09:00:00'),
(103, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : devise "XXX" remplacée par "TND" (dinar tunisien) sur les 5 lignes concernées. Vérification XSD effectuée, fichier conforme au schéma BCT_01.',
 '2026-05-03 11:00:00'),
(103, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-05-03 11:45:00');

-- Cycle 4
INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(104, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'LimiteExposition dépassée pour 2 contreparties. CPT_AA001 : exposition 15M TND vs limite autorisée 10M TND. CPT_AA002 : exposition 8.5M TND vs limite 7M TND.',
 '2026-05-04 14:00:00'),
(104, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : après consultation du département risques, les limites ont été révisées à la hausse suite à approbation comité. CPT_AA001 limite portée à 16M, CPT_AA002 à 9M. Documents d''approbation joints.',
 '2026-05-05 09:00:00'),
(104, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-05-05 10:00:00');

-- Cycle 5 (rejet double)
INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(105, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'Multiples anomalies : MontantExposition nul sur ligne 3 (interdit), devise vide sur lignes 8 et 11, taux négatif sur ligne 15.',
 '2026-05-06 10:00:00'),
(105, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Première correction partielle : devise vide → TND sur lignes 8 et 11. MontantExposition ligne 3 mis à jour (50000 TND). Taux ligne 15 non encore corrigé.',
 '2026-05-06 14:00:00'),
(105, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'TauxApplique toujours négatif sur ligne 15 (-2.1%). Correction incomplète.',
 '2026-05-06 15:00:00'),
(105, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction complète : taux ligne 15 remplacé par 3.5% (taux interbancaire). Toutes les anomalies corrigées. Fichier BCT_01 complet et conforme.',
 '2026-05-06 17:00:00'),
(105, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-05-07 09:00:00');

-- ══════════════════════════════════════════════════════════════════
-- BLOC 2 — BCT_002 : Opérations de change quotidiennes
-- Erreurs typiques : position nette incorrecte, devise manquante
-- ══════════════════════════════════════════════════════════════════

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(201, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'Position nette calculée incorrectement : PositionAchat - PositionVente ≠ PositionNette pour EUR (ligne 2). Attendu : 1250000 - 980000 = 270000 TND. Déclaré : 250000 TND.',
 '2026-05-07 09:30:00'),
(201, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : erreur de saisie sur PositionNette EUR. Valeur corrigée de 250000 à 270000 TND (= 1250000 - 980000). Recalcul vérifié pour toutes les devises du fichier CSV.',
 '2026-05-07 11:00:00'),
(201, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-05-07 11:30:00');

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(202, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'Limite autorisée dépassée pour USD : position nette 4.8M TND, limite BCT 4M TND. Dépassement de 20% non justifié.',
 '2026-05-08 10:00:00'),
(202, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : erreur de conversion de devise. Le montant 4.8M était en USD et non en TND. Après conversion (taux 3.12) : 4.8M USD = 14.976M TND. Limite USD distincte de TND. Fichier corrigé avec devise explicite.',
 '2026-05-08 13:00:00'),
(202, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-05-08 14:00:00');

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(203, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'Champ CodeDevise vide sur 8 lignes du fichier CSV. Ce champ est obligatoire pour toutes les opérations de change.',
 '2026-05-09 09:00:00'),
(203, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : CodeDevise renseigné sur les 8 lignes. EUR pour 5 opérations (virements SEPA), USD pour 2 opérations (importations), GBP pour 1 opération (exportation UK).',
 '2026-05-09 10:30:00'),
(203, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-05-09 11:00:00');

-- ══════════════════════════════════════════════════════════════════
-- BLOC 3 — BCT_04 : Opérations Bancaires Trimestrielles
-- Erreurs typiques : montant opération incohérent, date incorrecte
-- ══════════════════════════════════════════════════════════════════

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(401, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'MontantOperation négatif sur 4 transactions (IDs : TRX001, TRX004, TRX007, TRX009). Les montants d''opérations bancaires doivent être strictement positifs.',
 '2026-04-02 10:00:00'),
(401, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : les 4 transactions avec montants négatifs correspondaient à des remboursements. Montants convertis en valeur absolue et type d''opération mis à jour de "DEBIT" vers "REMBOURSEMENT". TRX001: 45000, TRX004: 12500, TRX007: 78000, TRX009: 33000.',
 '2026-04-02 14:00:00'),
(401, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-04-02 15:00:00');

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(402, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'MontantGarantie supérieur à MontantOperation pour 3 crédits. La garantie ne peut pas dépasser 150% du montant du crédit selon règlementation BCT.',
 '2026-04-05 09:00:00'),
(402, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : après vérification des dossiers de crédit, MontantGarantie recalculé à 100% du MontantOperation pour les 3 cas concernés. Crédit REF001 : garantie 500000 TND (= montant crédit), REF002 : 750000 TND, REF003 : 320000 TND.',
 '2026-04-05 11:30:00'),
(402, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-04-05 12:00:00');

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(403, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'DateOperation postérieure à la date de déclaration sur 6 enregistrements. Les opérations déclarées doivent avoir eu lieu avant la date de clôture du trimestre (31/03/2026).',
 '2026-04-08 10:00:00'),
(403, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : les 6 dates erronées étaient dues à une erreur de saisie (2026 au lieu de 2025). Dates corrigées : 04/2026 → 04/2025 pour toutes les lignes concernées. Vérification effectuée sur l''ensemble des 847 enregistrements.',
 '2026-04-08 14:00:00'),
(403, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-04-08 15:30:00');

-- ══════════════════════════════════════════════════════════════════
-- BLOC 4 — BCT-05 : Crédits Accordés aux Entreprises
-- Erreurs typiques : provision insuffisante, MontantImpaye > MontantCredit
-- ══════════════════════════════════════════════════════════════════

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(501, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'MontantImpaye supérieur à MontantCredit sur 5 lignes. Incohérence mathématique : l''impayé ne peut excéder le montant du crédit accordé. Lignes concernées : ID 12, 34, 56, 78, 90.',
 '2026-03-01 10:00:00'),
(501, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : erreur d''inversion des colonnes dans le fichier source SQL. MontantCredit et MontantImpaye intervertis pour les 5 lignes. Après correction : ID12 crédit 450000/impayé 380000, ID34 crédit 1200000/impayé 800000, etc. Fichier XML régénéré.',
 '2026-03-01 13:00:00'),
(501, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-03-01 14:00:00');

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(502, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'Provision insuffisante pour crédits classe D : taux de provisionnement 15% au lieu des 100% réglementaires. 8 crédits concernés avec encours total 3.2M TND sous-provisionnés.',
 '2026-03-05 09:00:00'),
(502, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : provision recalculée à 100% pour tous les crédits classe D (8 lignes). Provision totale portée de 480000 TND à 3200000 TND. Source : note interne risques #2026-034. Fichier XML mis à jour.',
 '2026-03-05 15:00:00'),
(502, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-03-06 09:00:00');

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(503, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'Classe de risque incorrecte pour 12 crédits. Crédits avec DureeRetard > 180 jours classés en classe B au lieu de classe D (selon circulaire BCT 91-24).',
 '2026-03-10 10:00:00'),
(503, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : classe de risque mise à jour de B → D pour les 12 crédits avec retard > 180 jours. Taux de provision ajusté à 100% en conséquence. Révision conforme à la circulaire BCT 91-24 article 8.',
 '2026-03-10 14:00:00'),
(503, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-03-10 15:00:00');

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(504, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'IdClient dupliqué sur 3 paires de lignes (IDs : 45/46, 112/113, 234/235). Chaque client ne doit apparaître qu''une seule fois par déclaration BCT-05.',
 '2026-03-15 09:00:00'),
(504, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : les doublons correspondaient à plusieurs crédits du même client. Agrégation effectuée : somme MontantCredit, somme MontantImpaye, provision maximale. 3 lignes doublons supprimées et 3 lignes consolidées.',
 '2026-03-15 11:30:00'),
(504, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-03-15 12:00:00');

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(505, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'NomClient vide ou absent sur 7 enregistrements. Ce champ est obligatoire selon le schéma XSD BCT-05 v3.2.',
 '2026-03-20 10:00:00'),
(505, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : NomClient récupéré depuis la base client de la banque pour les 7 identifiants. Vérification effectuée : tous les IdClient correspondent à des clients actifs. Champ NomClient renseigné pour toutes les lignes.',
 '2026-03-20 13:00:00'),
(505, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-03-20 14:00:00');

-- ══════════════════════════════════════════════════════════════════
-- BLOC 5 — BCT-06 : Créances Classées Trimestrielles
-- Erreurs typiques : créance mal classée, provision manquante
-- ══════════════════════════════════════════════════════════════════

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(601, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'Créances de classe 2 (actifs nécessitant une attention particulière) déclarées sans provision. La provision minimum de 20% est obligatoire pour classe 2 selon circulaire 91-24.',
 '2026-04-01 09:00:00'),
(601, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : provision de 20% ajoutée pour toutes les créances classe 2 (14 lignes). Montant total provisionné : 2.8M TND (20% de 14M TND d''encours classe 2). Fichier XML mis à jour.',
 '2026-04-01 13:00:00'),
(601, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-04-01 14:30:00');

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(602, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'Montant total des créances classées incohérent avec le solde précédent déclaré. Delta de 5.2M TND non justifié entre Q4/2025 et Q1/2026.',
 '2026-04-05 10:00:00'),
(602, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : le delta de 5.2M TND correspond à 3 nouveaux déclassements effectués en janvier 2026 (non inclus dans la déclaration initiale). Créances ajoutées : REF_2026_001 (2.1M), REF_2026_002 (1.8M), REF_2026_003 (1.3M). Tableau de réconciliation joint.',
 '2026-04-05 15:00:00'),
(602, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-04-06 09:00:00');

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(603, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'Date de déclassement postérieure à la date de fin de période pour 9 créances. Impossible de déclasser une créance après la clôture du trimestre.',
 '2026-04-08 09:30:00'),
(603, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : les 9 dates erronées (01/04/2026 et suivantes) représentaient la date de saisie et non la date de déclassement réelle. Dates corrigées selon les dossiers physiques : toutes les dates ramenées avant le 31/03/2026.',
 '2026-04-08 12:00:00'),
(603, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-04-08 13:00:00');

-- ══════════════════════════════════════════════════════════════════
-- BLOC 6 — BCT-07 : Comptes Bancaires Mensuels
-- Erreurs typiques : solde négatif non autorisé, IBAN invalide
-- ══════════════════════════════════════════════════════════════════

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(701, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'Solde débiteur non autorisé sur comptes d''épargne (type CPT_EP). 5 comptes d''épargne avec solde négatif, ce qui est contraire aux règles BCT pour ce type de compte.',
 '2026-05-02 09:00:00'),
(701, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : les 5 comptes avec solde négatif ont été reclassifiés de CPT_EP vers CPT_CC (compte courant). Les comptes courants peuvent avoir un solde débiteur sous réserve d''autorisation de découvert. Reclassification vérifiée avec le département conformité.',
 '2026-05-02 13:00:00'),
(701, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-05-02 14:00:00');

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(702, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'Format IBAN invalide pour 8 comptes. Les IBANs tunisiens doivent commencer par TN59 suivi de 20 chiffres. Structure incorrecte détectée sur les lignes 23, 45, 67, 89, 112, 134, 156, 178.',
 '2026-05-05 10:00:00'),
(702, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : IBANs recalculés selon la norme ISO 13616 pour les 8 comptes. Utilisation de l''outil de calcul BCT pour les clés de contrôle. Exemples : TN59001234000000012345 → TN5900012340000012345 (correction chiffres de contrôle).',
 '2026-05-05 13:30:00'),
(702, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-05-05 14:00:00');

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(703, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'Nombre total de comptes déclaré (1245) ne correspond pas au comptage réel (1238). Écart de 7 comptes non justifié.',
 '2026-05-08 09:00:00'),
(703, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : 7 comptes en doublon identifiés et supprimés (même IBAN, même titulaire, ouverture même date). Le champ NombreTotalComptes a été recalculé automatiquement à 1238. Rapport de déduplication joint.',
 '2026-05-08 11:00:00'),
(703, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-05-08 12:00:00');

-- ══════════════════════════════════════════════════════════════════
-- BLOC 7 — Rejets supplémentaires variés (enrichissement corpus)
-- ══════════════════════════════════════════════════════════════════

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(801, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'Période déclarée (2026-01) ne correspond pas à la période de génération (2025-12). Incohérence entre l''entête du fichier XML et les données de la balise <Periode>.',
 '2026-05-10 09:00:00'),
(801, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : erreur de saisie manuelle de la période dans l''entête. Période corrigée de 2026-01 à 2025-12 dans la balise <Periode>. Le reste du fichier était correct.',
 '2026-05-10 10:30:00'),
(801, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-05-10 11:00:00');

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(802, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'Structure XML non conforme au schéma XSD BCT : balise <MontantBrut> manquante dans l''entête sur 23 enregistrements. Ce champ est requis (minOccurs="1").',
 '2026-05-11 09:00:00'),
(802, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : balise <MontantBrut> ajoutée pour les 23 enregistrements. Valeur calculée comme somme des montants détaillés. Validation XSD locale effectuée avant resoumission.',
 '2026-05-11 11:00:00'),
(802, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-05-11 12:00:00');

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(803, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'Taux de provision classe C inférieur au minimum réglementaire : 12% déclaré au lieu de 50% minimum requis pour la classe C selon circulaire BCT 2019-07.',
 '2026-05-12 09:00:00'),
(803, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : taux de provision classe C recalculé à 50% pour les 6 créances concernées. Provision totale portée de 3.6M TND (12%) à 15M TND (50%). Validation par le comité des risques le 12/05/2026.',
 '2026-05-12 15:00:00'),
(803, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-05-13 09:00:00');

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(804, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'CodeTypeCredit invalide sur 4 lignes : valeur "CREDIT_INV" non reconnue. Valeurs acceptées : CREDIT_IMM, CREDIT_CONSOMM, CREDIT_INVEST, CREDIT_EXPLOIT, CREDIT_TRESOR.',
 '2026-05-13 10:00:00'),
(804, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction : CodeTypeCredit "CREDIT_INV" remplacé par "CREDIT_INVEST" (crédit d''investissement) pour les 4 lignes. Vérification dans le référentiel produits de la banque.',
 '2026-05-13 12:00:00'),
(804, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-05-13 13:00:00');

INSERT INTO validation_logs (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action) VALUES
(805, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
 'Soldes négatifs sur CPT0002, CPT0010 : devise vide sur CPT0003 : taux négatif sur CPT0012. Multiples anomalies détectées dans le fichier BCT_01.',
 '2026-05-14 09:00:00'),
(805, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
 'Correction complète : montants négatifs CPT0002 (-340000 → 340000) et CPT0010 (-125000 → 125000) convertis en valeur absolue. Devise CPT0003 renseignée (TND). Taux CPT0012 (-1.2% → 2.8% taux marché). Fichier XML régénéré et validé localement.',
 '2026-05-14 13:00:00'),
(805, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', NULL, '2026-05-14 14:00:00');

SELECT 'Seed BF17 réelles terminé !' as status;
SELECT COUNT(*) as total_rejets FROM validation_logs WHERE action = 'REJECT' AND commentaire IS NOT NULL AND LENGTH(commentaire) > 20;
SELECT COUNT(*) as total_corrections FROM validation_logs WHERE action = 'SUBMIT' AND commentaire IS NOT NULL AND LENGTH(commentaire) > 10;
