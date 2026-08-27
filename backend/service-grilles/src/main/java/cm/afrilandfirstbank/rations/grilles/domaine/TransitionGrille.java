package cm.afrilandfirstbank.rations.grilles.domaine;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import cm.afrilandfirstbank.rations.grilles.domaine.exception.MotifRejetRequisException;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.TransitionGrilleInterditeException;

/**
 * Machine a etats du cycle de vie d'une grille tarifaire (diagramme ET02, RG-14).
 *
 * <p>Transitions autorisees :
 *
 * <pre>
 *   (creation)      -> BROUILLON        creation par l'ARH (constructeur de GrilleTarifaire)
 *   BROUILLON       -> BROUILLON        ajustement avant soumission
 *   BROUILLON       -> EN_ATTENTE_DRH   soumission a la DRH
 *   EN_ATTENTE_DRH  -> ACTIVE           validation DRH
 *   EN_ATTENTE_DRH  -> REJETEE          rejet DRH, motif obligatoire
 *   ACTIVE          -> (fermee)         pose d'une date de fin (statut inchange)
 * </pre>
 *
 * <p>Toute autre transition est interdite et leve une erreur explicite. Une grille
 * REJETEE ne revient jamais en BROUILLON.
 *
 * <p>Perimetre : cette classe ne gere que le statut d'UNE grille prise isolement.
 * La fermeture de l'ancienne grille lors de l'activation d'une remplacante
 * (operation a deux lignes, transactionnelle) releve du service de validation au
 * Sprint 2.3, pas d'ici.
 *
 * <p>La « fermeture » n'est pas un statut : une grille fermee reste au statut
 * ACTIVE avec une {@code dateFin} non nulle. C'est ce que traduit l'index unique
 * partiel de la table ({@code WHERE statut_validation = 'ACTIVE' AND date_fin IS NULL}),
 * qui autorise ainsi l'historique tout en garantissant une seule grille courante
 * par couple (nature, session).
 */
public final class TransitionGrille {

    private static final Map<StatutGrilleEnum, Set<StatutGrilleEnum>> TRANSITIONS_AUTORISEES;

    static {
        Map<StatutGrilleEnum, Set<StatutGrilleEnum>> m = new EnumMap<>(StatutGrilleEnum.class);
        m.put(StatutGrilleEnum.BROUILLON,
                EnumSet.of(StatutGrilleEnum.BROUILLON, StatutGrilleEnum.EN_ATTENTE_DRH));
        m.put(StatutGrilleEnum.EN_ATTENTE_DRH,
                EnumSet.of(StatutGrilleEnum.ACTIVE, StatutGrilleEnum.REJETEE));
        m.put(StatutGrilleEnum.ACTIVE, EnumSet.noneOf(StatutGrilleEnum.class));   // etat terminal cote statut
        m.put(StatutGrilleEnum.REJETEE, EnumSet.noneOf(StatutGrilleEnum.class)); // etat terminal
        TRANSITIONS_AUTORISEES = m;
    }

    private TransitionGrille() {
        // classe utilitaire
    }

    /** Indique si le passage de statut {@code source} vers {@code cible} est prevu par ET02. */
    public static boolean estAutorisee(StatutGrilleEnum source, StatutGrilleEnum cible) {
        return TRANSITIONS_AUTORISEES
                .getOrDefault(source, EnumSet.noneOf(StatutGrilleEnum.class))
                .contains(cible);
    }

    /** BROUILLON -> EN_ATTENTE_DRH : l'ARH soumet la grille a la DRH. */
    public static void soumettre(GrilleTarifaire grille) {
        exigerTransition(grille.getStatutValidation(), StatutGrilleEnum.EN_ATTENTE_DRH);
        grille.appliquerStatut(StatutGrilleEnum.EN_ATTENTE_DRH);
    }

    /**
     * Verifie, sans rien modifier, que la grille peut etre validee.
     *
     * <p>Sert au service de decision (Sprint 2.3) : la bascule ferme d'abord
     * l'ancienne grille, et il serait deplaisant de decouvrir apres coup que la
     * cible n'etait pas validable. Le rollback rattraperait la situation, mais
     * la correction dependrait alors d'un mecanisme technique plutot que de
     * l'ordre des etapes.
     *
     * @throws TransitionGrilleInterditeException si le statut courant n'admet pas ACTIVE
     */
    public static void exigerValidationPossible(GrilleTarifaire grille) {
        exigerTransition(grille.getStatutValidation(), StatutGrilleEnum.ACTIVE);
    }

