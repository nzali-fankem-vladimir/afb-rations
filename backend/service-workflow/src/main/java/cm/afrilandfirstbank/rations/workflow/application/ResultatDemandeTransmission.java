package cm.afrilandfirstbank.rations.workflow.application;

/**
 * Les trois issues d'une demande de transmission au service Transmission.
 *
 * <h2>Trois issues, et la troisieme est une regle de securite, pas une nuance</h2>
 *
 * <p>La distinction entre {@link EchecAvantPublication} et {@link EchecApresTentative}
 * <b>est</b> la regle qui empeche un double paiement, exprimee dans le type plutot que
 * dans un commentaire :
 *
 * <table>
 *   <tr><th>Issue</th><th>Un message a-t-il pu atteindre le broker ?</th><th>Reessai</th></tr>
 *   <tr><td>{@link Transmise}</td><td>oui, et c'est confirme</td><td>sans objet</td></tr>
 *   <tr><td>{@link EchecAvantPublication}</td><td><b>non</b>, aucun envoi n'a eu lieu</td><td><b>autorise</b></td></tr>
 *   <tr><td>{@link EchecApresTentative}</td><td><b>peut-etre</b></td><td><b>interdit</b></td></tr>
 * </table>
 *
 * <h2>Pourquoi l'idempotence du producteur ne suffit pas</h2>
 *
 * <p>{@code enable.idempotence=true} protege des reessais <b>internes</b> du client Kafka
 * a l'interieur d'un seul {@code send()} : le producteur numerote ses messages par
 * partition et par session, et le broker ecarte un doublon. Un second {@code send()}
 * <b>applicatif</b>, lui, est un message neuf : rien ne le rattache au premier, et il
 * s'ecrit une seconde fois sur le topic.
 *
 * <p>Or un echec de publication est <b>ambigu par nature</b> : un accuse peut se perdre en
 * chemin apres que le broker a ecrit le message. Reessayer dans ce cas produirait deux
 * evenements pour le meme etat, donc deux jeux d'ecritures comptables, donc un double
 * paiement des memes beneficiaires — exactement ce que RG-13 existe pour empecher, et
 * l'une des erreurs interdites de CLAUDE.md section 15. Nul ne sait par ailleurs si le
 * module de comptabilisation dedoublonne par {@code idProcessus} : la question est
 * ouverte cote DFT ({@code docs/points-en-attente.md}, a cote de M-03).
 *
 * <p>Un delai depasse cote HTTP appartient a {@link EchecApresTentative} pour la meme
 * raison : ne pas avoir recu de reponse ne veut pas dire que rien ne s'est passe.
 *
 * <p>Type scelle : le {@code switch} qui decide de reessayer est exhaustif, et une
 * quatrieme issue ajoutee plus tard ferait echouer la compilation plutot que de se glisser
 * dans un {@code else} qui reessaierait par defaut.
 */
public sealed interface ResultatDemandeTransmission {

    /**
     * Le broker a accuse reception. C'est la <b>seule</b> issue qui autorise l'ecriture du
     * drapeau {@code transmis_comptabilite} (RG-13).
     *
     * @param topic topic effectivement servi
     * @param partition partition retenue par la cle, qui est l'identifiant du processus
     * @param offset position du message : avec la partition, elle le designe de maniere
     *        unique et permet de le relire tant que la retention le garde
     */
    record Transmise(String topic, int partition, long offset, int nombreLignes,
            long montantTotal) implements ResultatDemandeTransmission {
    }

    /**
     * L'echec est survenu <b>avant</b> tout envoi sur le topic : service Transmission
     * injoignable, ou {@code 503} attestant qu'il n'a pu lire ni l'en-tete ni le detail.
     * Aucun message n'a quitte la machine, le reessai est donc sans danger.
     *
     * @param motif ce qui a echoue, repris tel quel dans le journal et l'audit
     */
    record EchecAvantPublication(String motif) implements ResultatDemandeTransmission {
    }

    /**
     * L'echec est survenu <b>pendant ou apres</b> une tentative de publication, ou dans une
     * situation ou l'on ne peut pas l'exclure : {@code 503 PUBLICATION_ECHOUEE}, delai HTTP
     * depasse, reponse incomprehensible. <b>Aucun reessai</b>.
     *
     * <p>Recouvre aussi les echecs deterministes — {@code 500 CHARGE_INCOMPLETE},
     * {@code 422 ETAT_NON_CLOTURE} — qu'il serait vain de rejouer : la seconde tentative
     * echouerait a l'identique, deux secondes plus tard.
     */
    record EchecApresTentative(String motif) implements ResultatDemandeTransmission {
    }

}
