package cm.afrilandfirstbank.rations.saisie.application;

import java.time.LocalDate;

import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;

/**
 * Demande au service Grilles le montant applicable a une prestation (RG-03).
 *
 * <p><b>Pourquoi cette interface est dans {@code application} et non dans
 * {@code domaine}.</b> Le domaine de ce service porte des regles qui se
 * verifient sans rien demander a personne : une ligne est RATION ou TRANSPORT
 * (RG-01), une fiche appartient a un jour (RG-05). Or « quel montant s'applique
 * le 10 juillet ? » n'est pas une regle que le service Saisie sait appliquer :
 * c'est une question posee a un autre service, dont la reponse peut ne jamais
 * arriver. Le cas {@code ServiceGrillesIndisponible} le dit explicitement — et
 * une notion de panne reseau dans la couche domaine y ferait entrer une
 * preoccupation technique que la structure du projet (CLAUDE.md section 3) lui
 * interdit. La demande est donc une etape d'<b>orchestration</b> : l'interface
 * (le port) vit dans {@code application}, son implementation HTTP (l'adaptateur)
 * dans {@code infrastructure}. La dependance pointe vers l'interieur, et
 * l'orchestration reste testable sans reseau.
 *
 * <p><b>Aucune mise en cache, jamais.</b> Une grille peut etre remplacee entre
 * deux appels (validation DRH, Sprint 2.3). Memoriser une reponse positive
 * ferait appliquer un tarif perime sans que rien ne le signale
 * ({@code docs/appel-resolution-montant.md} section 3).
 *
 * @see ResultatResolutionMontant
 */
public interface ResolutionMontantClient {

    /**
     * Interroge {@code GET /grilles/active} pour le couple et la date demandes.
     *
     * <p><b>{@code datePrestation} est la date de la FICHE, jamais la date du
     * jour.</b> Une saisie du 10 juillet effectuee le 27 aout doit etre tarifee
     * au montant de juillet. C'est la raison pour laquelle ce parametre est
     * obligatoire et sans valeur par defaut, ici comme cote Grilles : un repli
     * sur {@code LocalDate.now()} ne se verrait pas tant que toutes les saisies
     * portent sur la journee courante, et produirait un montant faux — mais
     * plausible, donc invisible — des la premiere saisie retroactive.
     *
     * @param nature RATION ou TRANSPORT (RG-01)
     * @param session JOUR ou SOIR (RG-02)
     * @param datePrestation jour de la fiche journaliere concernee
     * @param enteteAutorisation en-tete {@code Authorization} de l'utilisateur
     *        final, <b>relaye tel quel</b> : le realm ne comporte aucun compte de
     *        service, et le mapper d'audience place deja {@code aud: rations-api}
     *        dans le jeton utilisateur (decision Sprint 1.3)
     * @return l'un des trois cas de {@link ResultatResolutionMontant} ; ne
     *         retourne jamais {@code null} et ne leve aucune exception pour un
     *         service injoignable — l'indisponibilite est un resultat, pas un
     *         accident
     */
    ResultatResolutionMontant resoudre(NatureEnum nature,
                                       SessionEnum session,
                                       LocalDate datePrestation,
                                       String enteteAutorisation);

}
