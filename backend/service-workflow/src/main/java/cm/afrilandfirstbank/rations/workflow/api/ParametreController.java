package cm.afrilandfirstbank.rations.workflow.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cm.afrilandfirstbank.rations.workflow.api.dto.FonctionnalitesActivesResponse;
import cm.afrilandfirstbank.rations.workflow.application.FonctionnaliteService;

/**
 * Les fonctionnalites que le frontend a le droit d'afficher (Sprint 6bis.1).
 *
 * <pre>
 *   GET /parametres/fonctionnalites    tout utilisateur authentifie
 * </pre>
 *
 * <h2>Endpoint interne, hors contrat passerelle</h2>
 *
 * <p>Il ne figure pas parmi les six endpoints du contrat d'API section 5 : il vient
 * du dispositif de drapeau de fonctionnalite
 * ({@code docs/dispositifs_provisoires.md} section 1.4), pas du besoin metier. Le
 * compte de six endpoints reste celui du contrat expose par la passerelle, comme
 * pour les trois endpoints internes deja portes par {@code ProcessusController}.
 *
 * <h2>Aucun role exige, et c'est delibere</h2>
 *
 * <p>Pas de {@code @PreAuthorize} : la chaine de securite exige un jeton valide
 * ({@code anyRequest().authenticated()}), rien de plus. Le frontend appelle cet
 * endpoint <b>au chargement de l'application</b>, avant de savoir quoi afficher, et
 * pour tous les roles a la fois — le reserver a l'agent d'unite obligerait a
 * traiter le {@code 403} des autres roles comme un « non » deguise, ce qui
 * reintroduirait exactement l'ambiguite que le code dedie
 * {@code FONCTIONNALITE_NON_OUVERTE} evite par ailleurs.
 *
 * <p>Le contenu ne le justifie pas davantage : savoir qu'une fonctionnalite du
 * module est fermee n'apprend rien sur un dossier, un montant ou une personne. Meme
 * parti qu'au Sprint 2.4 pour {@code GET /grilles/active}, authentifie mais sans
 * role exige — un bareme n'est pas une information nominative.
 *
 * <h2>Ce que cet endpoint n'est pas</h2>
 *
 * <p>Ce n'est ni une lecture ni une ecriture de {@code parametre_systeme} au sens
 * general : il n'expose ni le seuil d'aiguillage, ni le delai de regularisation, ni
 * aucune valeur brute. Il repond a une seule question — « quels ecrans dois-je
 * montrer ». Exposer la table entiere donnerait au frontend le seuil d'approbation
 * de la banque, qu'il n'a aucune raison de connaitre.
 */
@RestController
@RequestMapping("/parametres")
public class ParametreController {

    private final FonctionnaliteService fonctionnaliteService;

    public ParametreController(FonctionnaliteService fonctionnaliteService) {
        this.fonctionnaliteService = fonctionnaliteService;
    }

    /**
     * Les fonctionnalites ouvertes, telles que {@code parametre_systeme} les porte
     * <b>a cet instant</b>.
     *
     * <p>Aucun cache : le drapeau est relu a chaque appel. C'est ce qui permet a une
     * fermeture d'urgence de prendre effet au rechargement de la page, sans
     * redeploiement ({@code docs/dispositifs_provisoires.md} section 1.5).
     */
    @GetMapping("/fonctionnalites")
    public ResponseEntity<FonctionnalitesActivesResponse> fonctionnalitesActives() {
        return ResponseEntity.ok(
                new FonctionnalitesActivesResponse(fonctionnaliteService.rattrapageActif()));
    }

}