    /** EN_ATTENTE_DRH -> ACTIVE : la DRH valide. Enregistre le validateur et l'horodatage. */
    public static void valider(GrilleTarifaire grille, Long idValidateur, LocalDateTime instant) {
        valider(grille, idValidateur, instant, null);
    }

    /**
     * EN_ATTENTE_DRH -> ACTIVE, en recopiant le nom lisible de la DRH.
     *
     * <p>Le libelle est fige au moment de la decision, comme celui du createur au
     * Sprint 2.2 : il repond a « qui a valide, tel qu'il etait connu ce jour-la ».
     *
     * <p>La recopie passe par ici, et non par le service applicatif, parce que les
     * mutateurs de {@link GrilleTarifaire} sont en visibilite paquet : aucune
     * couche au-dessus du domaine ne modifie une grille sans qu'une transition
     * ait ete jugee legale au prealable.
     */
    public static void valider(GrilleTarifaire grille, Long idValidateur, LocalDateTime instant,
            String libelleValidateur) {
        exigerTransition(grille.getStatutValidation(), StatutGrilleEnum.ACTIVE);
        grille.enregistrerValidation(idValidateur, instant);
        grille.enregistrerLibelleValidateur(libelleValidateur);
        grille.appliquerStatut(StatutGrilleEnum.ACTIVE);
    }

    /**
     * EN_ATTENTE_DRH -> REJETEE : la DRH rejette. Le motif est obligatoire (RG-10) ;
     * son absence leve {@link MotifRejetRequisException}, distincte d'une transition
     * interdite.
     */
    public static void rejeter(GrilleTarifaire grille, String motif, LocalDateTime instant) {
        rejeter(grille, motif, instant, null, null);
    }

    /**
     * EN_ATTENTE_DRH -> REJETEE, en enregistrant qui a rejete.
     *
     * <p>Un rejet est une decision autant qu'une validation : l'omettre du journal
     * laisserait l'ARH devant un refus sans auteur. {@code id_validateur} porte
     * donc la DRH dans les deux cas — la colonne designe celui qui a tranche, pas
     * celui qui a approuve.
     *
     * <p>Le motif est verifie <b>avant</b> la transition : un rejet sans motif
     * d'une grille deja ACTIVE signale l'absence de motif plutot que la transition
     * interdite, et l'utilisateur corrige la premiere chose qu'on lui reproche.
     */
    public static void rejeter(GrilleTarifaire grille, String motif, LocalDateTime instant,
            Long idValidateur, String libelleValidateur) {
        if (motif == null || motif.isBlank()) {
            throw new MotifRejetRequisException(
                    "Le rejet d'une grille exige un motif (RG-10).");
        }
        exigerTransition(grille.getStatutValidation(), StatutGrilleEnum.REJETEE);
        grille.enregistrerRejet(motif.strip(), instant);
        grille.enregistrerValidation(idValidateur, instant);
        grille.enregistrerLibelleValidateur(libelleValidateur);
        grille.appliquerStatut(StatutGrilleEnum.REJETEE);
    }

    /**
     * ACTIVE -> (fermee) : pose la date de fin de validite. Le statut reste ACTIVE.
     * Fermer une grille qui n'est pas ACTIVE leve {@link TransitionGrilleInterditeException}.
     */
    public static void fermer(GrilleTarifaire grille, LocalDate dateFin) {
        if (grille.getStatutValidation() != StatutGrilleEnum.ACTIVE) {
            throw new TransitionGrilleInterditeException(
                    "Seule une grille ACTIVE peut etre fermee ; statut courant : "
                    + grille.getStatutValidation() + ".");
        }
        if (dateFin == null) {
            throw new IllegalArgumentException(
                    "La date de fin est obligatoire pour fermer une grille.");
        }
        grille.poserDateFin(dateFin);
    }

    private static void exigerTransition(StatutGrilleEnum source, StatutGrilleEnum cible) {
        if (!estAutorisee(source, cible)) {
            throw new TransitionGrilleInterditeException(
                    "Transition de statut interdite : " + source + " -> " + cible
                    + ". Depuis " + source + ", transitions autorisees : "
                    + TRANSITIONS_AUTORISEES.getOrDefault(source, EnumSet.noneOf(StatutGrilleEnum.class))
                    + ".");
        }
    }

}
