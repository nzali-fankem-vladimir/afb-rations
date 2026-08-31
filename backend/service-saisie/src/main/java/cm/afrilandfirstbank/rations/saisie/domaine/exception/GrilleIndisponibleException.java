package cm.afrilandfirstbank.rations.saisie.domaine.exception;

/**
 * <b>RG-03</b> : aucune grille tarifaire ne couvre la date de la prestation pour
 * ce couple (nature, session). La ligne est refusee — <b>refus metier</b>,
 * US-05 et CT-10.
 *
 * <p>Traduite en {@code 422 GRILLE_INDISPONIBLE} par le gestionnaire d'erreurs
 * de l'API (Sprint 3.3), code prevu par le contrat d'API section 3.
 *
 * <p><b>A ne jamais confondre avec {@link ServiceGrillesIndisponibleException}.</b>
 * Ici le service Grilles a repondu, et sa reponse est qu'il n'y a pas de tarif a
 * cette date : rien ne se debloquera en reessayant, il faut qu'une grille soit
 * proposee par l'ARH et validee par la DRH. La panne, elle, se repare toute
 * seule. Les deux appellent des actions opposees de la part de l'agent ; leur
 * donner le meme message l'enverrait dans la mauvaise direction une fois sur
 * deux ({@code docs/appel-resolution-montant.md} section 2).
 *
 * <p>Le montant n'est jamais remplace par {@code 0} ni par une valeur de repli :
 * une ligne a montant nul serait enregistree comme un tarif et pourrait partir
 * en comptabilite sans que personne ne s'en apercoive.
 */
public class GrilleIndisponibleException extends RuntimeException {

    public GrilleIndisponibleException(String message) {
        super(message);
    }

}
