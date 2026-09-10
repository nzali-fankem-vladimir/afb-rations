package cm.afrilandfirstbank.rations.reporting.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.reporting.domaine.Rapport;
import cm.afrilandfirstbank.rations.reporting.domaine.Rapport.LigneRapport;
import cm.afrilandfirstbank.rations.reporting.domaine.Rapport.SousTotalAgence;
import cm.afrilandfirstbank.rations.reporting.domaine.SituationIntegration;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.ExportImpossibleException;

/**
 * Export Excel du rapport d'activité (Apache POI, Sprint 6.2, US-16).
 *
 * <h2>Ce service ne calcule rien, comme {@link ExportPdfService}</h2>
 *
 * <p>Il reçoit le même {@link Rapport}, déjà entièrement calculé par
 * {@link RapportService}, et se contente de le poser dans un classeur. Aucune
 * somme n'est refaite ici (CT-32).
 *
 * <h2>Les montants sont des nombres, jamais du texte</h2>
 *
 * <p>Contrairement au PDF, cet export sert au <b>retraitement</b> : un total écrit
 * en chaîne de caractères, même correctement formaté (« 84 000 FCFA »), empêcherait
 * toute somme dans le tableur. Chaque cellule de montant reçoit donc
 * {@link Cell#setCellValue(double)} et un {@link CellStyle} de <b>présentation</b>
 * seulement (séparateur de milliers) — jamais {@link Cell#setCellValue(String)}. Le
 * test 9 du guide vérifie ce point en relisant le fichier produit et en contrôlant
 * le type de cellule.
 *
 * <h2>Deux feuilles</h2>
 *
 * <p>« Detail » porte une ligne par état (même grain que le PDF et l'écran) ;
 * « Synthese » porte les totaux généraux, les sous-totaux par agence et les deux
 * répartitions — le guide §5 demande une feuille de synthèse quand la structure
 * arbitrée le prévoit, et l'étape 1 en a arbitré une.
 *
 * <h2>Période sans données (CT-33)</h2>
 *
 * <p>Un rapport {@code vide} produit tout de même un classeur valide, avec la
 * mention « Aucune activite enregistree pour cette periode. » et des totaux à zéro
 * — jamais un fichier absent ou une erreur.
 */
@Service
public class ExportExcelService {

    private static final DateTimeFormatter HORODATAGE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final String MENTION_PERIODE_SANS_DONNEES =
            "Aucune activite enregistree pour cette periode.";

