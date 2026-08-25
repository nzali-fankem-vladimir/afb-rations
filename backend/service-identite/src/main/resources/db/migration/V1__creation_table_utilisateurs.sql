-- Projection locale des comptes annuaire (CLAUDE.md sections 4 et 10).
--
-- Aucune colonne de mot de passe : l'authentification est deleguee a Keycloak,
-- le module ne fait que valider les jetons.
--
-- sub_keycloak est nullable : l'administrateur ouvre le profil a partir du login
-- annuaire, la liaison au compte Keycloak s'etablit a la premiere connexion.

CREATE TABLE utilisateurs (
    id                 BIGSERIAL     PRIMARY KEY,
    login              VARCHAR(100)  NOT NULL,
    sub_keycloak       VARCHAR(100),
    matricule          VARCHAR(20),
    nom                VARCHAR(100)  NOT NULL,
    prenom             VARCHAR(100)  NOT NULL,
    email              VARCHAR(150),
    role               VARCHAR(30)   NOT NULL,
    code_unite         VARCHAR(5)    NOT NULL,
    actif              BOOLEAN       NOT NULL DEFAULT TRUE,
    date_creation      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_dernier_acces TIMESTAMP,

    CONSTRAINT uk_utilisateurs_login        UNIQUE (login),
    CONSTRAINT uk_utilisateurs_sub_keycloak UNIQUE (sub_keycloak),
    CONSTRAINT ck_utilisateurs_role CHECK (role IN (
        'AGENT_UNITE', 'CHEF_UNITE_DA', 'DIRECTEUR_RESEAU_DR', 'ARH', 'DRH', 'ADMIN'
    ))
);

-- Cle de resolution a chaque requete authentifiee.
CREATE INDEX idx_utilisateurs_sub_keycloak ON utilisateurs (sub_keycloak);

COMMENT ON TABLE  utilisateurs                    IS 'Projection locale du compte annuaire. Aucun mot de passe.';
COMMENT ON COLUMN utilisateurs.login              IS 'Identifiant annuaire au format prenom_nom.';
COMMENT ON COLUMN utilisateurs.sub_keycloak       IS 'Identifiant technique Keycloak, renseigne a la premiere connexion.';
COMMENT ON COLUMN utilisateurs.code_unite         IS 'Unite supportant la charge. Distinct du code agence du beneficiaire.';
