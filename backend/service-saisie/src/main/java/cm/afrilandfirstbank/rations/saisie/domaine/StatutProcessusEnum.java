package cm.afrilandfirstbank.rations.saisie.domaine;

import java.util.Optional;
import java.util.Set;

/**
 * Statut d'un processus mensuel, tel que le service Saisie le lit dans la
 * réponse de {@code GET /processus/{id}} (CLAUDE.md §5, {@code StatutEnum}).
 *
 * <h2>Une recopie, comme {@code NatureEnum} et {@code SessionEnum}</h2>
 *
 * <p>Ce n'est pas une projection du domaine de Workflow — celle-là serait une
 * faute d'architecture ({@code docs/rattachement-processus.md} §3, option 3
 * écartée). C'est la lecture typée d'un champ de réponse d'API : le service
 * Saisie ne stocke aucun statut, ne le fait jamais évoluer, et n'en déduit
 * qu'une chose, la seule qui le concerne — <b>puis-je encore écrire ?</b>
 *
 * <p>La recopie plutôt que la mutualisation suit la décision du Sprint 2.1
 * ({@code docs/decisions/2026-08-27-partage-enumerations-nature-session.md}) :
 * {@code rations-audit-commun} est la seule mutualisation de code du backend, et
 * son périmètre est vérifié au build.
 *
 * <h2>Deux statuts seulement laissent écrire</h2>
 *
 * <p>{@code EN_COURS_SAISIE}, la saisie du mois en cours, et {@code RETOURNE},
 * l'état renvoyé à l'agent pour correction (RG-11) — dont la correction serait
 * impossible s'il n'était pas modifiable. Les quatre autres sont soit en cours
 * de validation par un supérieur, soit définitifs.
 *
 * <p><b>La liste est fermée et positive</b> : elle énumère ce qui autorise, non
 * ce qui interdit. Un statut ajouté un jour au module ne deviendrait pas
 * modifiable par omission.
 */
public enum StatutProcessusEnum {

    EN_COURS_SAISIE,
    SOUMIS,
    EN_ATTENTE_DA,
    EN_ATTENTE_DR,
    RETOURNE,
    CLOTURE;

    private static final Set<StatutProcessusEnum> STATUTS_MODIFIABLES =
            Set.of(EN_COURS_SAISIE, RETOURNE);

    /**
     * Vrai si une ligne de prestation peut encore être ajoutée, modifiée ou
     * supprimée dans ce processus.
     */
    public boolean estModifiable() {
        return STATUTS_MODIFIABLES.contains(this);
    }

    /**
     * Lit un statut reçu du service Workflow, <b>sans lever</b> si la valeur est
     * inconnue.
     *
     * <p>C'est la posture du <i>tolerant reader</i> : le service Saisie ne doit
     * pas tomber parce que Workflow a introduit un statut qu'il ne connaît pas.
     * Mais il ne doit pas non plus l'interpréter comme modifiable — un
     * {@link Optional} vide est traduit par l'appelant en refus conservateur, au
     * même titre qu'une panne. Tolérant à la lecture, fermé à la décision.
     */
    public static Optional<StatutProcessusEnum> depuisLibelle(String libelle) {
        if (libelle == null || libelle.isBlank()) {
            return Optional.empty();
        }
        for (StatutProcessusEnum statut : values()) {
            if (statut.name().equalsIgnoreCase(libelle.strip())) {
                return Optional.of(statut);
            }
        }
        return Optional.empty();
    }

}
