-- Sprint Maille 2 : le compte de charge de l'ecriture comptable.
--
-- L'extrait du cahier des charges porte a l'analyse le 9 septembre 2026 a fait
-- apparaitre ce que l'ecriture attendue contient reellement :
--
--   DEBIT  : CODE UNITE - 64380090200 - cle - MONTANT - Libelle
--   CREDIT : AGENCE COMPTE COURANT - N° COMPTE - CLE - MONTANT - Libelle
--
-- 64380090200 est le COMPTE DE CHARGE sur lequel la depense s'impute. Le module
-- le transporte dans la charge publiee ; il ne code toujours ni le sens
-- debit/credit, ni la structure de l'ecriture (CLAUDE.md sections 8 et 15).
--
-- POURQUOI DANS parametre_systeme, ET NON EN DUR NI EN VARIABLE D'ENVIRONNEMENT
--
-- C'est la table qui porte deja SEUIL_AIGUILLAGE_DR, c'est-a-dire la valeur qui
-- commande le niveau d'approbation de la banque. Elle a ete concue pour cela :
-- une valeur qui gouverne de l'argent et doit pouvoir changer SANS
-- REDEPLOIEMENT. Un compte de charge releve exactement de la meme categorie --
-- un plan comptable evolue, et attendre une livraison pour le suivre serait
-- absurde.
--
-- Une variable d'environnement aurait exige un redemarrage de conteneur, ce qui
-- reste une operation de deploiement. Ici, un UPDATE suffit, et il prend effet a
-- la transmission suivante.
--
-- ABSENT OU VIDE : REFUS DE PUBLIER, jamais un repli. Meme doctrine que le seuil
-- d'aiguillage (RG-08, Sprint 4.3), et elle s'impose plus fort encore : publier
-- un message de paiement avec un compte de charge absent ou devine, c'est imputer
-- de l'argent sur le mauvais compte, sans erreur visible. Le refus est rendu en
-- 500 CHARGE_INCOMPLETE -- code existant, dont le motif d'origine s'applique tel
-- quel : l'etat est cloture donc fige, l'appelant n'a rien a corriger.
--
-- Voir docs/decisions/2026-09-09-contenu-de-la-charge-comptable.md, decisions 5 et 6.

INSERT INTO parametre_systeme (code, libelle, valeur, actif)
VALUES (
    'COMPTE_CHARGE_RATIONS',
    'Compte de charge sur lequel s''impute la depense de rations et transport de la garde armee. Transporte dans la charge publiee a la comptabilite. Modifiable par UPDATE, effet a la transmission suivante, sans redeploiement.',
    '64380090200',
    TRUE
);
