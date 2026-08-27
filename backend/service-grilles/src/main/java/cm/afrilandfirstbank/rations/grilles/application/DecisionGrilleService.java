package cm.afrilandfirstbank.rations.grilles.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.grilles.domaine.GrilleTarifaire;
import cm.afrilandfirstbank.rations.grilles.domaine.StatutGrilleEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.TransitionGrille;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.GrilleIntrouvableException;
import cm.afrilandfirstbank.rations.grilles.infrastructure.GrilleTarifaireRepository;
import cm.afrilandfirstbank.rations.grilles.infrastructure.identite.AuteurIdentifie;
import cm.afrilandfirstbank.rations.grilles.infrastructure.identite.ClientIdentite;

/**
 * Decision de la Directrice RH sur une grille proposee (Sprint 2.3, US-14, RG-14).
 *
 * <p>Pendant de {@link GrilleService} : l'ARH propose, la DRH tranche. La
 * separation en deux services n'est pas cosmetique — RG-14 veut que celui qui
 * redige un tarif ne soit pas celui qui le rend applicable, et deux classes
 * distinctes rendent visible dans le code ce que le metier impose.
 *
 * <h2>La bascule</h2>
 *
 * <p>Valider n'est pas changer un statut : c'est <b>remplacer</b> la grille en
 * vigueur. Deux lignes bougent, l'ancienne et la nouvelle, et l'invariant de
 * RG-14 ne tient que si elles bougent ensemble. Une fermeture reussie suivie
 * d'une activation echouee laisserait le couple (nature, session) sans aucun
 * tarif : toute saisie du Sprint 3 s'y briserait, et le defaut ne se verrait
 * qu'a ce moment-la, loin d'ici.
 *
 * <p>La transaction couvre donc, et seulement, les ecritures : chargement,
 * fermeture, activation. L'appel reseau au service Identite est place
 * <b>avant</b> son ouverture — sinon une transaction resterait ouverte pendant
 * les trois secondes de delai de lecture. La publication d'audit a lieu
 * <b>apres</b> le commit, par construction du module {@code rations-audit-commun}
 * (decision Sprint 1.3) : un incident de journal n'annule jamais une bascule
 * correcte.
 *
 * <h2>Bornage des periodes</h2>
 *
 * <p>L'ancienne grille est fermee <b>a la veille de la date de debut de la
 * remplacante</b>, jamais a la date de la decision (arbitrage du Sprint 2.3).
 * Les periodes s'enchainent ainsi sans trou ni chevauchement, et la resolution
 * du montant a une date passee (Sprint 2.4, puis rattrapage au Sprint 6bis)
 * trouve toujours exactement une grille. Fermer a la date de decision creerait
 * un trou des que la nouvelle grille prend effet plus tard : une prestation
 * tombee dans ce trou n'aurait aucun montant applicable.
 */
@Service
public class DecisionGrilleService {

    private static final String ENTITE_CIBLE = "grille_tarifaire";
    private static final String ACTION_VALIDATION = "VALIDATION_GRILLE";
    private static final String ACTION_FERMETURE = "FERMETURE_GRILLE";
    private static final String ACTION_REJET = "REJET_GRILLE";

    private final GrilleTarifaireRepository grilleTarifaireRepository;
    private final ClientIdentite clientIdentite;
    private final PublicateurAudit publicateurAudit;

