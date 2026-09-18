package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * La valeur proposee ne respecte pas le format attendu par ce parametre
 * (guide 7F.6, etape 6, ajout backend scope).
 *
 * <p>Reprend la lecture stricte de {@code SeuilService} (Sprint 4.3) pour les
 * deux parametres entiers ({@code SEUIL_AIGUILLAGE_DR},
 * {@code DELAI_REGULARISATION_JOURS}) : aucun separateur de milliers, aucune
 * decimale, aucune valeur negative. Un seuil negatif ferait monter tous les
 * etats au Directeur Reseau sans qu'aucune erreur ne le signale ; un delai
 * negatif n'a pas de sens. {@code COMPTE_CHARGE_RATIONS} n'accepte qu'une
 * contrainte : non vide -- c'est un numero de compte, pas necessairement
 * numerique dans tous les plans comptables.
 *
 * <p>{@code 400} et non {@code 422} : c'est une erreur de forme sur le corps
 * de la requete, pas une regle de gestion sur une ressource valide.
 */
public class ValeurParametreInvalideException extends RuntimeException {

    public ValeurParametreInvalideException(String message) {
        super(message);
    }

}
