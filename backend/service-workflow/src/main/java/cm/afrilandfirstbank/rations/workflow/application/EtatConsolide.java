package cm.afrilandfirstbank.rations.workflow.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Etat mensuel consolide rendu par le service Saisie
 * ({@code GET /saisie/processus/{id}/etat?codeUnite=...}, Sprint 3.4).
 *
 * <p>Structure et garanties decrites dans {@code docs/appel-consolidation.md}
 * section 3. RG-06 est partagee : <b>Saisie produit ce total, Workflow le
 * porte</b> sur {@code processus_mensuel.montant_total} et applique l'aiguillage
 * au seuil (RG-08). Workflow ne readditionne jamais les lignes — il n'existe
 * qu'un seul chemin de calcul, donc aucune divergence possible entre le detail
 * affiche et le total.
 *
 * <h2>Ce type vit dans la couche application, pas dans l'infrastructure</h2>
 *
 * <p>C'est le vocabulaire du port {@link ConsolidationClient} : le rendre
 * infrastructure obligerait la couche application a en dependre, dans le mauvais
 * sens. Le mapper une seconde fois vers un jumeau applicatif serait la solution
 * orthodoxe, mais sur un arbre a quatre niveaux ce serait de la ceremonie pure —
 * la recopie n'ajouterait aucune decision, seulement des occasions d'oublier un
 * champ. {@link JsonIgnoreProperties} est un indice de liaison, pas une
 * dependance de framework.
 *
 * <h2>Tolerant reader</h2>
 *
 * <p>Le service Saisie peut enrichir sa reponse sans casser celle-ci ; les champs
 * inconnus sont ignores. Tolerance a la <b>lecture</b> seulement : un champ
 * manquant se lit {@code null} — d'ou les types boites — et l'appelant refuse
 * plutot que de supposer une valeur.
 *
 * <h2>Montants entiers</h2>
 *
 * <p>{@code long} pour les sommes, {@code int} par ligne, aucun flottant ni
 * {@code BigDecimal} (section 3.3 de la convention). Un seul arrondi suffirait a
 * decaler d'un franc le montant qui commande l'aiguillage au seuil de
 * 100 000 XAF, et donc a envoyer un dossier au mauvais niveau de validation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EtatConsolide(
        Long idProcessus,
        String codeUnite,
        Integer moisPaiement,
        Integer anneePaiement,
        Integer nombreJournees,
        Integer nombreLignes,
        Integer nombreBeneficiaires,
        Long montantTotalFcfa,
        List<Journee> journees) {

    /**
     * Une journee saisie, avec ses lignes et son sous-total. Une journee ouverte
     * sans ligne est presente, avec un sous-total de zero.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Journee(
            Long idFicheJournaliere,
            LocalDate dateJour,
            String statut,
            Integer nombreLignes,
            Long sousTotalFcfa,
            List<Ligne> lignes) {
    }

    /**
     * Une ligne de prestation valorisee. {@code montantApplique} est le montant
     * <b>fige a la saisie</b> (RG-03) : le service Grilles n'est jamais rappele,
     * et une ligne de juillet garde le tarif de juillet.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ligne(
            Long id,
            Long idFicheJournaliere,
            Long idBeneficiaire,
            Beneficiaire beneficiaire,
            String nature,
            String session,
            Integer montantApplique,
            Long idGrille,
            LocalDateTime dateCreation) {
    }

    /**
     * Beneficiaire servi. {@code codeAgence} est l'agence de domiciliation de son
     * compte — la ligne de <b>credit</b>, a ne pas confondre avec le
     * {@code codeUnite} du processus, qui est la ligne de debit.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Beneficiaire(
            Long id,
            String nom,
            String prenom,
            String numCompteCourant,
            String codeAgence) {
    }

}
