-- Sprint 5.3 : horodatage de la reservation de transmission (RG-13).
--
-- CE QUE CETTE COLONNE REND POSSIBLE, ET QUI MANQUAIT.
--
-- Le verrou d'unicite de RG-13 fonctionne en deux temps : on RESERVE la
-- transmission (transmis_comptabilite passe a vrai) juste avant de publier sur
-- rations.etat.valide, puis on la CONFIRME apres l'accuse du broker
-- (statut_integration passe a EN_ATTENTE). Entre les deux, la ligne porte donc
-- transmis_comptabilite = TRUE et statut_integration NULL.
--
-- Cet etat intermediaire dure normalement quelques centaines de millisecondes.
-- Mais il PERSISTE quand la publication se termine de facon ambigue -- delai
-- d'accuse depasse, reponse perdue : on ignore alors si l'evenement est parti,
-- et la doctrine du Sprint 5.1 interdit de rejouer ce qui a pu partir. La
-- reservation reste donc posee, deliberement.
--
-- Sans horodatage, ces deux situations ont EXACTEMENT la meme signature en
-- base :
--
--   * un etat publie il y a 200 ms, dont l'accuse comptable va arriver ;
--   * un etat publie il y a trois jours, dont personne ne sait s'il est parti.
--
-- Une supervision batie sur « transmis_comptabilite = TRUE AND
-- statut_integration IS NULL » se noierait donc dans le trafic normal, ou ne se
-- declencherait jamais. L'age de la reservation est ce qui les separe :
--
--   SELECT id, code_unite, mois_paiement, annee_paiement
--     FROM processus_mensuel
--    WHERE statut = 'CLOTURE'
--      AND transmis_comptabilite = TRUE
--      AND statut_integration IS NULL
--      AND date_reservation_transmission < NOW() - INTERVAL '15 minutes';
--
-- POURQUOI UNE SEULE COLONNE, ET NON UN ETAT DE RESERVATION DEDIE. Le couple
-- (transmis_comptabilite, statut_integration) porte deja les trois situations :
-- jamais transmis (FALSE, NULL), reserve non confirme (TRUE, NULL), transmis
-- confirme (TRUE, EN_ATTENTE puis INTEGRE ou REJETE). Il ne manquait que le
-- TEMPS. Ajouter une quatrieme valeur d'etat aurait duplique une information
-- que ces deux colonnes disent deja, avec le risque de les voir diverger.
--
-- Migration purement additive, sur le modele de V4.

ALTER TABLE processus_mensuel
    ADD COLUMN date_reservation_transmission TIMESTAMP;

-- INVARIANT. Une reservation horodatee sans drapeau de transmission n'a aucun
-- sens : les deux sont poses par le meme geste et effaces par le meme geste (la
-- liberation, quand on a la preuve qu'aucun evenement n'est parti). La
-- reciproque n'est pas imposee : les etats transmis AVANT ce sprint portent le
-- drapeau sans horodatage, et cette migration ne les reecrit pas.
ALTER TABLE processus_mensuel
    ADD CONSTRAINT ck_processus_reservation_avec_transmission
        CHECK (date_reservation_transmission IS NULL OR transmis_comptabilite = TRUE);

COMMENT ON COLUMN processus_mensuel.date_reservation_transmission IS
    'Instant ou la transmission a ete reservee, juste avant la publication sur rations.etat.valide (RG-13, Sprint 5.3). Avec statut_integration NULL, une reservation ancienne signale une publication d''issue incertaine, a lever a la main : le module ne rejoue jamais ce qui a pu partir.';
