-- ══════════════════════════════════════════════════════════════════
-- SEED & FIX — Banque Wifak PFE
-- 1. Données de test pour les tables vides (wifak_PFE)
-- 2. Correction des SQL queries dans wifak_validation
-- ══════════════════════════════════════════════════════════════════

USE wifak_PFE;

-- ──────────────────────────────────────────────────────────────────
-- 1. TABLE banque
-- ──────────────────────────────────────────────────────────────────
INSERT IGNORE INTO banque (id, code_banque, nom_banque, code_agrement, devise) VALUES
(1, 'WIF', 'Banque Wifak International', 'AGR-2010-001', 'TND'),
(2, 'STB', 'Société Tunisienne de Banque', 'AGR-1958-002', 'TND');

-- ──────────────────────────────────────────────────────────────────
-- 2. TABLES it_* (pour BCT_03 — Conformité IT)
-- ──────────────────────────────────────────────────────────────────

-- it_infrastructure
INSERT IGNORE INTO it_infrastructure
  (id, banque_id, periode, date_debut, date_fin,
   nb_serveurs_total, nb_serveurs_actifs, nb_applications_critiques, nb_bases_de_data,
   taux_disponibilite_systemes, taux_disponibilite_cible,
   hebergement_cloud, fournisseur_cloud, derniere_maj)
VALUES
(1, 1, '2026-05', '2026-05-01', '2026-05-31', 45, 43, 12, 8, 99.750, 99.900, 1, 'AWS', '2026-05-15'),
(2, 1, '2026-06', '2026-06-01', '2026-06-30', 45, 44, 12, 8, 99.820, 99.900, 1, 'AWS', '2026-06-10'),
(3, 1, '2026-07', '2026-07-01', '2026-07-31', 46, 45, 13, 8, 99.900, 99.900, 1, 'AWS', '2026-07-05'),
(4, 2, '2026-05', '2026-05-01', '2026-05-31', 30, 29, 8,  5, 99.500, 99.800, 0, NULL,  '2026-05-20'),
(5, 2, '2026-06', '2026-06-01', '2026-06-30', 30, 30, 8,  5, 99.700, 99.800, 0, NULL,  '2026-06-18');

-- it_securite
INSERT IGNORE INTO it_securite
  (id, banque_id, periode,
   nb_vulnerabilites_critiques, nb_vulnerabilites_hautes, nb_vulnerabilites_corrigees,
   taux_correction, antivirus_actif, firewall_actif, chiffrement_donnees, mfa_actif,
   dernier_test_penetration, resultat_test_penetration,
   certification_iso27001, date_expiration_certif)
VALUES
(1, 1, '2026-05', 2, 8, 9,  90.000, 1, 1, 1, 1, '2026-04-15', 'REUSSI', 1, '2027-04-15'),
(2, 1, '2026-06', 1, 5, 6,  100.000,1, 1, 1, 1, '2026-06-01', 'REUSSI', 1, '2027-04-15'),
(3, 1, '2026-07', 0, 3, 3,  100.000,1, 1, 1, 1, '2026-07-01', 'REUSSI', 1, '2027-04-15'),
(4, 2, '2026-05', 5, 12, 10, 83.333, 1, 1, 1, 0, '2026-03-10', 'PARTIEL', 0, NULL),
(5, 2, '2026-06', 3, 9,  9,  100.000,1, 1, 1, 0, '2026-06-05', 'REUSSI', 0, NULL);

-- it_incidents
INSERT IGNORE INTO it_incidents
  (id, banque_id, periode,
   nb_incidents_totaux, nb_incidents_critiques, nb_incidents_resolus, nb_incidents_en_cours,
   delai_moyen_resolution_heures, nb_violations_donnees, nb_attaques_cyber, nb_attaques_bloquees,
   procedure_incident_documentee)
