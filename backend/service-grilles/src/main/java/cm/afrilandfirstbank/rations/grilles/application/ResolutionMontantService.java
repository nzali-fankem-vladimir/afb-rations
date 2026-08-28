package cm.afrilandfirstbank.rations.grilles.application;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.grilles.domaine.GrilleTarifaire;
import cm.afrilandfirstbank.rations.grilles.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.ResolutionMontant;
import cm.afrilandfirstbank.rations.grilles.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.IncoherenceGrilleException;
import cm.afrilandfirstbank.rations.grilles.infrastructure.GrilleTarifaireRepository;

/**
 * Resolution du montant applicable a une prestation (RG-03, Sprint 2.4).
 *
 * <p>C'est ce que le service Grilles rend au reste du module : le service Saisie
 * demandera « quel montant s'applique a une ration de jour le 10 juillet ? »
 * avant de figer une ligne de prestation. Le montant n'est jamais saisi ni
 * transmis par le client, il est repris d'ici.
 *
 * <p><b>A la date de la prestation, pas a la date du jour.</b> C'est le point
 * central du sous-sprint, et le plus facile a manquer : les deux dates
 * coincident tant qu'on saisit la journee courante, et le defaut reste alors
 * invisible. Une saisie du 10 juillet effectuee le 27 aout doit etre tarifee au
 * montant de juillet ; resoudre a {@code LocalDate.now()} produirait un montant
 * faux sans declencher la moindre erreur. L'enjeu grandit au Sprint 6bis, ou un
 * etat complementaire regularise par construction une periode close.
 *
 * <p><b>Aucun appel reseau, aucune ecriture.</b> La resolution lit la base
 * locale et rien d'autre : le service Grilles ne depend d'aucun autre service.
 * Une panne d'identite empeche de proposer un tarif, jamais d'en appliquer un
 * (decision Sprint 2.2). Aucun evenement d'audit n'est publie : consulter un
 * tarif n'est pas une action sensible, et le journal serait noye par une
 * publication par ligne saisie.
 */
@Service
public class ResolutionMontantService {

    private static final Logger log = LoggerFactory.getLogger(ResolutionMontantService.class);

    private final GrilleTarifaireRepository grilleTarifaireRepository;

    public ResolutionMontantService(GrilleTarifaireRepository grilleTarifaireRepository) {
        this.grilleTarifaireRepository = grilleTarifaireRepository;
    }

    /**
     * Montant applicable au couple (nature, session) a la date de la prestation.
     *
     * <p>La grille retenue est celle au statut {@code ACTIVE} dont la periode de
     * validite couvre la date : {@code dateDebut <= date}, et {@code dateFin}
     * nulle ou {@code >= date}. Les deux bornes sont inclusives — un {@code <} au
     * lieu d'un {@code <=} decalerait la bascule d'une journee, et cette
     * journee-la produirait un montant faux.
     *
     * <p>Une grille {@code EN_ATTENTE_DRH} ou {@code REJETEE} n'est jamais
     * retenue (CT-25) : tant que la DRH n'a pas tranche, une proposition est sans
     * effet sur les saisies, meme apres sa propre date de debut.
     *
     * <p>Trois issues, et trois seulement :
     * <ul>
     *   <li><b>une</b> grille couvre la date — cas nominal, le montant vient
     *       d'elle et elle est nommee dans la reponse ;</li>
     *   <li><b>aucune</b> — indisponibilite explicite. Le service ne retourne pas
     *       zero et ne leve pas d'erreur technique : c'est une reponse metier, que
     *       le service Saisie traduira en refus de ligne
     *       ({@code 422 GRILLE_INDISPONIBLE}, US-05 et CT-10) ;</li>
     *   <li><b>deux ou plus</b> — incoherence de donnees, refusee et signalee,
     *       jamais arbitree.</li>
     * </ul>
     *
     * @param nature RATION ou TRANSPORT (RG-01)
     * @param session JOUR ou SOIR (RG-02)
     * @param date date de la <b>prestation</b>, jamais la date de l'appel
     */
    @Transactional(readOnly = true)
    public ResolutionMontant resoudre(NatureEnum nature, SessionEnum session, LocalDate date) {
        List<GrilleTarifaire> couvrantes =
                grilleTarifaireRepository.rechercherGrillesCouvrant(nature, session, date);

        if (couvrantes.isEmpty()) {
            return ResolutionMontant.indisponible(nature, session, date);
        }
        if (couvrantes.size() > 1) {
            throw incoherence(nature, session, date, couvrantes);
        }
        return ResolutionMontant.trouve(nature, session, date, couvrantes.getFirst());
    }

    /**
     * Signale un chevauchement de periodes, en log puis en exception.
     *
     * <p>Le prefixe {@code INCOHERENCE GRILLE} est une chaine stable, cherchable
     * dans les journaux — meme intention que le prefixe {@code AUDIT PERDU} de
     * {@code rations-audit-commun} pour ses cas graves. Un incident de donnees
     * qu'on ne sait pas retrouver dans les logs n'est pas vraiment signale.
     *
     * <p>Le log nomme les identifiants et les periodes en conflit : sans eux,
     * l'exploitant saurait qu'il y a un probleme sans savoir quelles lignes
     * corriger.
     */
    private IncoherenceGrilleException incoherence(NatureEnum nature, SessionEnum session,
            LocalDate date, List<GrilleTarifaire> couvrantes) {

        String detail = couvrantes.stream()
                .map(grille -> "#%d [%s -> %s] %d FCFA".formatted(
                        grille.getId(), grille.getDateDebut(),
                        grille.getDateFin() == null ? "sans terme" : grille.getDateFin(),
                        grille.getMontantFcfa()))
                .reduce((gauche, droite) -> gauche + ", " + droite)
                .orElse("");

        log.error("INCOHERENCE GRILLE : {} grilles ACTIVE couvrent {} / {} au {}. "
                + "Les periodes se chevauchent, aucun montant ne peut etre resolu sans arbitrage. "
                + "Grilles en conflit : {}. A corriger en base : une seule grille doit couvrir une date donnee.",
                couvrantes.size(), nature, session, date, detail);

        return new IncoherenceGrilleException(
                ("Plusieurs grilles actives couvrent %s / %s au %s (%s). Le montant applicable ne peut "
                        + "pas etre determine sans arbitrage : aucune valeur n'est retournee plutot qu'une "
                        + "valeur possiblement fausse. Signalez cette incoherence a l'administrateur.")
                        .formatted(nature, session, date, detail));
    }

}
