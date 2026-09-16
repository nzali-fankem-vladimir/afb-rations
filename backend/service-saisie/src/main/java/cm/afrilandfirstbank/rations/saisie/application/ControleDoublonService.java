package cm.afrilandfirstbank.rations.saisie.application;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.saisie.domaine.FicheJournaliere;
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
 * <h2>Portee : RG-04 sur la journee, RG-15 sur la periode</h2>
 *
 * <p><b>RG-04 s'arrete a la fiche du jour</b> ({@link #estDoublonSurLaJournee}).
 * <b>RG-15 couvre tous les etats de l'unite qui portent cette journee</b>
 * ({@link #etatDeLaPeriodePortantDeja}, Sprint 6bis.2) — c'est ce qui empeche un
 * etat COMPLEMENTAIRE de repayer une prestation deja servie par l'etat NORMAL.
 * Les deux sont exactement complementaires : la premiere regarde a l'interieur
 * de l'etat courant, la seconde regarde partout ailleurs.
 *
 * <p>L'extension annoncee au Sprint 3.2 s'est faite <b>sans reecrire un seul
 * appelant</b>, et la disposition qui l'a permis merite d'etre nommee : <b>ce
 * service expose un verdict, pas une requete</b>. {@code CreationLigneService} et
 * {@code LigneService} demandent « cette ligne est-elle un doublon ? » et n'ont
 * aucune idee de la facon dont la reponse est obtenue. Si l'un d'eux avait
 * interroge le repository lui-meme, RG-15 aurait du etre ajoutee dans chacun, et
 * l'oubli d'un seul aurait suffi a laisser passer un double paiement.
 *
 * <p>La prevision du Sprint 3.2 s'est verifiee sur le fond comme sur le moyen :
 * RG-15 s'appuie bien sur les colonnes recopiees sur {@code fiche_journaliere}
 * (decision Sprint 3.1), et n'a donc besoin d'<b>aucun appel au service
 * Workflow</b> — contrairement a ce que le guide 6bis.2 prevoyait. Le detail du
 * raisonnement est porte par {@link #etatDeLaPeriodePortantDeja}.
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

    /**
     * Même contrôle, pour une <b>modification</b> ({@code PUT /saisie/lignes/{id}},
     * Sprint 3.3) : la ligne révisée est exclue de la comparaison.
     *
     * <p>Sans cette exclusion, une modification qui laisse la nature et la
     * session inchangées se trouverait doublon d'elle-même — la ligne existe déjà
     * avec cette combinaison, c'est elle. Le guide 3.3 est explicite : une
     * modification de nature ou de session « traite la modification comme une
     * création », donc rejoue RG-04 ; exclure la ligne visée est ce qui rend cette
     * règle applicable sans faux positif systématique.
     *
     * @param idLigneRevisee la ligne en cours de modification, absente de la
     *        comparaison
     */
    @Transactional(readOnly = true)
    public boolean estDoublonSurLaJourneeHorsLigne(Long idFicheJournaliere, Long idBeneficiaire,
                                                    NatureEnum nature, SessionEnum session,
                                                    Long idLigneRevisee) {
        return lignePrestationRepository
                .existsByIdFicheJournaliereAndIdBeneficiaireAndNatureAndSessionAndIdNot(
                        idFicheJournaliere, idBeneficiaire, nature, session, idLigneRevisee);
    }
    /**
     * <b>RG-15</b> — l'etat de la periode qui porte deja cette prestation, s'il
     * en existe un. {@link Optional#empty()} quand la ligne est libre.
     *
     * <p>Extension de {@link #estDoublonSurLaJournee} a <b>tous les etats</b> de
     * l'unite qui couvrent la journee, et non plus a la seule fiche courante.
     * C'est le rempart contre le double paiement en regularisation : sans lui, un
     * etat COMPLEMENTAIRE pourrait ressaisir une prestation deja payee dans son
     * etat d'origine.
     *
     * <h2>Aucun appel au service Workflow</h2>
     *
     * <p>Le guide 6bis.2 prevoyait ici un appel inter-services par ligne saisie,
     * pour demander au Workflow « quels sont les etats de cette unite sur cette
     * periode ? ». <b>Il est devenu inutile</b>, et la piste etait deja tracee
     * par le Sprint 3.1 : {@code code_unite} est <b>recopie et fige</b> sur la
     * fiche a son ouverture ({@code docs/rattachement-processus.md} §5).
     *
     * <p>Ce qui manquait pour s'en passer, c'est la garantie que « meme journee »
     * implique « meme periode ». La <b>contrainte d'exclusion</b> de la Maille 1
     * l'apporte : deux etats NORMAL d'une unite ne peuvent plus se chevaucher, et
     * un COMPLEMENTAIRE recopie les bornes de son origine. Le detour par la
     * periode n'apprend donc plus rien que la journee ne dise deja.
     *
     * <p>Gain concret : un appel reseau de moins <b>par ligne saisie</b>, sur un
     * chemin qui en comptait deja trois (Grilles, Workflow, Identite) pour une
     * cible de 3 secondes (Sprint 3.2). Le controle redevient une requete locale
     * indexee.
     *
     * <h2>Ce controle n'a pas de filet en base, et c'est assume</h2>
     *
     * <p>RG-04 a son index unique depuis la migration V4 : sa combinaison vit
     * dans une seule table. RG-15 porte sur une jointure fiche x ligne, qu'aucun
     * index ne peut garder. Il reste donc une fenetre entre la lecture et
     * l'ecriture — deux agents de la meme unite saisissant la meme prestation au
     * meme instant, dans deux etats differents. Cas nettement plus rare que le
     * double-clic que RG-04 rencontrait, et sans remede a cout raisonnable : le
     * fermer exigerait de verrouiller toutes les fiches de l'unite pour la
     * journee. Limite consignee au sous-sprint 6bis.2.
     *
     * <h2>Applique a tous les etats, pas aux seuls complementaires</h2>
     *
     * <p>Le service Saisie ne connait pas le type de l'etat — la fiche ne porte
     * pas {@code type_processus} — et le demander couterait exactement l'appel
     * reseau qu'on vient d'economiser. Ce n'est pas une concession : sur un etat
     * NORMAL en cours de saisie, le controle est un <b>no-op</b> demontrable. Un
     * complementaire exige une origine CLOTUREE (Sprint 6bis.1), et un second
     * NORMAL couvrant la meme journee est refuse par la base. Il n'existe donc
     * aucun autre etat a trouver, et la requete rend une liste vide.
     *
     * @param fiche la fiche de la ligne — elle porte a elle seule les trois
     *        reperes du controle : l'unite, la journee et l'etat courant. Les
     *        passer separement aurait permis de les fournir incoherents
     * @return l'identifiant du premier etat en conflit, ou vide
     */
    @Transactional(readOnly = true)
    public Optional<Long> etatDeLaPeriodePortantDeja(FicheJournaliere fiche, Long idBeneficiaire,
                                                     NatureEnum nature, SessionEnum session) {
        return lignePrestationRepository.etatsPortantDejaLaPrestation(
                        fiche.getCodeUnite(), fiche.getDateJour(), fiche.getIdProcessus(),
                        idBeneficiaire, nature, session)
                .stream()
                .findFirst();
    }

}
