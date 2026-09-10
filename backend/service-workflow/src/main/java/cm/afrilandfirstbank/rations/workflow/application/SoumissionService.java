package cm.afrilandfirstbank.rations.workflow.application;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.workflow.domaine.PieceJointe;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionProcessus;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.EtatIncompletException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.PieceJointeExistanteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ServiceIdentiteIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ServiceSaisieIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.TransitionProcessusInterditeException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.PieceJointeRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;

/**
 * Soumission d'un etat mensuel par l'agent d'unite (US-07, CT-12, CT-13, RG-07).
 *
 * <h2>L'ordre des operations est la substance de ce service</h2>
 *
 * <p>Il suit la section 7.3 du document maitre, avec une contrainte
 * supplementaire arbitree au Sprint 4.2 : <b>l'ecriture du fichier precede
 * l'ouverture de la transaction</b>.
 *
 * <pre>
 *   HORS TRANSACTION
 *     1. charger le processus                        -&gt; 404
 *     2. habilitation sur l'unite DU PROCESSUS       -&gt; 403 / 503
 *     3. statut : EN_COURS_SAISIE uniquement         -&gt; 422
 *     4. aucune piece jointe deja generee            -&gt; 409
 *     5. etat consolide, obtenu du service Saisie    -&gt; 503
 *     6. controle de completude                      -&gt; 422 + liste des manques
 *     7. profil de l'auteur (GET /identite/moi)      -&gt; 403 / 503
 *     8. GENERER, ECRIRE, CONFIRMER, SIGNER          -&gt; 500
 *        echec ici : rien n'est ecrit en base, la transaction ne s'ouvre pas
 *
 *   TRANSACTION ({@link EnregistrementSoumission})
 *     9. relire et revalider le statut (concurrence)
 *    10. reporter le montant total
 *    11. transitions EN_COURS_SAISIE -&gt; SOUMIS -&gt; EN_ATTENTE_DA
 *    12. etape SOUMISSION_AGENT, VALIDEE, signee
 *    13. piece jointe, nombre_signatures = 1
 *    14. audit (publie apres le commit par rations-audit-commun)
 * </pre>
 *
 * <h2>Pourquoi l'ecriture du fichier vient avant la transaction</h2>
 *
 * <p>Parce que {@code piece_jointe.nombre_signatures} ne compte que des
 * signatures <b>reellement ecrites</b> (migration V3). Si l'ecriture echouait
 * apres le commit, la base affirmerait une signature absente du document. Dans
 * cet ordre, l'echec d'ecriture arrete tout : ni etape, ni montant, ni changement
 * de statut.
 *
 * <p><b>L'asymetrie inverse est assumee</b> : une transaction en echec apres une
 * ecriture reussie laisse un <b>fichier orphelin</b> sur le stockage, que rien ne
 * reference. C'est le sens voulu, et c'est la meme preference qu'en matiere
 * d'audit (Sprint 1.3) : mieux vaut une trace de trop qui ne trompe personne
 * qu'une affirmation fausse en base.
 *
 * <h2>Aucun calcul de montant, aucun arbitrage de seuil</h2>
 *
 * <p>Le total est <b>recopie</b> de l'etat consolide, jamais readdiitionne (RG-06
 * partagee, decision Sprint 3.4). Et l'aiguillage au seuil (RG-08) n'est pas ici :
 * la soumission mene toujours a {@link StatutEnum#EN_ATTENTE_DA}. Le seuil sera lu
 * au sous-sprint 4.3, apres la validation du Chef d'Unite.
 */
@Service
public class SoumissionService {

    private final ProcessusMensuelRepository processusMensuelRepository;
    private final PieceJointeRepository pieceJointeRepository;
    private final HabilitationService habilitationService;
    private final ProfilClient profilClient;
    private final ConsolidationClient consolidationClient;
    private final CompletudeService completudeService;
    private final SignatureService signatureService;
    private final EnregistrementSoumission enregistrementSoumission;

