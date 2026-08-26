-- Journal d'audit immuable (CLAUDE.md sections 3 et 4).
--
-- service_emetteur n'existait pas dans le dictionnaire d'origine, qui supposait
-- un journal reparti par service. Sans lui, une trace centralisee ne dit plus
-- d'ou elle vient.
--
-- id_utilisateur reference utilisateurs (base rations_identite) : identifiant
-- simple, pas de cle etrangere inter-base.
--
-- Aucune methode de modification ni de suppression n'est exposee au niveau
-- applicatif (repository, controleur) : l'immuabilite est une propriete de
-- l'architecture, pas de ce schema seul (CLAUDE.md section 3).

CREATE TABLE audit_log (
    id               BIGSERIAL     PRIMARY KEY,
    id_utilisateur   BIGINT        NOT NULL,
    service_emetteur VARCHAR(30)   NOT NULL,
    action           VARCHAR(100)  NOT NULL,
    entite_cible     VARCHAR(50)   NOT NULL,
    id_entite        BIGINT        NOT NULL,
    date_action      TIMESTAMP     NOT NULL,
    adresse_ip       VARCHAR(45),
    detail_json      JSONB
);

COMMENT ON TABLE  audit_log                  IS 'Journal immuable, alimente exclusivement par le topic rations.audit.evenement.';
COMMENT ON COLUMN audit_log.id_utilisateur   IS 'Reference utilisateurs (base rations_identite). Identifiant simple.';
COMMENT ON COLUMN audit_log.service_emetteur IS 'Service metier a l''origine de l''evenement (identite, saisie, grilles, workflow, reporting, transmission).';
COMMENT ON COLUMN audit_log.detail_json      IS 'Delta avant/apres de l''action.';

-- Cas d'usage principal (CT-40) : consultation du journal d'un processus.
CREATE INDEX idx_audit_log_utilisateur      ON audit_log (id_utilisateur);
CREATE INDEX idx_audit_log_entite           ON audit_log (entite_cible, id_entite);
CREATE INDEX idx_audit_log_date_action      ON audit_log (date_action);
