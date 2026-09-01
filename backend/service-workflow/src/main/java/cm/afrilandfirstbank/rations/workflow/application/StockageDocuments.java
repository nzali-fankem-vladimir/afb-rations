package cm.afrilandfirstbank.rations.workflow.application;

/**
 * Port d'ecriture et de relecture des documents sur le stockage de fichiers.
 *
 * <h2>Pourquoi ce port existe, et ce qu'il garantit</h2>
 *
 * <p><b>Il n'est pas la pour abstraire un systeme de fichiers</b>, mais pour tenir
 * une garantie precise : quand une methode d'ecriture <i>rend</i> un
 * {@link DocumentEcrit}, le contenu est reellement sur le disque, force par un
 * {@code fsync}, verifie en taille, et rendu visible par un renommage atomique.
 * Tant qu'elle n'a pas rendu, rien n'est acquis.
 *
 * <p>C'est cette garantie qui autorise a incrementer
 * {@code piece_jointe.nombre_signatures} (migration V3, Sprint 4.2). Ce compteur
 * ne vaut que s'il constate une ecriture ; l'incrementer sur une simple intention
 * d'ecrire le rendrait equivalent a un comptage de {@code etape_workflow}, donc
 * inutile.
 *
 * <h2>Ordre impose aux appelants</h2>
 *
 * <p>L'ecriture a lieu <b>hors transaction et avant elle</b>. Si elle echoue, la
 * transaction de soumission ne s'ouvre pas : ni etape, ni piece jointe, ni montant,
 * ni changement de statut. L'asymetrie inverse est assumee — une transaction en
 * echec apres une ecriture reussie laisse un <b>fichier orphelin</b>, ce qui est
 * preferable a un compteur affirmant une signature absente du document.
 */
public interface StockageDocuments {

    /**
     * Ecrit un document qui n'existe pas encore.
     *
     * <p><b>Refuse d'ecraser.</b> Si un fichier occupe deja ce chemin, l'ecriture
     * echoue au lieu de le remplacer. Sans ce refus, deux soumissions concurrentes
     * sur un meme processus verraient la seconde ecraser le PDF signe de la
     * premiere <i>avant</i> que la contrainte d'unicite de {@code piece_jointe} ne
     * s'exprime.
     *
     * @param cheminRelatif chemin rendu par {@link NommageDocument}, relatif a la
     *        racine configuree
     * @param contenu le document complet
     * @return la preuve d'ecriture, une fois le contenu force sur disque
     * @throws cm.afrilandfirstbank.rations.workflow.domaine.exception.DocumentNonProduitException
     *         si l'ecriture, la synchronisation ou la verification echoue
     */
    DocumentEcrit ecrireNouveau(String cheminRelatif, byte[] contenu);

    /**
     * Remplace un document existant par sa version enrichie (sous-sprints 4.3 et
     * 4.4).
     *
     * <p>Le remplacement passe lui aussi par un fichier temporaire puis un
     * renommage atomique. Ecrire en place laisserait, en cas d'echec a mi-parcours,
     * un PDF <b>a moitie ecrit</b> : c'est-a-dire la perte des signatures
     * precedentes, exactement ce que le point de vigilance section 10 du guide
     * interdit. Avec le renommage, c'est l'ancien fichier ou le nouveau, jamais un
     * fichier mutile.
     */
    DocumentEcrit remplacer(String cheminRelatif, byte[] contenu);

    /**
     * Relit un document existant, pour l'enrichir.
     *
     * @throws cm.afrilandfirstbank.rations.workflow.domaine.exception.DocumentNonProduitException
     *         si le fichier est absent ou illisible
     */
    byte[] lire(String cheminRelatif);

    /** Vrai si un fichier occupe deja ce chemin. */
    boolean existe(String cheminRelatif);

}
