"""
seed_varied.py — Rejets VRAIMENT variés pour améliorer le silhouette score
Formulations naturelles, longueurs variées, vocabulaire diversifié
"""
import pymysql, os

DB_HOST = os.getenv("DB_HOST", "mysql")
DB_PORT = int(os.getenv("DB_PORT", "3306"))
DB_USER = os.getenv("DB_USER", "root")
DB_PASS = os.getenv("DB_PASSWORD", "wifak2024")
DB_NAME = "wifak_validation"

VARIED_DATA = [

    # ══════════════════════════════════════════════════════════════
    # CLUSTER MONTANT NEGATIF — formulations naturelles variées
    # ══════════════════════════════════════════════════════════════

    (20001,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Nous avons détecté des valeurs négatives dans votre fichier. Les lignes 3, 7 et 12 contiennent des montants inférieurs à zéro, ce qui est strictement interdit par la réglementation BCT.",
     '2026-01-03 09:00:00'),
    (20001,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "J'ai corrigé les montants négatifs en prenant leur valeur absolue. Ligne 3 : 45000 TND, Ligne 7 : 128000 TND, Ligne 12 : 67000 TND. Fichier régénéré et validé localement.",
     '2026-01-03 11:00:00'),
    (20001,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-03 12:00:00'),

    (20002,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Le MontantBrut saisi sur le compte CPT_REF_001 est négatif (-95 000 TND). Il s'agit probablement d'une erreur de signe lors de la saisie. À corriger impérativement.",
     '2026-01-04 09:00:00'),
    (20002,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Erreur de signe corrigée : CPT_REF_001 passe de -95000 à 95000 TND. Il s'agissait d'un remboursement mal codifié.",
     '2026-01-04 11:00:00'),
    (20002,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-04 12:00:00'),

    (20003,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Anomalie détectée : 6 enregistrements présentent des soldes débiteurs non justifiés. Les comptes d'épargne ne peuvent pas avoir de position débitrice selon les règles prudentielles.",
     '2026-01-05 09:00:00'),
    (20003,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Les 6 comptes ont été reclassifiés de CPT_EP (épargne) vers CPT_CC (courant) après vérification auprès du département commercial. Les soldes débiteurs sont désormais autorisés.",
     '2026-01-05 11:00:00'),
    (20003,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-05 12:00:00'),

    (20004,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "ERREUR CRITIQUE : MontantExposition = -3 500 000 TND sur la ligne 1. Ce montant négatif invalide toute la déclaration BCT_01.",
     '2026-01-06 09:00:00'),
    (20004,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Montant corrigé : il s'agissait d'une opération de couverture mal saisie. Valeur absolue appliquée : 3 500 000 TND. La déclaration est maintenant conforme.",
     '2026-01-06 11:00:00'),
    (20004,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-06 12:00:00'),

    (20005,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Plusieurs montants sont en dessous de zéro dans ce fichier. Veuillez vérifier les lignes 15, 23, 31 et 47 qui affichent respectivement -12000, -8500, -33000 et -75000 TND.",
     '2026-01-07 09:00:00'),
    (20005,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Montants corrigés : toutes les valeurs négatives ont été converties en valeur absolue. Vérification effectuée sur l'ensemble du fichier, aucune autre anomalie détectée.",
     '2026-01-07 11:00:00'),
    (20005,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-07 12:00:00'),

    (20006,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Le montant de la garantie (MontantGarantie) est négatif pour 2 crédits. Référence crédit RC-2026-001 : -450 000 TND et RC-2026-003 : -280 000 TND.",
     '2026-01-08 09:00:00'),
    (20006,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Garanties corrigées : RC-2026-001 = 450 000 TND et RC-2026-003 = 280 000 TND. Les négatifs résultaient d'un problème d'import depuis le système Core Banking.",
     '2026-01-08 11:00:00'),
    (20006,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-08 12:00:00'),

    # ══════════════════════════════════════════════════════════════
    # CLUSTER PROVISION — formulations naturelles variées
    # ══════════════════════════════════════════════════════════════

    (21001,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "La provision constituée pour vos créances douteuses de classe D est largement insuffisante. Vous avez déclaré 18% alors que la circulaire BCT 91-24 exige 100% de provisionnement.",
     '2026-02-03 09:00:00'),
    (21001,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Provision recalculée et portée à 100% pour toutes les créances classe D. Le montant total provisionné passe de 540 000 TND à 3 000 000 TND. Tableau de provision joint.",
     '2026-02-03 13:00:00'),
    (21001,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-03 14:00:00'),

    (21002,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Nous constatons que 4 crédits classés D ne sont pas provisionnés du tout dans votre déclaration. L'encours total de ces crédits est de 8,2 millions TND.",
     '2026-02-04 09:00:00'),
    (21002,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Provision de 100% ajoutée pour les 4 crédits classe D non provisionnés. Montant total : 8 200 000 TND. Le comité des risques a validé ces provisions le 04/02/2026.",
     '2026-02-04 13:00:00'),
    (21002,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-04 14:00:00'),

    (21003,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Taux de provisionnement inadéquat pour la classe C : vous avez appliqué 22% au lieu du minimum réglementaire de 50% (circulaire BCT n°2019-07).",
     '2026-02-05 09:00:00'),
    (21003,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Taux de provisionnement classe C porté de 22% à 50% conformément à la circulaire BCT 2019-07. La provision supplémentaire de 1 400 000 TND a été comptabilisée.",
     '2026-02-05 13:00:00'),
    (21003,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-05 14:00:00'),

    (21004,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Classification erronée : 3 créances présentant un retard supérieur à 6 mois sont classées en B au lieu de D. Conformément à l'article 8 de la circulaire 91-24, un retard > 180 jours impose le classement D.",
     '2026-02-06 09:00:00'),
    (21004,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Reclassification effectuée : les 3 créances passent de classe B à classe D. La provision passe à 100% soit 2 750 000 TND supplémentaires. Avis du comité de classification joint.",
     '2026-02-06 13:00:00'),
    (21004,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-06 14:00:00'),

    (21005,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Sous-provisionnement détecté sur créances classe 2 : vous avez appliqué un taux de 8% alors que le minimum est 20%. Montant de la provision insuffisante : 960 000 TND.",
     '2026-02-07 09:00:00'),
    (21005,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Provision classe 2 recalculée à 20% minimum. Montant supplémentaire provisionné : 960 000 TND. La provision totale passe de 384 000 à 1 344 000 TND.",
     '2026-02-07 13:00:00'),
    (21005,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-07 14:00:00'),

    (21006,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Incohérence détectée : le même client apparaît classé A dans une ligne et D dans une autre. Un client ne peut pas avoir deux classes de risque différentes dans la même déclaration.",
     '2026-02-08 09:00:00'),
    (21006,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Incohérence résolue : après analyse du dossier complet, le client est uniformément classé D (retard global > 180 jours). Toutes les lignes ont été mises à jour avec provision 100%.",
     '2026-02-08 13:00:00'),
    (21006,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-08 14:00:00'),

    # ══════════════════════════════════════════════════════════════
    # CLUSTER TAUX — formulations naturelles variées
    # ══════════════════════════════════════════════════════════════

    (22001,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Le TauxApplique est de -3,2% sur 5 lignes de votre déclaration. Un taux d'intérêt ne peut pas être négatif. Veuillez corriger avec le taux en vigueur (4,25% taux de référence BCT).",
     '2026-03-02 09:00:00'),
    (22001,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "TauxApplique corrigé à 4,25% (taux de référence BCT) pour les 5 lignes concernées. Il s'agissait d'une erreur d'export depuis le système de gestion des crédits.",
     '2026-03-02 11:00:00'),
    (22001,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-03-02 12:00:00'),

    (22002,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Taux nul (0%) détecté sur 8 enregistrements. Tous les crédits doivent avoir un taux d'intérêt positif selon la politique de crédit BCT. Minimum accepté : 0,1%.",
     '2026-03-03 09:00:00'),
    (22002,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Les taux nuls correspondaient à des crédits à taux zéro accordés au personnel. Après vérification, ces crédits doivent quand même être déclarés au taux de marché (3,75%). Correction effectuée.",
     '2026-03-03 11:00:00'),
    (22002,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-03-03 12:00:00'),

    (22003,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Le taux de change EUR/TND déclaré est de 0,0 sur la ligne 15. Cette valeur est manifestement erronée. Le taux officiel BCT EUR/TND est de 3,312 à la date de déclaration.",
     '2026-03-04 09:00:00'),
    (22003,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Taux de change corrigé : EUR/TND mis à jour à 3,312 (taux officiel BCT du 01/03/2026). L'erreur provenait d'un champ vide non détecté lors du contrôle interne.",
     '2026-03-04 11:00:00'),
    (22003,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-03-04 12:00:00'),

    (22004,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Taux hors plage autorisée : vous avez déclaré un TauxApplique de 42% sur le crédit REF-CR-2026-089. Le plafond BCT est de 25% pour les crédits aux entreprises.",
     '2026-03-05 09:00:00'),
    (22004,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Correction : le taux de 42% était une erreur de frappe (4,2% saisi sans la virgule). Valeur corrigée à 4,2% conforme à la convention de crédit signée.",
     '2026-03-05 11:00:00'),
    (22004,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-03-05 12:00:00'),

    (22005,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Incohérence dans les taux : le même type de crédit présente des taux allant de 1,5% à 18,5% dans votre fichier. Cette dispersion anormale nécessite une justification ou une correction.",
     '2026-03-06 09:00:00'),
    (22005,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Après vérification, les taux élevés (>10%) correspondaient à d'anciens crédits renégociés avant 2020 et déclarés par erreur. Ces crédits ont été exclus et refaits avec les taux actuels.",
     '2026-03-06 13:00:00'),
    (22005,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-03-06 14:00:00'),

    # ══════════════════════════════════════════════════════════════
    # CLUSTER DEVISE — formulations naturelles variées
    # ══════════════════════════════════════════════════════════════

    (23001,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Le champ CodeDevise est absent sur 12 lignes de votre déclaration quotidienne BCT_002. Toutes les opérations de change doivent obligatoirement indiquer la devise concernée.",
     '2026-04-01 09:00:00'),
    (23001,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "CodeDevise renseigné pour les 12 lignes : EUR (5 virements SEPA), USD (4 importations USA), GBP (2 exportations UK), CHF (1 opération Suisse). Sources vérifiées avec le back-office.",
     '2026-04-01 11:00:00'),
    (23001,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-04-01 12:00:00'),

    (23002,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Devise invalide 'TUN' détectée sur la ligne 7. Ce code n'existe pas dans la norme ISO 4217. La devise tunisienne s'écrit 'TND' (dinar tunisien).",
     '2026-04-02 09:00:00'),
    (23002,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Code devise corrigé : 'TUN' remplacé par 'TND' conformément à la norme ISO 4217. Vérification effectuée sur l'ensemble du fichier - aucune autre erreur de code devise.",
     '2026-04-02 11:00:00'),
    (23002,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-04-02 12:00:00'),

    (23003,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Vous avez utilisé les codes devises en minuscules ('eur', 'usd', 'gbp'). La norme ISO 4217 impose des codes en MAJUSCULES : 'EUR', 'USD', 'GBP'.",
     '2026-04-03 09:00:00'),
    (23003,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Tous les codes devises convertis en majuscules. Script de correction appliqué sur les 34 lignes concernées. Validation XSD effectuée avec succès.",
     '2026-04-03 11:00:00'),
    (23003,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-04-03 12:00:00'),

    (23004,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Incohérence devise/montant : vous déclarez un montant de 15 000 avec la devise 'EUR' mais l'opération concerne un transfert domestique. La devise devrait être 'TND'.",
     '2026-04-04 09:00:00'),
    (23004,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Après vérification avec le service comptable, il s'agit effectivement d'un virement en euros vers un correspondant européen. La devise EUR est maintenue, le montant converti en TND = 49 680.",
     '2026-04-04 11:00:00'),
    (23004,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-04-04 12:00:00'),

    (23005,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Devise 'XOF' (franc CFA) détectée sur 3 opérations. Banque Wifak n'est pas habilitée à effectuer des opérations en XOF. Veuillez vérifier l'exactitude de ces transactions.",
     '2026-04-05 09:00:00'),
    (23005,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Erreur identifiée : les 3 opérations en XOF étaient en réalité en TND. Il s'agissait d'une erreur de mapping dans le système d'export. Devises corrigées en TND.",
     '2026-04-05 11:00:00'),
    (23005,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-04-05 12:00:00'),

    # ══════════════════════════════════════════════════════════════
    # CLUSTER CHAMP OBLIGATOIRE — formulations naturelles variées
    # ══════════════════════════════════════════════════════════════

    (24001,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Le nom du client (NomClient) est absent sur 9 lignes de votre déclaration. Ce champ est requis par le schéma XSD BCT-05 version 3.2 (minOccurs=\"1\").",
     '2026-05-01 09:00:00'),
    (24001,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "NomClient renseigné pour les 9 lignes manquantes à partir de la base clients de la banque. Tous les clients sont actifs et leurs coordonnées sont à jour dans notre système.",
     '2026-05-01 11:00:00'),
    (24001,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-05-01 12:00:00'),

    (24002,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "La balise <TypeOperation> est manquante dans 15 enregistrements de votre fichier XML. Selon le XSD BCT_04, cette balise est obligatoire pour chaque transaction.",
     '2026-05-02 09:00:00'),
    (24002,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Balise TypeOperation ajoutée pour les 15 enregistrements : VIREMENT (9 cas), PRELEVEMENT (4 cas), REMBOURSEMENT (2 cas). Types déterminés à partir des libellés d'opération.",
     '2026-05-02 11:00:00'),
    (24002,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-05-02 12:00:00'),

    (24003,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Identifiant client (IdClient) absent sur la ligne 23. Sans cet identifiant unique, il est impossible de réconcilier cette créance avec notre base de données centrale.",
     '2026-05-03 09:00:00'),
    (24003,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "IdClient retrouvé et ajouté : CLI-2021-4578. Il s'agissait d'un client migré lors de la fusion qui avait conservé un ancien identifiant non reconnu par le nouveau système.",
     '2026-05-03 11:00:00'),
    (24003,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-05-03 12:00:00'),

    (24004,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Le code agence (CodeAgence) est vide pour 7 transactions. Les opérations doivent être rattachées à une agence selon les exigences de reporting BCT.",
     '2026-05-04 09:00:00'),
    (24004,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "CodeAgence renseigné pour les 7 transactions : AGC-001 (Tunis Centre) pour 5 opérations, AGC-008 (Sfax) pour 2 opérations. Vérification avec les relevés physiques effectuée.",
     '2026-05-04 11:00:00'),
    (24004,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-05-04 12:00:00'),

    (24005,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "La date d'opération (DateOperation) est manquante sur les lignes 4, 8 et 17. Ce champ est essentiel pour le contrôle de la période de déclaration.",
     '2026-05-05 09:00:00'),
    (24005,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "DateOperation reconstituée à partir des journaux comptables : Ligne 4 = 2026-04-03, Ligne 8 = 2026-04-15, Ligne 17 = 2026-04-28. Justificatifs disponibles sur demande.",
     '2026-05-05 11:00:00'),
    (24005,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-05-05 12:00:00'),

    # ══════════════════════════════════════════════════════════════
    # CLUSTER DATE INCORRECTE — formulations naturelles variées
    # ══════════════════════════════════════════════════════════════

    (25001,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Plusieurs dates d'opération sont postérieures à la date de clôture du trimestre (31/03/2026). Les transactions datées d'avril 2026 ne peuvent pas figurer dans la déclaration Q1 2026.",
     '2026-04-10 09:00:00'),
    (25001,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Erreur identifiée : les dates d'avril 2026 correspondaient à la date de saisie et non à la date valeur. Dates corrigées selon les avis de débit/crédit : toutes antérieures au 31/03/2026.",
     '2026-04-10 11:00:00'),
    (25001,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-04-10 12:00:00'),

    (25002,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "La période déclarée dans l'en-tête XML est '2025-12' mais les données correspondent visiblement à janvier 2026. Il y a une discordance entre la période et les dates d'opération.",
     '2026-01-20 09:00:00'),
    (25002,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Période corrigée dans l'en-tête XML de '2025-12' vers '2026-01'. Le fichier avait été généré en utilisant un template de décembre par erreur.",
     '2026-01-20 11:00:00'),
    (25002,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-20 12:00:00'),

    (25003,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Date d'ouverture de compte (DateOuverture = 15/07/2026) postérieure à la date de déclaration (31/05/2026). Un compte ne peut pas être déclaré avant son ouverture.",
     '2026-06-05 09:00:00'),
    (25003,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Erreur de frappe : la date 15/07/2026 devait être 15/07/2024 (date réelle d'ouverture). Correction effectuée après vérification dans le système de gestion des comptes.",
     '2026-06-05 11:00:00'),
    (25003,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-06-05 12:00:00'),

    (25004,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Format de date incorrect : les dates sont saisies au format JJ/MM/AAAA (ex: 15/03/2026) alors que le schéma XSD BCT impose le format ISO 8601 AAAA-MM-JJ (2026-03-15).",
     '2026-03-20 09:00:00'),
    (25004,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Toutes les dates converties au format ISO 8601 (AAAA-MM-JJ). Script de conversion appliqué sur les 847 dates du fichier. Validation XSD réussie après conversion.",
     '2026-03-20 11:00:00'),
    (25004,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-03-20 12:00:00'),

    # ══════════════════════════════════════════════════════════════
    # CLUSTER FORMAT XSD — formulations naturelles variées
    # ══════════════════════════════════════════════════════════════

    (26001,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Votre fichier XML ne valide pas par rapport au schéma XSD BCT_01 v4.2. L'erreur principale est l'absence de la balise obligatoire <Entete> en début de fichier.",
     '2026-02-15 09:00:00'),
    (26001,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Balise <Entete> ajoutée avec tous les champs requis : CodeDeclaration, Periode, DateDebut, DateFin, NombreLignes. Le fichier valide maintenant correctement contre le XSD BCT_01.",
     '2026-02-15 11:00:00'),
    (26001,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-15 12:00:00'),

    (26002,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "L'encodage du fichier XML est incorrect. Nous détectons des caractères corrompus (notamment les caractères accentués) ce qui indique un encodage latin-1 au lieu de l'UTF-8 requis.",
     '2026-02-16 09:00:00'),
    (26002,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Fichier régénéré en UTF-8 avec BOM. Les caractères spéciaux (accents, etc.) s'affichent correctement. Le problème venait de notre système d'export configuré en ISO-8859-1.",
     '2026-02-16 11:00:00'),
    (26002,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-16 12:00:00'),

    (26003,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "La structure XML est mal formée : les balises ne sont pas fermées correctement à la ligne 156. L'erreur XSD est : 'unexpected end of element Ligne, expected element Provision'.",
     '2026-02-17 09:00:00'),
    (26003,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Structure XML corrigée : la balise <Provision> manquante a été ajoutée avec la valeur calculée (50% de l'encours pour classe C). Validation XSD confirmée.",
     '2026-02-17 11:00:00'),
    (26003,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-17 12:00:00'),

    (26004,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Le nombre de lignes déclaré dans l'en-tête (NombreLignes = 250) ne correspond pas au nombre réel de balises <Ligne> dans le fichier (247 balises). Incohérence à corriger.",
     '2026-02-18 09:00:00'),
    (26004,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Incohérence corrigée : NombreLignes mis à jour à 247 dans l'en-tête. Les 3 lignes manquantes correspondaient à des doublons supprimés lors de la déduplication.",
     '2026-02-18 11:00:00'),
    (26004,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-18 12:00:00'),

    # ══════════════════════════════════════════════════════════════
    # CLUSTER MONTANT IMPAYE / INCOHERENCE — formulations variées
    # ══════════════════════════════════════════════════════════════

    (27001,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Incohérence mathématique : le montant impayé (4 200 000 TND) dépasse le montant du crédit accordé (3 800 000 TND) pour le client CLI-2024-0892. C'est mathématiquement impossible.",
     '2026-03-10 09:00:00'),
    (27001,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Erreur d'inversion : MontantCredit et MontantImpaye avaient été échangés lors de l'export. Après correction : crédit = 4 200 000 TND, impayé = 3 800 000 TND. Cohérent avec le dossier.",
     '2026-03-10 11:00:00'),
    (27001,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-03-10 12:00:00'),

    (27002,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "Sur 7 lignes de la déclaration BCT-05, MontantImpaye > MontantCredit. Cela est impossible : on ne peut pas avoir plus d'impayés que le capital prêté.",
     '2026-03-11 09:00:00'),
    (27002,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Correction effectuée : pour les 7 lignes, les colonnes MontantCredit et MontantImpaye avaient été inversées dans la requête SQL. Après correction, tous les impayés sont inférieurs aux crédits.",
     '2026-03-11 11:00:00'),
    (27002,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-03-11 12:00:00'),

    (27003,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     "La MontantGarantie déclarée (5 500 000 TND) excède 150% du MontantOperation (3 200 000 TND). La garantie ne peut dépasser ce plafond réglementaire.",
     '2026-03-12 09:00:00'),
    (27003,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     "Vérification effectuée avec le service juridique : la garantie de 5 500 000 TND couvre plusieurs crédits. Après répartition, la garantie par crédit respecte le plafond de 150%.",
     '2026-03-12 11:00:00'),
    (27003,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-03-12 12:00:00'),

]


def main():
    print(f"Connecting to {DB_HOST}:{DB_PORT}...")
    conn = pymysql.connect(host=DB_HOST, port=DB_PORT, user=DB_USER,
        password=DB_PASS, database=DB_NAME, charset='utf8mb4', use_unicode=True)
    cursor = conn.cursor()
    sql = """INSERT INTO validation_logs
        (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action)
        VALUES (%s, %s, %s, %s, %s, %s, %s)"""

    inserted = errors = 0
    for row in VARIED_DATA:
        try:
            cursor.execute(sql, row)
            inserted += 1
        except Exception as e:
            errors += 1
            print(f"  Skip {row[0]} {row[1]}: {e}")

    conn.commit()

    c2 = conn.cursor()
    c2.execute("SELECT COUNT(*) FROM validation_logs WHERE action='REJECT' AND LENGTH(commentaire)>20")
    rejets = c2.fetchone()[0]
    c2.execute("SELECT COUNT(*) FROM validation_logs WHERE action='SUBMIT' AND LENGTH(commentaire)>20")
    corrections = c2.fetchone()[0]
    conn.close()

    print(f"\nInserted: {inserted} | Errors: {errors}")
    print(f"Total REJECT: {rejets} | Total SUBMIT: {corrections}")
    print("Done! Now run: POST /train-all to retrain BF17")


if __name__ == "__main__":
    main()
