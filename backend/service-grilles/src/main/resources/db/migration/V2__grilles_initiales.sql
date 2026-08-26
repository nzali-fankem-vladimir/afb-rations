-- Grilles tarifaires initiales, une par combinaison nature/session (CLAUDE.md
-- section 4). Montants plausibles en FCFA, entiers, sans decimale.
--
-- id_createur et id_validateur referencent les utilisateurs de test du service
-- Identite (V1000) : claire_nkolo (ARH, id 4) a cree, agnes_tchinda (DRH, id 5)
-- a valide. Hypothese : insertion sequentielle du V1000, ids 1 a 6 dans l'ordre
-- jean_mbarga, paul_essama, sylvie_atangana, claire_nkolo, agnes_tchinda,
-- martin_fouda. A verifier apres migration si le service Identite est modifie.

INSERT INTO grille_tarifaire (nature, session, montant_fcfa, statut_validation, id_createur, id_validateur, date_debut, date_fin) VALUES
    ('RATION',    'JOUR', 1500, 'ACTIVE', 4, 5, date_trunc('month', CURRENT_DATE), NULL),
    ('RATION',    'SOIR', 2000, 'ACTIVE', 4, 5, date_trunc('month', CURRENT_DATE), NULL),
    ('TRANSPORT', 'JOUR', 1000, 'ACTIVE', 4, 5, date_trunc('month', CURRENT_DATE), NULL),
    ('TRANSPORT', 'SOIR', 1500, 'ACTIVE', 4, 5, date_trunc('month', CURRENT_DATE), NULL);
