package cm.afrilandfirstbank.rations.saisie.infrastructure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Repository;

import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

/**
 * Rend les <b>identifiants d'etats</b> qui contiennent au moins une ligne
 * repondant a des criteres de prestation (Sprint 6.1).
 *
 * <h2>Ce que cette requete rend, et pourquoi si peu</h2>
 *
 * <p>Uniquement des {@code id_processus}, jamais le detail des lignes. Le service
 * Reporting croise cet ensemble avec les en-tetes que lui rend le service Workflow
 * ; il n'a besoin de rien d'autre. Rendre les lignes ferait voyager des milliers
 * d'objets pour en garder quelques dizaines d'identifiants.
 *
 * <h2>Pourquoi du JPQL assemble plutot qu'une {@code Specification}</h2>
 *
 * <p>Les entites de ce service portent des <b>identifiants plats</b> et aucune
 * association JPA (decision Sprint 3.1,
 * {@code docs/decisions/2026-08-28-identifiants-plats-dans-service-saisie.md}). Une
 * {@code Specification} devrait donc declarer elle-meme ses racines et recoller les
 * identifiants a la main, ce qui serait moins lisible que le JPQL qu'elle produit.
 * Le predicat est assemble morceau par morceau, et <b>chaque morceau porte son
 * parametre nomme</b> : aucune valeur d'utilisateur n'est concatenee dans la
 * requete (CLAUDE.md section 12).
 */
@Repository
public class RechercheLignesRepository {

    @PersistenceContext
    private EntityManager gestionnaireEntites;

    /**
     * Les identifiants d'etats retenus, tries.
     *
     * @param codesUniteVisibles unites que l'appelant peut voir ; {@code null} pour
     *        une portee nationale. Un ensemble <b>vide</b> ne rend rien — ce n'est
     *        pas la meme chose qu'une absence de filtre.
     * @param beneficiaire numero de compte courant <b>exact</b>, ou fragment de nom
     *        ou de prenom, insensible a la casse
     */
    public List<Long> identifiantsProcessusAvecLigne(Integer mois, Integer annee,
            NatureEnum nature, SessionEnum session, String beneficiaire,
            Set<String> codesUniteVisibles) {

        List<String> conditions = new ArrayList<>();
        Map<String, Object> parametres = new LinkedHashMap<>();

        conditions.add("l.idFicheJournaliere = f.id");
        conditions.add("b.id = l.idBeneficiaire");

        if (mois != null) {
            conditions.add("f.moisPaiement = :mois");
            parametres.put("mois", mois);
        }
        if (annee != null) {
            conditions.add("f.anneePaiement = :annee");
            parametres.put("annee", annee);
        }
        if (nature != null) {
            conditions.add("l.nature = :nature");
            parametres.put("nature", nature);
        }
        if (session != null) {
            conditions.add("l.session = :session");
            parametres.put("session", session);
        }
        if (beneficiaire != null && !beneficiaire.isBlank()) {
            // Le numero de compte est la cle d'identification d'un beneficiaire
            // (Sprint 3.1) : il est compare a l'identique. Le nom, lui, est recopie
            // a la main d'un jour sur l'autre et varie ; il se cherche donc par
            // fragment, sans quoi une recherche par nom ne trouverait presque rien.
            conditions.add("(b.numCompteCourant = :beneficiaireExact"
                    + " or lower(b.nom) like :beneficiairePartiel"
                    + " or lower(b.prenom) like :beneficiairePartiel)");
            parametres.put("beneficiaireExact", beneficiaire.trim());
            parametres.put("beneficiairePartiel", "%" + beneficiaire.trim().toLowerCase() + "%");
        }
        if (codesUniteVisibles != null) {
            if (codesUniteVisibles.isEmpty()) {
                // Portee ne couvrant aucune unite : zero resultat, sans produire un
                // « in () » que la base refuserait, et sans neutraliser le filtre.
                return List.of();
            }
            conditions.add("f.codeUnite in :codesUnite");
            parametres.put("codesUnite", codesUniteVisibles);
        }

        String jpql = "select distinct f.idProcessus"
                + " from FicheJournaliere f, LignePrestation l, Beneficiaire b"
                + " where " + String.join(" and ", conditions)
                + " order by f.idProcessus";

        TypedQuery<Long> requete = gestionnaireEntites.createQuery(jpql, Long.class);
        parametres.forEach(requete::setParameter);
        return requete.getResultList();
    }

}
