"""
seed_1500_real.py — Insertion de 1500 commentaires BCT uniques et réalistes
Exécuter depuis le container ml-service :
  python scripts/seed_1500_real.py

Génère 1500 entrées UNIQUES avec :
- 10 types d'erreurs x 150 cycles chacun (450 entrées REJECT + 450 SUBMIT + 450 VALIDATE... wait actually 1500 total via 3 per cycle x 150 x 10 types but capped at 1500 = 500 cycles x 3)
- Commentaires réalistes BCT (Banque Centrale de Tunisie)
- Batch inserts de 100 lignes
- declaration_ids démarrant à 30000
"""
import os
import random
import pymysql
from datetime import datetime, timedelta

# ── DB config ──────────────────────────────────────────────────────────────────
DB_HOST = os.getenv("DB_HOST", "mysql")
DB_PORT = int(os.getenv("DB_PORT", "3306"))
DB_USER = os.getenv("DB_USER", "root")
DB_PASS = os.getenv("DB_PASSWORD", "wifak2024")
DB_NAME = "wifak_validation"

random.seed(42)  # reproductibilité

# ── Référentiels ───────────────────────────────────────────────────────────────
AGENTS    = ['miral', 'rawena', 'agent_sfax', 'agent_tunis', 'agent_sousse']
MANAGERS  = ['ridha_labaoui', 'manager_sfax', 'manager_tunis']
DEVISES   = ['TND', 'EUR', 'USD', 'GBP', 'JPY', 'CHF']
CLASSES   = ['A', 'B', 'C', 'D']
PROV_TAUX = [5, 8, 10, 12, 15, 18, 20, 22, 25, 30, 50, 100]
BCT_TYPES = ['BCT_01', 'BCT_02', 'BCT_04', 'BCT_05', 'BCT_06', 'BCT_07']

def rand_amount():
    return random.randint(10_000, 9_999_999)

def rand_account():
    return random.choice([
        f"CPT_{random.randint(100,999):03d}",
        f"CLI-{random.randint(1000,9999)}",
        f"CPT_BC{random.randint(10,99)}",
        f"CPT_EP{random.randint(10,99)}",
        f"CC-{random.randint(100,999)}",
        f"CPTE{random.randint(1000,9999)}",
    ])

def rand_account2():
    return rand_account()

def rand_line():
    return random.randint(1, 500)

def rand_prov():
    return random.choice(PROV_TAUX)

def rand_taux():
    return round(random.uniform(0.5, 25.0), 2)

def rand_neg_taux():
    return round(random.uniform(-15.0, -0.1), 2)

def rand_date_2025_2026():
    start = datetime(2025, 1, 1)
    end   = datetime(2026, 12, 31)
    delta = end - start
    return (start + timedelta(days=random.randint(0, delta.days))).strftime('%Y-%m-%d %H:%M:%S')

def rand_date_after(base_str, hours=1, max_hours=8):
    base = datetime.strptime(base_str, '%Y-%m-%d %H:%M:%S')
    h = random.randint(hours, max_hours)
    return (base + timedelta(hours=h)).strftime('%Y-%m-%d %H:%M:%S')

def rand_bad_devise():
    bad = ['XXX', 'ZZZ', 'ABC', 'TDN', 'EU', 'USE', 'LYD', 'MAR', 'GBX', 'XEU', 'DZD', 'SYP']
    return random.choice(bad)

def rand_xsd_field():
    fields = [
        'MontantBrut', 'MontantCredit', 'MontantImpaye', 'CodeDevise',
        'DateOperation', 'TauxApplique', 'ClasseRisque', 'NomClient',
        'IdClient', 'ReferenceCredit', 'MontantProvision', 'DateEcheance',
        'TypeGarantie', 'MontantGarantie', 'CodeAgence', 'IBAN',
        'PositionNette', 'LimiteExposition', 'MontantExposition',
    ]
    return random.choice(fields)

def rand_schema_version():
    return random.choice(['3.1', '3.2', '4.0', '4.1', '2.8', '5.0'])

def rand_ref():
    return f"REF_{random.randint(100000,999999)}"

def rand_trx():
    return f"TRX{random.randint(1000,9999)}"

def rand_period():
    year  = random.choice([2024, 2025, 2026])
    month = random.randint(1, 12)
    return f"{year}-{month:02d}"

def rand_quarter():
    return f"Q{random.randint(1,4)}-{random.choice([2024,2025,2026])}"


# ══════════════════════════════════════════════════════════════════════════════
# TEMPLATES DE REJET — 15+ par type
# ══════════════════════════════════════════════════════════════════════════════

def reject_type1():
    """TYPE 1: Montant négatif"""
    ref = rand_account(); amt = rand_amount(); line = rand_line()
    ref2 = rand_account(); amt2 = rand_amount(); line2 = rand_line()
    bct = random.choice(BCT_TYPES)
    templates = [
        f"Le MontantBrut du compte {ref} est négatif ({amt} TND). Les montants doivent être strictement positifs selon les règles BCT.",
        f"Valeur négative détectée : {ref} affiche -{amt} TND à la ligne {line}. Correction immédiate requise.",
        f"Anomalie sur la ligne {line} : montant de -{amt} TND négatif pour le compte {ref}, correction obligatoire.",
        f"MontantExposition négatif sur {ref} (valeur : -{amt} TND). Les expositions ne peuvent être négatives selon la circulaire BCT.",
        f"Le compte {ref} présente un solde négatif de -{amt} TND à la ligne {line}. Valeur absolue requise.",
        f"Erreur de signe sur la ligne {line} : {ref} déclare -{amt} TND. Seules les valeurs positives sont acceptées.",
        f"MontantCredit négatif détecté sur {ref} (ligne {line}) : -{amt} TND. Ce champ ne peut pas être négatif.",
        f"Rejet {bct} : {ref} enregistre un MontantBrut négatif -{amt} TND. Convertir en valeur absolue.",
        f"Montant négatif interdit : {ref} et {ref2} déclarent respectivement -{amt} TND et -{amt2} TND.",
        f"Ligne {line} rejetée — MontantOperation de -{amt} TND sur {ref}. Les opérations débitrices doivent être saisies positivement.",
        f"Anomalie financière : MontantImpaye de -{amt} TND sur le crédit {ref} (ligne {line}). L'impayé doit être positif.",
        f"Rejet automatique : valeur négative -{amt} TND sur {ref} ligne {line}. Appliquer la valeur absolue avant resoumission.",
        f"Montants négatifs sur {ref} (ligne {line}, -{amt} TND) et {ref2} (ligne {line2}, -{amt2} TND). Deux corrections nécessaires.",
        f"Le schéma XSD {bct} interdit les valeurs négatives. {ref} déclare -{amt} TND, correction requise.",
        f"MontantGarantie négatif sur {ref} : -{amt} TND à la ligne {line}. La garantie doit être une valeur positive.",
        f"Solde débiteur non autorisé : {ref} affiche -{amt} TND. Type de compte incompatible avec un solde négatif.",
        f"Ligne {line} : MontantBrut = -{amt} TND pour {ref}. Ce champ est contraint à des valeurs ≥ 0 selon la norme BCT.",
        f"Erreur critique : -{amt} TND déclaré pour {ref}. Les montants de crédits ne peuvent être inférieurs à zéro.",
        f"Rejet {bct} ligne {line} : MontantExposition {ref} = -{amt} TND. Montant corrigé attendu : {amt} TND (valeur absolue).",
        f"Valeur illicite : MontantCredit = -{amt} TND sur la référence {ref}. Veuillez corriger avant nouvelle soumission.",
    ]
    return random.choice(templates)


