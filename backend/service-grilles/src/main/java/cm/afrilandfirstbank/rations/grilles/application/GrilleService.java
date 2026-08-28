package cm.afrilandfirstbank.rations.grilles.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.grilles.api.dto.CreationGrilleRequest;
import cm.afrilandfirstbank.rations.grilles.domaine.GrilleTarifaire;
import cm.afrilandfirstbank.rations.grilles.domaine.StatutGrilleEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.TransitionGrille;
import cm.afrilandfirstbank.rations.grilles.infrastructure.GrilleTarifaireRepository;
import cm.afrilandfirstbank.rations.grilles.infrastructure.identite.AuteurIdentifie;
import cm.afrilandfirstbank.rations.grilles.infrastructure.identite.ClientIdentite;

/**
 * Cycle de vie des grilles tarifaires cote Analyste RH (Sprint 2.2, US-13).
 *
 * <p>Ce service porte ce que l'ARH peut faire : proposer une grille. Il ne porte
 * <b>aucune</b> decision de la DRH — ni validation, ni rejet, ni fermeture d'une
 * grille en vigueur. C'est le sous-sprint 2.3, et la separation n'est pas
 * qu'organisationnelle : RG-14 veut que celui qui propose ne soit pas celui qui
 * rend applicable.
 */
@Service
public class GrilleService {

    private static final String ENTITE_CIBLE = "grille_tarifaire";
    private static final String ACTION_CREATION = "CREATION_GRILLE";
    private static final String ACTION_SOUMISSION = "SOUMISSION_GRILLE";

    /**
     * Tri de la liste : nature, puis session, puis date de debut decroissante.
     *
     * <p>Regroupe les quatre couples (nature, session) du module, et place en tete
     * de chaque groupe la grille la plus recente — celle qui interesse l'ARH,
     * puisque c'est celle qui s'applique ou qui va s'appliquer. L'historique suit,
     * du plus recent au plus ancien.
     *
     * <p>Les directions sont mixtes (deux croissantes, une decroissante), ce que
     * {@code @PageableDefault} ne sait pas exprimer : le tri est donc pose ici.
     */
    private static final Sort TRI_PAR_DEFAUT = Sort.by(
            Sort.Order.asc("nature"),
            Sort.Order.asc("session"),
            Sort.Order.desc("dateDebut"));

    private final GrilleTarifaireRepository grilleTarifaireRepository;
    private final UniciteGrilleService uniciteGrilleService;
    private final ClientIdentite clientIdentite;
    private final PublicateurAudit publicateurAudit;

