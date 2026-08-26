-- Grilles tarifaires (CLAUDE.md section 4). Cycle de vie BROUILLON -> EN_ATTENTE_DRH
-- -> ACTIVE ou REJETEE (RG-14).
--
-- id_createur (ARH) et id_validateur (DRH) referencent utilisateurs, qui vit dans
-- rations_identite : pas de cle etrangere inter-base, identifiants simples.

CREATE TABLE grille_tarifaire (
    id                BIGSERIAL     PRIMARY KEY,
    nature            VARCHAR(20)   NOT NULL,
    session           VARCHAR(10)   NOT NULL,
    montant_fcfa      INTEGER       NOT NULL,
    statut_validation VARCHAR(20)   NOT NULL,
    id_createur       BIGINT        NOT NULL,
    id_validateur     BIGINT,
    date_debut        DATE          NOT NULL,
    date_fin          DATE,
    date_creation     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_grille_nature  CHECK (nature IN ('RATION', 'TRANSPORT')),
    CONSTRAINT ck_grille_session CHECK (session IN ('JOUR', 'SOIR')),
    CONSTRAINT ck_grille_statut  CHECK (statut_validation IN ('BROUILLON', 'EN_ATTENTE_DRH', 'ACTIVE', 'REJETEE'))
);

COMMENT ON COLUMN grille_tarifaire.id_createur   IS 'ARH, reference utilisateurs (base rations_identite). Identifiant simple.';
COMMENT ON COLUMN grille_tarifaire.id_validateur IS 'DRH, reference utilisateurs (base rations_identite). Identifiant simple.';
COMMENT ON COLUMN grille_tarifaire.montant_fcfa  IS 'Entier, FCFA, sans decimale.';

-- RG-14 : une seule grille ACTIVE, sans date de fin, par couple (nature, session).
-- Une contrainte d'unicite simple sur (nature, session) interdirait tout historique.
CREATE UNIQUE INDEX ux_grille_active_par_couple
    ON grille_tarifaire (nature, session)
    WHERE statut_validation = 'ACTIVE' AND date_fin IS NULL;
