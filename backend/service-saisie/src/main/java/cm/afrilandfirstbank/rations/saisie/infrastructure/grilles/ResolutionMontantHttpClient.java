package cm.afrilandfirstbank.rations.saisie.infrastructure.grilles;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import cm.afrilandfirstbank.rations.saisie.application.ResolutionMontantClient;
import cm.afrilandfirstbank.rations.saisie.application.ResultatResolutionMontant;
import cm.afrilandfirstbank.rations.saisie.application.ResultatResolutionMontant.AucuneGrilleApplicable;
import cm.afrilandfirstbank.rations.saisie.application.ResultatResolutionMontant.MontantResolu;
import cm.afrilandfirstbank.rations.saisie.application.ResultatResolutionMontant.ServiceGrillesIndisponible;
import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;

/**
 * Adaptateur HTTP du port {@link ResolutionMontantClient} : interroge
 * {@code GET /grilles/active} du service Grilles.
 *
 * <p><b>Premier appel inter-services sortant du service Saisie.</b> Il passe par
 * l'<b>API</b> du service Grilles, jamais par sa base : aucune source de donnees
 * pointant vers {@code rations_grilles} n'existe dans ce service, et la
 * cartographie le verifie (Sprint 3.2, etape 6).
 *
 * <h2>Propagation du jeton</h2>
 *
 * <p>L'en-tete {@code Authorization} de l'utilisateur final est <b>relaye tel
 * quel</b>, sans reemission ni compte de service (decision Sprint 1.3, confirmee
 * au Sprint 2.4 pour cet endpoint precis). Le realm {@code afb-rations-dev} n'a
 * qu'un client public, sans identite machine-a-machine ; le mapper d'audience
 * place deja {@code aud: rations-api} dans le jeton utilisateur. Aucun role
 * particulier n'est exige par {@code GET /grilles/active} : un jeton valide suffit.
 *
 * <h2>Traduction des reponses</h2>
 *
 * <ul>
 *   <li>{@code 200} avec {@code disponible: true} : {@link MontantResolu}</li>
 *   <li>{@code 200} avec {@code disponible: false} : {@link AucuneGrilleApplicable},
 *       refus <b>metier</b></li>
 *   <li>delai depasse, connexion refusee, {@code 4xx}, {@code 5xx}, corps
 *       illisible : {@link ServiceGrillesIndisponible}, refus <b>technique</b></li>
 * </ul>
 *
 * <p>Les deux refus ne sont jamais confondus : c'est l'exigence centrale de
 * {@code docs/appel-resolution-montant.md} section 2. En particulier,
 * <b>l'absence de tarif n'est jamais deduite d'un code HTTP</b> : elle se lit
 * dans le champ {@code disponible}, et nulle part ailleurs.
 *
 * <p><b>Un {@code 4xx} est un refus technique, pas une absence de tarif.</b> Un
 * {@code 400 REQUETE_INVALIDE} signale un parametre mal forme — un defaut de ce
 * code-ci ; un {@code 401} ou {@code 403}, un probleme de jeton. Aucun n'autorise
 * a conclure qu'aucune grille n'existe : les traiter comme tel enverrait l'agent
 * reclamer un tarif a l'ARH pour une faute de programmation.
 *
 * <p><b>{@code 500 INCOHERENCE_GRILLE} est distingue</b> (deux grilles actives
 * se chevauchent, Sprint 2.4) : il reste un refus technique, mais il est
 * journalise comme l'incident de donnees qu'il est, au prefixe repere
 * {@link #PREFIXE_INCOHERENCE}. Le confondre avec une panne reseau le rendrait
 * invisible, alors qu'il demande une intervention en base.
 *
 * <h2>Fabrique de requetes injectee</h2>
 *
 * <p>Le {@link RestClient.Builder} est <b>injecte</b> et non construit ici :
 * c'est ce qui permet a {@code MockRestServiceServer} de s'y attacher dans les
 * tests (contrainte relevee au Sprint 3.1, {@code docs/rattachement-processus.md}
 * section 6). Les delais d'attente — 2 s de connexion, 3 s de lecture — et la
 * decision de <b>ne pas reessayer</b> sont poses sur ce constructeur, dans
 * {@link cm.afrilandfirstbank.rations.saisie.infrastructure.config.ConfigurationAppelsSortants}.
 *
 * <p><b>Un appel, un verdict.</b> Aucun reessai automatique : l'agent est devant
 * son ecran et son geste de resaisie est deja le reessai ; un reessai
 * automatique ne ferait que masquer une degradation du service Grilles, et
 * doublerait l'attente sur exactement le chemin le plus lent (decision Sprint
 * 3.2).
 */
@Component
public class ResolutionMontantHttpClient implements ResolutionMontantClient {

    private static final Logger journal = LoggerFactory.getLogger(ResolutionMontantHttpClient.class);

    /**
     * Prefixe de log reperable en exploitation, meme convention que
     * {@code INCOHERENCE GRILLE} cote service Grilles, {@code INCOHERENCE
     * BENEFICIAIRE} (Sprint 3.1) et {@code AUDIT PERDU}
     * ({@code rations-audit-commun}).
     */
    static final String PREFIXE_INCOHERENCE = "INCOHERENCE GRILLE";

    /** Code du contrat d'erreur signalant deux grilles actives concurrentes. */
    static final String CODE_INCOHERENCE_GRILLE = "INCOHERENCE_GRILLE";

    private final RestClient clientRest;
    private final String urlServiceGrilles;

    public ResolutionMontantHttpClient(RestClient.Builder constructeurRest,
                                       @Value("${app.grilles.url}") String urlServiceGrilles) {
        this.urlServiceGrilles = urlServiceGrilles;
        this.clientRest = constructeurRest.baseUrl(urlServiceGrilles).build();
    }

