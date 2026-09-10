package cm.afrilandfirstbank.rations.reporting.application;

import java.time.LocalDate;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
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

import cm.afrilandfirstbank.rations.reporting.domaine.Rapport;
import cm.afrilandfirstbank.rations.reporting.domaine.Rapport.LigneRapport;
import cm.afrilandfirstbank.rations.reporting.domaine.Rapport.SousTotalAgence;
import cm.afrilandfirstbank.rations.reporting.domaine.SituationIntegration;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.ExportImpossibleException;

/**
 * Export PDF du rapport d'activité (iText 8, Sprint 6.2, US-16).
 *
 * <h2>Ce service ne calcule rien</h2>
 *
 * <p>Il reçoit un {@link Rapport} entièrement produit par {@link RapportService} et
 * ne fait que le mettre en page. Aucune somme n'est refaite : c'est la garantie de
 * CT-32 — l'écran, ce PDF et l'Excel partent du même objet, donc des mêmes chiffres
 * (test 6 du guide).
 *
 * <h2>Charte de production, document maître section 8.2</h2>
 *
 * <p>A4, marges de 2 cm, logo centré en tête de la <b>première page uniquement</b>,
 * aucun en-tête sur les pages, titre noir souligné et sous-titre rouge en 20 pt,
 * pied de page réduit au numéro de page en gris 9 pt, tableaux à filets fins gris
 * <b>sans aucun aplat de couleur</b>, rouge réservé au seul sous-titre.
 *
 * <h2>Écart assumé : la police</h2>
 *
 * <p>Times-Roman au lieu de Bookman Old Style, exactement comme l'état mensuel du
 * Sprint 4.2 : Bookman est une fonte licenciée avec Windows/Office, l'embarquer
 * dans une image Docker serait une redistribution. Times-Roman est native au format
 * PDF, sans octet embarqué ni licence. Même genre (serif), même lisibilité.
 *
 * <h2>Période sans données (CT-33)</h2>
 *
 * <p>Un rapport {@code vide} produit tout de même un PDF valide : l'en-tête, puis la
 * mention « Aucune activité enregistrée pour cette période. » à la place des
 * tableaux. C'est une information, pas une erreur.
 */
@Service
public class ExportPdfService {

    private static final PageSize FORMAT = PageSize.A4;

    /** 2 cm en points PDF : 2 / 2.54 * 72. */
    private static final float MARGE = 56.7f;

    private static final DeviceRgb NOIR = new DeviceRgb(0x1A, 0x1A, 0x1A);
    private static final DeviceRgb ROUGE = new DeviceRgb(0xE3, 0x06, 0x13);
    private static final DeviceRgb GRIS = new DeviceRgb(0x66, 0x66, 0x66);
    private static final DeviceRgb GRIS_FILET = new DeviceRgb(0xCC, 0xCC, 0xCC);

    private static final float CORPS = 12f;
    private static final float CORPS_TABLEAU = 9f;
    private static final float TITRE = 20f;
    private static final float PIED_DE_PAGE = 9f;
    private static final float LOGO_LARGEUR = 170f;
    private static final String LOGO_RESSOURCE = "/assets/logo afriland.png";

    private static final DateTimeFormatter HORODATAGE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a' HH:mm");

    private static final String[] MOIS = {
            "janvier", "fevrier", "mars", "avril", "mai", "juin",
            "juillet", "aout", "septembre", "octobre", "novembre", "decembre"};

    /**
     * Le rapport mis en page, en octets. Rien n'est écrit sur disque : le contrôleur
     * renvoie ces octets en téléchargement.
     *
     * @throws ExportImpossibleException la composition a échoué. Un PDF partiel n'est
     *         jamais rendu.
     */
    public byte[] exporter(Rapport rapport) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();

