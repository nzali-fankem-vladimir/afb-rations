package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Le mois et l'annee declares dans la demande d'ouverture ne correspondent pas a
 * ceux de l'etat d'origine (Sprint 6bis.1). Rendue en
 * {@code 422 PERIODE_NON_CONCORDANTE}.
 *
 * <h2>{@code 422} ici, {@code 403} pour l'unite : la difference n'est pas cosmetique</h2>
 *
 * <p>{@link UniteNonConcordanteException} touche a un <b>perimetre d'acces</b> —
 * l'unite est ce sur quoi la portee d'un agent est definie (Sprint 1.1), et un
 * desaccord peut signaler une tentative de debordement. La periode, elle, n'ouvre
 * aucun droit : se tromper de mois est une <b>maladresse de saisie</b>, pas un
 * franchissement. La traiter en {@code 403} enverrait l'agent reclamer une
 * habilitation dont l'absence n'est pas en cause, et polluerait le journal des refus
 * d'acces avec des fautes de frappe.
 *
 * <p>Un etat complementaire regularise <b>la periode de son origine</b>, jamais une
 * autre : c'est ce qui rend le rattachement lisible, et ce qui permettra a RG-15
 * (sous-sprint 6bis.2) de comparer des lignes comparables.
 */
public class PeriodeNonConcordanteException extends RuntimeException {

    public PeriodeNonConcordanteException(String message) {
        super(message);
    }

}
