package cm.afrilandfirstbank.rations.workflow.application;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.workflow.domaine.CycleValidation;
import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;
import cm.afrilandfirstbank.rations.workflow.domaine.NomEtapeEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.SeparationTachesException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.EtapeWorkflowRepository;

/**
 * Separation des taches (RG-12) : porte unique du controle, pour les deux niveaux
 * de validation.
 *
 * <h2>La regle appliquee</h2>
 *
 * <p>Une meme personne n'agit qu'<b>une fois</b> sur la version du dossier
 * actuellement dans le circuit. Cela couvre les deux cumuls que RG-12 vise :
 * celui qui a soumis ne valide a aucun niveau, et celui qui a valide a un niveau
 * ne valide pas au niveau suivant.
 *
 * <h2>« La version actuellement dans le circuit » : le cycle courant</h2>
 *
 * <p>Le controle porte sur les etapes du <b>cycle courant</b> — celles qui suivent
 * la derniere {@link NomEtapeEnum#SOUMISSION_AGENT} —, pas sur toute la vie du
 * processus. La raison est un blocage definitif, et non une nuance :
 *
 * <p>La portee d'un chef d'unite est limitee a son unite (Sprint 1.1), et beaucoup
 * d'unites n'ont qu'un seul DA. S'il retourne un etat pour correction, il laisse
 * une ligne {@code etape_workflow} sur ce processus. Compter cette ligne au cycle
 * suivant le rendrait <b>definitivement</b> incapable de valider la version
 * corrigee — c'est-a-dire de faire le travail que son propre retour a demande.
 * Aucun autre valideur n'etant habilite sur l'unite, le dossier resterait bloque
 * sans issue. Le controle cense proteger le circuit le condamnerait.
 *
 * <p>Un retour clot un cycle : ce qui y a ete fait ne pese plus contre personne au
 * cycle suivant. Voir {@link CycleValidation} et
 * {@code docs/decisions/2026-09-01-separation-des-taches-et-cycle.md}.
 *
 * <h2>Le refus est distinct des deux autres refus en 403</h2>
 *
 * <p>{@code SEPARATION_TACHES}, jamais {@code ACCES_REFUSE} ni
 * {@code UTILISATEUR_NON_HABILITE}. La personne refusee ici a le bon role et la
 * bonne portee : <b>rien ne lui manque</b>, et lui repondre « droits
 * insuffisants » l'enverrait reclamer une habilitation qu'elle possede deja. Voir
 * {@link SeparationTachesException}.
 *
 * <h2>Ce que ce service ne fait pas</h2>
 *
 * <p>Il ne verifie ni le role, ni la portee d'acces, ni le statut : ce sont trois
 * autres controles, poses avant lui dans l'ordre du document maitre section 7.3.
 * Il ne lit rien du service Identite non plus — il ne travaille que sur les
 * etapes deja enregistrees et sur l'identifiant local de l'acteur, deja obtenu.
 */
@Service
public class SeparationTachesService {

    private final EtapeWorkflowRepository etapeWorkflowRepository;

    public SeparationTachesService(EtapeWorkflowRepository etapeWorkflowRepository) {
        this.etapeWorkflowRepository = etapeWorkflowRepository;
    }

    /**
     * Cet acteur peut-il agir sur ce dossier, du point de vue de RG-12 ?
     *
     * <p>Lecture du parcours complet plutot que d'une recherche par acteur
     * ({@code findByIdProcessusAndIdActeur}) : le decoupage en cycles a besoin de
     * voir <b>toutes</b> les etapes, y compris celles des autres acteurs, pour
     * situer la derniere soumission. Un parcours compte au plus quelques lignes.
     *
     * @param idProcessus le dossier concerne
     * @param acteur celui qui tente d'agir, tel que le service Identite le connait
     */
    public ResultatSeparationTaches verifier(Long idProcessus, ActeurSignataire acteur) {
        List<EtapeWorkflow> parcours =
                etapeWorkflowRepository.findByIdProcessusOrderByOrdreEtape(idProcessus);

        Optional<EtapeWorkflow> dejaFaite =
                CycleValidation.etapeDeLActeurDansLeCycleCourant(parcours, acteur.id());

        return dejaFaite
                .<ResultatSeparationTaches>map(etape -> new ResultatSeparationTaches.Refuse(
                        etape.getNomEtape(), motifDuRefus(etape.getNomEtape())))
                .orElseGet(ResultatSeparationTaches.Autorise::new);
    }

    /**
     * Meme controle, rendu en refus direct. C'est la forme qu'appellent les services
     * de validation, sur le modele de
     * {@link HabilitationService#exigerHabilitationSurUnite(String, String)}.
     *
     * @throws SeparationTachesException {@code 403 SEPARATION_TACHES}
     */
    public void exigerSeparationDesTaches(Long idProcessus, ActeurSignataire acteur) {
        ResultatSeparationTaches resultat = verifier(idProcessus, acteur);

        switch (resultat) {
            case ResultatSeparationTaches.Autorise ignore -> {
                // rien a faire : la voie est libre
            }
            case ResultatSeparationTaches.Refuse refus ->
                    throw new SeparationTachesException(refus.motif());
        }
    }

    // --- Formulation du refus ------------------------------------------------------

    /**
     * Le message dit ce qui bloque <b>et la suite</b>, comme au Sprint 3.3 cote
     * Saisie : un refus sec laisserait l'utilisateur devant un dossier qu'il croit
     * lui revenir, sans savoir quoi faire.
     */
    private String motifDuRefus(NomEtapeEnum etapeDejaRealisee) {
        return switch (etapeDejaRealisee) {
            case SOUMISSION_AGENT -> "Vous avez soumis cet etat : vous ne pouvez pas le valider "
                    + "vous-meme (RG-12, separation des taches). La validation revient a votre "
                    + "chef d'unite.";
            case VALIDATION_DA -> "Vous avez deja valide cet etat au niveau du chef d'unite : "
                    + "vous ne pouvez pas le valider une seconde fois au niveau du directeur "
                    + "reseau (RG-12, separation des taches). Le second visa revient a un "
                    + "directeur reseau.";
            case VALIDATION_DR -> "Vous avez deja valide cet etat au niveau du directeur "
                    + "reseau : il n'y a pas de troisieme visa (RG-12, separation des taches).";
        };
    }

}