    @Override
    public ResultatResolutionMontant resoudre(NatureEnum nature, SessionEnum session,
                                              LocalDate datePrestation, String enteteAutorisation) {
        if (enteteAutorisation == null || enteteAutorisation.isBlank()) {
            // Ne peut pas arriver a l'execution : la SecurityConfig refuse en
            // amont toute requete metier sans jeton. Si cela arrive quand meme,
            // c'est que l'appelant a oublie de relayer l'en-tete, soit un defaut
            // de programmation. On echoue bruyamment plutot que de rendre
            // ServiceGrillesIndisponible, qui dirait a l'agent de reessayer plus
            // tard pour un bogue que le temps ne reparera pas.
            throw new IllegalStateException(
                    "Aucun en-tete Authorization a relayer au service Grilles : "
                            + "l'appelant doit propager le jeton de l'utilisateur final.");
        }

        try {
            MontantApplicableReponse reponse = clientRest.get()
                    .uri(constructeur -> constructeur
                            .path("/grilles/active")
                            .queryParam("nature", nature)
                            .queryParam("session", session)
                            // Date de la PRESTATION. Jamais LocalDate.now() :
                            // voir ResolutionMontantClient#resoudre.
                            .queryParam("date", datePrestation)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, enteteAutorisation)
                    .retrieve()
                    .body(MontantApplicableReponse.class);

            return interpreter(reponse, nature, session, datePrestation);

        } catch (RestClientResponseException reponseEnErreur) {
            return traduireReponseEnErreur(reponseEnErreur, nature, session, datePrestation);

        } catch (RestClientException panne) {
            // Delai depasse, connexion refusee, corps indeserialisable. Journalise
            // ici, seul endroit ou la cause technique est encore connue ; l'agent,
            // lui, recevra un message sans detail d'infrastructure.
            journal.warn("Service Grilles injoignable a l'adresse {} pour {} / {} au {} : {}",
                    urlServiceGrilles, nature, session, datePrestation, panne.getMessage());
            return new ServiceGrillesIndisponible(
                    "appel a " + urlServiceGrilles + " en echec : " + panne.getMessage());
        }
    }

    /**
     * Traduit un corps de reponse {@code 200}. L'absence de tarif se lit dans
     * {@code disponible}, jamais dans le code HTTP.
     */
    private ResultatResolutionMontant interpreter(MontantApplicableReponse reponse, NatureEnum nature,
                                                  SessionEnum session, LocalDate datePrestation) {
        if (reponse == null) {
            // Un 200 au corps vide est aussi inexploitable qu'une panne : on ne
            // fabrique ni montant ni indisponibilite metier a partir de rien.
            journal.warn("Le service Grilles a repondu 200 sans corps pour {} / {} au {}.",
                    nature, session, datePrestation);
            return new ServiceGrillesIndisponible("reponse 200 sans corps exploitable");
        }

        if (!reponse.disponible()) {
            return new AucuneGrilleApplicable(nature, session, datePrestation);
        }

        if (reponse.montantFcfa() == null || reponse.idGrille() == null) {
            // Contradiction interne au contrat : disponible sans montant. Refus
            // technique, jamais un repli sur zero (RG-03).
            journal.warn("Le service Grilles annonce un montant disponible sans le fournir "
                            + "pour {} / {} au {} (montant={}, idGrille={}).",
                    nature, session, datePrestation, reponse.montantFcfa(), reponse.idGrille());
            return new ServiceGrillesIndisponible("reponse disponible=true sans montant ni grille");
        }

        return new MontantResolu(reponse.montantFcfa(), reponse.idGrille(),
                reponse.dateDebut(), reponse.dateFin());
    }

    /**
     * Traduit un {@code 4xx} ou un {@code 5xx} en refus technique, en isolant le
     * cas {@code INCOHERENCE_GRILLE} pour qu'il reste visible.
     */
    private ResultatResolutionMontant traduireReponseEnErreur(RestClientResponseException erreur,
                                                              NatureEnum nature, SessionEnum session,
                                                              LocalDate datePrestation) {
        String code = lireCodeErreur(erreur);

        if (CODE_INCOHERENCE_GRILLE.equals(code)) {
            journal.error("{} : le service Grilles signale plusieurs grilles actives pour {} / {} au {}. "
                            + "Ligne refusee ; incident de donnees a corriger cote Grilles, "
                            + "pas une panne reseau. Message recu : {}",
                    PREFIXE_INCOHERENCE, nature, session, datePrestation, erreur.getMessage());
            return new ServiceGrillesIndisponible(
                    CODE_INCOHERENCE_GRILLE + " : plusieurs grilles actives couvrent " + nature + " / "
                            + session + " au " + datePrestation);
        }

        journal.warn("Le service Grilles a repondu {} pour {} / {} au {} (code metier {}). Ligne refusee.",
                erreur.getStatusCode(), nature, session, datePrestation, code);
        return new ServiceGrillesIndisponible(
                "reponse " + erreur.getStatusCode() + (code == null ? "" : " (" + code + ")"));
    }

    /** Code metier du format d'erreur uniforme, {@code null} si le corps ne s'en reclame pas. */
    private String lireCodeErreur(RestClientResponseException erreur) {
        try {
            ErreurRemontee corps = erreur.getResponseBodyAs(ErreurRemontee.class);
            return corps == null ? null : corps.code();
        } catch (RuntimeException corpsIllisible) {
            // Corps absent, non JSON, ou hors format uniforme : sans importance,
            // le refus technique est deja acquis. On ne masque rien d'utile.
            return null;
        }
    }

}
