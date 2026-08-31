package cm.afrilandfirstbank.rations.saisie.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cm.afrilandfirstbank.rations.saisie.domaine.LignePrestation;
import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;

/**
 * Accès aux lignes de prestation (base {@code rations_saisie}).
 */
@Repository
public interface LignePrestationRepository extends JpaRepository<LignePrestation, Long> {

    /** Toutes les lignes d'une fiche journalière. Tri laissé à l'appelant. */
    List<LignePrestation> findByIdFicheJournaliere(Long idFicheJournaliere);

    /**
     * Vrai s'il existe déjà une ligne pour cette combinaison
     * <b>bénéficiaire × fiche × nature × session</b>.
     *
     * <p>Portera <b>RG-04</b> (unicité journalière) au sous-sprint 3.2 : un même
     * bénéficiaire ne peut pas figurer deux fois sur la même journée pour la même
     * nature et la même session. Le contrôle porte sur la <b>combinaison
     * complète</b>, jamais sur le seul bénéficiaire : le même agent peut être
     * servi le même jour en RATION <i>et</i> en TRANSPORT, en JOUR <i>et</i> en
     * SOIR. Les tests 7 et 8 du sous-sprint le vérifient explicitement.
     */
    boolean existsByIdFicheJournaliereAndIdBeneficiaireAndNatureAndSession(
            Long idFicheJournaliere, Long idBeneficiaire, NatureEnum nature, SessionEnum session);

    /**
     * Même contrôle RG-04, en excluant une ligne précise. Sert la modification
     * (Sprint 3.3) : sans l'exclusion, une ligne dont la nature et la session ne
     * changent pas se trouverait doublon d'elle-même.
     */
    boolean existsByIdFicheJournaliereAndIdBeneficiaireAndNatureAndSessionAndIdNot(
            Long idFicheJournaliere, Long idBeneficiaire, NatureEnum nature, SessionEnum session,
            Long idExclu);

}
