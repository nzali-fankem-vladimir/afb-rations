package cm.afrilandfirstbank.rations.saisie.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.saisie.application.EtatConsolide.JourneeConsolidee;
import cm.afrilandfirstbank.rations.saisie.domaine.Beneficiaire;
import cm.afrilandfirstbank.rations.saisie.domaine.FicheJournaliere;
import cm.afrilandfirstbank.rations.saisie.domaine.LignePrestation;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.UniteNonConcordanteException;
import cm.afrilandfirstbank.rations.saisie.infrastructure.BeneficiaireRepository;
import cm.afrilandfirstbank.rations.saisie.infrastructure.FicheJournaliereRepository;
import cm.afrilandfirstbank.rations.saisie.infrastructure.LignePrestationRepository;

/**
 * <b>RG-06 côté service Saisie</b> : agrège les fiches journalières d'un
 * processus en un état mensuel, avec le détail par journée, les sous-totaux et
 * le total du mois (US-06, CT-11).
 *
 * <h2>Le partage de RG-06 entre deux services</h2>
 *
 * <p>La règle dit que les fiches sont consolidées automatiquement par mois et par
 * unité. Elle ne peut pas vivre dans un seul service : <b>Saisie</b> détient
 * {@code fiche_journaliere} et {@code ligne_prestation}, donc les données ;
 * <b>Workflow</b> détient {@code processus_mensuel}, donc le
 * {@code montant_total} et l'aiguillage au seuil (RG-08). Ce service produit
 * l'état ; Workflow le consomme et porte le total. Aucun des deux ne fait le
 * travail de l'autre.
 *
 * <h2>Où le montant est calculé, et pourquoi ici</h2>
 *
 * <p><b>Le total du mois est la somme des sous-totaux journaliers, eux-mêmes
 * sommes des lignes effectivement rendues dans la réponse.</b> Il n'est jamais
 * calculé par une requête d'agrégation distincte du détail affiché.
 *
 * <p>C'est le point le plus important de cette classe. Un {@code SUM(...)} SQL
 * posé à côté produirait un second chemin de calcul, avec sa propre clause
 * {@code WHERE} : le jour où l'un des deux dérive, l'état afficherait un détail
 * et un total qui ne s'additionnent pas — et rien ne le signalerait. Ici, la
 * divergence est <b>structurellement impossible</b> : retirer une ligne du détail
 * la retire du total dans le même mouvement.
 *
 * <p>Ce service est en conséquence le <b>seul endroit du module où un montant
 * mensuel est calculé</b>. Les DTO de sortie ne font que recopier ce qu'il
 * produit.
 *
 * <h2>Aucun montant n'est jamais recalculé depuis la grille</h2>
 *
 * <p>L'agrégation porte exclusivement sur {@code ligne_prestation.montant_applique},
 * figé à la saisie (RG-03). Le service Grilles n'est pas appelé, et ne doit
 * jamais l'être ici. Une ligne saisie en juillet garde le montant de la grille de
 * juillet, même si le tarif a changé depuis : recalculer rendrait un état validé
 * instable dans le temps, et un état déjà payé injustifiable.
 *
 * <h2>Aucune jointure, donc aucun double comptage</h2>
 *
 * <p>Les lignes sont lues par un {@code IN} sur les identifiants des fiches du
 * processus, jamais par une jointure vers {@code fiche_journaliere}. Une jointure
 * mal posée peut multiplier les lignes et donc compter deux fois un montant ;
 * l'appartenance de chaque ligne à sa journée est ensuite rétablie en mémoire par
 * un simple regroupement, où une ligne ne peut apparaître que dans un seul
 * groupe.
 *
 * <h2>Pourquoi cette méthode n'est pas transactionnelle</h2>
 *
 * <p>Elle commence par un appel réseau au service Identité. L'englober dans une
 * transaction immobiliserait une connexion de la réserve pendant tout cet appel —
 * le défaut que la doctrine du Sprint 2.3 écarte déjà à la bascule des grilles.
 * L'appel a donc lieu <b>avant</b> toute lecture en base.
 *
 * <p>Conséquence assumée : les fiches et leurs lignes sont lues en deux temps.
 * Une journée ouverte entre les deux lectures serait absente de la réponse — mais
 * absente <i>cohéremment</i> : le nombre de journées et le total la reflètent
 * l'un comme l'autre, exactement comme si la requête était arrivée un instant
 * plus tôt. Aucune ligne ne peut en revanche être comptée deux fois.
 *
 * <p>Et surtout, cette fenêtre n'existe que tant que l'état est en saisie :
 * dès la soumission, {@link EtatModifiableService} refuse toute écriture. Le
 * montant sur lequel se décide l'aiguillage (RG-08) est donc lu sur un état qui
 * ne peut plus changer.
 */
