-- Pre-provisionnement des profils correspondant aux comptes du realm afb-rations-dev.
--
-- Le role et le code unite sont des donnees du module, pas de l'annuaire : c'est
-- ici qu'ils sont decides, pas dans Keycloak (CLAUDE.md section 10).
-- sub_keycloak reste vide : la liaison s'etablit a la premiere connexion de chacun.
--
-- Codes guichets du referentiel : 00001 Siege Yaounde, 00002 Douala Bonanjo.

INSERT INTO utilisateurs (login, matricule, nom, prenom, email, role, code_unite) VALUES
    ('jean_mbarga',     '1847', 'MBARGA',   'Jean',   'jean_mbarga@afrilandfirstbank.com',     'AGENT_UNITE',         '00002'),
    ('paul_essama',     '2093', 'ESSAMA',   'Paul',   'paul_essama@afrilandfirstbank.com',     'CHEF_UNITE_DA',       '00002'),
    ('sylvie_atangana', '1562', 'ATANGANA', 'Sylvie', 'sylvie_atangana@afrilandfirstbank.com', 'DIRECTEUR_RESEAU_DR', '00001'),
    ('claire_nkolo',    '2201', 'NKOLO',    'Claire', 'claire_nkolo@afrilandfirstbank.com',    'ARH',                 '00001'),
    ('agnes_tchinda',   '1734', 'TCHINDA',  'Agnes',  'agnes_tchinda@afrilandfirstbank.com',   'DRH',                 '00001'),
    ('martin_fouda',    '2456', 'FOUDA',    'Martin', 'martin_fouda@afrilandfirstbank.com',    'ADMIN',               '00001');
