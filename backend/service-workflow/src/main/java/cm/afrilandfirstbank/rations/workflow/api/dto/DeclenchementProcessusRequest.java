package cm.afrilandfirstbank.rations.workflow.api.dto;

import cm.afrilandfirstbank.rations.workflow.domaine.TypeProcessusEnum;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
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

        /**
         * Premier jour de la periode, <b>inclus</b>. Format ISO 8601
         * ({@code 2026-09-07}), convention du module (CLAUDE.md section 11).
         */
        @NotNull(message = "la date de debut de periode est obligatoire")
        LocalDate dateDebut,

        /**
         * Dernier jour de la periode, <b>inclus</b> — et non le premier jour de la
         * suivante.
         *
         * <h2>Aucune duree maximale n'est imposee, et c'est delibere</h2>
         *
         * <p>Le point M-04 a retenu l'intervalle de dates <i>precisement</i> pour ne
         * pas figer une cadence : le metier a change d'avis une fois, et rien ne dit
         * qu'une periode ne durera pas quinze jours un jour. Ecrire ici « sept jours
         * au plus » reintroduirait le postulat que la decision vient de retirer.
         *
         * <p>Le seul controle est l'ordre des bornes, qui n'est pas une regle de
         * cadence mais une condition de sens.
         */
        @NotNull(message = "la date de fin de periode est obligatoire")
        LocalDate dateFin,

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


    /**
     * Les bornes sont dans l'ordre.
     *
     * <p>Refuse en {@code 400 REQUETE_INVALIDE} avec les autres fautes de forme,
     * plutot qu'en {@code 422} : une periode qui finit avant de commencer n'est pas
     * une regle de gestion qui refuse, c'est une demande qui ne veut rien dire.
     *
     * <p>La base porte la meme regle ({@code ck_processus_periode_ordonnee}) : sans
     * elle, la contrainte d'exclusion echouerait sur un {@code daterange} impossible
     * a construire, avec un message technique que personne ne saurait lire.
     */
    @AssertTrue(message = "la date de fin de periode ne peut pas preceder la date de debut")
    public boolean isPeriodeOrdonnee() {
        return dateDebut == null || dateFin == null || !dateFin.isBefore(dateDebut);
    }

}