def reject_type2():
    """TYPE 2: Provision insuffisante classe D/C"""
    ref = rand_account(); amt = rand_amount(); prov = rand_prov()
    ref2 = rand_account(); amt2 = rand_amount(); prov2 = rand_prov()
    classe = random.choice(['C', 'D'])
    req = 100 if classe == 'D' else 50
    bct = random.choice(BCT_TYPES)
    templates = [
        f"Provision insuffisante pour crédit classe {classe} sur {ref} : taux déclaré {prov}% au lieu des {req}% réglementaires. Encours : {amt} TND.",
        f"Le taux de provision {prov}% appliqué à {ref} (classe {classe}) est inférieur au minimum {req}% fixé par la circulaire BCT 91-24.",
        f"Sous-provisionnement détecté : {ref} (classe {classe}) provisionné à {prov}%, requis {req}%. Écart de provision : {round(amt*(req-prov)/100)} TND.",
        f"Créance {ref} de classe {classe} : provision {amt * prov // 100} TND déclarée alors que la règle BCT exige {amt * req // 100} TND ({req}%).",
        f"Rejet {bct} : {ref} et {ref2} de classe {classe} sous-provisionnés à {prov}%. Taux minimum {req}% non respecté.",
        f"Anomalie provision ligne {rand_line()} : MontantProvision insuffisant pour {ref} (classe {classe}). Déclaré {prov}%, attendu {req}%.",
        f"Classe {classe} — provision obligatoire à {req}% : {ref} déclare seulement {prov}%, soit un manque de {round(amt*(req-prov)/100)} TND.",
        f"Taux de provisionnement non conforme pour {ref} (classe {classe}) : {prov}% déclaré, {req}% requis par la norme prudentielle BCT.",
        f"Provision de {amt * prov // 100} TND insuffisante pour {ref}. Selon la circulaire 91-24, classe {classe} requiert {amt * req // 100} TND ({req}%).",
        f"Rejet prudentiel : {ref} classe {classe} provisionné à {prov}%. La règlementation BCT impose {req}% minimum pour cette classe.",
        f"Sous-provisionnement sur {ref} et {ref2} (classe {classe}) : taux constaté {prov}%, seuil réglementaire {req}%.",
        f"Manque de provision pour {ref} (classe {classe}) : écart de {round(amt*(req-prov)/100)} TND entre provision déclarée ({prov}%) et requise ({req}%).",
        f"Créance classée {classe} — {ref} : provision déclarée {prov}% insuffisante. Circulaire BCT 2019-07 impose {req}%.",
        f"Rejet {bct} : taux provision {prov}% pour {ref} (classe {classe}) inférieur au minimum légal de {req}%. Correction urgente.",
        f"Provision déclarée {amt * prov // 100} TND pour {ref} classe {classe}. Montant réglementaire attendu : {amt * req // 100} TND.",
        f"Déficit de provisionnement : {ref} classe {classe} — {prov}% appliqué vs {req}% réglementaire. Risque prudentiel signalé.",
        f"Anomalie BCT : classe {classe} exige {req}% de provision sur {ref} (encours {amt} TND). Déclaré seulement {prov}%.",
        f"Provision insuffisante sur {ref} (classe {classe}, encours {amt} TND) : {prov}% vs {req}% requis — manque {round(amt*(req-prov)/100)} TND.",
        f"Non-conformité prudentielle : {ref} et {ref2} de classe {classe} sous-provisionnés ({prov}% déclaré, {req}% requis).",
        f"Règle BCT non respectée : crédit {ref} classe {classe} — taux provision {prov}% inférieur au plancher {req}% de la circulaire 91-24.",
    ]
    return random.choice(templates)


def reject_type3():
    """TYPE 3: Taux invalide"""
    ref = rand_account(); taux = rand_neg_taux(); line = rand_line()
    ref2 = rand_account(); taux2 = rand_neg_taux()
    taux_haut = round(random.uniform(30, 99), 2)
    bct = random.choice(BCT_TYPES)
    templates = [
        f"TauxApplique négatif sur {ref} (ligne {line}) : {taux}%. Le taux d'intérêt doit être positif selon la politique BCT.",
        f"Taux invalide : {ref} déclare {taux}% à la ligne {line}. Les taux acceptés sont entre 0.1% et 25% maximum.",
        f"Rejet {bct} : TauxApplique = {taux}% sur {ref} et {taux2}% sur {ref2}. Taux négatifs non autorisés.",
        f"Anomalie de taux à la ligne {line} : {taux}% déclaré pour {ref}. Taux de référence BCT actuel : 8.0%.",
        f"TauxInteret hors plage sur {ref} (ligne {line}) : {taux_haut}% dépasse le plafond légal de 25% fixé par la BCT.",
        f"Taux nul ou négatif détecté : {ref} affiche {taux}% ligne {line}. Corriger avec le taux marché applicable.",
        f"Rejet {bct} ligne {line} : TauxApplique {taux}% pour {ref} invalide. Valeur minimale acceptée : 0.1%.",
        f"Taux d'intérêt {taux}% sur {ref} incohérent avec les conditions du marché. Plage autorisée : 0.1% à 25.0%.",
        f"TauxApplique négatif ({taux}%) sur {ref} et {ref2} ({taux2}%). Deux corrections de taux nécessaires.",
        f"Ligne {line} — TauxApplique invalide : {taux}% pour {ref}. Le taux ne peut être inférieur à 0.1%.",
        f"Rejet prudentiel : taux {taux}% sur {ref} non conforme à la politique de taux BCT (plage 0.1%-25%).",
        f"TauxRefinancement négatif sur {ref} (ligne {line}) : {taux}%. Utiliser le taux directeur BCT en vigueur.",
        f"Taux hors norme : {ref} déclare {taux_haut}% ligne {line}, dépassant le taux maximum autorisé de 25%.",
        f"Anomalie {bct} : TauxApplique {taux}% sur {ref} ligne {line}. Taux corrigé suggéré : taux BCT de référence.",
        f"TauxInteret = {taux}% pour {ref} (ligne {line}) : valeur négative rejetée automatiquement par le système BCT.",
        f"Taux d'application {taux}% sur {ref} inférieur au plancher réglementaire. Correction avec taux marché requise.",
        f"Rejet {bct} : {ref} (ligne {line}) applique un taux de {taux}%, valeur absurde financièrement.",
        f"TauxApplique 0% ou négatif ({taux}%) sur {ref} ligne {line}. Les taux nuls sont prohibés par la norme BCT.",
        f"Taux invalide ligne {line} : {ref} indique {taux}%. Taux de référence suggéré selon circulaire BCT : 7.75%.",
        f"Anomalie taux : {taux}% détecté sur {ref}, {taux2}% sur {ref2}. Taux négatifs systématiquement rejetés.",
    ]
    return random.choice(templates)


