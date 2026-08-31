package cm.afrilandfirstbank.rations.saisie.infrastructure.grilles;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Corps de la reponse {@code 200} de {@code GET /grilles/active}, tel que le
 * service Saisie le lit (contrat decrit dans
 * {@code docs/appel-resolution-montant.md} section 1).
 *
 * <p><b>Recopie plutot que partagee.</b> Le service Grilles publie le meme
 * format sous {@code MontantApplicableResponse} ; le type n'est pas mutualise,
 * conformement a CLAUDE.md section 3 — {@code rations-audit-commun} est la seule
 * bibliotheque partagee du backend, et son perimetre est verifie au build. Un
 * DTO commun creerait un couplage de compilation entre deux services qui doivent
 * pouvoir evoluer separement.
 *
 * <p><b>Lecteur tolerant.</b> {@code ignoreUnknown} : si le service Grilles
 * ajoute un champ demain, la Saisie continue de lire ceux qu'elle connait plutot
 * que d'echouer a la deserialisation — ce qui, ici, se traduirait par un refus
 * de toutes les lignes. Meme posture que le service Audit vis-a-vis du topic
 * (CLAUDE.md section 9.2).
 *
 * <p><b>{@code montantFcfa} et {@code idGrille} sont des objets, pas des
 * primitifs</b> : le contrat impose {@code null} — jamais {@code 0} — quand
 * {@code disponible} vaut {@code false}. Les declarer {@code int} ferait
 * silencieusement apparaitre un tarif a zero a la deserialisation. Ils
 * redeviennent primitifs des le passage dans
 * {@link cm.afrilandfirstbank.rations.saisie.application.ResultatResolutionMontant.MontantResolu},
 * ou l'absence n'a plus de sens.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MontantApplicableReponse(
        boolean disponible,
        String nature,
        String session,
        LocalDate date,
        Integer montantFcfa,
        Long idGrille,
        LocalDate dateDebut,
        LocalDate dateFin) {
}
