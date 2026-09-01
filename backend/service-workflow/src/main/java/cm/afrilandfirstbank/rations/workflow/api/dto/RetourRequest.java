package cm.afrilandfirstbank.rations.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /processus/{id}/retour} : le motif, et rien d'autre.
 *
 * <h2>Le motif est le seul champ</h2>
 *
 * <p>Ni le niveau, ni l'acteur, ni le statut cible ne sont laisses a l'appelant. Le
 * niveau se lit sur le statut du dossier, l'acteur sur le jeton, et le statut cible
 * est {@code RETOURNE} quel que soit le niveau (RG-11). Un champ de plus serait un
 * choix offert la ou la regle ne laisse aucun choix.
 *
 * <h2>{@code @NotBlank} et non {@code @NotNull}</h2>
 *
 * <p>{@code @NotBlank} refuse la chaine vide <b>et</b> la chaine d'espaces. RG-10
 * porte sur le contenu utile, pas sur la presence du champ : {@code "   "} est un
 * champ present et un motif absent, et l'agent qui le recevrait n'aurait rien a
 * corriger. Refus en {@code 400 REQUETE_INVALIDE}.
 *
 * <p>La regle est <b>redite en aval</b> — {@code TransitionProcessus.retourner...}
 * et {@code EtapeWorkflow.retournerAvecMotif} l'exigent aussi, en
 * {@code 422 MOTIF_OBLIGATOIRE}. Ce n'est pas une redondance inutile : la validation
 * du DTO ne protege que le chemin HTTP, et une etape {@code RETOURNEE} sans motif
 * enfreindrait RG-10 sans que rien ne le signale. Meme dispositif a deux etages
 * qu'au Sprint 2.3 pour le rejet d'une grille.
 *
 * <h2>La borne de longueur</h2>
 *
 * <p>{@code motif_retour} est un {@code VARCHAR(255)} (migration V1). Sans borne
 * ici, un motif plus long partirait jusqu'a la base pour y produire une erreur
 * technique illisible, apres que le controle de statut et les appels reseau ont ete
 * faits pour rien.
 */
public record RetourRequest(

        @NotBlank(message = "le motif du retour est obligatoire et ne peut pas etre vide (RG-10)")
        @Size(max = 255, message = "le motif du retour ne peut pas depasser 255 caracteres")
        String motif) {
}
