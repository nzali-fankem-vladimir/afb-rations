-- Sprint 5.1 : statut d'integration comptable sur processus_mensuel.
--
-- L'ECART ARBITRE. Le contrat d'API (section 7) expose, par
-- GET /transmission/processus/{id}, un statut d'integration a TROIS valeurs
-- (EN_ATTENTE, INTEGRE, REJETE), une reference comptable, une date de
-- traitement et un motif en cas de rejet. Le dictionnaire (CLAUDE.md section 4)
-- ne prevoit qu'un BOOLEEN transmis_comptabilite, et le service Transmission n'a
-- pas de base propre (CLAUDE.md section 3). Un booleen ne porte ni trois
-- valeurs, ni une reference, ni une date : il fallait trancher ou loger ces
-- donnees.
--
-- POURQUOI ICI, ET PAS DANS UNE BASE PROPRE AU SERVICE TRANSMISSION.
--
--   1. transmis_comptabilite vit deja sur cette ligne. Le drapeau de RG-13 et le
--      statut d'integration decrivent le meme fait -- ou en est cet etat vis-a-vis
--      de la comptabilite. Les separer dans deux bases laisserait deux moities de
--      verite a tenir coherentes a la main, sans transaction commune pour les y
--      obliger.
--   2. Le guide du Sprint 5.2 pose deja que la mise a jour du processus se fait
--      « par appel a l'API du service Workflow, jamais par acces direct a sa
--      base » : la donnee est donc supposee vivre ici.
--   3. Une base propre a Transmission obligerait a corriger les sections 3 et 4
--      de CLAUDE.md (« Transmission : topics Kafka », dix tables), a creer une
--      onzieme table, une base et son initialisation. Beaucoup d'architecture
--      pour quatre colonnes.
--
-- CONSEQUENCE ASSUMEE : le service Transmission depend du service Workflow pour
-- lire et ecrire ces valeurs, par son API et jamais par sa base (diagramme AR04).
-- C'est le meme couplage que Workflow -> Saisie pour la consolidation.
--
-- POURQUOI V4 ET NON V3, comme l'annonce le guide 5.1 : V3 est deja pris par
-- piece_jointe.nombre_signatures (Sprint 4.2). Les migrations existantes ne sont
-- pas touchees ; celle-ci est purement additive (CLAUDE.md section 12), sur le
-- modele de V4 cote Grilles et V2 cote Saisie.
--
-- POURQUOI TOUT EST NULLABLE. Un etat jamais transmis n'a AUCUN statut
-- d'integration -- pas « EN_ATTENTE ». EN_ATTENTE signifie « publie sur
-- rations.etat.valide, la comptabilite n'a pas encore accuse reception » : c'est
-- une etape de l'echange, pas un etat initial. Poser une valeur par defaut
-- ferait croire, sur les 6 etats en cours de saisie, qu'ils attendent quelque
-- chose de la comptabilite.

ALTER TABLE processus_mensuel
    ADD COLUMN statut_integration  VARCHAR(20),
    ADD COLUMN reference_comptable VARCHAR(50),
    ADD COLUMN date_traitement     TIMESTAMP,
    ADD COLUMN motif_integration   VARCHAR(255);

-- Domaine de valeurs, sur le modele des CHECK de V1 (ck_processus_statut).
-- La base refuse une quatrieme valeur meme si un defaut de code l'y menait.
ALTER TABLE processus_mensuel
    ADD CONSTRAINT ck_processus_statut_integration
        CHECK (statut_integration IS NULL
               OR statut_integration IN ('EN_ATTENTE', 'INTEGRE', 'REJETE'));

-- INVARIANT DE COHERENCE. Il n'existe pas de statut d'integration sans
-- transmission : la comptabilite ne peut pas avoir un avis sur un etat qu'elle
-- n'a jamais recu. La reciproque n'est PAS imposee -- un etat transmis dont
-- l'accuse n'est pas encore arrive garde un statut nul l'espace d'un instant,
-- entre la publication et l'ecriture du drapeau.
ALTER TABLE processus_mensuel
    ADD CONSTRAINT ck_processus_integration_apres_transmission
        CHECK (statut_integration IS NULL OR transmis_comptabilite = TRUE);

COMMENT ON COLUMN processus_mensuel.statut_integration IS
    'Suite donnee par la comptabilite a l''etat transmis : EN_ATTENTE (publie, sans accuse), INTEGRE, REJETE. NUL tant que l''etat n''a pas ete transmis. Alimente par l''accuse consomme sur rations.etat.accuse (Sprint 5.2), remonte dans le suivi (US-15).';
COMMENT ON COLUMN processus_mensuel.reference_comptable IS
    'Reference produite par le module de comptabilisation, portee par l''accuse. Ce module ne la fabrique jamais : il ne produit aucune ecriture comptable (CLAUDE.md section 8).';
COMMENT ON COLUMN processus_mensuel.date_traitement IS
    'Horodatage du traitement comptable, tel que declare par l''accuse. Distinct de date_creation, qui est celui du declenchement de l''etat.';
COMMENT ON COLUMN processus_mensuel.motif_integration IS
    'Motif accompagnant un accuse de rejet (contrat d''API section 7.2). Nul autrement.';
