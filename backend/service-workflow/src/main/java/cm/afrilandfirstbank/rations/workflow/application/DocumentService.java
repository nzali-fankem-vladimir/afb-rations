package cm.afrilandfirstbank.rations.workflow.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import cm.afrilandfirstbank.rations.workflow.domaine.NomEtapeEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.DocumentNonProduitException;

/**
 * Production du document PDF de l'etat mensuel (iText 8, US-06, Sprint 4.2).
 *
 * <h2>Ce service ne touche jamais au disque</h2>
 *
 * <p>Il rend des octets. L'ecriture, avec sa discipline de confirmation — fsync
 * puis renommage atomique —, appartient a {@link StockageDocuments}. La separation
 * n'est pas cosmetique : c'est elle qui permet d'ecrire le fichier <b>avant</b>
 * d'ouvrir la transaction, et donc de n'inscrire une signature en base que si elle
 * est reellement dans le fichier (migration V3).
 *
 * <h2>Un seul document, genere puis enrichi</h2>
 *
 * <p>Ce service <b>cree</b> le document, avec la premiere signature deja en place.
 * Les signatures suivantes (sous-sprints 4.3 et 4.4) ne repassent pas par ici :
 * elles estampent le fichier existant dans le cadre que la page des visas leur a
 * reserve. Regenerer a chaque etape ferait perdre les signatures precedentes, ce
 * que le point de vigilance section 10 du guide interdit expressement.
 *
 * <h2>Charte de production, document maitre section 8.2</h2>
 *
 * <p>Traduite en constantes par {@link GabaritDocument}, qui porte aussi le seul
 * ecart assume : la police. Tout le reste est tenu — A4, marges de 2 cm, logo
 * centre en tete de la <b>premiere page uniquement</b>, aucun en-tete sur les
 * pages, titre noir souligne et sous-titre rouge en 20 pt, pied de page reduit au
 * numero de page en gris 9 pt, tableaux a filets fins gris sans aucun aplat de
 * couleur.
 *
 * <h2>Aucun calcul</h2>
 *
 * <p>Les sous-totaux journaliers et le total du mois sont <b>recopies</b> de
 * l'etat consolide, jamais readdiitionnes. RG-06 est partagee entre Saisie et
 * Workflow (decision Sprint 3.4) : il n'existe qu'un seul chemin de calcul, donc
 * aucune divergence possible entre le detail imprime et le total imprime. Un
 * document qui recalculerait pourrait afficher un total different de celui qui
 * commande l'aiguillage au seuil.
 */
@Service
public class DocumentService {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORODATAGE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a' HH:mm");

    private static final String[] MOIS = {
            "janvier", "fevrier", "mars", "avril", "mai", "juin",
            "juillet", "aout", "septembre", "octobre", "novembre", "decembre"};

    /** Largeurs relatives des six colonnes du detail journalier. */
    private static final float[] COLONNES = {26f, 21f, 10f, 13f, 12f, 18f};

    /**
     * Compose le document de l'etat mensuel.
     *
     * @param processus le processus tel que Workflow le connait
     * @param etat l'etat consolide rendu par le service Saisie
     * @param signatures les mentions deja apposees. A la soumission, la seule de
     *        l'agent ; les cadres des etapes absentes restent vides, en attente
     * @return le document complet, en octets. Rien n'est ecrit sur disque ici
     * @throws DocumentNonProduitException si la composition echoue. Un document
     *         partiel n'est jamais rendu : sans document, pas de soumission
     */
    public byte[] genererEtatMensuel(ProcessusMensuel processus, EtatConsolide etat,
            List<MentionSignature> signatures) {

        ByteArrayOutputStream sortie = new ByteArrayOutputStream();

        try {
            PdfDocument pdf = new PdfDocument(new PdfWriter(sortie));
            PdfFont normal = PdfFontFactory.createFont(StandardFonts.TIMES_ROMAN);
            PdfFont gras = PdfFontFactory.createFont(StandardFonts.TIMES_BOLD);

            pdf.addEventHandler(PdfDocumentEvent.END_PAGE, new PiedDePage(normal));

            try (Document document = new Document(pdf, GabaritDocument.FORMAT)) {
                document.setMargins(GabaritDocument.MARGE, GabaritDocument.MARGE,
                        GabaritDocument.MARGE, GabaritDocument.MARGE);
                document.setFont(normal)
                        .setFontSize(GabaritDocument.CORPS)
                        .setFontColor(GabaritDocument.NOIR);

                composerEnTete(document, gras);
                composerIdentification(document, processus, etat, gras);
                composerDetailJournalier(document, etat, gras);
                composerTotal(document, etat, gras);

                document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                document.add(titreSection("VISAS ET SIGNATURES", gras));
                document.add(new Paragraph(
                        "Chaque visa est appose automatiquement par le systeme au moment de "
                                + "l'action, avec son horodatage (RG-09). Un cadre vide signale une "
                                + "etape que le circuit n'a pas encore atteinte.")
                        .setFontSize(GabaritDocument.CORPS_TABLEAU)
                        .setFontColor(GabaritDocument.GRIS));

                // Les cadres sont traces au point fixe defini par le gabarit, sur la
                // derniere page. C'est ce qui permettra aux sous-sprints 4.3 et 4.4
                // d'ecrire dans le bon cadre sans avoir a deviner ou il se trouve.
                dessinerCadresVisas(pdf, signatures, normal, gras);
            }

            return sortie.toByteArray();

        } catch (IOException | RuntimeException echec) {
            throw new DocumentNonProduitException(
                    "Le document de l'etat " + processus.libellePeriode() + " pour l'unite "
                            + processus.getCodeUnite() + " n'a pas pu etre produit ("
                            + echec.getMessage() + "). Aucune soumission n'est enregistree.",
                    echec);
        }
    }

