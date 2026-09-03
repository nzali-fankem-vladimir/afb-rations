package cm.afrilandfirstbank.rations.workflow.infrastructure.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Controle le secret partage sur l'endpoint interne de mise a jour du statut d'integration
 * (Sprint 5.2). <b>Dispositif provisoire</b>, voir
 * {@code docs/dispositifs_provisoires.md}.
 *
 * <h2>Pourquoi un secret partage, et pas un jeton</h2>
 *
 * <p>Toute la chaine du Sprint 5.1 relaie le jeton de l'utilisateur final (doctrine Sprint
 * 1.3). Cet appel-ci ne le peut pas : il nait d'un <b>message Kafka</b>, arrive plusieurs
 * minutes ou plusieurs heures apres la cloture, et <b>aucun utilisateur n'est derriere
 * lui</b>. La doctrine du relais ne s'applique donc pas, faute d'utilisateur final a
 * relayer.
 *
 * <p>Le realm {@code afb-rations-dev} ne porte par ailleurs qu'un client public,
 * {@code serviceAccountsEnabled: false} : il n'existe aujourd'hui aucune identite machine a
 * presenter. C'est le point en attente ouvert au Sprint 5.1 et confie a la DSI.
 *
 * <p>L'alternative — declarer la route {@code permitAll}, comme Swagger — a ete ecartee :
 * ce n'est pas une lecture de documentation, c'est une <b>ecriture sur le statut de
 * paiement d'un etat</b>, et quiconque atteindrait le port pourrait alors declarer n'importe
 * quel etat integre ou rejete.
 *
 * <h2>Le secret ne fuit par aucun canal</h2>
 *
 * <p>Sa valeur n'apparait <b>nulle part</b> : ni dans un message de journal, ni dans un
 * message d'exception, ni dans un evenement d'audit, ni dans un corps de reponse. Pas meme
 * sa longueur ni son prefixe — un prefixe est deja une fuite. Le module a l'habitude de
 * journaliser des motifs detailles ({@code AUDIT PERDU}, {@code TRANSMISSION MANQUEE},
 * {@code SEUIL INDISPONIBLE}) ; un garde-fou qui fuirait par le canal meme cense le
 * surveiller ne serait pas un garde-fou. Un test de garde relit ce fichier pour le
 * verifier.
 *
 * <h2>Comparaison a temps constant</h2>
 *
 * <p>{@link MessageDigest#isEqual(byte[], byte[])} plutot que {@code String.equals}, qui
 * s'arrete au premier octet different. La difference de duree est infime, mais elle est
 * mesurable a la repetition et permet de reconstituer un secret octet par octet. Le cout
 * est nul, la fermeture du canal est gratuite.
 *
 * <h2>Ni {@code @Component}, ni {@code @Bean} — deliberement</h2>
 *
 * <p>Un filtre declare en bean serait <b>enregistre automatiquement sur toutes les
 * requetes</b> par le conteneur de servlets, et exigerait alors le secret sur les six
 * endpoints du contrat. Il est donc instancie a la main dans {@code SecurityConfig} et
 * ajoute a la seule chaine de securite de l'endpoint interne : sa portee se lit a
 * l'endroit ou elle est decidee, et ne peut pas s'etendre par accident.
 */
public class FiltreCleInterne extends OncePerRequestFilter {

    private static final Logger journal = LoggerFactory.getLogger(FiltreCleInterne.class);

    /** Nom de l'en-tete. <b>Seul le nom apparait dans les journaux ; la valeur, jamais.</b> */
    public static final String EN_TETE_CLE_INTERNE = "X-Cle-Interne";

    private final byte[] cleAttendue;

    public FiltreCleInterne(String cleInterne) {
        this.cleAttendue = cleInterne.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
            FilterChain chaine) throws ServletException, IOException {

        String clePresentee = requete.getHeader(EN_TETE_CLE_INTERNE);

        if (clePresentee == null
                || !MessageDigest.isEqual(
                        clePresentee.getBytes(StandardCharsets.UTF_8), cleAttendue)) {

            // Le message ne dit PAS lequel des deux cas s'est produit : distinguer
            // « en-tete absent » de « en-tete refuse » apprendrait deja quelque chose a qui
            // sonde la route.
            journal.warn("Appel refuse sur {} : en-tete {} absent ou invalide.",
                    requete.getRequestURI(), EN_TETE_CLE_INTERNE);

            reponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            reponse.setContentType("application/json;charset=UTF-8");
            reponse.getWriter().write("""
                    {"status":401,"code":"CLE_INTERNE_INVALIDE",\
                    "message":"En-tete X-Cle-Interne absent ou invalide.","path":"%s"}"""
                    .formatted(requete.getRequestURI()));
            return;
        }

        chaine.doFilter(requete, reponse);
    }

}
