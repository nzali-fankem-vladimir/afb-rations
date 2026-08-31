package cm.afrilandfirstbank.rations.saisie.application;

import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;

/**
 * Ce qu'un agent fournit pour ajouter une ligne de prestation.
 *
 * <p><b>Il n'y a pas de champ montant, et c'est le point le plus important de ce
 * type.</b> RG-03 veut que le montant soit repris de la grille active, jamais
 * saisi. La facon la plus sure d'ignorer une valeur est de ne pas avoir d'endroit
 * ou la mettre : un champ « montant propose, jamais utilise » existerait dans le
 * code, et un developpeur pourrait le cabler par megarde en croyant corriger un
 * oubli. Ici, la ligne qui ouvrirait la porte au paiement arbitraire n'est pas
 * ecrivable. C'est aussi exactement le contrat d'API : {@code POST /saisie/lignes}
 * ne prevoit aucun montant en entree.
 *
 * <p>L'en-tete {@code Authorization} n'y figure pas non plus : c'est un element
 * de transport, pas une donnee metier. Il est passe separement a
 * {@link CreationLigneService#creer}, pour etre relaye au service Grilles
 * (decision Sprint 1.3).
 *
 * @param idFicheJournaliere fiche du jour a laquelle la ligne se rattache — elle
 *        porte la journee de la prestation (RG-05)
 * @param beneficiaire identite saisie par l'agent, resolue au fil de la saisie
 *        (US-03, aucun enrolement prealable)
 * @param nature RATION ou TRANSPORT (RG-01)
 * @param session JOUR ou SOIR (RG-02)
 */
public record CommandeCreationLigne(
        Long idFicheJournaliere,
        IdentiteBeneficiaire beneficiaire,
        NatureEnum nature,
        SessionEnum session) {

    /**
     * Identite du beneficiaire telle que l'agent la tape.
     *
     * <p><b>Seul {@code numCompteCourant} identifie</b> (decision Sprint 3.1) :
     * le nom et le prenom sont ressaisis a chaque ligne, et les inclure dans la
     * cle ferait de toute faute de frappe un doublon qui casserait RG-04. Un
     * ecart entre le nom saisi et le nom enregistre est trace, jamais bloquant.
     *
     * <p>{@code codeAgence} est l'agence de domiciliation du <b>compte du
     * beneficiaire</b> — la ligne de credit. A ne jamais confondre avec le
     * {@code code_unite}, qui designe l'unite supportant la charge (CLAUDE.md
     * section 4). Meme format, roles opposes.
     */
    public record IdentiteBeneficiaire(
            String nom,
            String prenom,
            String numCompteCourant,
            String codeAgence) {
    }

}
