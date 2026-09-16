-- Sprint 6bis.2 (cloture) : ouverture de la regularisation par etat complementaire.
--
-- RATTRAPAGE_ACTIF avait ete pose a 'false' par la migration V2, et c'etait delibere :
-- la fonctionnalite etait livree avant ses controles. Ouvrir un etat complementaire
-- sans RG-15, c'etait permettre de ressaisir une prestation deja payee dans l'etat
-- d'origine -- un double paiement, sans aucune erreur visible.
--
-- LES DEUX CONDITIONS D'OUVERTURE SONT DESORMAIS REUNIES
--
--   1. Le metier a confirme le besoin de regularisation.
--   2. RG-15 est implementee et verifiee en reel (Sprint 6bis.2) : la ressaisie d'une
--      prestation deja servie est refusee en 409 DOUBLON_INTER_ETATS, et la charge
--      comptable publiee pour un complementaire ne la porte pas.
--
-- POURQUOI UNE MIGRATION, ET PAS SEULEMENT UN UPDATE EN BASE
--
-- Le drapeau reste fait pour changer par UPDATE, sans redeploiement : c'est ainsi
-- qu'on le refermerait en urgence. Mais la VALEUR LIVREE doit suivre la decision
-- metier. Un UPDATE pose a la main sur la base de developpement laisserait la
-- migration V2 decider pour tout environnement neuf : chaque deploiement repartirait
-- ferme, et il faudrait qu'une personne se souvienne de l'ouvrir -- a chaque fois,
-- sur chaque environnement, sans que rien ne le lui rappelle.
--
-- Une migration appliquee ne se retouche pas (lecon du Sprint 6.3 sur V1000) : la V2
-- n'est donc pas modifiee, elle est suivie.
--
-- CE QUI RESTE OUVERT, ET N'EST PAS TRANCHE ICI
--
-- DELAI_REGULARISATION_JOURS garde sa valeur provisoire de 90 jours, a confirmer par
-- le metier (CLAUDE.md section 16).

UPDATE parametre_systeme
   SET valeur = 'true'
 WHERE code = 'RATTRAPAGE_ACTIF';
