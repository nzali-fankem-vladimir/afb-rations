package cm.afrilandfirstbank.rations.saisie.infrastructure;

import java.util.Collection;
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
     * Toutes les lignes d'un lot de fiches, en <b>une seule requête</b>. Sert la
     * consolidation mensuelle (RG-06, Sprint 3.4), qui agrège une trentaine de
     * journées : la méthode précédente appelée en boucle produirait autant de
     * requêtes que de jours.
     *
     * <p>Le critère est un {@code IN} sur {@code id_fiche_journaliere}, jamais une
     * jointure vers {@code fiche_journaliere}. C'est délibéré et c'est une
     * garantie de justesse du montant total : une jointure mal posée peut
     * multiplier les lignes (produit cartésien) et donc <b>compter deux fois</b>
     * un montant. Ici, chaque ligne de la table apparaît au plus une fois dans le
     * résultat, quelle que soit la forme du lot d'identifiants.
     *
     * <p>Tri laissé à l'appelant, comme les autres méthodes de ce repository.
     */
    List<LignePrestation> findByIdFicheJournaliereIn(Collection<Long> idsFichesJournalieres);

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