    // --- En-tete ----------------------------------------------------------------

    /**
     * Logo centre, titre, sous-titre.
     *
     * <p>Le logo est ajoute une seule fois, dans le flux du document : il apparait
     * donc en tete de la premiere page et nulle part ailleurs, comme la charte le
     * demande. Un logo pose par le gestionnaire d'evenement de fin de page se
     * repeterait sur toutes les pages, ce que la charte interdit explicitement.
     */
    private void composerEnTete(Document document, PdfFont gras) throws IOException {
        document.add(new Image(ImageDataFactory.create(logo()))
                .setWidth(GabaritDocument.LOGO_LARGEUR)
                .setHorizontalAlignment(HorizontalAlignment.CENTER)
                .setMarginBottom(18f));

        document.add(new Paragraph("ETAT MENSUEL DE PAIEMENT")
                .setFont(gras)
                .setFontSize(GabaritDocument.TITRE)
                .setFontColor(GabaritDocument.NOIR)
                .setUnderline()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(2f));

        document.add(new Paragraph("Rations et transport de la garde armee")
                .setFont(gras)
                .setFontSize(GabaritDocument.TITRE)
                .setFontColor(GabaritDocument.ROUGE)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(22f));
    }

    /** Bloc d'identification du dossier : unite, periode, type, numero, statut. */
    private void composerIdentification(Document document, ProcessusMensuel processus,
            EtatConsolide etat, PdfFont gras) {

        Table table = new Table(UnitValue.createPercentArray(new float[] {30f, 70f}))
                .useAllAvailableWidth()
                .setMarginBottom(18f);

        ajouterLigneIdentification(table, "Unite", processus.getCodeUnite(), gras);
        ajouterLigneIdentification(table, "Periode", periodeEnToutesLettres(processus), gras);
        ajouterLigneIdentification(table, "Type d'etat",
                String.valueOf(processus.getTypeProcessus()), gras);
        ajouterLigneIdentification(table, "Numero de dossier",
                String.valueOf(processus.getId()), gras);
        ajouterLigneIdentification(table, "Statut", String.valueOf(processus.getStatut()), gras);
        ajouterLigneIdentification(table, "Journees saisies",
                String.valueOf(valeur(etat.nombreJournees())), gras);
        ajouterLigneIdentification(table, "Lignes de prestation",
                String.valueOf(valeur(etat.nombreLignes())), gras);
        ajouterLigneIdentification(table, "Beneficiaires servis",
                String.valueOf(valeur(etat.nombreBeneficiaires())), gras);

        document.add(table);
    }

    private void ajouterLigneIdentification(Table table, String libelle, String valeur, PdfFont gras) {
        table.addCell(cellule(libelle).setFont(gras));
        table.addCell(cellule(valeur));
    }

    // --- Detail journalier -------------------------------------------------------

    private void composerDetailJournalier(Document document, EtatConsolide etat, PdfFont gras) {
        document.add(titreSection("DETAIL PAR JOURNEE", gras));

        List<EtatConsolide.Journee> journees =
                etat.journees() == null ? List.of() : etat.journees();

        for (EtatConsolide.Journee journee : journees) {
            if (journee == null) {
                continue;
            }
            document.add(new Paragraph(intituleJournee(journee))
                    .setFont(gras)
                    .setFontSize(GabaritDocument.CORPS)
                    .setMarginTop(10f)
                    .setMarginBottom(4f));

            List<EtatConsolide.Ligne> lignes =
                    journee.lignes() == null ? List.of() : journee.lignes();

            if (lignes.isEmpty()) {
                document.add(new Paragraph("Aucune prestation saisie pour cette journee.")
                        .setFontSize(GabaritDocument.CORPS_TABLEAU)
                        .setFontColor(GabaritDocument.GRIS));
                continue;
            }

            document.add(tableauDesLignes(lignes, gras));

            document.add(new Paragraph(
                    "Sous-total du " + intituleCourtJournee(journee) + " : "
                            + fcfa(valeur(journee.sousTotalFcfa())))
                    .setFont(gras)
                    .setFontSize(GabaritDocument.CORPS_TABLEAU)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setMarginTop(3f));
        }
    }

