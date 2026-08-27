package cm.afrilandfirstbank.rations.commun.audit;

/**
 * Port de publication des evenements d'audit, seul type que les services metier
 * manipulent (CLAUDE.md section 9.2).
 *
 * <p><b>Contrat, opposable a toute implementation :</b>
 * <ol>
 *   <li><b>Ne leve jamais.</b> Quelle que soit la panne — broker injoignable,
 *       serialisation impossible, file saturee — {@link #publier} retourne
 *       normalement. L'appelant n'a rien a rattraper, et ne doit pas essayer.</li>
 *   <li><b>Ne bloque pas.</b> Le thread appelant n'attend ni l'envoi, ni
 *       l'accuse du broker, ni la consommation par le service Audit.</li>
 *   <li><b>N'appelle jamais le service Audit en REST.</b> Interdit sans
 *       exception, y compris en repli apres un echec de publication
 *       (CLAUDE.md section 15). Un tel appel reintroduirait sur le chemin
 *       critique la dependance que le topic elimine.</li>
 *   <li><b>Ne garantit pas la livraison.</b> Un evenement peut etre perdu
 *       (broker durablement indisponible, file d'attente saturee, arret brutal
 *       de la JVM apres le commit). C'est un compromis assume, pas un oubli :
 *       voir la note sur le risque residuel dans {@code docs/publication-audit.md}.</li>
 * </ol>
 *
 * <p><b>Ordre d'appel.</b> La publication vient APRES la modification d'etat
 * (document maitre section 7.3). L'implementation renforce cette regle : quand
 * une transaction est active, l'envoi reel n'a lieu qu'apres son commit, de
 * sorte qu'un rollback ne laisse jamais derriere lui la trace d'une operation
 * qui n'a pas eu lieu.
 *
 * <p><b>Champs estampilles par l'implementation.</b> L'appelant ne renseigne pas
 * {@code serviceEmetteur} : le producteur l'inscrit depuis
 * {@code spring.application.name}. Le faire remplir a la main par six services
 * garantirait qu'un l'oublie, et une trace centralisee sans origine ne vaut
 * plus grand-chose.
 */
public interface PublicateurAudit {

    /**
     * Publie un evenement d'audit. Retourne toujours normalement.
     *
     * @param evenement charge a publier, typiquement construite par
     *        {@link EvenementAudit#de}
     */
    void publier(EvenementAudit evenement);

}
