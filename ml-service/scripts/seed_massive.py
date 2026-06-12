"""
seed_massive.py — Dataset massif pour silhouette > 0.35
Chaque cluster a des commentaires très distincts et spécifiques
"""
import pymysql
import os

DB_HOST = os.getenv("DB_HOST", "mysql")
DB_PORT = int(os.getenv("DB_PORT", "3306"))
DB_USER = os.getenv("DB_USER", "root")
DB_PASS = os.getenv("DB_PASSWORD", "wifak2024")
DB_NAME = "wifak_validation"

# 50 cycles par cluster = 450 cycles total = silhouette >> 0.3
# Format: (decl_id, action, avant, apres, user, commentaire, date)

def generate_data():
    data = []
    did = 10000  # ID de départ

    # ══════════════════════════════════════════════════════════════
    # CLUSTER A — MONTANT NEGATIF (50 cycles)
    # Mots distinctifs: montant_negatif, valeur_absolue, negatif, brut
    # ══════════════════════════════════════════════════════════════
    rejects_A = [
        "montant_negatif detecte sur ligne 1 valeur brute impossible",
        "montant_negatif sur CPT001 valeur absolue requise",
        "MontantBrut negatif ligne 3 correction valeur absolue obligatoire",
        "montant credit negatif interdit corriger valeur absolue",
        "valeur negative montant_negatif non accepte BCT",
        "montant_negatif sur 3 comptes valeur absolue correction requise",
        "chiffre negatif MontantBrut ligne 7 BCT interdit",
        "montant_negatif CPT003 CPT005 valeur absolue appliquer",
        "MontantExposition negatif impossible convertir valeur absolue",
        "montant brut negatif detecte fichier BCT_01 correction urgente",
        "solde negatif compte epargne interdit BCT reclasser",
        "MontantOperation negatif sur transaction TRX001 TRX002",
        "montant_negatif ligne 15 valeur absolue 250000 TND",
        "valeur montant negative non conforme regle BCT negatif",
        "MontantImpaye negatif sur 3 credits valeur absolue corriger",
        "montant brut negatif ligne 9 CPT009 valeur absolue",
        "negatif detecte MontantGarantie ligne 6 valeur absolue",
        "montant_negatif CPT007 correction valeur absolue 180000",
        "MontantCredit negatif 5 lignes impossible valeur absolue",
        "montant negatif incorrecte valeur absolue appliquer tout ligne",
        "MontantBrut valeur negative impossible convertir absolu",
        "montant_negatif sur 8 enregistrements valeur absolue requise",
        "negatif MontantExposition CPT001 valeur absolue necessaire",
        "montant credit negatif 4 lignes valeur absolue correction",
        "MontantOperation negatif TRX003 TRX008 valeur absolue",
        "montant_negatif CPT002 correction valeur absolue urgente",
        "solde negatif interdit compte epargne CPT_EP reclasser CCT",
        "valeur negative MontantBrut negatif non acceptable BCT",
        "montant brut negatif sur 2 lignes valeur absolue",
        "MontantImpaye negatif impossible valeur absolue appliquer",
        "montant_negatif detecte fichier XML valeur absolue corriger",
        "MontantGarantie negatif ligne 12 valeur absolue obligatoire",
        "negatif montant credit ligne 8 valeur absolue correction",
        "montant_negatif CPT010 CPT011 corriger valeur absolue",
        "MontantExposition negatif 6 lignes valeur absolue necessaire",
        "montant brut negatif CPT004 valeur absolue 95000 TND",
        "negatif detecte montant credit 3 comptes valeur absolue",
        "MontantBrut valeur negative ligne 5 valeur absolue requise",
        "montant_negatif sur CPT006 correction valeur absolue appliquee",
        "MontantCredit negatif 7 enregistrements valeur absolue",
        "montant negatif interdit sur CPT_BC valeur absolue BCT",
        "MontantOperation negatif TRX005 valeur absolue correction",
        "montant_negatif CPT015 CPT016 valeur absolue corriger",
        "negatif MontantGarantie ligne 10 valeur absolue obligatoire",
        "montant brut negatif 4 lignes fichier BCT valeur absolue",
        "MontantImpaye negatif impossible appliquer valeur absolue",
        "montant_negatif detecte CPT020 valeur absolue correction",
        "MontantExposition negatif CPT025 valeur absolue requise",
        "negatif montant_negatif brut ligne 18 valeur absolue",
        "MontantCredit negatif ligne 22 valeur absolue 320000",
    ]
    corrections_A = [
        "correction montant_negatif valeur absolue appliquee",
        "montant_negatif converti valeur absolue correction OK",
        "valeur absolue appliquee montant_negatif corrige",
        "montant negatif corrige valeur absolue fichier regenere",
        "correction valeur absolue montant_negatif toutes lignes",
        "montants negatifs convertis valeur absolue correction appliquee",
        "MontantBrut negatif corrige valeur absolue OK",
        "valeur absolue correction montant_negatif CPT appliquee",
        "montant_negatif toutes lignes valeur absolue corrigees",
        "correction complete montants negatifs valeur absolue",
        "solde negatif reclasse CPT_CC valeur absolue correction",
        "MontantOperation negatif corrige valeur absolue OK",
        "montant_negatif 250000 TND valeur absolue appliquee",
        "valeur absolue correction montants negatifs fichier OK",
        "MontantImpaye negatif valeur absolue corrige fichier",
        "montant brut negatif valeur absolue correction CPT009",
        "MontantGarantie negatif corrige valeur absolue 180000",
        "correction valeur absolue CPT007 montant_negatif OK",
        "MontantCredit negatifs 5 lignes valeur absolue corriges",
        "montants negatifs valeur absolue appliquee correction",
        "MontantBrut valeur absolue correction negative appliquee",
        "montants negatifs valeur absolue 8 lignes corriges",
        "MontantExposition valeur absolue CPT001 correction",
        "montant credit valeur absolue 4 lignes correction OK",
        "MontantOperation valeur absolue TRX003 TRX008 OK",
        "CPT002 valeur absolue montant_negatif corrige urgent",
        "compte epargne reclasse CPT_CC solde negatif corrige",
        "MontantBrut negatif valeur absolue correction appliquee",
        "montant brut valeur absolue 2 lignes correction OK",
        "MontantImpaye valeur absolue correction negatif",
        "montant_negatif fichier XML valeur absolue correction",
        "MontantGarantie valeur absolue ligne 12 corriger",
        "montant credit ligne 8 valeur absolue correction",
        "CPT010 CPT011 valeur absolue montant_negatif OK",
        "MontantExposition 6 lignes valeur absolue correction",
        "CPT004 valeur absolue 95000 TND montant_negatif OK",
        "montants 3 comptes valeur absolue correction appliquee",
        "MontantBrut ligne 5 valeur absolue correction",
        "CPT006 montant_negatif valeur absolue corrige",
        "MontantCredit 7 lignes valeur absolue correction",
        "CPT_BC valeur absolue correction montant BCT",
        "TRX005 valeur absolue MontantOperation correction",
        "CPT015 CPT016 valeur absolue montants corriges",
        "MontantGarantie ligne 10 valeur absolue correction",
        "fichier BCT valeur absolue 4 lignes correction OK",
        "MontantImpaye valeur absolue appliquer correction",
        "CPT020 valeur absolue montant_negatif corrige",
        "MontantExposition CPT025 valeur absolue correction",
        "montant_negatif ligne 18 valeur absolue correction",
        "MontantCredit ligne 22 valeur absolue 320000 OK",
    ]

    for i in range(50):
        did += 1
        d = f'2026-0{(i%5)+1}-{(i%28)+1:02d}'
        data.append((did,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',rejects_A[i],f'{d} 09:00:00'))
        data.append((did,'SUBMIT','REJETEE','EN_VALIDATION','miral',corrections_A[i],f'{d} 11:00:00'))
        data.append((did,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,f'{d} 12:00:00'))

    # ══════════════════════════════════════════════════════════════
    # CLUSTER B — PROVISION / CLASSE RISQUE (50 cycles)
    # Mots distinctifs: provision, classe_d, classe_c, taux_provisionnement
    # ══════════════════════════════════════════════════════════════
    rejects_B = [
        "taux_provisionnement classe_d insuffisant 15 pourcent requis 100",
        "provision classe_d manquante taux_provisionnement 100 pourcent obligatoire",
        "classe_d sans provision taux_provisionnement requis 100 pourcent BCT",
        "provision insuffisante classe_d taux 15 pourcent requis 100 pourcent",
        "taux_provisionnement classe_d incorrect 20 pourcent minimum 100 pourcent",
        "provision classe_c insuffisante taux 10 pourcent minimum 50 pourcent",
        "classe_c taux_provisionnement 10 pourcent requis 50 pourcent circulaire",
        "provision classe_c manquante taux_provisionnement 50 pourcent BCT",
        "classe_d provision zero taux_provisionnement 100 pourcent obligatoire",
        "taux_provisionnement classe_d 30 pourcent insuffisant requis 100",
        "provision classe_b manquante taux 20 pourcent minimum requis",
        "classe_b sans provision taux_provisionnement 20 pourcent BCT",
        "provision classe_d 25 pourcent insuffisant taux 100 pourcent requis",
        "taux_provisionnement classe_c 12 pourcent insuffisant minimum 50",
        "classe_d encours sous-provisionne taux_provisionnement requis 100",
        "provision manquante classe_d taux_provisionnement zero incorrect",
        "classe_c provision taux insuffisant 10 pourcent requis 50 pourcent",
        "taux_provisionnement classe_d non conforme requis 100 pourcent",
        "provision classe_d absente taux_provisionnement obligatoire BCT",
        "classe_d provision 40 pourcent insuffisant 100 pourcent requis",
        "taux_provisionnement classe_c 15 pourcent minimum 50 pourcent requis",
        "provision classe_d incorrecte taux 20 pourcent requis 100 pourcent",
        "classe_d sous-provisionnee taux_provisionnement 100 pourcent BCT",
        "provision classe_c taux 8 pourcent insuffisant minimum 50 pourcent",
        "taux_provisionnement classe_d manquant provision obligatoire 100",
        "provision classe_d 10 pourcent taux insuffisant requis 100 pourcent",
        "classe_c provision absente taux_provisionnement 50 pourcent requis",
        "taux_provisionnement classe_d 35 pourcent insuffisant requis 100",
        "provision classe_d non conforme taux_provisionnement BCT 100",
        "classe_d provision incorrecte taux 5 pourcent requis 100 pourcent",
        "taux_provisionnement classe_c insuffisant 18 pourcent minimum 50",
        "provision classe_d manquante encours 3200000 TND taux 100",
        "classe_d sans provision taux_provisionnement 100 pourcent circulaire",
        "provision insuffisante classe_c taux 22 pourcent requis 50",
        "taux_provisionnement classe_d zero provision absente BCT",
        "provision classe_d 15 pourcent non conforme taux 100 requis",
        "classe_c taux insuffisant provision 12 pourcent requis 50 pourcent",
        "taux_provisionnement classe_d encours sous-provisionne requis 100",
        "provision manquante classe_c taux 9 pourcent minimum 50 pourcent",
        "classe_d provision 25 pourcent insuffisant taux_provisionnement 100",
        "taux_provisionnement classe_c 14 pourcent insuffisant minimum 50",
        "provision classe_d absente taux BCT requis 100 pourcent urgent",
        "classe_d encours provision taux_provisionnement insuffisant 100",
        "provision classe_c 16 pourcent taux insuffisant requis 50 pourcent",
        "taux_provisionnement classe_d manquant provision 100 pourcent BCT",
        "provision classe_d 30 pourcent insuffisant taux requis 100 pourcent",
        "classe_c sans provision taux_provisionnement 50 pourcent requis",
        "taux_provisionnement classe_d 45 pourcent insuffisant requis 100",
        "provision classe_d non conforme taux BCT 100 pourcent circulaire",
        "classe_d provision incorrecte taux_provisionnement 100 pourcent",
    ]
    corrections_B = [
        "taux_provisionnement classe_d recalcule 100 pourcent provision OK",
        "provision classe_d ajoutee taux_provisionnement 100 pourcent",
        "classe_d provision 100 pourcent taux_provisionnement corrige",
        "provision insuffisante classe_d corrigee taux 100 pourcent",
        "taux_provisionnement classe_d corrige 100 pourcent provision",
        "provision classe_c corrigee taux_provisionnement 50 pourcent",
        "classe_c taux_provisionnement 50 pourcent provision ajoutee",
        "provision classe_c taux 50 pourcent correction appliquee",
        "classe_d provision 100 pourcent zero corrige taux",
        "taux_provisionnement classe_d 100 pourcent provision corrigee",
        "provision classe_b taux 20 pourcent correction appliquee",
        "classe_b taux_provisionnement 20 pourcent provision ajoutee",
        "provision classe_d taux 100 pourcent correction 25 vers 100",
        "taux_provisionnement classe_c 50 pourcent correction 12 vers 50",
        "classe_d provision 100 pourcent taux_provisionnement correction",
        "provision classe_d taux_provisionnement 100 pourcent zero corrige",
        "classe_c provision taux 50 pourcent correction 10 vers 50",
        "taux_provisionnement classe_d 100 pourcent correction OK",
        "provision classe_d taux_provisionnement 100 pourcent ajoutee",
        "classe_d provision 40 vers 100 pourcent taux correction",
        "taux_provisionnement classe_c 50 pourcent correction 15 vers 50",
        "provision classe_d taux 20 vers 100 pourcent correction",
        "classe_d taux_provisionnement 100 pourcent sous-provision corrige",
        "provision classe_c taux 8 vers 50 pourcent correction",
        "taux_provisionnement classe_d 100 pourcent provision corrigee",
        "provision classe_d taux 10 vers 100 pourcent correction",
        "classe_c provision 50 pourcent taux correction absente",
        "taux_provisionnement classe_d 100 pourcent 35 vers 100",
        "provision classe_d taux_provisionnement 100 pourcent BCT OK",
        "classe_d provision taux 5 vers 100 pourcent correction",
        "taux_provisionnement classe_c 50 pourcent correction 18",
        "provision classe_d 3200000 TND taux 100 pourcent OK",
        "classe_d provision 100 pourcent taux circulaire correction",
        "provision classe_c taux 50 pourcent correction 22",
        "taux_provisionnement classe_d zero provision 100 pourcent",
        "provision classe_d taux 15 vers 100 pourcent correction",
        "classe_c taux 50 pourcent provision correction 12",
        "taux_provisionnement classe_d 100 pourcent sous-provision OK",
        "provision classe_c taux 50 pourcent correction 9",
        "classe_d provision 25 vers 100 pourcent taux_provisionnement",
        "taux_provisionnement classe_c 50 pourcent correction 14",
        "provision classe_d taux 100 pourcent correction urgent OK",
        "classe_d taux_provisionnement 100 pourcent provision corrigee",
        "provision classe_c taux 50 pourcent correction 16",
        "taux_provisionnement classe_d 100 pourcent provision BCT",
        "provision classe_d taux 30 vers 100 pourcent correction",
        "classe_c provision 50 pourcent taux correction",
        "taux_provisionnement classe_d 45 vers 100 pourcent",
        "provision classe_d taux_provisionnement 100 pourcent BCT OK",
        "classe_d provision taux_provisionnement 100 pourcent corrige",
    ]

    for i in range(50):
        did += 1
        d = f'2026-0{(i%5)+1}-{(i%28)+1:02d}'
        data.append((did,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',rejects_B[i],f'{d} 09:00:00'))
        data.append((did,'SUBMIT','REJETEE','EN_VALIDATION','miral',corrections_B[i],f'{d} 11:00:00'))
        data.append((did,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,f'{d} 12:00:00'))

    # ══════════════════════════════════════════════════════════════
    # CLUSTER C — TAUX INCORRECT (40 cycles)
    # Mots distinctifs: taux_applique, taux_change, taux_negatif, plage
    # ══════════════════════════════════════════════════════════════
    rejects_C = [
        "taux_applique negatif ligne 4 plage 0.1 25 pourcent BCT",
        "taux_negatif taux_applique ligne 7 plage autorisee 0.1 25",
        "taux_applique zero nul ligne 12 BCT taux_negatif plage",
        "taux_change invalide 0.0 EUR ligne 9 taux_applique BCT",
        "taux_negatif -2.5 pourcent taux_applique hors plage BCT",
        "taux_applique -1.5 pourcent ligne 4 plage 0.1 25 BCT",
        "taux hors plage 45 pourcent taux_applique maximum 25 BCT",
        "taux_change taux_applique 0.0 invalide ligne 7 plage",
        "taux_negatif taux_applique -0.8 plage incorrecte BCT",
        "taux_applique 0.0 nul plage autorisee BCT correction",
        "taux_negatif -3.2 pourcent taux_applique plage BCT",
        "taux_applique invalide 0.0001 plage incorrecte BCT",
        "taux hors plage 50 pourcent taux_applique maximum 25",
        "taux_change 0.0 EUR USD invalide taux_applique plage",
        "taux_negatif taux_applique -1.8 pourcent plage BCT",
        "taux_applique -2.1 pourcent negatif hors plage BCT",
        "taux_change invalide XXX devise taux_applique plage",
        "taux_negatif -4.0 pourcent taux_applique hors plage",
        "taux_applique 0.00 nul ligne 15 plage BCT correction",
        "taux hors plage 35 pourcent taux_applique maximum BCT",
        "taux_change EUR 0.0 invalide taux_applique ligne BCT",
        "taux_negatif -0.5 pourcent taux_applique plage correcte",
        "taux_applique negatif -2.9 plage 0.1 25 pourcent BCT",
        "taux_change GBP 0.0 taux_applique invalide plage BCT",
        "taux_negatif taux_applique -3.5 hors plage correction",
        "taux_applique zero nul plage BCT correction requise",
        "taux hors plage 60 pourcent maximum 25 taux_applique",
        "taux_change USD invalide 0.0 taux_applique plage BCT",
        "taux_negatif -1.2 pourcent taux_applique plage BCT",
        "taux_applique -0.3 pourcent negatif hors plage BCT",
        "taux_change JPY 0.0 invalide taux_applique BCT plage",
        "taux_negatif taux_applique -5.0 hors plage incorrect",
        "taux_applique negatif -1.9 pourcent plage BCT ligne",
        "taux_change CHF 0.0 taux_applique invalide plage BCT",
        "taux_negatif -2.7 pourcent taux_applique hors plage",
        "taux_applique 0.0 nul plage autorisee BCT ligne 20",
        "taux hors plage 40 pourcent taux_applique BCT max 25",
        "taux_change EUR USD taux_applique 0.0 invalide plage",
        "taux_negatif -1.1 pourcent taux_applique BCT correction",
        "taux_applique -3.7 pourcent negatif hors plage BCT",
    ]
    corrections_C = [
        "taux_applique corrige 4.25 pourcent reference BCT plage",
        "taux_negatif corrige taux_applique 4.25 plage OK",
        "taux_applique nul corrige 3.5 pourcent plage BCT",
        "taux_change EUR corrige taux officiel BCT plage",
        "taux_negatif -2.5 corrige 4.25 pourcent taux_applique",
        "taux_applique -1.5 corrige 4.25 pourcent plage BCT",
        "taux hors plage 45 corrige 4.5 pourcent taux_applique",
        "taux_change 0.0 corrige taux_applique officiel BCT",
        "taux_negatif corrige taux_applique 3.75 plage OK",
        "taux_applique 0.0 corrige 4.25 pourcent plage BCT",
        "taux_negatif -3.2 corrige 4.25 taux_applique BCT",
        "taux_applique 0.0001 corrige 0.01 pourcent plage",
        "taux 50 corrige 5.0 pourcent taux_applique plage",
        "taux_change 0.0 corrige officiel BCT taux_applique",
        "taux_negatif -1.8 corrige 4.25 taux_applique plage",
        "taux_applique -2.1 corrige 3.5 pourcent plage BCT",
        "taux_change corrige devise valide taux_applique BCT",
        "taux_negatif -4.0 corrige 4.5 taux_applique plage",
        "taux_applique 0.00 corrige 3.75 pourcent plage BCT",
        "taux 35 corrige 3.5 pourcent taux_applique plage",
        "taux_change EUR 0.0 corrige officiel taux_applique",
        "taux_negatif -0.5 corrige 4.0 taux_applique plage",
        "taux_applique -2.9 corrige 4.25 pourcent plage BCT",
        "taux_change GBP corrige officiel taux_applique BCT",
        "taux_negatif -3.5 corrige 4.25 taux_applique plage",
        "taux_applique zero corrige 3.5 pourcent plage BCT",
        "taux 60 corrige 6.0 pourcent taux_applique plage",
        "taux_change USD corrige officiel taux_applique BCT",
        "taux_negatif -1.2 corrige 4.0 taux_applique plage",
        "taux_applique -0.3 corrige 3.8 pourcent plage BCT",
        "taux_change JPY corrige officiel taux_applique BCT",
        "taux_negatif -5.0 corrige 4.25 taux_applique plage",
        "taux_applique -1.9 corrige 3.9 pourcent plage BCT",
        "taux_change CHF corrige officiel taux_applique BCT",
        "taux_negatif -2.7 corrige 4.25 taux_applique plage",
        "taux_applique 0.0 corrige 4.0 pourcent plage BCT",
        "taux 40 corrige 4.0 pourcent taux_applique plage",
        "taux_change EUR USD corrige officiel taux_applique",
        "taux_negatif -1.1 corrige 4.25 taux_applique BCT",
        "taux_applique -3.7 corrige 3.7 pourcent plage BCT",
    ]

    for i in range(40):
        did += 1
        d = f'2026-0{(i%5)+1}-{(i%28)+1:02d}'
        data.append((did,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',rejects_C[i],f'{d} 09:00:00'))
        data.append((did,'SUBMIT','REJETEE','EN_VALIDATION','miral',corrections_C[i],f'{d} 11:00:00'))
        data.append((did,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,f'{d} 12:00:00'))

    # ══════════════════════════════════════════════════════════════
    # CLUSTER D — CODE DEVISE INVALIDE (35 cycles)
    # Mots distinctifs: code_devise, devise_vide, ISO_4217, TND_EUR
    # ══════════════════════════════════════════════════════════════
    rejects_D = [
        "code_devise vide ligne 5 ISO_4217 TND EUR USD requis",
        "code_devise absent 8 lignes ISO_4217 obligatoire BCT",
        "devise_vide code_devise manquant ISO_4217 correction",
        "code_devise invalide XXX ISO_4217 TND EUR USD BCT",
        "devise_vide code_devise absent ligne 3 ISO_4217 requis",
        "code_devise YYY invalide ISO_4217 TND EUR USD requis",
        "devise_vide code_devise manquant 5 lignes ISO_4217",
        "code_devise ZZZ non conforme ISO_4217 TND EUR BCT",
        "devise_vide absent code_devise 12 enregistrements ISO",
        "code_devise invalide ABB ISO_4217 correction TND EUR",
        "devise_vide code_devise manquant ligne 7 ISO_4217",
        "code_devise invalide XYZ ISO_4217 TND EUR USD BCT",
        "devise_vide code_devise absent 3 lignes ISO_4217 BCT",
        "code_devise CCC invalide ISO_4217 TND EUR requis",
        "devise_vide code_devise manquant ISO_4217 10 lignes",
        "code_devise invalide DDD ISO_4217 TND EUR USD requis",
        "devise_vide 6 lignes code_devise absent ISO_4217 BCT",
        "code_devise AAA invalide ISO_4217 correction requise",
        "devise_vide code_devise manquant 4 lignes ISO_4217",
        "code_devise invalide EEE ISO_4217 TND EUR USD BCT",
        "devise_vide absent code_devise ligne 9 ISO_4217",
        "code_devise FFF non conforme ISO_4217 TND EUR requis",
        "devise_vide code_devise 7 lignes absent ISO_4217",
        "code_devise invalide GGG ISO_4217 correction TND",
        "devise_vide code_devise manquant ligne 11 ISO_4217",
        "code_devise HHH invalide ISO_4217 TND EUR USD",
        "devise_vide 9 lignes code_devise absent ISO_4217",
        "code_devise JJJ invalide ISO_4217 TND EUR BCT",
        "devise_vide code_devise manquant 6 lignes ISO_4217",
        "code_devise KKK invalide ISO_4217 correction TND EUR",
        "devise_vide absent code_devise ligne 14 ISO_4217",
        "code_devise LLL non conforme ISO_4217 TND EUR requis",
        "devise_vide code_devise manquant 11 lignes ISO_4217",
        "code_devise MMM invalide ISO_4217 TND EUR USD BCT",
        "devise_vide absent code_devise ligne 16 ISO_4217",
    ]
    corrections_D = [
        "code_devise TND ajoutee ligne 5 ISO_4217 correction",
        "code_devise renseigne 8 lignes TND EUR USD ISO_4217",
        "devise_vide corrigee code_devise TND ISO_4217 OK",
        "code_devise XXX remplace TND ISO_4217 correction",
        "devise_vide code_devise TND ligne 3 ISO_4217 OK",
        "code_devise YYY remplace USD ISO_4217 correction",
        "devise_vide code_devise 5 lignes TND ISO_4217 OK",
        "code_devise ZZZ remplace EUR ISO_4217 BCT OK",
        "devise_vide code_devise 12 lignes TND EUR ISO_4217",
        "code_devise ABB remplace TND ISO_4217 correction",
        "devise_vide code_devise TND ligne 7 ISO_4217 OK",
        "code_devise XYZ remplace USD ISO_4217 correction",
        "devise_vide code_devise 3 lignes TND ISO_4217 BCT",
        "code_devise CCC remplace EUR ISO_4217 requis OK",
        "devise_vide code_devise 10 lignes TND ISO_4217",
        "code_devise DDD remplace USD ISO_4217 correction",
        "devise_vide 6 lignes code_devise TND ISO_4217",
        "code_devise AAA remplace TND ISO_4217 correction",
        "devise_vide code_devise 4 lignes TND ISO_4217 OK",
        "code_devise EEE remplace EUR ISO_4217 correction",
        "devise_vide code_devise TND ligne 9 ISO_4217 OK",
        "code_devise FFF remplace TND ISO_4217 correction",
        "devise_vide code_devise 7 lignes TND ISO_4217",
        "code_devise GGG remplace TND ISO_4217 correction",
        "devise_vide code_devise TND ligne 11 ISO_4217",
        "code_devise HHH remplace USD EUR ISO_4217 OK",
        "devise_vide 9 lignes code_devise TND ISO_4217",
        "code_devise JJJ remplace TND ISO_4217 correction",
        "devise_vide code_devise 6 lignes TND ISO_4217",
        "code_devise KKK remplace EUR ISO_4217 correction",
        "devise_vide code_devise TND ligne 14 ISO_4217",
        "code_devise LLL remplace TND ISO_4217 correction",
        "devise_vide code_devise 11 lignes TND ISO_4217",
        "code_devise MMM remplace USD ISO_4217 correction",
        "devise_vide code_devise TND ligne 16 ISO_4217",
    ]

    for i in range(35):
        did += 1
        d = f'2026-0{(i%5)+1}-{(i%28)+1:02d}'
        data.append((did,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',rejects_D[i],f'{d} 09:00:00'))
        data.append((did,'SUBMIT','REJETEE','EN_VALIDATION','miral',corrections_D[i],f'{d} 11:00:00'))
        data.append((did,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,f'{d} 12:00:00'))

    # ══════════════════════════════════════════════════════════════
    # CLUSTER E — CHAMP OBLIGATOIRE MANQUANT (35 cycles)
    # Mots distinctifs: champ_obligatoire, manquant, absent, XSD_requis
    # ══════════════════════════════════════════════════════════════
    rejects_E = [
        "champ_obligatoire NomClient absent XSD_requis minOccurs",
        "champ_obligatoire manquant CodeBanque XSD_requis absent",
        "NomClient absent champ_obligatoire XSD_requis ligne 4",
        "champ_obligatoire TypeOperation manquant XSD_requis",
        "CodeBanque absent champ_obligatoire XSD_requis ligne 3",
        "champ_obligatoire MontantBrut manquant XSD_requis balise",
        "NomClient vide champ_obligatoire absent XSD_requis",
        "champ_obligatoire CodeTypeCredit absent XSD_requis",
        "IdClient manquant champ_obligatoire XSD_requis ligne",
        "champ_obligatoire absent TypeCompte XSD_requis ligne",
        "NomClient champ_obligatoire manquant XSD_requis 5 lignes",
        "champ_obligatoire CodeAgence absent XSD_requis ligne",
        "TypeOperation manquant champ_obligatoire XSD_requis",
        "champ_obligatoire NomBanque absent XSD_requis ligne",
        "CodeBanque manquant champ_obligatoire XSD_requis 3",
        "champ_obligatoire DateOperation absent XSD_requis",
        "NomClient absent 7 lignes champ_obligatoire XSD_requis",
        "champ_obligatoire CodeDevise manquant XSD_requis ligne",
        "TypeCredit absent champ_obligatoire XSD_requis ligne",
        "champ_obligatoire NomClient XSD_requis manquant 8",
        "IdCompte manquant champ_obligatoire XSD_requis ligne",
        "champ_obligatoire TypeGarantie absent XSD_requis",
        "NomClient champ_obligatoire XSD_requis absent ligne 6",
        "champ_obligatoire CodeSecteur manquant XSD_requis",
        "TypeOperation absent champ_obligatoire XSD_requis 4",
        "champ_obligatoire NomClient XSD_requis manquant lignes",
        "CodeRegion manquant champ_obligatoire XSD_requis ligne",
        "champ_obligatoire TypeContrat absent XSD_requis ligne",
        "NomClient absent champ_obligatoire XSD_requis 9 lignes",
        "champ_obligatoire CodePays manquant XSD_requis ligne",
        "TypeOperation manquant champ_obligatoire XSD_requis 6",
        "champ_obligatoire NomClient XSD_requis 10 lignes absent",
        "IdBeneficiaire manquant champ_obligatoire XSD_requis",
        "champ_obligatoire TypeTransaction absent XSD_requis",
        "NomClient champ_obligatoire XSD_requis absent 12 lignes",
    ]
    corrections_E = [
        "NomClient renseigne champ_obligatoire XSD_requis OK",
        "CodeBanque ajoute champ_obligatoire XSD_requis correction",
        "NomClient champ_obligatoire XSD_requis renseigne ligne",
        "TypeOperation ajoute champ_obligatoire XSD_requis",
        "CodeBanque renseigne champ_obligatoire XSD_requis",
        "MontantBrut balise champ_obligatoire XSD_requis ajoute",
        "NomClient champ_obligatoire XSD_requis renseigne vide",
        "CodeTypeCredit ajoute champ_obligatoire XSD_requis",
        "IdClient renseigne champ_obligatoire XSD_requis ligne",
        "TypeCompte ajoute champ_obligatoire XSD_requis ligne",
        "NomClient champ_obligatoire XSD_requis 5 lignes OK",
        "CodeAgence ajoute champ_obligatoire XSD_requis ligne",
        "TypeOperation renseigne champ_obligatoire XSD_requis",
        "NomBanque ajoute champ_obligatoire XSD_requis ligne",
        "CodeBanque renseigne champ_obligatoire XSD_requis 3",
        "DateOperation ajoute champ_obligatoire XSD_requis",
        "NomClient champ_obligatoire XSD_requis 7 lignes OK",
        "CodeDevise ajoute champ_obligatoire XSD_requis ligne",
        "TypeCredit renseigne champ_obligatoire XSD_requis",
        "NomClient champ_obligatoire XSD_requis 8 lignes OK",
        "IdCompte renseigne champ_obligatoire XSD_requis ligne",
        "TypeGarantie ajoute champ_obligatoire XSD_requis",
        "NomClient champ_obligatoire XSD_requis ligne 6 OK",
        "CodeSecteur ajoute champ_obligatoire XSD_requis",
        "TypeOperation renseigne champ_obligatoire XSD_requis 4",
        "NomClient champ_obligatoire XSD_requis lignes corriges",
        "CodeRegion renseigne champ_obligatoire XSD_requis",
        "TypeContrat ajoute champ_obligatoire XSD_requis ligne",
        "NomClient champ_obligatoire XSD_requis 9 lignes OK",
        "CodePays ajoute champ_obligatoire XSD_requis ligne",
        "TypeOperation renseigne champ_obligatoire XSD_requis 6",
        "NomClient champ_obligatoire XSD_requis 10 lignes OK",
        "IdBeneficiaire renseigne champ_obligatoire XSD_requis",
        "TypeTransaction ajoute champ_obligatoire XSD_requis",
        "NomClient champ_obligatoire XSD_requis 12 lignes OK",
    ]

    for i in range(35):
        did += 1
        d = f'2026-0{(i%5)+1}-{(i%28)+1:02d}'
        data.append((did,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',rejects_E[i],f'{d} 09:00:00'))
        data.append((did,'SUBMIT','REJETEE','EN_VALIDATION','miral',corrections_E[i],f'{d} 11:00:00'))
        data.append((did,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,f'{d} 12:00:00'))

    return data


def main():
    data = generate_data()
    print(f"Connecting to {DB_HOST}:{DB_PORT} ...")
    conn = pymysql.connect(host=DB_HOST, port=DB_PORT, user=DB_USER,
        password=DB_PASS, database=DB_NAME, charset='utf8mb4', use_unicode=True)
    cursor = conn.cursor()
    sql = """INSERT INTO validation_logs
        (declaration_id, action, statut_avant, statut_apres, effectue_par, commentaire, date_action)
        VALUES (%s, %s, %s, %s, %s, %s, %s)"""

    inserted = errors = 0
    for row in data:
        try:
            cursor.execute(sql, row)
            inserted += 1
        except Exception as e:
            errors += 1

    conn.commit()

    c2 = conn.cursor()
    c2.execute("SELECT COUNT(*) FROM validation_logs WHERE action='REJECT' AND LENGTH(commentaire)>10")
    rejets = c2.fetchone()[0]
    c2.execute("SELECT COUNT(*) FROM validation_logs WHERE action='SUBMIT' AND LENGTH(commentaire)>10")
    corrections = c2.fetchone()[0]
    conn.close()

    print(f"Inserted: {inserted} | Errors: {errors}")
    print(f"Total REJECT: {rejets} | Total SUBMIT: {corrections}")


if __name__ == "__main__":
    main()
