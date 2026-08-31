-- Sprint 3.3 : verrou d'unicite RG-04 en base.
--
-- POINT OUVERT N.1 DU SPRINT 3.2, traite ici comme prevu :
--   docs/decisions/2026-08-31-refus-de-ligne-et-codes-erreur-saisie.md section 4
--   docs/resumes-sprints/sprint-3.2-valorisation-et-controle-des-doublons.md
--
-- CE QUE CET INDEX AJOUTE. ControleDoublonService applique RG-04 par une LECTURE
-- AVANT ECRITURE. Entre la lecture et l'insertion, il existe une fenetre : deux
-- requetes concurrentes portant la meme combinaison peuvent franchir le controle
-- toutes les deux et produire deux lignes identiques — donc deux paiements. Le
-- cas est peu probable (un agent saisit ligne a ligne) mais pas impossible :
-- double clic, deux onglets, un client qui rejoue une requete.
--
-- L'INDEX NE REMPLACE PAS LE CONTROLE APPLICATIF, il le double. Le controle
-- applicatif produit le message qui nomme le beneficiaire, la journee, la nature
-- et la session ; l'index ne produit qu'une violation de contrainte. L'index est
-- le filet, pas le message. La violation est traduite en 409 DOUBLON_LIGNE par
-- GestionnaireErreursApi, exactement comme service-grilles le fait deja pour
-- ux_grille_active_par_couple (Sprint 2.2).
--
-- PORTEE : la FICHE, donc la journee. RG-04 porte sur la combinaison
-- (fiche, beneficiaire, nature, session) — l'unicite (id_processus, date_jour) de
-- la migration V1 fait de la fiche l'identite de la journee. RG-15, qui etend le
-- controle a toute la periode et a tous les etats d'une meme unite, ne peut pas
-- etre un index : elle porte sur une jointure a trois tables et sur des lignes
-- d'autres processus. Elle reste applicative (Sprint 6bis).
--
-- Index unique et non contrainte UNIQUE : meme effet, mais un index se cree et se
-- supprime sans toucher a la definition de la table. Meme forme que
-- ux_grille_active_par_couple cote Grilles.
--
-- SI CETTE MIGRATION ECHOUE au deploiement, c'est que des doublons existent deja
-- en base — creer l'index les revele plutot qu'il ne les cree. Les identifier :
--   SELECT id_fiche_journaliere, id_beneficiaire, nature, session, COUNT(*)
--     FROM ligne_prestation
--    GROUP BY 1, 2, 3, 4 HAVING COUNT(*) > 1;

CREATE UNIQUE INDEX ux_ligne_par_fiche_beneficiaire_nature_session
    ON ligne_prestation (id_fiche_journaliere, id_beneficiaire, nature, session);

COMMENT ON INDEX ux_ligne_par_fiche_beneficiaire_nature_session IS 'RG-04 : un beneficiaire ne figure pas deux fois sur la meme journee pour la meme nature et la meme session. Filet contre les ecritures concurrentes ; le message de refus vient du controle applicatif.';
