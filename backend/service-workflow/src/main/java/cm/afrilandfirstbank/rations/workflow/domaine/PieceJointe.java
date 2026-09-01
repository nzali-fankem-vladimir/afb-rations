package cm.afrilandfirstbank.rations.workflow.domaine;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Le document de l'etat mensuel : <b>un seul par processus</b>, enrichi des
 * signatures successives jusqu'a la cloture (CLAUDE.md section 4, RG-09).
 *
 * <h2>Un seul document, jamais un par etape</h2>
 *
 * <p>La contrainte {@code id_processus UNIQUE} de la migration V1 l'impose en
 * base. Le code l'applique <b>en amont</b> : le service de soumission verifie
 * qu'aucune piece jointe n'existe avant d'en generer une. Sans ce controle
 * applicatif, une seconde tentative produirait une violation de contrainte,
 * c'est-a-dire un message technique illisible la ou l'agent a besoin d'une phrase
 * (point de vigilance section 10 du guide 4.2).
 *
 * <p>Un document par etape aurait multiplie les pieces et rendu la tracabilite
 * illisible : trois fichiers pour un meme etat, sans qu'aucun ne fasse foi.
 *
 * <h2>Le fichier vit sur un stockage, pas en base</h2>
 *
 * <p>{@code chemin_fichier} pointe vers ce stockage. Le chemin enregistre est
 * <b>relatif</b> a une racine de configuration ({@code app.pieces-jointes.repertoire}),
 * jamais absolu : une racine absolue en base rendrait toute la table fausse le
 * jour ou le volume change de point de montage entre le poste de developpement et
 * Kubernetes.
 *
 * <h2>nombre_signatures compte les ecritures, pas les etapes</h2>
 *
 * <p><b>C'est tout l'interet de cette colonne</b> (migration V3, Sprint 4.2).
 * Elle ne redit pas ce que {@code etape_workflow} sait deja : elle compte les
 * signatures <b>reellement ecrites dans le fichier PDF</b>. L'ecriture disque et
 * le commit de la transaction sont deux evenements independants — un disque plein
 * ou une erreur d'ecriture peuvent faire echouer l'un sans l'autre — et un
 * compteur derive des etapes serait toujours d'accord avec elles, donc incapable
 * de reveler quoi que ce soit.
 *
 * <p><b>Regle a ne jamais relacher :</b> les deux methodes de mutation ci-dessous
 * ne s'appellent qu'<b>apres confirmation d'ecriture</b> du fichier — fsync et
 * renommage atomique effectues. Les appeler dans la meme transaction que la
 * creation de l'etape, sans preuve d'ecriture, viderait la colonne de son sens.
 *
 * <h2>Conforme au dictionnaire, plus la seule colonne du 4.2</h2>
 *
 * <p>Hibernate tourne en {@code ddl-auto: validate} : les colonnes sont
 * exactement celles de la migration V1, plus {@code nombre_signatures} ajoutee
 * par V3. Ni {@code nom_fichier} — le nom est le dernier segment du chemin — ni
 * aucune autre colonne annoncee par le guide sans exister en base.
 */
@Entity
@Table(name = "piece_jointe")
public class PieceJointe {

    /** Nombre maximal de signatures sur un etat : agent, chef d'unite, directeur reseau (RG-09). */
    public static final int SIGNATURES_MAXIMUM = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Reference {@code processus_mensuel}, dans la meme base : vraie cle etrangere,
     * doublee d'une contrainte d'unicite. Modelise en identifiant simple, comme
     * partout ailleurs dans ce service — rien ici ne remonte au processus complet.
     */
    @Column(name = "id_processus", nullable = false, unique = true)
    private Long idProcessus;

    /** Chemin <b>relatif</b> a la racine de stockage configuree. */
    @Column(name = "chemin_fichier", nullable = false, length = 500)
    private String cheminFichier;

    @Column(name = "type_mime", nullable = false, length = 100)
    private String typeMime;

    /** Horodatage technique d'audit (convention transverse, Sprint 0.7). */
    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    /** Derniere fois que le fichier a ete enrichi d'une signature. Nul tant qu'aucun enrichissement n'a eu lieu. */
    @Column(name = "date_derniere_modification")
    private LocalDateTime dateDerniereModification;

    /** Signatures reellement ecrites dans le fichier (migration V3). */
    @Column(name = "nombre_signatures", nullable = false)
    private int nombreSignatures;

    protected PieceJointe() {
        // requis par JPA
    }