def reject_type4():
    """TYPE 4: Devise invalide/vide"""
    ref = rand_account(); line = rand_line()
    ref2 = rand_account(); line2 = rand_line()
    bad_dev = rand_bad_devise()
    n = random.randint(2, 12)
    bct = random.choice(BCT_TYPES)
    templates = [
        f"Code devise invalide '{bad_dev}' détecté sur {ref} (ligne {line}). Seuls TND, EUR, USD, GBP sont acceptés.",
        f"Champ CodeDevise vide sur {n} lignes dont {ref}. Ce champ est obligatoire pour toutes les opérations {bct}.",
        f"Devise '{bad_dev}' non reconnue sur {ref} et {ref2}. Utiliser les codes ISO 4217 valides.",
        f"Rejet {bct} ligne {line} : CodeDevise absent pour {ref}. Renseigner TND ou la devise de transaction.",
        f"Code devise '{bad_dev}' inexistant dans le référentiel BCT sur {n} enregistrements dont {ref}.",
        f"Anomalie devise : {ref} (ligne {line}) et {ref2} (ligne {line2}) sans CodeDevise. Champ requis non renseigné.",
        f"Devise vide ou incorrecte sur {ref} (ligne {line}) : '{bad_dev}' non ISO 4217. Corriger avec TND/EUR/USD.",
        f"Rejet automatique : CodeDevise manquant pour {n} comptes. {ref} et {ref2} sans devise déclarée.",
        f"'{bad_dev}' n'est pas un code devise ISO 4217 valide. {ref} ligne {line} et {n-1} autres lignes à corriger.",
        f"Champ devise obligatoire absent sur {ref} ligne {line}. Conformément au schéma XSD {bct}, CodeDevise est requis.",
        f"Devise invalide '{bad_dev}' sur {ref} : code non supporté. Valeurs acceptées : TND, EUR, USD, GBP, CHF.",
        f"CodeDevise vide pour {n} opérations de change. Lignes {line} à {line + n} sans devise — correction obligatoire.",
        f"Rejet {bct} : {ref} déclare devise '{bad_dev}' inconnue. Référentiel BCT : TND, EUR, USD, GBP uniquement.",
        f"Anomalie devise ligne {line} : '{bad_dev}' non conforme ISO 4217. {ref} et {n-1} autres enregistrements concernés.",
        f"Devise manquante sur {ref} (ligne {line}) et {ref2} (ligne {line2}). Opérations sans devise rejetées par BCT.",
        f"CodeDevise incorrect : '{bad_dev}' détecté sur {ref}. Ce code n'est pas dans la liste des devises autorisées BCT.",
        f"Rejet {bct} : {n} lignes sans CodeDevise dont {ref} (ligne {line}). Renseigner la devise de chaque opération.",
        f"Devise '{bad_dev}' invalide ligne {line} pour {ref}. Correction : utiliser TND pour les opérations en dinars tunisiens.",
        f"CodeDevise absent sur {ref} et {ref2}. Conformément à la circulaire BCT, toute opération doit indiquer sa devise.",
        f"Anomalie {bct} : devise '{bad_dev}' non homologuée sur {n} lignes. {ref} ligne {line} — correction attendue.",
    ]
    return random.choice(templates)


def reject_type5():
    """TYPE 5: Champ obligatoire manquant"""
    ref = rand_account(); line = rand_line()
    field = rand_xsd_field(); field2 = rand_xsd_field()
    n = random.randint(2, 15)
    bct = random.choice(BCT_TYPES)
    schema_v = rand_schema_version()
    templates = [
        f"Champ obligatoire '{field}' manquant sur {ref} (ligne {line}). Ce champ est requis (minOccurs=1) dans le schéma {bct}.",
        f"Rejet XSD {bct} v{schema_v} : '{field}' absent sur {n} enregistrements dont {ref} ligne {line}.",
        f"Validation {bct} échouée : '{field}' vide ou nul sur {ref} ligne {line}. Champ obligatoire non renseigné.",
        f"Champs requis manquants sur {ref} (ligne {line}) : '{field}' et '{field2}'. Deux corrections nécessaires.",
        f"Anomalie XSD : '{field}' nul pour {n} lignes. {ref} (ligne {line}) parmi les enregistrements incomplets.",
        f"Rejet {bct} : champ '{field}' obligatoire absent sur {ref}. Schéma version {schema_v} exige ce champ pour toutes les lignes.",
        f"'{field}' non renseigné sur {n} enregistrements dont {ref} ligne {line}. Correction avant resoumission.",
        f"Validation échouée ligne {line} : '{field}' manquant pour {ref}. Ce champ est indispensable selon la norme BCT.",
        f"Champ '{field}' vide sur {ref} et '{field2}' vide sur {rand_account()}. Deux champs obligatoires à compléter.",
        f"Rejet automatique {bct} : '{field}' absent pour {n} clients dont {ref}. Données incomplètes.",
        f"Schéma {bct} v{schema_v} — '{field}' requis : {n} lignes sans valeur, dont {ref} (ligne {line}).",
        f"Champ obligatoire '{field}' vide sur {ref} ligne {line}. Récupérer la valeur depuis la base clients.",
        f"Rejet {bct} : '{field}' et '{field2}' absents sur {ref}. Ces champs sont de type obligatoire dans le XSD.",
        f"Anomalie données : '{field}' NULL pour {n} enregistrements. {ref} ligne {line} — valeur attendue non nulle.",
        f"Validation XSD {bct} v{schema_v} : '{field}' manquant sur {ref} (ligne {line}). Erreur de schéma détectée.",
        f"'{field}' obligatoire absent sur {ref} et {n-1} autres lignes. Renseigner ce champ depuis le système source.",
        f"Rejet {bct} ligne {line} : champ '{field}' non fourni pour {ref}. La déclaration est incomplète.",
        f"Champ '{field}' manquant — {n} enregistrements dont {ref} ligne {line}. Champ critique pour la validation BCT.",
        f"Anomalie {bct} v{schema_v} : '{field}' absent sur {ref}. Toutes les déclarations doivent inclure ce champ.",
        f"Rejet prudentiel : '{field}' vide pour {ref} (ligne {line}). Ce champ permet l'identification du débiteur.",
    ]
    return random.choice(templates)


