-- Parametres systeme (CLAUDE.md sections 6 et 7 ; docs/dispositifs_provisoires.md).
--
-- RATTRAPAGE_ACTIF et DELAI_REGULARISATION_JOURS sont deux dispositifs
-- provisoires : le premier ferme la fonctionnalite d'etat complementaire tant
-- que le metier ne l'a pas confirmee, le second porte une valeur explicitement
-- marquee provisoire dans son libelle (repere par le point d'attente M-01/M-02).

INSERT INTO parametre_systeme (code, libelle, valeur, actif) VALUES
    ('SEUIL_AIGUILLAGE_DR',
     'Seuil, en FCFA, au-dela duquel un etat valide par le DA est aiguille vers le DR (RG-08)',
     '100000',
     TRUE),
    ('RATTRAPAGE_ACTIF',
     'Ouverture d''un etat complementaire sur une periode close',
     'false',
     TRUE),
    ('DELAI_REGULARISATION_JOURS',
     'Delai en jours pendant lequel une periode close reste regularisable (VALEUR PROVISOIRE)',
     '90',
     TRUE);
