package cm.afrilandfirstbank.rations.workflow.domaine;

/**
 * Vocabulaire ferme des manques que le controle de completude peut constater
 * avant une soumission (US-07, CT-13).
 *
 * <p>Quatre valeurs, une par controle retenu. Le cahier des charges dit que « le
 * systeme verifie la completude des informations » sans preciser lesquelles :
 * cette liste est le resultat de l'arbitrage du Sprint 4.2, et
 * {@code docs/controles-completude.md} porte le detail de ce qui a ete retenu,
 * de ce qui a ete ecarte, et pourquoi.
 *
 * <h2>Pourquoi une enumeration plutot que des chaines libres</h2>
 *
 * <p>Ces codes sortent du service : ils accompagnent le refus {@code 422} pour
 * que l'interface puisse afficher les manques un a un, et non un paragraphe a
 * decouper (decision Sprint 4.2 sur le format du refus). Une enumeration ferme
 * le vocabulaire et rend le contrat verifiable : un test confronte cette liste
 * aux quatre controles arbitres, et l'ajout d'un cinquieme controle devient un
 * geste visible plutot qu'une chaine glissee au fil de l'eau.
 *
 * <h2>Ce que ces codes ne sont pas</h2>
 *
 * <p>Ce ne sont pas des codes d'erreur HTTP au sens de CLAUDE.md section 11. Le
 * refus porte un seul code d'erreur, {@code ETAT_INCOMPLET} ({@code 422}) ;
 * ceux-ci qualifient les elements de la liste qui l'accompagne.
 */
public enum CodeManqueEnum {

    /**
     * Aucune ligne de prestation a soumettre.
     *
     * <p>Point C-03 du Sprint 3.4, inscrit comme du a ce sous-sprint par
     * {@code docs/appel-consolidation.md} section 4 : « c'est a Workflow de
     * refuser la soumission d'un etat vide, a partir de {@code nombreLignes == 0}.
     * C'est sa regle, pas celle de Saisie. » Sans ce controle, un dossier a
     * 0 FCFA traverserait tout le circuit de validation jusqu'a la comptabilite.
     */
    ETAT_VIDE,

    /**
     * Une ou plusieurs lignes sont rattachees a une journee qui ne tombe pas dans
     * le mois du processus.
     *
     * <p>Controle <b>delegue ici par le service Saisie</b> : le DTO
     * {@code OuvertureFicheRequest} (Sprint 3.3) dit explicitement que « savoir
     * si la journee tombe dans le mois du processus, c'est une question qui
     * appartient au processus, donc au service Workflow ». Si Workflow ne le prend
     * pas, il n'est implemente nulle part.
     *
     * <p>L'enjeu depasse l'erreur de periode : RG-15 (Sprint 6bis) verifie
     * l'unicite inter-etats <i>a unite et periode egales</i>. Une ligne de mars
     * glissee dans l'etat de septembre echappe donc au controle d'unicite de mars,
     * et la meme journee devient payable deux fois.
     *
     * <p><b>Porte sur les lignes, jamais sur les journees.</b> Une journee ouverte
     * hors periode mais vide ne bloque pas : elle ne porte aucun montant, et il
     * n'existe aucun {@code DELETE /saisie/fiches/{id}} au contrat d'API — bloquer
     * dessus enfermerait l'agent sans recours. Les lignes, elles, se suppriment.
     */
    LIGNE_HORS_PERIODE,

    /**
     * Une ou plusieurs lignes ne portent aucun montant applicable.
     *
     * <p>Impossible aujourd'hui par la voie applicative :
     * {@code ligne_prestation.montant_applique} est {@code INTEGER NOT NULL}, et
     * le service Saisie refuse deja une ligne dont le service Grilles n'a pas rendu
     * de montant (refus conservateur, Sprint 2.4).
     *
     * <p><b>Ce n'est pas pour autant du code mort.</b> Cette garantie vit dans le
     * schema d'un <i>autre</i> service, avec lequel Workflow n'a aucun lien de
     * compilation. {@link cm.afrilandfirstbank.rations.workflow.application.EtatConsolide}
     * est un <i>tolerant reader</i> aux champs boites : un champ absent se lit
     * {@code null} et doit faire refuser (decision Sprint 4.1 section 8). Sans ce
     * controle, une ligne sans montant traverserait la soumission en silence et
     * partirait telle quelle vers la comptabilite.
     */
    LIGNE_SANS_MONTANT,

    /**
     * Un ou plusieurs beneficiaires n'ont aucun numero de compte courant.
     *
     * <p>Meme statut que {@link #LIGNE_SANS_MONTANT} : garantie tenue par le schema
     * du service Saisie ({@code num_compte_courant VARCHAR(20) NOT NULL},
     * {@code @NotBlank} a l'entree), donc hors de portee de Workflow.
     *
     * <p>Le numero de compte courant est la donnee qui commande <b>qui est
     * paye</b> : c'est elle qui porte la ligne de credit dans la charge du topic
     * {@code rations.etat.valide} (contrat section 7.1). Une ligne sans compte est
     * impayable.
     */
    BENEFICIAIRE_SANS_COMPTE

}
