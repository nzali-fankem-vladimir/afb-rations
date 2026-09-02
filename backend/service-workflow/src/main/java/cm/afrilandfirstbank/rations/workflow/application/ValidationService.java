package cm.afrilandfirstbank.rations.workflow.application;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.workflow.domaine.NiveauValidation;
import cm.afrilandfirstbank.rations.workflow.domaine.PieceJointe;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.DocumentNonProduitException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.RoleNonAttenduException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ServiceIdentiteIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.SeuilIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.TransitionProcessusInterditeException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.PieceJointeRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;

/**
 * Validation d'un etat, aux deux niveaux du circuit (US-08, US-10, CT-14, CT-15,
 * CT-19, RG-07, RG-08, RG-09, RG-12).
 *
 * <h2>Le statut designe le niveau, le role est verifie contre lui</h2>
 *
 * <p>Un seul endpoint sert les deux visas (contrat d'API section 5). Le niveau
 * traite se lit sur le statut du dossier via {@link NiveauValidation#attenduPour},
 * puis le role de l'appelant est confronte a ce niveau. Le raisonnement complet est
 * dans {@link NiveauValidation} : en resume, le statut est detenu par le service et
 * ne peut pas etre change de l'exterieur, alors que le role vient du profil local
 * qu'un administrateur peut modifier entre deux gestes — et RG-07 decrit le
 * parcours du dossier, pas la qualite de qui le regarde.
 *
 * <h2>L'ordre des operations, comme a la soumission</h2>
 *
 * <pre>
 *   HORS TRANSACTION
 *     1. charger le processus                        -&gt; 404
 *     2. habilitation sur l'unite DU PROCESSUS       -&gt; 403 / 503
 *     3. le statut designe le niveau attendu         -&gt; 422
 *     4. la piece jointe existe                      -&gt; 500
 *     5. profil du valideur (GET /identite/moi)      -&gt; 403 / 503
 *     6. le role de l'appelant tient ce niveau       -&gt; 403
 *     7. separation des taches RG-12                 -&gt; 403
 *     8. AIGUILLAGE, au premier niveau seulement     -&gt; 500
 *     9. ESTAMPER, ECRIRE, CONFIRMER                 -&gt; 500
 *
 *   TRANSACTION ({@link EnregistrementValidation})
 *    10. relire, revalider le statut et le montant (concurrence)
 *    11. etape VALIDATION_DA ou VALIDATION_DR, VALIDEE, signee
 *    12. piece jointe : une signature de plus
 *    13. transition : aiguillage au premier niveau, cloture au second
 *    14. audit (publie apres le commit)
 *
 *   APRES LA TRANSACTION
 *    15. si l'etat est desormais CLOTURE : transmission comptable, sur pool dedie
 * </pre>
 *
 * <h2>Pas d'aiguillage au second niveau</h2>
 *
 * <p>Apres le visa du directeur reseau, il n'y a plus d'echelon : la cloture est
 * directe. {@link NiveauValidation#declencheAiguillage()} porte cette difference, et
 * l'aiguillage n'est meme pas appele — le seuil n'est donc pas lu. Le rappeler « par
 * symetrie » ferait comparer un montant a un seuil pour choisir entre deux issues
 * dont une seule existe, et un parametre illisible bloquerait une validation qui
 * n'en depend pas.
 *
 * <h2>Pourquoi l'aiguillage precede l'ecriture du document</h2>
 *
 * <p>Parce qu'un seuil illisible doit arreter la validation <b>avant</b> qu'une
 * signature ne soit gravee dans le PDF. Dans l'ordre inverse, un parametre absent
 * laisserait un document estampe d'un visa que la base ne connaitrait jamais : la
 * piece archivee affirmerait une validation qui n'a pas eu lieu. C'est le meme
 * raisonnement qu'au Sprint 4.2 pour la completude — tous les refus possibles sont
 * epuises avant la premiere ecriture.
 *
 * <h2>Le montant n'est jamais recalcule ici</h2>
 *
 * <p>{@link AiguillageService} compare le montant <b>enregistre a la soumission</b>.
 * Redemander l'etat consolide au service Saisie ferait dependre l'aiguillage d'une
 * grille tarifaire modifiee entretemps, et le chef d'unite validerait un montant
 * different de celui qu'il a lu et signe.
 *
 * <h2>La cloture transmet, mais apres le commit, et sans jamais le remettre en cause</h2>
 *
 * <p>Depuis le Sprint 5.1, une cloture declenche la mise a disposition de l'etat vers la
 * comptabilite. Le declenchement a lieu <b>hors et apres</b> la transaction
 * ({@link DeclenchementTransmission}), aux <b>deux</b> points ou la cloture survient : la
 * validation du chef d'unite sous le seuil, et celle du directeur reseau. Ces deux points
 * sont couverts par une seule ligne de code, qui teste le statut atteint — les separer en
 * deux appels ouvrirait la possibilite d'en oublier un.
 *
 * <p><b>Un echec de transmission n'annule jamais la validation.</b> Le document porte deja
 * le visa, ecrit sur disque hors transaction (Sprint 4.2), et faire dependre une validation
 * de la banque de la disponibilite de Kafka serait absurde. L'echec est signale : journal au
 * prefixe {@code TRANSMISSION MANQUEE}, evenement d'audit, et champ {@code transmission}
 * dans la reponse — le seul moment ou un humain l'apprend, aucune reprise automatique
 * n'etant possible faute de compte de service au realm.
 *
 * <p>{@code transmis_comptabilite} n'est pose qu'apres accuse du broker, dans sa propre
 * transaction ({@link EnregistrementTransmission}). Le poser a la cloture contournerait le
 * verrou de RG-13 : l'etat paraitrait transmis avant de l'etre, et la transmission reelle
 * serait ensuite refusee comme un doublon.
 *
 * <h2>La separation des taches</h2>
 *
 * <p>{@link SeparationTachesService} est appele en 7, apres l'obtention du profil —
 * son identifiant local est necessaire — et <b>avant</b> l'aiguillage et toute
 * ecriture. Le refus qu'il produit est un {@code 403 SEPARATION_TACHES}, distinct
 * des deux autres refus en {@code 403} : celui qu'il vise a le bon role et la bonne
 * portee, il a seulement deja agi sur cette version du dossier.
 */
