package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Une fonctionnalite livree mais non ouverte a ete demandee. Rendue en
 * {@code 422 FONCTIONNALITE_NON_OUVERTE}
 * ({@code docs/dispositifs_provisoires.md} section 1.3).
 *
 * <p>Unique cas au Sprint 4.1 : l'ouverture d'un etat
 * {@code COMPLEMENTAIRE}, dont le besoin n'est pas confirme par le metier
 * (point M-01 de {@code docs/points-en-attente.md}).
 *
 * <p><b>Un code distinct, deliberement.</b> Un {@code 403} laisserait croire a un
 * probleme d'habilitation, et l'agent perdrait du temps a chercher qui peut lui
 * donner le droit — alors que personne ne le peut aujourd'hui.
 */
public class FonctionnaliteNonOuverteException extends RuntimeException {

    public FonctionnaliteNonOuverteException(String message) {
        super(message);
    }

}
