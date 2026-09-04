package cm.afrilandfirstbank.rations.reporting.domaine.exception;

/**
 * Le service Saisie n'a pas repondu alors que la recherche portait sur la nature, la
 * session ou le beneficiaire. {@code 503 SERVICE_SAISIE_INDISPONIBLE}.
 *
 * <p><b>Echec net plutot que resultat partiel</b> (decision Sprint 6.1). Rendre les
 * etats filtres sur la seule periode afficherait, sous une etiquette « RATION », des
 * dossiers dont on ignore s'ils en contiennent. Le module refuse et signale, il
 * n'arbitre jamais - meme parti qu'au Sprint 2.4 pour {@code INCOHERENCE_GRILLE}.
 */
public class ServiceSaisieIndisponibleException extends RuntimeException {

    public ServiceSaisieIndisponibleException(String message) {
        super(message);
    }

}