@Service
public class ConsolidationService {

    private static final Logger journal = LoggerFactory.getLogger(ConsolidationService.class);

    private final FicheJournaliereRepository ficheJournaliereRepository;
    private final LignePrestationRepository lignePrestationRepository;
    private final BeneficiaireRepository beneficiaireRepository;
    private final EtatModifiableService etatModifiableService;

    public ConsolidationService(FicheJournaliereRepository ficheJournaliereRepository,
                                LignePrestationRepository lignePrestationRepository,
                                BeneficiaireRepository beneficiaireRepository,
                                EtatModifiableService etatModifiableService) {
        this.ficheJournaliereRepository = ficheJournaliereRepository;
        this.lignePrestationRepository = lignePrestationRepository;
        this.beneficiaireRepository = beneficiaireRepository;
        this.etatModifiableService = etatModifiableService;
    }

    /**
     * Produit l'état mensuel consolidé d'un processus.
     *
     * <p><b>{@code codeUniteDeclare} est obligatoire</b>, sans valeur par défaut.
     * Il est fourni par l'appelant — le service Workflow, qui détient
     * {@code processus_mensuel} — et sert deux choses distinctes, dans cet
     * ordre :
     *
     * <ol>
     *   <li>vérifier la portée d'accès, <b>y compris quand le processus n'a
     *       encore aucune fiche</b> ;</li>
     *   <li>être lui-même recoupé contre le code unité figé sur les fiches, qui
     *       reste l'autorité.</li>
     * </ol>
     *
     * <p>Un état sans aucune fiche n'est pas une erreur : la réponse est rendue
     * avec zéro journée et un total de zéro. C'est le parti déjà retenu au
     * Sprint 2.4 pour {@code GET /grilles/active} — un endpoint interne qui
     * répond à une question métier ne déguise pas une réponse négative légitime
     * en erreur de transport. Refuser l'existence d'un processus serait de toute
     * façon faux : Saisie ne sait pas si le processus existe, elle sait seulement
     * qu'elle ne détient rien pour lui.
     *
     * @param idProcessus processus à consolider
     * @param codeUniteDeclare unité déclarée par l'appelant, obligatoire
     * @param enteteAutorisation en-tête {@code Authorization} de l'utilisateur
     *        final, relayé tel quel (doctrine Sprint 1.3)
     * @throws cm.afrilandfirstbank.rations.saisie.domaine.exception.AgentNonHabiliteException
     *         hors de la portée d'accès ({@code 403})
     * @throws UniteNonConcordanteException le processus ne relève pas de l'unité
     *         déclarée ({@code 403})
     * @throws cm.afrilandfirstbank.rations.saisie.domaine.exception.ServiceIdentiteIndisponibleException
     *         service Identité muet ({@code 503})
     */
    public EtatConsolide consolider(Long idProcessus, String codeUniteDeclare,
                                    String enteteAutorisation) {

        // 1. Portee d'acces, avant toute lecture, et hors transaction.
        etatModifiableService.exigerHabilitationSurUnite(codeUniteDeclare, enteteAutorisation);

        // 2. Les journees du processus, triees par date croissante.
        List<FicheJournaliere> fiches = ficheJournaliereRepository.findByIdProcessus(idProcessus).stream()
                .sorted(Comparator.comparing(FicheJournaliere::getDateJour))
                .toList();

        if (fiches.isEmpty()) {
            journal.debug("Consolidation du processus {} : aucune fiche journaliere, etat vide rendu.",
                    idProcessus);
            return new EtatConsolide(idProcessus, codeUniteDeclare, null, null,
                    0, 0, 0, 0L, List.of());
        }

        // 3. La declaration de l'appelant est-elle veridique ?
        exigerConcordanceDesUnites(idProcessus, codeUniteDeclare, fiches);

        // 4. Toutes les lignes du mois, en une requete, sans jointure.
        List<Long> idsFiches = fiches.stream().map(FicheJournaliere::getId).toList();
        List<LignePrestation> lignes = lignePrestationRepository.findByIdFicheJournaliereIn(idsFiches);

        Map<Long, Beneficiaire> beneficiairesParId = chargerBeneficiaires(lignes);
        Map<Long, List<LignePrestation>> lignesParFiche = lignes.stream()
                .collect(Collectors.groupingBy(LignePrestation::getIdFicheJournaliere));

        // 5. Une journee par fiche, chacune avec son sous-total.
        List<JourneeConsolidee> journees = fiches.stream()
                .map(fiche -> consoliderJournee(
                        fiche,
                        lignesParFiche.getOrDefault(fiche.getId(), List.of()),
                        beneficiairesParId))
                .toList();

        // 6. Le total du mois : somme des sous-totaux, jamais un calcul parallele.
        long montantTotalFcfa = journees.stream()
                .mapToLong(JourneeConsolidee::sousTotalFcfa)
                .sum();

        int nombreLignes = journees.stream()
                .mapToInt(JourneeConsolidee::nombreLignes)
                .sum();

        int nombreBeneficiaires = lignes.stream()
                .map(LignePrestation::getIdBeneficiaire)
                .collect(Collectors.toSet())
                .size();

        FicheJournaliere premiere = fiches.getFirst();

        return new EtatConsolide(
                idProcessus,
                premiere.getCodeUnite() != null ? premiere.getCodeUnite() : codeUniteDeclare,
                premiere.getMoisPaiement(),
                premiere.getAnneePaiement(),
                journees.size(),
                nombreLignes,
                nombreBeneficiaires,
                montantTotalFcfa,
                journees);
    }

