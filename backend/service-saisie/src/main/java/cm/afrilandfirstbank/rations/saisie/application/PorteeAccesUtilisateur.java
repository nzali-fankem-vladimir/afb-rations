package cm.afrilandfirstbank.rations.saisie.application;

import java.util.Set;

/**
 * La portee d'acces d'un utilisateur, vue comme un <b>ensemble d'unites</b>
 * (Sprint 6.1).
 *
 * <h2>Pourquoi une seconde forme de la portee dans ce service</h2>
 *
 * <p>Jusqu'ici le service Workflow ne posait jamais qu'une question a la fois :
 * « cette personne peut-elle agir sur l'unite {@code 00002} ? », a laquelle
 * {@code GET /identite/habilitation} repond par oui ou par non
 * ({@link HabilitationService}). Une <b>recherche</b> pose la question a
 * l'envers : « quelles unites cette personne peut-elle voir ? ». La poser unite
 * par unite exigerait un appel par code guichet du referentiel.
 *
 * <p>Cette forme-la n'est <b>pas recalculee ici</b> : elle est lue telle quelle
 * dans le champ {@code porteeAcces} que le service Identite publie sur
 * {@code GET /identite/moi}. Le proprietaire de la regle reste le meme service ;
 * seul l'angle de la question change. C'est la nuance avec le commentaire de
 * {@code ProfilReponse}, qui ecarte a juste titre toute <i>reinterpretation</i>
 * locale de la portee.
 *
 * @param nationale toutes les unites, sans enumeration
 * @param codesUnite unites accessibles quand la portee ne l'est pas ; vide si nationale
 */
public record PorteeAccesUtilisateur(boolean nationale, Set<String> codesUnite) {

    public PorteeAccesUtilisateur {
        codesUnite = codesUnite == null ? Set.of() : Set.copyOf(codesUnite);
    }

    public boolean couvre(String codeUnite) {
        return nationale || codesUnite.contains(codeUnite);
    }

    /**
     * Une portee qui ne couvre rien : ni nationale, ni aucune unite.
     *
     * <p>Elle existe parce qu'elle est <b>possible</b> — un profil local sans code
     * unite et sans role a portee nationale — et qu'une recherche doit alors rendre
     * zero resultat, pas toutes les unites. Le cas est nomme plutot que subi : une
     * liste vide passee a un {@code in (...)} SQL serait au mieux une erreur de
     * syntaxe, au pire un filtre sans effet.
     */
    public boolean neCouvreRien() {
        return !nationale && codesUnite.isEmpty();
    }

}
