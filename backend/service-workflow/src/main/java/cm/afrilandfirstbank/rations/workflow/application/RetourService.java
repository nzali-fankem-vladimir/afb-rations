package cm.afrilandfirstbank.rations.workflow.application;

import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.workflow.domaine.NiveauValidation;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.MotifRetourRequisException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.RoleNonAttenduException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ServiceIdentiteIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.TransitionProcessusInterditeException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;

/**
 * Retour motive d'un etat a l'agent d'unite (US-10, US-11, CT-16, CT-20, CT-23,
 * RG-10, RG-11).
 *
 * <h2>RG-11 : le retour ramene TOUJOURS a l'agent</h2>
 *
 * <p>Un retour du directeur reseau ne revient pas au chef d'unite : il redescend
 * directement a la saisie. C'est le piege du sous-sprint — renvoyer au niveau
 * precedent semble naturel, et serait faux : le chef d'unite a deja donne son visa
 * sur une version que le directeur reseau vient de refuser, il n'a rien a en faire.
 * C'est l'agent, et lui seul, qui peut corriger des lignes.
 *
 * <p>La regle est rendue <b>structurelle</b> plutot que respectee par discipline :
 * les deux transitions d'ET01 ({@code retournerParChefUnite},
 * {@code retournerParDirecteurReseau}) menent au meme et unique statut
 * {@link StatutEnum#RETOURNE}. Il n'existe aucune autre cible a choisir, donc aucune
 * occasion de se tromper.
 *
 * <h2>RG-10 : le motif, exige a trois etages</h2>
 *
 * <ol>
 *   <li>{@code @NotBlank} sur {@code RetourRequest} — {@code 400 REQUETE_INVALIDE},
 *       la chaine d'espaces comprise ;</li>
 *   <li>{@code TransitionProcessus.retourner...} — {@code 422 MOTIF_OBLIGATOIRE} ;</li>
 *   <li>{@code EtapeWorkflow.retournerAvecMotif} — une etape {@code RETOURNEE} ne
 *       peut pas naitre sans motif.</li>
 * </ol>
 *
 * <p>Le premier protege le chemin HTTP, les deux autres protegent la regle
 * elle-meme : un retour sans motif laisserait l'agent devant un dossier refuse sans
 * rien a corriger, et RG-10 serait enfreinte sans que rien ne le signale.
 *
 * <h2>L'ordre des operations</h2>
 *
 * <pre>
 *   HORS TRANSACTION
 *     1. charger le processus                        -&gt; 404
 *     2. habilitation sur l'unite DU PROCESSUS       -&gt; 403 / 503
 *     3. le statut designe le niveau qui retourne    -&gt; 422
 *     4. profil de l'acteur (GET /identite/moi)      -&gt; 403 / 503
 *     5. le role de l'appelant tient ce niveau       -&gt; 403
 *
 *   TRANSACTION ({@link EnregistrementRetour})
 *     6. relire, revalider le statut (concurrence)
 *     7. etape du niveau, RETOURNEE, avec le motif
 *     8. transition vers RETOURNE, quel que soit le niveau
 *     9. audit (publie apres le commit)
 * </pre>
 *
 * <h2>Trois differences avec la validation</h2>
 *
 * <p><b>Aucune signature</b> (RG-09 ne vaut que pour les validations : le valideur
 * refuse d'engager la banque, il ne l'engage pas), donc <b>aucune ecriture
 * disque</b> — le document reste intact, et il sera regenere a la resoumission
 * puisque les montants auront change. <b>Aucun aiguillage</b> non plus : le seuil ne
 * commande que le choix d'un echelon superieur, et un retour n'en cherche aucun.
 *
 * <p><b>Aucun controle de separation des taches</b> : RG-12 interdit de <i>valider</i>
 * un dossier qu'on a soutenu, pas de le refuser. Le refus n'engage pas la banque, il
 * l'en empeche — le risque que RG-12 previent n'existe pas ici. Un chef d'unite qui
 * aurait soumis puis retourne son propre etat ne ferait que le renvoyer en saisie,
 * ce qui ne fait avancer aucun paiement.
 *
 * <h2>La reprise n'est pas ici</h2>
 *
 * <p>Le dossier reste visiblement {@link StatutEnum#RETOURNE} : c'est ce qui permet a
 * l'agent de le reconnaitre dans sa liste. Il peut deja corriger ses lignes, le
 * service Saisie tenant {@code RETOURNE} pour modifiable (decision Sprint 3.3). La
 * transition {@code RETOURNE -> EN_COURS_SAISIE} est portee par la resoumission, dans
 * {@code SoumissionService} : aucun endpoint de reprise n'est ajoute, le contrat d'API
 * en compte six et pas un de plus.
 */
