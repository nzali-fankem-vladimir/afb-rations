package cm.afrilandfirstbank.rations.workflow.infrastructure;

import java.time.LocalDate;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;

/**
 * Criteres combinables de la recherche d'etats mensuels (Sprint 6.1). Chaque
 * filtre nul est <b>omis</b> plutot que traduit en condition SQL — meme facture
 * que {@code UtilisateurSpecifications} du service Identite (Sprint 1.2).
 *
 * <p><b>Le filtre de portee n'est pas optionnel.</b> Il est pose par
 * {@link #avecFiltres} a partir de la portee resolue depuis le jeton, et il n'y a
 * aucun chemin pour l'omettre : ne pas le passer ne compile pas. C'est
 * volontaire — un filtre de cloisonnement qu'on peut oublier finit par etre
 * oublie.
 */
public final class ProcessusSpecifications {

    private ProcessusSpecifications() {
    }

    /**
     * @param codesUniteVisibles unites que l'appelant peut voir ; {@code null} pour
     *        une portee <b>nationale</b>, seule facon d'obtenir l'absence de filtre
     *        d'unite. Un ensemble <i>vide</i> signifie « aucune unite » et ne rend
     *        rien, ce qui n'est pas la meme chose.
     */
    public static Specification<ProcessusMensuel> avecFiltres(LocalDate dateDebut, LocalDate dateFin,
            String codeUnite, StatutEnum statut, Set<String> codesUniteVisibles) {

        return Specification.allOf(
                finApres(dateDebut),
                debutAvant(dateFin),
                codeUnite(codeUnite),
                statut(statut),
                portee(codesUniteVisibles));
    }

    /**
     * Les deux filtres de periode se lisent ensemble : ils selectionnent les etats
     * dont la periode <b>recoupe</b> la plage demandee, pas seulement ceux qui y
     * tiennent entierement.
     *
     * <p>C'est le comportement voulu : un rapport sur le mois de septembre doit
     * montrer la semaine du 29 septembre au 5 octobre, qui commence en septembre.
     * L'exclure parce qu'elle deborde donnerait un total incomplet sans le dire.
     */
    private static Specification<ProcessusMensuel> finApres(LocalDate dateDebut) {
        return (racine, requete, cb) ->
                dateDebut == null ? null : cb.greaterThanOrEqualTo(racine.get("dateFin"), dateDebut);
    }

    private static Specification<ProcessusMensuel> debutAvant(LocalDate dateFin) {
        return (racine, requete, cb) ->
                dateFin == null ? null : cb.lessThanOrEqualTo(racine.get("dateDebut"), dateFin);
    }

    private static Specification<ProcessusMensuel> codeUnite(String codeUnite) {
        return (racine, requete, cb) -> codeUnite == null ? null : cb.equal(racine.get("codeUnite"), codeUnite);
    }

    private static Specification<ProcessusMensuel> statut(StatutEnum statut) {
        return (racine, requete, cb) -> statut == null ? null : cb.equal(racine.get("statut"), statut);
    }

    /**
     * Restriction aux unites visibles.
     *
     * <p>Un ensemble vide n'est pas traduit en {@code in ()} — syntaxe invalide chez
     * la plupart des bases — mais en une condition <b>toujours fausse</b>. Le
     * resultat est le meme qu'une portee ne couvrant rien : zero ligne, sans erreur
     * technique et sans filtre silencieusement neutralise.
     */
    private static Specification<ProcessusMensuel> portee(Set<String> codesUniteVisibles) {
        return (racine, requete, cb) -> {
            if (codesUniteVisibles == null) {
                return null;
            }
            if (codesUniteVisibles.isEmpty()) {
                return cb.disjunction();
            }
            return racine.get("codeUnite").in(codesUniteVisibles);
        };
    }

}
