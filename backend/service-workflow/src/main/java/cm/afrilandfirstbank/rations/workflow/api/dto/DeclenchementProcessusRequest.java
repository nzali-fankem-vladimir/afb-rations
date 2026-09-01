package cm.afrilandfirstbank.rations.workflow.api.dto;

import cm.afrilandfirstbank.rations.workflow.domaine.TypeProcessusEnum;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /processus} : demande de declenchement d'un etat mensuel
 * (contrat d'API section 5).
 *
 * <p>Les trois premiers champs suffisent a un etat NORMAL, le seul ouvert au
 * Sprint 4. Les trois derniers appartiennent a l'etat COMPLEMENTAIRE : ils sont
 * <b>acceptes par le contrat mais refuses par le service</b> tant que le metier
 * n'a pas confirme le besoin (point M-01,
 * {@code docs/dispositifs_provisoires.md}). Les declarer ici plutot que de les
 * ignorer evite qu'une demande de regularisation soit silencieusement traitee
 * comme un etat normal — le pire des deux comportements possibles.
 */
public record DeclenchementProcessusRequest(

        @NotNull(message = "le mois de paiement est obligatoire")
        @Min(value = 1, message = "le mois de paiement va de 1 a 12")
        @Max(value = 12, message = "le mois de paiement va de 1 a 12")
        Integer moisPaiement,

        @NotNull(message = "l'annee de paiement est obligatoire")
        @Min(value = 2000, message = "l'annee de paiement est manifestement erronee")
        @Max(value = 2100, message = "l'annee de paiement est manifestement erronee")
        Integer anneePaiement,

        /**
         * Unite qui supporte la charge, cinq chiffres du referentiel des codes
         * guichets (contrat section 1.1). A ne pas confondre avec le code agence
         * du beneficiaire.
         */
        @NotBlank(message = "le code unite est obligatoire")
        @Pattern(regexp = "\\d{5}", message = "le code unite est une chaine de cinq chiffres")
        String codeUnite,

        /** Absent : {@link TypeProcessusEnum#NORMAL} par defaut. */
        TypeProcessusEnum typeProcessus,

        /** Etat d'origine d'une regularisation. Reserve au type COMPLEMENTAIRE. */
        Long idProcessusOrigine,

        @Size(max = 255, message = "le motif d'ouverture ne depasse pas 255 caracteres")
        String motifOuverture) {

    /**
     * Type demande, {@link TypeProcessusEnum#NORMAL} si l'appelant n'en precise
     * aucun.
     *
     * <p>Le defaut est pose ici plutot que dans le service : la valeur absente est
     * une question de lecture du corps de requete, pas une decision metier.
     */
    public TypeProcessusEnum typeDemande() {
        return typeProcessus == null ? TypeProcessusEnum.NORMAL : typeProcessus;
    }

}
