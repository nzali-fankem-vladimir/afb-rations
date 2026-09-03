package cm.afrilandfirstbank.rations.transmission.domaine;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Accuse de prise en charge renvoye par le module de comptabilisation sur
 * {@code rations.etat.accuse} (contrat d'API section 7.2, US-12, US-15).
 *
 * <h2>C'est la seconde moitie de l'echange</h2>
 *
 * <p>Le module publie l'etat valide (section 7.1, Sprint 5.1) et <b>consomme</b> cet
 * accuse : il est producteur et consommateur (CLAUDE.md section 9). Sans ce type et
 * son consommateur, un etat apparaitrait indefiniment comme transmis sans qu'on sache
 * s'il a ete traite, rejete, ou perdu — le suivi (US-15) resterait aveugle sur le sort
 * de ce qui est parti en paiement.
 *
 * <h2>Pourquoi le statut et la date voyagent en chaine</h2>
 *
 * <p>{@code statutIntegration} est declare {@code String} et non
 * {@link StatutIntegrationEnum}, et {@code dateTraitement} {@code String} et non
 * {@code OffsetDateTime}. Meme raison que pour {@code nature} et {@code session} dans
 * {@link EtatValideEvent} : une valeur inconnue ferait <b>echouer la deserialisation</b>
 * au lieu d'etre vue.
 *
 * <p>La consequence serait tres concrete ici. Un statut {@code "BIDON"} tombe dans le
 * cas « message illisible » — diagnostic pauvre, indiscernable d'un JSON tronque —
 * alors qu'il merite son propre refus nomme, {@code STATUT_INCONNU}, qui dit exactement
 * ce qui ne va pas. C'est la lecon du Sprint 5.1, ou une exception mal placee avait
 * fait perdre le diagnostic sans changer le comportement.
 *
 * <p>La conversion vers le type juste a lieu <b>une seule fois</b>, dans
 * {@code ValidationAccuseService}, et rien en aval ne voit jamais un statut non
 * verifie.
 *
 * <h2>Tolerant reader</h2>
 *
 * <p>Le module de comptabilisation est ecrit par une autre equipe, dans une autre
 * technologie peut-etre, et <b>l'equipe n'y a pas acces</b>. Les champs inconnus sont
 * donc ignores : il peut enrichir son accuse sans casser ce consommateur.
 *
 * <p>Le motif accepte trois noms ({@code motif}, {@code motifRejet},
 * {@code motifIntegration}) via {@link JsonAlias}. Le contrat section 7.2 decrit le
 * champ en toutes lettres — « en cas de rejet, un motif accompagne l'accuse » — mais ne
 * le nomme pas dans son exemple JSON, qui ne montre qu'un accuse d'integration. Un
 * desaccord de nom ferait refuser <b>tous</b> les accuses de rejet pour
 * {@code MOTIF_REJET_ABSENT}, et le motif d'un refus comptable serait perdu ; les trois
 * alias coutent une annotation et ferment ce trou.
 *
 * <p>La tolerance porte sur la <b>lecture</b> seulement. Un champ manquant se lit
 * {@code null}, et la validation refuse plutot que de supposer une valeur — c'est
 * l'inverse d'un statut absent lu comme {@code EN_ATTENTE}.
 *
 * @param idProcessus l'etat que la comptabilite dit avoir traite. C'est la seule cle de
 *        rapprochement avec {@code processus_mensuel}
 * @param statutIntegration {@code EN_ATTENTE}, {@code INTEGRE} ou {@code REJETE}, non
 *        verifie a ce stade
 * @param referenceComptable reference produite par la comptabilite. Ce module ne la
 *        fabrique jamais : il ne produit aucune ecriture comptable (CLAUDE.md section 8)
 * @param dateTraitement horodatage declare du traitement comptable, ISO 8601 avec
 *        decalage, non verifie a ce stade
 * @param motif motif du refus comptable, exige quand le statut vaut {@code REJETE}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccuseComptableEvent(
        Long idProcessus,
        String statutIntegration,
        String referenceComptable,
        String dateTraitement,
        @JsonAlias({ "motifRejet", "motifIntegration" }) String motif) {
}
