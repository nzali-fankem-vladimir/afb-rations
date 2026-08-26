-- Processus mensuel, etapes de validation, piece jointe, parametres systeme
-- (CLAUDE.md section 4).
--
-- id_acteur (etape_workflow) et id_createur/id_validateur ailleurs referencent
-- utilisateurs, qui vit dans rations_identite : identifiants simples, sans FK
-- inter-base. id_processus et id_fiche/etape restent dans cette base : FK reelles.

CREATE TABLE processus_mensuel (
    id                    BIGSERIAL     PRIMARY KEY,
    mois_paiement         INTEGER       NOT NULL,
    annee_paiement        INTEGER       NOT NULL,
    code_unite            VARCHAR(5)    NOT NULL,
    type_processus        VARCHAR(20)   NOT NULL,
    id_processus_origine  BIGINT        REFERENCES processus_mensuel (id),
    motif_ouverture       VARCHAR(255),
    montant_total         INTEGER       NOT NULL DEFAULT 0,
    statut                VARCHAR(20)   NOT NULL,
    transmis_comptabilite BOOLEAN       NOT NULL DEFAULT FALSE,
    date_creation         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_processus_type   CHECK (type_processus IN ('NORMAL', 'COMPLEMENTAIRE')),
    CONSTRAINT ck_processus_statut CHECK (statut IN (
        'EN_COURS_SAISIE', 'SOUMIS', 'EN_ATTENTE_DA', 'EN_ATTENTE_DR', 'RETOURNE', 'CLOTURE'
    ))
);

COMMENT ON COLUMN processus_mensuel.code_unite           IS 'Unite qui supporte la charge (ligne de debit). Distinct du code agence.';
COMMENT ON COLUMN processus_mensuel.id_processus_origine IS 'Renseigne uniquement pour un COMPLEMENTAIRE : reference l''etat clos jamais rouvert.';

-- RG-15 : un seul processus NORMAL par unite et periode, plusieurs COMPLEMENTAIRE
-- autorises. Une contrainte simple sur (code_unite, mois, annee) interdirait
-- tout etat complementaire.
CREATE UNIQUE INDEX ux_processus_normal_par_periode
    ON processus_mensuel (code_unite, mois_paiement, annee_paiement)
    WHERE type_processus = 'NORMAL';

CREATE TABLE etape_workflow (
    id                   BIGSERIAL     PRIMARY KEY,
    id_processus         BIGINT        NOT NULL REFERENCES processus_mensuel (id),
    id_acteur            BIGINT        NOT NULL,
    ordre_etape          INTEGER       NOT NULL,
    nom_etape            VARCHAR(30)   NOT NULL,
    statut_etape         VARCHAR(20)   NOT NULL,
    motif_retour         VARCHAR(255),
    signature_numerique  VARCHAR(255),
    date_creation        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_etape_nom    CHECK (nom_etape IN ('SOUMISSION_AGENT', 'VALIDATION_DA', 'VALIDATION_DR')),
    CONSTRAINT ck_etape_statut CHECK (statut_etape IN ('EN_ATTENTE', 'VALIDEE', 'RETOURNEE'))
);

COMMENT ON COLUMN etape_workflow.id_acteur IS 'Reference utilisateurs (base rations_identite). Identifiant simple.';

CREATE INDEX idx_etape_workflow_processus ON etape_workflow (id_processus);

-- Un seul document par processus, enrichi de signatures a chaque validation
-- (RG-09). Le contenu du PDF (iText) vit sur un stockage de fichiers, pas en
-- base : chemin_fichier pointe vers ce stockage.
CREATE TABLE piece_jointe (
    id                           BIGSERIAL     PRIMARY KEY,
    id_processus                 BIGINT        NOT NULL UNIQUE REFERENCES processus_mensuel (id),
    chemin_fichier               VARCHAR(500)  NOT NULL,
    type_mime                    VARCHAR(100)  NOT NULL DEFAULT 'application/pdf',
    date_creation                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_derniere_modification   TIMESTAMP
);

CREATE TABLE parametre_systeme (
    id      BIGSERIAL     PRIMARY KEY,
    code    VARCHAR(50)   NOT NULL,
    libelle VARCHAR(255)  NOT NULL,
    valeur  VARCHAR(255)  NOT NULL,
    actif   BOOLEAN       NOT NULL DEFAULT TRUE,

    CONSTRAINT uk_parametre_systeme_code UNIQUE (code)
);

COMMENT ON COLUMN parametre_systeme.valeur IS 'Stockee en texte : le type reel (entier, booleen) est interprete cote application.';
