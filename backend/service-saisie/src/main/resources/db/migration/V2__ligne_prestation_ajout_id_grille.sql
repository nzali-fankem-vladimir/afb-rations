-- Sprint 3.1 : tracabilite de la grille d'origine sur chaque ligne de prestation.
--
-- POURQUOI CETTE COLONNE. La ligne porte deja montant_applique (V1), repris de la
-- grille ACTIVE au moment de la saisie (RG-03) et fige. Mais un montant conteste
-- plus tard n'est justifiable que si l'on sait DE QUELLE grille il venait :
-- l'endpoint GET /grilles/active du service Grilles renvoie idGrille, dateDebut,
-- dateFin precisement pour cela (Sprint 2.4), et cette information n'avait
-- jusqu'ici nulle part ou atterrir.
--
-- Dette identifiee au Sprint 2.4, consignee a trois endroits pour etre traitee
-- ici et pas redecouverte :
--   docs/decisions/2026-08-27-resolution-du-montant-applicable.md section 5
--   docs/resumes-sprints/sprint-2.4-resolution-du-montant-et-cloture-du-sprint-2.md section 2.7
--   CLAUDE.md section 17, ligne Sprint 2.4
--
-- PAS DE CLE ETRANGERE. grille_tarifaire vit dans la base rations_grilles :
-- aucune FK inter-base n'est possible. Reference logique par identifiant simple,
-- meme convention que id_createur / id_validateur sur grille_tarifaire.
--
-- NULLABLE A DESSEIN. La valorisation qui renseignera cette colonne releve du
-- Sprint 3.2. Les lignes creees avant en sont depourvues, et une contrainte
-- NOT NULL ferait dependre l'ecriture d'une donnee resolue par un service
-- distant. montant_applique reste la donnee de reference du montant ; id_grille
-- est sa justification.
--
-- Migration purement additive et versionnee (CLAUDE.md section 12) : ni
-- recreation de table, ni auto-DDL Hibernate. La migration V1, appliquee au
-- Sprint 0.5, n'est pas modifiee.

ALTER TABLE ligne_prestation
    ADD COLUMN id_grille BIGINT;

COMMENT ON COLUMN ligne_prestation.id_grille IS 'Grille d''ou provient montant_applique (base rations_grilles). Reference logique sans FK inter-base. Renseignee par la valorisation du Sprint 3.2 ; nulle avant.';
