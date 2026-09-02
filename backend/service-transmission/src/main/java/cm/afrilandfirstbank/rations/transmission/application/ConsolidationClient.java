package cm.afrilandfirstbank.rations.transmission.application;

/**
 * Port sortant : « quel est le detail consolide de ce processus ? » Question posee au
 * service Saisie, qui detient {@code fiche_journaliere} et {@code ligne_prestation}
 * (RG-06, {@code docs/appel-consolidation.md}).
 *
 * <p><b>Aucun acces a la base du service Saisie.</b> L'echange passe par l'API et rien
 * d'autre : aucune source de donnees vers {@code rations_saisie} n'existe dans ce
 * service, et la cartographie le verifie (critere du guide 5.1 section 9).
 *
 * <p><b>Aucun cache.</b> Le detail publie doit etre celui de l'etat au moment de sa
 * cloture, lu une fois, a la source.
 */
public interface ConsolidationClient {

    /**
     * @param idProcessus processus dont on demande le detail
     * @param codeUnite code unite lu sur l'en-tete du processus. <b>Obligatoire</b> :
     *        le service Saisie refuse l'appel sans lui ({@code 400}) et le recoupe
     *        contre le {@code code_unite} fige sur les fiches, qui fait autorite
     *        ({@code 403 UNITE_NON_CONCORDANTE} en cas de desaccord). Un parametre
     *        fourni par l'appelant ne se croit pas sur parole (Sprint 3.4)
     * @param enteteAutorisation en-tete {@code Authorization} de l'utilisateur final,
     *        relaye tel quel
     * @return l'une des deux issues, jamais {@code null}
     */
    ResultatConsolidation consolider(Long idProcessus, String codeUnite,
            String enteteAutorisation);

}
