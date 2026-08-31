package cm.afrilandfirstbank.rations.saisie.application;

/**
 * Port sortant : « cet utilisateur peut-il agir sur un dossier de ce code
 * unite ? » Question posee au service Identite, proprietaire de la portee
 * d'acces (RG-12, Sprint 1.1).
 *
 * <p><b>Pourquoi demander plutot que decider ici.</b> Le {@code code_unite} de
 * l'utilisateur n'est pas dans le jeton Keycloak : il est gere localement par le
 * service Identite (CLAUDE.md section 10). Un consommateur ne peut donc pas
 * trancher seul le cas d'un role a portee locale. L'endpoint interne
 * {@code GET /identite/habilitation?codeUnite=...} fixe ou s'execute la regle —
 * dans le service qui en est proprietaire, plutot que recopiee six fois
 * (decision Sprint 1.3, {@code docs/appel-habilitation.md}).
 *
 * <p>Cet endpoint est hors du contrat passerelle : il n'est pas destine au
 * frontend. Le decompte de trois endpoints du service Identite reste inchange
 * (CLAUDE.md section 11).
 */
public interface HabilitationClient {

    /**
     * @param codeUnite code unite du DOSSIER concerne — celui du processus, lu
     *        dans la reponse de Workflow ou recopie sur la fiche ; jamais une
     *        valeur fournie par le client, qui reviendrait a demander a l'agent
     *        sur quelle unite il a le droit d'ecrire
     * @param enteteAutorisation en-tete {@code Authorization} de l'utilisateur
     *        final, relaye tel quel
     */
    ResultatHabilitationUnite verifier(String codeUnite, String enteteAutorisation);

}
