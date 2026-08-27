package cm.afrilandfirstbank.rations.grilles.infrastructure.identite;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import cm.afrilandfirstbank.rations.grilles.domaine.exception.AuteurNonHabiliteException;

/**
 * Resout l'auteur d'une action aupres du service Identite.
 *
 * <h2>Pourquoi cet appel existe</h2>
 *
 * <p>La table {@code grille_tarifaire} porte {@code id_createur BIGINT NOT NULL} :
 * l'identifiant local de l'ARH dans la table {@code utilisateurs}, qui vit dans
 * une AUTRE base. Le jeton Keycloak ne porte pas cet identifiant et ne peut pas
 * le porter : il est attribue localement au pre-provisionnement, il n'existe pas
 * a l'annuaire (CLAUDE.md section 10). L'appel n'est donc pas un choix de
 * conception, il est impose par le schema.
 *
 * <p>Il n'a lieu <b>qu'a l'ecriture</b>. Les lectures ({@code GET /grilles}) ne
 * traversent jamais le reseau : le libelle de l'auteur est recopie dans la ligne
 * au moment de l'acte (decision Sprint 2.2). Une panne du service Identite
 * empeche donc de creer une grille, jamais d'en consulter.
 *
 * <h2>Propagation du jeton</h2>
 *
 * <p>L'en-tete {@code Authorization} de l'utilisateur final est <b>relaye tel
 * quel</b>, sans reemission ni compte de service : le realm de developpement n'a
 * qu'un client public, sans identite machine-a-machine, et son mapper d'audience
 * place deja {@code aud: rations-api} dans le jeton utilisateur (decision Sprint
 * 1.3, {@code docs/appel-habilitation.md} section 2).
 *
 * <h2>Refus conservateur</h2>
 *
 * <p>Toute reponse autre qu'un {@code 200} exploitable fait echouer l'action :
 * timeout, connexion refusee, {@code 5xx}, corps illisible. Aucun cache, aucune
 * valeur de repli. Les delais sont bornes court (2 s de connexion, 3 s de
 * lecture) : sans cela, le comportement par defaut laisserait un ARH devant un
 * ecran fige pendant une minute avant d'apprendre que rien n'a ete enregistre.
 */
@Component
public class ClientIdentite {

    private static final Logger journal = LoggerFactory.getLogger(ClientIdentite.class);

    private static final Duration DELAI_CONNEXION = Duration.ofSeconds(2);
    private static final Duration DELAI_LECTURE = Duration.ofSeconds(3);

    private final RestClient clientRest;
    private final String urlServiceIdentite;

    public ClientIdentite(@Value("${app.identite.url}") String urlServiceIdentite) {
        this.urlServiceIdentite = urlServiceIdentite;

        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout(DELAI_CONNEXION);
        fabrique.setReadTimeout(DELAI_LECTURE);

        this.clientRest = RestClient.builder()
                .baseUrl(urlServiceIdentite)
                .requestFactory(fabrique)
                .build();
    }

    /**
     * Interroge {@code GET /identite/moi} avec le jeton de l'appelant.
     *
     * @param enteteAutorisation en-tete {@code Authorization} recu de l'utilisateur
     * @return l'auteur resolu, avec son identifiant local et son libelle
     * @throws AuteurNonHabiliteException si le service Identite refuse (4xx) :
     *         aucun profil local ouvert, ou profil desactive
     * @throws IdentiteIndisponibleException si le service Identite ne repond pas,
     *         repond en erreur serveur, ou repond un corps inexploitable
     */
    public AuteurIdentifie resoudreAuteur(String enteteAutorisation) {
        if (enteteAutorisation == null || enteteAutorisation.isBlank()) {
            // Ne devrait pas arriver : la securite refuse en amont toute requete
            // sans jeton. Si cela arrive quand meme, on refuse plutot que
            // d'appeler le service Identite sans identite a lui soumettre.
            throw new AuteurNonHabiliteException(
                    "Aucun jeton n'accompagne la requete : l'auteur ne peut pas etre identifie.");
        }

        try {
            AuteurIdentifie auteur = clientRest.get()
                    .uri("/identite/moi")
                    .header(HttpHeaders.AUTHORIZATION, enteteAutorisation)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (requete, reponse) -> {
                        throw new AuteurNonHabiliteException(
                                "Aucun profil actif n'est ouvert dans le module pour ce compte. "
                                        + "L'habilitation au module est un acte d'administration : "
                                        + "un compte a l'annuaire ne suffit pas.");
                    })
                    .body(AuteurIdentifie.class);

            if (auteur == null || auteur.id() == null) {
                // Un 200 au corps vide ou sans identifiant est aussi inexploitable
                // qu'une panne : on ne fabrique pas un auteur par defaut.
                throw new IdentiteIndisponibleException(
                        "Le service Identite a repondu sans identifiant exploitable.");
            }
            return auteur;

        } catch (AuteurNonHabiliteException | IdentiteIndisponibleException refus) {
            throw refus;

        } catch (RestClientException panne) {
            // Timeout, connexion refusee, 5xx, corps illisible : tous traites de
            // la meme facon. On journalise ici parce que c'est le seul endroit ou
            // la cause technique est encore connue ; l'utilisateur, lui, recevra
            // un message sans detail d'infrastructure.
            journal.warn("Service Identite injoignable a l'adresse {} : {}",
                    urlServiceIdentite, panne.getMessage());
            throw new IdentiteIndisponibleException(
                    "Le service Identite n'a pas repondu dans le delai imparti.", panne);
        }
    }

}
