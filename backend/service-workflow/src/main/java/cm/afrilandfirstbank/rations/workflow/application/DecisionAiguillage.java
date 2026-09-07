package cm.afrilandfirstbank.rations.workflow.application;

/**
 * Ce que RG-08 decide apres la validation du Chef d'Unite : l'etat est clos, ou il
 * monte au Directeur Reseau.
 *
 * <h2>Deux valeurs, et le nom du contrat</h2>
 *
 * <p>Les libelles sont ceux de l'exemple du contrat d'API section 5
 * ({@code SOUS_SEUIL_CLOTURE_DIRECTE}, {@code ENVOI_DIRECTEUR_RESEAU}) : la reponse
 * rendue au chef d'unite les porte telles quelles, sans table de correspondance
 * intermediaire qu'il faudrait tenir a jour.
 *
 * <h2>Pourquoi une enumeration plutot qu'un booleen</h2>
 *
 * <p>Un {@code boolean depasseLeSeuil} se lit correctement une fois sur deux : a
 * l'appel comme au retour, rien n'empeche de l'interpreter a l'envers, et le
 * compilateur ne dit rien. Ici, {@code SOUS_SEUIL_CLOTURE_DIRECTE} nomme la
 * consequence, pas la comparaison ; une inversion devient lisible a l'oeil nu dans
 * le code qui l'applique.
 *
 * <h2>Ce type ne vit pas dans le domaine, deliberement</h2>
 *
 * <p>{@code TransitionProcessus} ignore le montant et le seuil, et un test du
 * Sprint 4.1 verifie qu'aucune de ses methodes n'en parle. Cette decision est un
 * arbitrage <b>applicatif</b>, fonde sur un parametre de configuration : la placer
 * dans {@code domaine} laisserait croire que la machine a etats la connait. Elle ne
 * la connait pas — elle recoit une transition deja choisie.
 */
public enum DecisionAiguillage {

    /**
     * Montant <b>au plus egal</b> au seuil : l'etat est clos sur la seule
     * validation du Chef d'Unite (RG-08, CT-14).
     *
     * <p>La cloture ne transmet rien a la comptabilite :
     * {@code transmis_comptabilite} reste faux jusqu'au Sprint 5.
     */
    SOUS_SEUIL_CLOTURE_DIRECTE,

    /**
     * Montant <b>strictement superieur</b> au seuil : l'etat passe au Directeur
     * Reseau, qui devra le valider a son tour (RG-07, RG-08, CT-15).
     */
    ENVOI_DIRECTEUR_RESEAU,

    /**
     * <b>Etat COMPLEMENTAIRE : le Directeur Reseau valide, quel que soit le
     * montant</b> (Sprint 6bis.1). Le seuil n'est pas lu, aucune comparaison n'a
     * lieu.
     *
     * <h2>Pourquoi une troisieme valeur, et pas simplement {@link #ENVOI_DIRECTEUR_RESEAU}</h2>
     *
     * <p>La consequence est la meme — l'etat monte au Directeur Reseau —, mais le
     * <b>motif</b> ne l'est pas, et il doit se lire sans etre deduit. Avec
     * {@code ENVOI_DIRECTEUR_RESEAU} et un {@code seuilApplique} nul, un controle
     * interne devrait inferer d'une absence ; ici il lit la raison. C'est d'autant
     * plus necessaire que cette regle est <b>provisoire</b> : elle attend l'arbitrage
     * du metier, et le jour ou il tranchera, il faudra retrouver les dossiers qu'elle
     * a aiguilles.
     *
     * <h2>Pourquoi cette regle, avant l'arbitrage metier</h2>
     *
     * <p>Trois lectures etaient possibles pour un etat complementaire : comparer son
     * montant au seuil comme un etat normal, le faire monter systematiquement au
     * Directeur Reseau, ou le laisser clore par le seul Chef d'Unite. Le module en
     * retient une <b>en attendant</b>, et retient la plus exigeante : quand on doit
     * decider sans le metier, on decide dans le sens qui demande <i>plus</i>
     * d'approbation, jamais moins.
     *
     * <p>Les deux autres lectures laissaient un trou de controle interne. Comparer au
     * seuil permettait de fractionner une regularisation en plusieurs complementaires
     * restant chacun sous la barre ; laisser le Chef d'Unite clore seul autorisait un
     * complementaire de n'importe quel montant sur un second niveau d'approbation
     * jamais sollicite. La regle retenue ne peut, elle, que trop demander.
     */
    COMPLEMENTAIRE_ENVOI_DIRECTEUR_RESEAU

}