    /**
     * Construit une journée : ses lignes triées dans l'ordre de saisie, et leur
     * sous-total.
     *
     * <p>Le sous-total est une somme de {@code int} accumulée en {@code long}
     * ({@code mapToLong}). Aucun flottant n'intervient — c'est le seul type de
     * calcul admis sur un montant en FCFA.
     */
    private JourneeConsolidee consoliderJournee(FicheJournaliere fiche,
                                                List<LignePrestation> lignesDeLaFiche,
                                                Map<Long, Beneficiaire> beneficiairesParId) {

        List<LigneAvecBeneficiaire> lignes = lignesDeLaFiche.stream()
                .sorted(Comparator.comparing(LignePrestation::getId))
                .map(ligne -> new LigneAvecBeneficiaire(
                        ligne, beneficiairesParId.get(ligne.getIdBeneficiaire())))
                .toList();

        long sousTotalFcfa = lignes.stream()
                .mapToLong(ligne -> ligne.ligne().getMontantApplique())
                .sum();

        return new JourneeConsolidee(
                fiche.getId(),
                fiche.getDateJour(),
                fiche.getStatut(),
                lignes.size(),
                sousTotalFcfa,
                lignes);
    }

    /**
     * Recoupe le code unité déclaré par l'appelant contre celui figé sur les
     * fiches du processus (migration V3), qui fait autorité.
     *
     * <p>Les fiches antérieures à la migration V3 portent un code unité nul. Une
     * valeur absente ne peut pas contredire la déclaration : elle est écartée du
     * recoupement, mais <b>signalée en journal</b> — silencieusement l'ignorer
     * ferait disparaître le contrôle sans que personne ne le sache. Toute fiche
     * ouverte depuis le Sprint 3.3 porte la valeur.
     */
    private void exigerConcordanceDesUnites(Long idProcessus, String codeUniteDeclare,
                                            List<FicheJournaliere> fiches) {

        Set<String> unitesDesFiches = fiches.stream()
                .map(FicheJournaliere::getCodeUnite)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());

        if (unitesDesFiches.isEmpty()) {
            journal.warn("Consolidation du processus {} : aucune fiche ne porte de code unite "
                    + "(fiches anterieures a la migration V3). Le code unite declare \"{}\" "
                    + "n'a pas pu etre recoupe.", idProcessus, codeUniteDeclare);
            return;
        }

        if (!unitesDesFiches.equals(Set.of(codeUniteDeclare))) {
            throw new UniteNonConcordanteException(String.format(
                    "Le processus %d ne releve pas de l'unite %s declaree, mais de %s. "
                            + "L'etat consolide ne peut pas etre rendu.",
                    idProcessus, codeUniteDeclare, String.join(", ", unitesDesFiches)));
        }
    }

    /**
     * Charge en une requête les bénéficiaires servis sur le mois. Un même agent
     * servi vingt jours n'est chargé qu'une fois.
     */
    private Map<Long, Beneficiaire> chargerBeneficiaires(List<LignePrestation> lignes) {
        if (lignes.isEmpty()) {
            return Map.of();
        }

        Set<Long> ids = lignes.stream()
                .map(LignePrestation::getIdBeneficiaire)
                .collect(Collectors.toSet());

        return beneficiaireRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Beneficiaire::getId, Function.identity()));
    }

}