    public SoumissionService(ProcessusMensuelRepository processusMensuelRepository,
            PieceJointeRepository pieceJointeRepository,
            HabilitationService habilitationService,
            ProfilClient profilClient,
            ConsolidationClient consolidationClient,
            CompletudeService completudeService,
            SignatureService signatureService,
            EnregistrementSoumission enregistrementSoumission) {
        this.processusMensuelRepository = processusMensuelRepository;
        this.pieceJointeRepository = pieceJointeRepository;
        this.habilitationService = habilitationService;
        this.profilClient = profilClient;
        this.consolidationClient = consolidationClient;
        this.completudeService = completudeService;
        this.signatureService = signatureService;
        this.enregistrementSoumission = enregistrementSoumission;
    }

    /**
     * Soumet l'etat mensuel et le transfere au Chef d'Unite.
     *
     * <p><b>Cette methode n'est pas transactionnelle.</b> Elle enchaine trois
     * appels reseau et une ecriture disque ; une transaction ouverte autour
     * immobiliserait une connexion a la base pendant toute leur duree (doctrines
     * des Sprints 2.3, 3.4 et 4.1). L'unique bloc transactionnel est confie a
     * {@link EnregistrementSoumission}, et il ne contient que des ecritures en
     * base.
     *
     * @return les trois ecritures produites : le processus, la piece jointe et
     *         l'etape signee
     */
    public ResultatSoumission soumettre(Long idProcessus, String enteteAutorisation,
            String adresseIp) {

        // 1. Le processus existe ? Question posee avant toute autre, comme a la
        //    consultation (Sprint 4.1) : un identifiant inconnu se refuse en 404
        //    sans interroger le service Identite.
        ProcessusMensuel processus = processusMensuelRepository.findById(idProcessus)
                .orElseThrow(() -> new ProcessusIntrouvableException(idProcessus));

        // 2. Portee d'acces, sur l'unite DU PROCESSUS et non sur un parametre.
        habilitationService.exigerHabilitationSurUnite(processus.getCodeUnite(), enteteAutorisation);

        // 3. Le statut permet-il la soumission ? EN_COURS_SAISIE pour une premiere
        //    soumission, RETOURNE pour une resoumission apres correction (RG-11).
        exigerStatutSoumissible(processus);

        // 4. Le document existe-t-il deja ? Sur un etat retourne, oui : il sera
        //    regenere depuis l'etat corrige. Sur un etat en saisie, non — et sa
        //    presence serait une incoherence.
        PieceJointe pieceJointeExistante = pieceJointeExistanteOuRefus(processus);

        // 5. L'etat consolide, seule source du montant total.
        EtatConsolide etat = consoliderOuRefuser(processus, enteteAutorisation);

        // 6. Completude. En cas d'echec, le refus porte la LISTE des manques.
        exigerEtatComplet(processus, etat);

        long montantTotal = montantTotalOuRefus(processus, etat);

        // 7. L'auteur. Appel impose par etape_workflow.id_acteur, qui est NOT NULL.
        ActeurSignataire acteur = profilOuRefus(enteteAutorisation);

        // 8. Un seul horodatage pour tout l'acte : la mention imprimee sur le
        //    document et la ligne etape_workflow doivent porter le meme instant.
        //    Deux appels a now() donneraient deux valeurs proches mais differentes,
        //    et le document contredirait la base.
        LocalDateTime horodatage = LocalDateTime.now();

        // Premiere soumission : le document nait. Resoumission apres retour : il est
        // REGENERE depuis l'etat corrige, jamais enrichi — les montants ont change,
        // et le cadre du visa agent est deja occupe (voir SignatureService).
        ResultatSignature signature = pieceJointeExistante == null
                ? signatureService.creerEtSigner(processus, etat, acteur, horodatage)
                : signatureService.regenererEtSigner(processus, etat, acteur, horodatage);

        // 9 a 14. Le fichier existe et il est confirme : on peut ecrire en base.
        return enregistrementSoumission.enregistrer(
                idProcessus, montantTotal, acteur, signature, etat, adresseIp);
    }

    // --- Regles -----------------------------------------------------------------

