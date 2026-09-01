package cm.afrilandfirstbank.rations.workflow.domaine.exception;

import java.util.List;

import cm.afrilandfirstbank.rations.workflow.application.ManqueCompletude;

/**
 * L'etat mensuel ne peut pas etre soumis : des informations manquent (CT-13).
 *
 * <h2>Elle porte la liste, pas seulement un message</h2>
 *
 * <p>C'est l'exigence explicite de l'etape 2 du guide : « il ne retourne pas un
 * simple booleen : il retourne la liste des manques constates, pour que
 * l'interface les affiche a l'agent. Un message generique du type etat incomplet
 * obligerait l'agent a chercher lui-meme. » Le gestionnaire d'erreurs recopie
 * cette liste dans le champ {@code manques} de la reponse.
 *
 * <p>Traduite en <b>{@code 422 ETAT_INCOMPLET}</b>, et non en {@code 409} : rien
 * n'est duplique, c'est une regle de gestion qui refuse. Meme raisonnement qu'au
 * Sprint 2.3 pour {@code TRANSITION_INTERDITE} et qu'au Sprint 3.3 pour
 * {@code ETAT_NON_MODIFIABLE}.
 *
 * <h2>Levee avant toute ecriture</h2>
 *
 * <p>Le controle de completude precede la generation du document et l'ouverture
 * de la transaction. Quand cette exception est levee, <b>rien n'a ete ecrit</b> :
 * ni fichier sur le stockage, ni ligne en base. L'agent corrige et resoumet.
 */
public class EtatIncompletException extends RuntimeException {

    private final transient List<ManqueCompletude> manques;

    public EtatIncompletException(String message, List<ManqueCompletude> manques) {
        super(message);
        this.manques = List.copyOf(manques);
    }

    /** Les manques constates, dans l'ordre stable rendu par le controle. */
    public List<ManqueCompletude> getManques() {
        return manques;
    }

}
