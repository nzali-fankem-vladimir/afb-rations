package cm.afrilandfirstbank.rations.workflow.application;

import java.time.LocalDateTime;

/**
 * Preuve qu'un document a ete ecrit et confirme sur le stockage.
 *
 * <p><b>Ce type est une preuve, pas un compte rendu.</b> Il n'existe que rendu par
 * {@link StockageDocuments}, apres {@code fsync}, verification de taille et
 * renommage atomique. On ne peut donc pas en obtenir un sans que l'ecriture ait
 * reellement eu lieu.
 *
 * <p>C'est une garantie de discipline autant que de type : le service de
 * soumission n'incremente {@code piece_jointe.nombre_signatures} qu'a partir du
 * chemin porte ici. Ecrire ce compteur depuis un chemin simplement <i>calcule</i>
 * par {@link NommageDocument}, sans passer par le stockage, reste possible au
 * compilateur mais devient visiblement faux a la relecture.
 *
 * @param cheminRelatif chemin relatif a la racine configuree, tel qu'il sera
 *        enregistre dans {@code piece_jointe.chemin_fichier}
 * @param octetsEcrits taille constatee sur disque apres synchronisation, et non
 *        la taille du tableau qu'on a demande d'ecrire. La distinction est le
 *        controle lui-meme : un disque plein peut accepter une ecriture
 *        partielle
 * @param horodatage instant de la confirmation
 */
public record DocumentEcrit(String cheminRelatif, long octetsEcrits, LocalDateTime horodatage) {
}