def reject_type6():
    """TYPE 6: Date incorrecte"""
    ref = rand_account(); line = rand_line()
    n = random.randint(2, 10)
    bct = random.choice(BCT_TYPES)
    yr_bad = random.choice([2020, 2021, 2019, 2027, 2028, 2099])
    yr_good = random.choice([2025, 2026])
    q = rand_quarter()
    period = rand_period()
    templates = [
        f"DateOperation postérieure à la clôture sur {ref} (ligne {line}). Les opérations de la période {period} ne peuvent pas avoir une date future.",
        f"Date incorrecte : {ref} ligne {line} déclare une DateOperation en {yr_bad} au lieu de {yr_good}. Erreur probable de saisie.",
        f"Rejet {bct} : {n} enregistrements avec DateOperation hors période {period}. {ref} (ligne {line}) parmi les concernés.",
        f"DateEcheance antérieure à DateDebut sur {ref} (ligne {line}). Incohérence temporelle détectée.",
        f"Anomalie de date : DateOperation de {yr_bad} incompatible avec la déclaration {period}. Ligne {line} — {ref}.",
        f"Rejet {bct} : {n} dates incorrectes dont {ref} ligne {line}. Toutes les dates doivent être dans la période {period}.",
        f"DateCreation postérieure à DateOperation sur {ref} (ligne {line}). Ordre chronologique non respecté.",
        f"Date hors plage pour {n} enregistrements : {ref} ligne {line} dépasse la date de clôture de {period}.",
        f"Rejet automatique : DateOperation {yr_bad} saisie au lieu de {yr_good} sur {ref} et {n-1} autres lignes.",
        f"Anomalie temporelle ligne {line} : {ref} déclare une date en {yr_bad}. Correction avec date de {yr_good} attendue.",
        f"Rejet {bct} {q} : {n} dates postérieures à la fin de période. {ref} (ligne {line}) — date à corriger.",
        f"DateEcheance incorrecte sur {ref} (ligne {line}) : date en {yr_bad} incompatible avec la période déclarée.",
        f"Dates futures non autorisées : {ref} et {rand_account()} (ligne {line}) déclarent des dates en {yr_bad}.",
        f"Rejet {bct} : DateOperation de {ref} (ligne {line}) antérieure de plus de 5 ans à la période {period}.",
        f"Anomalie {n} dates : {ref} ligne {line} — DateOperation en {yr_bad} dépasse les bornes de la période {period}.",
        f"Date incohérente sur {ref} : DateCredit > DateEcheance ligne {line}. Le crédit expire avant d'être accordé.",
        f"Rejet {bct} : {n} DateOperation hors période {period}. {ref} (ligne {line}) déclare une date de {yr_bad}.",
        f"DateDeClassement postérieure à fin de trimestre sur {ref} (ligne {line}). Impossible de déclasser après clôture.",
        f"Anomalie de date ligne {line} : {ref} indique {yr_bad} alors que la période déclarée est {period}.",
        f"Rejet {bct} {q} : {ref} (ligne {line}) — date {yr_bad} hors période autorisée. {n} enregistrements affectés.",
    ]
    return random.choice(templates)


def reject_type7():
    """TYPE 7: MontantImpaye > MontantCredit"""
    ref = rand_account(); line = rand_line()
    credit = rand_amount()
    impaye = credit + random.randint(1000, 500_000)
    ref2 = rand_account()
    n = random.randint(2, 8)
    bct = random.choice(BCT_TYPES)
    templates = [
        f"MontantImpaye ({impaye} TND) supérieur au MontantCredit ({credit} TND) sur {ref} ligne {line}. Incohérence mathématique.",
        f"Rejet {bct} : {ref} déclare MontantImpaye {impaye} TND > MontantCredit {credit} TND (ligne {line}). Impossible.",
        f"Anomalie financière sur {ref} (ligne {line}) : impayé {impaye} TND dépasse le crédit accordé {credit} TND.",
        f"Incohérence {ref} ligne {line} : MontantImpaye {impaye} TND doit être ≤ MontantCredit {credit} TND.",
        f"Rejet {bct} : {n} crédits dont {ref} (ligne {line}) — impayé supérieur au capital. Erreur d'inversion de colonnes probable.",
        f"MontantImpaye > MontantCredit sur {ref} et {ref2} : {impaye} TND vs {credit} TND. Deux corrections nécessaires.",
        f"Anomalie {n} lignes : MontantImpaye dépasse MontantCredit. {ref} ligne {line} — {impaye} TND > {credit} TND.",
        f"Rejet prudentiel {bct} : {ref} (ligne {line}) affiche impayé {impaye} TND pour un crédit de {credit} TND.",
        f"Erreur logique sur {ref} ligne {line} : l'impayé {impaye} TND ne peut excéder le montant du crédit {credit} TND.",
        f"Rejet {bct} ligne {line} : {ref} — MontantImpaye ({impaye} TND) > MontantCredit ({credit} TND). Colonnes inversées ?",
        f"Incohérence détectée : {ref} déclare {impaye} TND d'impayé pour un crédit de {credit} TND. Ratio impayé > 100%.",
        f"MontantImpaye {impaye} TND illicite pour {ref} (crédit {credit} TND). L'impayé est plafonné au montant du crédit.",
        f"Rejet {bct} : {n} enregistrements dont {ref} ligne {line} — ratio impayé/crédit > 100% non admis.",
        f"Anomalie mathématique : {ref} (ligne {line}) — {impaye} TND impayé sur {credit} TND de crédit. Ratio {round(impaye/credit*100)}%.",
        f"MontantImpaye > MontantCredit sur {ref} : {impaye} TND vs {credit} TND. Règle BCT : impayé ≤ crédit accordé.",
        f"Rejet {bct} : inversion probable MontantCredit/MontantImpaye sur {ref} ligne {line} ({impaye} vs {credit} TND).",
        f"Valeur incohérente : {ref} (ligne {line}) — impayé {impaye} TND pour crédit {credit} TND. Rapport {round(impaye/credit*100)}% rejeté.",
        f"Anomalie {n} crédits : MontantImpaye > MontantCredit. {ref} (ligne {line}) et {ref2} nécessitent correction.",
        f"Rejet {bct} ligne {line} : {ref} — {impaye} TND impayé impossible pour un encours de {credit} TND.",
        f"Incohérence financière {bct} : {ref} déclare {impaye} TND d'impayé sur {credit} TND de capital. {n} cas similaires.",
    ]
    return random.choice(templates)


def reject_type8():
    """TYPE 8: Position nette incorrecte"""
    ref = rand_account(); line = rand_line()
    achat = rand_amount()
    vente = random.randint(1000, achat - 1000)
    pos_declare = achat - vente + random.randint(1000, 100_000)
    pos_correct = achat - vente
    devise = random.choice(['EUR', 'USD', 'GBP', 'CHF'])
    bct = random.choice(BCT_TYPES)
    templates = [
        f"Position nette {devise} incorrecte : attendu {pos_correct} TND (achat {achat} − vente {vente}), déclaré {pos_declare} TND.",
        f"Rejet {bct} : PositionNette {devise} = {pos_declare} TND ne correspond pas au calcul {achat} - {vente} = {pos_correct} TND.",
        f"Anomalie ligne {line} : PositionNette calculée {pos_correct} TND, déclarée {pos_declare} TND. Écart de {pos_declare - pos_correct} TND.",
        f"PositionNette {devise} incohérente sur {ref} (ligne {line}). Recalcul attendu : {achat} TND - {vente} TND = {pos_correct} TND.",
        f"Rejet {bct} : PositionNette {devise} = {pos_declare} TND alors que PositionAchat({achat}) - PositionVente({vente}) = {pos_correct} TND.",
        f"Erreur de calcul position nette {devise} : déclaré {pos_declare} TND, recalculé {pos_correct} TND. Écart : {pos_declare - pos_correct} TND.",
        f"Position nette incorrecte sur {ref} ligne {line} : {pos_declare} TND au lieu de {pos_correct} TND ({devise}).",
        f"Rejet {bct} ligne {line} : {ref} — PositionNette {pos_declare} TND invalide. Valeur correcte : {pos_correct} TND.",
        f"Incohérence PositionNette {devise} : achat {achat} TND − vente {vente} TND = {pos_correct} TND, déclaré {pos_declare} TND.",
        f"Anomalie {ref} (ligne {line}) : PositionNette {devise} {pos_declare} TND ne vérifie pas la règle PositionAchat − PositionVente.",
        f"Rejet {bct} : PositionNette {devise} = {pos_declare} TND. Calcul correct : {pos_correct} TND. Différence : {pos_declare - pos_correct} TND.",
        f"Position nette {devise} mal calculée sur {ref} : {pos_declare} TND déclaré vs {pos_correct} TND attendu ligne {line}.",
        f"Rejet {bct} : {ref} (ligne {line}) — PositionNette {pos_declare} TND incohérent avec positions achat/vente.",
        f"Erreur position {devise} : {ref} ligne {line} — PositionAchat {achat} − PositionVente {vente} ≠ {pos_declare} TND.",
        f"PositionNette {devise} incorrecte : écart de {pos_declare - pos_correct} TND sur {ref} (ligne {line}). Correction attendue.",
        f"Rejet {bct} : PositionNette {devise} déclarée {pos_declare} TND incompatible avec les montants achat/vente déclarés.",
        f"Anomalie de position : {ref} ligne {line} — {devise} PositionNette {pos_declare} TND, attendu {pos_correct} TND.",
        f"Rejet {bct} : {ref} (ligne {line}) PositionNette {devise} = {pos_declare} TND erronée. Recalcul : {pos_correct} TND.",
        f"Position nette {devise} sur {ref} : valeur {pos_declare} TND incorrecte. Formule : achat {achat} − vente {vente} = {pos_correct} TND.",
        f"Incohérence {ref} ligne {line} : PositionNette {devise} {pos_declare} TND ≠ {pos_correct} TND (achat − vente).",
    ]
    return random.choice(templates)