@Service
public class RetourService {

    private final ProcessusMensuelRepository processusMensuelRepository;
    private final HabilitationService habilitationService;
    private final ProfilClient profilClient;
    private final EnregistrementRetour enregistrementRetour;

    public RetourService(ProcessusMensuelRepository processusMensuelRepository,
            HabilitationService habilitationService,
            ProfilClient profilClient,
            EnregistrementRetour enregistrementRetour) {
        this.processusMensuelRepository = processusMensuelRepository;
        this.habilitationService = habilitationService;
        this.profilClient = profilClient;
        this.enregistrementRetour = enregistrementRetour;
    }

    /**
     * Retourne l'etat a l'agent, avec motif.
     *
     * <p><b>Non transactionnelle</b>, comme la soumission et la validation : deux
     * appels reseau s'y enchainent, et une transaction ouverte autour immobiliserait
     * une connexion a la base pendant toute leur duree. Le seul bloc transactionnel
     * est {@link EnregistrementRetour}.
     *
     * @param motif ce que l'agent doit corriger, exige non vide (RG-10)
     * @param enteteAutorisation en-tete {@code Authorization} du valideur, relaye tel
     *        quel aux appels sortants (doctrine Sprint 1.3)
     */
    public ResultatRetour retourner(Long idProcessus, String motif, String enteteAutorisation,
            String adresseIp) {

        // 0. RG-10 avant tout le reste. Un motif vide se refuse sans deranger le
        //    service Identite : c'est une faute de la requete, pas du dossier.
        exigerMotif(motif);

        // 1. Le processus existe ?
        ProcessusMensuel processus = processusMensuelRepository.findById(idProcessus)
                .orElseThrow(() -> new ProcessusIntrouvableException(idProcessus));

        // 2. Portee d'acces, sur l'unite DU PROCESSUS et non sur un parametre.
        habilitationService.exigerHabilitationSurUnite(processus.getCodeUnite(), enteteAutorisation);

        // 3. Le dossier designe le niveau qui peut le retourner : le meme que celui
        //    qui pourrait le valider. Retourner et valider sont les deux issues d'un
        //    meme geste, offertes au meme acteur au meme moment.
        NiveauValidation niveau = niveauAttenduOuRefus(processus);

        // 4. L'acteur. Appel impose par etape_workflow.id_acteur, NOT NULL.
        ActeurSignataire acteur = profilOuRefus(enteteAutorisation);

        // 5. Le role de l'appelant est-il celui que ce niveau exige ?
        exigerRoleDuNiveau(processus, niveau, acteur);

        // 6 a 9. Aucune ecriture disque : on peut ecrire en base directement.
        return enregistrementRetour.enregistrer(idProcessus, niveau, acteur, motif, adresseIp);
    }

    // --- Regles -----------------------------------------------------------------

