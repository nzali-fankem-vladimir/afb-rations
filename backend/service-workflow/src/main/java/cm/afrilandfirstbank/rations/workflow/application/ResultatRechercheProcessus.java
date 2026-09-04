package cm.afrilandfirstbank.rations.workflow.application;

import java.util.List;

import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;

/**
 * Ce que rend une recherche d'etats mensuels (Sprint 6.1).
 *
 * <h2>Pourquoi le compte voyage a cote du contenu</h2>
 *
 * <p>Le service Reporting croise et pagine <b>en memoire</b> (decision
 * {@code docs/decisions/2026-09-03-agregation-multi-services-du-reporting.md}). Il
 * a donc besoin de savoir, <i>avant</i> de recevoir quoi que ce soit, si le volume
 * tient dans sa borne. D'ou deux requetes ici — un {@code count}, puis un
 * {@code select} seulement s'il est utile — plutot qu'un chargement suivi d'un
 * comptage, qui aurait ramene ce qu'on cherchait justement a ne pas ramener.
 *
 * <h2>Tronque n'est pas vide, et c'est tout l'enjeu</h2>
 *
 * <p>{@code tronque = true} avec un contenu vide dit « il y a {@code nombreTotal}
 * etats, je ne te les envoie pas ». Un contenu vide avec {@code tronque = false}
 * dit « il n'y en a aucun ». Confondre les deux ferait conclure a une analyste RH
 * qu'il n'y a rien a trouver alors qu'il y a des milliers de dossiers qu'elle ne
 * voit pas — c'est exactement le principe deja pose par {@code INCOHERENCE_GRILLE}
 * (Sprint 2.4) et par les cinq situations nommees de la consultation d'integration
 * (Sprint 5.3).
 *
 * @param nombreTotal nombre d'etats correspondant aux criteres, <b>toujours renseigne</b>
 * @param tronque le volume depasse la borne demandee ; {@code contenu} est alors vide
 * @param contenu les en-tetes, tries du plus recent au plus ancien
 */
public record ResultatRechercheProcessus(long nombreTotal, boolean tronque,
        List<ProcessusMensuel> contenu) {

    public static ResultatRechercheProcessus tronquee(long nombreTotal) {
        return new ResultatRechercheProcessus(nombreTotal, true, List.of());
    }

    public static ResultatRechercheProcessus complete(List<ProcessusMensuel> contenu) {
        return new ResultatRechercheProcessus(contenu.size(), false, List.copyOf(contenu));
    }

}