def reject_type9():
    """TYPE 9: Format XSD non conforme"""
    ref = rand_account(); line = rand_line()
    field = rand_xsd_field()
    bct = random.choice(BCT_TYPES)
    schema_v = rand_schema_version()
    n = random.randint(2, 25)
    bad_val = random.choice(['CREDIT_INV', 'CLASSE_X', 'DEVIZE', 'TYPE_BC', 'STATUT_Z', 'REF_NULL', 'CODE_ERR', 'XSD_BAD'])
    good_val = random.choice(['CREDIT_INVEST', 'CLASSE_D', 'TND', 'TYPE_CC', 'STATUT_A', 'REF_001', 'CODE_01', 'XSD_OK'])
    templates = [
        f"Structure XML non conforme au schéma {bct} v{schema_v} : balise '{field}' manquante (minOccurs=1) sur {n} enregistrements.",
        f"Rejet XSD {bct} v{schema_v} : valeur '{bad_val}' invalide pour {field} sur {ref} ligne {line}. Valeur attendue : '{good_val}'.",
        f"Validation {bct} échouée : {field} de type xs:string mais valeur numérique reçue sur {ref} ligne {line}.",
        f"Non-conformité XSD {bct} v{schema_v} : balise '{field}' dupliquée sur {n} enregistrements. MaxOccurs=1 non respecté.",
        f"Erreur schéma {bct} : '{bad_val}' n'est pas dans l'énumération autorisée pour {field} (ligne {line}).",
        f"Rejet {bct} v{schema_v} : élément racine incorrect — balise {field} hors structure XSD attendue.",
        f"Format XML non conforme : {n} balises '{field}' fermées incorrectement dans le fichier {bct}.",
        f"Validation XSD échouée : {ref} ligne {line} — {field} = '{bad_val}' non conforme au type défini dans {bct} v{schema_v}.",
        f"Erreur XSD {bct} : namespace incorrect dans le fichier XML. Attendu : urn:bct:{bct.lower()}:v{schema_v}.",
        f"Rejet {bct} v{schema_v} : {n} éléments '{field}' avec valeur hors plage définie dans le schéma XSD.",
        f"Non-conformité format : {bct} v{schema_v} — '{field}' de {ref} (ligne {line}) viole la contrainte xs:pattern.",
        f"Validation échouée : {field} = '{bad_val}' sur {ref} ligne {line}. Valeurs autorisées dans {bct} : voir énumération XSD.",
        f"Rejet XSD {bct} v{schema_v} : attribut obligatoire absent sur {n} balises '{field}'. Correction structure XML requise.",
        f"Format non conforme {bct} : {field} ligne {line} dépasse la longueur maximale xs:maxLength définie dans le schéma.",
        f"Erreur XSD {bct} v{schema_v} : '{field}' de {ref} (ligne {line}) contient des caractères interdits (ASCII < 32).",
        f"Rejet {bct} : schéma XSD v{schema_v} — {n} erreurs de validation dont {field} manquant sur {ref} ligne {line}.",
        f"Non-conformité {bct} v{schema_v} : '{field}' de type date invalide sur {ref} ligne {line}. Format attendu : YYYY-MM-DD.",
        f"Validation XSD {bct} v{schema_v} : {field} = '{bad_val}' invalide. Remplacer par '{good_val}' selon le référentiel BCT.",
        f"Rejet {bct} : {n} violations XSD détectées. {ref} ligne {line} — {field} hors type xs:decimal.",
        f"Erreur de conformité XSD {bct} v{schema_v} : balise '{field}' mal ordonnée. L'ordre des éléments est strict dans ce schéma.",
    ]
    return random.choice(templates)


def reject_type10():
    """TYPE 10: Doublon/unicité"""
    ref = rand_account(); line = rand_line()
    ref2 = rand_account(); line2 = rand_line()
    field = random.choice(['IdClient', 'ReferenceCredit', 'IBAN', 'NumeroCompte', 'CodeClient', 'IdDeclaration'])
    n = random.randint(2, 8)
    bct = random.choice(BCT_TYPES)
    pair_lines = f"lignes {line}-{line+1}"
    templates = [
        f"Doublon détecté : {field} identique sur les {pair_lines} pour {ref} et {ref2}. Chaque {field} doit être unique.",
        f"Rejet {bct} : {ref} apparaît {n} fois dans la déclaration. Contrainte d'unicité {field} violée.",
        f"Anomalie unicité : {field} de {ref} (ligne {line}) dupliqué avec {ref2} (ligne {line2}). Agréger ou supprimer le doublon.",
        f"Violation contrainte UNIQUE sur {field} : {ref} et {ref2} partagent le même identifiant. Rejet {bct}.",
        f"Doublon {field} sur {n} paires de lignes dont {pair_lines}. Chaque client/compte ne doit figurer qu'une fois.",
        f"Rejet {bct} : {n} doublons {field} détectés. {ref} (ligne {line}) et {ref2} (ligne {line2}) en conflit.",
        f"Unicité violée : {field} de {ref} présent {n} fois. Le schéma {bct} impose une valeur unique par enregistrement.",
        f"Rejet automatique : {n} doublons {field} dont {ref}/{ref2} sur {pair_lines}. Consolider les enregistrements.",
        f"Anomalie {bct} : {field} dupliqué pour {ref} et {ref2}. Contrainte d'unicité non respectée ligne {line}.",
        f"Rejet {bct} : {ref} apparaît en double (lignes {line} et {line2}). Même {field}, même client — doublon à supprimer.",
        f"Violation d'unicité {field} : {n} doublons détectés dont {ref} (ligne {line}) et {ref2} (ligne {line2}).",
        f"Doublon {bct} : {ref} et {ref2} ont le même {field}. Consolider : sommer les montants, garder la provision max.",
        f"Rejet {bct} : contrainte UNIQUE violée — {field} de {ref} ligne {line} existe déjà dans la base de données.",
        f"Anomalie unicité : {n} couples de doublons dont {pair_lines}. {field} doit être unique par déclaration.",
        f"Rejet {bct} : {ref} dupliqué {n} fois (lignes {line}, {line2}…). Supprimer les lignes redondantes.",
        f"Doublon {field} sur {pair_lines} : {ref} et {ref2} en conflit. Même identifiant, données potentiellement différentes.",
        f"Rejet {bct} : {n} doublons {field} identifiés. Règle BCT : un client ne peut apparaître qu'une fois par déclaration.",
        f"Violation d'unicité sur {ref} (ligne {line}) : {field} déjà présent à la ligne {line2} ({ref2}).",
        f"Anomalie {bct} : {n} enregistrements avec {field} identique. {ref} ligne {line} — doublon à traiter.",
        f"Rejet {bct} : {ref} et {ref2} — même {field}, même période. Doublon confirmé sur {pair_lines}.",
    ]
    return random.choice(templates)


