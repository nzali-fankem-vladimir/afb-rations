package cm.afrilandfirstbank.rations.workflow.application;

/**
 * Ce qu'une signature laisse derriere elle, une fois le fichier confirme sur
 * disque.
 *
 * <p><b>Ce type n'existe que rendu par {@link SignatureService}</b>, apres que le
 * stockage a confirme l'ecriture (fsync, verification de taille, renommage
 * atomique). L'obtenir, c'est donc detenir la preuve que la signature est
 * reellement dans le fichier — et c'est a cette condition, et a cette condition
 * seulement, que {@code piece_jointe.nombre_signatures} peut etre incremente
 * (migration V3).
 *
 * @param document la preuve d'ecriture, portant le chemin relatif a enregistrer
 * @param mention la mention telle qu'elle est imprimee sur le document
 * @param empreinte empreinte SHA-256 du fichier <b>tel qu'il est apres cette
 *        signature</b>, destinee a {@code etape_workflow.signature_numerique}.
 *        Voir {@link SignatureService} pour ce qu'elle prouve et ce qu'elle ne
 *        prouve pas
 */
public record ResultatSignature(DocumentEcrit document, MentionSignature mention, String empreinte) {
}
