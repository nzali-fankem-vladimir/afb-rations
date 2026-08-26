-- Saisie journaliere : fiches, lignes de prestation, beneficiaires (CLAUDE.md section 4).
--
-- id_processus (fiche_journaliere) reference processus_mensuel, qui vit dans
-- rations_workflow : pas de cle etrangere inter-base, identifiant simple.
--
-- Aucune contrainte ici pour RG-04 (doublon beneficiaire/jour/nature/session) :
-- hors perimetre du Sprint 0.5 (section 5 du guide), a implementer avec le
-- service applicatif.

CREATE TABLE beneficiaires (
    id                 BIGSERIAL     PRIMARY KEY,
    nom                VARCHAR(100)  NOT NULL,
    prenom             VARCHAR(100)  NOT NULL,
    num_compte_courant VARCHAR(20)   NOT NULL,
    code_agence        VARCHAR(5)    NOT NULL,
    date_creation      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  beneficiaires             IS 'Agent servi. Cree au fil des saisies, pas d''enrolement.';
COMMENT ON COLUMN beneficiaires.code_agence IS 'Agence de domiciliation du compte (ligne de credit). Distinct de code_unite.';

-- date_jour est un jour calendaire, pas un instant : DATE plutot que TIMESTAMP,
-- par exception a la convention generale de CLAUDE.md section 4.
CREATE TABLE fiche_journaliere (
    id            BIGSERIAL     PRIMARY KEY,
    id_processus  BIGINT        NOT NULL,
    date_jour     DATE          NOT NULL,
    statut        VARCHAR(20)   NOT NULL,
    date_creation TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_fiche_journaliere_processus_jour UNIQUE (id_processus, date_jour),
    CONSTRAINT ck_fiche_journaliere_statut CHECK (statut IN ('EN_SAISIE', 'ENREGISTREE'))
);

COMMENT ON COLUMN fiche_journaliere.id_processus IS 'Reference processus_mensuel (base rations_workflow). Identifiant simple, sans FK inter-base.';

CREATE TABLE ligne_prestation (
    id                    BIGSERIAL     PRIMARY KEY,
    id_fiche_journaliere  BIGINT        NOT NULL REFERENCES fiche_journaliere (id),
    id_beneficiaire       BIGINT        NOT NULL REFERENCES beneficiaires (id),
    nature                VARCHAR(20)   NOT NULL,
    session               VARCHAR(10)   NOT NULL,
    montant_applique      INTEGER       NOT NULL,
    date_creation         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_ligne_prestation_nature  CHECK (nature IN ('RATION', 'TRANSPORT')),
    CONSTRAINT ck_ligne_prestation_session CHECK (session IN ('JOUR', 'SOIR'))
);

COMMENT ON COLUMN ligne_prestation.montant_applique IS 'Repris de la grille active a la saisie (RG-03). Fige, jamais recalcule.';

CREATE INDEX idx_ligne_prestation_fiche       ON ligne_prestation (id_fiche_journaliere);
CREATE INDEX idx_ligne_prestation_beneficiaire ON ligne_prestation (id_beneficiaire);
