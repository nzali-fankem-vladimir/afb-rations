package cm.afrilandfirstbank.rations.workflow.application;

import java.time.format.DateTimeFormatter;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Paragraph;

import cm.afrilandfirstbank.rations.workflow.domaine.NomEtapeEnum;

/**
 * Trace les cadres de visa et y inscrit les mentions de signature.
 *
 * <h2>Pourquoi cette classe existe separement</h2>
 *
 * <p>Elle est le <b>seul endroit</b> ou une mention de signature est dessinee, et
 * elle est appelee a deux moments qui n'ont rien a voir :
 *
 * <ul>
 *   <li>a la <b>creation</b> du document, par {@link DocumentService}, qui trace
 *       les trois cadres et inscrit la mention de l'agent ;</li>
 *   <li>a l'<b>enrichissement</b>, par {@link SignatureService}, qui rouvre un PDF
 *       existant et n'inscrit qu'une mention de plus, dans un cadre laisse vide.</li>
 * </ul>
 *
 * <p>Si ces deux moments dessinaient chacun de leur cote, la mention du Chef
 * d'Unite finirait par ne plus ressembler a celle de l'agent, ou par tomber a cote
 * de son cadre. La duplication serait d'autant plus insidieuse qu'elle ne casserait
 * aucune compilation.
 *
 * <h2>Aucun cadre n'est jamais efface</h2>
 *
 * <p>L'estampage d'un PDF <b>ajoute</b> du contenu : il n'en retire jamais. C'est
 * pourquoi un cadre non encore atteint par le circuit reste <b>reellement vide</b>
 * — pas de mention « en attente » gravee a la creation, qui resterait visible sous
 * la signature venue s'inscrire par-dessus. La page des visas porte un paragraphe
 * d'introduction qui explique ce que signifie un cadre vide.
 */
final class RedacteurVisa {

    private static final DateTimeFormatter HORODATAGE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    private RedacteurVisa() {
        // classe utilitaire
    }

    /**
     * Trace le filet du cadre et grave son intitule.
     *
     * <p>Appele <b>uniquement a la creation</b> du document, pour les trois etapes,
     * y compris {@link NomEtapeEnum#VALIDATION_DR} qui n'est atteinte qu'au-dela du
     * seuil (RG-08). Un cadre reste vide plutot que d'etre absent : la geometrie de
     * la page ne depend ainsi jamais du montant, et l'estampage connait ses
     * coordonnees sans avoir a lire le document.
     */
    static void tracerCadre(PdfPage page, NomEtapeEnum etape, PdfFont gras) {
        Rectangle cadre = GabaritDocument.cadreVisa(etape);

        new PdfCanvas(page)
                .saveState()
                .setStrokeColor(GabaritDocument.FILET)
                .setLineWidth(0.5f)
                .rectangle(cadre)
                .stroke()
                .restoreState();

        try (Canvas canvas = new Canvas(page, GabaritDocument.zoneIntituleVisa(etape))) {
            canvas.add(new Paragraph(GabaritDocument.intituleVisa(etape))
                    .setFont(gras)
                    .setFontSize(GabaritDocument.CORPS_TABLEAU)
                    .setFontColor(GabaritDocument.NOIR));
        }
    }

    /**
     * Inscrit une mention de signature dans la zone reservee a son etape.
     *
     * <p>Trois lignes : le login, le role fige au moment de l'acte, et
     * l'horodatage. RG-09 exige que la signature soit <b>horodatee</b> ; la ligne
     * de date n'est donc pas decorative, et c'est elle qui a impose d'elargir le
     * cadre au Sprint 4.2 (voir {@link GabaritDocument#VISA_HAUTEUR}).
     */
    static void inscrireMention(PdfPage page, MentionSignature mention,
            PdfFont normal, PdfFont gras) {

        Rectangle zone = GabaritDocument.zoneMentionVisa(mention.etape());

        try (Canvas canvas = new Canvas(page, zone)) {
            canvas.setFont(normal).setFontColor(GabaritDocument.NOIR);

            canvas.add(new Paragraph(texte(mention.login()))
                    .setFont(gras)
                    .setFontSize(GabaritDocument.CORPS)
                    .setMarginBottom(1f));
            canvas.add(new Paragraph("Rôle : " + texte(mention.role()))
                    .setFontSize(GabaritDocument.CORPS_MENTION)
                    .setFontColor(GabaritDocument.GRIS)
                    .setMarginBottom(1f));
            canvas.add(new Paragraph("Signé le " + mention.horodatage().format(HORODATAGE))
                    .setFontSize(GabaritDocument.CORPS_MENTION)
                    .setFontColor(GabaritDocument.GRIS));
        }
    }

    private static String texte(String valeur) {
        return valeur == null ? "" : valeur;
    }

}
