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
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import cm.afrilandfirstbank.rations.workflow.domaine.NomEtapeEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.TypeProcessusEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.DocumentNonProduitException;

/**
 * Production du document PDF de l'etat de paiement (iText 8, US-06, Sprint 4.2).
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
 * Les signatures suivantes ne repassent pas par ici : elles estampent le fichier
 * existant dans la bande des visas que la creation leur a reservee. Regenerer a
 * chaque etape ferait perdre les signatures precedentes.
 *
 * <h2>Contenu (retour utilisateur post-7F.7)</h2>
 *
 * <p>Le document ne porte que ce qui reste vrai apres sa generation. Il n'imprime
 * <b>aucun statut</b> : il n'est jamais regenere apres la soumission, il afficherait
 * donc le statut d'avant soumission sur un etat deja cloture. L'avancement se lit sur
 * les visas. Le type d'etat n'apparait que pour un complementaire, avec l'etat dont
 * il depend et son motif. Les journees sans ligne ne sont pas imprimees.
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
 * <p>Les sous-totaux journaliers et le total de la periode sont <b>recopies</b> de
 * l'etat consolide, jamais readdiitionnes. RG-06 est partagee entre Saisie et
 * Workflow (decision Sprint 3.4) : il n'existe qu'un seul chemin de calcul, donc
 * aucune divergence possible entre le detail imprime et le total imprime. Un
 * document qui recalculerait pourrait afficher un total different de celui qui
 * commande l'aiguillage au seuil.
 */
@Service
public class DocumentService {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String[] MOIS = {
            "janvier", "février", "mars", "avril", "mai", "juin",
            "juillet", "août", "septembre", "octobre", "novembre", "décembre"};

    /** Largeurs relatives des sept colonnes du detail : date, beneficiaire, compte, agence, nature, session, montant. */
    private static final float[] COLONNES = {13f, 19f, 18f, 10f, 10f, 10f, 20f};

    /** Nombre de colonnes qui precedent le montant, pour les lignes de total. */
    private static final int COLONNES_AVANT_MONTANT = 6;

    /**
     * Compose le document de l'etat de paiement.
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
            PdfFont normal = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont gras = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            pdf.addEventHandler(PdfDocumentEvent.END_PAGE, new PiedDePage(normal));

            try (Document document = new Document(pdf, GabaritDocument.FORMAT)) {
                // La marge basse reserve la bande des visas sur TOUTES les pages : le
                // detail ne l'atteint jamais, et elle se trouve libre sur la derniere.
                document.setMargins(GabaritDocument.MARGE, GabaritDocument.MARGE,
                        GabaritDocument.MARGE_BASSE, GabaritDocument.MARGE);
                document.setFont(normal)
                        .setFontSize(GabaritDocument.CORPS)
                        .setFontColor(GabaritDocument.NOIR);

                composerEnTete(document, gras);
                composerIdentification(document, processus, etat, gras);
                composerDetailEtTotal(document, etat, gras);

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
                .setMarginBottom(10f));

        document.add(new Paragraph("ÉTAT DE PAIEMENT")
                .setFont(gras)
                .setFontSize(GabaritDocument.TITRE)
                .setFontColor(GabaritDocument.NOIR)
                .setUnderline()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(2f));

        document.add(new Paragraph("Rations et transport de la garde armée")
                .setFont(gras)
                .setFontSize(GabaritDocument.TITRE)
                .setFontColor(GabaritDocument.ROUGE)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(14f));
    }

    /**
     * Bloc d'identification compact : unite et numero de dossier sur une ligne, la
     * periode sur la suivante, puis le volume de prestations. Pour un complementaire
     * seulement, l'etat dont il depend et le motif de son ouverture.
     */
    private void composerIdentification(Document document, ProcessusMensuel processus,
            EtatConsolide etat, PdfFont gras) {

        Table table = new Table(UnitValue.createPercentArray(new float[] {16f, 44f, 18f, 22f}))
                .useAllAvailableWidth()
                .setMarginBottom(12f);

        table.addCell(cellule("Unité", 1).setFont(gras));
        table.addCell(cellule(processus.getCodeUnite(), 1));
        table.addCell(cellule("Dossier n°", 1).setFont(gras));
        table.addCell(cellule(String.valueOf(processus.getId()), 1));

        ajouterLigneEtalee(table, "Période", periodeEnToutesLettres(processus), gras);

        if (processus.getTypeProcessus() == TypeProcessusEnum.COMPLEMENTAIRE) {
            ajouterLigneEtalee(table, "Type", "Complémentaire de l'état n° "
                    + processus.getIdProcessusOrigine(), gras);
            String motif = texte(processus.getMotifOuverture()).strip();
            if (!motif.isEmpty()) {
                ajouterLigneEtalee(table, "Motif", motif, gras);
            }
        }

        ajouterLigneEtalee(table, "Prestations",
                pluriel(valeur(etat.nombreBeneficiaires()), "bénéficiaire") + ", "
                        + pluriel(valeur(etat.nombreLignes()), "ligne"), gras);

        document.add(table);
    }

