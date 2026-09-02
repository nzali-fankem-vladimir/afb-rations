package cm.afrilandfirstbank.rations.transmission.domaine;

/**
 * Suite donnee par la comptabilite a un etat transmis (contrat d'API section 7.2).
 *
 * <h2>Trois valeurs, et une quatrieme qui n'en est pas une</h2>
 *
 * <p>Le contrat en nomme trois. La quatrieme situation — « cet etat n'a jamais ete
 * transmis » — ne recoit deliberement pas de constante : elle se lit sur
 * {@code processus_mensuel.transmis_comptabilite}, et la colonne
 * {@code statut_integration} reste alors <b>nulle</b> (migration V4 cote Workflow).
 *
 * <p>Lui donner une constante {@code NON_TRANSMIS} melangerait deux questions
 * differentes : « le module a-t-il envoye cet etat ? », qui releve de RG-13 et de ce
 * module, et « qu'en a fait la comptabilite ? », qui releve d'un systeme auquel
 * l'equipe n'a pas acces. Le contrat d'API ne rend d'ailleurs cette enumeration que
 * pour des etats deja transmis.
 *
 * <h2>Ce module ne decide d'aucune de ces valeurs, sauf la premiere</h2>
 *
 * <p>{@link #EN_ATTENTE} est la seule que ce module pose lui-meme, et il la pose
 * comme un constat : l'evenement est parti sur {@code rations.etat.valide}, la
 * comptabilite n'a pas encore repondu. {@link #INTEGRE} et {@link #REJETE} viennent
 * exclusivement de l'accuse recu sur {@code rations.etat.accuse} (Sprint 5.2). Les
 * fabriquer ici reviendrait a prejuger d'un traitement comptable que ce module ne
 * realise pas (CLAUDE.md section 8).
 *
 * <h2>Copie assumee</h2>
 *
 * <p>La meme enumeration existe dans {@code service-workflow}, qui porte la colonne.
 * Duplication deliberee, sur le modele de {@code NatureEnum} / {@code SessionEnum}
 * au Sprint 2.1 et de {@code PageResponse} au Sprint 2.2 :
 * {@code rations-audit-commun} est la seule mutualisation de code du backend et son
 * perimetre est verifie au build (CLAUDE.md sections 3 et 15).
 */
public enum StatutIntegrationEnum {

    /**
     * L'etat est parti sur le topic ; la comptabilite n'a pas encore accuse
     * reception. Ce n'est pas un etat initial du processus : un etat en cours de
     * saisie n'attend rien de la comptabilite, son statut d'integration est nul.
     */
    EN_ATTENTE,

    /** La comptabilite a pris l'etat en charge et rend une reference comptable. */
    INTEGRE,

    /**
     * La comptabilite refuse l'etat. Le contrat d'API exige alors un motif, qui
     * remonte dans le suivi (US-15).
     */
    REJETE

}