    /**
     * Enregistre la piece jointe d'un processus, <b>apres que son fichier a ete
     * ecrit et confirme sur disque</b>, portant deja la premiere signature.
     *
     * <p><b>N'appelez ce constructeur qu'avec un chemin rendu par le stockage</b>,
     * c'est-a-dire apres fsync et renommage atomique reussis. Le construire avant
     * l'ecriture, ou a partir d'un chemin calcule mais pas encore honore,
     * inscrirait en base une signature qui n'est peut-etre nulle part dans le
     * fichier — exactement ce que la colonne existe pour empecher.
     *
     * @param idProcessus le processus auquel ce document se rattache
     * @param cheminFichier chemin relatif rendu par le stockage apres ecriture confirmee
     */
    public PieceJointe(Long idProcessus, String cheminFichier) {
        this.idProcessus = idProcessus;
        this.cheminFichier = cheminFichier;
        this.typeMime = "application/pdf";
        this.nombreSignatures = 1;
    }

    @PrePersist
    void avantInsertion() {
        if (dateCreation == null) {
            dateCreation = LocalDateTime.now();
        }
    }

    /**
     * Constate qu'une signature supplementaire a ete ecrite dans le fichier
     * (sous-sprints 4.3 et 4.4).
     *
     * <p>Meme exigence que le constructeur : <b>a n'appeler qu'apres confirmation
     * d'ecriture</b>. Le document est enrichi par estampage du PDF existant, jamais
     * regenere — regenerer ferait perdre les signatures precedentes.
     *
     * @throws IllegalStateException au-dela de trois signatures. Un quatrieme
     *         appel signalerait une erreur de circuit, pas un cas metier : ET01
     *         ne prevoit que trois passages. Refuser vaut mieux qu'incrementer un
     *         compteur qui ne veut plus rien dire.
     */
    public void enregistrerSignatureSupplementaire() {
        if (nombreSignatures >= SIGNATURES_MAXIMUM) {
            throw new IllegalStateException(
                    "La piece jointe du processus " + idProcessus + " porte deja "
                            + nombreSignatures + " signatures. Le circuit n'en prevoit que "
                            + SIGNATURES_MAXIMUM + " (agent, chef d'unite, directeur reseau).");
        }
        this.nombreSignatures++;
        this.dateDerniereModification = LocalDateTime.now();
    }

    /**
     * Le document a ete <b>regenere</b> apres un retour : il repart de la seule
     * signature de l'agent.
     *
     * <p>Un etat retourne est corrige puis resoumis. Ses montants ont change : garder
     * le document d'origine ferait valider au chef d'unite un PDF qui ne correspond
     * plus au dossier. Le fichier est donc reconstruit depuis l'etat corrige et
     * remplace l'ancien ; les visas apposes avant le retour disparaissent avec lui, ce
     * qui est juste — ils portaient sur une version annulee.
     *
     * <p>Le compteur repart a un, et non a zero : le document neuf porte deja la
     * mention de l'agent qui vient de le resoumettre. C'est la meme regle qu'a la
     * naissance de la piece.
     *
     * <p>Le chemin est repris en parametre bien qu'il soit en pratique identique — la
     * convention de nommage (Sprint 4.2) ne depend que de l'unite, de la periode et de
     * l'identifiant du processus. Le passer explicitement evite que cette entite
     * suppose une invariance qui n'est pas la sienne a garantir.
     *
     * <p><b>A n'appeler qu'apres confirmation d'ecriture du nouveau fichier</b>, comme
     * {@link #enregistrerSignatureSupplementaire()} : le compteur constate une
     * ecriture, il ne l'anticipe pas (doctrine Sprint 4.2).
     */
    public void regenererApresRetour(String cheminFichier) {
        if (cheminFichier == null || cheminFichier.isBlank()) {
            throw new IllegalArgumentException(
                    "Aucun chemin de fichier pour la piece jointe regeneree du processus "
                            + idProcessus + ".");
        }
        this.cheminFichier = cheminFichier;
        this.nombreSignatures = 1;
        this.dateDerniereModification = LocalDateTime.now();
    }

    // --- Lecture ---------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public Long getIdProcessus() {
        return idProcessus;
    }

    public String getCheminFichier() {
        return cheminFichier;
    }

    public String getTypeMime() {
        return typeMime;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public LocalDateTime getDateDerniereModification() {
        return dateDerniereModification;
    }

    public int getNombreSignatures() {
        return nombreSignatures;
    }

}