    /**
     * RG-10. Le controle porte sur le <b>contenu utile</b> : {@code isBlank} refuse la
     * chaine vide et la chaine d'espaces, la que {@code isEmpty} aurait laisse passer
     * {@code "   "} — un champ present et un motif absent.
     */
    private void exigerMotif(String motif) {
        if (motif == null || motif.isBlank()) {
            throw new MotifRetourRequisException(
                    "Le retour d'un etat a l'agent exige un motif (RG-10) : sans explication, "
                            + "l'agent ne sait pas ce qu'il doit corriger. Une suite d'espaces "
                            + "n'est pas un motif.");
        }
    }

    /**
     * Le statut du dossier designe le niveau qui peut le retourner — ou aucun, et le
     * retour est refuse.
     *
     * <p>Meme lecture qu'a la validation : c'est le dossier qui commande, pas le role
     * de l'appelant (voir {@link NiveauValidation}). Le message nomme le statut reel
     * et l'action attendue.
     */
    private NiveauValidation niveauAttenduOuRefus(ProcessusMensuel processus) {
        return NiveauValidation.attenduPour(processus.getStatut())
                .orElseThrow(() -> new TransitionProcessusInterditeException(
                        "L'etat " + libellePeriode(processus) + " de l'unite "
                                + processus.getCodeUnite() + " ne peut pas etre retourne : son "
                                + "statut est " + processus.getStatut() + ", alors qu'un retour "
                                + "exige " + StatutEnum.EN_ATTENTE_DA + " ou "
                                + StatutEnum.EN_ATTENTE_DR + "." + suiteSelonStatut(processus)));
    }

    private String suiteSelonStatut(ProcessusMensuel processus) {
        return switch (processus.getStatut()) {
            case EN_COURS_SAISIE -> " Cet etat est encore en saisie chez l'agent : il ne vous a "
                    + "pas ete soumis, il n'y a rien a lui retourner.";
            case SOUMIS -> " La soumission de cet etat est en cours d'enregistrement ; "
                    + "reaffichez le dossier dans un instant.";
            case RETOURNE -> " Cet etat a deja ete retourne a l'agent et attend sa correction.";
            case CLOTURE -> " Cet etat est cloture, donc definitif : il ne peut plus etre "
                    + "renvoye en saisie. Une regularisation passe par un etat complementaire "
                    + "qui le reference, jamais par sa reouverture.";
            default -> "";
        };
    }

    private void exigerRoleDuNiveau(ProcessusMensuel processus, NiveauValidation niveau,
            ActeurSignataire acteur) {

        if (niveau.estTenuPar(acteur.role())) {
            return;
        }

        throw new RoleNonAttenduException(
                "L'etat " + libellePeriode(processus) + " de l'unite " + processus.getCodeUnite()
                        + " est entre les mains du " + niveau.libelle() + " (role "
                        + niveau.roleRequis() + "), et votre profil porte le role "
                        + acteur.role() + ". Ce dossier ne vous revient pas a ce stade du "
                        + "circuit : vous ne pouvez ni le valider ni le retourner.");
    }

    private ActeurSignataire profilOuRefus(String enteteAutorisation) {
        ResultatProfil resultat = profilClient.obtenir(enteteAutorisation);

        return switch (resultat) {
            case ResultatProfil.ProfilObtenu obtenu -> obtenu.acteur();
            case ResultatProfil.ProfilAbsent refus -> throw new AgentNonHabiliteException(
                    "Le retour d'un etat exige un profil ouvert dans le module : " + refus.motif()
                            + ". Rapprochez-vous de l'administrateur du module.");
            case ResultatProfil.ServiceIdentiteIndisponible panne ->
                    throw new ServiceIdentiteIndisponibleException(
                            "Le service Identite est momentanement indisponible : le retour est "
                                    + "refuse par precaution (" + panne.motifTechnique()
                                    + "). Le dossier n'a pas bouge. Reessayez dans un instant.");
        };
    }

    private String libellePeriode(ProcessusMensuel processus) {
        return String.format("%02d/%d", processus.getMoisPaiement(), processus.getAnneePaiement());
    }

}
