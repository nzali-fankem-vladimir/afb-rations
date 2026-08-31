package cm.afrilandfirstbank.rations.saisie.application;

import java.time.LocalDate;

import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;

/**
 * Issue d'une demande de montant applicable au service Grilles (RG-03).
 *
 * <p><b>Trois cas, et trois seulement</b> — c'est la distinction exigée par
 * {@code docs/appel-resolution-montant.md} section 2, reprise par le Sprint 3.2 :
 *
 * <ol>
 *   <li>{@link MontantResolu} — une grille couvre la date, la ligne est tarifable ;</li>
 *   <li>{@link AucuneGrilleApplicable} — le service a repondu, aucun tarif ne
 *       couvre la date : <b>refus metier</b> ({@code 422 GRILLE_INDISPONIBLE},
 *       US-05 / CT-10). L'agent doit attendre qu'une grille soit proposee par
 *       l'ARH et validee par la DRH ;</li>
 *   <li>{@link ServiceGrillesIndisponible} — le service n'a rien repondu
 *       d'exploitable : <b>refus technique</b>. L'agent doit reessayer plus tard.</li>
 * </ol>
 *
 * <p>Les deux refus ne doivent <b>jamais</b> produire le meme message : le
 * premier dit qu'il n'y a pas de tarif (contacter l'ARH), le second que le
 * systeme est en panne (reessayer). Les confondre enverrait l'agent reclamer une
 * grille pendant qu'un serveur est a terre, ou l'inverse.
 *
 * <p><b>Pourquoi un type scelle plutot qu'un enregistrement plat a drapeau.</b>
 * Le meme raisonnement qu'au Sprint 2.4 pour {@code ResolutionMontant} cote
 * Grilles, pousse d'un cran : ici, <b>seul {@link MontantResolu} porte un
 * montant, et c'est un {@code int} primitif</b>. Le repli qui ruinerait RG-03 —
 * {@code montant != null ? montant : 0} — n'est pas ecrivable, parce qu'il n'y a
 * aucun montant a replier dans les deux autres cas. Un {@code switch} sur ce
 * type est par ailleurs exhaustif sans {@code default} : ajouter un quatrieme
 * cas un jour ferait echouer la compilation des appelants au lieu de les laisser
 * tomber silencieusement dans une branche fourre-tout.
 */
public sealed interface ResultatResolutionMontant {

    /**
     * Une grille couvre la date de la prestation. Le montant est celui a figer
     * dans {@code ligne_prestation.montant_applique}, sans recalcul ni ajustement.
     *
     * @param montantFcfa montant applicable, strictement positif
     * @param idGrille grille d'ou vient le montant, tracee dans la ligne pour
     *        justifier a posteriori un montant conteste (dette levee au Sprint 3.1,
     *        colonne {@code id_grille}, migration V2)
     * @param dateDebut premier jour de validite de la grille retenue
     * @param dateFin dernier jour de validite, nul si la grille est encore courante
     */
    record MontantResolu(int montantFcfa, long idGrille, LocalDate dateDebut, LocalDate dateFin)
            implements ResultatResolutionMontant {

        public MontantResolu {
            if (montantFcfa <= 0) {
                // Le service Grilles impose deja un montant strictement positif
                // a la creation (@Positive sur CreationGrilleRequest). Un zero
                // arrive ici signale donc une reponse alteree ou un defaut de
                // deserialisation, pas un tarif : mieux vaut echouer bruyamment
                // que figer une ligne a zero qui partirait en comptabilite.
                throw new IllegalArgumentException(
                        "Montant applicable non strictement positif : " + montantFcfa
                                + ". Un tarif nul n'existe pas cote Grilles.");
            }
        }
    }

    /**
     * Le service Grilles a repondu {@code 200} avec {@code disponible: false} :
     * aucune grille ne couvre cette date pour ce couple.
     *
     * <p>Cas metier <b>normal</b>, pas une erreur de transport. La question posee
     * est conservee pour que le message rendu a l'agent la nomme, sans que
     * l'appelant ait a garder sa propre requete sous la main.
     */
    record AucuneGrilleApplicable(NatureEnum nature, SessionEnum session, LocalDate date)
            implements ResultatResolutionMontant {
    }

    /**
     * Le service Grilles n'a rien repondu d'exploitable : delai depasse,
     * connexion refusee, {@code 5xx}, corps illisible ou incoherent.
     *
     * <p><b>Refus conservateur</b> (fail-closed, doctrine Sprint 1.3 etendue au
     * service Grilles au Sprint 2.4) : la ligne est refusee, jamais enregistree
     * sans montant en vue d'une valorisation ulterieure.
     *
     * @param motifTechnique cause telle que constatee par le client HTTP,
     *        destinee au journal et au diagnostic — <b>jamais affichee telle
     *        quelle a l'agent</b>, qui n'a pas a lire une adresse de service ni
     *        une trace d'infrastructure
     */
    record ServiceGrillesIndisponible(String motifTechnique)
            implements ResultatResolutionMontant {
    }

}
