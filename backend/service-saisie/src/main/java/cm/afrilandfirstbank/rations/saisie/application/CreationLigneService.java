package cm.afrilandfirstbank.rations.saisie.application;

import java.time.LocalDate;

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
import cm.afrilandfirstbank.rations.saisie.domaine.exception.DoublonLigneException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.FicheIntrouvableException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.GrilleIndisponibleException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.ServiceGrillesIndisponibleException;
import cm.afrilandfirstbank.rations.saisie.infrastructure.FicheJournaliereRepository;
import cm.afrilandfirstbank.rations.saisie.infrastructure.LignePrestationRepository;

/**
 * Ajoute une ligne de prestation a une fiche journaliere, en appliquant les deux
 * regles qui font la fiabilite de la saisie : <b>RG-04</b> (pas deux fois le
 * meme beneficiaire sur la meme journee, meme nature, meme session) et
 * <b>RG-03</b> (montant repris de la grille active, jamais saisi).
 *
 * <h2>L'ordre des etapes n'est pas indifferent</h2>
 *
 * <ol>
 *   <li><b>Charger la fiche.</b> Elle porte la journee de la prestation, dont
 *       depend tout le reste : la portee de RG-04 et la date interrogee pour le
 *       tarif.</li>
 *   <li><b>Resoudre le beneficiaire</b> (Sprint 3.1). Necessaire avant RG-04 :
 *       le controle porte sur un identifiant, pas sur un nom.</li>
 *   <li><b>Verifier le doublon (RG-04).</b> <b>Avant</b> l'appel reseau : une
 *       ligne qui sera refusee de toute facon ne doit pas couter un aller-retour
 *       au service Grilles. L'ordre inverse fonctionnerait, mais consommerait un
 *       appel pour rien sur exactement le chemin le plus frequent — un agent qui
 *       ressaisit par megarde une ligne deja passee.</li>
 *   <li><b>Resoudre le montant (RG-03).</b> A la date de la fiche, jamais a la
 *       date du jour.</li>
 *   <li><b>Ecrire la ligne</b> avec le montant et la grille recus.</li>
 *   <li><b>Tracer</b> dans le journal d'audit.</li>
 * </ol>
 *
 * <h2>Trois refus, trois messages</h2>
 *
 * <p>Le doublon ({@code 409}), l'absence de tarif ({@code 422}) et la panne du
 * service Grilles ({@code 503}) produisent trois messages distincts, parce
 * qu'ils appellent trois actions differentes de l'agent : corriger sa saisie,
 * attendre qu'une grille soit validee, reessayer plus tard. Les confondre
 * enverrait l'agent dans la mauvaise direction.
 *
 * <h2>Le montant ne vient jamais de l'exterieur</h2>
 *
 * <p>{@link CommandeCreationLigne} n'a pas de champ montant : il n'y a rien a
 * ignorer, donc rien a oublier d'ignorer. Le seul montant qui atteint
 * {@link LignePrestation} est celui porte par {@link MontantResolu}, et ce cas
 * est le seul des trois a en porter un.
 *
 * <h2>Ce que ce service ne fait PAS encore</h2>
 *
 * <p>Le Sprint 3.1 a acte que le statut du processus mensuel serait verifie
 * aupres du service Workflow <b>a chaque ecriture de ligne</b>, en refus
 * conservateur ({@code docs/rattachement-processus.md} §4). Ce controle n'est
 * pas ici : le service Workflow n'existe pas avant le Sprint 4, et le present
 * sous-sprint porte RG-03 et RG-04. <b>Rien n'empeche donc aujourd'hui d'ecrire
 * une ligne dans une fiche dont le processus est deja soumis</b> — a lever au
 * Sprint 3.3 (endpoint) et au Sprint 4 (integration reelle).
 */
@Service
public class CreationLigneService {

    private static final Logger journal = LoggerFactory.getLogger(CreationLigneService.class);

    private final FicheJournaliereRepository ficheJournaliereRepository;
    private final LignePrestationRepository lignePrestationRepository;
    private final ResolutionBeneficiaireService resolutionBeneficiaireService;
    private final ControleDoublonService controleDoublonService;
    private final ResolutionMontantClient resolutionMontantClient;
    private final PublicateurAudit publicateurAudit;