@Service
public class ValidationService {

    private final ProcessusMensuelRepository processusMensuelRepository;
    private final PieceJointeRepository pieceJointeRepository;
    private final HabilitationService habilitationService;
    private final ProfilClient profilClient;
    private final SeparationTachesService separationTachesService;
    private final AiguillageService aiguillageService;
    private final SignatureService signatureService;
    private final EnregistrementValidation enregistrementValidation;
    private final DeclenchementTransmission declenchementTransmission;

    public ValidationService(ProcessusMensuelRepository processusMensuelRepository,
            PieceJointeRepository pieceJointeRepository,
            HabilitationService habilitationService,
            ProfilClient profilClient,
            SeparationTachesService separationTachesService,
            AiguillageService aiguillageService,
            SignatureService signatureService,
            EnregistrementValidation enregistrementValidation,
            DeclenchementTransmission declenchementTransmission) {
        this.processusMensuelRepository = processusMensuelRepository;
        this.pieceJointeRepository = pieceJointeRepository;
        this.habilitationService = habilitationService;
        this.profilClient = profilClient;
        this.separationTachesService = separationTachesService;
        this.aiguillageService = aiguillageService;
        this.signatureService = signatureService;
        this.enregistrementValidation = enregistrementValidation;
        this.declenchementTransmission = declenchementTransmission;
    }

