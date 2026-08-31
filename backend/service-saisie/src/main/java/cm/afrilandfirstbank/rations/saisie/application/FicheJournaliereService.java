package cm.afrilandfirstbank.rations.saisie.application;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.saisie.application.ResultatVerificationProcessus.ProcessusVerifie;
import cm.afrilandfirstbank.rations.saisie.domaine.FicheJournaliere;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.FicheIntrouvableException;
import cm.afrilandfirstbank.rations.saisie.infrastructure.FicheJournaliereRepository;

/**
 * Ouvre — ou retrouve — la fiche d'une journée. C'est ici que vit RG-05.
 *
 * <h2>RG-05 n'est pas une remise à zéro</h2>
 *
 * <p>La règle dit qu'un nouveau jour ouvre une fiche neuve : elle empêche de
 * reprendre par inadvertance les lignes de la veille. <b>Elle ne dit pas qu'une
 * réouverture efface la saisie en cours.</b> L'interprétation destructrice
 * ferait perdre le travail de l'agent au moindre rafraîchissement d'écran, et
 * elle serait pratiquement indétectable : l'écran afficherait un tableau vide,
 * ce que RG-05 semble justement prescrire.
 *
 * <p>L'opération est donc <b>idempotente</b> : ouvrir deux fois le même jour rend
 * la même fiche avec ses lignes ; ouvrir un autre jour en rend une distincte et
 * vide. C'est l'unicité {@code (id_processus, date_jour)} de la migration V1 qui
 * porte structurellement la règle.
 *
 * <h2>Ce que « la même fiche » veut dire sous concurrence</h2>
 *
 * <p>Deux ouvertures simultanées du même jour — double clic, deux onglets —
 * franchiraient toutes deux la lecture avant de créer. La seconde insertion
 * échoue alors sur la contrainte d'unicité, et ce service <b>relit</b> plutôt que
 * de propager l'erreur : le client a demandé « ouvre-moi ce jour », la fiche
 * existe, sa demande est satisfaite. Rendre un conflit ici serait exact au sens
 * de la base et faux au sens de l'intention.
 *
 * <p>C'est pourquoi la méthode d'ouverture n'est pas transactionnelle : une
 * transaction rompue par la violation de contrainte ne permettrait pas de relire
 * ensuite. Aucune atomicité n'est perdue — il n'y a qu'une seule écriture.
 *
 * <h2>Aucune fiche ne naît sans processus vérifié</h2>
 *
 * <p>{@code fiche_journaliere.id_processus} désigne une ligne d'une autre base :
 * aucune clé étrangère n'est possible. La vérification préalable auprès du
 * service Workflow tient lieu de contrainte référentielle, et fournit du même
 * coup le triplet unité / mois / année recopié et figé sur la fiche
 * ({@code docs/rattachement-processus.md} §3 et §5).
 */
@Service
public class FicheJournaliereService {

    private static final Logger journal = LoggerFactory.getLogger(FicheJournaliereService.class);

    private final FicheJournaliereRepository ficheJournaliereRepository;
    private final EtatModifiableService etatModifiableService;
    private final LigneService ligneService;
    private final PublicateurAudit publicateurAudit;

