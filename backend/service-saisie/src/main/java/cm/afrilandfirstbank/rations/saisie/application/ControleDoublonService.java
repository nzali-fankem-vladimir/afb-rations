package cm.afrilandfirstbank.rations.saisie.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.saisie.infrastructure.LignePrestationRepository;

/**
 * Refuse qu'un meme beneficiaire figure deux fois sur une meme journee pour la
 * meme nature et la meme session — <b>RG-04</b>, US-04, CT-09.
 *
 * <p>Premier rempart du module contre le double paiement : deux lignes
 * identiques sur une fiche, c'est deux fois le meme montant consolide, valide,
 * puis transmis a la comptabilite.
 *
 * <h2>Le controle porte sur la combinaison COMPLETE</h2>
 *
 * <p>Quatre elements, pas un de moins : <b>fiche journaliere × beneficiaire ×
 * nature × session</b>. Un controle sur le seul beneficiaire serait plus simple
 * et faux : un agent de la garde peut legitimement recevoir, le meme jour, une
 * ration ET un transport, en session de jour ET en session de soir. Le refuser
 * bloquerait des saisies parfaitement regulieres, et l'agent contournerait le
 * blocage d'une facon ou d'une autre.
 *
 * <p>La journee n'est pas un parametre de ce service : elle est <b>portee par la
 * fiche</b>. Une fiche vaut pour un jour et un seul (RG-05, unicite
 * {@code (id_processus, date_jour)}), donc raisonner par identifiant de fiche
 * revient a raisonner par journee, sans avoir a faire circuler une date qu'il
 * faudrait garder coherente avec elle.
 *
 * <h2>Portee : RG-04 aujourd'hui, RG-15 au Sprint 6bis</h2>
 *
 * <p><b>RG-04 s'arrete a la fiche du jour.</b> RG-15 etendra le controle a tous
 * les etats de la meme unite et de la meme periode — pour empecher qu'un etat
 * COMPLEMENTAIRE ne reproduise une ligne deja payee dans l'etat NORMAL. Ce
 * sous-sprint <b>n'implemente pas</b> RG-15.
 *
 * <p>Il est en revanche ecrit pour l'accueillir sans reecriture, par une seule
 * disposition : <b>ce service expose un verdict, pas une requete</b>. Les
 * appelants ({@code CreationLigneService} aujourd'hui, la modification de ligne
 * au Sprint 3.3) demandent « cette ligne est-elle un doublon ? » et n'ont aucune
 * idee de la facon dont la reponse est obtenue. Le jour ou RG-15 arrive :
 *
 * <ul>
 *   <li>une seconde methode {@code estDoublonSurLaPeriode(...)} rejoint celle-ci,
 *       ou {@link #estDoublonSurLaJournee} enchaine les deux verifications ;</li>
 *   <li>elle s'appuiera sur les colonnes {@code code_unite}, {@code mois_paiement}
 *       et {@code annee_paiement} <b>recopiees sur {@code fiche_journaliere}</b>
 *       (decision Sprint 3.1, {@code docs/rattachement-processus.md} §5) : sans
 *       cette recopie, RG-15 exigerait un appel au service Workflow pour chaque
 *       ligne saisie ;</li>
 *   <li>aucun appelant ne change, parce qu'aucun appelant ne sait ce qu'il y a
 *       derriere le verdict.</li>
 * </ul>
 *
 * <p>Ce qui rend l'extension possible, ce n'est donc pas du code ecrit d'avance
 * — il n'y en a aucun ici pour RG-15 — mais le refus de laisser fuir la requete
 * chez l'appelant. Si {@code CreationLigneService} interrogeait lui-meme le
 * repository, RG-15 devrait etre ajoutee dans chaque appelant, et l'oubli d'un
 * seul suffirait a laisser passer un double paiement.
 */
@Service
public class ControleDoublonService {

    private final LignePrestationRepository lignePrestationRepository;

    public ControleDoublonService(LignePrestationRepository lignePrestationRepository) {
        this.lignePrestationRepository = lignePrestationRepository;
    }

    /**
     * Vrai si la fiche porte deja une ligne pour ce beneficiaire, cette nature et
     * cette session (RG-04).
     *
     * <p>Le controle est fait <b>avant</b> tout appel reseau : refuser d'abord ce
     * qui se refuse localement evite de solliciter le service Grilles pour une
     * ligne qui sera de toute facon rejetee.
     *
     * <p><b>Ce controle applicatif ne remplace pas une contrainte de base.</b> Il
     * laisse une fenetre entre la lecture et l'ecriture : deux requetes
     * concurrentes portant la meme combinaison peuvent le franchir toutes les
     * deux. Le cas est peu probable — un agent saisit ses lignes une par une —
     * mais il n'est pas impossible (double clic, deux onglets ouverts). Le
     * verrou definitif est un index unique sur
     * {@code (id_fiche_journaliere, id_beneficiaire, nature, session)} :
     * <b>point ouvert du Sprint 3.3</b>, ou l'endpoint devra de toute facon
     * traduire une violation de contrainte en {@code 409 DOUBLON_LIGNE}.
     *
     * @param idFicheJournaliere fiche du jour concernee, qui porte la journee
     * @param idBeneficiaire beneficiaire deja resolu (jamais son nom : l'identite
     *        se fait par le compte courant, decision Sprint 3.1)
     * @param nature RATION ou TRANSPORT (RG-01)
     * @param session JOUR ou SOIR (RG-02)
     */
    @Transactional(readOnly = true)
    public boolean estDoublonSurLaJournee(Long idFicheJournaliere, Long idBeneficiaire,
                                          NatureEnum nature, SessionEnum session) {
        return lignePrestationRepository
                .existsByIdFicheJournaliereAndIdBeneficiaireAndNatureAndSession(
                        idFicheJournaliere, idBeneficiaire, nature, session);
    }

}
