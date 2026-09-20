package cm.afrilandfirstbank.rations.saisie.application;

import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;

/**
 * Ce que l'agent demande de changer sur une ligne existante.
 *
 * <p>{@code nature} et {@code session} sont toujours présentes. Les quatre champs
 * d'identité du bénéficiaire sont <b>facultatifs</b> : {@code null} veut dire « ne
 * pas toucher ». Aucun champ de montant, comme à la création : il se résout à
 * partir de la grille (RG-03), il ne se saisit jamais.
 */
public record CommandeModificationLigne(
        NatureEnum nature,
        SessionEnum session,
        String nom,
        String prenom,
        String numCompteCourant,
        String codeAgence) {
}
