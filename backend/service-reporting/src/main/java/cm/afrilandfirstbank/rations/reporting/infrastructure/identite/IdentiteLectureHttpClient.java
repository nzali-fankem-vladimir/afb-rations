package cm.afrilandfirstbank.rations.reporting.infrastructure.identite;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import cm.afrilandfirstbank.rations.reporting.application.IdentiteLectureClient;
import cm.afrilandfirstbank.rations.reporting.domaine.LibelleActeur;

/**
 * Appel de {@code GET /identite/utilisateurs/libelles}, endpoint interne du
 * service Identite (Sprint 6.1).
 *
 * <h2>La seule dependance du module qui ne fait jamais echouer son appelant</h2>
 *
 * <p>Partout ailleurs, un service Identite muet fait <b>refuser</b> l'operation
 * (doctrine du refus conservateur, Sprint 1.3). Ici non, et la difference n'est pas
 * un relachement : cet appel ne tranche <b>aucune question d'acces</b>. Le
 * cloisonnement a deja ete decide en amont par le service Workflow, qui a rendu ou
 * refuse l'historique. Ce qui se joue ici est un <b>libelle d'affichage</b>.
 *
 * <p>Faire echouer l'historique entier parce qu'un nom manque priverait un
 * controleur interne du dossier qu'il consulte, pour une raison cosmetique. Une
 * carte vide est rendue, et l'historique s'affiche avec ses identifiants nus — ce
 * qui se voit, et se corrige.
 */
@Component
public class IdentiteLectureHttpClient implements IdentiteLectureClient {

    private static final Logger journal = LoggerFactory.getLogger(IdentiteLectureHttpClient.class);

    private static final ParameterizedTypeReference<List<LibelleReponse>> TYPE_LISTE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient clientRest;
    private final String urlServiceIdentite;

    public IdentiteLectureHttpClient(RestClient.Builder constructeurRest,
            @Value("${app.identite.url}") String urlServiceIdentite) {
        this.urlServiceIdentite = urlServiceIdentite;
        this.clientRest = constructeurRest.baseUrl(urlServiceIdentite).build();
    }

    @Override
    public Map<Long, LibelleActeur> libelles(Collection<Long> identifiants,
            String enteteAutorisation) {

        Set<Long> demandes = identifiants == null
                ? Set.of()
                : identifiants.stream().filter(java.util.Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        if (demandes.isEmpty()) {
            return Map.of();
        }

        try {
            List<LibelleReponse> reponse = clientRest.get()
                    .uri(uri -> uri.path("/identite/utilisateurs/libelles")
                            .queryParam("ids", demandes.toArray())
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, enteteAutorisation)
                    .retrieve()
                    .body(TYPE_LISTE);

            if (reponse == null) {
                return Map.of();
            }

            return reponse.stream()
                    .filter(libelle -> libelle.id() != null)
                    .collect(Collectors.toMap(LibelleReponse::id,
                            libelle -> LibelleActeur.de(libelle.id(), libelle.login(),
                                    libelle.nom(), libelle.prenom()),
                            (premier, second) -> premier));

        } catch (RestClientException panne) {
            // Volontairement non bloquant : voir la note de classe. L'incident reste
            // visible dans le journal, et l'historique s'affiche sans les logins.
            journal.warn("Libelles d'acteurs indisponibles ({} sur /identite/utilisateurs/libelles : {}). "
                    + "L'historique est rendu avec les seuls identifiants.",
                    urlServiceIdentite, panne.getMessage());
            return Map.of();
        }
    }

    /** Charge de {@code GET /identite/utilisateurs/libelles}, lue en tolerant reader. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record LibelleReponse(Long id, String login, String nom, String prenom) {
    }

}