    /**
     * Le rapport mis en classeur, en octets. Rien n'est écrit sur disque : le
     * contrôleur renvoie ces octets en téléchargement.
     *
     * @throws ExportImpossibleException la composition a échoué. Un classeur
     *         partiel n'est jamais rendu.
     */
    public byte[] exporter(Rapport rapport) {
        try (XSSFWorkbook classeur = new XSSFWorkbook();
                ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {

            CellStyle styleEntete = styleEntete(classeur);
            CellStyle styleMontant = styleMontant(classeur);

            composerFeuilleDetail(classeur, rapport, styleEntete, styleMontant);
            composerFeuilleSynthese(classeur, rapport, styleEntete, styleMontant);

            classeur.write(sortie);
            return sortie.toByteArray();

        } catch (IOException | RuntimeException echec) {
            throw new ExportImpossibleException(
                    "Le rapport Excel du " + rapport.periodeDebut() + " au " + rapport.periodeFin()
                            + " n'a pas pu etre produit (" + echec.getMessage() + ").",
                    echec);
        }
    }

    // --- Feuille "Detail" --------------------------------------------------------

    private void composerFeuilleDetail(XSSFWorkbook classeur, Rapport rapport,
            CellStyle styleEntete, CellStyle styleMontant) {

        Sheet feuille = classeur.createSheet("Detail");
        int ligneCourante = 0;

        ligneCourante = composerBlocIdentification(feuille, rapport, styleEntete, ligneCourante);
        ligneCourante++; // ligne vide de separation

        if (rapport.vide()) {
            feuille.createRow(ligneCourante).createCell(0).setCellValue(MENTION_PERIODE_SANS_DONNEES);
            autoDimensionner(feuille, 7);
            return;
        }

        Row entetes = feuille.createRow(ligneCourante++);
        String[] colonnes = {"N° dossier", "Unite", "Type", "Statut", "Montant FCFA",
                "Envoye compta.", "Situation integration"};
        for (int colonne = 0; colonne < colonnes.length; colonne++) {
            Cell cellule = entetes.createCell(colonne);
            cellule.setCellValue(colonnes[colonne]);
            cellule.setCellStyle(styleEntete);
        }

        for (LigneRapport ligne : rapport.lignes()) {
            Row rangee = feuille.createRow(ligneCourante++);
            rangee.createCell(0).setCellValue(ligne.idProcessus());
            rangee.createCell(1).setCellValue(texte(ligne.codeUnite()));
            rangee.createCell(2).setCellValue(texte(ligne.typeProcessus()));
            rangee.createCell(3).setCellValue(texte(ligne.statut()));

            // Montant : valeur NUMERIQUE, jamais une chaine — c'est ce qui rend la
            // colonne sommable dans le tableur (voir la Javadoc de cette classe).
            Cell montant = rangee.createCell(4);
            montant.setCellValue((double) ligne.montantTotal());
            montant.setCellStyle(styleMontant);

            rangee.createCell(5).setCellValue(ligne.envoyeComptabilite() ? "Oui" : "Non");
            rangee.createCell(6).setCellValue(libelle(ligne.situationIntegration()));
        }

        autoDimensionner(feuille, colonnes.length);
    }

    // --- Feuille "Synthese" --------------------------------------------------------

    private void composerFeuilleSynthese(XSSFWorkbook classeur, Rapport rapport,
            CellStyle styleEntete, CellStyle styleMontant) {

        Sheet feuille = classeur.createSheet("Synthese");
        Rapport.Synthese synthese = rapport.synthese();
        int ligneCourante = 0;

        ligneCourante = composerBlocIdentification(feuille, rapport, styleEntete, ligneCourante);
        ligneCourante++;

        ligneCourante = ligneMontant(feuille, ligneCourante, "Nombre d'etats",
                synthese.nombreEtats(), null);
        ligneCourante = ligneMontant(feuille, ligneCourante, "Montant total de la periode",
                synthese.montantTotalPeriode(), styleMontant);
        ligneCourante = ligneMontant(feuille, ligneCourante, "Montant envoye a la comptabilite",
                synthese.montantEnvoyeComptabilite(), styleMontant);
        ligneCourante = ligneMontant(feuille, ligneCourante, "Montant non envoye a la comptabilite",
                synthese.montantNonEnvoyeComptabilite(), styleMontant);
        ligneCourante = ligneMontant(feuille, ligneCourante, "Montant rejete par la comptabilite",
                synthese.montantRejeteComptabilite(), styleMontant);
        ligneCourante++;

        if (!rapport.sousTotauxParAgence().isEmpty()) {
            Row entetesAgences = feuille.createRow(ligneCourante++);
            String[] colonnesAgences = {"Agence", "Nombre d'etats", "Montant FCFA"};
            for (int colonne = 0; colonne < colonnesAgences.length; colonne++) {
                Cell cellule = entetesAgences.createCell(colonne);
                cellule.setCellValue(colonnesAgences[colonne]);
                cellule.setCellStyle(styleEntete);
            }
            for (SousTotalAgence sousTotal : rapport.sousTotauxParAgence()) {
                Row rangee = feuille.createRow(ligneCourante++);
                rangee.createCell(0).setCellValue(texte(sousTotal.codeUnite()));
                rangee.createCell(1).setCellValue(sousTotal.nombreEtats());
                Cell montant = rangee.createCell(2);
                montant.setCellValue((double) sousTotal.montantTotal());
                montant.setCellStyle(styleMontant);
            }
            ligneCourante++;
        }

        ligneCourante = composerRepartition(feuille, ligneCourante,
                "Repartition par statut d'avancement", synthese.repartitionParStatut(), styleEntete);
        ligneCourante++;

        Map<String, Integer> repartitionSituation = new java.util.LinkedHashMap<>();
        for (Map.Entry<SituationIntegration, Integer> entree
                : synthese.repartitionParSituation().entrySet()) {
            repartitionSituation.put(libelle(entree.getKey()), entree.getValue());
        }
        composerRepartition(feuille, ligneCourante,
                "Repartition par situation d'integration", repartitionSituation, styleEntete);

        autoDimensionner(feuille, 3);
    }

    private int composerRepartition(Sheet feuille, int ligneCourante, String titre,
            Map<String, Integer> comptes, CellStyle styleEntete) {

        Row entete = feuille.createRow(ligneCourante++);
        Cell titreCellule = entete.createCell(0);
        titreCellule.setCellValue(titre);
        titreCellule.setCellStyle(styleEntete);

        for (Map.Entry<String, Integer> entree : comptes.entrySet()) {
            Row rangee = feuille.createRow(ligneCourante++);
            rangee.createCell(0).setCellValue(entree.getKey());
            rangee.createCell(1).setCellValue(entree.getValue());
        }
        return ligneCourante;
    }

    private int ligneMontant(Sheet feuille, int ligneCourante, String libelle, long valeur,
            CellStyle styleMontant) {
        Row rangee = feuille.createRow(ligneCourante);
        rangee.createCell(0).setCellValue(libelle);
        Cell cellule = rangee.createCell(1);
        cellule.setCellValue((double) valeur);
        if (styleMontant != null) {
            cellule.setCellStyle(styleMontant);
        }
        return ligneCourante + 1;
    }

    // --- Bloc d'identification, commun aux deux feuilles ----------------------------

    /** Période, agence, date de génération, producteur — même contenu que le PDF (guide 6.2, étape 4). */
    private int composerBlocIdentification(Sheet feuille, Rapport rapport, CellStyle styleEntete,
            int ligneDepart) {

        int ligneCourante = ligneDepart;
        ligneCourante = ligneTexte(feuille, ligneCourante, "Periode",
                "du " + rapport.periodeDebut() + " au " + rapport.periodeFin(), styleEntete);
        ligneCourante = ligneTexte(feuille, ligneCourante, "Agence",
                rapport.codeUnite() == null ? "Toutes les unites" : rapport.codeUnite(), styleEntete);
        ligneCourante = ligneTexte(feuille, ligneCourante, "Date de generation",
                rapport.dateGeneration().format(HORODATAGE), styleEntete);
        ligneCourante = ligneTexte(feuille, ligneCourante, "Produit par",
                texte(rapport.loginUtilisateur()), styleEntete);
        return ligneCourante;
    }

    private int ligneTexte(Sheet feuille, int ligneCourante, String libelle, String valeur,
            CellStyle styleEntete) {
        Row rangee = feuille.createRow(ligneCourante);
        Cell celluleLibelle = rangee.createCell(0);
        celluleLibelle.setCellValue(libelle);
        celluleLibelle.setCellStyle(styleEntete);
        rangee.createCell(1).setCellValue(valeur);
        return ligneCourante + 1;
    }

    // --- Styles ------------------------------------------------------------------

    private CellStyle styleEntete(XSSFWorkbook classeur) {
        Font gras = classeur.createFont();
        gras.setBold(true);
        CellStyle style = classeur.createCellStyle();
        style.setFont(gras);
        return style;
    }

    /**
     * Style de <b>présentation</b> seulement : séparateur de milliers. La valeur de
     * la cellule reste un nombre à part entière — ce style ne change pas son type.
     */
    private CellStyle styleMontant(XSSFWorkbook classeur) {
        DataFormat format = classeur.createDataFormat();
        CellStyle style = classeur.createCellStyle();
        style.setDataFormat(format.getFormat("#,##0"));
        return style;
    }

    private void autoDimensionner(Sheet feuille, int nombreColonnes) {
        for (int colonne = 0; colonne < nombreColonnes; colonne++) {
            feuille.autoSizeColumn(colonne);
        }
    }

    private String texte(String valeur) {
        return valeur == null ? "" : valeur;
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
}
