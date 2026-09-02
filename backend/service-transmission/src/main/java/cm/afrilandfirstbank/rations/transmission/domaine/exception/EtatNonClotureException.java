package cm.afrilandfirstbank.rations.transmission.domaine.exception;

/**
 * L'etat n'est pas cloture, donc rien ne part vers la comptabilite
 * ({@code 422 ETAT_NON_CLOTURE}).
 *
 * <p><b>Ce controle est la raison pour laquelle ce service relit l'en-tete a la
 * source</b> plutot que de le recevoir dans la demande (arbitrage Sprint 5.1, etape 3).
 * Sans lui, l'endpoint interne de declenchement publierait vers la comptabilite tout ce
 * qu'on lui presenterait — y compris un etat encore en saisie, dont les montants ne sont
 * ni consolides, ni valides, ni signes.
 *
 * <p>{@code 422} et non {@code 409} : rien n'est duplique, c'est une regle de gestion qui
 * refuse. Meme distinction qu'au Sprint 2.3 pour {@code TRANSITION_INTERDITE} et au
 * Sprint 3.3 pour {@code ETAT_NON_MODIFIABLE}.
 */
public class EtatNonClotureException extends RuntimeException {

    public EtatNonClotureException(Long idProcessus, String statutConstate) {
        super("L'etat " + idProcessus + " porte le statut " + statutConstate + " : seul un etat "
                + "CLOTURE est mis a la disposition de la comptabilite. La cloture est prononcee "
                + "par la validation du chef d'unite sous le seuil, ou par celle du directeur "
                + "reseau au-dela (RG-08). Rien n'a ete publie.");
    }

}