    public GrilleService(GrilleTarifaireRepository grilleTarifaireRepository,
            UniciteGrilleService uniciteGrilleService,
            ClientIdentite clientIdentite,
            PublicateurAudit publicateurAudit) {
        this.grilleTarifaireRepository = grilleTarifaireRepository;
        this.uniciteGrilleService = uniciteGrilleService;
        this.clientIdentite = clientIdentite;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Cree une grille et la soumet immediatement a la Directrice RH.
     *
     * <p><b>Un seul appel, un seul engagement</b> (decision Sprint 2.2). La grille
     * naît {@code BROUILLON} — le constructeur du domaine l'impose, et la machine
     * a etats du Sprint 2.1 n'admet pas d'autre etat initial — puis est soumise
     * dans la meme transaction. Ce qui atteint la base est donc deja
     * {@code EN_ATTENTE_DRH} : aucun brouillon n'est jamais persiste. Le contrat
     * d'API ne prevoit qu'un {@code POST /grilles}, et l'ARH n'a pas d'etape
     * intermediaire ou revenir.
     *
     * <p><b>Sans effet sur les saisies</b> (CT-25). Tant que la DRH n'a pas
     * tranche, la grille reste invisible de {@code rechercherGrillesCouvrant} : le
     * montant applique aux prestations du jour ne change pas d'un centime. C'est
     * ce qui protege la chaine de paiement d'un tarif errone saisi par
     * inadvertance.
     *
     * <p><b>Ordre des operations</b> (document maitre section 7.3) :
     * habilitation, chargement, regles, modification d'etat, audit. L'appel
     * reseau au service Identite est place en tete, avant toute requete : avec
     * l'acquisition differee de connexion de Spring Boot, aucune connexion a la
     * base n'est encore detenue pendant qu'il dure.
     *
     * @param requete grille proposee, deja validee syntaxiquement par la couche api
     * @param enteteAutorisation en-tete {@code Authorization} de l'ARH, relaye tel quel
     * @param adresseIp origine de la requete, pour la trace d'audit
     * @return la grille enregistree, au statut {@code EN_ATTENTE_DRH}
     */
    @Transactional
    public GrilleTarifaire creerEtSoumettre(CreationGrilleRequest requete,
            String enteteAutorisation, String adresseIp) {

        // 1. Habilitation. Le role ARH est deja exige par la securite ; il reste a
        //    savoir QUI agit, ce que le jeton ne dit pas (id_createur est un
        //    identifiant local). Un profil absent ou desactive fait echouer ici.
        AuteurIdentifie auteur = clientIdentite.resoudreAuteur(enteteAutorisation);

        // 2-3. Chargement et regles : RG-14, refus tot en cas de conflit.
        uniciteGrilleService.verifierAvantCreation(requete.nature(), requete.session(), requete.dateDebut());

        // 4. Modification d'etat.
        GrilleTarifaire grille = new GrilleTarifaire(
                requete.nature(),
                requete.session(),
                requete.montantFcfa(),
                requete.dateDebut(),
                auteur.id(),
                auteur.libelle());
        TransitionGrille.soumettre(grille);
        GrilleTarifaire enregistree = grilleTarifaireRepository.save(grille);

        // 5. Audit. Deux evenements, parce que ce sont deux faits distincts :
        //    une grille a ete redigee, et elle a ete engagee aupres de la DRH. Les
        //    fondre en un seul rendrait le journal muet le jour ou la creation et
        //    la soumission seront dissociees.
        //    La publication ne leve jamais et n'attend rien (contrat de
        //    PublicateurAudit) : l'envoi reel a lieu apres le commit.
        publierCreation(enregistree, auteur, adresseIp);
        publierSoumission(enregistree, auteur, adresseIp);

        return enregistree;
    }

    /**
     * Liste paginee des grilles, filtre optionnel sur le statut.
     *
     * <p><b>Aucun appel reseau.</b> Les libelles d'auteur sont deja dans les
     * lignes : consulter les grilles reste possible meme si le service Identite
     * est arrete.
     *
     * <p>Le tri par defaut ne s'applique que si l'appelant n'en a pas demande un
     * autre. Le remplacer inconditionnellement ignorerait silencieusement un
     * parametre {@code sort} envoye par le frontend.
     *
     * @param statut statut a filtrer, ou {@code null} pour toutes les grilles
     */
    @Transactional(readOnly = true)
    public Page<GrilleTarifaire> lister(StatutGrilleEnum statut, Pageable pagination) {
        Pageable pageEffective = pagination.getSort().isSorted()
                ? pagination
                : PageRequest.of(pagination.getPageNumber(), pagination.getPageSize(), TRI_PAR_DEFAUT);

        return grilleTarifaireRepository.rechercherParStatut(statut, pageEffective);
    }

    private void publierCreation(GrilleTarifaire grille, AuteurIdentifie auteur, String adresseIp) {
        publicateurAudit.publier(EvenementAudit.de(
                auteur.id(),
                ACTION_CREATION,
                ENTITE_CIBLE,
                grille.getId(),
                adresseIp,
                DeltaAudit.nouveau()
                        .champ("nature", null, grille.getNature())
                        .champ("session", null, grille.getSession())
                        .champ("montantFcfa", null, grille.getMontantFcfa())
                        .champ("dateDebut", null, grille.getDateDebut())
                        .contexte("auteur", auteur.login())
                        .enJson()));
    }

    private void publierSoumission(GrilleTarifaire grille, AuteurIdentifie auteur, String adresseIp) {
        publicateurAudit.publier(EvenementAudit.de(
                auteur.id(),
                ACTION_SOUMISSION,
                ENTITE_CIBLE,
                grille.getId(),
                adresseIp,
                DeltaAudit.nouveau()
                        .champ("statutValidation", StatutGrilleEnum.BROUILLON, grille.getStatutValidation())
                        .contexte("auteur", auteur.login())
                        .contexte("destinataire", "DRH")
                        .enJson()));
    }

}
