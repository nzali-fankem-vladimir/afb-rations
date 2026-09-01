package cm.afrilandfirstbank.rations.workflow.infrastructure.stockage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import cm.afrilandfirstbank.rations.workflow.application.DocumentEcrit;
import cm.afrilandfirstbank.rations.workflow.application.StockageDocuments;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.DocumentNonProduitException;

/**
 * Stockage des pieces jointes sur un systeme de fichiers, avec confirmation
 * d'ecriture.
 *
 * <h2>Les quatre etapes d'une ecriture, et pourquoi aucune n'est superflue</h2>
 *
 * <ol>
 *   <li><b>Ecrire dans un fichier temporaire.</b> Le chemin definitif ne doit
 *       jamais montrer un contenu incomplet, meme une fraction de seconde. En cas
 *       d'enrichissement (sous-sprints 4.3 et 4.4), ecrire en place laisserait, sur
 *       un echec a mi-parcours, un PDF <b>a moitie ecrit</b> : la perte des
 *       signatures precedentes, precisement ce que le guide interdit.</li>
 *   <li><b>{@code flush} puis {@code FileChannel.force(true)}.</b> Un
 *       {@code Files.write} rend la main quand le systeme d'exploitation a accepte
 *       les octets, pas quand le disque les a. Sans {@code fsync}, « ecriture
 *       reussie » veut dire « ecriture promise ».</li>
 *   <li><b>Verifier la taille sur disque.</b> On compare ce que le systeme de
 *       fichiers declare a ce qu'on a demande d'ecrire. Un disque plein peut
 *       accepter une ecriture partielle sans lever d'exception a chaque octet.</li>
 *   <li><b>Renommage atomique.</b> Le fichier apparait a son chemin definitif d'un
 *       seul coup, complet. C'est l'ancien fichier ou le nouveau, jamais un
 *       fichier mutile.</li>
 * </ol>
 *
 * <p>Ce n'est qu'apres ces quatre etapes qu'un {@link DocumentEcrit} est rendu, et
 * donc qu'il devient legitime d'incrementer
 * {@code piece_jointe.nombre_signatures} (decision Sprint 4.2).
 *
 * <h2>Limite connue, qui n'est pas masquee</h2>
 *
 * <p>Java n'expose aucun moyen de forcer sur disque l'<b>entree de repertoire</b>
 * apres le renommage. Le <i>contenu</i> du fichier est bien synchronise ; la
 * durabilite du renommage lui-meme, en cas de coupure d'alimentation dans la
 * seconde qui suit, depend du systeme de fichiers. Le risque residuel est un
 * fichier absent alors que la base le reference — l'inverse de ce que le dispositif
 * ecarte, et une panne franche plutot qu'une donnee fausse.
 *
 * <h2>Confinement a la racine</h2>
 *
 * <p>Tout chemin est normalise puis verifie comme appartenant a la racine
 * configuree. Les chemins sont pourtant construits par
 * {@code NommageDocument} et non recus d'un client : c'est une ceinture, pas une
 * reponse a une menace identifiee. Sur un module bancaire, une ecriture de fichier
 * qui ne verifie pas ou elle atterrit est une ligne qu'on ne veut pas avoir a
 * defendre.
 */
@Component
public class StockageDocumentsFichier implements StockageDocuments {

    private static final Logger journal = LoggerFactory.getLogger(StockageDocumentsFichier.class);

    private static final String SUFFIXE_TEMPORAIRE = ".partiel";

    private final Path racine;

    public StockageDocumentsFichier(
            @Value("${app.pieces-jointes.repertoire}") String repertoire) {
        this.racine = Path.of(repertoire).toAbsolutePath().normalize();
    }

    @Override
    public DocumentEcrit ecrireNouveau(String cheminRelatif, byte[] contenu) {
        return ecrire(cheminRelatif, contenu, false);
    }

    @Override
    public DocumentEcrit remplacer(String cheminRelatif, byte[] contenu) {
        return ecrire(cheminRelatif, contenu, true);
    }

    @Override
    public byte[] lire(String cheminRelatif) {
        Path destination = resoudre(cheminRelatif);
        try {
            return Files.readAllBytes(destination);
        } catch (IOException echec) {
            throw new DocumentNonProduitException(
                    "Le document " + cheminRelatif + " n'a pas pu etre relu ("
                            + echec.getMessage() + ").", echec);
        }
    }

    @Override
    public boolean existe(String cheminRelatif) {
        return Files.exists(resoudre(cheminRelatif));
    }

    // --- Ecriture confirmee -----------------------------------------------------

