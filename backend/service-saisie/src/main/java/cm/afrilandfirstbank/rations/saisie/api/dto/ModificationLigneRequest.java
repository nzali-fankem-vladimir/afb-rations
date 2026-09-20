package cm.afrilandfirstbank.rations.saisie.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import cm.afrilandfirstbank.rations.saisie.application.CommandeModificationLigne;
import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Entrée de {@code PUT /saisie/lignes/{id}} : la nature et la session, plus,
 * <b>facultativement</b>, l'identité du bénéficiaire (nom, prénom, numéro de
 * compte, code agence).
 *
 * <h2>Une modification se fait sur place</h2>
 *
 * <p>Jusqu'ici, changer le bénéficiaire d'une ligne passait par une suppression
 * suivie d'une création, deux appels depuis l'écran (décision du Sprint 3.3).
 * Cette voie avait deux défauts, relevés à l'usage après le Sprint 7F.7 : corriger
 * l'agence d'un bénéficiaire connu se heurtait à RG-04 (la nouvelle ligne
 * ressemblait à l'ancienne, pas encore supprimée), et si la création réussissait
 * sans que la suppression aboutisse, <b>deux lignes restaient pour la même
 * prestation</b> — un double paiement en puissance. La modification sur place est
 * une seule transaction : tout passe, ou rien ne change.
 *
 * <h2>Sens des champs d'identité</h2>
 *
 * <ul>
 *   <li><b>Numéro de compte inchangé</b> : nom, prénom et agence, s'ils diffèrent,
 *       corrigent la fiche du bénéficiaire.</li>
 *   <li><b>Numéro de compte différent</b> : la ligne est rattachée au bénéficiaire
 *       de ce compte, créé s'il n'existe pas. Un bénéficiaire déjà connu garde ses
 *       propres nom et agence (rien n'est écrasé par ce chemin).</li>
 *   <li><b>Champ absent</b> : inchangé.</li>
 * </ul>
 *
 * <p><b>Le montant n'a toujours aucune entrée</b> (RG-03) : changer la nature, la
 * session ou la date le fait recalculer. <b>La fiche</b> ne se change pas non plus :
 * déplacer une ligne d'un jour à l'autre, c'est une autre prestation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModificationLigneRequest(

        @NotNull(message = "la nature est obligatoire : RATION ou TRANSPORT")
        NatureEnum nature,

        @NotNull(message = "la session est obligatoire : JOUR ou SOIR")
        SessionEnum session,

        @Pattern(regexp = ".*\\S.*", message = "le nom du bénéficiaire ne peut pas être vide")
        @Size(max = 100, message = "le nom du bénéficiaire ne peut pas dépasser 100 caractères")
        String nom,

        @Pattern(regexp = ".*\\S.*", message = "le prénom du bénéficiaire ne peut pas être vide")
        @Size(max = 100, message = "le prénom du bénéficiaire ne peut pas dépasser 100 caractères")
        String prenom,

        @Pattern(regexp = "^[0-9]{11}$",
                message = "le numéro de compte courant doit comporter exactement onze chiffres, sans espace ni séparateur")
        String numCompteCourant,

        @Pattern(regexp = "\\d{5}", message = "le code agence doit comporter exactement cinq chiffres")
        String codeAgence) {

    public CommandeModificationLigne versCommande() {
        return new CommandeModificationLigne(nature, session, nom, prenom, numCompteCourant, codeAgence);
    }
}
