package cm.afrilandfirstbank.rations.workflow.domaine;

import java.util.List;
import java.util.Optional;

/**
 * Decoupe le parcours d'un etat en <b>cycles de validation</b>, et designe le
 * cycle courant. Support de RG-12 (separation des taches).
 *
 * <h2>Ce qu'est un cycle</h2>
 *
 * <p>Un cycle commence a une {@link NomEtapeEnum#SOUMISSION_AGENT} et s'acheve
 * soit par une cloture, soit par un retour a l'agent (RG-11). Un etat retourne,
 * corrige, puis resoumis ouvre donc un <b>nouveau</b> cycle : le parcours d'un
 * meme processus peut en compter plusieurs.
 *
 * <pre>
 *   ordre 1  SOUMISSION_AGENT  VALIDEE     -- cycle 1
 *   ordre 2  VALIDATION_DA     RETOURNEE   -- cycle 1, clos par le retour
 *   ordre 3  SOUMISSION_AGENT  VALIDEE     -- cycle 2, courant
 *   ordre 4  VALIDATION_DA     VALIDEE     -- cycle 2
 * </pre>
 *
 * <h2>Pourquoi RG-12 ne regarde que le cycle courant</h2>
 *
 * <p>Parce qu'une lecture sur la vie entiere du processus produirait un blocage
 * definitif, et non un controle. La portee d'un chef d'unite est limitee a sa
 * propre unite (Sprint 1.1) : beaucoup d'unites n'ont qu'un seul DA. S'il
 * retourne l'etat de juillet pour une erreur de saisie, il laisse derriere lui une
 * ligne {@code etape_workflow} sur ce processus ; compter cette ligne au cycle
 * suivant l'empecherait <b>a vie</b> de valider la version corrigee — c'est-a-dire
 * exactement le travail que le retour lui demande de refaire. Aucune echappatoire
 * n'existerait : le dossier resterait bloque.
 *
 * <p>L'esprit de RG-12 est intact : <b>sur la version du dossier qui est
 * actuellement dans le circuit</b>, une personne n'agit qu'une fois. Ce qu'elle a
 * fait sur une version anterieure, annulee par un retour, ne pese plus contre
 * elle. Voir {@code docs/decisions/2026-09-01-separation-des-taches-et-cycle.md}.
 *
 * <h2>Un parcours sans aucune soumission</h2>
 *
 * <p>Ne devrait pas exister : la soumission est le premier pas de tout circuit.
 * Si le cas se presente, le cycle courant est <b>tout le parcours</b> — le choix
 * conservateur, qui refuse plutot que de laisser agir deux fois par omission.
 */
public final class CycleValidation {

    private CycleValidation() {
        // classe utilitaire
    }

    /**
     * Les etapes du cycle en cours : celles dont le rang est superieur ou egal a
     * celui de la derniere soumission de l'agent.
     *
     * @param parcours toutes les etapes du processus, triees par
     *        {@code ordre_etape} croissant
     *        ({@code findByIdProcessusOrderByOrdreEtape})
     */
    public static List<EtapeWorkflow> etapesDuCycleCourant(List<EtapeWorkflow> parcours) {
        if (parcours == null || parcours.isEmpty()) {
            return List.of();
        }

        int debut = rangDeLaDerniereSoumission(parcours);

        return parcours.stream()
                .filter(etape -> etape.getOrdreEtape() >= debut)
                .toList();
    }

    /**
     * L'etape que cet acteur a deja realisee dans le cycle courant, s'il en a une.
     *
     * <p>Une valeur presente est le refus de RG-12 : la personne a deja agi sur la
     * version du dossier qui est dans le circuit. L'etape rendue sert a formuler le
     * refus — dire « vous avez soumis cet etat » ou « vous l'avez deja valide au
     * premier niveau » n'appelle pas la meme suite pour l'utilisateur.
     *
     * <p>Rend la <b>premiere</b> etape trouvee : un acteur ne peut pas en avoir
     * deux dans un cycle, precisement parce que cette regle l'en empeche.
     */
    public static Optional<EtapeWorkflow> etapeDeLActeurDansLeCycleCourant(
            List<EtapeWorkflow> parcours, Long idActeur) {

        if (idActeur == null) {
            return Optional.empty();
        }

        return etapesDuCycleCourant(parcours).stream()
                .filter(etape -> idActeur.equals(etape.getIdActeur()))
                .findFirst();
    }

    // --- Mecanique interne -------------------------------------------------------

    /**
     * Rang de la derniere {@link NomEtapeEnum#SOUMISSION_AGENT} du parcours.
     *
     * <p>Le parcours est parcouru a l'envers : la derniere soumission est celle qui
     * ouvre le cycle courant, pas la premiere. Aucune soumission trouvee : on rend
     * le rang du premier pas, ce qui fait du parcours entier le cycle courant.
     */
    private static int rangDeLaDerniereSoumission(List<EtapeWorkflow> parcours) {
        for (int position = parcours.size() - 1; position >= 0; position--) {
            EtapeWorkflow etape = parcours.get(position);
            if (etape.getNomEtape() == NomEtapeEnum.SOUMISSION_AGENT) {
                return etape.getOrdreEtape();
            }
        }
        return parcours.get(0).getOrdreEtape();
    }

}
