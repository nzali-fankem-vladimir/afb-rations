package cm.afrilandfirstbank.rations.workflow.infrastructure.identite;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * La partie de {@code GET /identite/moi} qui interesse une recherche : le champ
 * {@code porteeAcces} (Sprint 6.1).
 *
 * <p><b>Tolerant reader</b>, comme {@link ProfilReponse} : le service Identite
 * peut enrichir sa reponse sans casser celui-ci. Tolerance a la <i>lecture</i>
 * seulement — un champ absent se lit {@code null} et fait <b>refuser</b> plutot
 * que de produire une portee par defaut. Une portee devinee serait soit trop
 * large — des dossiers d'autres unites rendus visibles —, soit trop etroite, et
 * l'une des deux erreurs ne se verrait jamais.
 *
 * <p>Une classe distincte de {@link ProfilReponse} plutot qu'un champ ajoute :
 * celle-ci ecarte deliberement {@code porteeAcces} au motif que la portee est
 * tranchee par {@code GET /identite/habilitation}. Ce motif tient toujours pour
 * la question « cette personne peut-elle agir sur cette unite ». Il ne tient pas
 * pour « quelles unites peut-elle voir », a laquelle {@code habilitation} ne sait
 * pas repondre. Deux questions, deux lectures, aucune ambiguite a l'usage.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PorteeReponse(PorteeAccesReponse porteeAcces) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PorteeAccesReponse(Boolean nationale, Set<String> codesUnite) {
    }

}