    /**
     * Valide l'etat au niveau que son statut designe, et applique l'aiguillage RG-08
     * si ce niveau est le premier.
     *
     * <p><b>Non transactionnelle</b>, comme la soumission : deux appels reseau et une
     * ecriture disque s'y enchainent, et une transaction ouverte autour
     * immobiliserait une connexion a la base pendant toute leur duree (doctrines des
     * Sprints 2.3, 3.4, 4.1 et 4.2). Le seul bloc transactionnel est
     * {@link EnregistrementValidation}.
     *
     * @param idProcessus l'etat a valider
     * @param enteteAutorisation en-tete {@code Authorization} du valideur, relaye tel
     *        quel aux appels sortants (doctrine Sprint 1.3)
     * @param adresseIp origine de la requete, pour la trace d'audit
     * @throws SeuilIndisponibleException si le seuil RG-08 n'est pas lisible au
     *         premier niveau : la validation est refusee avant toute ecriture
     */
    public ResultatValidation valider(Long idProcessus, String enteteAutorisation,
            String adresseIp) {

        // 1. Le processus existe ? Question posee avant toute autre : un identifiant
        //    inconnu se refuse en 404 sans interroger le service Identite.
        ProcessusMensuel processus = processusMensuelRepository.findById(idProcessus)
                .orElseThrow(() -> new ProcessusIntrouvableException(idProcessus));

        // 2. Portee d'acces, sur l'unite DU PROCESSUS et non sur un parametre. Le role
        //    est deja filtre par le controleur ; il reste a savoir si CE valideur a
        //    portee sur CETTE unite (RG-12).
        habilitationService.exigerHabilitationSurUnite(processus.getCodeUnite(), enteteAutorisation);

        // 3. Le dossier designe le niveau qu'il attend. C'est lui qui commande.
        NiveauValidation niveau = niveauAttenduOuRefus(processus);

        // 4. Le document existe : on l'enrichit, on ne le recree jamais.
        PieceJointe pieceJointe = pieceJointeOuRefus(processus);

        // 5. Le valideur. Appel impose par etape_workflow.id_acteur, NOT NULL.
        ActeurSignataire acteur = profilOuRefus(enteteAutorisation);

        // 6. Le role de l'appelant est-il celui que ce niveau exige ? Lu sur le profil
        //    local, qui fait autorite sur le role applicatif (CLAUDE.md section 10),
        //    et non sur une revendication du jeton.
        exigerRoleDuNiveau(processus, niveau, acteur);

        // 7. RG-12. A sa place exacte : le profil du valideur vient d'etre obtenu,
        //    donc son identifiant local est connu, et rien n'a encore ete ecrit ni
        //    sur le disque ni en base. Ordre du document maitre section 7.3 :
        //    habilitation, regles, puis modification — jamais l'inverse.
        separationTachesService.exigerSeparationDesTaches(idProcessus, acteur);

        // 8. RG-08, AU PREMIER NIVEAU SEULEMENT. Place avant toute ecriture : un seuil
        //    illisible doit arreter la validation avant qu'une signature ne soit
        //    gravee dans le document. Au second niveau, le seuil n'est pas lu du tout.
        ResultatAiguillage aiguillage = niveau.declencheAiguillage()
                ? aiguillageService.aiguiller(processus)
                : null;

        // 9. Un seul horodatage pour tout l'acte, comme a la soumission : la mention
        //    imprimee et la ligne etape_workflow portent le meme instant.
        LocalDateTime horodatage = LocalDateTime.now();

        ResultatSignature signature = signatureService.enrichirEtSigner(
                pieceJointe, acteur, niveau.nomEtape(), horodatage);

        // 10 a 14. Le document porte la signature et son ecriture est confirmee : on
        //          peut ecrire en base.
        ResultatValidation resultat = enregistrementValidation.enregistrer(
                idProcessus, niveau, acteur, signature, aiguillage, adresseIp);

        // 15. La cloture est commitee. Si elle a eu lieu, l'etat part en comptabilite.
        return transmettreSiCloture(resultat, enteteAutorisation, adresseIp);
    }

    /**
     * Declenche la transmission quand, et seulement quand, la validation a cloture l'etat.
     *
     * <h2>Un seul point de branchement pour les deux clotures</h2>
     *
     * <p>Le circuit cloture a deux endroits : la validation du chef d'unite quand le montant
     * est sous le seuil (RG-08), et celle du directeur reseau, qui est terminale. Plutot que
     * de brancher la transmission a ces deux endroits, on la branche sur ce qui leur est
     * commun et qui les <b>definit</b> : le statut atteint vaut {@link StatutEnum#CLOTURE}.
     *
     * <p>Deux appels separes se seraient ressembles a s'y meprendre, et il aurait suffi d'en
     * oublier un pour qu'une moitie des etats de la banque ne parte jamais en paiement, sans
     * aucune erreur visible. Ici, il n'y a rien a oublier : tout etat qui atteint
     * {@code CLOTURE} passe par cette ligne, y compris par une troisieme voie de cloture
     * qu'un sprint ulterieur ajouterait.
     *
     * <p><b>Rien n'est tente sur un etat aiguille vers le directeur reseau</b> : il n'est pas
     * cloture, il n'a rien a transmettre, et le champ {@code transmission} reste nul.
     */
    private ResultatValidation transmettreSiCloture(ResultatValidation resultat,
            String enteteAutorisation, String adresseIp) {

        if (resultat.processus().getStatut() != StatutEnum.CLOTURE) {
            return resultat;
        }

        return resultat.avecTransmission(declenchementTransmission.transmettre(
                resultat.processus().getId(),
                resultat.processus().getCodeUnite(),
                enteteAutorisation,
                adresseIp));
    }

    // --- Regles -----------------------------------------------------------------