    private void ajouterLigneEtalee(Table table, String libelle, String valeur, PdfFont gras) {
        table.addCell(cellule(libelle, 1).setFont(gras));
        table.addCell(cellule(valeur, 3));
    }

    // --- Detail et total ---------------------------------------------------------

    /**
     * Un seul tableau pour tout l'etat, avec un seul en-tete : la date est une
     * colonne, et chaque journee se referme sur son sous-total. Les journees
     * ouvertes sans aucune ligne ne sont pas imprimees, elles n'apprennent rien au
     * valideur. Le total de la periode ferme le tableau.
     */
    private void composerDetailEtTotal(Document document, EtatConsolide etat, PdfFont gras) {
        document.add(titreSection("DÉTAIL DES PRESTATIONS", gras));

        Table table = new Table(UnitValue.createPercentArray(COLONNES))
                .useAllAvailableWidth()
                .setFixedLayout();

        for (String entete : new String[] {
                "Date", "Bénéficiaire", "N° compte", "Agence", "Nature", "Session", "Montant"}) {
            table.addHeaderCell(cellule(entete, 1).setFont(gras));
        }

        List<EtatConsolide.Journee> journees =
                etat.journees() == null ? List.of() : etat.journees();

        for (EtatConsolide.Journee journee : journees) {
            if (journee == null || journee.lignes() == null || journee.lignes().isEmpty()) {
                continue;
            }

            String date = dateDeLaJournee(journee);

            for (EtatConsolide.Ligne ligne : journee.lignes()) {
                if (ligne == null) {
                    continue;
                }
                EtatConsolide.Beneficiaire beneficiaire = ligne.beneficiaire();
                table.addCell(cellule(date, 1));
                table.addCell(cellule(nomBeneficiaire(beneficiaire), 1));
                table.addCell(cellule(beneficiaire == null ? "" : texte(beneficiaire.numCompteCourant()), 1));
                table.addCell(cellule(beneficiaire == null ? "" : texte(beneficiaire.codeAgence()), 1));
                table.addCell(cellule(capitaliser(ligne.nature()), 1));
                table.addCell(cellule(capitaliser(ligne.session()), 1));
                table.addCell(cellule(fcfa(valeur(ligne.montantApplique())), 1)
                        .setTextAlignment(TextAlignment.RIGHT));
            }

            ajouterLigneDeTotal(table,
                    journee.dateJour() == null
                            ? "Sous-total (journée sans date)"
                            : "Sous-total du " + date,
                    valeur(journee.sousTotalFcfa()), gras);
        }

        // Total de la periode, recopie de l'etat consolide et jamais recalcule.
        ajouterLigneDeTotal(table, "TOTAL DE LA PÉRIODE", valeur(etat.montantTotalFcfa()), gras);

        document.add(table);
    }

