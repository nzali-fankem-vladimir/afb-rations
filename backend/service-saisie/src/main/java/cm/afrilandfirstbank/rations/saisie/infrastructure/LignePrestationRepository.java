package cm.afrilandfirstbank.rations.saisie.infrastructure;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * <b>RG-15</b> — identifiants des <b>autres</b> etats de la meme unite qui
     * portent deja cette combinaison beneficiaire × journee × nature × session.
     *
     * <h2>Pourquoi la journee suffit a designer « la meme periode »</h2>
     *
     * <p>La regle parle des etats « de la meme unite et de la meme periode ».
     * Cette requete ne connait pourtant aucune periode : elle filtre sur
     * {@code code_unite} et {@code date_jour}. Les deux formulations designent le
     * meme ensemble, et c'est la <b>contrainte d'exclusion</b> posee par la
     * Maille 1 sur {@code processus_mensuel} qui le garantit :
     *
     * <ul>
     *   <li>deux etats {@code NORMAL} d'une meme unite ne peuvent plus se
     *       chevaucher — la base les refuse ;</li>
     *   <li>un etat {@code COMPLEMENTAIRE} recopie exactement les bornes de son
     *       origine (Sprint 6bis.1) ;</li>
     *   <li>donc tous les etats d'une unite qui couvrent une journee donnee
     *       partagent la meme periode.</li>
     * </ul>
     *
     * <p><b>Consequence si cette contrainte disparaissait un jour :</b> le
     * controle resterait correct mais deviendrait <i>plus large</i> que RG-15 —
     * il refuserait une combinaison presente dans un etat d'une periode
     * seulement chevauchante. Il ne peut jamais devenir plus etroit, donc jamais
     * laisser passer un double paiement. La degradation est du bon cote.
     *
     * <h2>Pourquoi l'etat courant est exclu, et non la fiche courante</h2>
     *
     * <p>RG-04 tient deja la journee a l'interieur d'un etat, et l'unicite
     * {@code (id_processus, date_jour)} fait qu'un etat n'a qu'une fiche par
     * jour : a l'interieur de l'etat courant, « meme journee » veut dire « meme
     * fiche ». Exclure l'etat plutot que la fiche laisse donc les deux regles
     * exactement complementaires, sans recouvrement ni trou.
     *
     * <h2>Pourquoi des identifiants et non un booleen</h2>
     *
     * <p>Le message de refus doit dire a l'agent <i>ou</i> la prestation figure
     * deja, sans quoi il n'a aucun moyen de verifier. Un booleen aurait impose
     * une seconde requete pour le savoir.
     *
     * @param codeUnite unite portant la charge, recopiee et figee sur la fiche
     *        (Sprint 3.1) : la lecture ne depend pas du service Workflow
     * @param journee jour de la prestation
     * @param idProcessusCourant etat dans lequel la saisie a lieu, exclu
     */
    @Query("""
            select distinct f.idProcessus
              from LignePrestation l, FicheJournaliere f
             where l.idFicheJournaliere = f.id
               and f.codeUnite          = :codeUnite
               and f.dateJour           = :journee
               and f.idProcessus       <> :idProcessusCourant
               and l.idBeneficiaire     = :idBeneficiaire
               and l.nature             = :nature
               and l.session            = :session
            """)
    List<Long> etatsPortantDejaLaPrestation(
            @Param("codeUnite") String codeUnite,
            @Param("journee") LocalDate journee,
            @Param("idProcessusCourant") Long idProcessusCourant,
            @Param("idBeneficiaire") Long idBeneficiaire,
            @Param("nature") NatureEnum nature,
            @Param("session") SessionEnum session);

}
