package cm.afrilandfirstbank.rations.workflow.application;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;

import cm.afrilandfirstbank.rations.workflow.domaine.NomEtapeEnum;

/**
 * Constantes de mise en page du document mensuel : la charte de production des
 * documents (document maitre section 8.2), traduite en points PDF.
 *
 * <h2>Pourquoi une classe a part</h2>
 *
 * <p>Ces constantes sont partagees par <b>deux moments distincts</b> : la
 * generation du document a la soumission, et l'estampage des signatures des
 * sous-sprints 4.3 et 4.4, qui rouvre un fichier existant. Si les deux
 * divergeaient d'un point, la signature du Chef d'Unite s'inscrirait a cote de son
 * cadre — ou par-dessus celle de l'agent. Une seule source pour la geometrie rend
 * cette derive impossible.
 *
 * <h2>Ecart assume a la charte : la police</h2>
 *
 * <p>La charte prescrit <b>Bookman Old Style</b>. Le document utilise
 * <b>Times-Roman</b>, l'une des quatorze polices natives du format PDF. Motif,
 * arbitre avec l'utilisateur au Sprint 4.2 : Bookman Old Style est une fonte
 * Monotype licenciee avec Windows et Office ; l'embarquer dans un depot puis dans
 * une image Docker deployee sur Kubernetes serait une <i>redistribution</i>, ce
 * qui est une question juridique et non technique. Times-Roman n'est pas embarquee
 * du tout — elle est garantie par tout lecteur PDF — donc sans octet ajoute, sans
 * licence a valider et sans dependance reseau au build. Meme genre (serif), meme
 * lisibilite sur un etat de paiement.
 *
 * <p>Tout le reste de la charte est tenu : A4, marges de 2 cm, noir {@code 1A1A1A},
 * rouge {@code E30613} reserve au sous-titre, logo centre en tete de la premiere
 * page uniquement, aucun en-tete sur les pages, pied de page reduit au numero en
 * gris {@code 666666} 9 pt, tableaux a filets fins gris sans aucun aplat de
 * couleur.
 *
 * <p>Le retour a Bookman, si la DSI valide une fonte libre de la famille, est un
 * changement d'une ligne dans {@code DocumentService} (point ouvert K-05).
 */
public final class GabaritDocument {

    private GabaritDocument() {
        // classe de constantes
    }

    // --- Page (charte 8.2 : A4, marges de 2 cm sur les quatre cotes) -----------

    public static final PageSize FORMAT = PageSize.A4;

    /** 2 cm en points PDF : 2 / 2.54 * 72. */
    public static final float MARGE = 56.7f;

    // --- Couleurs (charte 8.1 et 8.2) ------------------------------------------

    /** Noir de la charte. Le noir pur n'est pas utilise. */
    public static final DeviceRgb NOIR = new DeviceRgb(0x1A, 0x1A, 0x1A);

    /** Rouge principal Afriland. <b>Reserve aux accents</b> : ici le seul sous-titre. */
    public static final DeviceRgb ROUGE = new DeviceRgb(0xE3, 0x06, 0x13);

    /** Gris du pied de page. */
    public static final DeviceRgb GRIS = new DeviceRgb(0x66, 0x66, 0x66);

    /** Gris des filets de tableau. Filets fins, fond blanc, aucun aplat. */
    public static final DeviceRgb GRIS_FILET = new DeviceRgb(0xCC, 0xCC, 0xCC);

    // --- Corps -----------------------------------------------------------------

    /** Corps de texte, charte 8.2. */
    public static final float CORPS = 12f;

    /**
     * Corps des cellules de tableau.
     *
     * <p>La charte fixe le corps a 12 pt ; applique aux cellules d'un tableau de
     * six colonnes sur une largeur utile de 482 pt, il ferait deborder un nom de
     * beneficiaire sur trois lignes. Le 12 pt est donc tenu pour la prose, et les
     * tableaux descendent a 10 pt — lecture retenue au Sprint 4.2, a confirmer si
     * la charte devait etre precisee.
     */
    public static final float CORPS_TABLEAU = 10f;

    public static final float TITRE = 20f;
    public static final float PIED_DE_PAGE = 9f;

    /** Largeur du logo, centre en tete de la premiere page. Ratio d'origine 832 x 249. */
    public static final float LOGO_LARGEUR = 170f;

    /** Ressource du logo, chargee par flux de classpath (l'espace du nom est sans effet). */
    public static final String LOGO_RESSOURCE = "/assets/logo afriland.png";

    // --- Page des visas --------------------------------------------------------

