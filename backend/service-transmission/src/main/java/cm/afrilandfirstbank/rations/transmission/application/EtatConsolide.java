package cm.afrilandfirstbank.rations.transmission.application;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Etat mensuel consolide rendu par le service Saisie
 * ({@code GET /saisie/processus/{id}/etat?codeUnite=...}, endpoint interne du
 * Sprint 3.4, {@code docs/appel-consolidation.md}).
 *
 * <p>C'est la <b>seule source du detail des lignes</b> qui part vers la comptabilite :
 * {@code beneficiaires} et {@code ligne_prestation} vivent dans la base du service
 * Saisie, et ce service n'y a aucun acces direct (diagramme AR04).
 *
 * <h2>Copie a l'identique du type ecrit cote Workflow</h2>
 *
 * <p>Le service Workflow porte le meme type depuis le Sprint 3.4, contre le meme
 * endpoint. Duplication assumee, doctrine du module :
 * {@code rations-audit-commun} est la seule mutualisation de code du backend et son
 * perimetre est verifie au build (CLAUDE.md sections 3 et 15). Deux lecteurs du meme
 * contrat valent mieux qu'un huitieme artefact partage a faire evoluer au pas cadence.
 *
 * <h2>Ce qui est repris de la reponse, et ce qui ne l'est pas</h2>
 *
 * <p>Les sous-totaux journaliers et les identifiants techniques ({@code idGrille},
 * {@code idFicheJournaliere}) sont lus mais ne partent <b>pas</b> dans la charge : le
 * contrat section 7.1 ne les prevoit pas. {@code montantTotalFcfa} sert de <b>second
 * temoin</b> au controle de coherence, aux cotes du montant porte par le processus.
 *
 * <h2>Tolerant reader, montants entiers</h2>
 *
 * <p>Champs inconnus ignores ; types boites pour qu'un champ manquant se lise
 * {@code null} et soit refuse, jamais suppose. {@code long} en somme, {@code int} par
 * ligne, aucun flottant.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EtatConsolide(
        Long idProcessus,
        String codeUnite,
        LocalDate dateDebut,
        LocalDate dateFin,
        Integer nombreJournees,
        Integer nombreLignes,
        Integer nombreBeneficiaires,
        Long montantTotalFcfa,
        List<Journee> journees) {

    /** Une journee saisie. Une journee ouverte sans ligne est presente, sous-total nul. */
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
     * <b>fige a la saisie</b> (RG-03) : le service Grilles n'est jamais rappele, ni
     * ici ni ailleurs, et une ligne de juillet garde le tarif de juillet meme si la
     * grille a change depuis.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ligne(
            Long id,
            Long idBeneficiaire,
            Beneficiaire beneficiaire,
            String nature,
            String session,
            Integer montantApplique) {
    }

    /**
     * Beneficiaire servi. {@code codeAgence} est l'agence de domiciliation de son
     * compte — la ligne de <b>credit</b> —, a ne pas confondre avec le
     * {@code codeUnite} du processus, qui est la ligne de <b>debit</b>.
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