        try {
            PdfDocument pdf = new PdfDocument(new PdfWriter(sortie));
            PdfFont normal = PdfFontFactory.createFont(StandardFonts.TIMES_ROMAN);
            PdfFont gras = PdfFontFactory.createFont(StandardFonts.TIMES_BOLD);

            pdf.addEventHandler(PdfDocumentEvent.END_PAGE, new PiedDePage(normal));

            try (Document document = new Document(pdf, FORMAT)) {
                document.setMargins(MARGE, MARGE, MARGE, MARGE);
                document.setFont(normal).setFontSize(CORPS).setFontColor(NOIR);

                composerEnTete(document, gras);
                composerIdentification(document, rapport, gras);

                // Pas de "return" ici : un retour a l'interieur du try-with-resources
                // capturerait les octets AVANT la fermeture du document, donc avant
                // l'ecriture de la table xref et du trailer — un PDF illisible que
                // seul le test 11 (relecture reelle du fichier) a permis de voir.
                if (rapport.vide()) {
                    document.add(new Paragraph(
                            "Aucune activite enregistree pour cette periode.")
                            .setFontSize(CORPS)
                            .setMarginTop(18f));
                } else {
                    composerDetail(document, rapport, gras);
                    composerSousTotaux(document, rapport, gras);
                    composerSynthese(document, rapport, gras);
                }
            }

            return sortie.toByteArray();

        } catch (IOException | RuntimeException echec) {
            throw new ExportImpossibleException(
                    "Le rapport PDF de " + rapport.periodeDebut() + "/" + rapport.periodeFin()
                            + " n'a pas pu etre produit (" + echec.getMessage() + ").",
                    echec);
        }
    }

    // --- En-tête ---------------------------------------------------------------

    private void composerEnTete(Document document, PdfFont gras) throws IOException {
        document.add(new Image(ImageDataFactory.create(logo()))
                .setWidth(LOGO_LARGEUR)
                .setHorizontalAlignment(HorizontalAlignment.CENTER)
                .setMarginBottom(18f));

        document.add(new Paragraph("RAPPORT D'ACTIVITE")
                .setFont(gras).setFontSize(TITRE).setFontColor(NOIR)
                .setUnderline().setTextAlignment(TextAlignment.CENTER).setMarginBottom(2f));

        document.add(new Paragraph("Rations et transport de la garde armee")
                .setFont(gras).setFontSize(TITRE).setFontColor(ROUGE)
                .setTextAlignment(TextAlignment.CENTER).setMarginBottom(22f));
    }

    /** Période, agence, date de génération, producteur, nombre d'états (guide 6.2, étape 4). */
    private void composerIdentification(Document document, Rapport rapport, PdfFont gras) {
        Table table = new Table(UnitValue.createPercentArray(new float[] {32f, 68f}))
                .useAllAvailableWidth().setMarginBottom(18f);

        ligneIdentification(table, "Periode", periodeEnToutesLettres(rapport), gras);
        ligneIdentification(table, "Agence",
                rapport.codeUnite() == null ? "Toutes les unites" : rapport.codeUnite(), gras);
        ligneIdentification(table, "Date de generation",
                rapport.dateGeneration().format(HORODATAGE), gras);
        ligneIdentification(table, "Produit par", texte(rapport.loginUtilisateur()), gras);
        ligneIdentification(table, "Nombre d'etats",
                String.valueOf(rapport.synthese().nombreEtats()), gras);

        document.add(table);
    }

    private void ligneIdentification(Table table, String libelle, String valeur, PdfFont gras) {
        table.addCell(cellule(libelle).setFont(gras));
        table.addCell(cellule(valeur));
    }

    // --- Détail par état -----------------------------------------------------------

    private void composerDetail(Document document, Rapport rapport, PdfFont gras) {
        document.add(titreSection("DETAIL PAR ETAT", gras));

        Table table = new Table(UnitValue.createPercentArray(
                new float[] {11f, 11f, 15f, 17f, 17f, 12f, 17f})).useAllAvailableWidth();

        for (String entete : new String[] {
                "N° dossier", "Unite", "Type", "Statut", "Montant", "Envoye compta.", "Situation"}) {
            table.addHeaderCell(cellule(entete).setFont(gras));
        }

        for (LigneRapport ligne : rapport.lignes()) {
            table.addCell(cellule(String.valueOf(ligne.idProcessus())));
            table.addCell(cellule(texte(ligne.codeUnite())));
            table.addCell(cellule(texte(ligne.typeProcessus())));
            table.addCell(cellule(texte(ligne.statut())));
            table.addCell(cellule(fcfa(ligne.montantTotal())).setTextAlignment(TextAlignment.RIGHT));
            table.addCell(cellule(ligne.envoyeComptabilite() ? "Oui" : "Non"));
            table.addCell(cellule(libelle(ligne.situationIntegration())));
        }

        document.add(table);
    }

    // --- Sous-totaux par agence -------------------------------------------------

    private void composerSousTotaux(Document document, Rapport rapport, PdfFont gras) {
        if (rapport.sousTotauxParAgence().isEmpty()) {
            return;
        }
        document.add(titreSection("SOUS-TOTAUX PAR AGENCE", gras));

        Table table = new Table(UnitValue.createPercentArray(new float[] {40f, 25f, 35f}))
                .useAllAvailableWidth();
        for (String entete : new String[] {"Agence", "Nombre d'etats", "Montant"}) {
            table.addHeaderCell(cellule(entete).setFont(gras));
        }
        for (SousTotalAgence sousTotal : rapport.sousTotauxParAgence()) {
            table.addCell(cellule(texte(sousTotal.codeUnite())));
            table.addCell(cellule(String.valueOf(sousTotal.nombreEtats())));
            table.addCell(cellule(fcfa(sousTotal.montantTotal())).setTextAlignment(TextAlignment.RIGHT));
        }
        document.add(table);
    }

    // --- Synthèse -------------------------------------------------------------------

    private void composerSynthese(Document document, Rapport rapport, PdfFont gras) {
        Rapport.Synthese synthese = rapport.synthese();
        document.add(titreSection("SYNTHESE", gras));

        Table table = new Table(UnitValue.createPercentArray(new float[] {62f, 38f}))
                .useAllAvailableWidth();

        ligneSynthese(table, "Nombre d'etats", String.valueOf(synthese.nombreEtats()), gras);
        ligneSynthese(table, "Montant total de la periode",
                fcfa(synthese.montantTotalPeriode()), gras);
        ligneSynthese(table, "Montant envoye a la comptabilite",
                fcfa(synthese.montantEnvoyeComptabilite()), gras);
        ligneSynthese(table, "Montant non envoye a la comptabilite",
                fcfa(synthese.montantNonEnvoyeComptabilite()), gras);
        ligneSynthese(table, "Montant rejete par la comptabilite",
                fcfa(synthese.montantRejeteComptabilite()), gras);

        document.add(table);

        document.add(new Paragraph("Repartition des etats par statut d'avancement")
                .setFont(gras).setFontSize(CORPS_TABLEAU).setMarginTop(12f).setMarginBottom(4f));
        document.add(repartition(synthese.repartitionParStatut()));

        document.add(new Paragraph("Repartition des etats par situation d'integration")
                .setFont(gras).setFontSize(CORPS_TABLEAU).setMarginTop(10f).setMarginBottom(4f));
        Table parSituation = new Table(UnitValue.createPercentArray(new float[] {62f, 38f}))
                .useAllAvailableWidth();
        for (Map.Entry<SituationIntegration, Integer> entree
                : synthese.repartitionParSituation().entrySet()) {
            parSituation.addCell(cellule(libelle(entree.getKey())));
            parSituation.addCell(cellule(String.valueOf(entree.getValue())));
        }
        document.add(parSituation);
    }

    private Table repartition(Map<String, Integer> comptes) {
        Table table = new Table(UnitValue.createPercentArray(new float[] {62f, 38f}))
                .useAllAvailableWidth();
        for (Map.Entry<String, Integer> entree : comptes.entrySet()) {
            table.addCell(cellule(texte(entree.getKey())));
            table.addCell(cellule(String.valueOf(entree.getValue())));
        }
        return table;
    }

    private void ligneSynthese(Table table, String libelle, String valeur, PdfFont gras) {
        table.addCell(cellule(libelle).setFont(gras));
        table.addCell(cellule(valeur).setTextAlignment(TextAlignment.RIGHT));
    }

    // --- Pied de page --------------------------------------------------------------

    /** Numéro de page seul, centré, gris, 9 pt (charte 8.2). Aucun en-tête de page. */
    private record PiedDePage(PdfFont police) implements IEventHandler {

        @Override
        public void handleEvent(Event evenement) {
            PdfDocumentEvent evenementPdf = (PdfDocumentEvent) evenement;
            PdfPage page = evenementPdf.getPage();
            int numero = evenementPdf.getDocument().getPageNumber(page);

            try (Canvas canvas = new Canvas(page, page.getPageSize())) {
                canvas.setFont(police).setFontSize(PIED_DE_PAGE).setFontColor(GRIS)
                        .showTextAligned(String.valueOf(numero),
                                page.getPageSize().getWidth() / 2f, MARGE / 2f,
                                TextAlignment.CENTER);
            }
        }
    }

    // --- Mise en forme -----------------------------------------------------------

    private byte[] logo() throws IOException {
        try (InputStream flux = ExportPdfService.class.getResourceAsStream(LOGO_RESSOURCE)) {
            if (flux == null) {
                throw new IOException("Logo introuvable dans le classpath a " + LOGO_RESSOURCE);
            }
            return flux.readAllBytes();
        }
    }

    private Paragraph titreSection(String libelle, PdfFont gras) {
        return new Paragraph(libelle)
                .setFont(gras).setFontSize(CORPS).setFontColor(NOIR)
                .setMarginTop(14f).setMarginBottom(6f);
    }

    /** Cellule à filets fins gris, fond blanc, sans aucun aplat de couleur (charte 8.2). */
    private Cell cellule(String contenu) {
        return new Cell()
                .add(new Paragraph(texte(contenu)).setFontSize(CORPS_TABLEAU))
                .setBorder(new SolidBorder(GRIS_FILET, 0.5f))
                .setPadding(4f);
    }

    /**
     * La periode telle qu'elle s'imprime en tete du rapport : « du 7 septembre 2026
     * au 13 septembre 2026 ».
     *
     * <p>Le tableau {@code MOIS} sert toujours, mais a nommer le mois de chaque
     * borne — non plus a nommer la periode, qui n'est plus un mois (Maille 1, M-04).
     */
    private String periodeEnToutesLettres(Rapport rapport) {
        return "du " + jourEnToutesLettres(rapport.periodeDebut())
                + " au " + jourEnToutesLettres(rapport.periodeFin());
    }

    private String jourEnToutesLettres(LocalDate jour) {
        if (jour == null) {
            return "(date absente)";
        }
        return jour.getDayOfMonth() + " " + MOIS[jour.getMonthValue() - 1] + " " + jour.getYear();
    }

    /**
     * Montant FCFA, entier, groupé par milliers avec une espace ordinaire.
     *
     * <p>Espace ordinaire et non insécable fine : l'encodage WinAnsi de Times-Roman
     * ne porte pas ce caractère, qui s'imprimerait en carré vide (leçon du Sprint 4.2).
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

    private String libelle(SituationIntegration situation) {
        return switch (situation) {
            case NON_TRANSMIS -> "Non transmis";
            case PUBLICATION_NON_CONFIRMEE -> "Publication non confirmee";
            case EN_ATTENTE_ACCUSE -> "En attente d'accuse";
            case INTEGRE -> "Integre";
            case REJETE -> "Rejete";
        };
    }

    private String texte(String valeur) {
        return valeur == null ? "" : valeur;
    }
}
