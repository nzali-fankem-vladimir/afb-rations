package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Une ouverture d'etat complementaire a ete demandee sans identifiant d'etat
 * d'origine (Sprint 6bis.1). Rendue en {@code 422 ORIGINE_REQUISE}.
 *
 * <h2>Pourquoi le controle n'est pas un {@code @NotNull} sur le DTO</h2>
 *
 * <p>{@code idProcessusOrigine} n'est obligatoire que pour un type
 * {@code COMPLEMENTAIRE} : un etat NORMAL ne regularise rien et le laisse nul. La
 * contrainte est donc <b>conditionnelle</b>, et un {@code @NotNull} inconditionnel
 * refuserait tous les declenchements normaux du module.
 *
 * <p>{@code 422} et non {@code 400} : ce n'est pas la requete qui est malformee —
 * elle est syntaxiquement valide et l'est meme pour un etat normal —, c'est une
 * regle de gestion qui refuse. Un etat complementaire <b>est</b> un rattachement :
 * sans origine, il n'a aucun sens, et le laisser naitre produirait un second etat
 * mensuel autonome sur une periode close, exactement ce que la solution retenue
 * evite (CLAUDE.md section 7).
 */
public class OrigineRequiseException extends RuntimeException {

    public OrigineRequiseException(String message) {
        super(message);
    }

}
