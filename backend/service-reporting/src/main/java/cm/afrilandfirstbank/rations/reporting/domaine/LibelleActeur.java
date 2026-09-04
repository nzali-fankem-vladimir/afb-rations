package cm.afrilandfirstbank.rations.reporting.domaine;

/**
 * Un acteur du circuit, nomme (Sprint 6.1).
 *
 * <p>Le {@code login} d'abord : c'est la cle de rapprochement avec le journal
 * d'audit et c'est deja lui qui figure dans le cadre de visa des documents signes
 * (RG-09, Sprint 4.2). Le nom d'usage ensuite, pour la lecture.
 *
 * <p>Ni role ni code unite : ils sont ceux d'aujourd'hui, pas ceux que l'acteur
 * portait au moment ou il a valide. Le niveau d'intervention est deja porte par le
 * nom de l'etape, qui lui ne bouge plus.
 */
public record LibelleActeur(Long id, String login, String nomComplet) {

    public static LibelleActeur de(Long id, String login, String nom, String prenom) {
        return new LibelleActeur(id, login, assembler(nom, prenom));
    }

    private static String assembler(String nom, String prenom) {
        String gauche = prenom == null ? "" : prenom.trim();
        String droite = nom == null ? "" : nom.trim();
        String complet = (gauche + " " + droite).trim();
        return complet.isEmpty() ? null : complet;
    }

}
