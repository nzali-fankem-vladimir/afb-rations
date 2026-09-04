package cm.afrilandfirstbank.rations.identite.api;

import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cm.afrilandfirstbank.rations.identite.api.dto.LibelleUtilisateurResponse;
import cm.afrilandfirstbank.rations.identite.domaine.exception.LotTropGrandException;
import cm.afrilandfirstbank.rations.identite.infrastructure.persistence.UtilisateurRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Traduit un lot d'identifiants locaux en libelles lisibles.
 * <b>Endpoint interne, hors contrat passerelle</b> (Sprint 6.1).
 *
 * <pre>
 *   GET /identite/utilisateurs/libelles?ids=1,3,7
 * </pre>
 *
 * <h2>Pourquoi il existe</h2>
 *
 * <p>{@code etape_workflow.id_acteur} ne stocke qu'un nombre. Un historique de
 * validations qui afficherait « valide par l'acteur 7 » n'aurait aucune valeur
 * pour un controle interne, qui devrait rapprocher les identifiants a la main.
 *
 * <p>Les deux autres chemins ont ete ecartes : {@code GET /identite/utilisateurs}
 * est reserve a l'ADMIN et pagine, il ne peut pas servir un lecteur du circuit ;
 * et figer un libelle dans {@code etape_workflow} (doctrine Sprint 2.2 pour les
 * grilles) aurait impose une migration, la modification des trois chemins
 * d'ecriture du circuit, et aurait laisse sans libelle toutes les etapes deja
 * enregistrees.
 *
 * <h2>Un lot, jamais un appel par identifiant</h2>
 *
 * <p>Un historique compte quelques etapes et deux ou trois acteurs distincts. Le
 * consommateur dedoublonne et demande tout en <b>un seul appel</b> : c'est le
 * meme principe qu'a l'agregation du Reporting, ou une strategie appelant un
 * service par ligne de resultat est explicitement proscrite.
 *
 * <h2>Ce que la reponse ne garantit pas</h2>
 *
 * <p><b>Un identifiant inconnu est simplement absent de la reponse</b>, sans
 * erreur. Un compte peut avoir ete supprime de la projection locale depuis la
 * validation qu'il a signee ; c'est au consommateur de rendre l'etape avec son
 * seul identifiant plutot que de faire echouer tout l'historique. Refuser en
 * bloc priverait le controle interne d'un dossier entier pour un compte parti.
 *
 * <h2>Protection</h2>
 *
 * <p>Route OAuth2 ordinaire, ouverte a tout utilisateur authentifie : elle ne
 * rend qu'un annuaire de libelles, deja visible sur les documents signes, et
 * ne porte ni role, ni code unite, ni aucune donnee de dossier. Le lot est
 * <b>borne a {@value #LOT_MAXIMUM} identifiants</b> pour qu'elle ne devienne pas
 * un moyen d'aspirer la projection locale.
 */
@RestController
@RequestMapping("/identite/utilisateurs/libelles")
@Tag(name = "Libelles utilisateurs",
        description = "Endpoint interne : traduit un lot d'identifiants locaux en logins")
public class LibelleUtilisateurController {

    /** Au-dela, la demande n'est plus un historique : elle est un export d'annuaire. */
    static final int LOT_MAXIMUM = 200;

    private final UtilisateurRepository utilisateurRepository;

    public LibelleUtilisateurController(UtilisateurRepository utilisateurRepository) {
        this.utilisateurRepository = utilisateurRepository;
    }

    @GetMapping
    @Operation(summary = "Traduit un lot d'identifiants locaux en logins",
            description = """
                    Endpoint **interne**, destine aux autres services — le service Reporting
                    l'appelle pour nommer les acteurs d'un historique de validations.

                    **Role requis :** aucun. La reponse ne porte que login, nom et prenom :
                    ni role, ni code unite, ni donnee de dossier.

                    **Un identifiant inconnu est absent de la reponse, sans erreur.** Un compte
                    supprime depuis la validation qu'il a signee ne doit pas faire echouer tout
                    l'historique.

                    **Lot borne a 200 identifiants**, au-dela la demande est refusee.
                    """)
    public ResponseEntity<List<LibelleUtilisateurResponse>> traduire(
            @RequestParam(required = false) List<Long> ids) {

        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        // Dedoublonnage avant le controle de taille : un appelant qui repete le meme
        // acteur sur dix etapes ne demande qu'un libelle, pas dix.
        Set<Long> identifiants = new LinkedHashSet<>(ids);

        if (identifiants.size() > LOT_MAXIMUM) {
            throw new LotTropGrandException(
                    "Le parametre ids porte " + identifiants.size()
                            + " identifiants distincts, au-dela de la limite de " + LOT_MAXIMUM
                            + ". Decoupez la demande.");
        }

        return ResponseEntity.ok(utilisateurRepository.findAllById(identifiants).stream()
                .map(LibelleUtilisateurResponse::depuis)
                .toList());
    }

}