# ══════════════════════════════════════════════════════════════════════════════
# TEMPLATES DE CORRECTION (SUBMIT)
# ══════════════════════════════════════════════════════════════════════════════

def submit_type1():
    ref = rand_account(); amt = rand_amount(); ref2 = rand_account(); amt2 = rand_amount()
    templates = [
        f"Correction : MontantBrut négatif converti en valeur absolue. {ref} : -{amt} TND → {amt} TND. Fichier XML régénéré.",
        f"Valeur absolue appliquée sur {ref} ({amt} TND) et {ref2} ({amt2} TND). Montants négatifs corrigés.",
        f"Correction montants négatifs : {ref} -{amt} → {amt} TND. Règle BCT appliquée, fichier resoumis.",
        f"Montants corrigés : {ref} {amt} TND (ex -{amt}). Toutes les lignes vérifiées, aucun autre montant négatif.",
        f"Erreur de signe corrigée sur {ref} (-{amt} → {amt}) et {ref2} (-{amt2} → {amt2}). Fichier XML mis à jour.",
        f"Conversion valeur absolue effectuée : {ref} = {amt} TND, {ref2} = {amt2} TND. Resoumission après vérification.",
        f"Correction : {ref} montant -{amt} TND corrigé à {amt} TND. Erreur de saisie rectifiée dans le système.",
        f"Montants négatifs rectifiés : {ref} ({amt} TND) et {ref2} ({amt2} TND). Validation XSD locale passée.",
        f"Valeur absolue sur {ref} : {amt} TND. Reclassification du type opération si nécessaire.",
        f"Correction effectuée : {ref} et {ref2} — montants négatifs remplacés par valeurs absolues respectives.",
    ]
    return random.choice(templates)


def submit_type2():
    ref = rand_account(); amt = rand_amount(); classe = random.choice(['C', 'D'])
    req = 100 if classe == 'D' else 50
    prov = amt * req // 100
    templates = [
        f"Provision recalculée à {req}% pour {ref} (classe {classe}). Montant corrigé : {prov} TND. Note interne jointe.",
        f"Taux de provision porté à {req}% pour {ref} (classe {classe}). Provision : {prov} TND. Comité risques validé.",
        f"Correction provision classe {classe} : {ref} — taux {req}%, montant {prov} TND. Conforme circulaire 91-24.",
        f"Provision {ref} classe {classe} ajustée à {req}% : {prov} TND. Fichier XML mis à jour et resoumis.",
        f"Provisionnement corrigé pour {ref} (classe {classe}) : {prov} TND ({req}%). Approbation comité des risques.",
        f"Correction classe {classe} : taux provision {req}% appliqué sur {ref}. Montant provisionné : {prov} TND.",
        f"Provision insuffisante corrigée : {ref} classe {classe} — {prov} TND ({req}%). Conformité circulaire 91-24 assurée.",
        f"Taux provision recalculé {req}% pour classe {classe} sur {ref}. Provision totale : {prov} TND.",
        f"Correction effectuée : {ref} classe {classe} — provision portée à {prov} TND ({req}%). Documents joints.",
        f"Provisionnement régularisé : {ref} (classe {classe}) — {prov} TND soit {req}% de l'encours {amt} TND.",
    ]
    return random.choice(templates)


def submit_type3():
    ref = rand_account(); taux = round(random.uniform(2.0, 9.5), 2)
    ref2 = rand_account(); taux2 = round(random.uniform(2.0, 9.5), 2)
    templates = [
        f"Taux corrigé sur {ref} : {taux}% (taux marché). Taux négatifs remplacés par taux directeur BCT.",
        f"Correction taux : {ref} → {taux}%, {ref2} → {taux2}%. Taux de référence BCT utilisés.",
        f"Taux invalides rectifiés : {ref} = {taux}%, {ref2} = {taux2}%. Plage 0.1%-25% respectée.",
        f"Remplacement taux négatifs par taux BCT de référence ({taux}%) pour {ref}. Fichier XML corrigé.",
        f"Taux corrigé : {ref} {taux}% (taux marché interbancaire). Validation XSD passée.",
        f"Correction : taux négatifs sur {ref} remplacés par {taux}% (taux directeur BCT en vigueur).",
        f"Taux rectifiés pour {ref} ({taux}%) et {ref2} ({taux2}%). Tous les taux dans la plage autorisée.",
        f"Taux de référence BCT ({taux}%) appliqué sur {ref} et {ref2} ({taux2}%). Cohérence vérifiée.",
        f"Correction taux invalide : {ref} → {taux}%. Taux marché utilisé selon circulaire BCT en vigueur.",
        f"Taux négatifs corrigés : {ref} = {taux}%, {ref2} = {taux2}%. Conformité plage BCT vérifiée.",
    ]
    return random.choice(templates)


def submit_type4():
    ref = rand_account(); dev = random.choice(['TND', 'EUR', 'USD', 'GBP'])
    ref2 = rand_account(); dev2 = random.choice(['TND', 'EUR', 'USD'])
    n = random.randint(2, 10)
    templates = [
        f"Devise renseignée : {ref} → {dev}, {ref2} → {dev2}. CodeDevise complété pour tous les enregistrements.",
        f"CodeDevise corrigé : {ref} = {dev}, {ref2} = {dev2}. Codes ISO 4217 vérifiés.",
        f"Correction devise : {n} lignes mises à jour avec CodeDevise {dev}. Fichier XML resoumis.",
        f"Devise invalide remplacée par {dev} pour {ref} et {dev2} pour {ref2}. Codes ISO 4217 conformes.",
        f"CodeDevise absent renseigné : {ref} → {dev}, {ref2} → {dev2}. {n} corrections effectuées.",
        f"Correction {n} devises manquantes : {ref} = {dev}, autres opérations = {dev2}. Fichier corrigé.",
        f"Devises corrigées : {ref} → {dev} (opérations nationales), {ref2} → {dev2} (virements SEPA).",
        f"CodeDevise mis à jour pour {n} enregistrements. {ref} = {dev}, {ref2} = {dev2}.",
        f"Devise corrected : codes ISO 4217 appliqués. {ref} → {dev}, {ref2} → {dev2}. XSD validé.",
        f"Correction devise : {ref} → {dev} (TND pour opérations locales). {n} enregistrements corrigés.",
    ]
    return random.choice(templates)


