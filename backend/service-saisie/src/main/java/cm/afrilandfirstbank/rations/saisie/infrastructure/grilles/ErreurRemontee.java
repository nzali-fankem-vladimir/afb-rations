package cm.afrilandfirstbank.rations.saisie.infrastructure.grilles;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Corps d'erreur au format uniforme du projet ({@code timestamp, status, code,
 * message, path}, CLAUDE.md section 11), tel que le service Saisie le lit quand
 * le service Grilles repond en erreur.
 *
 * <p>Seul {@code code} est exploite : il permet de reconnaitre
 * {@code INCOHERENCE_GRILLE} — deux grilles actives se chevauchent — et de le
 * journaliser comme l'incident de donnees qu'il est, plutot que comme une panne
 * reseau ordinaire ({@code docs/appel-resolution-montant.md} section 3).
 * Les autres champs sont ignores.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErreurRemontee(String code, String message) {
}
