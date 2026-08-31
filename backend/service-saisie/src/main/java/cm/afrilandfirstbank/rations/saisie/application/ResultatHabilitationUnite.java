package cm.afrilandfirstbank.rations.saisie.application;

/**
 * Les trois issues d'une verification d'habilitation aupres du service Identite,
 * pour un code unite donne.
 *
 * <p>Type scelle, meme motif qu'ailleurs : {@link AgentHabilite} est le seul cas
 * qui autorise, et il n'existe aucun champ booleen a mal lire. Un
 * {@code resultat.autorise()} qui vaudrait {@code false} par defaut en cas de
 * panne serait correct aujourd'hui et faux le jour ou quelqu'un inverserait la
 * valeur par defaut ; ici, la panne n'a tout simplement pas de champ
 * {@code autorise}.
 *
 * <p><b>Aucun cache.</b> Ces valeurs ne sont jamais memorisees d'un appel a
 * l'autre : une habilitation peut changer entre deux saisies
 * ({@code docs/appel-habilitation.md} section 3).
 */
public sealed interface ResultatHabilitationUnite {

    /** {@code 200} avec {@code autorise = true}. Seul cas qui laisse agir. */
    record AgentHabilite(String login, String codeUnite) implements ResultatHabilitationUnite {
    }

    /**
     * Le service Identite a repondu, et sa reponse est negative :
     * {@code autorise = false} (role a portee locale, autre unite), ou
     * {@code 403} (aucun profil local ouvert pour ce compte).
     */
    record AgentNonHabilite(String motif) implements ResultatHabilitationUnite {
    }

    /** Le service Identite n'a rien repondu d'exploitable. Refus conservateur. */
    record ServiceIdentiteIndisponible(String motifTechnique)
            implements ResultatHabilitationUnite {
    }

}
