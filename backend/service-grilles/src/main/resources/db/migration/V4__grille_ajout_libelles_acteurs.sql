-- Sprint 2.2 : libelles lisibles du createur (ARH) et du validateur (DRH).
--
-- POURQUOI CES COLONNES. La table porte deja id_createur et id_validateur, qui
-- referencent utilisateurs dans la base rations_identite. Ces identifiants sont
-- justes mais illisibles : une liste de grilles affichant « cree par 4 » n'est
-- pas exploitable par un Analyste RH.
--
-- Les traduire a la lecture supposerait un appel reseau par ligne affichee vers
-- le service Identite, sur le chemin de consultation, alors qu'aucun endpoint
-- accessible a l'ARH ne permet de traduire un identifiant en nom
-- (/identite/utilisateurs est reserve au role ADMIN). Le libelle est donc
-- RECOPIE au moment de l'acte, depuis GET /identite/moi, puis fige ici.
-- Decision Sprint 2.2, docs/decisions/2026-08-27-libelle-acteur-grille.md.
--
-- CE N'EST PAS UNE DENORMALISATION DE CONFORT. Le libelle fige repond a la
-- question « qui a cree cette grille, tel qu'il etait connu ce jour-la ». Si
-- Claire NKOLO se marie ou quitte la banque, la grille de septembre continue de
-- nommer son auteur d'alors : c'est le comportement attendu d'une piece de
-- controle interne, pas un cache a rafraichir.
--
-- Colonnes NULLABLES a dessein : le libelle est un confort de lecture, pas une
-- donnee de reference. L'identifiant technique reste id_createur. Une contrainte
-- NOT NULL ferait dependre l'ecriture en base de la disponibilite d'un service
-- distant, ce qui n'a pas sa place dans un schema.
--
-- Migration purement additive et versionnee (CLAUDE.md section 2) : ni
-- recreation de table, ni auto-DDL Hibernate.

ALTER TABLE grille_tarifaire
    ADD COLUMN libelle_createur   VARCHAR(100),
    ADD COLUMN libelle_validateur VARCHAR(100);

COMMENT ON COLUMN grille_tarifaire.libelle_createur   IS 'Nom lisible de l''ARH auteur, recopie a la creation depuis GET /identite/moi. Fige : ne suit pas les changements ulterieurs du profil.';
COMMENT ON COLUMN grille_tarifaire.libelle_validateur IS 'Nom lisible de la DRH ayant tranche, recopie a la validation (Sprint 2.3). Fige de la meme facon.';

-- Reprise des quatre grilles de reference inserees par V2, dont les
-- identifiants d'acteurs sont connus (claire_nkolo id 4, agnes_tchinda id 5).
-- Sans cela, les seules grilles ACTIVE du module s'afficheraient sans auteur.
UPDATE grille_tarifaire
   SET libelle_createur   = 'NKOLO Claire',
       libelle_validateur = 'TCHINDA Agnes'
 WHERE id_createur = 4
   AND id_validateur = 5
   AND libelle_createur IS NULL;
