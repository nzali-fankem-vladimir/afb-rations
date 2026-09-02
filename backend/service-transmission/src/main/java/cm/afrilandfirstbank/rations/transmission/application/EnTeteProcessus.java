package cm.afrilandfirstbank.rations.transmission.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * En-tete d'un etat, tel que rendu par {@code GET /processus/{id}} du service
 * Workflow, qui detient {@code processus_mensuel}.
 *
 * <h2>Pourquoi ce service relit l'en-tete plutot que de le recevoir</h2>
 *
 * <p>Le declenchement de la transmission ne porte que l'identifiant du processus. La
 * periode, l'unite, le type et le montant total sont ensuite relus a la source
 * (arbitrage du Sprint 5.1, etape 3). Trois raisons :
 *
 * <ol>
 *   <li>Ce service peut alors <b>verifier lui-meme que l'etat est cloture</b> avant de
 *       publier. Un endpoint interne qui publierait vers la comptabilite sur la seule
 *       foi d'un corps fourni par l'appelant serait un trou : le montant qui part en
 *       paiement viendrait de la requete, pas de la base.</li>
 *   <li>Le service Workflow n'a alors rien a savoir de la forme de la charge comptable
 *       (contrat section 7.1), qui n'est pas sa responsabilite (CLAUDE.md section 3).</li>
 *   <li>Le detail des lignes ne traverse le reseau qu'une fois — de la Saisie vers ici
 *       — au lieu de deux.</li>
 * </ol>
 *
 * <h2>Aucun acces a la base du service Workflow</h2>
 *
 * <p>L'echange passe par l'API et rien d'autre (diagramme AR04) : aucune source de
 * donnees vers {@code rations_workflow} n'existe dans ce service, et la cartographie
 * le verifie.
 *
 * <h2>Tolerant reader</h2>
 *
 * <p>Le service Workflow peut enrichir {@code ProcessusResponse} sans casser ce type ;
 * les champs inconnus sont ignores. Tolerance a la <b>lecture</b> seulement : un champ
 * manquant se lit {@code null} — d'ou les types boites — et l'appelant refuse plutot
 * que de supposer une valeur. C'est l'inverse d'un montant absent lu comme un zero.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EnTeteProcessus(
        Long idProcessus,
        String statut,
        String codeUnite,
        Integer moisPaiement,
        Integer anneePaiement,
        String typeProcessus,
        Integer montantTotal,
        Boolean transmisComptabilite) {
}