    public FicheJournaliereService(FicheJournaliereRepository ficheJournaliereRepository,
                                   EtatModifiableService etatModifiableService,
                                   LigneService ligneService,
                                   PublicateurAudit publicateurAudit) {
        this.ficheJournaliereRepository = ficheJournaliereRepository;
        this.etatModifiableService = etatModifiableService;
        this.ligneService = ligneService;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Résultat d'une ouverture.
     *
     * @param creee {@code true} si la fiche vient de naître, {@code false} si
     *        elle existait déjà. Sert au contrôleur à répondre {@code 201} ou
     *        {@code 200} : deux issues légitimes de la même requête, que le
     *        client a le droit de distinguer.
     */
    public record FicheOuverte(FicheJournaliere fiche,
                               List<LigneAvecBeneficiaire> lignes,
                               boolean creee) {
    }

    /**
     * Ouvre la fiche du jour, ou rend celle qui existe déjà, avec ses lignes.
     *
     * <p>Ordre des étapes, non indifférent : le processus est vérifié
     * <b>avant</b> toute lecture ou écriture de fiche. Un agent hors portée
     * n'apprend donc pas, par le simple code de retour, si une saisie existe pour
     * une unité qui ne le regarde pas.
     */
    public FicheOuverte ouvrir(Long idProcessus, LocalDate dateJour, String enteteAutorisation,
                               String adresseIp) {

        // 1. Le processus existe, il est encore modifiable, et l'agent a le droit
        //    d'y ecrire. Le triplet unite / periode vient de la meme reponse.
        ProcessusVerifie processus =
                etatModifiableService.exigerEcriturePossible(idProcessus, enteteAutorisation);

        // 2. RG-05 : la fiche du jour existe deja ? Elle est rendue TELLE QUELLE.
        return ficheJournaliereRepository.findByIdProcessusAndDateJour(idProcessus, dateJour)
                .map(existante -> new FicheOuverte(
                        existante, ligneService.listerLignes(existante.getId()), false))
                .orElseGet(() -> creer(processus, dateJour, adresseIp));
    }

    /**
     * Crée la fiche vierge du jour. En cas de course perdue sur la contrainte
     * d'unicité, relit la fiche que l'autre requête vient de créer.
     */
    private FicheOuverte creer(ProcessusVerifie processus, LocalDate dateJour, String adresseIp) {
        FicheJournaliere fiche = new FicheJournaliere(
                processus.idProcessus(), dateJour, processus.codeUnite(),
                processus.moisPaiement(), processus.anneePaiement());

        try {
            // saveAndFlush et non save : sans vidage explicite, la violation de
            // contrainte ne surviendrait qu'au commit, hors de portee de ce
            // catch. Meme raison qu'a la bascule des grilles (Sprint 2.3) —
            // l'ordre de vidage d'Hibernate n'est pas un contrat public, il se
            // fixe plutot qu'il ne se subit.
            FicheJournaliere enregistree = ficheJournaliereRepository.saveAndFlush(fiche);
            tracerOuverture(enregistree, adresseIp);
            return new FicheOuverte(enregistree, List.of(), true);

        } catch (DataIntegrityViolationException course) {
            journal.debug("Ouverture concurrente de la fiche du {} pour le processus {} : "
                            + "la fiche creee par l'autre requete est relue.",
                    dateJour, processus.idProcessus());

            FicheJournaliere existante = ficheJournaliereRepository
                    .findByIdProcessusAndDateJour(processus.idProcessus(), dateJour)
                    .orElseThrow(() -> new FicheIntrouvableException(
                            "La fiche du " + dateJour + " n'a pu etre ni creee ni relue."));

            return new FicheOuverte(existante, ligneService.listerLignes(existante.getId()), false);
        }
    }

    /**
     * Retrouve une fiche par son identifiant, après vérification de la portée
     * d'accès. Sert la consultation des lignes.
     *
     * <p>Le statut du processus n'est pas interrogé : consulter un état clôturé
     * est légitime, seule l'écriture est fermée.
     */
    @Transactional(readOnly = true)
    public FicheJournaliere consulter(Long idFiche, String enteteAutorisation) {
        FicheJournaliere fiche = ficheJournaliereRepository.findById(idFiche)
                .orElseThrow(() -> new FicheIntrouvableException(
                        "Aucune fiche journaliere ne porte l'identifiant " + idFiche + "."));

        etatModifiableService.exigerLecturePossible(fiche, enteteAutorisation);
        return fiche;
    }

    /**
     * Trace l'ouverture d'une fiche neuve. <b>La récupération d'une fiche
     * existante n'est pas tracée</b> : elle ne change rien, et un événement à
     * chaque affichage d'écran noierait le journal — l'audit doit rester
     * consultable pour y retrouver ce qui a modifié un état.
     */
    private void tracerOuverture(FicheJournaliere fiche, String adresseIp) {
        publicateurAudit.publier(EvenementAudit.de(
                null,
                "OUVERTURE_FICHE_JOURNALIERE",
                "fiche_journaliere",
                fiche.getId(),
                adresseIp,
                DeltaAudit.nouveau()
                        .contexte("idProcessus", fiche.getIdProcessus())
                        .contexte("dateJour", fiche.getDateJour())
                        .contexte("codeUnite", fiche.getCodeUnite())
                        .contexte("moisPaiement", fiche.getMoisPaiement())
                        .contexte("anneePaiement", fiche.getAnneePaiement())
                        .contexte("statut", fiche.getStatut())
                        .enJson()));
    }

}