VALUES
(1, 1, '2026-05', 15, 2, 14, 1, 4.50, 0, 3, 3, 1),
(2, 1, '2026-06', 10, 1, 10, 0, 3.20, 0, 2, 2, 1),
(3, 1, '2026-07', 8,  0, 8,  0, 2.80, 0, 1, 1, 1),
(4, 2, '2026-05', 22, 4, 20, 2, 6.00, 1, 5, 4, 1),
(5, 2, '2026-06', 18, 3, 17, 1, 5.50, 0, 4, 4, 1);

-- it_continuite
INSERT IGNORE INTO it_continuite
  (id, banque_id, periode,
   plan_continuite_existe, date_dernier_test, resultat_dernier_test,
   site_secours_dispo, delai_basculement_minutes, rpo_heures, rto_heures,
   frequence_sauvegardes, dernier_test_restauration)
VALUES
(1, 1, '2026-05', 1, '2026-03-15', 'REUSSI', 1, 30, 1.00, 4.00, 'QUOTIDIENNE', '2026-04-01'),
(2, 1, '2026-06', 1, '2026-03-15', 'REUSSI', 1, 30, 1.00, 4.00, 'QUOTIDIENNE', '2026-06-01'),
(3, 1, '2026-07', 1, '2026-06-20', 'REUSSI', 1, 25, 0.50, 2.00, 'QUOTIDIENNE', '2026-07-01'),
(4, 2, '2026-05', 1, '2026-01-10', 'PARTIEL', 1, 60, 2.00, 8.00, 'HEBDOMADAIRE', '2026-02-01'),
(5, 2, '2026-06', 1, '2026-01-10', 'PARTIEL', 1, 60, 2.00, 8.00, 'HEBDOMADAIRE', '2026-06-15');

-- it_rgpd
INSERT IGNORE INTO it_rgpd
  (id, banque_id, periode,
   politique_confidentialite, responsable_protection_donnees,
   nb_demandes_acces, nb_demandes_traitees, nb_violations_signalees,
   consentement_clients_gere, registre_traitements_a_jour, formation_sensibilisation, nb_employes_formes)
VALUES
(1, 1, '2026-05', 1, 'Sami Ben Ali',    12, 12, 0, 1, 1, 1, 145),
(2, 1, '2026-06', 1, 'Sami Ben Ali',    9,  9,  0, 1, 1, 1, 148),
(3, 1, '2026-07', 1, 'Sami Ben Ali',    7,  7,  0, 1, 1, 1, 150),
(4, 2, '2026-05', 1, 'Leila Mansouri',  5,  4,  1, 1, 0, 1, 80),
(5, 2, '2026-06', 1, 'Leila Mansouri',  6,  6,  0, 1, 1, 1, 82);

-- it_audits
INSERT IGNORE INTO it_audits
  (id, banque_id, periode,
   nb_audits_realises, nb_non_conformites_majeures, nb_non_conformites_mineurs,
   nb_actions_correctives_ouvertes, nb_actions_correctives_closes,
   date_dernier_audit_interne, date_dernier_audit_externe, score_conformite_global)
VALUES
(1, 1, '2026-05', 3, 1, 4, 2, 8,  '2026-04-20', '2026-02-15', 88.500),
(2, 1, '2026-06', 2, 0, 2, 1, 9,  '2026-06-10', '2026-02-15', 92.000),
(3, 1, '2026-07', 2, 0, 1, 0, 10, '2026-07-05', '2026-05-20', 95.000),
(4, 2, '2026-05', 2, 3, 7, 5, 4,  '2026-04-15', '2026-01-20', 72.000),
(5, 2, '2026-06', 2, 2, 5, 4, 5,  '2026-06-12', '2026-01-20', 76.500);

-- it_signature
INSERT IGNORE INTO it_signature
  (id, banque_id, periode,
   responsable_nom, responsable_fonction, responsable_email, rssi,
   date_signature, statut, numero_reference, observations)
