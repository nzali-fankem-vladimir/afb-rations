package cm.afrilandfirstbank.rations.workflow.application;

/**
 * Port de lecture de la portee d'acces d'un utilisateur (Sprint 6.1).
 *
 * <p>Interface dans la couche application, implementation HTTP dans
 * l'infrastructure : le domaine et l'orchestration ne connaissent pas
 * {@code RestClient}, exactement comme {@link HabilitationClient},
 * {@link ProfilClient} et {@link ConsolidationClient}.
 *
 * <p><b>Jamais mis en cache.</b> Une portee peut changer entre deux appels — un
 * administrateur reaffecte une personne a une autre unite —, et servir une
 * recherche sur une portee perimee montrerait des dossiers auxquels l'utilisateur
 * n'a plus droit. Meme regle qu'au Sprint 1.3 pour l'habilitation.
 */
public interface PorteeClient {

    /**
     * Demande au service Identite les unites que l'utilisateur du jeton peut voir.
     *
     * @param enteteAutorisation en-tete {@code Authorization} de l'utilisateur
     *        final, relaye tel quel (doctrine Sprint 1.3)
     */
    ResultatPortee obtenir(String enteteAutorisation);

}
