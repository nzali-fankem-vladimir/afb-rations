package cm.afrilandfirstbank.rations.workflow.application;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;

import cm.afrilandfirstbank.rations.workflow.domaine.NomEtapeEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.PieceJointe;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.DocumentNonProduitException;

/**
 * Apposition des signatures sur le document de l'etat mensuel (RG-09).
 *
 * <h2>Nature de la signature, arbitree au Sprint 4.2</h2>
 *
 * <p>Une <b>mention signee horodatee</b> imprimee sur le document (login, role,
 * date et heure), <b>doublee d'une empreinte SHA-256</b> du fichier enregistree
 * dans {@code etape_workflow.signature_numerique}.
 *
 * <p><b>Ce que l'empreinte prouve.</b> Que le document archive n'a pas ete altere
 * depuis la derniere signature : on recalcule l'empreinte du fichier et on la
 * compare a celle enregistree.
 *
 * <p><b>Ce qu'elle ne prouve pas, et qu'il ne faut pas laisser croire.</b> Ce
 * n'est pas une signature electronique au sens juridique : aucune cle, aucun
 * certificat, aucune autorite. Et l'empreinte vit dans la <i>meme base</i> que le
 * reste du module : elle ne protege pas de quelqu'un qui peut y ecrire. Elle
 * detecte une alteration du fichier, pas une falsification coordonnee.
 * L'integration au service de signature de la banque est inscrite comme point
 * ouvert cote DSI ({@code docs/points-en-attente.md}).
 *
 * <p><b>Une consequence a connaitre.</b> Le fichier change a chaque
 * enrichissement : seule la <b>derniere</b> empreinte reste verifiable contre lui.
 * Les precedentes documentent ce qu'etait le document a leur etape, sans pouvoir
 * etre recontrolees, l'etat intermediaire n'existant plus. Apres cloture, c'est
 * l'empreinte finale qui scelle le justificatif archive, et c'est celle-la qui
 * compte.
 *
 * <h2>Enrichir, jamais regenerer</h2>
 *
 * <p>Les deux methodes de ce service disent la difference dans leur nom.
 * {@link #creerEtSigner} compose un document neuf ; {@link #enrichirEtSigner}
 * rouvre le fichier existant en mode estampage et n'y ajoute qu'une mention, dans
 * un cadre laisse vide a la creation. <b>Aucune des deux ne recompose un document
 * deja signe</b> : le faire perdrait les signatures precedentes, ce que le point
 * de vigilance section 10 du guide interdit.
 *
 * <h2>L'ecriture precede toujours l'enregistrement</h2>
 *
 * <p>Ces methodes rendent un {@link ResultatSignature} <b>apres</b> que le
 * stockage a confirme l'ecriture. C'est cette confirmation, et elle seule, qui
 * autorise l'appelant a incrementer {@code piece_jointe.nombre_signatures}. Un
 * compteur incremente sur une intention d'ecrire ne mesurerait que le circuit,
 * que {@code etape_workflow} decrit deja (migration V3).
 */
@Service
public class SignatureService {

    /** Prefixe de l'empreinte, pour que l'algorithme reste lisible en base. */
    static final String PREFIXE_EMPREINTE = "SHA-256:";

    private final DocumentService documentService;
    private final StockageDocuments stockage;

    public SignatureService(DocumentService documentService, StockageDocuments stockage) {
        this.documentService = documentService;
        this.stockage = stockage;
    }

