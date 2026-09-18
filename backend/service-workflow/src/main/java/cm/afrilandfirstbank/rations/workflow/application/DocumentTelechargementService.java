package cm.afrilandfirstbank.rations.workflow.application;

import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.domaine.PieceJointe;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.PieceJointeIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.PieceJointeRepository;

/**
 * Téléchargement du document signé d'un état (rattrapage post-7F.6, demande n°8 du
 * retour de vérification visuelle du Sprint 7F.6). Point ouvert le
 * 16 septembre 2026, fermé ici : voir
 * {@code docs/points-en-attente.md} section « PDF signé », et
 * {@code docs/decisions/2026-09-18-endpoint-telechargement-document-signe.md}.
 *
 * <h2>Pourquoi une classe à part plutôt qu'une méthode de {@code ProcessusService}</h2>
 *
 * <p>Le geste est distinct : lire un fichier sur le stockage et publier un
 * événement d'audit, plutôt que muter le processus. Regrouper ce point de
 * lecture disque et {@code ProcessusService} aurait mélangé deux
 * responsabilités que rien ne rapproche au-delà du premier contrôle partagé.
 *
 * <h2>La portée d'accès n'est pas revérifiée ici, elle est réutilisée</h2>
 *
 * <p>{@link ProcessusService#consulter} fait déjà les deux vérifications
 * nécessaires — le processus existe, l'appelant a portée sur son unité — et
 * lève {@code ProcessusIntrouvableException} ou un refus d'habilitation le
 * cas échéant. Les rappeler ici serait un second appel réseau au service
 * Identité, pour la même question posée deux fois dans la même requête.
 *
 * <h2>Événement d'audit obligatoire, {@code idUtilisateur} nul mais l'auteur nommé</h2>
 *
 * <p>CLAUDE.md §9.2 trace « ce qui fait sortir un fichier du système, pas ce
 * qui affiche un dossier » : un document PDF téléchargé quitte le périmètre
 * applicatif exactement comme un export du service Reporting, et doit donc
 * être tracé de la même façon. {@code idUtilisateur} reste nul, même parti
 * que {@code TracabiliteRapportService.tracerExport} côté Reporting :
 * l'identifiant local exigerait un appel supplémentaire au service Identité
 * pour un geste de lecture. <b>Le login, lui, est repris en contexte du
 * delta</b> (retour utilisateur, rattrapage post-7F.6) — sans appel
 * supplémentaire non plus, {@link ProcessusService#consulter} l'a déjà obtenu
 * en vérifiant la portée d'accès, et {@link ResultatHabilitationUnite.AgentHabilite}
 * le portait déjà, simplement inutilisé jusqu'ici.
 */
@Service
public class DocumentTelechargementService {

    private static final String ACTION = "TELECHARGEMENT_DOCUMENT";
    private static final String ENTITE_CIBLE = "processus_mensuel";

    private final ProcessusService processusService;
    private final PieceJointeRepository pieceJointeRepository;
    private final StockageDocuments stockageDocuments;
    private final PublicateurAudit publicateurAudit;

    public DocumentTelechargementService(ProcessusService processusService,
            PieceJointeRepository pieceJointeRepository,
            StockageDocuments stockageDocuments,
            PublicateurAudit publicateurAudit) {
        this.processusService = processusService;
        this.pieceJointeRepository = pieceJointeRepository;
        this.stockageDocuments = stockageDocuments;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Relit le document signé d'un processus, portée d'accès vérifiée.
     *
     * @throws cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException
     *         aucun processus ne porte cet identifiant (404)
     * @throws PieceJointeIntrouvableException
     *         le processus existe mais n'a jamais été soumis, donc aucun
     *         document n'a été produit (404)
     * @throws cm.afrilandfirstbank.rations.workflow.domaine.exception.DocumentNonProduitException
     *         la base atteste un document que le stockage ne peut pas relire (500)
     */
    public DocumentTelecharge telecharger(Long idProcessus, String enteteAutorisation, String adresseIp) {
        ProcessusService.DetailProcessus detail = processusService.consulter(idProcessus, enteteAutorisation);
        ProcessusMensuel processus = detail.processus();

        PieceJointe pieceJointe = pieceJointeRepository.findByIdProcessus(idProcessus)
                .orElseThrow(() -> new PieceJointeIntrouvableException(idProcessus));

        byte[] contenu = stockageDocuments.lire(pieceJointe.getCheminFichier());
        String nomFichier = nomDeFichier(pieceJointe.getCheminFichier());

        publicateurAudit.publier(EvenementAudit.de(
                null,
                ACTION,
                ENTITE_CIBLE,
                idProcessus,
                adresseIp,
                DeltaAudit.nouveau()
                        // auteur/role : idUtilisateur reste nul (voir javadoc de classe),
                        // mais le login est deja en main -- aucun second appel au service
                        // Identite (retour utilisateur, rattrapage post-7F.6).
                        .contexte("auteur", detail.appelant().login())
                        .contexte("role", detail.appelant().role())
                        // codeUnite/dateDebut/dateFin : memes trois champs que les autres
                        // evenements portant sur processus_mensuel (soumission, validation,
                        // retour...), pour que le journal d'audit identifie TOUJOURS le
                        // dossier concerne, pas seulement son type (retour utilisateur,
                        // rattrapage post-7F.6). `consulter` les tenait deja en memoire :
                        // aucun appel reseau supplementaire.
                        .contexte("codeUnite", processus.getCodeUnite())
                        .contexte("dateDebut", processus.getDateDebut())
                        .contexte("dateFin", processus.getDateFin())
                        .contexte("nomFichier", nomFichier)
                        .contexte("tailleOctets", contenu.length)
                        .enJson()));

        return new DocumentTelecharge(contenu, nomFichier, pieceJointe.getTypeMime());
    }

    /** Le dernier segment du chemin relatif, pour l'en-tête Content-Disposition et l'audit. */
    private String nomDeFichier(String cheminRelatif) {
        int dernierSeparateur = Math.max(cheminRelatif.lastIndexOf('/'), cheminRelatif.lastIndexOf('\\'));
        return dernierSeparateur >= 0 ? cheminRelatif.substring(dernierSeparateur + 1) : cheminRelatif;
    }

    /** Le document relu, prêt à être rendu par le contrôleur. */
    public record DocumentTelecharge(byte[] contenu, String nomFichier, String typeMime) {
    }

}