def submit_type5():
    ref = rand_account(); field = rand_xsd_field()
    ref2 = rand_account(); field2 = rand_xsd_field()
    n = random.randint(2, 12)
    templates = [
        f"Champ '{field}' renseigné pour {ref} depuis la base clients. {n} enregistrements complétés.",
        f"Correction : '{field}' récupéré du système source pour {ref} et {n-1} autres lignes.",
        f"Champs obligatoires complétés : '{field}' pour {ref}, '{field2}' pour {ref2}. Fichier XML mis à jour.",
        f"'{field}' ajouté pour {n} enregistrements dont {ref}. Données extraites du référentiel client.",
        f"Correction champs manquants : {ref} — '{field}' renseigné depuis la base de données centrale.",
        f"'{field}' et '{field2}' complétés pour {ref} et {ref2}. {n} corrections effectuées au total.",
        f"Champ obligatoire '{field}' renseigné pour {n} lignes. {ref} et {ref2} corrigés. XSD validé.",
        f"Correction données incomplètes : '{field}' récupéré pour {ref} depuis le dossier client.",
        f"'{field}' absent complété pour {ref} ({n} lignes au total). Validation XSD locale passée.",
        f"Champs requis ajoutés : '{field}' pour {ref}, '{field2}' pour {ref2}. Fichier resoumis.",
    ]
    return random.choice(templates)


def submit_type6():
    ref = rand_account(); yr = random.choice([2025, 2026])
    n = random.randint(2, 8)
    period = rand_period()
    templates = [
        f"Dates corrigées : erreur de saisie {yr+1} au lieu de {yr}. {n} enregistrements dont {ref} corrigés.",
        f"Correction dates : {ref} et {n-1} autres lignes — dates ramenées dans la période {period}.",
        f"Dates rectifiées selon les dossiers physiques. {n} corrections effectuées. Toutes dans la période {period}.",
        f"Erreur de date corrigée sur {ref} et {n-1} autres : année {yr+2} remplacée par {yr}.",
        f"Dates corrigées : {n} enregistrements dont {ref}. Dates replacées dans la période déclarée {period}.",
        f"Correction : dates erronées représentaient la date de saisie, pas la date d'opération. {n} corrections.",
        f"Dates rectifiées pour {n} lignes dont {ref}. Toutes les dates vérifiées conformes à la période {period}.",
        f"Erreur de date {ref} corrigée : date de {yr+2} → {yr}. Conformité période {period} rétablie.",
        f"Correction {n} dates hors période : {ref} et autres — dates alignées sur la période {period}.",
        f"Dates corrigées selon documents physiques : {n} lignes dont {ref} ramenées dans la période {period}.",
    ]
    return random.choice(templates)


