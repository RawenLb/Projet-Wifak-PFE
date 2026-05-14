SET NAMES utf8mb4;
SET character_set_client = utf8mb4;
SET character_set_results = utf8mb4;
SET collation_connection = utf8mb4_unicode_ci;

USE wifak_validation;

UPDATE declaration_types SET 
  nom = 'Déclaration Mensuelle des Risques de Change et de Taux',
  date_limite = 'Le 10 de chaque mois pour le mois précédent'
WHERE code = 'BCT_01';

UPDATE declaration_types SET 
  nom = 'Déclaration quotidienne des opérations de change',
  date_limite = 'Jour ouvrable suivant avant 10h00'
WHERE code = 'BCT_002';

UPDATE declaration_types SET 
  nom = 'Déclaration de Conformité IT & Sécurité',
  date_limite = 'Avant le 31 Mars'
WHERE code = 'BCT_03';

UPDATE declaration_types SET 
  nom = 'Déclaration Trimestrielle des Opérations Bancaires',
  date_limite = '30 jours après la fin du trimestre'
WHERE code = 'BCT_04';

UPDATE declaration_types SET 
  nom = 'Déclaration Mensuelle des Crédits Accordés aux Entreprises',
  date_limite = 'Le 15 de chaque mois pour le mois précédent'
WHERE code = 'BCT-05';

UPDATE declaration_types SET 
  nom = 'Déclaration trimestrielle des créances classées',
  date_limite = '15 jours après la fin du trimestre'
WHERE code = 'BCT-06';

UPDATE declaration_types SET 
  nom = 'Déclaration mensuelle des comptes bancaires',
  date_limite = '10 du mois suivant'
WHERE code = 'BCT-07';

UPDATE declaration_types SET 
  nom = 'Déclaration Test',
  date_limite = 'fin Juin'
WHERE code = 'BCT_07';

-- Fix commentaires de rejet
UPDATE declarations SET commentaire_rejet = REPLACE(commentaire_rejet, 'D??claration', 'Déclaration') WHERE commentaire_rejet LIKE '%D??claration%';
UPDATE declarations SET commentaire_rejet = REPLACE(commentaire_rejet, 'g??n??r??', 'généré') WHERE commentaire_rejet LIKE '%g??n??r??%';
UPDATE declarations SET commentaire_rejet = REPLACE(commentaire_rejet, 'op??rations', 'opérations') WHERE commentaire_rejet LIKE '%op??rations%';
UPDATE declarations SET commentaire_rejet = REPLACE(commentaire_rejet, 'n??gatif', 'négatif') WHERE commentaire_rejet LIKE '%n??gatif%';
UPDATE declarations SET commentaire_rejet = REPLACE(commentaire_rejet, 'n??gatifs', 'négatifs') WHERE commentaire_rejet LIKE '%n??gatifs%';
UPDATE declarations SET commentaire_rejet = REPLACE(commentaire_rejet, 'apr??s', 'après') WHERE commentaire_rejet LIKE '%apr??s%';
UPDATE declarations SET commentaire_rejet = REPLACE(commentaire_rejet, 'pr??c??dent', 'précédent') WHERE commentaire_rejet LIKE '%pr??c??dent%';
UPDATE declarations SET commentaire_rejet = REPLACE(commentaire_rejet, 'Risques', 'Risques') WHERE commentaire_rejet LIKE '%Risques%';
UPDATE declarations SET commentaire_rejet = REPLACE(commentaire_rejet, '???', '') WHERE commentaire_rejet LIKE '%???%';
