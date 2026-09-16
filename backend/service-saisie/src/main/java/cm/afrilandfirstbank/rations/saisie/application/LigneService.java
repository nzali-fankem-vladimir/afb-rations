package cm.afrilandfirstbank.rations.saisie.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.saisie.application.ResultatResolutionMontant.AucuneGrilleApplicable;
import cm.afrilandfirstbank.rations.saisie.application.ResultatResolutionMontant.MontantResolu;
import cm.afrilandfirstbank.rations.saisie.application.ResultatResolutionMontant.ServiceGrillesIndisponible;
import cm.afrilandfirstbank.rations.saisie.domaine.Beneficiaire;
import cm.afrilandfirstbank.rations.saisie.domaine.FicheJournaliere;
import cm.afrilandfirstbank.rations.saisie.domaine.LignePrestation;
import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.DoublonInterEtatsException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.DoublonLigneException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.FicheIntrouvableException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.GrilleIndisponibleException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.LigneIntrouvableException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.ServiceGrillesIndisponibleException;
import cm.afrilandfirstbank.rations.saisie.infrastructure.BeneficiaireRepository;
import cm.afrilandfirstbank.rations.saisie.infrastructure.FicheJournaliereRepository;
import cm.afrilandfirstbank.rations.saisie.infrastructure.LignePrestationRepository;

/**
 * Point d'entrée unique des trois écritures sur une ligne de prestation :
 * création, modification, suppression. Sert aussi la consultation d'une fiche.
 *
 * <h2>Pourquoi la création passe par ici et non directement par
 * {@code CreationLigneService}</h2>
 *
 * <p>{@code CreationLigneService} (Sprint 3.2) porte RG-03 et RG-04, mais ne
 * connaît pas {@link EtatModifiableService} : le service Workflow n'existait pas
 * encore, la vérification du statut était hors périmètre. Ce service ajoute le
 * contrôle sans rouvrir le fichier testé de 3.2 — il charge la fiche, vérifie
 * que l'écriture est possible, <b>puis</b> délègue.
 *
 * <p>La fiche est donc chargée deux fois sur le chemin de création (ici, puis à
 * nouveau dans {@code CreationLigneService}). C'est un compromis assumé : une
 * lecture en base supplémentaire coûte moins qu'une réécriture d'un service déjà
 * livré et testé, sur une opération qui appelle de toute façon deux services
 * distants.
 *
 * <h2>Modification : traitée comme une création, jamais comme une mise à jour de champ</h2>
 *
 * <p>Changer la nature ou la session d'une ligne change le tarif qui s'applique
 * et peut faire apparaître un doublon. Les deux contrôles — RG-03, RG-04 —
 * rejouent donc intégralement, exactement comme à la création. Seul le
 * bénéficiaire ne peut pas changer (décision prise avec l'utilisateur, étape 1) :
 * une erreur de destinataire se corrige par suppression puis recréation, deux
 * actions tracées séparément, plutôt qu'une mutation qui changerait en place la
 * personne payée par une ligne existante.
 */
@Service
public class LigneService {

    private static final Logger journal = LoggerFactory.getLogger(LigneService.class);

    private final FicheJournaliereRepository ficheJournaliereRepository;
    private final LignePrestationRepository lignePrestationRepository;
    private final BeneficiaireRepository beneficiaireRepository;
    private final EtatModifiableService etatModifiableService;
    private final CreationLigneService creationLigneService;
    private final ControleDoublonService controleDoublonService;
    private final ResolutionMontantClient resolutionMontantClient;
    private final PublicateurAudit publicateurAudit;

