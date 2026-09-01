package cm.afrilandfirstbank.rations.workflow.domaine;

import java.util.Optional;

/**
 * Les deux niveaux du circuit de validation, et ce que chacun exige (RG-07).
 *
 * <h2>C'est le STATUT qui designe le niveau, pas le role de l'appelant</h2>
 *
 * <p>Un seul endpoint sert les deux niveaux ({@code POST /processus/{id}/validation},
 * contrat d'API section 5). Le niveau traite se lit sur le <b>statut du dossier</b> :
 * {@link StatutEnum#EN_ATTENTE_DA} appelle le visa du chef d'unite,
 * {@link StatutEnum#EN_ATTENTE_DR} celui du directeur reseau. Le role de l'appelant
 * est ensuite <i>verifie contre</i> ce niveau ; il ne le choisit pas.
 *
 * <p>Ce sens-la, et pas l'inverse, pour trois raisons :
 *
 * <ul>
 *   <li><b>Le statut est detenu par le service</b>, il ne peut pas etre change de
 *       l'exterieur. Le role vient du profil local, qu'un administrateur peut
 *       modifier entre deux gestes (Sprint 1.2).</li>
 *   <li><b>RG-07 est une propriete du dossier</b> — « aucun saut de niveau » decrit
 *       le parcours de l'etat, pas la qualite de qui le regarde. Laisser le role
 *       decider reviendrait a laisser l'appelant designer le niveau d'approbation
 *       qui l'engage.</li>
 *   <li><b>Les messages de refus deviennent justes.</b> Le dossier peut dire « je
 *       n'attends aucune validation » ({@code 422}) ou « j'attends le visa du
 *       directeur reseau, pas le votre » ({@code 403}) — deux refus distincts,
 *       chacun nommant l'etat reel du dossier.</li>
 * </ul>
 *
 * <h2>Un seul niveau attendu a la fois</h2>
 *
 * <p>La correspondance statut → niveau est une <b>bijection partielle</b> : un
 * statut attend au plus un niveau, et les quatre autres statuts n'en attendent
 * aucun. Il n'existe donc jamais d'ambiguite a lever, ni de priorite a inventer.
 */
public enum NiveauValidation {

    /** Premier niveau : le chef d'unite (DA). Suivi de l'aiguillage au seuil (RG-08). */
    CHEF_UNITE(
            StatutEnum.EN_ATTENTE_DA,
            NomEtapeEnum.VALIDATION_DA,
            RoleEnum.CHEF_UNITE_DA,
            "chef d'unite"),

    /**
     * Second niveau : le directeur reseau (DR). <b>Aucun aiguillage</b> : apres le
     * second visa, il n'y a plus d'echelon — la cloture est directe.
     */
    DIRECTEUR_RESEAU(
            StatutEnum.EN_ATTENTE_DR,
            NomEtapeEnum.VALIDATION_DR,
            RoleEnum.DIRECTEUR_RESEAU_DR,
            "directeur reseau");

    private final StatutEnum statutRequis;
    private final NomEtapeEnum nomEtape;
    private final RoleEnum roleRequis;
    private final String libelle;

    NiveauValidation(StatutEnum statutRequis, NomEtapeEnum nomEtape, RoleEnum roleRequis,
            String libelle) {
        this.statutRequis = statutRequis;
        this.nomEtape = nomEtape;
        this.roleRequis = roleRequis;
        this.libelle = libelle;
    }

    /**
     * Le niveau qu'attend un dossier a ce statut, s'il en attend un.
     *
     * <p>Vide pour les quatre autres statuts : rien a valider en saisie, en cours de
     * soumission, apres un retour ou apres la cloture. L'appelant traduit ce vide en
     * {@code 422 TRANSITION_INTERDITE}, en nommant le statut reel.
     */
    public static Optional<NiveauValidation> attenduPour(StatutEnum statut) {
        if (statut == null) {
            return Optional.empty();
        }
        for (NiveauValidation niveau : values()) {
            if (niveau.statutRequis == statut) {
                return Optional.of(niveau);
            }
        }
        return Optional.empty();
    }

    /** Vrai si ce role est celui qu'exige ce niveau. */
    public boolean estTenuPar(RoleEnum role) {
        return roleRequis == role;
    }

    /**
     * Vrai si ce niveau declenche l'aiguillage au seuil (RG-08).
     *
     * <p>Seul le premier niveau : apres la validation du directeur reseau, il n'y a
     * plus d'echelon vers lequel aiguiller. Rappeler le service d'aiguillage au
     * second niveau ferait comparer un montant a un seuil pour choisir entre deux
     * issues dont une seule existe.
     */
    public boolean declencheAiguillage() {
        return this == CHEF_UNITE;
    }

    public StatutEnum statutRequis() {
        return statutRequis;
    }

    public NomEtapeEnum nomEtape() {
        return nomEtape;
    }

    public RoleEnum roleRequis() {
        return roleRequis;
    }

    /** Intitule destine aux messages rendus a l'utilisateur. */
    public String libelle() {
        return libelle;
    }

}
