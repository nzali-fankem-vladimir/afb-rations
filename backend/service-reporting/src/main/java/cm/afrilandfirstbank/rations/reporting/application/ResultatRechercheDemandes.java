package cm.afrilandfirstbank.rations.reporting.application;

import java.util.List;

import cm.afrilandfirstbank.rations.reporting.domaine.EnTeteDemande;

/**
 * Les issues d'une recherche d'en-tetes aupres du service Workflow (Sprint 6.1).
 *
 * <p>Type <b>scelle</b> : le {@code switch} qui l'applique est exhaustif, et une
 * cinquieme issue ajoutee plus tard fera echouer la compilation aux endroits exacts
 * a corriger. Meme dispositif qu'aux Sprints 5.1 et 5.3, ou il a designe lui-meme
 * les deux endroits ou une nouvelle issue devait etre traitee.
 *
 * <h2>Quatre issues, parce qu'elles appellent quatre gestes differents</h2>
 *
 * <p>Un resultat, un refus de droit, un volume excessif et une panne ne se disent
 * pas de la meme facon a l'utilisateur : « voici », « demandez une habilitation »,
 * « affinez votre recherche », « reessayez ». Les fondre en un seul cas — ou pire,
 * en une liste vide — ferait conclure a une absence de dossiers dans trois cas sur
 * quatre.
 */
public sealed interface ResultatRechercheDemandes {

    /** Les en-tetes, tries du plus recent au plus ancien. */
    record Obtenue(List<EnTeteDemande> contenu) implements ResultatRechercheDemandes {

        public Obtenue {
            contenu = List.copyOf(contenu);
        }
    }

    /**
     * Le volume depasse la borne : le service Workflow a rendu le compte sans le
     * contenu.
     *
     * <p><b>Ce n'est pas un resultat vide.</b> {@code nombreTotal} est exact et sert
     * a formuler un refus qui nomme ce qu'il ne montre pas.
     */
    record TropDeResultats(long nombreTotal) implements ResultatRechercheDemandes {
    }

    /**
     * Refus de droit prononce par le service Workflow, relaye tel quel. Un
     * {@code 403} venu de l'aval reste un {@code 403}, jamais deguise en panne
     * (doctrine Sprint 5.3).
     */
    record AccesRefuse(String motif) implements ResultatRechercheDemandes {
    }

    /** Le service Workflow n'a pas repondu, ou a repondu quelque chose d'inexploitable. */
    record ServiceIndisponible(String motifTechnique) implements ResultatRechercheDemandes {
    }

}