    public CreationLigneService(FicheJournaliereRepository ficheJournaliereRepository,
                                LignePrestationRepository lignePrestationRepository,
                                ResolutionBeneficiaireService resolutionBeneficiaireService,
                                ControleDoublonService controleDoublonService,
                                ResolutionMontantClient resolutionMontantClient,
                                PublicateurAudit publicateurAudit) {
        this.ficheJournaliereRepository = ficheJournaliereRepository;
        this.lignePrestationRepository = lignePrestationRepository;
        this.resolutionBeneficiaireService = resolutionBeneficiaireService;
        this.controleDoublonService = controleDoublonService;
        this.resolutionMontantClient = resolutionMontantClient;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Cree la ligne, ou la refuse.
     *
     * @param commande ce que l'agent a fourni — sans montant, par construction
     * @param enteteAutorisation en-tete {@code Authorization} de l'agent, relaye
     *        tel quel au service Grilles (decision Sprint 1.3)
     * @return la ligne enregistree, montant figé depuis la grille
     * @throws FicheIntrouvableException fiche inexistante ({@code 404})
     * @throws DoublonLigneException RG-04 violee ({@code 409 DOUBLON_LIGNE})
     * @throws GrilleIndisponibleException aucun tarif a cette date
     *         ({@code 422 GRILLE_INDISPONIBLE}) — refus metier
     * @throws ServiceGrillesIndisponibleException service Grilles muet
     *         ({@code 503}) — refus technique
     */
    @Transactional
    public LignePrestation creer(CommandeCreationLigne commande, String enteteAutorisation) {
        return creer(commande, enteteAutorisation, null);
    }

    /**
     * Même opération, avec l'adresse d'origine de la requête pour la trace
     * d'audit (Sprint 3.3 : {@code SaisieController} la connaît, l'appel de base
     * ci-dessus continue de servir les tests du Sprint 3.2, écrits avant que ce
     * service ait un contexte HTTP).
     *
     * @param adresseIp origine de la requête, ou {@code null} si inconnue de
     *        l'appelant
     */
    @Transactional
    public LignePrestation creer(CommandeCreationLigne commande, String enteteAutorisation,
                                 String adresseIp) {

        // 1. La fiche porte la journee : sans elle, ni RG-04 ni RG-03 ne sont
        //    evaluables. Aucun repli sur la date du jour.
        FicheJournaliere fiche = ficheJournaliereRepository.findById(commande.idFicheJournaliere())
                .orElseThrow(() -> new FicheIntrouvableException(
                        "Aucune fiche journaliere ne porte l'identifiant " + commande.idFicheJournaliere()
                                + "."));
        LocalDate journee = fiche.getDateJour();

        // 2. Le beneficiaire est identifie par son compte courant (Sprint 3.1).
        //    Il doit exister avant le controle de doublon, qui compare des
        //    identifiants et non des noms.
        Beneficiaire beneficiaire = resolutionBeneficiaireService.resoudre(
                commande.beneficiaire().nom(),
                commande.beneficiaire().prenom(),
                commande.beneficiaire().numCompteCourant(),
                commande.beneficiaire().codeAgence(),
                adresseIp);

        // 3. RG-04, avant tout appel reseau.
        if (controleDoublonService.estDoublonSurLaJournee(
                fiche.getId(), beneficiaire.getId(), commande.nature(), commande.session())) {
            throw new DoublonLigneException(String.format(
                    "%s %s (compte %s) figure deja sur la journee du %s en %s / %s. "
                            + "Une meme prestation ne peut pas etre saisie deux fois.",
                    beneficiaire.getNom(), beneficiaire.getPrenom(), beneficiaire.getNumCompteCourant(),
                    journee, commande.nature(), commande.session()));
        }

        // 4. RG-03. A la date de la PRESTATION, jamais LocalDate.now().
        MontantResolu montant = resoudreMontant(commande, journee, enteteAutorisation);

        // 5. Le montant et la grille viennent du service Grilles, et de nulle
        //    part ailleurs.
        LignePrestation ligne = lignePrestationRepository.save(new LignePrestation(
                fiche.getId(), beneficiaire.getId(), commande.nature(), commande.session(),
                montant.montantFcfa(), montant.idGrille()));

        tracer(ligne, beneficiaire, journee, montant, adresseIp);
        return ligne;
    }

    /**
     * Traduit les trois issues de la resolution en un montant ou en un refus.
     *
     * <p>Le {@code switch} est exhaustif sans {@code default} : si un quatrieme
     * cas apparaissait un jour dans {@link ResultatResolutionMontant}, ce code ne
     * compilerait plus — plutot que de le laisser tomber silencieusement dans une
     * branche fourre-tout, sur la regle qui protege du paiement errone.
     */
    private MontantResolu resoudreMontant(CommandeCreationLigne commande, LocalDate journee,
                                          String enteteAutorisation) {
        ResultatResolutionMontant resultat = resolutionMontantClient.resoudre(
                commande.nature(), commande.session(), journee, enteteAutorisation);

        return switch (resultat) {

            case MontantResolu montant -> montant;

            // Refus METIER : le service a repondu qu'il n'y a pas de tarif.
            // Reessayer n'y changera rien ; il faut qu'une grille soit proposee
            // par l'ARH puis validee par la DRH.
            case AucuneGrilleApplicable indisponible -> throw new GrilleIndisponibleException(String.format(
                    "Aucune grille tarifaire n'est en vigueur pour %s / %s au %s. "
                            + "La ligne ne peut pas etre valorisee tant qu'une grille n'a pas ete "
                            + "proposee par l'analyste RH et validee par la directrice RH.",
                    indisponible.nature(), indisponible.session(), indisponible.date()));

            // Refus TECHNIQUE : le service n'a rien repondu d'exploitable. La
            // cause technique reste dans le journal ; l'agent, lui, n'apprendrait
            // rien d'utile d'une adresse de service.
            case ServiceGrillesIndisponible panne -> {
                journal.warn("Ligne refusee : resolution du montant impossible pour {} / {} au {} ({}).",
                        commande.nature(), commande.session(), journee, panne.motifTechnique());
                throw new ServiceGrillesIndisponibleException(
                        "Le service des grilles tarifaires est momentanement indisponible : "
                                + "le montant applicable n'a pas pu etre determine. "
                                + "Aucune ligne n'a ete enregistree, reessayez dans un instant.");
            }
        };
    }

    /**
     * Publie la trace de creation. Le contrat de {@link PublicateurAudit} garantit
     * que cet appel ne leve jamais et n'attend rien : l'audit ne fait jamais
     * echouer le metier (CLAUDE.md section 9.2).
     *
     * <p><b>{@code idUtilisateur} reste nul.</b> {@code GET /identite/habilitation}
     * — la seule verification d'habilitation que ce service appelle — ne rend
     * qu'un {@code login}, jamais l'identifiant numerique local
     * ({@code docs/appel-habilitation.md} section 1). Le resoudre exigerait un
     * quatrieme appel synchrone (vers {@code GET /identite/moi}) sur un chemin
     * qui en empile deja trois, pour un seul besoin d'audit — cout juge excessif
     * ici, a la difference du service Grilles ou cet appel a deja lieu pour
     * remplir {@code id_createur} (colonne {@code NOT NULL}, Sprint 2.2).
     * {@code ligne_prestation} ne porte d'ailleurs aucune colonne d'auteur au
     * dictionnaire (CLAUDE.md section 4).
     *
     * <p>{@code adresseIp} est desormais renseignee (Sprint 3.3), quand
     * l'appelant la connait.
     *
     * <p>Le delta porte la <b>grille</b> autant que le montant : c'est ce qui
     * permet de justifier a posteriori un montant conteste, en nommant la ligne
     * tarifaire qui l'a produit.
     */
    private void tracer(LignePrestation ligne, Beneficiaire beneficiaire, LocalDate journee,
                        MontantResolu montant, String adresseIp) {
        publicateurAudit.publier(EvenementAudit.de(
                null,
                "CREATION_LIGNE_PRESTATION",
                "ligne_prestation",
                ligne.getId(),
                adresseIp,
                DeltaAudit.nouveau()
                        .contexte("idFicheJournaliere", ligne.getIdFicheJournaliere())
                        .contexte("journee", journee)
                        .contexte("idBeneficiaire", beneficiaire.getId())
                        .contexte("numCompteCourant", beneficiaire.getNumCompteCourant())
                        .contexte("nature", ligne.getNature())
                        .contexte("session", ligne.getSession())
                        .contexte("montantApplique", montant.montantFcfa())
                        .contexte("idGrille", montant.idGrille())
                        .contexte("origineMontant", "grille active resolue au " + journee)
                        .enJson()));
    }

}