    /**
     * Seul {@link StatutEnum#EN_COURS_SAISIE} ouvre la soumission.
     *
     * <p>Un etat {@link StatutEnum#RETOURNE} n'est pas soumissible directement :
     * ET01 impose de le <i>reprendre</i> d'abord
     * ({@link TransitionProcessus#reprendreParAgent}), ce qui le ramene en
     * {@code EN_COURS_SAISIE}. Ce geste appartient au sous-sprint 4.4 ; le message
     * le dit, pour que l'agent ne reste pas devant un refus muet.
     *
     * <p>Le controle est fait ici <b>et</b> revalide dans la transaction : entre
     * les deux, trois appels reseau et une ecriture disque laissent tout le temps
     * a une seconde requete de passer.
     */
    /**
     * Deux statuts ouvrent la soumission : {@link StatutEnum#EN_COURS_SAISIE}, la
     * premiere fois, et {@link StatutEnum#RETOURNE}, apres correction.
     *
     * <p><b>C'est la resoumission qui porte la reprise</b> (US-11, CT-24). Le contrat
     * d'API ne prevoit pas d'endpoint de reprise, et en creer un en ferait un
     * septieme. Le dossier reste donc visiblement {@code RETOURNE} tant que l'agent
     * n'a pas resoumis — c'est ce qui lui permet de le reconnaitre dans sa liste — et
     * il peut deja corriger ses lignes, le service Saisie tenant {@code RETOURNE} pour
     * modifiable (decision Sprint 3.3). La transition {@code RETOURNE ->
     * EN_COURS_SAISIE} est appliquee dans la transaction de resoumission, juste avant
     * {@code EN_COURS_SAISIE -> SOUMIS}.
     *
     * <p>La liste est fermee et positive, comme cote Saisie : elle enumere ce qui
     * autorise, non ce qui interdit. Un statut ajoute un jour ne deviendrait pas
     * soumissible par omission.
     */
    private void exigerStatutSoumissible(ProcessusMensuel processus) {
        if (processus.getStatut() == StatutEnum.EN_COURS_SAISIE
                || processus.getStatut() == StatutEnum.RETOURNE) {
            return;
        }

        String suite = switch (processus.getStatut()) {
            case SOUMIS, EN_ATTENTE_DA, EN_ATTENTE_DR -> " Il est deja dans le circuit de "
                    + "validation et suit son cours.";
            case CLOTURE -> " Un etat cloture est definitif. Pour regulariser, ouvrez un etat "
                    + "complementaire qui reference celui-ci, sans le rouvrir.";
            default -> "";
        };

        throw new TransitionProcessusInterditeException(
                "L'etat " + processus.libellePeriode() + " de l'unite " + processus.getCodeUnite()
                        + " ne peut pas etre soumis : son statut est " + processus.getStatut()
                        + ", alors que la soumission exige " + StatutEnum.EN_COURS_SAISIE
                        + " ou " + StatutEnum.RETOURNE + "." + suite);
    }

    /**
     * Le document existant, s'il y en a un — et le refus si sa presence est
     * incoherente avec le statut.
     *
     * <p>Un etat {@link StatutEnum#RETOURNE} <b>doit</b> en avoir un : il a ete soumis
     * au moins une fois. Il sera regenere depuis l'etat corrige.
     *
     * <p>Un etat {@link StatutEnum#EN_COURS_SAISIE} ne doit <b>pas</b> en avoir :
     * aucune soumission n'a abouti. Sa presence signale un etat deja soumis dont le
     * statut aurait ete change autrement que par la machine a etats — d'ou le maintien
     * du refus {@code 409 PIECE_JOINTE_EXISTANTE} du Sprint 4.2, pose ici plutot que
     * laisse a la contrainte d'unicite, qui produirait un message technique la ou
     * l'agent attend une phrase.
     */
    private PieceJointe pieceJointeExistanteOuRefus(ProcessusMensuel processus) {
        PieceJointe existante = pieceJointeRepository.findByIdProcessus(processus.getId())
                .orElse(null);

        if (existante != null && processus.getStatut() == StatutEnum.EN_COURS_SAISIE) {
            throw new PieceJointeExistanteException(
                    "Un document a deja ete genere pour l'etat " + processus.libellePeriode()
                            + " de l'unite " + processus.getCodeUnite() + ", alors que son statut "
                            + "est " + StatutEnum.EN_COURS_SAISIE + " : cet etat a donc deja ete "
                            + "soumis. Signalez-le a l'administrateur du module.");
        }

        return existante;
    }