    private void ajouterLigneDeTotal(Table table, String libelle, long montant, PdfFont gras) {
        table.addCell(cellule(libelle, COLONNES_AVANT_MONTANT)
                .setFont(gras)
                .setTextAlignment(TextAlignment.RIGHT));
        table.addCell(cellule(fcfa(montant), 1)
                .setFont(gras)
                .setTextAlignment(TextAlignment.RIGHT));
    }

    // --- Bande des visas ---------------------------------------------------------

    /**
     * Trace les cadres a leur position fixe et y inscrit les mentions connues.
     *
     * <p>Le cadre de l'agent et celui du chef d'unite sont toujours traces : un
     * cadre reste vide plutot que d'etre absent, de sorte que la geometrie ne depend
     * jamais du circuit. <b>Le cadre du directeur reseau n'est trace que s'il porte
     * une mention</b> : son visa n'intervient qu'au-dela du seuil (RG-08), decision
     * prise a la validation du chef d'unite, pas a la creation du document. C'est
     * l'estampage de cette validation qui trace le cadre quand l'etat est aiguille
     * vers lui (retour utilisateur post-7F.7).
     *
     * <p><b>Un cadre non signe reste rigoureusement vide.</b> Aucune mention « en
     * attente » n'y est gravee : l'estampage ajoute du contenu sans jamais en
     * retirer, et une telle mention resterait lisible sous la signature venue
     * s'inscrire par-dessus.
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
            MentionSignature mention = parEtape.get(etape);
            if (etape == NomEtapeEnum.VALIDATION_DR && mention == null) {
                continue;
            }
            RedacteurVisa.tracerCadre(page, etape, gras);
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
                .setMarginTop(4f)
                .setMarginBottom(6f);
    }

    /** Cellule a filets fins noirs, fond blanc, sans aucun aplat de couleur (charte 8.2). */
    private Cell cellule(String contenu, int colonnes) {
        return new Cell(1, colonnes)
                .add(new Paragraph(texte(contenu)).setFontSize(GabaritDocument.CORPS_TABLEAU))
                .setBorder(new SolidBorder(GabaritDocument.FILET, 0.5f))
                .setPadding(3f);
    }

    private String dateDeLaJournee(EtatConsolide.Journee journee) {
        LocalDate date = journee.dateJour();
        return date == null ? "sans date" : date.format(JOUR);
    }

    /**
     * La periode telle qu'elle s'imprime sur le document, en ne repetant que ce qui
     * change : « du 4 au 10 octobre 2027 », « du 29 septembre au 5 octobre 2027 »,
     * et les deux dates en entier seulement d'une annee a l'autre.
     */
    private String periodeEnToutesLettres(ProcessusMensuel processus) {
        LocalDate debut = processus.getDateDebut();
        LocalDate fin = processus.getDateFin();

        if (debut == null || fin == null) {
            return "(date absente)";
        }
        if (debut.equals(fin)) {
            return "le " + jourEtMois(debut) + " " + debut.getYear();
        }
        if (debut.getYear() != fin.getYear()) {
            return "du " + jourEtMois(debut) + " " + debut.getYear()
                    + " au " + jourEtMois(fin) + " " + fin.getYear();
        }
        if (debut.getMonth() != fin.getMonth()) {
            return "du " + jourEtMois(debut) + " au " + jourEtMois(fin) + " " + fin.getYear();
        }
        return "du " + debut.getDayOfMonth() + " au " + jourEtMois(fin) + " " + fin.getYear();
    }

    private String jourEtMois(LocalDate jour) {
        return jour.getDayOfMonth() + " " + MOIS[jour.getMonthValue() - 1];
    }

    private String pluriel(long nombre, String nom) {
        return nombre + " " + nom + (nombre > 1 ? "s" : "");
    }

    /**
     * Montant en FCFA, entier, groupe par milliers avec une espace ordinaire.
     *
     * <p>Espace ordinaire et non insecable fine : l'encodage WinAnsi de
     * Helvetica ne porte pas ce caractere, qui s'imprimerait en carre vide sur
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