VALUES
(1, 1, '2026-05', 'Mohamed Trabelsi', 'DSI', 'dsi@wifak.tn', 'Karim Jebali', '2026-06-05', 'DEFINITIF', 'REF-WIF-2026-05', NULL),
(2, 1, '2026-06', 'Mohamed Trabelsi', 'DSI', 'dsi@wifak.tn', 'Karim Jebali', '2026-07-05', 'DEFINITIF', 'REF-WIF-2026-06', NULL),
(3, 1, '2026-07', 'Mohamed Trabelsi', 'DSI', 'dsi@wifak.tn', 'Karim Jebali', '2026-08-05', 'DEFINITIF', 'REF-WIF-2026-07', NULL),
(4, 2, '2026-05', 'Hedi Chaabane',    'DSI', 'dsi@stb.tn',   'Nour Hamdi',   '2026-06-08', 'DEFINITIF', 'REF-STB-2026-05', NULL),
(5, 2, '2026-06', 'Hedi Chaabane',    'DSI', 'dsi@stb.tn',   'Nour Hamdi',   '2026-07-08', 'DEFINITIF', 'REF-STB-2026-06', NULL);

-- ──────────────────────────────────────────────────────────────────
-- 3. TABLE bilan
-- ──────────────────────────────────────────────────────────────────
INSERT IGNORE INTO bilan
  (id, banque_id, annee, trimestre,
   total_actifs, creances_clientele, creances_douteuses, provisions,
   titres_placement, titres_investissement, immobilisations_nettes, autres_actifs,
   total_passifs, depots_demande, depots_epargne, depots_terme, total_depots,
   emprunts_interbancaires, emprunts_subordonnes, autres_passifs, fonds_propres)
VALUES
(1, 1, 2026, 'T1', 4250000.000, 2800000.000, 185000.000, 92500.000,
   320000.000, 480000.000, 95000.000, 275000.000,
   4250000.000, 680000.000, 1250000.000, 1420000.000, 3350000.000,
   280000.000, 150000.000, 120000.000, 350000.000),
(2, 1, 2026, 'T2', 4380000.000, 2920000.000, 190000.000, 95000.000,
   335000.000, 495000.000, 93000.000, 247000.000,
   4380000.000, 710000.000, 1290000.000, 1480000.000, 3480000.000,
   270000.000, 150000.000, 115000.000, 365000.000),
(3, 2, 2026, 'T1', 8500000.000, 5600000.000, 420000.000, 210000.000,
   680000.000, 920000.000, 185000.000, 415000.000,
   8500000.000, 1350000.000, 2480000.000, 2870000.000, 6700000.000,
   580000.000, 300000.000, 220000.000, 700000.000),
(4, 2, 2026, 'T2', 8720000.000, 5750000.000, 435000.000, 217500.000,
   695000.000, 940000.000, 182000.000, 395500.000,
   8720000.000, 1390000.000, 2550000.000, 2960000.000, 6900000.000,
   560000.000, 300000.000, 210000.000, 750000.000);

-- ──────────────────────────────────────────────────────────────────
-- 4. TABLE compte_resultat
-- ──────────────────────────────────────────────────────────────────
INSERT IGNORE INTO compte_resultat
  (id, banque_id, annee, trimestre,
   produit_net_bancaire, charges_exploitation, dotations_provisions,
   resultat_exploitation, charges_exceptionnelles, resultat_net)
VALUES
(1, 1, 2026, 'T1', 125000.000, 68000.000, 12500.000, 44500.000, 2000.000, 42500.000),
(2, 1, 2026, 'T2', 132000.000, 70000.000, 13000.000, 49000.000, 1500.000, 47500.000),
(3, 2, 2026, 'T1', 245000.000, 138000.000, 25000.000, 82000.000, 3500.000, 78500.000),
(4, 2, 2026, 'T2', 258000.000, 142000.000, 26000.000, 90000.000, 3000.000, 87000.000);

-- ──────────────────────────────────────────────────────────────────
-- 5. TABLE ratios_prudentiels
-- ──────────────────────────────────────────────────────────────────
INSERT IGNORE INTO ratios_prudentiels
  (id, banque_id, annee, trimestre,
   ratio_cet1, ratio_tier1, ratio_total_capital,
   ratio_levier, couverture_creances, ratio_transformation)
