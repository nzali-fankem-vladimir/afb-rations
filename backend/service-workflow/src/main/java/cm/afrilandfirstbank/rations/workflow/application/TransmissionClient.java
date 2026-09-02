package cm.afrilandfirstbank.rations.workflow.application;

/**
 * Port sortant : « mets cet etat cloture a la disposition de la comptabilite ». Question
 * posee au service Transmission, qui detient le producteur du topic
 * {@code rations.etat.valide} (contrat d'API section 7.1, diagramme AR04).
 *
 * <p><b>Pourquoi une interface</b>, comme {@link HabilitationClient} au Sprint 1.3 et
 * {@link ConsolidationClient} au Sprint 3.4 : le declenchement de la transmission, sa
 * regle de reessai et l'ecriture du drapeau de RG-13 doivent pouvoir etre eprouves sans
 * reseau ni broker, et une notion de panne HTTP n'a rien a faire dans les regles du
 * workflow.
 *
 * <p><b>Par l'API, jamais par la base.</b> Le service Transmission n'a d'ailleurs pas de
 * base : il lit l'en-tete et le detail aupres du Workflow et de la Saisie, par leurs API.
 *
 * <p><b>Ce port ne pose aucun drapeau.</b> {@code transmis_comptabilite} vit sur
 * {@code processus_mensuel}, dans cette base-ci ; c'est ce service qui l'ecrit, au vu de
 * la reponse. Faire poser le drapeau par le service Transmission demanderait un appel
 * retour la ou une valeur de retour suffit.
 */
public interface TransmissionClient {

    /**
     * @param idProcessus l'etat cloture a transmettre
     * @param enteteAutorisation en-tete {@code Authorization} du valideur qui vient de
     *        cloturer, relaye tel quel (doctrine Sprint 1.3). Aucune identite machine
     *        n'existe au realm — {@code serviceAccountsEnabled: false} —, ce qui interdit
     *        par ailleurs toute reprise programmee sans jeton
     * @return l'une des trois issues, jamais {@code null} et jamais une exception. La
     *         distinction entre les deux echecs commande le reessai : voir
     *         {@link ResultatDemandeTransmission}
     */
    ResultatDemandeTransmission demanderTransmission(Long idProcessus, String enteteAutorisation);

}