    private EtatConsolide consoliderOuRefuser(ProcessusMensuel processus, String enteteAutorisation) {
        ResultatConsolidation resultat = consolidationClient.consolider(
                processus.getId(), processus.getCodeUnite(), enteteAutorisation);

        return switch (resultat) {
            case ResultatConsolidation.EtatObtenu obtenu -> obtenu.etat();
            case ResultatConsolidation.ServiceSaisieIndisponible panne ->
                    throw new ServiceSaisieIndisponibleException(
                            "Le service Saisie est momentanement indisponible : l'etat "
                                    + processus.libellePeriode() + " ne peut pas etre soumis ("
                                    + panne.motifTechnique() + "). Aucun montant n'est suppose ni "
                                    + "repris d'une lecture anterieure. Reessayez dans un instant.");
        };
    }

    private void exigerEtatComplet(ProcessusMensuel processus, EtatConsolide etat) {
        ResultatCompletude completude = completudeService.verifier(processus, etat);
        if (completude.estComplet()) {
            return;
        }
        throw new EtatIncompletException(
                "L'etat " + processus.libellePeriode() + " de l'unite " + processus.getCodeUnite()
                        + " ne peut pas etre soumis : " + completude.manques().size()
                        + " point(s) a corriger.",
                completude.manques());
    }

    /**
     * Le montant total, tel que le service Saisie l'a rendu.
     *
     * <p><b>Un total absent est un refus, jamais un zero.</b> Les deux
     * commanderaient le meme aiguillage au sous-sprint 4.3 (« sous le seuil »),
     * l'un a juste titre, l'autre par accident (decision Sprint 4.1 section 6).
     *
     * <p>Le debordement est verifie explicitement : {@code montant_total} est un
     * {@code INTEGER} en base, alors que le service Saisie rend un {@code long}.
     * Une conversion silencieuse rendrait negatif un total superieur a deux
     * milliards de FCFA, et ce montant negatif commanderait ensuite l'aiguillage.
     * Le cas est improbable ; sa consequence ne l'est pas.
     */
    private long montantTotalOuRefus(ProcessusMensuel processus, EtatConsolide etat) {
        Long total = etat.montantTotalFcfa();
        if (total == null) {
            throw new ServiceSaisieIndisponibleException(
                    "Le service Saisie n'a rendu aucun montant total pour l'etat "
                            + processus.libellePeriode() + " : la soumission est refusee. Un total "
                            + "absent n'est pas un total nul, et ne doit jamais etre traite comme "
                            + "tel. Reessayez dans un instant.");
        }
        if (total > Integer.MAX_VALUE) {
            throw new ServiceSaisieIndisponibleException(
                    "Le montant total rendu pour l'etat " + processus.libellePeriode() + " ("
                            + total + " FCFA) depasse la capacite de la colonne montant_total. "
                            + "La soumission est refusee plutot que d'enregistrer un montant "
                            + "tronque. Signalez-le a l'administrateur du module.");
        }
        return total;
    }

    private ActeurSignataire profilOuRefus(String enteteAutorisation) {
        ResultatProfil resultat = profilClient.obtenir(enteteAutorisation);

        return switch (resultat) {
            case ResultatProfil.ProfilObtenu obtenu -> obtenu.acteur();
            case ResultatProfil.ProfilAbsent refus -> throw new AgentNonHabiliteException(
                    "La soumission exige un profil ouvert dans le module : " + refus.motif()
                            + ". Rapprochez-vous de l'administrateur du module.");
            case ResultatProfil.ServiceIdentiteIndisponible panne ->
                    throw new ServiceIdentiteIndisponibleException(
                            "Le service Identite est momentanement indisponible : la soumission "
                                    + "est refusee par precaution (" + panne.motifTechnique()
                                    + "). Aucun document n'a ete produit. Reessayez dans un instant.");
        };
    }


}