VALUES
(1, 1, 2026, 'T1', 12.500, 13.200, 15.800, 6.200, 50.000, 118.500),
(2, 1, 2026, 'T2', 12.800, 13.500, 16.100, 6.400, 50.000, 119.200),
(3, 2, 2026, 'T1', 11.200, 12.000, 14.500, 5.800, 50.000, 122.000),
(4, 2, 2026, 'T2', 11.500, 12.300, 14.800, 5.900, 50.000, 121.500);

-- ══════════════════════════════════════════════════════════════════
-- CORRECTION DES SQL QUERIES dans wifak_validation
-- ══════════════════════════════════════════════════════════════════

USE wifak_validation;

-- ── BCT_01 : supprimer le filtre CL_ERR% restrictif ──────────────
UPDATE declaration_types
SET sql_query =
'SELECT
    id_client        AS IdClient,
    nom_client       AS NomClient,
    CASE
        WHEN type_taux IN (''FIXE'',''VARIABLE'',''MIXTE'') THEN ''TAUX''
        ELSE ''CHANGE''
    END              AS TypeRisque,
    montant_encours  AS MontantExposition,
    UPPER(devise)    AS Devise,
    date_echeance    AS DateEcheance,
    taux_contractuel AS TauxApplique
FROM exposition_taux
ORDER BY id_client'
WHERE code = 'BCT_01';

-- ── BCT-05 : remplacer periode hardcodée par filtre sur date ──────
UPDATE declaration_types
SET sql_query =
'SELECT
    id_client        AS IdClient,
    nom_client       AS NomClient,
    montant_credit   AS MontantCredit,
    montant_impaye   AS MontantImpaye,
    classe_risque    AS ClasseRisque,
    provision        AS Provision,
    duree_retard     AS DureeRetard,
    type_client      AS TypeClient,
    date_classification AS DateClassification
FROM credits_entreprises
WHERE date_classification BETWEEN :dateDebut AND :dateFin
ORDER BY id_client'
WHERE code = 'BCT-05';

-- ── BCT-07 : remplacer periode hardcodée par filtre dynamique ─────
UPDATE declaration_types
SET sql_query =
'SELECT
    id_compte        AS IdCompte,
    intitule_compte  AS IntituleCompte,
    solde            AS Solde,
    UPPER(devise)    AS Devise,
    date_ouverture   AS DateOuverture,
    taux_interet     AS TauxInteret
FROM comptes_bancaires
WHERE date_ouverture <= :dateFin
ORDER BY id_compte'
WHERE code = 'BCT-07';

-- ── BCT_03 : correction du filtre date (inf.date_debut >= :dateDebut) ──
-- La query utilise déjà :dateDebut/:dateFin — OK, pas de changement nécessaire
-- Mais on s'assure que les données IT existent (insérées ci-dessus)

-- ── BCT_07 (id=8, sans SQL) : ajouter une query de base ──────────
UPDATE declaration_types
SET sql_query =
'SELECT
    id_compte        AS IdCompte,
    intitule_compte  AS IntituleCompte,
    solde            AS Solde,
    UPPER(devise)    AS Devise,
    date_ouverture   AS DateOuverture,
    taux_interet     AS TauxInteret
FROM comptes_bancaires
WHERE date_ouverture <= :dateFin
ORDER BY id_compte'
WHERE code = 'BCT_07' AND (sql_query IS NULL OR sql_query = '');

-- ══════════════════════════════════════════════════════════════════
-- VÉRIFICATION FINALE
-- ══════════════════════════════════════════════════════════════════
SELECT id, code, LEFT(sql_query, 80) AS sql_preview,
       CASE WHEN sql_query IS NULL THEN 'MANQUANT' ELSE 'OK' END AS statut_sql
FROM declaration_types
ORDER BY id;