    private Table tableauDesLignes(List<EtatConsolide.Ligne> lignes, PdfFont gras) {
        Table table = new Table(UnitValue.createPercentArray(COLONNES)).useAllAvailableWidth();

        for (String entete : new String[] {
                "Beneficiaire", "N° compte", "Agence", "Nature", "Session", "Montant"}) {
            table.addHeaderCell(cellule(entete).setFont(gras));
        }

        for (EtatConsolide.Ligne ligne : lignes) {
            if (ligne == null) {
                continue;
            }
            EtatConsolide.Beneficiaire beneficiaire = ligne.beneficiaire();
            table.addCell(cellule(nomBeneficiaire(beneficiaire)));
            table.addCell(cellule(beneficiaire == null ? "" : texte(beneficiaire.numCompteCourant())));
            table.addCell(cellule(beneficiaire == null ? "" : texte(beneficiaire.codeAgence())));
            table.addCell(cellule(capitaliser(ligne.nature())));
            table.addCell(cellule(capitaliser(ligne.session())));
            table.addCell(cellule(fcfa(valeur(ligne.montantApplique())))
                    .setTextAlignment(TextAlignment.RIGHT));
        }

        return table;
    }

    /** Total du mois, recopie de l'etat consolide et jamais recalcule. */
    private void composerTotal(Document document, EtatConsolide etat, PdfFont gras) {
        Table table = new Table(UnitValue.createPercentArray(new float[] {70f, 30f}))
                .useAllAvailableWidth()
                .setMarginTop(20f);

        table.addCell(cellule("TOTAL DU MOIS").setFont(gras));
        table.addCell(cellule(fcfa(valeur(etat.montantTotalFcfa())))
                .setFont(gras)
                .setTextAlignment(TextAlignment.RIGHT));

        document.add(table);
    }

    // --- Page des visas ----------------------------------------------------------

    /**
     * Trace les trois cadres a leur position fixe et y inscrit les mentions
     * connues.
     *
     * <p>Les cadres sont dessines pour les trois etapes, y compris
     * {@link NomEtapeEnum#VALIDATION_DR} qui n'est atteinte qu'au-dela du seuil
     * (RG-08) : un cadre reste vide plutot que d'etre absent, de sorte que la
     * geometrie de la page ne depende jamais du montant. C'est ce qui permet a
     * l'estampage des sous-sprints suivants de connaitre ses coordonnees sans lire
     * le document.
     *
     * <p><b>Un cadre non signe reste rigoureusement vide.</b> Aucune mention « en
     * attente » n'y est gravee : l'estampage ajoute du contenu sans jamais en
     * retirer, et une telle mention resterait lisible sous la signature venue
     * s'inscrire par-dessus. C'est le paragraphe d'introduction de la page qui dit
     * ce que signifie un cadre vide.
     */
    private void dessinerCadresVisas(PdfDocument pdf, List<MentionSignature> signatures,
            PdfFont normal, PdfFont gras) {

        Map<NomEtapeEnum, MentionSignature> parEtape = new EnumMap<>(NomEtapeEnum.class);
        if (signatures != null) {
            for (MentionSignature mention : signatures) {
                if (mention != null && mention.etape() != null) {
                    parEtape.put(mention.etape(), mention);
                }
            }
        }

        PdfPage page = pdf.getLastPage();
        for (NomEtapeEnum etape : NomEtapeEnum.values()) {
            RedacteurVisa.tracerCadre(page, etape, gras);
            MentionSignature mention = parEtape.get(etape);
            if (mention != null) {
                RedacteurVisa.inscrireMention(page, mention, normal, gras);
            }
        }
    }

    // --- Pied de page ------------------------------------------------------------

    /**
     * Numero de page seul, centre, gris, 9 pt (charte 8.2).
     *
     * <p>Aucun en-tete de page : la charte ne prevoit que le logo en tete de la
     * premiere page. Ce gestionnaire ne pose donc rien en haut des pages.
     */
    private record PiedDePage(PdfFont police) implements IEventHandler {

