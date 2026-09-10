-- Sprint Maille 1 : la periode d'un processus devient un intervalle de dates.
--
-- Le metier a etabli le 9 septembre 2026 que le cycle de paiement est
-- HEBDOMADAIRE et non mensuel (point M-04). Le couple (mois_paiement,
-- annee_paiement) ne peut pas porter une semaine : celle du 29 septembre au
-- 5 octobre n'appartient a aucun mois, et lui en attribuer un serait inventer.
--
-- La forme retenue est un intervalle de dates plutot qu'un numero de semaine
-- ISO. Motif decisif : elle survit a tout changement ulterieur de cadence sans
-- nouvelle migration -- le metier vient de changer d'avis une fois. Elle evite
-- aussi la semaine 53 et l'annee ISO decalee.
-- Voir docs/decisions/2026-09-09-rythme-de-paiement-et-maille-de-la-periode.md
--
-- LA TABLE N'EST PAS RENOMMEE. « processus_mensuel » devient un nom vieilli,
-- assume : la chaine litterale est ecrite comme VALEUR dans entite_cible chez
-- 14 fichiers de trois services, et 193 evenements d'audit la portent deja.
-- Renommer couperait l'historique du journal, et le reecrire est ce que son
-- immuabilite interdit (decision 4 du meme document).

-- --------------------------------------------------------------------------
-- 1. Les deux bornes, nullables le temps de la conversion
-- --------------------------------------------------------------------------

ALTER TABLE processus_mensuel
    ADD COLUMN date_debut DATE,
    ADD COLUMN date_fin   DATE;

-- --------------------------------------------------------------------------
-- 2. Conversion des lignes existantes
--
-- Un mois EST un intervalle : la conversion est mecanique et sans perte.
-- date_trunc rend le premier jour ; on ajoute un mois moins un jour pour
-- obtenir le dernier, ce qui traite fevrier et les annees bissextiles sans
-- table de correspondance.
-- --------------------------------------------------------------------------

UPDATE processus_mensuel
   SET date_debut = MAKE_DATE(annee_paiement, mois_paiement, 1),
       date_fin   = (MAKE_DATE(annee_paiement, mois_paiement, 1)
                     + INTERVAL '1 month - 1 day')::DATE;

-- --------------------------------------------------------------------------
-- 3. Les bornes deviennent obligatoires
-- --------------------------------------------------------------------------

ALTER TABLE processus_mensuel
    ALTER COLUMN date_debut SET NOT NULL,
    ALTER COLUMN date_fin   SET NOT NULL;

-- Une periode a l'envers n'a aucun sens. Le rendre IMPOSSIBLE plutot
-- qu'improbable : la contrainte d'exclusion posee plus bas s'appuie sur un
-- daterange, et PostgreSQL refuse d'en construire un dont la borne haute
-- precede la borne basse -- l'erreur serait alors technique et illisible.
ALTER TABLE processus_mensuel
    ADD CONSTRAINT ck_processus_periode_ordonnee CHECK (date_fin >= date_debut);

-- --------------------------------------------------------------------------
-- 4. L'unicite de RG-15 : de l'egalite au non-chevauchement
--
-- L'ancien index interdisait deux etats NORMAL sur le meme couple
-- (unite, mois, annee). Avec deux entiers, deux periodes etaient egales ou
-- disjointes -- jamais partiellement superposees.
--
-- Avec un intervalle, ce n'est plus vrai. Un index unique sur
-- (code_unite, date_debut, date_fin) laisserait coexister :
--     etat A : du 07/09 au 13/09
--     etat B : du 10/09 au 16/09
-- Les journees du 10 au 13 appartiendraient a DEUX etats NORMAL de la meme
-- unite. RG-15 interroge « les autres etats de la meme periode » : la question
-- n'aurait plus de reponse definie, et ces journees deviendraient payables
-- deux fois.
--
-- La contrainte d'exclusion est donc STRICTEMENT PLUS FORTE que l'index
-- qu'elle remplace : elle interdit l'egalite ET le chevauchement, et c'est la
-- base qui la fait respecter, pas une discipline de code.
--
-- Bornes INCLUSIVES des deux cotes ('[]') : date_fin est le dernier jour de la
-- periode, pas le premier jour de la suivante. Deux semaines consecutives
-- [07-13] et [14-20] ne se chevauchent donc pas.
-- --------------------------------------------------------------------------

-- Requise par EXCLUDE USING gist pour combiner une egalite (code_unite) et un
-- recouvrement d'intervalle dans le meme index. Point D-12 au registre : en
-- production, la creation d'extension est un geste de DBA.
CREATE EXTENSION IF NOT EXISTS btree_gist;

DROP INDEX IF EXISTS ux_processus_normal_par_periode;

ALTER TABLE processus_mensuel
    ADD CONSTRAINT ex_processus_normal_sans_chevauchement
    EXCLUDE USING gist (
        code_unite WITH =,
        DATERANGE(date_debut, date_fin, '[]') WITH &&
    ) WHERE (type_processus = 'NORMAL');

-- --------------------------------------------------------------------------
-- 5. Les anciennes colonnes disparaissent
--
-- Supprimees plutot que conservees en double. Deux representations de la meme
-- periode sur la meme ligne, c'est deux verites dont l'une sera lue par erreur
-- -- et la conversion etant sans perte, il n'y a rien a conserver.
-- --------------------------------------------------------------------------

ALTER TABLE processus_mensuel
    DROP COLUMN mois_paiement,
    DROP COLUMN annee_paiement;

-- --------------------------------------------------------------------------
-- 6. Documentation des colonnes
-- --------------------------------------------------------------------------

COMMENT ON COLUMN processus_mensuel.date_debut IS
    'Premier jour de la periode de paiement, INCLUS. Remplace mois_paiement (Maille 1, M-04).';

COMMENT ON COLUMN processus_mensuel.date_fin IS
    'Dernier jour de la periode de paiement, INCLUS -- pas le premier jour de la periode suivante. Remplace annee_paiement (Maille 1, M-04).';

COMMENT ON CONSTRAINT ex_processus_normal_sans_chevauchement ON processus_mensuel IS
    'RG-15 : deux etats NORMAL d''une meme unite ne peuvent ni porter la meme periode, ni se chevaucher, meme partiellement. Plusieurs COMPLEMENTAIRE restent autorises sur la meme periode.';