def submit_type7():
    ref = rand_account()
    credit = rand_amount(); impaye = credit - random.randint(1000, credit // 2)
    ref2 = rand_account()
    templates = [
        f"Correction : inversion colonnes MontantCredit/MontantImpaye corrigée. {ref} : crédit {credit} TND, impayé {impaye} TND.",
        f"MontantImpaye recalculé sur {ref} : {impaye} TND (≤ MontantCredit {credit} TND). Fichier XML mis à jour.",
        f"Erreur d'inversion corrigée : {ref} crédit {credit} TND, impayé {impaye} TND. Ratio {round(impaye/credit*100)}%.",
        f"Correction {ref} : MontantImpaye = {impaye} TND, MontantCredit = {credit} TND. Cohérence vérifiée.",
        f"Colonnes inversées corrigées sur {ref} et {ref2}. Impayé {impaye} TND ≤ crédit {credit} TND.",
        f"MontantImpaye corrigé à {impaye} TND pour {ref} (crédit {credit} TND). Ratio sain : {round(impaye/credit*100)}%.",
        f"Correction erreur logique : {ref} — crédit {credit} TND, impayé {impaye} TND. Fichier resoumis.",
        f"Inversion MontantCredit/MontantImpaye corrigée sur {ref}. Valeurs : crédit {credit}, impayé {impaye} TND.",
        f"Correction {ref} : impayé recalculé à {impaye} TND pour un crédit de {credit} TND. XSD validé.",
        f"MontantImpaye = {impaye} TND corrigé sur {ref} (crédit {credit} TND). {ref2} également corrigé.",
    ]
    return random.choice(templates)


def submit_type8():
    ref = rand_account()
    achat = rand_amount(); vente = random.randint(1000, achat - 1000)
    pos = achat - vente
    devise = random.choice(['EUR', 'USD', 'GBP', 'CHF'])
    templates = [
        f"PositionNette {devise} corrigée : {achat} TND - {vente} TND = {pos} TND. Recalcul vérifié pour toutes devises.",
        f"Correction position {devise} : {pos} TND ({achat} achat − {vente} vente). Fichier CSV mis à jour.",
        f"PositionNette {devise} rectifiée à {pos} TND. Formule : achat {achat} − vente {vente} appliquée.",
        f"Erreur de calcul corrigée : {devise} PositionNette = {pos} TND. Données achat/vente vérifiées.",
        f"Position nette {devise} recalculée : {pos} TND. {ref} — achat {achat} TND, vente {vente} TND.",
        f"PositionNette {devise} corrigée à {pos} TND sur {ref}. Recalcul automatique effectué.",
        f"Correction : PositionNette {devise} = {pos} TND ({achat} - {vente}). Toutes les devises vérifiées.",
        f"Position {devise} rectifiée pour {ref} : {pos} TND. Erreur de saisie dans le calcul corrigée.",
        f"PositionNette {devise} recalculée à {pos} TND. Validation XSD passée, fichier resoumis.",
        f"Correction position {devise} {ref} : {achat} TND − {vente} TND = {pos} TND. Conforme règle BCT.",
    ]
    return random.choice(templates)


def submit_type9():
    bct = random.choice(BCT_TYPES)
    schema_v = rand_schema_version()
    field = rand_xsd_field()
    good_val = random.choice(['CREDIT_INVEST', 'TND', 'STATUT_A', 'CODE_01', 'CLASSE_D'])
    n = random.randint(2, 15)
    templates = [
        f"Fichier XML régénéré conforme au schéma {bct} v{schema_v}. Balise '{field}' corrigée. Validation XSD locale OK.",
        f"Correction XSD {bct} v{schema_v} : '{field}' remplacé par '{good_val}'. {n} erreurs de schéma corrigées.",
        f"Non-conformité XSD {bct} corrigée : '{field}' mis à jour, namespace vérifié, schéma v{schema_v} validé.",
        f"Fichier XML corrigé : {n} violations XSD {bct} v{schema_v} résolues dont '{field}'.",
        f"Structure XML {bct} v{schema_v} corrigée : '{field}' renseigné avec valeur '{good_val}'. XSD validé.",
        f"Correction {n} erreurs XSD {bct} v{schema_v} : balises manquantes ajoutées, '{field}' corrigé.",
        f"XSD {bct} v{schema_v} respecté : '{field}' = '{good_val}'. Toutes les violations de schéma résolues.",
        f"Fichier XML reconfiguré selon {bct} v{schema_v} : '{field}' et {n-1} autres champs corrigés.",
        f"Correction format XSD : {bct} v{schema_v} — '{field}' = '{good_val}'. Validation locale passée.",
        f"Non-conformité XSD {bct} v{schema_v} résolue : {n} champs dont '{field}' corrigés. Fichier resoumis.",
    ]
    return random.choice(templates)


def submit_type10():
    ref = rand_account(); ref2 = rand_account()
    field = random.choice(['IdClient', 'ReferenceCredit', 'IBAN', 'NumeroCompte'])
    n = random.randint(2, 6)
    templates = [
        f"Doublons {field} supprimés : {n} paires consolidées. {ref} et {ref2} agrégés (somme montants, max provision).",
        f"Correction doublons : {ref} et {ref2} — même {field}. Données consolidées, ligne redondante supprimée.",
        f"Doublon {field} résolu : {n} lignes dupliquées supprimées. Montants agrégés, unicité rétablie.",
        f"Consolidation doublons {field} : {ref} ({n} occurrences) → 1 ligne. Montants sommés, max provision gardé.",
        f"Doublons corrigés : {n} paires dont {ref}/{ref2}. Agrégation effectuée, fichier conforme.",
        f"Correction unicité {field} : {ref} et {ref2} fusionnés. {n} doublons supprimés du fichier.",
        f"Doublon {field} {ref}/{ref2} corrigé : données consolidées. {n} lignes redondantes éliminées.",
        f"Unicité rétablie : {n} doublons {field} traités dont {ref}. Consolidation somme montants appliquée.",
        f"Correction {n} doublons {field} : {ref} et {ref2} agrégés. Ligne dupliquée supprimée. XSD validé.",
        f"Doublons {field} supprimés : {ref} ({n} fois) → 1 entrée consolidée. Montants sommés.",
    ]
    return random.choice(templates)


# ── Map types → fonctions ──────────────────────────────────────────────────────
REJECT_FN  = [reject_type1, reject_type2, reject_type3, reject_type4, reject_type5,
              reject_type6, reject_type7, reject_type8, reject_type9, reject_type10]
SUBMIT_FN  = [submit_type1, submit_type2, submit_type3, submit_type4, submit_type5,
              submit_type6, submit_type7, submit_type8, submit_type9, submit_type10]


# ══════════════════════════════════════════════════════════════════════════════
# GÉNÉRATION DES DONNÉES
# ══════════════════════════════════════════════════════════════════════════════

def generate_all_rows():
    """
    Génère 1500 lignes : 10 types × 150 cycles × 3 actions (REJECT, SUBMIT, VALIDATE)
    = 10 × 150 × 3 = 4500 lignes... mais le brief dit 1500 total.
    On génère donc 10 types × 50 cycles × 3 actions = 1500 lignes.
    declaration_ids : 30000 à 30499 (500 cycles)
    """
    rows = []
    decl_id = 30000

    for type_idx in range(10):
        reject_fn = REJECT_FN[type_idx]
        submit_fn = SUBMIT_FN[type_idx]

        for _ in range(50):  # 50 cycles par type = 500 cycles totaux = 1500 lignes
            agent   = random.choice(AGENTS)
            manager = random.choice(MANAGERS)

            date_reject  = rand_date_2025_2026()
            date_submit  = rand_date_after(date_reject, hours=1, max_hours=10)
            date_validate = rand_date_after(date_submit, hours=1, max_hours=6)

            reject_comment  = reject_fn()
            submit_comment  = submit_fn()

            rows.append((decl_id, 'REJECT',   'EN_VALIDATION', 'REJETEE',       manager, reject_comment,  date_reject))
            rows.append((decl_id, 'SUBMIT',   'REJETEE',       'EN_VALIDATION', agent,   submit_comment,   date_submit))
            rows.append((decl_id, 'VALIDATE', 'EN_VALIDATION', 'VALIDEE',       manager, None,            date_validate))

            decl_id += 1

    return rows


# ══════════════════════════════════════════════════════════════════════════════
# INSERTION EN BASE
# ══════════════════════════════════════════════════════════════════════════════

def batch_insert(cursor, rows):
    sql = """
        INSERT INTO validation_logs
            (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action)
        VALUES (%s, %s, %s, %s, %s, %s, %s)
    """
    cursor.executemany(sql, rows)


def main():
    print(f"Connexion à {DB_HOST}:{DB_PORT} …")
    conn = pymysql.connect(
        host=DB_HOST, port=DB_PORT,
        user=DB_USER, password=DB_PASS,
        database=DB_NAME,
        charset='utf8mb4',
        use_unicode=True,
        autocommit=False,
    )
    cursor = conn.cursor()

    print("Génération des données …")
    all_rows = generate_all_rows()
    total = len(all_rows)
    print(f"{total} lignes générées (10 types × 50 cycles × 3 actions).")

    BATCH = 100
    inserted = 0
    errors   = 0

    for i in range(0, total, BATCH):
        batch = all_rows[i:i + BATCH]
        try:
            batch_insert(cursor, batch)
            conn.commit()
            inserted += len(batch)
            if inserted % 100 == 0 or inserted == total:
                print(f"  → {inserted}/{total} lignes insérées …")
        except Exception as e:
            conn.rollback()
            print(f"  ✗ Erreur batch {i}-{i+BATCH} : {e}")
            # Tentative ligne par ligne pour identifier la ligne problématique
            sql_single = """
                INSERT INTO validation_logs
                    (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action)
                VALUES (%s, %s, %s, %s, %s, %s, %s)
            """
            for row in batch:
                try:
                    cursor.execute(sql_single, row)
                    conn.commit()
                    inserted += 1
                except Exception as e2:
                    errors += 1
                    print(f"    ✗ Ligne declaration_id={row[0]} action={row[1]} : {e2}")

    cursor.close()

    print(f"\n{'─'*50}")
    print(f"Insertion terminée : {inserted} lignes insérées, {errors} erreurs.")

    # ── Comptages finaux ───────────────────────────────────────────────────────
    conn2 = pymysql.connect(
        host=DB_HOST, port=DB_PORT,
        user=DB_USER, password=DB_PASS,
        database=DB_NAME,
        charset='utf8mb4',
    )
    c2 = conn2.cursor()

    c2.execute("SELECT COUNT(*) FROM validation_logs WHERE action='REJECT' AND commentaire IS NOT NULL AND LENGTH(commentaire) > 20")
    total_rejects = c2.fetchone()[0]

    c2.execute("SELECT COUNT(*) FROM validation_logs WHERE action='SUBMIT' AND commentaire IS NOT NULL AND LENGTH(commentaire) > 10")
    total_corrections = c2.fetchone()[0]

    c2.execute("SELECT COUNT(*) FROM validation_logs WHERE action='VALIDATE'")
    total_validates = c2.fetchone()[0]

    c2.execute("SELECT COUNT(*) FROM validation_logs WHERE declaration_id BETWEEN 30000 AND 30499")
    total_seeded = c2.fetchone()[0]

    conn2.close()

    print(f"\n{'─'*50}")
    print(f"Total en base (tous scripts) :")
    print(f"  REJECT     : {total_rejects}")
    print(f"  SUBMIT     : {total_corrections}")
    print(f"  VALIDATE   : {total_validates}")
    print(f"\nLignes insérées par CE script (decl_id 30000-30499) : {total_seeded}")
    print(f"{'─'*50}")


if __name__ == "__main__":
    main()
