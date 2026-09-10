package cm.afrilandfirstbank.rations.workflow.application;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;

/**
 * Convention de nommage et de rangement du document mensuel (validee au
 * Sprint 4.2).
 *
 * <pre>
 *   2026/09/etat-rations-00002-202609-p109.pdf
 * </pre>
 *
 * <h2>Chaque morceau repond a une raison</h2>
 *
 * <table>
 *   <tr><td>{@code 00002}</td>
 *       <td>le <b>code unite</b>, comme l'etape 4 du guide le demande. C'est
 *           l'unite qui supporte la charge (ligne de debit), jamais le code
 *           agence du beneficiaire (ligne de credit).</td></tr>
 *   <tr><td>{@code 202609}</td>
 *       <td>annee <b>puis</b> mois, sur six chiffres. Un tri alphabetique du
 *           repertoire donne alors l'ordre chronologique reel ; {@code 09-2026}
 *           placerait septembre 2026 avant janvier 2027.</td></tr>
 *   <tr><td>{@code p109}</td>
 *       <td>l'identifiant du processus. <b>Sans lui, deux etats produiraient le
 *           meme nom de fichier.</b></td></tr>
 * </table>
 *
 * <h2>Pourquoi l'identifiant du processus n'est pas facultatif</h2>
 *
 * <p>CLAUDE.md section 4 n'impose l'unicite que sur le processus <b>NORMAL</b> :
 * l'index partiel {@code ux_processus_normal_par_periode} laisse ouverts
 * <i>plusieurs</i> etats COMPLEMENTAIRE sur un meme couple unite / periode
 * (Sprint 6bis). Un nom fonde sur la seule unite et la seule periode ferait donc
 * collision, et le renommage atomique de l'ecriture ecraserait le PDF signe du
 * premier etat.
 *
 * <p>La contrainte {@code id_processus UNIQUE} de {@code piece_jointe} ne
 * protegerait pas de cela : elle porte sur des identifiants qui different. Deux
 * lignes distinctes en base pointeraient vers un seul et meme fichier, sans
 * qu'aucune contrainte ne s'en apercoive.
 *
 * <h2>Un chemin relatif, jamais absolu</h2>
 *
 * <p>C'est ce chemin, et lui seul, qui est enregistre dans
 * {@code piece_jointe.chemin_fichier}. La racine du stockage est une donnee de
 * configuration ({@code app.pieces-jointes.repertoire}) : une racine absolue en
 * base rendrait toute la table fausse le jour ou le volume change de point de
 * montage entre le poste de developpement et Kubernetes.
 *
 * <p>Les sous-dossiers {@code {annee}/{mois}} evitent un repertoire plat de
 * plusieurs milliers de fichiers. Minuscules, tirets, aucun accent, aucune espace :
 * sur pour tout systeme de fichiers, toute URL et tout conteneur Linux.
 */
public final class NommageDocument {

    private static final String PREFIXE = "etat-rations";
    private static final String EXTENSION = ".pdf";

    private NommageDocument() {
        // classe utilitaire
    }

    /**
     * Chemin relatif du document d'un processus, sous-dossiers compris.
     *
     * <p><b>Deterministe</b> : le meme processus produit toujours le meme chemin.
     * C'est ce qui permet de retrouver le fichier a l'estampage des sous-sprints
     * 4.3 et 4.4 sans dependre d'autre chose que du processus lui-meme — le chemin
     * enregistre en base restant la reference.
     */
    /**
     * Jour de debut en {@code AAAAMMJJ}, sans separateur.
     *
     * <p>Le format compact est ce qui fait qu'un tri alphabetique du repertoire est
     * un tri chronologique — propriete voulue depuis le Sprint 4.2, conservee ici.
     * Le jour remplace le mois : quatre periodes hebdomadaires d'un meme mois
     * portaient sinon le meme nom.
     */
    private static final DateTimeFormatter JOUR_COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static String cheminRelatif(ProcessusMensuel processus) {
        LocalDate debut = processus.getDateDebut();
        return "%d/%02d/%s-%s-%s-p%d%s".formatted(
                debut.getYear(), debut.getMonthValue(),
                PREFIXE, processus.getCodeUnite(),
                debut.format(JOUR_COMPACT),
                processus.getId(),
                EXTENSION);
    }

    /** Nom du fichier seul, sans les sous-dossiers. */
    public static String nomFichier(ProcessusMensuel processus) {
        String chemin = cheminRelatif(processus);
        return chemin.substring(chemin.lastIndexOf('/') + 1);
    }

}
