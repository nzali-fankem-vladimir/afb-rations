package cm.afrilandfirstbank.rations.reporting.application;

import java.time.LocalDate;
import cm.afrilandfirstbank.rations.reporting.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.reporting.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.reporting.domaine.StatutEnum;

/**
 * Les criteres de la recherche multicritere (CT-30), reunis en un objet.
 *
 * <h2>Pourquoi un objet plutot que cinq parametres</h2>
 *
 * <p>Parce qu'il porte la question qui commande toute la strategie d'agregation :
 * {@link #porteSurLesLignes()}. C'est elle qui decide si le service Saisie est
 * appele — un appel de moins dans le cas courant, et une panne de la Saisie sans
 * effet sur une recherche qui ne la concerne pas. La laisser se reconstituer chez
 * l'appelant, ou pire deux fois, serait le genre d'oubli qui ne se voit qu'en
 * production.
 *
 * <p>La periode est portee par {@code mois} et {@code annee} separement, comme dans
 * {@code processus_mensuel}. Le contrat expose {@code periode=2026-08} : la
 * traduction se fait au controleur, ou vivent les questions de forme.
 *
 * @param dateDebut borne de periode, ou nul
 * @param dateFin borne de periode, ou nul
 * @param codeUnite cinq chiffres du referentiel des codes guichets, ou nul
 * @param nature RATION ou TRANSPORT, ou nul
 * @param session JOUR ou SOIR, ou nul
 * @param beneficiaire numero de compte courant exact, ou fragment de nom, ou nul
 * @param statut filtre d'avancement du dossier (Sprint 7F.5) -- relaye tel quel a
 *        {@code GET /processus/recherche}, qui l'accepte deja depuis le Sprint 6.1 ;
 *        vit dans {@code rations_workflow}, jamais un critere de ligne
 */
public record CriteresRecherche(
        LocalDate dateDebut,
        LocalDate dateFin,
        String codeUnite,
        NatureEnum nature,
        SessionEnum session,
        String beneficiaire,
        StatutEnum statut) {

    /** Aucun filtre : toutes les demandes accessibles a l'utilisateur. */
    public static CriteresRecherche aucun() {
        return new CriteresRecherche(null, null, null, null, null, null, null);
    }

    /**
     * La recherche porte-t-elle sur au moins un critere qui vit dans la base de la
     * Saisie ?
     *
     * <p>Si non, la Saisie n'est pas appelee du tout : la recherche se resout en un
     * seul appel, et une panne de ce service reste sans effet.
     */
    public boolean porteSurLesLignes() {
        return nature != null || session != null
                || (beneficiaire != null && !beneficiaire.isBlank());
    }

}
