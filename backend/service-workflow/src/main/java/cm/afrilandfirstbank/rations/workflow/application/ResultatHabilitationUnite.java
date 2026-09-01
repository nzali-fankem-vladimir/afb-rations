package cm.afrilandfirstbank.rations.workflow.application;

/**
 * Les trois issues d'une verification d'habilitation aupres du service Identite,
 * pour un code unite donne.
 *
 * <p><b>Type scelle, sans champ booleen.</b> {@link AgentHabilite} est le seul cas
 * qui autorise. Un {@code resultat.autorise()} qui vaudrait {@code false} par
 * defaut en cas de panne serait correct aujourd'hui et faux le jour ou quelqu'un
 * inverserait la valeur par defaut ; ici, la panne n'a tout simplement pas de
 * champ {@code autorise} a mal lire.
 *
 * <p><b>Aucun cache.</b> Ces valeurs ne sont jamais memorisees d'un appel a
 * l'autre : une habilitation peut changer entre deux operations — attribution de
 * role, changement de code unite, desactivation de profil
 * ({@code docs/appel-habilitation.md} section 3). Autoriser sur une valeur
 * potentiellement obsolete contredirait RG-12.
 */
public sealed interface ResultatHabilitationUnite {

    /** {@code 200} avec {@code autorise = true}. Seul cas qui laisse agir. */
    record AgentHabilite(String login, String role, String codeUnite)
            implements ResultatHabilitationUnite {
    }

    /**
     * Le service Identite a repondu, et sa reponse est negative :
     * {@code autorise = false} (role a portee locale, autre unite), ou
     * {@code 403} (aucun profil local ouvert pour ce compte).
     */
    record AgentNonHabilite(String motif) implements ResultatHabilitationUnite {
    }

    /** Le service Identite n'a rien repondu d'exploitable. Refus conservateur. */
    record ServiceIdentiteIndisponible(String motifTechnique) implements ResultatHabilitationUnite {
    }

}
