package cm.afrilandfirstbank.rations.workflow.domaine;

/**
 * Suite donnee par la comptabilite a un etat transmis (contrat d'API section 7.2),
 * portee par {@code processus_mensuel.statut_integration} (migration V4).
 *
 * <h2>Pourquoi cette enumeration vit dans le service Workflow</h2>
 *
 * <p>Parce que la colonne y vit. L'arbitrage du Sprint 5.1 a loge le statut
 * d'integration sur {@code processus_mensuel}, aux cotes de
 * {@code transmis_comptabilite} : les deux decrivent le meme fait — ou en est cet
 * etat vis-a-vis de la comptabilite — et les separer dans deux bases laisserait deux
 * moities de verite sans transaction commune pour les tenir d'accord.
 *
 * <p>Le service Transmission en detient une copie a l'identique, dans son propre
 * paquet. Duplication assumee, sur le modele de {@code NatureEnum} au Sprint 2.1 :
 * {@code rations-audit-commun} est la seule mutualisation de code du backend et son
 * perimetre est verifie au build (CLAUDE.md sections 3 et 15).
 *
 * <h2>Aucune valeur pour « jamais transmis »</h2>
 *
 * <p>Cette situation se lit sur {@code transmis_comptabilite}, et la colonne
 * {@code statut_integration} reste nulle. Une constante {@code NON_TRANSMIS}
 * melangerait « le module a-t-il envoye cet etat ? », qui releve de RG-13, et
 * « qu'en a fait la comptabilite ? », qui releve d'un systeme auquel l'equipe n'a
 * pas acces.
 *
 * <h2>Le Workflow ne pose que la premiere</h2>
 *
 * <p>{@link #EN_ATTENTE} est ecrit a la publication effective, avec le drapeau de
 * RG-13. {@link #INTEGRE} et {@link #REJETE} viennent de l'accuse consomme sur
 * {@code rations.etat.accuse} (Sprint 5.2) et n'ont aucune autre origine legitime :
 * ce module ne produit aucune ecriture comptable (CLAUDE.md section 8).
 */
public enum StatutIntegrationEnum {

    /** Publie sur le topic ; aucun accuse recu a ce jour. */
    EN_ATTENTE,

    /** Pris en charge par la comptabilite, qui rend une reference comptable. */
    INTEGRE,

    /** Refuse par la comptabilite, avec motif (contrat d'API section 7.2). */
    REJETE

}