    private DocumentEcrit ecrire(String cheminRelatif, byte[] contenu, boolean remplacementAttendu) {
        if (contenu == null || contenu.length == 0) {
            // Un document vide n'est pas un document. L'ecrire produirait un
            // fichier que la base declarerait signe.
            throw new DocumentNonProduitException(
                    "Refus d'ecrire un document vide a " + cheminRelatif + ".");
        }

        Path destination = resoudre(cheminRelatif);
        Path temporaire = destination.resolveSibling(
                destination.getFileName() + SUFFIXE_TEMPORAIRE);

        try {
            Files.createDirectories(destination.getParent());

            // 1 et 2. Ecrire, puis forcer reellement sur disque.
            //
            // UN SEUL descripteur, volontairement. Ouvrir en parallele un flux
            // pour ecrire et un canal pour synchroniser laisserait le succes du
            // fsync dependre de l'ordre de fermeture des deux, et Windows refuse
            // parfois le second acces en ecriture. Le canal fait les deux.
            //
            // La boucle n'est pas une precaution theorique : write(ByteBuffer) est
            // autorise a n'ecrire qu'une partie du tampon.
            try (FileChannel canal = FileChannel.open(temporaire,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                ByteBuffer tampon = ByteBuffer.wrap(contenu);
                while (tampon.hasRemaining()) {
                    canal.write(tampon);
                }
                canal.force(true);
            }

            // 3. Verifier ce que le systeme de fichiers declare reellement detenir.
            long tailleSurDisque = Files.size(temporaire);
            if (tailleSurDisque != contenu.length) {
                Files.deleteIfExists(temporaire);
                throw new DocumentNonProduitException(
                        "Ecriture incomplete de " + cheminRelatif + " : " + tailleSurDisque
                                + " octets sur disque pour " + contenu.length
                                + " attendus. Le fichier partiel a ete supprime.");
            }

            // 4. Rendre visible d'un seul coup.
            deplacerAtomiquement(temporaire, destination, remplacementAttendu);

            return new DocumentEcrit(cheminRelatif, tailleSurDisque, LocalDateTime.now());

        } catch (DocumentNonProduitException refus) {
            throw refus;

        } catch (FileAlreadyExistsException collision) {
            nettoyer(temporaire);
            throw new DocumentNonProduitException(
                    "Un document occupe deja le chemin " + cheminRelatif
                            + ". Il n'a pas ete ecrase : une piece jointe existante peut porter "
                            + "des signatures deja apposees.", collision);

        } catch (IOException echec) {
            nettoyer(temporaire);
            journal.error("Ecriture du document {} en echec sous la racine {}",
                    cheminRelatif, racine, echec);
            throw new DocumentNonProduitException(
                    "Le document " + cheminRelatif + " n'a pas pu etre ecrit ("
                            + echec.getMessage() + "). Aucune soumission n'est enregistree.",
                    echec);
        }
    }

    /**
     * Renomme le temporaire vers sa destination.
     *
     * <p><b>{@code REPLACE_EXISTING} n'est pose que sur un remplacement voulu.</b>
     * A la creation, une destination deja occupee doit faire echouer l'operation :
     * c'est ce qui empeche une seconde soumission concurrente d'ecraser le PDF
     * signe de la premiere avant meme que la contrainte {@code id_processus UNIQUE}
     * ne s'exprime.
     *
     * <p>Le repli sans {@code ATOMIC_MOVE} n'est pas un renoncement de confort : la
     * garantie d'atomicite depend du systeme de fichiers, et certains montages
     * reseau ne la portent pas. On journalise alors l'ecart plutot que de faire
     * echouer une soumission legitime, mais on ne le fait pas en silence.
     */
    private void deplacerAtomiquement(Path temporaire, Path destination, boolean remplacementAttendu)
            throws IOException {

        if (!remplacementAttendu && Files.exists(destination)) {
            throw new FileAlreadyExistsException(destination.toString());
        }

        try {
            if (remplacementAttendu) {
                Files.move(temporaire, destination,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(temporaire, destination, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (AtomicMoveNotSupportedException nonAtomique) {
            journal.warn("Le systeme de fichiers ne supporte pas le renommage atomique sous {} : "
                    + "repli sur un deplacement simple pour {}", racine, destination, nonAtomique);
            if (remplacementAttendu) {
                Files.move(temporaire, destination, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(temporaire, destination);
            }
        }
    }

    private void nettoyer(Path temporaire) {
        try {
            Files.deleteIfExists(temporaire);
        } catch (IOException echec) {
            // Un temporaire qui subsiste est un desagrement d'exploitation, pas une
            // donnee fausse : il ne doit surtout pas masquer la panne d'origine.
            journal.warn("Fichier temporaire non supprime : {}", temporaire, echec);
        }
    }

    /**
     * Resout un chemin relatif sous la racine, et refuse tout ce qui en sortirait.
     */
    private Path resoudre(String cheminRelatif) {
        if (cheminRelatif == null || cheminRelatif.isBlank()) {
            throw new DocumentNonProduitException("Aucun chemin de document fourni.");
        }
        Path resolu = racine.resolve(cheminRelatif).normalize();
        if (!resolu.startsWith(racine)) {
            throw new DocumentNonProduitException(
                    "Chemin de document hors du repertoire de stockage : " + cheminRelatif);
        }
        return resolu;
    }

}
