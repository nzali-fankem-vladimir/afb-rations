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
 * <p>Changer la nature, la session ou le bénéficiaire d'une ligne change le tarif
 * qui s'applique et peut faire apparaître un doublon. RG-03, RG-04 et RG-15
 * rejouent donc intégralement, exactement comme à la création, puis la ligne est
 * modifiée <b>sur place</b>, dans une seule transaction. Le bénéficiaire pouvait
 * autrefois se changer seulement par suppression puis recréation (décision du
 * Sprint 3.3) ; ce chemin en deux temps pouvait laisser deux lignes pour la même
 * prestation et refusait la correction d'une agence (retour utilisateur après le
 * Sprint 7F.7).
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
    private final ResolutionBeneficiaireService resolutionBeneficiaireService;
    private final PublicateurAudit publicateurAudit;

    public LigneService(FicheJournaliereRepository ficheJournaliereRepository,
                        LignePrestationRepository lignePrestationRepository,
                        BeneficiaireRepository beneficiaireRepository,
                        EtatModifiableService etatModifiableService,
                        CreationLigneService creationLigneService,
                        ControleDoublonService controleDoublonService,
                        ResolutionMontantClient resolutionMontantClient,
                        ResolutionBeneficiaireService resolutionBeneficiaireService,
                        PublicateurAudit publicateurAudit) {
        this.ficheJournaliereRepository = ficheJournaliereRepository;
        this.lignePrestationRepository = lignePrestationRepository;
        this.beneficiaireRepository = beneficiaireRepository;
        this.etatModifiableService = etatModifiableService;
        this.creationLigneService = creationLigneService;
        this.controleDoublonService = controleDoublonService;
        this.resolutionMontantClient = resolutionMontantClient;
        this.resolutionBeneficiaireService = resolutionBeneficiaireService;
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
     * Modifie une ligne <b>sur place</b>, avant soumission : nature, session et,
     * facultativement, l'identité du bénéficiaire (nom, prénom, compte, agence).
     *
     * <p>Une seule transaction : RG-04, RG-15 et RG-03 sont rejouées pour la
     * nouvelle combinaison, et ce n'est qu'après leur succès que la ligne (et,
     * selon le cas, la fiche du bénéficiaire) est modifiée. Un refus laisse tout
     * intact. Il n'y a plus de suppression suivie d'une création : ce chemin
     * pouvait laisser deux lignes pour la même prestation.
     *
     * <ul>
     *   <li><b>Compte inchangé</b> : nom, prénom, agence corrigent la fiche du
     *       bénéficiaire, avec l'avant et l'après dans la trace d'audit.</li>
     *   <li><b>Compte différent</b> : la ligne est rattachée au bénéficiaire de ce
     *       compte, créé s'il n'existe pas ; un bénéficiaire déjà connu garde ses
     *       propres données.</li>
     * </ul>
     *
     * @param adresseIp origine de la requête, ou {@code null} si inconnue
     * @throws LigneIntrouvableException ligne inexistante ({@code 404})
     * @throws DoublonLigneException la nouvelle combinaison existe déjà ailleurs
     *         sur la même journée ({@code 409})
     * @throws DoublonInterEtatsException la prestation est déjà servie dans un autre
     *         état de l'unité ({@code 409})
     * @throws GrilleIndisponibleException aucun tarif pour la nouvelle
     *         combinaison ({@code 422})
     */
    @Transactional
    public LigneAvecBeneficiaire modifier(Long idLigne, CommandeModificationLigne commande,
                                          String enteteAutorisation, String adresseIp) {
        LignePrestation ligne = chargerLigne(idLigne);
        FicheJournaliere fiche = chargerFiche(ligne.getIdFicheJournaliere());
        etatModifiableService.exigerEcriturePossible(fiche.getIdProcessus(), enteteAutorisation);

        Beneficiaire actuel = chargerBeneficiaire(ligne.getIdBeneficiaire());

        boolean changeDeCompte = commande.numCompteCourant() != null
                && !commande.numCompteCourant().equals(actuel.getNumCompteCourant());

        // Le bénéficiaire visé par la ligne une fois modifiée. Un compte différent
        // en désigne un autre (existant, ou créé dans cette transaction : un refus
        // plus bas le fait disparaître avec le reste).
        Beneficiaire vise = changeDeCompte
                ? resolutionBeneficiaireService.resoudre(
                        ouActuel(commande.nom(), actuel.getNom()),
                        ouActuel(commande.prenom(), actuel.getPrenom()),
                        commande.numCompteCourant(),
                        ouActuel(commande.codeAgence(), actuel.getCodeAgence()),
                        adresseIp)
                : actuel;

        NatureEnum nature = commande.nature();
        SessionEnum session = commande.session();

        // RG-04, en excluant la ligne revisee elle-meme.
        if (controleDoublonService.estDoublonSurLaJourneeHorsLigne(
                fiche.getId(), vise.getId(), nature, session, ligne.getId())) {
            throw new DoublonLigneException(String.format(
                    "%s %s (compte %s) figure deja sur la journee du %s en %s / %s. "
                            + "Une meme prestation ne peut pas etre saisie deux fois.",
                    vise.getNom(), vise.getPrenom(), vise.getNumCompteCourant(),
                    fiche.getDateJour(), nature, session));
        }

        // RG-15. Pas d'exclusion de ligne a prevoir ici : la ligne revisee vit
        // dans l'etat courant, que le controle ecarte deja par construction.
        // Une modification de nature, de session ou de beneficiaire est une nouvelle
        // combinaison, qui peut tres bien avoir deja ete servie ailleurs sur la periode.
        controleDoublonService
                .etatDeLaPeriodePortantDeja(fiche, vise.getId(), nature, session)
                .ifPresent(idEtatEnConflit -> {
                    throw new DoublonInterEtatsException(CreationLigneService.messageRg15(
                            vise, fiche.getDateJour(), nature, session, idEtatEnConflit));
                });

        // RG-03, integralement rejouee pour la nouvelle combinaison.
        MontantResolu montant = resoudreMontant(nature, session, fiche.getDateJour(), enteteAutorisation);

        NatureEnum ancienneNature = ligne.getNature();
        SessionEnum ancienneSession = ligne.getSession();
        Integer ancienMontant = ligne.getMontantApplique();
        Long ancienneGrille = ligne.getIdGrille();
        Long ancienBeneficiaire = ligne.getIdBeneficiaire();

        // Toutes les regles ont ete satisfaites : on peut ecrire.
        CorrectionIdentite correction = changeDeCompte
                ? null
                : corrigerIdentiteSiDifferente(actuel, commande);
        ligne.reviser(nature, session, montant.montantFcfa(), montant.idGrille());
        ligne.rattacherA(vise.getId());

        tracerModification(ligne, ancienneNature, ancienneSession, ancienMontant, ancienneGrille,
                ancienBeneficiaire, correction, adresseIp);
        return new LigneAvecBeneficiaire(ligne, vise);
    }

    private static String ouActuel(String demande, String actuelle) {
        return demande == null ? actuelle : demande;
    }

    /**
     * Applique nom, prénom et agence demandés à la fiche du bénéficiaire s'ils
     * diffèrent. Rend l'avant et l'après pour la trace d'audit, ou {@code null}
     * si rien n'a changé.
     */
    private CorrectionIdentite corrigerIdentiteSiDifferente(Beneficiaire beneficiaire,
                                                            CommandeModificationLigne commande) {
        String nom = ouActuel(commande.nom(), beneficiaire.getNom());
        String prenom = ouActuel(commande.prenom(), beneficiaire.getPrenom());
        String agence = ouActuel(commande.codeAgence(), beneficiaire.getCodeAgence());

        if (nom.equals(beneficiaire.getNom()) && prenom.equals(beneficiaire.getPrenom())
                && agence.equals(beneficiaire.getCodeAgence())) {
            return null;
        }

        CorrectionIdentite correction = new CorrectionIdentite(beneficiaire.getId(),
                beneficiaire.getNom(), nom, beneficiaire.getPrenom(), prenom,
                beneficiaire.getCodeAgence(), agence);
        journal.info("Identite du beneficiaire {} corrigee depuis une modification de ligne.",
                beneficiaire.getId());
        beneficiaire.corrigerIdentite(nom, prenom, agence);
        return correction;
    }

    /** L'avant et l'après d'une correction de la fiche du bénéficiaire, pour l'audit. */
    private record CorrectionIdentite(Long idBeneficiaire, String nomAvant, String nomApres,
                                      String prenomAvant, String prenomApres,
                                      String agenceAvant, String agenceApres) {
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
                                    Long ancienneGrille, Long ancienBeneficiaire,
                                    CorrectionIdentite correction, String adresseIp) {
        DeltaAudit delta = DeltaAudit.nouveau()
                .contexte("idFicheJournaliere", ligne.getIdFicheJournaliere())
                .champ("nature", ancienneNature, ligne.getNature())
                .champ("session", ancienneSession, ligne.getSession())
                .champ("montantApplique", ancienMontant, ligne.getMontantApplique())
                .champ("idGrille", ancienneGrille, ligne.getIdGrille())
                .champ("idBeneficiaire", ancienBeneficiaire, ligne.getIdBeneficiaire());
        if (correction != null) {
            delta.contexte("idBeneficiaireCorrige", correction.idBeneficiaire())
                    .champ("nomBeneficiaire", correction.nomAvant(), correction.nomApres())
                    .champ("prenomBeneficiaire", correction.prenomAvant(), correction.prenomApres())
                    .champ("codeAgenceBeneficiaire", correction.agenceAvant(), correction.agenceApres());
        }
        publicateurAudit.publier(EvenementAudit.de(
                null,
                "MODIFICATION_LIGNE_PRESTATION",
                "ligne_prestation",
                ligne.getId(),
                adresseIp,
                delta.enJson()));
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
