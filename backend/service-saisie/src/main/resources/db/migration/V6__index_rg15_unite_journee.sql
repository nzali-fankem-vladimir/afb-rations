-- Sprint 6bis.2 : le chemin d'acces de RG-15.
--
-- RG-15 interdit qu'une ligne reproduise une combinaison (beneficiaire, journee,
-- nature, session) deja presente dans un AUTRE etat de la meme unite et de la
-- meme periode. La requete qui l'applique part donc de deux valeurs connues --
-- le code unite et la journee -- et remonte aux lignes par la fiche.
--
-- POURQUOI (code_unite, date_jour) ET NON (code_unite, date_debut, date_fin)
--
-- L'index de periode pose par la V5 repondait a la forme prevue par le guide :
-- « trouver les etats de la meme periode, puis y chercher la journee ». La
-- contrainte d'exclusion de la Maille 1 rend ce detour inutile.
--
-- Depuis cette contrainte, deux etats NORMAL d'une meme unite ne peuvent plus se
-- chevaucher, et un etat COMPLEMENTAIRE recopie exactement les bornes de son
-- origine. « Les etats de l'unite qui couvrent cette journee » et « les etats de
-- l'unite sur cette periode » designent donc le meme ensemble -- a ceci pres que
-- le premier se demande sans connaitre la periode.
--
-- Et il est le plus sur des deux. Une fiche peut porter une date_jour situee
-- HORS des bornes de son propre etat : c'est le defaut que le controle de
-- completude refuse a la soumission (LIGNE_HORS_PERIODE, Sprint 4.2), donc il
-- existe tant que l'etat n'est pas soumis, et la base de developpement en porte
-- un exemplaire. Interroge par periode, RG-15 ne verrait pas une telle ligne ;
-- interroge par journee, il la voit.
--
-- L'INDEX DE PERIODE DE LA V5 EST CONSERVE : il sert la recherche de lignes du
-- Reporting (RechercheLignesRepository), qui filtre bien sur un intervalle.
--
-- PAS UN INDEX UNIQUE, et c'est une difference de fond avec RG-04.
-- ux_ligne_par_fiche_beneficiaire_nature_session (V4) peut etre unique parce que
-- la combinaison qu'il garde vit dans une seule table. RG-15 porte sur une
-- jointure fiche x ligne : aucun index ne peut la faire respecter. Le controle
-- reste applicatif, et sa fenetre de concurrence est assumee -- deux agents de
-- la meme unite saisissant la meme prestation au meme instant dans deux etats
-- differents. Le meme compromis que RG-04 avant la V4, sur un cas bien plus
-- rare. Consigne au sous-sprint 6bis.2.
--
-- La V4 annoncait que RG-15 « porte sur une jointure a trois tables » : c'etait
-- vrai de la forme inter-services alors prevue. Une migration appliquee ne se
-- retouche pas (lecon du Sprint 6.3 sur V1000) ; la correction est ici.

CREATE INDEX idx_fiche_journaliere_unite_journee
    ON fiche_journaliere (code_unite, date_jour);

COMMENT ON INDEX idx_fiche_journaliere_unite_journee IS
    'RG-15 : chemin d''acces aux fiches d''une unite pour une journee donnee, tous etats confondus. La contrainte d''exclusion de la Maille 1 garantit que cet ensemble est celui des etats de la periode.';