        @Override
        public void handleEvent(Event evenement) {
            PdfDocumentEvent evenementPdf = (PdfDocumentEvent) evenement;
            PdfPage page = evenementPdf.getPage();
            int numero = evenementPdf.getDocument().getPageNumber(page);

            try (Canvas canvas = new Canvas(page, page.getPageSize())) {
                canvas.setFont(police)
                        .setFontSize(GabaritDocument.PIED_DE_PAGE)
                        .setFontColor(GabaritDocument.GRIS)
                        .showTextAligned(String.valueOf(numero),
                                page.getPageSize().getWidth() / 2f,
                                GabaritDocument.MARGE / 2f,
                                TextAlignment.CENTER);
            }
        }
    }

    // --- Mise en forme -----------------------------------------------------------

    private byte[] logo() throws IOException {
        try (InputStream flux = DocumentService.class
                .getResourceAsStream(GabaritDocument.LOGO_RESSOURCE)) {
            if (flux == null) {
                throw new IOException("Logo introuvable dans le classpath a "
                        + GabaritDocument.LOGO_RESSOURCE);
            }
            return flux.readAllBytes();
        }
    }

    private Paragraph titreSection(String libelle, PdfFont gras) {
        return new Paragraph(libelle)
                .setFont(gras)
                .setFontSize(GabaritDocument.CORPS)
                .setFontColor(GabaritDocument.NOIR)
                .setMarginTop(6f)
                .setMarginBottom(6f);
    }

    /** Cellule a filets fins gris, fond blanc, sans aucun aplat de couleur (charte 8.2). */
    private Cell cellule(String contenu) {
        return new Cell()
                .add(new Paragraph(texte(contenu)).setFontSize(GabaritDocument.CORPS_TABLEAU))
                .setBorder(new SolidBorder(GabaritDocument.GRIS_FILET, 0.5f))
                .setPadding(4f);
    }

    private String intituleJournee(EtatConsolide.Journee journee) {
        LocalDate date = journee.dateJour();
        if (date == null) {
            return "Journee sans date"
                    + (journee.idFicheJournaliere() == null
                            ? "" : " (fiche n° " + journee.idFicheJournaliere() + ")");
        }
        return "Journee du " + date.format(JOUR);
    }

    private String intituleCourtJournee(EtatConsolide.Journee journee) {
        LocalDate date = journee.dateJour();
        return date == null ? "cette journee" : date.format(JOUR);
    }

    /**
     * La periode telle qu'elle s'imprime sur le document : « du 7 septembre 2026 au
     * 13 septembre 2026 ».
     *
     * <p>Le tableau {@code MOIS} sert toujours, mais a nommer le mois de chaque
     * borne — non plus a nommer la periode elle-meme, qui n'est plus un mois.
     */
    private String periodeEnToutesLettres(ProcessusMensuel processus) {
        return "du " + jourEnToutesLettres(processus.getDateDebut())
                + " au " + jourEnToutesLettres(processus.getDateFin());
    }

    private String jourEnToutesLettres(LocalDate jour) {
        if (jour == null) {
            return "(date absente)";
        }
        return jour.getDayOfMonth() + " " + MOIS[jour.getMonthValue() - 1] + " " + jour.getYear();
    }

    /**
     * Montant en FCFA, entier, groupe par milliers avec une espace ordinaire.
     *
     * <p>Espace ordinaire et non insecable fine : l'encodage WinAnsi de
     * Times-Roman ne porte pas ce caractere, qui s'imprimerait en carre vide sur
     * chaque montant du document.
     */
    private String fcfa(long montant) {
        String chiffres = Long.toString(Math.abs(montant));
        StringBuilder groupe = new StringBuilder();
        for (int i = 0; i < chiffres.length(); i++) {
            if (i > 0 && (chiffres.length() - i) % 3 == 0) {
                groupe.append(' ');
            }
            groupe.append(chiffres.charAt(i));
        }
        return (montant < 0 ? "-" : "") + groupe + " FCFA";
    }

    private long valeur(Long valeur) {
        return valeur == null ? 0L : valeur;
    }

    private long valeur(Integer valeur) {
        return valeur == null ? 0L : valeur;
    }

    private String nomBeneficiaire(EtatConsolide.Beneficiaire beneficiaire) {
        if (beneficiaire == null) {
            return "";
        }
        String nom = texte(beneficiaire.nom());
        String prenom = texte(beneficiaire.prenom());
        return (nom + " " + prenom).strip();
    }

    private String capitaliser(String valeur) {
        String propre = texte(valeur);
        if (propre.isEmpty()) {
            return propre;
        }
        return propre.charAt(0) + propre.substring(1).toLowerCase();
    }

    private String texte(String valeur) {
        return valeur == null ? "" : valeur;
    }


}
