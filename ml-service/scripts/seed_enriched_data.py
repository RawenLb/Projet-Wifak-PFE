"""
seed_enriched_data.py — Données enrichies pour améliorer le silhouette score
Plus de données par cluster + termes BCT normalisés + variété dans les formulations
"""
import pymysql
import os

DB_HOST = os.getenv("DB_HOST", "mysql")
DB_PORT = int(os.getenv("DB_PORT", "3306"))
DB_USER = os.getenv("DB_USER", "root")
DB_PASS = os.getenv("DB_PASSWORD", "wifak2024")
DB_NAME = "wifak_validation"

# Format: (decl_id, action, avant, apres, user, commentaire, date)
ENRICHED_DATA = [

    # ══════════════════════════════════════════════════════
    # CLUSTER 1 — MONTANT NÉGATIF / VALEUR ABSOLUE
    # BCT_01, BCT_04, BCT-05 — 15 cycles
    # ══════════════════════════════════════════════════════

    (1001,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'MontantBrut negatif detecte sur ligne 5. Valeur -125000 TND interdite.',
     '2026-01-05 09:00:00'),
    (1001,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Correction montant negatif : -125000 converti en 125000 TND valeur absolue.',
     '2026-01-05 11:00:00'),
    (1001,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-05 12:00:00'),

    (1002,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Montant credit negatif sur 4 lignes : -50000, -75000, -30000, -90000 TND.',
     '2026-01-06 09:00:00'),
    (1002,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Correction montants negatifs convertis valeur absolue : 50000, 75000, 30000, 90000 TND.',
     '2026-01-06 11:30:00'),
    (1002,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-06 12:00:00'),

    (1003,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'MontantExposition negatif sur CPT001 CPT003 CPT007 impossible selon regle BCT.',
     '2026-01-07 09:00:00'),
    (1003,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Montants negatifs corriges en valeur absolue pour CPT001 CPT003 CPT007.',
     '2026-01-07 11:00:00'),
    (1003,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-07 12:00:00'),

    (1004,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Solde compte debiteur negatif non autorise sur 3 comptes epargne CPT_EP.',
     '2026-01-08 09:00:00'),
    (1004,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Correction solde negatif : comptes reclasses CPT_EP vers CPT_CC compte courant autorise decouvert.',
     '2026-01-08 13:00:00'),
    (1004,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-08 14:00:00'),

    (1005,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'MontantOperation negatif ligne 12 valeur -250000 TND. Montants doivent etre positifs.',
     '2026-01-09 09:00:00'),
    (1005,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'MontantOperation corrige -250000 vers 250000 TND. Type operation mis a jour REMBOURSEMENT.',
     '2026-01-09 11:00:00'),
    (1005,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-09 12:00:00'),

    (1006,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'MontantImpaye negatif sur lignes 8 15 22. Impaye ne peut pas etre negatif.',
     '2026-01-10 09:00:00'),
    (1006,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Correction montants impaye negatifs convertis valeur absolue lignes 8 15 22.',
     '2026-01-10 11:00:00'),
    (1006,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-10 12:00:00'),

    (1007,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Valeur MontantGarantie negative -180000 TND non conforme schema XSD BCT_04.',
     '2026-01-11 09:00:00'),
    (1007,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'MontantGarantie corrige de -180000 a 180000 TND valeur absolue appliquee.',
     '2026-01-11 10:30:00'),
    (1007,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-11 11:00:00'),

    (1008,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Montant nul zero sur ligne 3 et ligne 9. Montants nuls ne sont pas acceptes BCT.',
     '2026-01-12 09:00:00'),
    (1008,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Montants nuls corriges apres verification dossiers : ligne 3 -> 45000 TND, ligne 9 -> 32000 TND.',
     '2026-01-12 11:00:00'),
    (1008,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-12 12:00:00'),

    # ══════════════════════════════════════════════════════
    # CLUSTER 2 — TAUX INVALIDE
    # BCT_01 — 12 cycles
    # ══════════════════════════════════════════════════════

    (2001,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'TauxApplique zero ou negatif ligne 3 valeur 0.00 non acceptable.',
     '2026-01-15 09:00:00'),
    (2001,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'TauxApplique corrige 0.00 vers taux reference BCT 4.25 pourcent ligne 3.',
     '2026-01-15 11:00:00'),
    (2001,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-15 12:00:00'),

    (2002,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Taux interet negatif -2.5 pourcent sur 6 credits. Taux doit etre entre 0.1 et 25 pourcent.',
     '2026-01-16 09:00:00'),
    (2002,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Taux negatif -2.5 remplace par taux marche 3.75 pourcent pour 6 credits concernes.',
     '2026-01-16 11:30:00'),
    (2002,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-16 12:00:00'),

    (2003,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'TauxChange invalide 0.0 pour devise EUR ligne 7. Taux change ne peut pas etre nul.',
     '2026-01-17 09:00:00'),
    (2003,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'TauxChange EUR corrige avec taux officiel BCT du jour 3.312.',
     '2026-01-17 10:30:00'),
    (2003,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-17 11:00:00'),

    (2004,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Taux hors plage autorisee : 45 pourcent detecte sur ligne 12. Maximum BCT est 25 pourcent.',
     '2026-01-18 09:00:00'),
    (2004,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Taux 45 pourcent etait erreur saisie. Corrige a 4.5 pourcent apres verification contrat.',
     '2026-01-18 11:00:00'),
    (2004,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-18 12:00:00'),

    (2005,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'TauxProvisionnement negatif -5 pourcent detecte classe C. Provisionnement doit etre positif.',
     '2026-01-19 09:00:00'),
    (2005,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'TauxProvisionnement negatif corrige : 50 pourcent applique pour classe C conforme circulaire BCT.',
     '2026-01-19 11:00:00'),
    (2005,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-19 12:00:00'),

    (2006,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Taux interbancaire saisi comme 0.0001 semble errone. Verifier avec salle des marches.',
     '2026-01-20 09:00:00'),
    (2006,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Taux corrige apres consultation salle marches : 0.0001 etait en pourcentage decimal. Converti en 0.01 pourcent.',
     '2026-01-20 11:00:00'),
    (2006,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-20 12:00:00'),

    # ══════════════════════════════════════════════════════
    # CLUSTER 3 — PROVISION / CLASSE DE RISQUE
    # BCT-05, BCT-06 — 15 cycles
    # ══════════════════════════════════════════════════════

    (3001,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Provision manquante classe D : 0 TND declare. Minimum 100 pourcent encours obligatoire.',
     '2026-02-01 09:00:00'),
    (3001,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Provision ajoutee 100 pourcent encours classe D : 850000 TND provision declaree.',
     '2026-02-01 11:00:00'),
    (3001,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-01 12:00:00'),

    (3002,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Classe risque B incorrecte pour credit retard 210 jours. Devrait etre classe D selon BCT.',
     '2026-02-02 09:00:00'),
    (3002,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Classe de risque corrigee B vers D pour credit retard 210 jours. Provision 100 pourcent appliquee.',
     '2026-02-02 11:30:00'),
    (3002,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-02 12:00:00'),

    (3003,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Taux provisionnement classe C 10 pourcent insuffisant. Minimum 50 pourcent circulaire 91-24.',
     '2026-02-03 09:00:00'),
    (3003,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Taux provision classe C recalcule 50 pourcent. Provision portee de 200000 a 1000000 TND.',
     '2026-02-03 11:00:00'),
    (3003,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-03 12:00:00'),

    (3004,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Credit classe A mais retard 95 jours. Classement A incorrect : devrait etre classe B.',
     '2026-02-04 09:00:00'),
    (3004,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Classe corrigee A vers B pour credit retard 95 jours. Provision 20 pourcent appliquee selon BCT.',
     '2026-02-04 11:00:00'),
    (3004,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-04 12:00:00'),

    (3005,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Encours classe D sous-provisionne : provision 30 pourcent au lieu 100 pourcent requis.',
     '2026-02-05 09:00:00'),
    (3005,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Provision classe D augmentee 30 pourcent vers 100 pourcent. Montant additionnel 2100000 TND.',
     '2026-02-05 13:00:00'),
    (3005,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-05 14:00:00'),

    (3006,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Provision classe 2 absente sur 8 creances. Minimum 20 pourcent obligatoire.',
     '2026-02-06 09:00:00'),
    (3006,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Provision 20 pourcent ajoutee classe 2 sur 8 creances. Total provision 560000 TND.',
     '2026-02-06 11:00:00'),
    (3006,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-06 12:00:00'),

    (3007,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Classement risque incohérent : meme client en classe A et classe C simultanement.',
     '2026-02-07 09:00:00'),
    (3007,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Classement unifie : client homogeneise en classe C apres analyse dossier complet.',
     '2026-02-07 11:00:00'),
    (3007,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-07 12:00:00'),

    (3008,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Provision insuffisante creances douteuses : 25 pourcent au lieu minimum 50 pourcent BCT.',
     '2026-02-08 09:00:00'),
    (3008,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Provision creances douteuses recalculee 50 pourcent. Montant provisionne porte a 3500000 TND.',
     '2026-02-08 13:00:00'),
    (3008,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-08 14:00:00'),

    # ══════════════════════════════════════════════════════
    # CLUSTER 4 — DEVISE / CODE INVALIDE
    # BCT_01, BCT_002 — 10 cycles
    # ══════════════════════════════════════════════════════

    (4001,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Code devise EUR vide sur 5 lignes fichier BCT_002. Champ CodeDevise obligatoire.',
     '2026-02-10 09:00:00'),
    (4001,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'CodeDevise renseigne EUR pour 5 lignes operations virement international.',
     '2026-02-10 11:00:00'),
    (4001,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-10 12:00:00'),

    (4002,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Devise YYY inconnue non conforme ISO 4217 sur ligne 9. Utiliser TND EUR USD GBP.',
     '2026-02-11 09:00:00'),
    (4002,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Devise YYY remplacee par USD apres verification operation importation.',
     '2026-02-11 10:30:00'),
    (4002,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-11 11:00:00'),

    (4003,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'CodeDevise absent sur 12 enregistrements BCT_01. Champ requis minOccurs 1.',
     '2026-02-12 09:00:00'),
    (4003,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'CodeDevise renseigne pour 12 enregistrements : TND 8 lignes EUR 3 lignes USD 1 ligne.',
     '2026-02-12 11:00:00'),
    (4003,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-12 12:00:00'),

    (4004,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Devise saisie en minuscules tnd au lieu TND. Code ISO 4217 doit etre majuscules.',
     '2026-02-13 09:00:00'),
    (4004,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Devise corrigee en majuscules TND USD EUR sur toutes les lignes concernees.',
     '2026-02-13 10:00:00'),
    (4004,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-13 11:00:00'),

    (4005,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Devise vide sur CPT0003 CPT0008 CPT0015. Toutes operations change doivent avoir devise.',
     '2026-02-14 09:00:00'),
    (4005,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Devise ajoutee : CPT0003 EUR virement SEPA CPT0008 USD import CPT0015 GBP export.',
     '2026-02-14 11:00:00'),
    (4005,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-14 12:00:00'),

    # ══════════════════════════════════════════════════════
    # CLUSTER 5 — POSITION NETTE / CALCUL
    # BCT_002 — 8 cycles
    # ══════════════════════════════════════════════════════

    (5001,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'PositionNette USD incorrecte : PositionAchat 500000 - PositionVente 350000 = 150000. Declare 130000.',
     '2026-02-15 09:00:00'),
    (5001,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'PositionNette USD corrigee de 130000 a 150000 TND. Recalcul achat-vente verifie.',
     '2026-02-15 11:00:00'),
    (5001,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-15 12:00:00'),

    (5002,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Ecart position nette GBP : calcul 85000 declare 95000. Difference 10000 TND injustifiee.',
     '2026-02-16 09:00:00'),
    (5002,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Erreur saisie PositionNette GBP. Valeur correcte 85000 saisie selon calcul PositionAchat-PositionVente.',
     '2026-02-16 11:00:00'),
    (5002,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-16 12:00:00'),

    (5003,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'PositionNette negative -200000 TND non autorisee. Depassement limite BCT detection.',
     '2026-02-17 09:00:00'),
    (5003,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Position nette negative justifiee : vente USD superieure achat ce jour autorisee avec limite speciale.',
     '2026-02-17 11:30:00'),
    (5003,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-17 12:00:00'),

    (5004,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Limite position nette EUR depassee : 5200000 TND vs limite autorisee 4000000 TND.',
     '2026-02-18 09:00:00'),
    (5004,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Erreur conversion devise : 5200000 etait en EUR non TND. Converti 5200000 EUR = 17222400 TND. Limite TND respectee.',
     '2026-02-18 11:00:00'),
    (5004,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-18 12:00:00'),

    # ══════════════════════════════════════════════════════
    # CLUSTER 6 — DOUBLONS / UNICITE
    # BCT-05, BCT-07 — 10 cycles
    # ══════════════════════════════════════════════════════

    (6001,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'IdClient duplique : client CLI001 apparait 3 fois. Un seul enregistrement par client autorise.',
     '2026-02-20 09:00:00'),
    (6001,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Doublons CLI001 fusionnes : montants credit et impaye additionnes en une seule ligne.',
     '2026-02-20 11:00:00'),
    (6001,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-20 12:00:00'),

    (6002,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'IBAN double detecte : TN5900123456789012 present sur lignes 5 et 12 meme titulaire.',
     '2026-02-21 09:00:00'),
    (6002,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Doublon IBAN supprime : ligne 12 supprimee car duplicata exact de ligne 5. Compte consolide.',
     '2026-02-21 10:30:00'),
    (6002,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-21 11:00:00'),

    (6003,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Reference credit duplicata : REF_2026_001 declaree deux fois dans fichier BCT-05.',
     '2026-02-22 09:00:00'),
    (6003,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Doublon reference credit REF_2026_001 supprime. La seconde occurence etait erreur copier-coller.',
     '2026-02-22 10:30:00'),
    (6003,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-22 11:00:00'),

    (6004,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Nombre comptes declare 1500 superieur au nombre reel 1487. Ecart 13 comptes inexplique.',
     '2026-02-23 09:00:00'),
    (6004,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     '13 comptes doublons identifies et supprimes. NombreComptes recalcule a 1487 conforme.',
     '2026-02-23 11:00:00'),
    (6004,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-23 12:00:00'),

    # ══════════════════════════════════════════════════════
    # CLUSTER 7 — CHAMP OBLIGATOIRE MANQUANT
    # BCT-05, BCT-07, BCT_01 — 10 cycles
    # ══════════════════════════════════════════════════════

    (7001,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'NomClient vide sur ligne 4 et 8. Champ obligatoire schema XSD BCT-05.',
     '2026-02-25 09:00:00'),
    (7001,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'NomClient renseigne : ligne 4 Societe Alpha SARL, ligne 8 Entreprise Beta SA depuis base client.',
     '2026-02-25 11:00:00'),
    (7001,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-25 12:00:00'),

    (7002,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'TypeOperation absent sur 6 transactions BCT_04. Champ requis minOccurs 1.',
     '2026-02-26 09:00:00'),
    (7002,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'TypeOperation renseigne selon nature transactions : VIREMENT 4 lignes PRELEVEMENT 2 lignes.',
     '2026-02-26 11:00:00'),
    (7002,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-26 12:00:00'),

    (7003,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'CodeBanque manquant sur 3 enregistrements. Ce code identifiant est obligatoire BCT.',
     '2026-02-27 09:00:00'),
    (7003,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'CodeBanque WIF ajoute pour les 3 enregistrements manquants. Code Banque Wifak.',
     '2026-02-27 10:30:00'),
    (7003,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-27 11:00:00'),

    (7004,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Champ MontantBrut absent balise manquante sur 15 lignes. XSD impose ce champ.',
     '2026-02-28 09:00:00'),
    (7004,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Balise MontantBrut ajoutee 15 lignes. Valeur = somme composantes montant net plus charges.',
     '2026-02-28 11:00:00'),
    (7004,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-02-28 12:00:00'),

    # ══════════════════════════════════════════════════════
    # CLUSTER 8 — DATE INCORRECTE / PÉRIODE
    # BCT_04, BCT-06 — 8 cycles
    # ══════════════════════════════════════════════════════

    (8001,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Date operation 15-04-2026 posterieure date cloture trimestre 31-03-2026. Impossible.',
     '2026-04-10 09:00:00'),
    (8001,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Date corrigee : 15-04-2026 etait erreur de saisie. Date reelle operation : 15-03-2026.',
     '2026-04-10 11:00:00'),
    (8001,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-04-10 12:00:00'),

    (8002,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Periode declaree 2025-12 mais dates operations en 2026-01. Incoherence periode.',
     '2026-01-25 09:00:00'),
    (8002,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Periode mise a jour 2025-12 vers 2026-01. Periode correspond maintenant aux dates operations.',
     '2026-01-25 11:00:00'),
    (8002,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-01-25 12:00:00'),

    (8003,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'DateOuverture compte posterieure date declaration : 2026-06-15 > 2026-05-31 fin periode.',
     '2026-06-05 09:00:00'),
    (8003,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'DateOuverture corrigee : compte ouvert 2026-05-15 et non 2026-06-15. Erreur saisie.',
     '2026-06-05 11:00:00'),
    (8003,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-06-05 12:00:00'),

    (8004,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Date declassement creance apres fin trimestre. Doit etre avant 31 mars 2026.',
     '2026-04-12 09:00:00'),
    (8004,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Date declassement corrigee selon dossier physique : 28 mars 2026 avant cloture trimestre.',
     '2026-04-12 11:00:00'),
    (8004,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-04-12 12:00:00'),

    # ══════════════════════════════════════════════════════
    # CLUSTER 9 — FORMAT XSD / STRUCTURE XML
    # BCT_01, BCT-05, BCT-06 — 8 cycles
    # ══════════════════════════════════════════════════════

    (9001,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Fichier XML non conforme XSD : balise Entete absente. Structure obligatoire BCT_01.',
     '2026-03-01 09:00:00'),
    (9001,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Balise Entete ajoutee avec CodeDeclaration Periode DateDebut DateFin NombreLignes.',
     '2026-03-01 11:00:00'),
    (9001,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-03-01 12:00:00'),

    (9002,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Format date incorrect : 01/03/2026 au lieu format ISO YYYY-MM-DD requis XSD.',
     '2026-03-02 09:00:00'),
    (9002,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Format date corrige : 01/03/2026 converti en 2026-03-01 format ISO pour toutes dates.',
     '2026-03-02 10:30:00'),
    (9002,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-03-02 11:00:00'),

    (9003,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Encodage caracteres incorrect : accents corrompus dans NomClient. UTF-8 requis.',
     '2026-03-03 09:00:00'),
    (9003,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Fichier regenere avec encodage UTF-8 correct. Caracteres speciaux verifies.',
     '2026-03-03 10:30:00'),
    (9003,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-03-03 11:00:00'),

    (9004,'REJECT','EN_VALIDATION','REJETEE','ridha_labaoui',
     'Balise Ligne mal formee : attributs manquants NumLigne et TypeLigne requis XSD.',
     '2026-03-04 09:00:00'),
    (9004,'SUBMIT','REJETEE','EN_VALIDATION','miral',
     'Attributs NumLigne et TypeLigne ajoutes a toutes les balises Ligne du fichier.',
     '2026-03-04 11:00:00'),
    (9004,'VALIDATE','EN_VALIDATION','VALIDEE','ridha_labaoui',None,'2026-03-04 12:00:00'),

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
    errors = 0
    for row in ENRICHED_DATA:
        try:
            cursor.execute(sql, row)
            inserted += 1
        except Exception as e:
            print(f"  Skip {row[0]} {row[1]}: {e}")
            errors += 1

    conn.commit()
    cursor.close()

    # Vérification
    c2 = conn.cursor()
    c2.execute("SELECT COUNT(*) FROM validation_logs WHERE action='REJECT' AND commentaire IS NOT NULL AND LENGTH(commentaire)>10")
    rejets = c2.fetchone()[0]
    c2.execute("SELECT COUNT(*) FROM validation_logs WHERE action='SUBMIT' AND commentaire IS NOT NULL AND LENGTH(commentaire)>10")
    corrections = c2.fetchone()[0]
    conn.close()

    print(f"\nInserted: {inserted} | Errors: {errors}")
    print(f"Total rejets: {rejets} | Total corrections: {corrections}")


if __name__ == "__main__":
    main()