    public LigneService(FicheJournaliereRepository ficheJournaliereRepository,
                        LignePrestationRepository lignePrestationRepository,
                        BeneficiaireRepository beneficiaireRepository,
                        EtatModifiableService etatModifiableService,
                        CreationLigneService creationLigneService,
                        ControleDoublonService controleDoublonService,
                        ResolutionMontantClient resolutionMontantClient,
                        PublicateurAudit publicateurAudit) {
        this.ficheJournaliereRepository = ficheJournaliereRepository;
        this.lignePrestationRepository = lignePrestationRepository;
        this.beneficiaireRepository = beneficiaireRepository;
        this.etatModifiableService = etatModifiableService;
        this.creationLigneService = creationLigneService;
        this.controleDoublonService = controleDoublonService;
        this.resolutionMontantClient = resolutionMontantClient;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Ajoute une ligne à une fiche (RG-03, RG-04), après vérification du
     * caractère modifiable de l'état et de la portée d'accès.
     *
     * @throws FicheIntrouvableException fiche inexistante ({@code 404})
     * @throws cm.afrilandfirstbank.rations.saisie.domaine.exception.ProcessusIntrouvableException
     *         processus inexistant ({@code 404})
     * @throws cm.afrilandfirstbank.rations.saisie.domaine.exception.EtatNonModifiableException
     *         état verrouillé ({@code 422 ETAT_NON_MODIFIABLE})
     * @throws cm.afrilandfirstbank.rations.saisie.domaine.exception.AgentNonHabiliteException
     *         hors portée ({@code 403})
     * @throws DoublonLigneException RG-04 violée ({@code 409 DOUBLON_LIGNE})
     * @throws GrilleIndisponibleException aucun tarif à cette date ({@code 422})
     */
    @Transactional
    public LigneAvecBeneficiaire creer(CommandeCreationLigne commande, String enteteAutorisation,
                                       String adresseIp) {
        FicheJournaliere fiche = chargerFiche(commande.idFicheJournaliere());
        etatModifiableService.exigerEcriturePossible(fiche.getIdProcessus(), enteteAutorisation);

        LignePrestation ligne = creationLigneService.creer(commande, enteteAutorisation, adresseIp);
        return new LigneAvecBeneficiaire(ligne, chargerBeneficiaire(ligne.getIdBeneficiaire()));
    }

    /**
     * Révise la nature et la session d'une ligne existante, avant soumission.
     *
     * <p>RG-04 est rejouée en excluant la ligne elle-même (sans quoi une
     * modification qui ne change rien serait toujours vue comme doublon d'elle
     * même). RG-03 est rejouée intégralement, à la date de la fiche.
     *
     * @throws LigneIntrouvableException ligne inexistante ({@code 404})
     * @throws DoublonLigneException la nouvelle combinaison existe déjà ailleurs
     *         sur la même journée ({@code 409})
     * @throws GrilleIndisponibleException aucun tarif pour la nouvelle
     *         combinaison ({@code 422})
     */
    @Transactional
    public LigneAvecBeneficiaire modifier(Long idLigne, NatureEnum nature, SessionEnum session,
                                          String enteteAutorisation) {
        return modifier(idLigne, nature, session, enteteAutorisation, null);
    }

    /**
     * Même opération, avec l'adresse d'origine de la requête pour la trace
     * d'audit.
     *
     * <p>Surcharge ajoutée au Sprint 6.3 : l'inventaire de couverture a relevé
     * que {@code MODIFICATION_LIGNE_PRESTATION} et
     * {@code SUPPRESSION_LIGNE_PRESTATION} publiaient une {@code adresseIp} nulle
     * alors que {@code CREATION_LIGNE_PRESTATION} la renseignait — le contrôleur
     * la détenait déjà et ne la passait qu'à la création. Trois opérations de
     * même nature sur la même entité tracées de deux façons différentes : c'était
     * un oubli, pas un choix.
     *
     * @param adresseIp origine de la requête, ou {@code null} si inconnue
     */
    @Transactional
    public LigneAvecBeneficiaire modifier(Long idLigne, NatureEnum nature, SessionEnum session,
                                          String enteteAutorisation, String adresseIp) {
        LignePrestation ligne = chargerLigne(idLigne);
        FicheJournaliere fiche = chargerFiche(ligne.getIdFicheJournaliere());
        etatModifiableService.exigerEcriturePossible(fiche.getIdProcessus(), enteteAutorisation);

        Beneficiaire beneficiaire = chargerBeneficiaire(ligne.getIdBeneficiaire());

        // RG-04, en excluant la ligne revisee elle-meme.
        if (controleDoublonService.estDoublonSurLaJourneeHorsLigne(
                fiche.getId(), beneficiaire.getId(), nature, session, ligne.getId())) {
            throw new DoublonLigneException(String.format(
                    "%s %s (compte %s) figure deja sur la journee du %s en %s / %s. "
                            + "Une meme prestation ne peut pas etre saisie deux fois.",
                    beneficiaire.getNom(), beneficiaire.getPrenom(), beneficiaire.getNumCompteCourant(),
                    fiche.getDateJour(), nature, session));
        }

        // RG-15. Pas d'exclusion de ligne a prevoir ici : la ligne revisee vit
        // dans l'etat courant, que le controle ecarte deja par construction.
        // Une modification de nature ou de session est une nouvelle combinaison,
        // qui peut tres bien avoir deja ete servie ailleurs sur la periode.
        controleDoublonService
                .etatDeLaPeriodePortantDeja(fiche, beneficiaire.getId(), nature, session)
                .ifPresent(idEtatEnConflit -> {
                    throw new DoublonInterEtatsException(CreationLigneService.messageRg15(
                            beneficiaire, fiche.getDateJour(), nature, session, idEtatEnConflit));
                });

        // RG-03, integralement rejouee pour la nouvelle combinaison.
        MontantResolu montant = resoudreMontant(nature, session, fiche.getDateJour(), enteteAutorisation);

        NatureEnum ancienneNature = ligne.getNature();
        SessionEnum ancienneSession = ligne.getSession();
        Integer ancienMontant = ligne.getMontantApplique();
        Long ancienneGrille = ligne.getIdGrille();

        ligne.reviser(nature, session, montant.montantFcfa(), montant.idGrille());

        tracerModification(ligne, ancienneNature, ancienneSession, ancienMontant, ancienneGrille,
                adresseIp);
        return new LigneAvecBeneficiaire(ligne, beneficiaire);
    }

    /**
     * Supprime une ligne, avant soumission.
     *
     * <p><b>N'est pas idempotente.</b> Une seconde suppression de la même ligne
     * rend {@code 404 LIGNE_INTROUVABLE}, pas {@code 204} : un agent qui supprime
     * deux fois la même ligne se trompe probablement de ligne, et un
     * {@code 204} silencieux le lui cacherait.
     */
    @Transactional
    public void supprimer(Long idLigne, String enteteAutorisation) {
        supprimer(idLigne, enteteAutorisation, null);
    }

    /**
     * Même opération, avec l'adresse d'origine de la requête pour la trace
     * d'audit (correction du Sprint 6.3, voir {@link #modifier}).
     *
     * @param adresseIp origine de la requête, ou {@code null} si inconnue
     */
    @Transactional
    public void supprimer(Long idLigne, String enteteAutorisation, String adresseIp) {
        LignePrestation ligne = chargerLigne(idLigne);
        FicheJournaliere fiche = chargerFiche(ligne.getIdFicheJournaliere());
        etatModifiableService.exigerEcriturePossible(fiche.getIdProcessus(), enteteAutorisation);

        lignePrestationRepository.delete(ligne);
        tracerSuppression(ligne, fiche.getDateJour(), adresseIp);
    }

    /**
     * Lignes d'une fiche, avec leur bénéficiaire résolu en une seule requête
     * supplémentaire (pas un chargement par ligne).
     *
     * <p>Aucune vérification de portée ici : elle appartient à l'appelant
     * ({@code FicheJournaliereService}, qui connaît le contexte — ouverture ou
     * consultation — dans lequel les lignes sont demandées).
     */
    @Transactional(readOnly = true)
    public List<LigneAvecBeneficiaire> listerLignes(Long idFiche) {
        List<LignePrestation> lignes = lignePrestationRepository.findByIdFicheJournaliere(idFiche);
        if (lignes.isEmpty()) {
            return List.of();
        }

        List<Long> idsBeneficiaires = lignes.stream().map(LignePrestation::getIdBeneficiaire).toList();
        Map<Long, Beneficiaire> beneficiairesParId = beneficiaireRepository.findAllById(idsBeneficiaires)
                .stream()
                .collect(java.util.stream.Collectors.toMap(Beneficiaire::getId, Function.identity()));

        return lignes.stream()
                .map(ligne -> new LigneAvecBeneficiaire(ligne, beneficiairesParId.get(ligne.getIdBeneficiaire())))
                .toList();
    }

    private FicheJournaliere chargerFiche(Long idFiche) {
        return ficheJournaliereRepository.findById(idFiche)
                .orElseThrow(() -> new FicheIntrouvableException(
                        "Aucune fiche journaliere ne porte l'identifiant " + idFiche + "."));
    }

    private LignePrestation chargerLigne(Long idLigne) {
        return lignePrestationRepository.findById(idLigne)
                .orElseThrow(() -> new LigneIntrouvableException(
                        "Aucune ligne de prestation ne porte l'identifiant " + idLigne + "."));
    }

    /**
     * Une ligne référence toujours un bénéficiaire existant : c'est l'ordre de
     * création de {@code CreationLigneService} qui le garantit (le bénéficiaire
     * est résolu ou créé avant la ligne, jamais après). Une absence ici signale
     * une incohérence de données, pas une entrée utilisateur à refuser proprement
     * — d'où une exception non métier.
     */
    private Beneficiaire chargerBeneficiaire(Long idBeneficiaire) {
        return beneficiaireRepository.findById(idBeneficiaire)
                .orElseThrow(() -> new IllegalStateException(
                        "Beneficiaire " + idBeneficiaire + " introuvable : incoherence de donnees."));
    }

    /**
     * Même traduction des trois issues de résolution qu'à la création
     * ({@code CreationLigneService.resoudreMontant}), dupliquée plutôt que
     * partagée : la mutualisation du projet se limite à
     * {@code rations-audit-commun} (CLAUDE.md §3 et §15), et extraire un
     * troisième service pour quinze lignes identiques coûterait plus qu'il ne
     * rapporte.
     */
    private MontantResolu resoudreMontant(NatureEnum nature, SessionEnum session, LocalDate journee,
                                          String enteteAutorisation) {
        ResultatResolutionMontant resultat =
                resolutionMontantClient.resoudre(nature, session, journee, enteteAutorisation);

        return switch (resultat) {

            case MontantResolu montant -> montant;

            case AucuneGrilleApplicable indisponible -> throw new GrilleIndisponibleException(String.format(
                    "Aucune grille tarifaire n'est en vigueur pour %s / %s au %s. "
                            + "La ligne ne peut pas etre valorisee tant qu'une grille n'a pas ete "
                            + "proposee par l'analyste RH et validee par la directrice RH.",
                    indisponible.nature(), indisponible.session(), indisponible.date()));

            case ServiceGrillesIndisponible panne -> {
                journal.warn("Revision refusee : resolution du montant impossible pour {} / {} au {} ({}).",
                        nature, session, journee, panne.motifTechnique());
                throw new ServiceGrillesIndisponibleException(
                        "Le service des grilles tarifaires est momentanement indisponible : "
                                + "le montant applicable n'a pas pu etre determine. "
                                + "La ligne n'a pas ete modifiee, reessayez dans un instant.");
            }
        };
    }

    private void tracerModification(LignePrestation ligne, NatureEnum ancienneNature,
                                    SessionEnum ancienneSession, Integer ancienMontant,
                                    Long ancienneGrille, String adresseIp) {
        publicateurAudit.publier(EvenementAudit.de(
                null,
                "MODIFICATION_LIGNE_PRESTATION",
                "ligne_prestation",
                ligne.getId(),
                adresseIp,
                DeltaAudit.nouveau()
                        .contexte("idFicheJournaliere", ligne.getIdFicheJournaliere())
                        .champ("nature", ancienneNature, ligne.getNature())
                        .champ("session", ancienneSession, ligne.getSession())
                        .champ("montantApplique", ancienMontant, ligne.getMontantApplique())
                        .champ("idGrille", ancienneGrille, ligne.getIdGrille())
                        .enJson()));
    }

    private void tracerSuppression(LignePrestation ligne, LocalDate journee, String adresseIp) {
        publicateurAudit.publier(EvenementAudit.de(
                null,
                "SUPPRESSION_LIGNE_PRESTATION",
                "ligne_prestation",
                ligne.getId(),
                adresseIp,
                DeltaAudit.nouveau()
                        .contexte("idFicheJournaliere", ligne.getIdFicheJournaliere())
                        .contexte("journee", journee)
                        .contexte("idBeneficiaire", ligne.getIdBeneficiaire())
                        .contexte("nature", ligne.getNature())
                        .contexte("session", ligne.getSession())
                        .contexte("montantApplique", ligne.getMontantApplique())
                        .enJson()));
    }

}