    /**
     * Cree le document de l'etat et y appose la premiere signature, celle de
     * l'agent.
     *
     * <p>Le fichier est ecrit par {@link StockageDocuments#ecrireNouveau}, qui
     * <b>refuse d'ecraser</b> : si un document occupe deja le chemin, l'operation
     * echoue au lieu de remplacer un PDF peut-etre deja signe.
     *
     * @param horodatage instant de l'acte, <b>fourni par l'appelant</b> pour que la
     *        mention imprimee et la ligne {@code etape_workflow} portent
     *        rigoureusement le meme. Deux appels a {@code now()} donneraient deux
     *        valeurs proches mais differentes, et le document contredirait la base
     */
    public ResultatSignature creerEtSigner(ProcessusMensuel processus, EtatConsolide etat,
            ActeurSignataire acteur, LocalDateTime horodatage) {

        MentionSignature mention = new MentionSignature(
                NomEtapeEnum.SOUMISSION_AGENT, acteur.login(),
                String.valueOf(acteur.role()), horodatage);

        byte[] document = documentService.genererEtatMensuel(processus, etat, List.of(mention));
        String cheminRelatif = NommageDocument.cheminRelatif(processus);

        DocumentEcrit ecrit = stockage.ecrireNouveau(cheminRelatif, document);

        return new ResultatSignature(ecrit, mention, empreinte(document));
    }

    /**
     * Ajoute une signature a un document existant, sans le recomposer
     * (sous-sprints 4.3 et 4.4).
     *
     * <p>Le fichier est relu, ouvert en <b>mode estampage</b> (un
     * {@link PdfReader} et un {@link PdfWriter} sur le meme document), et la
     * mention est inscrite dans le cadre reserve a son etape. Tout le reste du
     * document, mentions precedentes comprises, est reporte intact : iText recopie
     * les pages existantes, il ne les rejoue pas.
     *
     * @throws DocumentNonProduitException si le document est introuvable, illisible,
     *         ou si l'estampage echoue. Rien n'est alors ecrit : le fichier
     *         d'origine, avec ses signatures, reste en place
     */
    public ResultatSignature enrichirEtSigner(PieceJointe pieceJointe, ActeurSignataire acteur,
            NomEtapeEnum etape, LocalDateTime horodatage) {

        MentionSignature mention = new MentionSignature(
                etape, acteur.login(), String.valueOf(acteur.role()), horodatage);

        byte[] existant = stockage.lire(pieceJointe.getCheminFichier());
        byte[] enrichi = estamper(existant, mention, pieceJointe.getCheminFichier());

        DocumentEcrit ecrit = stockage.remplacer(pieceJointe.getCheminFichier(), enrichi);

        return new ResultatSignature(ecrit, mention, empreinte(enrichi));
    }

    // --- Estampage ---------------------------------------------------------------

    private byte[] estamper(byte[] existant, MentionSignature mention, String cheminRelatif) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();

        try (PdfDocument pdf = new PdfDocument(
                new PdfReader(new ByteArrayInputStream(existant)), new PdfWriter(sortie))) {

            PdfFont normal = PdfFontFactory.createFont(StandardFonts.TIMES_ROMAN);
            PdfFont gras = PdfFontFactory.createFont(StandardFonts.TIMES_BOLD);

            // La page des visas est toujours la derniere : le gabarit lui reserve
            // une page entiere, ce qui rend les coordonnees des cadres independantes
            // du nombre de journees saisies.
            RedacteurVisa.inscrireMention(pdf.getLastPage(), mention, normal, gras);

        } catch (IOException | RuntimeException echec) {
            throw new DocumentNonProduitException(
                    "La signature de " + mention.login() + " n'a pas pu etre apposee sur "
                            + cheminRelatif + " (" + echec.getMessage() + "). Le document "
                            + "existant est intact et aucune etape n'est enregistree.", echec);
        }

        return sortie.toByteArray();
    }

    // --- Empreinte ----------------------------------------------------------------

    /**
     * Empreinte SHA-256 du document, prefixee de son algorithme.
     *
     * <p>Le prefixe n'est pas decoratif : il permettra de reconnaitre les
     * empreintes d'un ancien algorithme le jour ou l'on en changera, sans avoir a
     * le deviner a la longueur de la chaine. Le tout tient en 71 caracteres, sur
     * les 255 de {@code etape_workflow.signature_numerique}.
     */
    static String empreinte(byte[] document) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return PREFIXE_EMPREINTE + HexFormat.of().formatHex(sha256.digest(document));
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 est exige de toute implementation de la plateforme Java.
            throw new IllegalStateException("SHA-256 indisponible sur cette machine virtuelle.",
                    impossible);
        }
    }

}
