SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

UPDATE wifak_validation.declaration_types SET
  nom = 'Déclaration Mensuelle des Risques de Change et de Taux',
  date_limite = 'Le 10 de chaque mois pour le mois précédent'
WHERE code = 'BCT_01';

UPDATE wifak_validation.declaration_types SET
  nom = 'Déclaration quotidienne des opérations de change',
  date_limite = 'Jour ouvrable suivant avant 10h00'
WHERE code = 'BCT_002';

UPDATE wifak_validation.declaration_types SET
  nom = 'Déclaration de Conformité IT & Sécurité',
  date_limite = 'Avant le 31 Mars'
WHERE code = 'BCT_03';

UPDATE wifak_validation.declaration_types SET
  nom = 'Déclaration Trimestrielle des Opérations Bancaires',
  date_limite = '30 jours après la fin du trimestre'
WHERE code = 'BCT_04';

UPDATE wifak_validation.declaration_types SET
  nom = 'Déclaration Mensuelle des Crédits Accordés aux Entreprises',
  date_limite = 'Le 15 de chaque mois pour le mois précédent'
WHERE code = 'BCT-05';

UPDATE wifak_validation.declaration_types SET
  nom = 'Déclaration trimestrielle des créances classées',
  date_limite = '15 jours après la fin du trimestre'
WHERE code = 'BCT-06';

UPDATE wifak_validation.declaration_types SET
  nom = 'Déclaration mensuelle des comptes bancaires',
  date_limite = '10 du mois suivant'
WHERE code = 'BCT-07';

UPDATE wifak_validation.declaration_types SET
  nom = 'Déclaration Test',
  date_limite = 'fin Juin'
WHERE code = 'BCT_07';

-- Fix commentaires de rejet dans declarations
UPDATE wifak_validation.declarations SET
  commentaire_rejet = CONVERT(CAST(CONVERT(commentaire_rejet USING latin1) AS BINARY) USING utf8mb4)
WHERE commentaire_rejet IS NOT NULL;
