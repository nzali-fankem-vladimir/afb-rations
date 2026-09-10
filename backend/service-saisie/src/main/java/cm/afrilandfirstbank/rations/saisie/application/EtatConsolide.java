package cm.afrilandfirstbank.rations.saisie.application;

import java.time.LocalDate;
import java.util.List;

import cm.afrilandfirstbank.rations.saisie.domaine.StatutFicheEnum;

/**
 * L'état mensuel consolidé d'un processus, tel que le produit le service Saisie
 * — la moitié de RG-06 qui lui revient (CLAUDE.md §6, US-06, CT-11).
 *
 * <h2>Un type applicatif, pas un DTO</h2>
 *
 * <p>{@code EtatConsolideResponse} le recopie sans rien recalculer. La couche
 * {@code application} ne dépend pas de {@code api} (CLAUDE.md §3, dépendances
 * vers l'intérieur) : c'est la même séparation que
 * {@link LigneAvecBeneficiaire} et {@code LigneResponse} au Sprint 3.3.
 *
 * <h2>Tous les montants sont des entiers</h2>
 *
 * <p>{@code montantTotalFcfa} et {@code sousTotalFcfa} sont des {@code long},
 * jamais des {@code double} ni des {@code BigDecimal}. Les montants de ligne sont
 * des {@code int} en base (FCFA sans décimale) ; une somme d'entiers positifs ne
 * peut pas déborder sur un {@code long}, quel que soit le nombre de lignes. Un
 * seul flottant introduit dans cette chaîne suffirait à produire un écart
 * d'arrondi sur le montant qui commande l'aiguillage au seuil (RG-08).
 *
 * <h2>Ce que ce type ne porte pas, volontairement</h2>
 *
 * <p>Ni le statut du processus, ni son type ({@code NORMAL} /
 * {@code COMPLEMENTAIRE}), ni le {@code montant_total} porté par
 * {@code processus_mensuel}. Ces données vivent dans le service Workflow ; les
 * recopier ici recréerait dans Saisie une projection du domaine de Workflow, ce
 * que {@code docs/rattachement-processus.md} §3 désigne comme une faute
 * d'architecture. Workflow reprend ce total et le porte lui-même.
 *
 * @param idProcessus processus consolidé, tel que demandé par l'appelant
 * @param codeUnite unité supportant la charge — recopiée des fiches (migration
 *        V3) quand il y en a, sinon écho du paramètre déclaré par l'appelant
 * @param dateDebut premier jour de la période, recopié des fiches ; {@code null} si
 *        aucune fiche n'a encore été ouverte
 * @param dateFin dernier jour de la période, recopié des fiches ; {@code null}
 *        dans le même cas
 * @param nombreJournees nombre de journées saisies (agrégat)
 * @param nombreLignes nombre total de lignes sur le mois (agrégat)
 * @param nombreBeneficiaires bénéficiaires <b>distincts</b> servis sur le mois
 *        (agrégat) — un même agent servi vingt jours compte pour un
 * @param montantTotalFcfa total du mois (agrégat) — <b>somme des sous-totaux
 *        journaliers</b>, eux-mêmes sommes des lignes détaillées ci-dessous : le
 *        total et le détail affiché ne peuvent donc pas diverger
 * @param journees détail par journée, trié par date croissante
 */
public record EtatConsolide(
        Long idProcessus,
        String codeUnite,
        LocalDate dateDebut,
        LocalDate dateFin,
        int nombreJournees,
        int nombreLignes,
        int nombreBeneficiaires,
        long montantTotalFcfa,
        List<JourneeConsolidee> journees) {

    /**
     * Une journée saisie : la fiche, ses lignes détaillées et leur sous-total.
     *
     * @param sousTotalFcfa somme des montants des lignes de cette journée
     *        (agrégat)
     * @param lignes détail ligne à ligne, trié par identifiant croissant — donc
     *        dans l'ordre de saisie. Chaque ligne porte son bénéficiaire
     *        développé, sa nature, sa session, son montant figé et la grille dont
     *        il provient : exactement ce qu'exige la charge
     *        {@code rations.etat.valide} du contrat d'API §7.1.
     */
    public record JourneeConsolidee(
            Long idFicheJournaliere,
            LocalDate dateJour,
            StatutFicheEnum statut,
            int nombreLignes,
            long sousTotalFcfa,
            List<LigneAvecBeneficiaire> lignes) {
    }

}
