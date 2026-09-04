package cm.afrilandfirstbank.rations.reporting.application;

import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.reporting.domaine.Rapport;

/**
 * Traces d'audit du service Reporting (omission relevée au Sprint 6.3, étape 2 :
 * ce service ne publiait <b>aucun</b> événement, alors que le tableau de
 * couverture du guide lui en demande deux).
 *
 * <h2>Pourquoi tracer un service qui n'écrit rien</h2>
 *
 * <p>Le module trace ailleurs des <i>modifications d'état</i>. Ici il n'y en a
 * aucune : les quatre endpoints sont en lecture, et ce service n'a ni entité JPA
 * ni base. La question qu'un contrôle interne pose n'est donc pas « qu'a-t-on
 * modifié » mais <b>« qui a sorti quoi du système »</b>.
 *
 * <p>Un export est le seul geste du module qui produit un fichier <b>quittant
 * définitivement le périmètre applicatif</b> : une fois le PDF ou le classeur
 * enregistré sur un poste, plus aucune habilitation ne le protège, et il porte
 * des données nominatives de paiement — noms, montants, agences. C'est
 * précisément le type d'événement qu'une exigence d'historisation vise.
 *
 * <h2>Ce qui n'est délibérément pas tracé, et pourquoi</h2>
 *
 * <p>Les deux endpoints de suivi — {@code GET /reporting/demandes} et
 * {@code GET /reporting/processus/{id}/historique} — ne publient rien. Ce sont
 * les outils de travail quotidiens du circuit : les tracer produirait un
 * événement par consultation d'écran, et le journal se remplirait de bruit au
 * point de rendre les faits notables introuvables. Aucune lecture n'est tracée
 * ailleurs dans le module ({@code GET /processus/{id}}, {@code GET /grilles},
 * {@code GET /saisie/fiches} n'en publient aucune), et c'est le même raisonnement
 * qui a écarté {@code date_dernier_acces} au Sprint 6.3.
 *
 * <p>La ligne retenue est donc : <b>on trace ce qui agrège une portée entière ou
 * ce qui fait sortir un fichier, pas ce qui affiche un dossier</b>.
 *
 * <h2>Refus d'accès</h2>
 *
 * <p>{@code ACCES_REFUSE} reste non publié par ce service, décision antérieure
 * documentée dans {@code GestionnaireErreursApi} : il ne consomme jamais
 * {@code GET /identite/habilitation} lui-même et se borne à relayer les refus
 * prononcés en amont par Workflow et Saisie, déjà tracés à leur source.
 */
@Service
public class TracabiliteRapportService {

    private static final String ENTITE_CIBLE = "rapport_activite";
    private static final String ACTION_GENERATION = "GENERATION_RAPPORT";
    private static final String ACTION_EXPORT = "EXPORT_RAPPORT";

    private final PublicateurAudit publicateurAudit;

    public TracabiliteRapportService(PublicateurAudit publicateurAudit) {
        this.publicateurAudit = publicateurAudit;
    }

    /** Consultation à l'écran d'un rapport d'activité (US-16). */
    public void tracerGeneration(Rapport rapport, String adresseIp) {
        publicateurAudit.publier(EvenementAudit.de(
                null,
                ACTION_GENERATION,
                ENTITE_CIBLE,
                null,
                adresseIp,
                socle(rapport).enJson()));
    }

    /**
     * Production d'un fichier téléchargeable.
     *
     * <p>Le nom du fichier et sa taille figurent au delta : c'est ce qui permet,
     * des mois plus tard, de rapprocher un document retrouvé hors du système de
     * l'extraction qui l'a produit.
     */
    public void tracerExport(Rapport rapport, String format, String nomFichier, int tailleOctets,
            String adresseIp) {
        publicateurAudit.publier(EvenementAudit.de(
                null,
                ACTION_EXPORT,
                ENTITE_CIBLE,
                null,
                adresseIp,
                socle(rapport)
                        .contexte("format", format)
                        .contexte("nomFichier", nomFichier)
                        .contexte("tailleOctets", tailleOctets)
                        .enJson()));
    }

    /**
     * Partie commune aux deux traces : ce que le rapport couvrait réellement.
     *
     * <p>{@code codeUnite} nul est rendu explicitement comme « toutes unités
     * visibles » : c'est la portée la plus large que ce service produise, et un
     * champ vide laisserait croire à une information manquante plutôt qu'à une
     * absence de filtre.
     *
     * <p>{@code idUtilisateur} n'est pas résolu — ce service n'appelle jamais
     * {@code GET /identite/moi} —, mais le {@code loginUtilisateur} porté par le
     * rapport lui-même est repris ici. Limite de recherche par utilisateur
     * consignée dans {@code docs/points-en-attente.md}, point A-01.
     */
    private DeltaAudit socle(Rapport rapport) {
        return DeltaAudit.nouveau()
                .contexte("login", rapport.loginUtilisateur())
                .contexte("periodeMois", rapport.periodeMois())
                .contexte("periodeAnnee", rapport.periodeAnnee())
                .contexte("codeUnite",
                        rapport.codeUnite() == null ? "TOUTES_UNITES_VISIBLES" : rapport.codeUnite())
                .contexte("nombreEtats", rapport.synthese().nombreEtats())
                .contexte("montantTotalPeriode", rapport.synthese().montantTotalPeriode())
                .contexte("rapportVide", rapport.vide());
    }

}
