package cm.afrilandfirstbank.rations.workflow.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cm.afrilandfirstbank.rations.workflow.api.dto.FonctionnalitesActivesResponse;
import cm.afrilandfirstbank.rations.workflow.api.dto.ModificationParametreRequest;
import cm.afrilandfirstbank.rations.workflow.api.dto.ParametreResponse;
import cm.afrilandfirstbank.rations.workflow.application.FonctionnaliteService;
import cm.afrilandfirstbank.rations.workflow.application.ParametreAdminService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

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
    private final ParametreAdminService parametreAdminService;

    public ParametreController(FonctionnaliteService fonctionnaliteService,
            ParametreAdminService parametreAdminService) {
        this.fonctionnaliteService = fonctionnaliteService;
        this.parametreAdminService = parametreAdminService;
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

    /**
     * Consulte un parametre par son code, quel qu'il soit (guide 7F.6, etape
     * 6, ajout backend scope). Reserve a l'ADMIN : {@code SEUIL_AIGUILLAGE_DR}
     * commande le niveau d'approbation requis par la banque, ce n'est pas une
     * information a exposer largement.
     *
     * <p>Sert l'ecran d'administration a afficher la valeur courante avant
     * modification -- une ecriture a l'aveugle exposerait a un ecrasement non
     * voulu. Aucune trace d'audit : une lecture de travail n'en publie pas.
     */
    @GetMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ParametreResponse> consulter(@PathVariable String code) {
        return ResponseEntity.ok(ParametreResponse.depuis(parametreAdminService.consulter(code)));
    }

    /**
     * Modifie la valeur d'un des trois parametres modifiables (guide 7F.6,
     * etape 6, ajout backend scope tranche avec l'utilisateur).
     *
     * <p><b>Endpoint additif, hors des 26 endpoints du contrat d'API section
     * 11 tel qu'arrete a l'origine.</b> Reserve a l'ADMIN, jamais expose sans
     * jeton : contrairement a {@code GET /fonctionnalites}, c'est une
     * ecriture sur une valeur qui commande le niveau d'approbation requis par
     * la banque ({@code SEUIL_AIGUILLAGE_DR}) ou l'imputation comptable
     * ({@code COMPTE_CHARGE_RATIONS}).
     *
     * <p>Trois codes seulement : {@link ParametreAdminService#CODES_MODIFIABLES}.
     * {@code RATTRAPAGE_ACTIF} reste hors de portee de cet endpoint (voir
     * {@code ParametreNonModifiableException}).
     */
    @PutMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ParametreResponse> modifier(
            @PathVariable String code,
            @Valid @RequestBody ModificationParametreRequest requete,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation,
            HttpServletRequest requeteHttp) {

        return ResponseEntity.ok(ParametreResponse.depuis(
                parametreAdminService.modifier(code, requete.valeur(), enteteAutorisation,
                        requeteHttp.getRemoteAddr())));
    }

}
