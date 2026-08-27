package cm.afrilandfirstbank.rations.grilles.domaine.exception;

/**
 * Conflit d'unicite au sens de RG-14 : la grille proposee ne peut pas coexister
 * avec ce que porte deja le couple (nature, session).
 *
 * <p><b>Pourquoi une exception applicative, alors que la base a deja un index
 * partiel.</b> L'index {@code ux_grille_active_par_couple} est un garde-fou de
 * dernier recours, pas le controle. S'il se declenche, l'utilisateur recoit une
 * violation de contrainte PostgreSQL : un message technique, en anglais, citant
 * un nom d'index. Le refus doit venir du service, en amont, avec un message qui
 * nomme la nature et la session concernees.
 *
 * <p>L'index reste indispensable pour autant : il protege des ecritures
 * concurrentes que deux verifications applicatives ne peuvent pas voir l'une de
 * l'autre. Les deux jouent, dans cet ordre.
 *
 * <p>Porte son propre code d'erreur : les deux conflits possibles n'ont pas la
 * meme cause ni le meme remede, et un code unique obligerait l'appelant a lire
 * le message pour les distinguer.
 */
public class ConflitGrilleException extends RuntimeException {

    /**
     * Une grille est deja en vigueur sur le couple, et la periode proposee la
     * chevaucherait. Code du contrat d'API (section 4).
     */
    public static final String CODE_GRILLE_ACTIVE = "GRILLE_ACTIVE_EXISTANTE";

    /**
     * Une proposition attend deja la decision de la DRH sur ce couple.
     *
     * <p>Code ajoute au Sprint 2.2, au-dela du seul code prevu par le contrat
     * d'API. Motif : le remede differe. Sur {@link #CODE_GRILLE_ACTIVE}, l'ARH
     * corrige sa date de debut ; ici, il n'a rien a corriger — il doit attendre
     * que la DRH tranche. Les confondre enverrait l'utilisateur modifier une
     * saisie qui n'a rien d'errone.
     */
    public static final String CODE_PROPOSITION_EN_ATTENTE = "GRILLE_EN_ATTENTE_EXISTANTE";

    private final String code;

    public ConflitGrilleException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Code porte dans le champ {@code code} du format d'erreur uniforme du module. */
    public String getCode() {
        return code;
    }

}
