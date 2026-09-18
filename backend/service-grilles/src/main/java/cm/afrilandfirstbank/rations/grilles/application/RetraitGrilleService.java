package cm.afrilandfirstbank.rations.grilles.application;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.grilles.domaine.GrilleTarifaire;
import cm.afrilandfirstbank.rations.grilles.domaine.StatutGrilleEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.TransitionGrille;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.GrilleIntrouvableException;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.GrilleNonProprietaireException;
import cm.afrilandfirstbank.rations.grilles.infrastructure.GrilleTarifaireRepository;
import cm.afrilandfirstbank.rations.grilles.infrastructure.identite.AuteurIdentifie;
import cm.afrilandfirstbank.rations.grilles.infrastructure.identite.ClientIdentite;

/**
 * Retrait d'une grille en attente par son propre auteur (rattrapage post-7F.6, demande
 * n°7 de la vérification visuelle du Sprint 7F.6, {@code POST /grilles/{id}/retrait}).
 *
 * <h2>Pourquoi un retrait plutôt qu'une modification en place</h2>
 *
 * <p>Aucun endpoint de ce module ne modifie une grille déjà écrite : la
 * décision du Sprint 2.2 (« modifier une grille ACTIVE crée une nouvelle
 * ligne, jamais une mise à jour en place ») s'étend ici par le même
 * raisonnement, même si la grille visée n'est pas encore ACTIVE. Réécrire un
 * montant ou une date d'effet en place effacerait la proposition d'origine de
 * l'historique, sans qu'aucun événement d'audit n'en garde trace exacte. Le
 * retrait ferme la proposition (statut {@code REJETEE}, comme un rejet de la
 * DRH), et l'Analyste RH en soumet une nouvelle, corrigée, par le chemin déjà
 * existant ({@code POST /grilles}).
 *
 * <h2>Pourquoi le statut REJETEE, et non un cinquième statut</h2>
 *
 * <p>{@code StatutGrilleEnum} est fixé par CLAUDE.md §5 à quatre valeurs. Un
 * retrait et un rejet produisent la même conséquence observable -- « cette
 * proposition précise ne deviendra jamais active » -- et se distinguent déjà
 * dans le journal d'audit par leur action ({@code RETRAIT_GRILLE} contre
 * {@code REJET_GRILLE}) et par l'auteur de la décision, qui est ici le
 * créateur lui-même. Ajouter un statut pour ce seul écart aurait touché
 * l'énumération partagée, {@code BadgeStatutGrille} côté frontend, et tout
 * code qui commute déjà sur les quatre valeurs existantes -- un coût
 * disproportionné à ce que la distinction apporte réellement.
 *
 * <h2>Vérification de propriété</h2>
 *
 * <p>Seul l'auteur de la proposition peut la retirer : {@code id_createur} est
 * comparé à l'identifiant résolu de l'appelant, jamais au libellé affiché
 * (deux Analystes RH pourraient porter un nom proche). Un Analyste RH qui
 * tenterait de retirer la proposition d'un collègue reçoit
 * {@code 403 GRILLE_NON_PROPRIETAIRE}, distinct d'un rôle insuffisant : le
 * rôle est le bon, la ressource ne lui appartient pas.
 */
@Service
public class RetraitGrilleService {

    private static final String ENTITE_CIBLE = "grille_tarifaire";
    private static final String ACTION_RETRAIT = "RETRAIT_GRILLE";

    private final GrilleTarifaireRepository grilleTarifaireRepository;
    private final ClientIdentite clientIdentite;
    private final PublicateurAudit publicateurAudit;

    public RetraitGrilleService(GrilleTarifaireRepository grilleTarifaireRepository,
            ClientIdentite clientIdentite,
            PublicateurAudit publicateurAudit) {
        this.grilleTarifaireRepository = grilleTarifaireRepository;
        this.clientIdentite = clientIdentite;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Retire une grille EN_ATTENTE_DRH, à la demande de son propre auteur.
     *
     * @throws GrilleIntrouvableException aucune grille ne porte cet identifiant (404)
     * @throws GrilleNonProprietaireException l'appelant n'est pas l'auteur de cette
     *         proposition précise (403)
     * @throws cm.afrilandfirstbank.rations.grilles.domaine.exception.TransitionGrilleInterditeException
     *         la grille n'est plus EN_ATTENTE_DRH -- déjà tranchée par la DRH (422)
     * @throws cm.afrilandfirstbank.rations.grilles.domaine.exception.MotifRejetRequisException
     *         motif vide, une fois épuré des espaces (422)
     */
    @Transactional
    public GrilleTarifaire retirer(Long idGrille, String motif, String enteteAutorisation, String adresseIp) {

        // 1. Qui demande. Meme appel que pour toute autre ecriture de ce service :
        //    le jeton Keycloak ne porte pas l'identifiant local (CLAUDE.md section 10).
        AuteurIdentifie auteur = clientIdentite.resoudreAuteur(enteteAutorisation);

        // 2. Chargement.
        GrilleTarifaire cible = grilleTarifaireRepository.findById(idGrille)
                .orElseThrow(() -> new GrilleIntrouvableException(
                        "Aucune grille tarifaire ne porte l'identifiant " + idGrille + "."));

        // 3. Propriete, AVANT la transition : un Analyste RH qui n'est pas
        //    l'auteur doit voir un refus nommant precisement ce qui cloche,
        //    plutot qu'un refus de transition qui ne le concerne pas.
        if (!auteur.id().equals(cible.getIdCreateur())) {
            throw new GrilleNonProprietaireException(idGrille);
        }

        StatutGrilleEnum statutInitial = cible.getStatutValidation();

        // 4. Meme transition qu'un rejet DRH (EN_ATTENTE_DRH -> REJETEE), avec
        //    l'auteur du retrait comme "validateur" -- la colonne designe celui
        //    qui a tranche, pas celui qui a approuve (meme lecture qu'au rejet).
        TransitionGrille.rejeter(cible, motif, LocalDateTime.now(), auteur.id(), auteur.libelle());
        GrilleTarifaire retiree = grilleTarifaireRepository.save(cible);

        publierRetrait(retiree, statutInitial, auteur, adresseIp);
        return retiree;
    }

    private void publierRetrait(GrilleTarifaire retiree, StatutGrilleEnum statutInitial,
            AuteurIdentifie auteur, String adresseIp) {

        publicateurAudit.publier(EvenementAudit.de(
                auteur.id(), ACTION_RETRAIT, ENTITE_CIBLE, retiree.getId(), adresseIp,
                DeltaAudit.nouveau()
                        .champ("statutValidation", statutInitial, retiree.getStatutValidation())
                        .contexte("auteur", auteur.login())
                        .contexte("nature", retiree.getNature())
                        .contexte("session", retiree.getSession())
                        .contexte("montantFcfa", retiree.getMontantFcfa())
                        .contexte("motif", retiree.getMotifRejet())
                        .enJson()));
    }

}
