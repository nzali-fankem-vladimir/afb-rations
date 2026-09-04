-- Pre-provisionnement des profils correspondant aux comptes du realm afb-rations-dev.
--
-- Le role et le code unite sont des donnees du module, pas de l'annuaire : c'est
-- ici qu'ils sont decides, pas dans Keycloak (CLAUDE.md section 10).
-- sub_keycloak reste vide : la liaison s'etablit a la premiere connexion de chacun.
--
-- Codes guichets du referentiel : 00001 Siege Yaounde, 00002 Douala Bonanjo.
--
-- ============================================================================
-- NE JAMAIS AJOUTER thomas_ndzana A CETTE LISTE.
--
-- C'est le compte de controle du refus 403 : present a l'annuaire, sans profil
-- local, il est le seul cas qui prouve qu'un jeton Keycloak valide mais non
-- habilite dans le module est bien refuse (decision Sprint 0.4).
--
-- Lui ouvrir un profil ne casse rien de visible : le build passe, les tests
-- passent -- et la checklist d'environnement rend 200 la ou elle attend 403,
-- sur un test de securite, sans un mot d'explication.
--
-- C'est deja arrive au Sprint 6.3 avec pierre_belinga, alors compte de
-- controle : un profil lui a ete ouvert en base pour eprouver la trace
-- LIAISON_COMPTE_KEYCLOAK, et le cas de test a disparu en silence. Il est
-- depuis un compte habilite ordinaire, et ne doit pas revenir ici non plus :
-- son profil vit en base, l'ajouter a la migration ferait diverger un
-- environnement neuf d'un environnement existant.
--
-- Besoin d'un profil pre-provisionne non lie, pour eprouver une liaison ?
-- Creer un compte jetable. Jamais le compte de controle.
--
-- Garde au build : CompteDeControleDuRefusTest.
-- Voir docs/decisions/2026-09-04-compte-de-controle-du-refus-403.md
-- ============================================================================

INSERT INTO utilisateurs (login, matricule, nom, prenom, email, role, code_unite) VALUES
    ('jean_mbarga',     '1847', 'MBARGA',   'Jean',   'jean_mbarga@afrilandfirstbank.com',     'AGENT_UNITE',         '00002'),
    ('paul_essama',     '2093', 'ESSAMA',   'Paul',   'paul_essama@afrilandfirstbank.com',     'CHEF_UNITE_DA',       '00002'),
    ('sylvie_atangana', '1562', 'ATANGANA', 'Sylvie', 'sylvie_atangana@afrilandfirstbank.com', 'DIRECTEUR_RESEAU_DR', '00001'),
    ('claire_nkolo',    '2201', 'NKOLO',    'Claire', 'claire_nkolo@afrilandfirstbank.com',    'ARH',                 '00001'),
    ('agnes_tchinda',   '1734', 'TCHINDA',  'Agnes',  'agnes_tchinda@afrilandfirstbank.com',   'DRH',                 '00001'),
    ('martin_fouda',    '2456', 'FOUDA',    'Martin', 'martin_fouda@afrilandfirstbank.com',    'ADMIN',               '00001');
