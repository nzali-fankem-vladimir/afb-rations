package cm.afrilandfirstbank.rations.workflow.application;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;

import cm.afrilandfirstbank.rations.workflow.domaine.NomEtapeEnum;

/**
 * Constantes de mise en page du document d'etat : la charte de production des
 * documents (document maitre section 8.2), traduite en points PDF.
 *
 * <h2>Pourquoi une classe a part</h2>
 *
 * <p>Ces constantes sont partagees par <b>deux moments distincts</b> : la
 * generation du document a la soumission, et l'estampage des signatures, qui
 * rouvre un fichier existant. Si les deux divergeaient d'un point, la signature du
 * Chef d'Unite s'inscrirait a cote de son cadre — ou par-dessus celle de l'agent.
 * Une seule source pour la geometrie rend cette derive impossible.
 *
 * <h2>Ecart assume a la charte : la police</h2>
 *
 * <p>La charte prescrit <b>Bookman Old Style</b>. Le document utilise
 * <b>Helvetica</b>, l'une des quatorze polices natives du format PDF, sans
 * empattement (retour utilisateur post-7F.7 : lecture plus aisee ; le Sprint 4.2
 * avait retenu Times-Roman). Motif du renoncement a Bookman, arbitre au Sprint 4.2 :
 * c'est une fonte Monotype licenciee avec Windows et Office ; l'embarquer dans un
 * depot puis dans une image Docker serait une <i>redistribution</i>, question
 * juridique et non technique. Helvetica n'est pas embarquee du tout — elle est
 * garantie par tout lecteur PDF — donc sans octet ajoute ni licence a valider
 * (point ouvert K-05).
 *
 * <p>Tout le reste de la charte est tenu : A4, marges de 2 cm, noir {@code 1A1A1A},
 * rouge {@code E30613} reserve au sous-titre, logo centre en tete de la premiere
 * page uniquement, aucun en-tete sur les pages, pied de page reduit au numero en
 * gris {@code 666666} 9 pt, tableaux a filets fins noirs (ecart : la charte dit gris) sans aucun aplat de
 * couleur.
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

    /** Filets de tableau et de cadres : noir de la charte, 0,5 pt (retour utilisateur post-7F.7 : le gris pâle de la charte se lisait mal à l'impression). Fond blanc, aucun aplat. */
    public static final DeviceRgb FILET = NOIR;

    // --- Corps -----------------------------------------------------------------

    /** Corps de texte, charte 8.2. */
    public static final float CORPS = 12f;

    /**
     * Corps des cellules de tableau.
     *
     * <p>La charte fixe le corps a 12 pt ; applique aux cellules d'un tableau de
     * sept colonnes sur une largeur utile de 482 pt, il ferait deborder un nom de
     * beneficiaire sur plusieurs lignes. Le 12 pt est donc tenu pour la prose, et les
     * tableaux descendent a 10 pt — lecture retenue au Sprint 4.2.
     */
    public static final float CORPS_TABLEAU = 10f;

    public static final float TITRE = 20f;
    public static final float PIED_DE_PAGE = 9f;

    /** Corps des lignes de role et d'horodatage d'une mention : le cadre n'a que 138 pt de large. */
    public static final float CORPS_MENTION = 8f;

    /** Largeur du logo, centre en tete de la premiere page. Ratio d'origine 832 x 249. */
    public static final float LOGO_LARGEUR = 130f;

    /** Ressource du logo, chargee par flux de classpath (l'espace du nom est sans effet). */
    public static final String LOGO_RESSOURCE = "/assets/logo afriland.png";

    // --- Bande des visas -------------------------------------------------------

    /**
     * Les trois cadres de signature forment une <b>bande horizontale, en bas de la
     * derniere page</b> (retour utilisateur post-7F.7 : la page entiere dediee aux
     * visas gaspillait l'espace, un etat hebdomadaire tient desormais sur une page).
     *
     * <p>C'est ce qui garde l'enrichissement possible sans regeneration : la bande
     * occupe toujours les memes coordonnees, sur la derniere page. Pour que le
     * detail ne l'atteigne jamais, {@link #MARGE_BASSE} reserve son emplacement sur
     * <b>toutes</b> les pages — un document de plusieurs pages perd donc cette hauteur
     * en bas de chacune.
     */
    private static final float VISA_GOUTTIERE = 10f;

    /** Largeur d'un cadre : un tiers de la largeur utile, moins les gouttieres. */
    public static final float VISA_LARGEUR =
            (FORMAT.getWidth() - 2 * MARGE - 2 * VISA_GOUTTIERE) / 3f;

    /**
     * Hauteur d'un cadre de visa.
     *
     * <p><b>Dimensionnee large a dessein.</b> Un canevas iText pose sur un
     * rectangle fixe <i>abandonne sans bruit</i> ce qui n'y tient pas : la ligne
     * d'horodatage, exigee par RG-09, disparaissait du document sans qu'aucune erreur
     * ne soit levee. Le defaut a ete trouve par un test qui relit le PDF produit au
     * lieu de verifier les appels a iText — il faut relire ce test avant de reduire
     * cette valeur. Le role de directeur reseau, le plus long, tient sur deux lignes.
     */
    public static final float VISA_HAUTEUR = 100f;

    /** Marge basse du contenu, sur toutes les pages : la bande des visas, plus un espace. */
    public static final float MARGE_BASSE = MARGE + VISA_HAUTEUR + 14f;

    /**
     * Le cadre reserve a une etape du circuit, dans la bande.
     *
     * <p>L'ordre de declaration de {@link NomEtapeEnum} est celui du circuit
     * (soumission, validation DA, validation DR) : l'ordinal donne donc directement
     * la colonne du cadre. Le lien est verifie par un test, pour qu'une reorganisation
     * de l'enumeration ne deplace pas silencieusement les signatures.
     */
    public static Rectangle cadreVisa(NomEtapeEnum etape) {
        float gauche = MARGE + etape.ordinal() * (VISA_LARGEUR + VISA_GOUTTIERE);
        return new Rectangle(gauche, MARGE, VISA_LARGEUR, VISA_HAUTEUR);
    }

    /** Intitule du cadre, tel qu'il est imprime sur le document. */
    public static String intituleVisa(NomEtapeEnum etape) {
        return switch (etape) {
            case SOUMISSION_AGENT -> "Agent d'unité";
            case VALIDATION_DA -> "Chef d'unité";
            case VALIDATION_DR -> "Directeur réseau";
        };
    }

    /** Retrait interieur d'un cadre, pour que le texte ne colle pas au filet. */
    private static final float VISA_RETRAIT = 6f;

    /** Hauteur reservee a l'intitule, en haut du cadre. */
    private static final float VISA_BANDEAU = 22f;

    /**
     * Zone ou s'inscrit la mention de signature, sous l'intitule du cadre.
     *
     * <p><b>C'est la zone que l'estampage vise.</b> Elle est distincte du cadre
     * entier parce que l'intitule, lui, est grave a la creation du cadre et ne doit
     * jamais etre recouvert.
     *
     * <p>Un cadre non atteint par le circuit reste <b>reellement vide</b> : aucun
     * texte n'y est ecrit a la creation. L'estampage d'un PDF <i>ajoute</i> du
     * contenu et n'en retire jamais : une mention « en attente » gravee a la
     * creation resterait sous la signature venue s'inscrire par-dessus.
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
