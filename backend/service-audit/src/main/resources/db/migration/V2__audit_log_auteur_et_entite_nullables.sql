-- Sprint 6.3 : id_utilisateur et id_entite deviennent nullables.
--
-- POURQUOI. La V1 les declarait NOT NULL, en contradiction directe avec le
-- contrat de fil publie par les six services metier. EvenementAudit
-- (rations-audit-commun) documente les deux comme nullables, et pour un motif
-- de fond :
--
--   « idUtilisateur : nul quand l'auteur n'a pas de profil local. Cas reel et
--     important : un refus d'acces (CT-04) oppose a un jeton valide sans profil
--     ouvert dans le module. La trace doit exister malgre l'absence
--     d'identifiant local. »
--
-- Constate sur le terrain au Sprint 6.3 : sur les 30 points de publication du
-- backend, 21 passent un null litteral en idUtilisateur et 7 en idEntite. Un
-- message ACCES_REFUSE reellement present sur le topic a ete oppose au schema :
--
--   ERROR: null value in column "id_utilisateur" of relation "audit_log"
--          violates not-null constraint
--
-- Autrement dit, la trace la plus importante a conserver — quelqu'un a tente
-- une action qu'il n'avait pas le droit de faire — etait la premiere que le
-- schema aurait refusee. C'est le schema qui a tort, pas le code.
--
-- VALEUR SENTINELLE ECARTEE. Remplir id_utilisateur par -1 « pour les cas sans
-- profil » fabriquerait un utilisateur qui n'existe pas, qu'une jointure
-- ulterieure prendrait pour reel. Un nul dit ce qu'il est : on ne sait pas.
--
-- SANS EFFET DE BORD. Les trois index de la V1 sont des B-tree simples, non
-- uniques et non partiels ; PostgreSQL y indexe nativement les valeurs nulles.
-- Aucune reconstruction, aucune requete de recherche degradee. La table est
-- vide au moment de cette migration (0 ligne, verifie).
--
-- LIMITE CONNUE, NON TRAITEE ICI. Une recherche « toutes les actions de cette
-- personne » filtrant sur id_utilisateur ne verra pas les 21 traces qui le
-- laissent nul. Le login y figure pour partie dans detail_json, interrogeable
-- mais par un chemin que personne n'emprunte spontanement. Un champ dedie et
-- indexable a ete envisage puis reporte au sprint de construction du service
-- Audit, qui concevra l'entite et le schema de lecture d'un seul tenant :
-- decider de la forme de l'ecriture avant d'avoir concu la lecture garantit une
-- troisieme migration. Voir docs/points-en-attente.md, point A-01.

ALTER TABLE audit_log ALTER COLUMN id_utilisateur DROP NOT NULL;
ALTER TABLE audit_log ALTER COLUMN id_entite      DROP NOT NULL;

COMMENT ON COLUMN audit_log.id_utilisateur IS
    'Reference utilisateurs (base rations_identite). Identifiant simple. NUL quand l''auteur n''a pas de profil local dans le module (refus d''acces CT-04) ou quand le service emetteur ne le resout pas.';
COMMENT ON COLUMN audit_log.id_entite IS
    'Identifiant de l''entite visee. NUL quand l''action ne vise aucune entite precise (refus d''acces, message entrant illisible).';
