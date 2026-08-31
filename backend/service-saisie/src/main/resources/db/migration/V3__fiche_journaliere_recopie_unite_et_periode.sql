-- Sprint 3.3 : recopie figee de l'unite et de la periode sur la fiche journaliere.
--
-- DECIDE AU SPRINT 3.1, PREVU AU 3.2, NON FAIT : le sous-sprint 3.2 a porte RG-03
-- et RG-04, qui n'ecrivent pas de fiche. La premiere ecriture reelle de fiche est
-- l'ouverture du Sprint 3.3 : la migration arrive avec elle.
--   docs/rattachement-processus.md section 5
--   docs/resumes-sprints/sprint-3.2-valorisation-et-controle-des-doublons.md
--
-- POURQUOI CES TROIS COLONNES. RG-15 (Sprint 6bis) interdit qu'une ligne
-- reproduise une combinaison (beneficiaire, journee, nature, session) deja
-- presente dans UN AUTRE ETAT de la meme unite et de la meme periode. Or
-- processus_mensuel vit dans la base rations_workflow : sans ces copies, le
-- service Saisie ne peut pas regrouper ses fiches par unite et par periode, il ne
-- connait que des id_processus opaques. Le controle exigerait alors un appel
-- reseau vers Workflow pour chaque processus candidat a la comparaison, sur le
-- chemin d'ecriture d'une ligne.
--
-- Elles servent des maintenant a la verification de portee d'acces : le code
-- unite d'un dossier est ce qui permet de savoir si un agent a le droit d'y
-- ecrire (RG-12).
--
-- POURQUOI LA COPIE NE PERIME PAS. Ces trois valeurs sont IMMUABLES pour un
-- processus donne : l'index ux_processus_normal_par_periode impose un seul
-- processus NORMAL par (code_unite, mois_paiement, annee_paiement) — le triplet
-- fait partie de l'identite du processus, il ne peut pas changer sous lui. C'est
-- le motif « libelle recopie et fige » du Sprint 2.2, avec une garantie plus
-- forte.
--
-- LE STATUT N'EST PAS COPIE. Il est mutable, et c'est exactement pour cela qu'il
-- est redemande a Workflow a chaque ecriture, sans jamais etre mis en cache
-- (docs/rattachement-processus.md section 4).
--
-- NULLABLES A DESSEIN, comme libelle_createur au Sprint 2.2 : ce sont des copies
-- de confort, id_processus reste la donnee de reference. Les fiches creees avant
-- cette migration en sont depourvues. Rendre code_unite NOT NULL ferait dependre
-- l'ecriture d'une donnee resolue par un service distant.
--
-- Migration purement additive et versionnee (CLAUDE.md section 12) : ni
-- recreation de table, ni auto-DDL Hibernate.

ALTER TABLE fiche_journaliere
    ADD COLUMN code_unite     VARCHAR(5),
    ADD COLUMN mois_paiement  INTEGER,
    ADD COLUMN annee_paiement INTEGER;

COMMENT ON COLUMN fiche_journaliere.code_unite     IS 'Unite supportant la charge, recopiee du processus mensuel a l''ouverture et figee. Ligne de debit. Distinct de beneficiaires.code_agence.';
COMMENT ON COLUMN fiche_journaliere.mois_paiement  IS 'Mois du processus mensuel, recopie et fige. Sert au regroupement local exige par RG-15.';
COMMENT ON COLUMN fiche_journaliere.annee_paiement IS 'Annee du processus mensuel, recopiee et figee. Sert au regroupement local exige par RG-15.';

-- Chemin d'acces de RG-15 au Sprint 6bis : toutes les fiches d'une unite pour une
-- periode, tous processus confondus. Cree ici parce que la colonne nait ici ; il
-- ne coute rien tant que la requete n'existe pas.
CREATE INDEX idx_fiche_journaliere_unite_periode
    ON fiche_journaliere (code_unite, annee_paiement, mois_paiement);