    /**
     * Le statut du dossier designe le niveau attendu — ou aucun, et la validation est
     * refusee (RG-07 : aucun saut de niveau).
     *
     * <p>Le message nomme le statut reel <b>et l'action attendue</b>, comme au Sprint
     * 3.3 cote Saisie : un « validation impossible » sec laisserait le valideur sans
     * recours, devant un dossier qu'il croit lui appartenir.
     *
     * <p>Le controle est fait ici <b>et</b> revalide dans la transaction : entre les
     * deux, deux appels reseau et une ecriture disque laissent le temps a une seconde
     * requete de passer.
     */
    private NiveauValidation niveauAttenduOuRefus(ProcessusMensuel processus) {
        return NiveauValidation.attenduPour(processus.getStatut())
                .orElseThrow(() -> new TransitionProcessusInterditeException(
                        "L'etat " + libellePeriode(processus) + " de l'unite "
                                + processus.getCodeUnite() + " n'attend aucune validation : son "
                                + "statut est " + processus.getStatut() + ", alors qu'une "
                                + "validation exige " + StatutEnum.EN_ATTENTE_DA + " ou "
                                + StatutEnum.EN_ATTENTE_DR + "." + suiteSelonStatut(processus)));
    }

    private String suiteSelonStatut(ProcessusMensuel processus) {
        return switch (processus.getStatut()) {
            case EN_COURS_SAISIE -> " L'agent n'a pas encore soumis cet etat : il n'y a rien a "
                    + "valider tant qu'il est en saisie.";
            case SOUMIS -> " La soumission de cet etat est en cours d'enregistrement ; "
                    + "reaffichez le dossier dans un instant.";
            case RETOURNE -> " Cet etat a ete retourne a l'agent pour correction ; il vous "
                    + "reviendra apres une nouvelle soumission.";
            case CLOTURE -> " Cet etat est cloture, donc definitif. Une regularisation passe par "
                    + "un etat complementaire qui le reference, jamais par sa reouverture.";
            default -> "";
        };
    }

    /**
     * Le role de l'appelant doit etre celui que le niveau attendu exige.
     *
     * <p>Refuse en {@code 403}, avec un message qui nomme le niveau que le dossier
     * attend. Un chef d'unite devant un etat deja monte au directeur reseau ne doit
     * pas lire « votre role ne permet pas cette action » : son role permet la
     * validation, mais pas a ce stade-la du dossier.
     */
    private void exigerRoleDuNiveau(ProcessusMensuel processus, NiveauValidation niveau,
            ActeurSignataire acteur) {

        if (niveau.estTenuPar(acteur.role())) {
            return;
        }

        throw new RoleNonAttenduException(
                "L'etat " + libellePeriode(processus) + " de l'unite " + processus.getCodeUnite()
                        + " attend la validation du " + niveau.libelle() + " (role "
                        + niveau.roleRequis() + "), et votre profil porte le role "
                        + acteur.role() + ". Ce dossier ne vous revient pas a ce stade du "
                        + "circuit.");
    }

    /**
     * Le document doit exister : la validation l'<b>enrichit</b>, elle ne le cree
     * jamais.
     *
     * <p>Son absence sur un etat en attente de validation est une incoherence, pas un
     * cas metier : la soumission ne peut pas avoir abouti sans produire de piece
     * jointe. D'ou un {@code 500} — le valideur n'a rien fait de faux et n'a rien a
     * corriger — et non un refus metier qui l'enverrait chercher une faute
     * inexistante. Meme parti qu'au Sprint 2.4 pour {@code INCOHERENCE_GRILLE} : le
     * service signale, il n'improvise pas.
     */
    private PieceJointe pieceJointeOuRefus(ProcessusMensuel processus) {
        return pieceJointeRepository.findByIdProcessus(processus.getId())
                .orElseThrow(() -> new DocumentNonProduitException(
                        "Aucun document n'est enregistre pour l'etat " + libellePeriode(processus)
                                + " de l'unite " + processus.getCodeUnite() + ", alors que son "
                                + "statut est " + processus.getStatut() + ". La validation appose "
                                + "une signature sur la piece existante et ne la recree jamais : "
                                + "elle est refusee. Signalez-le a l'administrateur du module."));
    }

    private ActeurSignataire profilOuRefus(String enteteAutorisation) {
        ResultatProfil resultat = profilClient.obtenir(enteteAutorisation);

        return switch (resultat) {
            case ResultatProfil.ProfilObtenu obtenu -> obtenu.acteur();
            case ResultatProfil.ProfilAbsent refus -> throw new AgentNonHabiliteException(
                    "La validation exige un profil ouvert dans le module : " + refus.motif()
                            + ". Rapprochez-vous de l'administrateur du module.");
            case ResultatProfil.ServiceIdentiteIndisponible panne ->
                    throw new ServiceIdentiteIndisponibleException(
                            "Le service Identite est momentanement indisponible : la validation "
                                    + "est refusee par precaution (" + panne.motifTechnique()
                                    + "). Le document n'a pas ete modifie. Reessayez dans un "
                                    + "instant.");
        };
    }

    private String libellePeriode(ProcessusMensuel processus) {
        return String.format("%02d/%d", processus.getMoisPaiement(), processus.getAnneePaiement());
    }

}
