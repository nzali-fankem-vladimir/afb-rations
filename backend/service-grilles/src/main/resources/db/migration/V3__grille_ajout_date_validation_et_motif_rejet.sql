-- Sprint 2.1 : le dictionnaire de donnees du domaine Grille tarifaire porte deux
-- colonnes que la creation initiale (V1, Sprint 0.5) n'avait pas materialisees :
--   - date_validation : horodatage de la decision DRH (validation ou rejet),
--                       renseigne par le service de validation au Sprint 2.3 ;
--   - motif_rejet     : motif obligatoire au rejet DRH (RG-10, RG-14), egalement
--                       renseigne au Sprint 2.3.
--
-- Migration purement additive et versionnee (CLAUDE.md section 2) : ni recreation
-- de table, ni auto-DDL Hibernate. Les deux colonnes restent nulles pour les
-- quatre grilles ACTIVE de reference, qui n'ont connu ni rejet ni validation
-- trace dans le module.

ALTER TABLE grille_tarifaire
    ADD COLUMN date_validation TIMESTAMP,
    ADD COLUMN motif_rejet     VARCHAR(255);

COMMENT ON COLUMN grille_tarifaire.date_validation IS 'Horodatage de la decision DRH (ACTIVE ou REJETEE). Nul tant que la grille n''a pas ete soumise puis tranchee.';
COMMENT ON COLUMN grille_tarifaire.motif_rejet     IS 'Motif obligatoire au rejet DRH (RG-10). Nul hors statut REJETEE.';
