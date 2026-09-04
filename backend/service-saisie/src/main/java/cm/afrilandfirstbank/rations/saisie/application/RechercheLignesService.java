package cm.afrilandfirstbank.rations.saisie.application;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.saisie.infrastructure.RechercheLignesRepository;

/**
 * Repond a la seule question que le service Reporting pose a la Saisie
 * (Sprint 6.1) : <b>quels etats contiennent au moins une ligne repondant a ces
 * criteres ?</b>
 *
 * <h2>Pourquoi la reponse est une liste d'identifiants et rien de plus</h2>
 *
 * <p>La recherche de suivi porte sur des <b>etats mensuels</b>, pas sur des
 * prestations : {@code GET /reporting/demandes} rend des demandes. Un critere de
 * ligne agit donc comme un filtre d'existence sur l'etat, jamais comme un
 * decoupage de son contenu. Rendre les lignes ferait voyager des milliers d'objets
 * dont l'appelant ne garderait que les identifiants.
 *
 * <h2>La portee est resolue ici, jamais recue en parametre</h2>
 *
 * <p>Meme dispositif que cote Workflow. Ce service pourrait s'en dispenser — le
 * Reporting croise de toute facon ces identifiants avec des en-tetes deja
 * cloisonnes — mais l'endpoint est joignable directement sur le port 8082, et un
 * lecteur du circuit pourrait alors sonder l'existence d'un beneficiaire hors de
 * son unite. Le filtre de portee ferme ce chemin sans rien couter au cas nominal.
 *
 * <p>Cela n'est possible que parce que {@code fiche_journaliere} porte
 * {@code code_unite} <b>fige a l'ouverture</b> (Sprint 3.1, migration V3) : la
 * Saisie sait a quelle unite appartient une journee sans redemander quoi que ce
 * soit au service Workflow.
 */
@Service
public class RechercheLignesService {

    private final RechercheLignesRepository rechercheRepository;
    private final PorteeService porteeService;

    public RechercheLignesService(RechercheLignesRepository rechercheRepository,
            PorteeService porteeService) {
        this.rechercheRepository = rechercheRepository;
        this.porteeService = porteeService;
    }

    /**
     * @return les identifiants d'etats retenus, tries et sans doublon ; liste vide
     *         si rien ne correspond — une absence de resultat est une reponse
     *         normale, jamais une erreur
     */
    @Transactional(readOnly = true)
    public List<Long> rechercher(Integer mois, Integer annee, NatureEnum nature,
            SessionEnum session, String beneficiaire, String enteteAutorisation) {

        PorteeAccesUtilisateur portee = porteeService.exigerPortee(enteteAutorisation);

        // null = portee nationale, donc aucun filtre d'unite ; un ensemble vide
        // signifie « aucune unite » et ne rend rien.
        Set<String> codesVisibles = portee.nationale() ? null : portee.codesUnite();

        return rechercheRepository.identifiantsProcessusAvecLigne(
                mois, annee, nature, session, beneficiaire, codesVisibles);
    }

}