    public DecisionGrilleService(GrilleTarifaireRepository grilleTarifaireRepository,
            ClientIdentite clientIdentite,
            PublicateurAudit publicateurAudit) {
        this.grilleTarifaireRepository = grilleTarifaireRepository;
        this.clientIdentite = clientIdentite;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Valide une grille en attente et la rend applicable, en fermant celle
     * qu'elle remplace.
     *
     * <p>Sequence, arretee a l'etape 1 du sous-sprint :
     * <ol>
     *   <li>identification de l'auteur ;</li>
     *   <li>chargement de la grille cible, 404 si absente ;</li>
     *   <li>refus si son statut n'est pas {@code EN_ATTENTE_DRH} ;</li>
     *   <li>recherche de la grille COURANTE du couple ({@code ACTIVE} et sans
     *       date de fin) ;</li>
     *   <li>si elle existe, fermeture a la veille de la date de debut de la
     *       cible, puis <b>vidage explicite</b> ;</li>
     *   <li>activation de la cible ;</li>
     *   <li>audit apres commit.</li>
     * </ol>
     *
     * <p><b>Pourquoi le vidage explicite entre 5 et 6.</b> L'index partiel
     * {@code ux_grille_active_par_couple} porte sur
     * {@code statut_validation = 'ACTIVE' AND date_fin IS NULL}, et PostgreSQL
     * l'evalue instruction par instruction, pas au commit. Si Hibernate ecrivait
     * l'activation de la cible avant la fermeture de l'ancienne, il existerait
     * l'espace d'une instruction deux lignes actives sans date de fin, et la
     * base refuserait une bascule pourtant legitime. L'ordre de vidage du
     * contexte de persistance n'est pas un contrat public d'Hibernate : on le
     * fixe ici plutot que d'en dependre.
     *
     * @param idGrille identifiant de la grille a valider
     * @param enteteAutorisation en-tete {@code Authorization} de la DRH, relaye tel quel
     * @param adresseIp origine de la requete, pour la trace d'audit
     * @return la grille validee et, le cas echeant, celle qui a ete fermee
     */
    @Transactional
    public ResultatValidation valider(Long idGrille, String enteteAutorisation, String adresseIp) {

        // 1. Qui tranche. Le role DRH est deja exige par la securite ; reste
        //    l'identifiant local, que le jeton Keycloak ne porte pas.
        AuteurIdentifie auteur = clientIdentite.resoudreAuteur(enteteAutorisation);
        LocalDateTime instant = LocalDateTime.now();

        // 2. Chargement.
        GrilleTarifaire cible = charger(idGrille);
        StatutGrilleEnum statutInitial = cible.getStatutValidation();

        // 3. Statut compatible. TransitionGrille refuse tout ce qui n'est pas
        //    EN_ATTENTE_DRH -> ACTIVE ; on ne redouble pas ce controle ici, sans
        //    quoi la regle vivrait a deux endroits. Mais on l'exige AVANT de
        //    toucher a l'ancienne grille : verifier apres l'avoir fermee
        //    fonctionnerait par le rollback, et ferait dependre la correction du
        //    resultat d'un mecanisme technique plutot que de l'ordre des etapes.
        TransitionGrille.exigerValidationPossible(cible);

        // 4. La grille en vigueur sur le meme couple, s'il y en a une.
        Optional<GrilleTarifaire> ancienne = grilleTarifaireRepository
                .rechercherGrilleCourante(cible.getNature(), cible.getSession())
                .filter(grille -> !grille.getId().equals(cible.getId()));

        // 5. Fermeture a la veille, puis vidage : voir la javadoc ci-dessus.
        LocalDate dateFermeture = cible.getDateDebut().minusDays(1);
        ancienne.ifPresent(grille -> {
            TransitionGrille.fermer(grille, dateFermeture);
            grilleTarifaireRepository.saveAndFlush(grille);
        });

        // 6. Activation. Une exception ici annule aussi la fermeture du point 5 :
        //    c'est toute la raison d'etre de la transaction.
        TransitionGrille.valider(cible, auteur.id(), instant, auteur.libelle());
        GrilleTarifaire validee = grilleTarifaireRepository.save(cible);

        // 7. Audit. Deux evenements quand il y a bascule : « une grille est
        //    devenue applicable » et « une grille a cesse de l'etre » sont deux
        //    faits, et le second interesse autant le controle interne que le
        //    premier.
        publierValidation(validee, statutInitial, auteur, ancienne.orElse(null), adresseIp);
        ancienne.ifPresent(grille -> publierFermeture(grille, validee, auteur, adresseIp));

        return new ResultatValidation(validee, ancienne.orElse(null));
    }

    /**
     * Rejette une grille en attente, avec motif obligatoire (RG-10).
     *
     * <p><b>Aucune ecriture sur la grille en vigueur.</b> Un rejet dit « ce tarif
     * ne s'appliquera pas », pas « il n'y a plus de tarif » : confondre rejet et
     * fermeture priverait le couple de grille active sans raison, et bloquerait
     * les saisies alors que rien n'a change. Une seule ligne bouge, donc aucune
     * question d'atomicite ici.
     *
     * <p>Le motif est exige <b>non vide</b>, pas seulement non nul : une chaine
     * d'espaces n'est pas une explication, et l'ARH ne saurait pas quoi corriger.
     * Le controle vit dans {@link TransitionGrille#rejeter}, avec la transition
     * qu'il conditionne.
     *
     * @param idGrille identifiant de la grille a rejeter
     * @param motif explication de la decision, obligatoire
     * @return la grille rejetee, au statut {@code REJETEE}
     */
    @Transactional
    public GrilleTarifaire rejeter(Long idGrille, String motif, String enteteAutorisation,
            String adresseIp) {

        AuteurIdentifie auteur = clientIdentite.resoudreAuteur(enteteAutorisation);
        LocalDateTime instant = LocalDateTime.now();

        GrilleTarifaire cible = charger(idGrille);
        StatutGrilleEnum statutInitial = cible.getStatutValidation();

        // Motif d'abord, transition ensuite : c'est l'ordre de TransitionGrille,
        // pour qu'un rejet sans motif d'une grille deja ACTIVE signale l'absence
        // de motif plutot que la transition — l'utilisateur corrige alors la
        // premiere chose qu'on lui reproche.
        TransitionGrille.rejeter(cible, motif, instant, auteur.id(), auteur.libelle());
        GrilleTarifaire rejetee = grilleTarifaireRepository.save(cible);

        publierRejet(rejetee, statutInitial, auteur, adresseIp);
        return rejetee;
    }

    private GrilleTarifaire charger(Long idGrille) {
        return grilleTarifaireRepository.findById(idGrille)
                .orElseThrow(() -> new GrilleIntrouvableException(
                        "Aucune grille tarifaire ne porte l'identifiant " + idGrille + "."));
    }

    private void publierValidation(GrilleTarifaire validee, StatutGrilleEnum statutInitial,
            AuteurIdentifie auteur, GrilleTarifaire ancienne, String adresseIp) {

        // La grille remplacee est portee dans le contexte de l'evenement de
        // validation : lire « pourquoi ce montant a change » ne doit pas obliger
        // a rapprocher deux entrees du journal.
        publicateurAudit.publier(EvenementAudit.de(
                auteur.id(), ACTION_VALIDATION, ENTITE_CIBLE, validee.getId(), adresseIp,
                DeltaAudit.nouveau()
                        .champ("statutValidation", statutInitial, validee.getStatutValidation())
                        .champ("idValidateur", null, validee.getIdValidateur())
                        .contexte("auteur", auteur.login())
                        .contexte("nature", validee.getNature())
                        .contexte("session", validee.getSession())
                        .contexte("montantFcfa", validee.getMontantFcfa())
                        .contexte("dateDebut", validee.getDateDebut())
                        .contexte("grilleRemplacee", ancienne == null ? null : ancienne.getId())
                        .enJson()));
    }

    private void publierFermeture(GrilleTarifaire ancienne, GrilleTarifaire remplacante,
            AuteurIdentifie auteur, String adresseIp) {

        publicateurAudit.publier(EvenementAudit.de(
                auteur.id(), ACTION_FERMETURE, ENTITE_CIBLE, ancienne.getId(), adresseIp,
                DeltaAudit.nouveau()
                        .champ("dateFin", null, ancienne.getDateFin())
                        .contexte("auteur", auteur.login())
                        .contexte("remplaceePar", remplacante.getId())
                        .contexte("ancienMontantFcfa", ancienne.getMontantFcfa())
                        .contexte("nouveauMontantFcfa", remplacante.getMontantFcfa())
                        .enJson()));
    }

    private void publierRejet(GrilleTarifaire rejetee, StatutGrilleEnum statutInitial,
            AuteurIdentifie auteur, String adresseIp) {

        publicateurAudit.publier(EvenementAudit.de(
                auteur.id(), ACTION_REJET, ENTITE_CIBLE, rejetee.getId(), adresseIp,
                DeltaAudit.nouveau()
                        .champ("statutValidation", statutInitial, rejetee.getStatutValidation())
                        .champ("idValidateur", null, rejetee.getIdValidateur())
                        .contexte("auteur", auteur.login())
                        .contexte("motif", rejetee.getMotifRejet())
                        .enJson()));
    }

    /**
     * Issue d'une validation : la grille devenue applicable, et celle qu'elle a
     * fermee — nulle s'il s'agissait de la premiere grille du couple.
     *
     * <p>Le service rend les deux parce que l'interface doit pouvoir dire « le
     * tarif TRANSPORT / SOIR passe de 2500 a 3000 FCFA au 1er septembre ».
     * Ne rendre que la grille validee obligerait le frontend a relire la liste
     * pour comprendre ce qui vient de changer.
     */
    public record ResultatValidation(GrilleTarifaire validee, GrilleTarifaire ancienneFermee) {
    }

}
