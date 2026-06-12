"""
seed_real_data.py — Insertion des données réelles BCT en UTF-8
Exécuter depuis le container ml-service :
  python scripts/seed_real_data.py
"""
import os
import pymysql
from datetime import datetime

DB_HOST = os.getenv("DB_HOST", "mysql")
DB_PORT = int(os.getenv("DB_PORT", "3306"))
DB_USER = os.getenv("DB_USER", "root")
DB_PASS = os.getenv("DB_PASSWORD", "wifak2024")
DB_NAME = "wifak_validation"

# Cycle complet : (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action)
SEED_DATA = [

    # ══════════════════════════════════════════════════════════════
    # BCT_01 — Risques de Change et de Taux
    # ══════════════════════════════════════════════════════════════

    # Cycle 1 — Montant négatif
    (101, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "MontantExposition négatif sur 3 lignes (CPT0001, CPT0002, CPT0005). Les montants d'exposition ne peuvent pas être négatifs selon les règles BCT.",
     '2026-05-01 09:15:00'),
    (101, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : montants négatifs convertis en valeur absolue. CPT0001: -250000 → 250000, CPT0002: -180000 → 180000, CPT0005: -95000 → 95000. Vérification effectuée sur toutes les lignes.",
     '2026-05-01 10:30:00'),
    (101, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-05-01 11:00:00'),

    # Cycle 2 — Taux négatif
    (102, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "TauxApplique négatif ou nul sur lignes 4, 7, 12. Le taux d'intérêt doit être positif et compris entre 0.1% et 25% selon la politique BCT.",
     '2026-05-02 08:45:00'),
    (102, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : taux négatifs remplacés par le taux de référence BCT 4.25%. Ligne 4: -1.5% → 4.25%, Ligne 7: 0% → 4.25%, Ligne 12: -0.8% → 4.25%. Fichier XML régénéré.",
     '2026-05-02 10:00:00'),
    (102, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-05-02 10:30:00'),

    # Cycle 3 — Devise invalide
    (103, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "Code devise invalide XXX détecté sur 5 enregistrements. Seuls les codes ISO 4217 sont acceptés : TND, EUR, USD, GBP.",
     '2026-05-03 09:00:00'),
    (103, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : devise XXX remplacée par TND sur les 5 lignes concernées. Vérification XSD effectuée, fichier conforme au schéma BCT_01.",
     '2026-05-03 11:00:00'),
    (103, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-05-03 11:45:00'),

    # Cycle 4 — Limite dépassée
    (104, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "LimiteExposition dépassée pour 2 contreparties. CPT_AA001 : exposition 15M TND vs limite autorisée 10M TND. CPT_AA002 : exposition 8.5M TND vs limite 7M TND.",
     '2026-05-04 14:00:00'),
    (104, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : limites révisées suite à approbation comité risques. CPT_AA001 limite portée à 16M, CPT_AA002 à 9M. Documents d'approbation joints.",
     '2026-05-05 09:00:00'),
    (104, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-05-05 10:00:00'),

    # Cycle 5 — Multiples anomalies
    (105, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "Multiples anomalies BCT_01 : MontantExposition nul sur ligne 3, devise vide sur lignes 8 et 11, taux négatif sur ligne 15.",
     '2026-05-06 10:00:00'),
    (105, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction complète : montant nul ligne 3 → 50000 TND. Devise lignes 8 et 11 → TND. Taux ligne 15 → 3.5%. Toutes anomalies corrigées. Fichier BCT_01 complet et conforme.",
     '2026-05-06 17:00:00'),
    (105, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-05-07 09:00:00'),

    # ══════════════════════════════════════════════════════════════
    # BCT_002 — Opérations de Change Quotidiennes
    # ══════════════════════════════════════════════════════════════

    (201, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "Position nette calculée incorrectement pour EUR ligne 2. Attendu : PositionAchat 1250000 - PositionVente 980000 = 270000 TND. Déclaré : 250000 TND.",
     '2026-05-07 09:30:00'),
    (201, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : erreur de saisie PositionNette EUR. Valeur corrigée de 250000 à 270000 TND. Recalcul vérifié pour toutes les devises du fichier CSV.",
     '2026-05-07 11:00:00'),
    (201, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-05-07 11:30:00'),

    (202, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "Limite autorisée dépassée pour USD : position nette 4.8M TND, limite BCT 4M TND. Dépassement de 20% non justifié.",
     '2026-05-08 10:00:00'),
    (202, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : erreur de conversion devise. Montant 4.8M était en USD non en TND. Après conversion taux 3.12 : valeur correcte. Fichier corrigé avec devise explicite.",
     '2026-05-08 13:00:00'),
    (202, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-05-08 14:00:00'),

    (203, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "Champ CodeDevise vide sur 8 lignes du fichier CSV. Ce champ est obligatoire pour toutes les opérations de change BCT.",
     '2026-05-09 09:00:00'),
    (203, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : CodeDevise renseigné sur les 8 lignes. EUR pour 5 opérations virements SEPA, USD pour 2 importations, GBP pour 1 exportation UK.",
     '2026-05-09 10:30:00'),
    (203, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-05-09 11:00:00'),

    # ══════════════════════════════════════════════════════════════
    # BCT_04 — Opérations Bancaires Trimestrielles
    # ══════════════════════════════════════════════════════════════

    (401, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "MontantOperation négatif sur 4 transactions IDs TRX001 TRX004 TRX007 TRX009. Les montants d'opérations bancaires doivent être strictement positifs.",
     '2026-04-02 10:00:00'),
    (401, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : les 4 transactions avec montants négatifs étaient des remboursements. Montants convertis en valeur absolue et type d'opération mis à jour DEBIT vers REMBOURSEMENT.",
     '2026-04-02 14:00:00'),
    (401, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-04-02 15:00:00'),

    (402, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "MontantGarantie supérieur à MontantOperation pour 3 crédits. La garantie ne peut pas dépasser 150% du montant du crédit selon règlementation BCT.",
     '2026-04-05 09:00:00'),
    (402, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : MontantGarantie recalculé à 100% du MontantOperation pour les 3 cas concernés. REF001 : garantie 500000 TND, REF002 : 750000 TND, REF003 : 320000 TND.",
     '2026-04-05 11:30:00'),
    (402, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-04-05 12:00:00'),

    (403, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "DateOperation postérieure à la date de déclaration sur 6 enregistrements. Les opérations déclarées doivent avoir eu lieu avant la date de clôture du trimestre 31 mars 2026.",
     '2026-04-08 10:00:00'),
    (403, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : les 6 dates erronées étaient dues à une erreur de saisie 2026 au lieu de 2025. Dates corrigées selon les dossiers physiques : toutes ramenées avant le 31 mars 2026.",
     '2026-04-08 14:00:00'),
    (403, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-04-08 15:30:00'),

    # ══════════════════════════════════════════════════════════════
    # BCT-05 — Crédits Accordés aux Entreprises
    # ══════════════════════════════════════════════════════════════

    (501, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "MontantImpaye supérieur à MontantCredit sur 5 lignes. Incohérence mathématique : l'impayé ne peut excéder le montant du crédit accordé. Lignes ID 12, 34, 56, 78, 90.",
     '2026-03-01 10:00:00'),
    (501, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : erreur d'inversion des colonnes MontantCredit et MontantImpaye pour les 5 lignes. Après correction ID12 crédit 450000 impayé 380000, ID34 crédit 1200000 impayé 800000. Fichier XML régénéré.",
     '2026-03-01 13:00:00'),
    (501, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-03-01 14:00:00'),

    (502, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "Provision insuffisante pour crédits classe D : taux de provisionnement 15% au lieu des 100% réglementaires. 8 crédits concernés avec encours total 3.2M TND sous-provisionnés.",
     '2026-03-05 09:00:00'),
    (502, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : provision recalculée à 100% pour tous les crédits classe D. Provision totale portée de 480000 TND à 3200000 TND. Source note interne risques 2026-034. Fichier XML mis à jour.",
     '2026-03-05 15:00:00'),
    (502, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-03-06 09:00:00'),

    (503, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "Classe de risque incorrecte pour 12 crédits. Crédits avec DureeRetard supérieur à 180 jours classés en classe B au lieu de classe D selon circulaire BCT 91-24.",
     '2026-03-10 10:00:00'),
    (503, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : classe de risque mise à jour de B vers D pour les 12 crédits avec retard supérieur à 180 jours. Taux de provision ajusté à 100%. Révision conforme à la circulaire BCT 91-24 article 8.",
     '2026-03-10 14:00:00'),
    (503, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-03-10 15:00:00'),

    (504, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "IdClient dupliqué sur 3 paires de lignes IDs 45-46, 112-113, 234-235. Chaque client ne doit apparaître qu'une seule fois par déclaration BCT-05.",
     '2026-03-15 09:00:00'),
    (504, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : les doublons correspondaient à plusieurs crédits du même client. Agrégation : somme MontantCredit, somme MontantImpaye, provision maximale. 3 lignes doublons supprimées et 3 lignes consolidées.",
     '2026-03-15 11:30:00'),
    (504, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-03-15 12:00:00'),

    (505, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "NomClient vide ou absent sur 7 enregistrements. Ce champ est obligatoire selon le schéma XSD BCT-05 version 3.2.",
     '2026-03-20 10:00:00'),
    (505, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : NomClient récupéré depuis la base client de la banque pour les 7 identifiants. Tous les IdClient correspondent à des clients actifs. Champ NomClient renseigné pour toutes les lignes.",
     '2026-03-20 13:00:00'),
    (505, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-03-20 14:00:00'),

    # ══════════════════════════════════════════════════════════════
    # BCT-06 — Créances Classées Trimestrielles
    # ══════════════════════════════════════════════════════════════

    (601, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "Créances de classe 2 déclarées sans provision. La provision minimum de 20% est obligatoire pour classe 2 selon circulaire 91-24.",
     '2026-04-01 09:00:00'),
    (601, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : provision de 20% ajoutée pour toutes les créances classe 2 sur 14 lignes. Montant total provisionné : 2.8M TND soit 20% de 14M TND encours classe 2.",
     '2026-04-01 13:00:00'),
    (601, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-04-01 14:30:00'),

    (602, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "Montant total des créances classées incohérent avec le solde précédent déclaré. Delta de 5.2M TND non justifié entre Q4-2025 et Q1-2026.",
     '2026-04-05 10:00:00'),
    (602, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : le delta de 5.2M TND correspond à 3 nouveaux déclassements en janvier 2026 non inclus dans la déclaration initiale. Créances ajoutées REF_2026_001 2.1M, REF_2026_002 1.8M, REF_2026_003 1.3M.",
     '2026-04-05 15:00:00'),
    (602, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-04-06 09:00:00'),

    (603, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "Date de déclassement postérieure à la date de fin de période pour 9 créances. Impossible de déclasser une créance après la clôture du trimestre.",
     '2026-04-08 09:30:00'),
    (603, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : les 9 dates erronées représentaient la date de saisie et non la date de déclassement réelle. Dates corrigées selon les dossiers physiques avant le 31 mars 2026.",
     '2026-04-08 12:00:00'),
    (603, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-04-08 13:00:00'),

    # ══════════════════════════════════════════════════════════════
    # BCT-07 — Comptes Bancaires Mensuels
    # ══════════════════════════════════════════════════════════════

    (701, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "Solde débiteur non autorisé sur comptes d'épargne type CPT_EP. 5 comptes d'épargne avec solde négatif, contraire aux règles BCT pour ce type de compte.",
     '2026-05-02 09:00:00'),
    (701, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : les 5 comptes avec solde négatif reclassifiés de CPT_EP vers CPT_CC compte courant. Les comptes courants peuvent avoir solde débiteur sous réserve autorisation découvert.",
     '2026-05-02 13:00:00'),
    (701, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-05-02 14:00:00'),

    (702, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "Format IBAN invalide pour 8 comptes. Les IBANs tunisiens doivent commencer par TN59 suivi de 20 chiffres. Structure incorrecte détectée sur les lignes 23, 45, 67, 89, 112, 134, 156, 178.",
     '2026-05-05 10:00:00'),
    (702, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : IBANs recalculés selon la norme ISO 13616 pour les 8 comptes. Utilisation de l'outil de calcul BCT pour les clés de contrôle. Tous les IBANs vérifiés conformes.",
     '2026-05-05 13:30:00'),
    (702, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-05-05 14:00:00'),

    (703, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "Nombre total de comptes déclaré 1245 ne correspond pas au comptage réel 1238. Écart de 7 comptes non justifié.",
     '2026-05-08 09:00:00'),
    (703, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : 7 comptes en doublon identifiés et supprimés, même IBAN, même titulaire, ouverture même date. NombreTotalComptes recalculé automatiquement à 1238. Rapport de déduplication joint.",
     '2026-05-08 11:00:00'),
    (703, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-05-08 12:00:00'),

    # ══════════════════════════════════════════════════════════════
    # Rejets supplémentaires variés
    # ══════════════════════════════════════════════════════════════

    (801, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "Période déclarée 2026-01 ne correspond pas à la période de génération 2025-12. Incohérence entre entête fichier XML et balise Periode.",
     '2026-05-10 09:00:00'),
    (801, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : erreur de saisie manuelle de la période dans l'entête. Période corrigée de 2026-01 à 2025-12 dans la balise Periode. Le reste du fichier était correct.",
     '2026-05-10 10:30:00'),
    (801, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-05-10 11:00:00'),

    (802, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "Structure XML non conforme au schéma XSD BCT : balise MontantBrut manquante dans l'entête sur 23 enregistrements. Ce champ est requis minOccurs=1.",
     '2026-05-11 09:00:00'),
    (802, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : balise MontantBrut ajoutée pour les 23 enregistrements. Valeur calculée comme somme des montants détaillés. Validation XSD locale effectuée avant resoumission.",
     '2026-05-11 11:00:00'),
    (802, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-05-11 12:00:00'),

    (803, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "Taux de provision classe C inférieur au minimum réglementaire : 12% déclaré au lieu de 50% minimum requis pour la classe C selon circulaire BCT 2019-07.",
     '2026-05-12 09:00:00'),
    (803, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : taux de provision classe C recalculé à 50% pour les 6 créances concernées. Provision totale portée de 3.6M TND à 15M TND. Validation par comité des risques.",
     '2026-05-12 15:00:00'),
    (803, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-05-13 09:00:00'),

    (804, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "CodeTypeCredit invalide sur 4 lignes : valeur CREDIT_INV non reconnue. Valeurs acceptées : CREDIT_IMM, CREDIT_CONSOMM, CREDIT_INVEST, CREDIT_EXPLOIT, CREDIT_TRESOR.",
     '2026-05-13 10:00:00'),
    (804, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction : CodeTypeCredit CREDIT_INV remplacé par CREDIT_INVEST crédit d'investissement pour les 4 lignes. Vérification dans le référentiel produits de la banque.",
     '2026-05-13 12:00:00'),
    (804, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-05-13 13:00:00'),

    (805, 'REJECT',   'EN_VALIDATION', 'REJETEE',       'ridha_labaoui',
     "Soldes négatifs sur CPT0002 et CPT0010, devise vide sur CPT0003, taux négatif sur CPT0012. Multiples anomalies détectées dans le fichier BCT_01.",
     '2026-05-14 09:00:00'),
    (805, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', 'miral',
     "Correction complète : montants négatifs CPT0002 -340000 vers 340000 et CPT0010 -125000 vers 125000. Devise CPT0003 renseignée TND. Taux CPT0012 -1.2% vers 2.8% taux marché. Fichier XML régénéré.",
     '2026-05-14 13:00:00'),
    (805, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       'ridha_labaoui', None, '2026-05-14 14:00:00'),
]


def main():
    print(f"Connecting to {DB_HOST}:{DB_PORT} ...")
    conn = pymysql.connect(
        host=DB_HOST, port=DB_PORT,
        user=DB_USER, password=DB_PASS,
        database=DB_NAME,
        charset='utf8mb4',
        use_unicode=True
    )
    cursor = conn.cursor()

    sql = """
        INSERT INTO validation_logs
            (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action)
        VALUES (%s, %s, %s, %s, %s, %s, %s)
    """

    inserted = 0
    errors   = 0

    for row in SEED_DATA:
        try:
            cursor.execute(sql, row)
            inserted += 1
        except Exception as e:
            print(f"Error inserting row {row[0]} {row[1]}: {e}")
            errors += 1

    conn.commit()
    cursor.close()
    conn.close()

    print(f"\nDone: {inserted} inserted, {errors} errors")

    # Count totals
    conn2 = pymysql.connect(host=DB_HOST, port=DB_PORT, user=DB_USER,
                             password=DB_PASS, database=DB_NAME, charset='utf8mb4')
    c2 = conn2.cursor()
    c2.execute("SELECT COUNT(*) FROM validation_logs WHERE action='REJECT' AND commentaire IS NOT NULL AND LENGTH(commentaire) > 20")
    rejets = c2.fetchone()[0]
    c2.execute("SELECT COUNT(*) FROM validation_logs WHERE action='SUBMIT' AND commentaire IS NOT NULL AND LENGTH(commentaire) > 10")
    corrections = c2.fetchone()[0]
    conn2.close()
    print(f"Total in DB: {rejets} rejets, {corrections} corrections")


if __name__ == "__main__":
    main()
