-- Fix encoding issues: ?? -> correct French characters
-- declaration_types: date_limite
UPDATE wifak_validation.declaration_types SET date_limite = 'Le 10 de chaque mois pour le mois précédent' WHERE id = 1;
UPDATE wifak_validation.declaration_types SET date_limite = 'Le 15 de chaque mois pour le mois précédent' WHERE code = 'BCT-05';
UPDATE wifak_validation.declaration_types SET date_limite = '15 jours après la fin du trimestre' WHERE code = 'BCT-06';

-- declaration_types: nom
UPDATE wifak_validation.declaration_types SET nom = 'Déclaration Mensuelle des Risques de Change et de Taux' WHERE code = 'BCT_01';
UPDATE wifak_validation.declaration_types SET nom = 'Déclaration quotidienne des opérations de change' WHERE code = 'BCT_002';
UPDATE wifak_validation.declaration_types SET nom = 'Déclaration de Conformité IT & Sécurité' WHERE code = 'BCT_03';
UPDATE wifak_validation.declaration_types SET nom = 'Déclaration Trimestrielle des Opérations Bancaires' WHERE code = 'BCT_04';
UPDATE wifak_validation.declaration_types SET nom = 'Déclaration Mensuelle des Crédits Accordés aux Entreprises' WHERE code = 'BCT-05';
UPDATE wifak_validation.declaration_types SET nom = 'Déclaration trimestrielle des créances classées' WHERE code = 'BCT-06';

-- declarations: commentaire_rejet - fix common patterns
UPDATE wifak_validation.declarations SET commentaire_rejet = REPLACE(commentaire_rejet, 'D??claration', 'Déclaration') WHERE commentaire_rejet LIKE '%D??claration%';
UPDATE wifak_validation.declarations SET commentaire_rejet = REPLACE(commentaire_rejet, 'g??n??r??', 'généré') WHERE commentaire_rejet LIKE '%g??n??r??%';
UPDATE wifak_validation.declarations SET commentaire_rejet = REPLACE(commentaire_rejet, 'op??rations', 'opérations') WHERE commentaire_rejet LIKE '%op??rations%';
UPDATE wifak_validation.declarations SET commentaire_rejet = REPLACE(commentaire_rejet, 'n??gatif', 'négatif') WHERE commentaire_rejet LIKE '%n??gatif%';
UPDATE wifak_validation.declarations SET commentaire_rejet = REPLACE(commentaire_rejet, 'n??gatifs', 'négatifs') WHERE commentaire_rejet LIKE '%n??gatifs%';
UPDATE wifak_validation.declarations SET commentaire_rejet = REPLACE(commentaire_rejet, 'Soldes n??gatifs', 'Soldes négatifs') WHERE commentaire_rejet LIKE '%Soldes n??gatifs%';
UPDATE wifak_validation.declarations SET commentaire_rejet = REPLACE(commentaire_rejet, 'devise vide', 'devise vide') WHERE commentaire_rejet LIKE '%devise vide%';
UPDATE wifak_validation.declarations SET commentaire_rejet = REPLACE(commentaire_rejet, 'taux n??gatif', 'taux négatif') WHERE commentaire_rejet LIKE '%taux n??gatif%';
UPDATE wifak_validation.declarations SET commentaire_rejet = REPLACE(commentaire_rejet, 'absent ou vide ???', 'absent ou vide') WHERE commentaire_rejet LIKE '%absent ou vide ???%';
UPDATE wifak_validation.declarations SET commentaire_rejet = REPLACE(commentaire_rejet, '???', '') WHERE commentaire_rejet LIKE '%???%';
UPDATE wifak_validation.declarations SET commentaire_rejet = REPLACE(commentaire_rejet, '??', 'é') WHERE commentaire_rejet LIKE '%??%';

-- description fields
UPDATE wifak_validation.declaration_types SET description = REPLACE(description, '??', 'é') WHERE description LIKE '%??%';
