package cm.afrilandfirstbank.rations.saisie.api;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cm.afrilandfirstbank.rations.saisie.api.dto.CreationLigneRequest;
import cm.afrilandfirstbank.rations.saisie.api.dto.FicheResponse;
import cm.afrilandfirstbank.rations.saisie.api.dto.LigneResponse;
import cm.afrilandfirstbank.rations.saisie.api.dto.ModificationLigneRequest;
import cm.afrilandfirstbank.rations.saisie.api.dto.OuvertureFicheRequest;
import cm.afrilandfirstbank.rations.saisie.application.FicheJournaliereService;
import cm.afrilandfirstbank.rations.saisie.application.FicheJournaliereService.FicheOuverte;
import cm.afrilandfirstbank.rations.saisie.application.LigneAvecBeneficiaire;
import cm.afrilandfirstbank.rations.saisie.application.LigneService;
import cm.afrilandfirstbank.rations.saisie.domaine.FicheJournaliere;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Les cinq endpoints du service Saisie (contrat d'API §3), tous réservés au
 * rôle {@code AGENT_UNITE} — décision constatée à l'étape 1, reprise telle
 * quelle du contrat, sans exception ni endpoint hors périmètre.
 *
 * <h2>Le contrôleur n'orchestre rien</h2>
 *
 * <p>Chaque méthode traduit la requête HTTP en appel de service et le résultat
 * en DTO de sortie. RG-05, la vérification de portée, le contrôle du caractère
 * modifiable, RG-03 et RG-04 vivent tous dans la couche {@code application}
 * ({@link FicheJournaliereService}, {@link LigneService},
 * {@code EtatModifiableService}) — le contrôleur ne les connaît pas.
 *
 * <h2>Le jeton et l'adresse traversent, jamais interprétés ici</h2>
 *
 * <p>L'en-tête {@code Authorization} est relayé tel quel jusqu'aux services
 * distants (Grilles, Workflow, Identité) — décision Sprint 1.3, jamais réémis ni
 * modifié. {@code HttpServletRequest.getRemoteAddr()} nourrit les traces
 * d'audit.
 */
@RestController
@RequestMapping("/saisie")
@PreAuthorize("hasRole('AGENT_UNITE')")
public class SaisieController {

    private final FicheJournaliereService ficheJournaliereService;
    private final LigneService ligneService;

    public SaisieController(FicheJournaliereService ficheJournaliereService, LigneService ligneService) {
        this.ficheJournaliereService = ficheJournaliereService;
        this.ligneService = ligneService;
    }

    /**
     * Ouvre — ou récupère — la fiche d'un jour. RG-05 : idempotent, non
     * destructif.
     *
     * <p><b>{@code 201} à la création, {@code 200} à la récupération.</b> Ce
     * sont deux issues légitimes de la même requête ({@code docs} guide 3.3,
     * point de vigilance) : le client a le droit de les distinguer, par exemple
     * pour avertir l'agent qu'une saisie existait déjà sur ce jour.
     */
    @PostMapping("/fiches")
    public ResponseEntity<FicheResponse> ouvrirFiche(
            @Valid @RequestBody OuvertureFicheRequest requete,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation,
            HttpServletRequest requeteHttp) {

        FicheOuverte resultat = ficheJournaliereService.ouvrir(
                requete.idProcessus(), requete.dateJour(), enteteAutorisation, requeteHttp.getRemoteAddr());

        FicheResponse reponse = FicheResponse.depuis(resultat.fiche(), versLignes(resultat.lignes()));
        HttpStatus statut = resultat.creee() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(statut).body(reponse);
    }

    /**
     * Lignes d'une fiche, bénéficiaire et sous-total inclus (étape 5). Une seule
     * lecture réseau : le code unité de la portée d'accès est celui recopié sur
     * la fiche (migration V3), pas redemandé au service Workflow.
     */
    @GetMapping("/fiches/{id}/lignes")
    public ResponseEntity<FicheResponse> listerLignes(
            @PathVariable("id") Long idFiche,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation) {

        FicheJournaliere fiche = ficheJournaliereService.consulter(idFiche, enteteAutorisation);
        List<LigneAvecBeneficiaire> lignes = ligneService.listerLignes(idFiche);

        return ResponseEntity.ok(FicheResponse.depuis(fiche, versLignes(lignes)));
    }

    @PostMapping("/lignes")
    public ResponseEntity<LigneResponse> creerLigne(
            @Valid @RequestBody CreationLigneRequest requete,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation,
            HttpServletRequest requeteHttp) {

        LigneAvecBeneficiaire resultat = ligneService.creer(
                requete.versCommande(), enteteAutorisation, requeteHttp.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(LigneResponse.depuis(resultat.ligne(), resultat.beneficiaire()));
    }

    /**
     * Modification avant soumission : nature et session seulement (décision
     * prise avec l'utilisateur, étape 1). Traitée comme une création — RG-03 et
     * RG-04 rejouent intégralement.
     */
    @PutMapping("/lignes/{id}")
    public ResponseEntity<LigneResponse> modifierLigne(
            @PathVariable("id") Long idLigne,
            @Valid @RequestBody ModificationLigneRequest requete,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation) {

        LigneAvecBeneficiaire resultat = ligneService.modifier(
                idLigne, requete.nature(), requete.session(), enteteAutorisation);

        return ResponseEntity.ok(LigneResponse.depuis(resultat.ligne(), resultat.beneficiaire()));
    }

    /**
     * Suppression avant soumission. {@code 204} sans corps ; une seconde
     * suppression de la même ligne rend {@code 404}, la suppression n'étant pas
     * rendue idempotente (voir {@code LigneIntrouvableException}).
     */
    @DeleteMapping("/lignes/{id}")
    public ResponseEntity<Void> supprimerLigne(
            @PathVariable("id") Long idLigne,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation) {

        ligneService.supprimer(idLigne, enteteAutorisation);
        return ResponseEntity.noContent().build();
    }

    private List<LigneResponse> versLignes(List<LigneAvecBeneficiaire> lignes) {
        return lignes.stream()
                .map(l -> LigneResponse.depuis(l.ligne(), l.beneficiaire()))
                .toList();
    }

}