    /**
     * Les trois cadres de signature vivent sur une <b>page dediee</b>, toujours la
     * derniere.
     *
     * <p>C'est ce qui rend l'enrichissement possible sans regeneration. Une zone
     * placee a la suite du detail se trouverait a une ordonnee differente selon le
     * nombre de journees saisies ; l'estampage du sous-sprint 4.3 devrait alors
     * deviner ou ecrire. Sur une page dediee, les coordonnees sont les memes pour
     * tous les etats du module, et la signature du Chef d'Unite s'inscrit dans un
     * cadre laisse vide, sans toucher a celle de l'agent.
     */
    public static final float VISA_LARGEUR = FORMAT.getWidth() - 2 * MARGE;

    /**
     * Hauteur d'un cadre de visa.
     *
     * <p><b>Dimensionnee large a dessein.</b> Un canevas iText pose sur un
     * rectangle fixe <i>abandonne sans bruit</i> ce qui n'y tient pas : a 90 pt,
     * la ligne d'horodatage disparaissait du document sans qu'aucune erreur ne
     * soit levee, alors que RG-09 exige une signature <b>horodatee</b>. Le defaut
     * a ete trouve par le test 8 de {@code DocumentServiceTest}, qui relit le PDF
     * produit au lieu de verifier les appels a iText — c'est ce test qui garde
     * cette valeur, et il faut le relire avant de la reduire.
     */
    public static final float VISA_HAUTEUR = 110f;

    /** Ordonnee du bas du premier cadre. */
    private static final float VISA_Y_PREMIER = 590f;

    /** Distance verticale d'un cadre au suivant. */
    private static final float VISA_ECART = 122f;

    /**
     * Le cadre reserve a une etape du circuit, sur la page des visas.
     *
     * <p>L'ordre de declaration de {@link NomEtapeEnum} est celui du circuit
     * (soumission, validation DA, validation DR) : l'ordinal donne donc directement
     * le rang du cadre. Le lien est verifie par un test, pour qu'une reorganisation
     * de l'enumeration ne deplace pas silencieusement les signatures.
     */
    public static Rectangle cadreVisa(NomEtapeEnum etape) {
        float bas = VISA_Y_PREMIER - etape.ordinal() * VISA_ECART;
        return new Rectangle(MARGE, bas, VISA_LARGEUR, VISA_HAUTEUR);
    }

    /** Intitule du cadre, tel qu'il est imprime sur le document. */
    public static String intituleVisa(NomEtapeEnum etape) {
        return switch (etape) {
            case SOUMISSION_AGENT -> "Agent d'unite";
            case VALIDATION_DA -> "Chef d'Unite (DA)";
            case VALIDATION_DR -> "Directeur Reseau (DR)";
        };
    }

    /** Retrait interieur d'un cadre, pour que le texte ne colle pas au filet. */
    private static final float VISA_RETRAIT = 10f;

    /** Hauteur reservee a l'intitule, en haut du cadre. */
    private static final float VISA_BANDEAU = 22f;

    /**
     * Zone ou s'inscrit la mention de signature, sous l'intitule du cadre.
     *
     * <p><b>C'est la zone que l'estampage des sous-sprints 4.3 et 4.4 vise.</b>
     * Elle est distincte du cadre entier parce que l'intitule, lui, est grave a la
     * creation du document et ne doit jamais etre recouvert.
     *
     * <p>Un cadre non atteint par le circuit reste <b>reellement vide</b> : aucun
     * texte n'y est ecrit a la creation. C'est une contrainte technique autant
     * qu'esthetique — l'estampage d'un PDF <i>ajoute</i> du contenu et n'en retire
     * jamais. Une mention « en attente » gravee a la creation resterait sous la
     * signature venue s'inscrire par-dessus, et les deux se liraient ensemble.
     */
    public static Rectangle zoneMentionVisa(NomEtapeEnum etape) {
        Rectangle cadre = cadreVisa(etape);
        return new Rectangle(
                cadre.getX() + VISA_RETRAIT,
                cadre.getY() + VISA_RETRAIT,
                cadre.getWidth() - 2 * VISA_RETRAIT,
                cadre.getHeight() - VISA_BANDEAU - VISA_RETRAIT);
    }

    /** Zone du bandeau d'intitule, en haut du cadre. */
    public static Rectangle zoneIntituleVisa(NomEtapeEnum etape) {
        Rectangle cadre = cadreVisa(etape);
        return new Rectangle(
                cadre.getX() + VISA_RETRAIT,
                cadre.getY() + cadre.getHeight() - VISA_BANDEAU,
                cadre.getWidth() - 2 * VISA_RETRAIT,
                VISA_BANDEAU - 4f);
    }

}
