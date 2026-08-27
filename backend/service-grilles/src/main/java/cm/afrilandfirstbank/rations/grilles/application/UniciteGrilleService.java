package cm.afrilandfirstbank.rations.grilles.application;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.grilles.domaine.GrilleTarifaire;
import cm.afrilandfirstbank.rations.grilles.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.ConflitGrilleException;
import cm.afrilandfirstbank.rations.grilles.infrastructure.GrilleTarifaireRepository;

/**
 * Controle d'unicite des grilles tarifaires — cœur de RG-14.
 *
 * <p>RG-14 pose un invariant : <b>une seule grille ACTIVE par couple (nature,
 * session)</b>. Cet invariant n'est pas verifiable a l'instant de la creation,
 * puisqu'une grille naît {@code EN_ATTENTE_DRH} et ne devient active que bien
 * plus tard. Ce service verifie donc ce qui en tient lieu : <b>qu'aucune
 * proposition acceptee aujourd'hui ne puisse produire deux grilles actives
 * demain</b>.
 *
 * <h2>Les deux facons de casser l'invariant</h2>
 *
 * <p><b>1. Deux propositions concurrentes sur le meme couple.</b> Si deux grilles
 * attendent la DRH pour RATION / JOUR et qu'elle valide les deux, la seconde
 * validation trouvera la premiere deja active. Laquelle ferme laquelle ? La
 * question n'a pas de bonne reponse, et la base tranchera par une violation
 * d'index. On refuse donc la seconde proposition des sa creation.
 *
 * <p><b>2. Une proposition qui remonte le temps.</b> C'est le cas subtil. Au
 * Sprint 2.3, valider une grille ferme la precedente en posant sa date de fin a
 * la <i>veille</i> de la date de debut de la remplacante. Cette mecanique n'a de
 * sens que si la remplacante commence APRES. Sinon :
 *
 * <pre>
 *   en vigueur  : RATION/JOUR  1500 FCFA  debut 01/08  fin (aucune)
 *   proposition : RATION/JOUR  1800 FCFA  debut 01/07
 *
 *   a la validation, fermeture de l'ancienne a la veille du 01/07 :
 *   en vigueur  : RATION/JOUR  1500 FCFA  debut 01/08  fin 30/06   <-- periode a l'envers
 * </pre>
 *
 * L'ancienne grille devient un intervalle vide. Les lignes de prestation deja
 * saisies en aout, payees 1500 FCFA, ne se rattachent plus a aucune grille
 * valide a leur date : le montant paye devient injustifiable. Ce n'est pas une
 * incoherence d'affichage, c'est une piece comptable qui perd sa justification.
 *
 * <h2>Ce que ce service ne refuse PAS</h2>
 *
 * <p>Une proposition sur un couple deja actif, avec une date de debut
 * posterieure, est <b>acceptee</b> : c'est le mecanisme normal de changement de
 * tarif (decision Sprint 2.2, versionnement par nouvelle ligne). Tant qu'elle
 * reste {@code EN_ATTENTE_DRH}, elle est invisible de
 * {@code rechercherGrilleActive} et ne change aucun montant paye.
 *
 * <h2>Rapport avec l'index de la base</h2>
 *
 * <p>L'index partiel {@code ux_grille_active_par_couple} reste indispensable : il
 * protege des ecritures reellement concurrentes, que deux verifications
 * applicatives simultanees ne peuvent pas voir l'une de l'autre. Mais il ne doit
 * jamais etre ce qui parle a l'utilisateur.
 */
@Service
public class UniciteGrilleService {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final GrilleTarifaireRepository grilleTarifaireRepository;

    public UniciteGrilleService(GrilleTarifaireRepository grilleTarifaireRepository) {
        this.grilleTarifaireRepository = grilleTarifaireRepository;
    }

    /**
     * Verifie qu'une grille peut etre proposee sur ce couple a cette date.
     * Retourne normalement si c'est le cas, leve sinon.
     *
     * <p>L'ordre des deux controles n'est pas indifferent : la proposition
     * concurrente est verifiee d'abord, parce que c'est le cas ou l'ARH n'a rien
     * a corriger. Lui signaler d'abord un probleme de date l'enverrait modifier
     * une saisie qui n'a rien d'errone.
     *
     * @throws ConflitGrilleException si la proposition ne peut pas coexister
     *         avec ce que porte deja le couple
     */
    @Transactional(readOnly = true)
    public void verifierAvantCreation(NatureEnum nature, SessionEnum session, LocalDate dateDebut) {
        refuserSiPropositionConcurrente(nature, session);
        refuserSiPeriodeIncoherente(nature, session, dateDebut);
    }

    private void refuserSiPropositionConcurrente(NatureEnum nature, SessionEnum session) {
        List<GrilleTarifaire> enAttente =
                grilleTarifaireRepository.rechercherPropositionsEnAttente(nature, session);
        if (enAttente.isEmpty()) {
            return;
        }
        GrilleTarifaire proposition = enAttente.get(0);
        throw new ConflitGrilleException(
                ConflitGrilleException.CODE_PROPOSITION_EN_ATTENTE,
                ("Une grille %s / %s attend deja la decision de la Directrice RH "
                        + "(proposee a %d FCFA a compter du %s). Une seule proposition a la fois "
                        + "par couple nature et session : attendez qu'elle soit validee ou rejetee.")
                        .formatted(nature, session, proposition.getMontantFcfa(),
                                proposition.getDateDebut().format(JOUR)));
    }

    private void refuserSiPeriodeIncoherente(NatureEnum nature, SessionEnum session, LocalDate dateDebut) {
        grilleTarifaireRepository.rechercherGrilleCourante(nature, session)
                .filter(courante -> !dateDebut.isAfter(courante.getDateDebut()))
                .ifPresent(courante -> {
                    throw new ConflitGrilleException(
                            ConflitGrilleException.CODE_GRILLE_ACTIVE,
                            ("Une grille %s / %s est active depuis le %s, a %d FCFA. Une nouvelle "
                                    + "grille doit prendre effet APRES cette date ; le %s demande "
                                    + "recouvrirait une periode deja servie.")
                                    .formatted(nature, session, courante.getDateDebut().format(JOUR),
                                            courante.getMontantFcfa(), dateDebut.format(JOUR)));
                });
    }

}
