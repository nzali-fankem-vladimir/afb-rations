-- Sprint Maille 1 : la periode recopiee sur la fiche devient un intervalle.
--
-- Pendant de la migration V6 du service Workflow. Le metier a etabli le
-- 9 septembre 2026 que le cycle de paiement est HEBDOMADAIRE (point M-04), et
-- la periode d'un processus est desormais un couple (date_debut, date_fin).
-- La recopie figee posee par la V3 doit suivre la meme forme, sans quoi RG-15
-- ne saurait plus regrouper les fiches d'une meme periode.
--
-- POURQUOI LA COPIE NE PERIME TOUJOURS PAS -- la justification de la V3 est
-- REECRITE, pas recopiee. Elle s'appuyait sur l'index
-- ux_processus_normal_par_periode, qui n'existe plus : la V6 le remplace par la
-- contrainte d'exclusion ex_processus_normal_sans_chevauchement. La garantie
-- est en realite PLUS FORTE qu'avant. L'ancien index interdisait deux etats
-- NORMAL portant la meme periode ; la nouvelle contrainte interdit aussi ceux
-- qui se CHEVAUCHENT, meme partiellement. Le triplet (unite, date_debut,
-- date_fin) reste donc l'identite d'un processus NORMAL, et il ne peut pas
-- changer sous la fiche qui l'a recopie.
--
-- NULLABLES A DESSEIN, comme les colonnes qu'elles remplacent : ce sont des
-- copies de confort, id_processus reste la donnee de reference.

-- --------------------------------------------------------------------------
-- 1. Les deux bornes
-- --------------------------------------------------------------------------

ALTER TABLE fiche_journaliere
    ADD COLUMN date_debut DATE,
    ADD COLUMN date_fin   DATE;

-- --------------------------------------------------------------------------
-- 2. Conversion des lignes existantes
--
-- Meme conversion qu'en V6 cote Workflow, et pour la meme raison : un mois est
-- un intervalle, la traduction est mecanique et sans perte.
--
-- Le WHERE n'est pas une precaution de style : les fiches anterieures a la V3
-- ne portent aucune periode recopiee, et MAKE_DATE sur un NULL rendrait NULL
-- sans erreur -- la ligne resterait donc telle quelle. Le WHERE le dit plutot
-- que de le laisser deviner.
-- --------------------------------------------------------------------------

UPDATE fiche_journaliere
   SET date_debut = MAKE_DATE(annee_paiement, mois_paiement, 1),
       date_fin   = (MAKE_DATE(annee_paiement, mois_paiement, 1)
                     + INTERVAL '1 month - 1 day')::DATE
 WHERE mois_paiement IS NOT NULL
   AND annee_paiement IS NOT NULL;

-- --------------------------------------------------------------------------
-- 3. L'index de regroupement de RG-15
--
-- Chemin d'acces du sous-sprint 6bis.2 : toutes les fiches d'une unite pour une
-- periode, tous processus confondus. L'ordre des colonnes suit celui des
-- valeurs connues en premier dans la requete -- l'unite, puis les bornes.
-- --------------------------------------------------------------------------

DROP INDEX IF EXISTS idx_fiche_journaliere_unite_periode;

CREATE INDEX idx_fiche_journaliere_unite_periode
    ON fiche_journaliere (code_unite, date_debut, date_fin);

-- --------------------------------------------------------------------------
-- 4. Les anciennes colonnes disparaissent
-- --------------------------------------------------------------------------

ALTER TABLE fiche_journaliere
    DROP COLUMN mois_paiement,
    DROP COLUMN annee_paiement;

-- --------------------------------------------------------------------------
-- 5. Documentation des colonnes
-- --------------------------------------------------------------------------

COMMENT ON COLUMN fiche_journaliere.date_debut IS
    'Premier jour de la periode du processus, INCLUS. Recopie et fige a l''ouverture de la fiche. Sert au regroupement local exige par RG-15. Remplace mois_paiement (Maille 1, M-04).';

COMMENT ON COLUMN fiche_journaliere.date_fin IS
    'Dernier jour de la periode du processus, INCLUS. Recopie et fige a l''ouverture de la fiche. Sert au regroupement local exige par RG-15. Remplace annee_paiement (Maille 1, M-04).';
