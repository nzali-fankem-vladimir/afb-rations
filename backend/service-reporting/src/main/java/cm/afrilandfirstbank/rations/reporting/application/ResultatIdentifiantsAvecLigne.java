package cm.afrilandfirstbank.rations.reporting.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Les issues d'une recherche par criteres de ligne aupres du service Saisie
 * (Sprint 6.1).
 *
 * <p>{@link Obtenus} porte un <b>ensemble</b> et non une liste : le seul usage aval
 * est une intersection avec les en-tetes du Workflow, et une intersection sur une
 * liste serait quadratique. Le nombre d'etats candidats se compte en centaines ou
 * en milliers ; l'ecart n'est pas theorique.
 */
public sealed interface ResultatIdentifiantsAvecLigne {

    record Obtenus(Set<Long> identifiants) implements ResultatIdentifiantsAvecLigne {

        public Obtenus {
            identifiants = Set.copyOf(identifiants);
        }

        public static Obtenus de(List<Long> identifiants) {
            return new Obtenus(new LinkedHashSet<>(identifiants));
        }
    }

    /** Refus de droit prononce par le service Saisie, relaye tel quel. */
    record AccesRefuse(String motif) implements ResultatIdentifiantsAvecLigne {
    }

    record ServiceIndisponible(String motifTechnique) implements ResultatIdentifiantsAvecLigne {
    }

}
