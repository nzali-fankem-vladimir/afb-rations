package cm.afrilandfirstbank.rations.saisie.api.dto;

import java.util.List;

/**
 * Reponse de l'endpoint <b>interne</b>
 * {@code GET /saisie/processus/recherche} (Sprint 6.1).
 *
 * <p>Un objet plutot qu'un tableau nu : une reponse JSON de premier niveau qui
 * serait un tableau ne peut plus etre enrichie sans casser tous ses lecteurs. Le
 * module a deja pris ce parti partout ailleurs.
 *
 * <p><b>Une liste vide est une reponse normale</b>, en {@code 200}, jamais un
 * {@code 404} : aucun etat ne repond aux criteres, ce qui est le resultat legitime
 * d'une recherche. Meme parti qu'au Sprint 2.4 pour {@code GET /grilles/active} et
 * qu'au Sprint 3.4 pour un processus sans aucune fiche.
 *
 * @param idsProcessus identifiants d'etats contenant au moins une ligne retenue,
 *        tries et sans doublon
 */
public record RechercheLignesResponse(List<Long> idsProcessus) {

    public static RechercheLignesResponse de(List<Long> identifiants) {
        return new RechercheLignesResponse(List.copyOf(identifiants));
    }

}
