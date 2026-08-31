package cm.afrilandfirstbank.rations.saisie.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import cm.afrilandfirstbank.rations.saisie.application.CommandeCreationLigne;
import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Entrée de {@code POST /saisie/lignes} : le bénéficiaire servi, la nature et la
 * session. Rien d'autre.
 *
 * <h2>Aucun champ montant, et c'est le point de ce DTO</h2>
 *
 * <p>RG-03 veut que le montant vienne de la grille active, jamais de
 * l'utilisateur. L'option écartée au Sprint 3.2 était un champ « proposé, jamais
 * utilisé » : il rendait le test plus direct mais créait exactement la porte
 * qu'il prétendait fermer — le champ existerait, et un développeur pourrait le
 * câbler en croyant réparer un oubli. <b>La façon la plus sûre d'ignorer une
 * valeur est de n'avoir aucun endroit où la mettre</b>
 * ({@code docs/decisions/2026-08-31-refus-de-ligne-et-codes-erreur-saisie.md} §3).
 *
 * <p>{@link JsonIgnoreProperties} rend l'ignorance d'un {@code montantApplique}
 * envoyé malgré tout par un client <b>une propriété de ce DTO</b>, et non un
 * défaut de configuration Jackson qu'un {@code application.yml} pourrait
 * renverser à distance. Le champ n'est ni lu, ni mappé, ni rejeté : il n'existe
 * pas.
 *
 * <p>La même règle vaut en aval : {@link CommandeCreationLigne} n'a pas non plus
 * de champ montant, et un test structurel du Sprint 3.2 échoue si l'on en ajoute
 * un.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreationLigneRequest(

        @NotNull(message = "l'identifiant de la fiche journalière est obligatoire")
        @Positive(message = "l'identifiant de la fiche journalière doit être un entier positif")
        Long idFicheJournaliere,

        @NotNull(message = "l'identité du bénéficiaire est obligatoire")
        @Valid
        IdentiteBeneficiaireRequest beneficiaire,

        @NotNull(message = "la nature est obligatoire : RATION ou TRANSPORT")
        NatureEnum nature,

        @NotNull(message = "la session est obligatoire : JOUR ou SOIR")
        SessionEnum session) {

    /**
     * Traduit la requête HTTP en commande applicative. Le DTO d'api ne franchit
     * pas la frontière de la couche {@code application} : même séparation que
     * {@code CreationGrilleRequest} au Sprint 2.2.
     */
    public CommandeCreationLigne versCommande() {
        return new CommandeCreationLigne(
                idFicheJournaliere,
                new CommandeCreationLigne.IdentiteBeneficiaire(
                        beneficiaire.nom(),
                        beneficiaire.prenom(),
                        beneficiaire.numCompteCourant(),
                        beneficiaire.codeAgence()),
                nature,
                session);
    }

}
