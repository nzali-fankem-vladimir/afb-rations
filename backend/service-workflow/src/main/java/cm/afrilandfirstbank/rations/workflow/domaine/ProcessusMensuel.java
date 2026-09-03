package cm.afrilandfirstbank.rations.workflow.domaine;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Processus mensuel : l'objet central du module. Il porte la periode, l'unite qui
 * supporte la charge, le statut d'avancement, le montant total consolide et
 * l'indicateur de transmission comptable (CLAUDE.md section 4).
 *
 * <h2>Strictement conforme au dictionnaire et a la table du Sprint 0.5</h2>
 *
 * <p>La table {@code processus_mensuel} existe depuis la migration V1 ; Hibernate
 * tourne en {@code ddl-auto: validate} et ne cree rien. Les colonnes sont donc
 * exactement celles du dictionnaire (CLAUDE.md section 4) plus la convention
 * transverse {@code date_creation} (Sprint 0.7) : ni {@code date_declenchement},
 * ni {@code date_cloture}, ni {@code id_createur}. Le « qui a declenche » et le
 * « quand » sont portes par le journal d'audit (etape de declenchement, Sprint
 * 4.1) et par les lignes {@code etape_workflow} des sous-sprints 4.2 et suivants.
 *
 * <h2>id_processus_origine — auto-reference, jamais une association JPA chargee</h2>
 *
 * <p>Renseigne uniquement pour un {@link TypeProcessusEnum#COMPLEMENTAIRE} : il
 * pointe vers l'etat clos jamais rouvert. La colonne porte une vraie cle
 * etrangere en base (meme table, meme base), mais on la modelise en identifiant
 * simple : rien dans ce sous-sprint ne remonte la chaine, et une
 * {@code @ManyToOne} ferait charger un second processus a chaque lecture sans
 * usage.
 *
 * <h2>Mutation du statut</h2>
 *
 * <p>Le seul mutateur, {@link #appliquerStatut(StatutEnum)}, est en visibilite
 * paquet : aucune couche au-dessus du domaine ne change le statut sans que
 * {@link TransitionProcessus} ait juge la transition legale au prealable — meme
 * discipline que {@code GrilleTarifaire} au Sprint 2.1.
 */
@Entity
@Table(name = "processus_mensuel")
public class ProcessusMensuel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mois_paiement", nullable = false)
    private Integer moisPaiement;

    @Column(name = "annee_paiement", nullable = false)
    private Integer anneePaiement;

    /**
     * Unite qui supporte la charge (ligne de <i>debit</i>). Format du referentiel
     * des codes guichets Afriland ({@code VARCHAR(5)}). A ne jamais confondre
     * avec {@code beneficiaires.code_agence}, l'agence de domiciliation du compte
     * credite (CLAUDE.md section 4).
     */
    @Column(name = "code_unite", nullable = false, length = 5)
    private String codeUnite;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_processus", nullable = false, length = 20)
    private TypeProcessusEnum typeProcessus;

    /**
     * Reference vers l'etat d'origine, non nulle uniquement pour un
     * {@link TypeProcessusEnum#COMPLEMENTAIRE}. Identifiant simple, pas
     * d'association JPA.
     */
    @Column(name = "id_processus_origine")
    private Long idProcessusOrigine;

    @Column(name = "motif_ouverture", length = 255)
    private String motifOuverture;

    /**
     * Montant total consolide, en FCFA entiers. Reste a {@code 0} tant que l'etat
     * n'a pas ete soumis : c'est la soumission (Sprint 4.2) qui y reporte le
     * total rendu par le service Saisie (RG-06, {@code docs/appel-consolidation.md}).
     */
    @Column(name = "montant_total", nullable = false)
    private int montantTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutEnum statut;

    /**
     * Vrai une fois l'etat valide publie sur {@code rations.etat.valide}. Verrou
     * de RG-13 : un etat n'est transmis qu'une fois. Positionne au Sprint 5.
     */
    @Column(name = "transmis_comptabilite", nullable = false)
    private boolean transmisComptabilite;

    /**
     * Suite donnee par la comptabilite (migration V4, Sprint 5.1). <b>Nul tant que
     * l'etat n'a pas ete transmis</b> : un etat en cours de saisie n'attend rien de
     * la comptabilite, et {@code EN_ATTENTE} signifierait le contraire.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_integration", length = 20)
    private StatutIntegrationEnum statutIntegration;

    /**
     * Reference produite par le module de comptabilisation, portee par l'accuse
     * (Sprint 5.2). Ce module ne la fabrique jamais : il ne produit aucune ecriture
     * comptable (CLAUDE.md section 8).
     */
    @Column(name = "reference_comptable", length = 50)
    private String referenceComptable;

    /** Horodatage du traitement comptable, declare par l'accuse (Sprint 5.2). */
    @Column(name = "date_traitement")
    private LocalDateTime dateTraitement;

    /**
     * Instant ou la transmission a ete <b>reservee</b>, juste avant la publication
     * (migration V5, Sprint 5.3). Avec un {@link #statutIntegration} nul, une
     * reservation ancienne signale une publication d'issue incertaine : c'est ce
     * qui distingue un etat normalement en transit d'un etat a lever a la main.
     */
    @Column(name = "date_reservation_transmission")
    private LocalDateTime dateReservationTransmission;

    /** Motif accompagnant un accuse de rejet (contrat d'API section 7.2). */
    @Column(name = "motif_integration", length = 255)
    private String motifIntegration;

    /** Horodatage technique d'audit (convention transverse, Sprint 0.7). */
    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    protected ProcessusMensuel() {
        // requis par JPA
    }

    /**
     * Declenche un processus <b>NORMAL</b> pour une unite et une periode donnees.
     *
     * <p><b>Visibilite paquet, deliberement.</b> La creation est la premiere des
     * neuf transitions d'ET01 ; elle passe donc par
     * {@link TransitionProcessus#declencher(Integer, Integer, String)}, comme les
     * huit autres passent par la machine a etats. Un constructeur public
     * laisserait un service applicatif faire naitre un processus sans que la
     * machine l'ait vu — l'invariant « toute transition passe par
     * {@link TransitionProcessus} » deviendrait une convention au lieu d'etre
     * verifie par le compilateur.
     *
     * <p>Statut initial {@link StatutEnum#EN_COURS_SAISIE} : l'agent va saisir ses
     * fiches journalieres. {@code montantTotal} a {@code 0},
     * {@code transmisComptabilite} a {@code false}, {@code idProcessusOrigine} et
     * {@code motifOuverture} nuls — un etat normal ne regularise rien.
     *
     * <p>Le Sprint 4.1 ne construit que des processus normaux : la creation d'un
     * {@link TypeProcessusEnum#COMPLEMENTAIRE} viendra au Sprint 6bis, avec ses
     * propres controles (drapeau {@code RATTRAPAGE_ACTIF}, delai de
     * regularisation, RG-15).
     *
     * @param moisPaiement mois du cycle, 1 a 12
     * @param anneePaiement annee du cycle
     * @param codeUnite unite qui supporte la charge, cinq chiffres
     */
    ProcessusMensuel(Integer moisPaiement, Integer anneePaiement, String codeUnite) {
        this.moisPaiement = moisPaiement;
        this.anneePaiement = anneePaiement;
        this.codeUnite = codeUnite;
        this.typeProcessus = TypeProcessusEnum.NORMAL;
        this.idProcessusOrigine = null;
        this.motifOuverture = null;
        this.montantTotal = 0;
        this.transmisComptabilite = false;
        this.statut = StatutEnum.EN_COURS_SAISIE;
    }

    @PrePersist
    void avantInsertion() {
        if (dateCreation == null) {
            dateCreation = LocalDateTime.now();
        }
    }

    /**
     * Applique un nouveau statut. <b>Visibilite paquet</b> : reserve a
     * {@link TransitionProcessus}, qui a d'abord verifie que la transition est
     * prevue par ET01. Aucun service applicatif n'appelle cette methode
     * directement.
     */
    void appliquerStatut(StatutEnum nouveauStatut) {
        this.statut = nouveauStatut;
    }

    /**
     * Reporte sur le processus le montant total rendu par le service Saisie, a la
     * soumission (Sprint 4.2).
     *
     * <p><b>Report, jamais calcul.</b> Le nom de la methode le dit : la valeur
     * vient telle quelle de {@code EtatConsolide.montantTotalFcfa}, qui est la
     * somme des sous-totaux journaliers, eux-memes sommes des lignes affichees
     * (RG-06 partagee, decision Sprint 3.4). Readditionner ici creerait un second
     * chemin de calcul, et donc une divergence possible entre le detail affiche a
     * l'agent et le montant qui commande l'aiguillage au seuil (RG-08).
     *
     * <h2>Pourquoi ce mutateur est public alors que appliquerStatut ne l'est pas</h2>
     *
     * <p>{@link #appliquerStatut(StatutEnum)} est ferme parce que le statut a une
     * machine a etats : toute transition doit passer par {@link TransitionProcessus},
     * et le compilateur le garantit. Le montant n'en a pas. Sa legalite tient a
     * des regles que le service de soumission verifie avant d'appeler : etat
     * complet, etat non vide, montant reellement obtenu du service Saisie. Faire
     * passer le montant par la machine a etats lui donnerait au contraire quelque
     * chose a arbitrer, ce que la decision 3 du Sprint 4.1 interdit explicitement.
     *
     * @param montantConsolide total en FCFA entiers, tel que rendu par le service
     *        Saisie
     * @throws IllegalArgumentException sur un montant negatif. Un etat ne peut pas
     *         couter moins que rien, et un negatif signalerait une lecture fausse
     *         plutot qu'un cas metier
     */
    public void reporterMontantTotal(int montantConsolide) {
        if (montantConsolide < 0) {
            throw new IllegalArgumentException(
                    "Montant total negatif (" + montantConsolide + ") pour le processus " + id
                            + " : refus plutot que report d'une valeur qui ne peut pas etre juste.");
        }
        this.montantTotal = montantConsolide;
    }

    /**
     * <b>Reserve</b> la transmission de cet etat : pose le verrou de RG-13 juste avant
     * la publication sur {@code rations.etat.valide} (Sprint 5.3).
     *
     * <h2>Pourquoi avant, et non apres</h2>
     *
     * <p>Le Sprint 5.1 posait le drapeau <i>apres</i> l'accuse du broker, et c'etait la
     * bonne prudence tant qu'il n'y avait pas de verrou : rien n'etait jamais repute
     * transmis sans l'etre. Mais un drapeau pose apres coup ne protege de rien pendant
     * la publication elle-meme — deux instances, ou deux demandes successives, y
     * passeraient toutes les deux et la comptabilite recevrait deux fois le meme etat,
     * donc deux jeux d'ecritures pour les memes beneficiaires.
     *
     * <p>La reservation renverse l'ordre et assume le risque inverse, <b>parce qu'il est
     * reparable et que l'autre ne l'est pas</b> : un etat repute transmis qui ne l'est
     * pas se voit — il porte une reservation ancienne sans statut d'integration — et se
     * reprend a la main ; un double paiement, lui, est deja parti.
     *
     * <h2>Ce que la reservation n'ecrit PAS</h2>
     *
     * <p>{@code statutIntegration} <b>reste nul</b>. Il ne passe a
     * {@link StatutIntegrationEnum#EN_ATTENTE} qu'a la confirmation, apres l'accuse du
     * broker : c'est ce qui permet de distinguer « publie, la comptabilite n'a pas encore
     * repondu » de « reserve, on ignore si l'evenement est parti ». Les ecrire ensemble
     * ferait disparaitre cette difference, qui est toute la detectabilite du dispositif.
     *
     * <p><b>Visibilite paquet</b>, comme {@link #appliquerStatut(StatutEnum)} : les trois
     * gestes du verrou passent par {@link VerrouTransmission}, qui tient leur ordre. Le
     * compilateur garantit qu'aucun service applicatif n'en saute un.
     *
     * @param instantReservation horodatage de la reservation, jamais nul : sans lui, une
     *        publication d'issue incertaine serait indiscernable d'un etat normalement
     *        en transit
     * @throws IllegalStateException si l'etat porte deja le drapeau. Dernier garde-fou,
     *         double du verrou de ligne pose par le repository : deux transmissions
     *         produiraient un second jeu d'ecritures pour les memes beneficiaires (RG-13)
     */
    void reserverTransmission(LocalDateTime instantReservation) {
        if (transmisComptabilite) {
            throw new IllegalStateException(
                    "L'etat " + id + " porte deja le drapeau de transmission comptable : une "
                            + "seconde transmission produirait un second jeu d'ecritures pour les "
                            + "memes beneficiaires (RG-13). Refus.");
        }
        if (instantReservation == null) {
            throw new IllegalArgumentException(
                    "Une reservation de transmission sans horodatage rendrait une publication "
                            + "d'issue incertaine indiscernable d'un etat en transit normal. Refus.");
        }
        this.transmisComptabilite = true;
        this.dateReservationTransmission = instantReservation;
    }

    /**
     * <b>Confirme</b> que l'evenement est sur le broker : ouvre l'attente de l'accuse
     * comptable (Sprint 5.3, ex-{@code constaterTransmissionComptable} du Sprint 5.1).
     *
     * <p>Appelee uniquement apres un accuse du broker. C'est elle qui fait passer l'etat
     * de « reserve » a « transmis, en attente de la comptabilite » — la difference que le
     * suivi (US-15) et la supervision lisent.
     *
     * <p><b>Sans effet sur un etat deja confirme</b> : le statut n'est pose que s'il est
     * nul. Une confirmation rejouee ne doit pas ecraser un accuse comptable deja recu, qui
     * serait alors ramene de {@code INTEGRE} a {@code EN_ATTENTE} — la regression que
     * {@link TransitionIntegration} interdit precisement.
     *
     * @throws IllegalStateException si l'etat n'a pas ete reserve. Confirmer sans avoir
     *         reserve reviendrait a poser le drapeau de RG-13 sans verrou
     */
    void confirmerTransmission() {
        if (!transmisComptabilite) {
            throw new IllegalStateException(
                    "L'etat " + id + " n'a pas ete reserve : sa transmission ne peut pas etre "
                            + "confirmee. La reservation precede toujours la publication (RG-13).");
        }
        if (statutIntegration == null) {
            this.statutIntegration = StatutIntegrationEnum.EN_ATTENTE;
        }
    }

    /**
     * <b>Libere</b> une reservation dont on a la <i>preuve</i> qu'aucun evenement n'est
     * parti : le drapeau et l'horodatage reviennent a leur etat d'avant, et une reprise
     * redevient possible (Sprint 5.3).
     *
     * <h2>La preuve, et rien d'autre</h2>
     *
     * <p>Liberer sur un echec <i>ambigu</i> — delai d'accuse depasse, reponse perdue —
     * rouvrirait la porte au double paiement : l'evenement a pu etre ecrit sur le topic
     * avant que l'accuse ne se perde, et la demande suivante en publierait un second.
     * Seuls les echecs anterieurs a tout envoi se liberent ; c'est l'appelant qui en
     * repond, avec un type qui distingue les deux cas.
     *
     * @throws IllegalStateException si un accuse comptable est deja inscrit. La
     *         comptabilite ayant repondu, l'evenement etait bien parti : liberer ferait
     *         republier un etat deja pris en charge
     */
    void libererTransmission() {
        if (statutIntegration != null) {
            throw new IllegalStateException(
                    "L'etat " + id + " porte le statut d'integration " + statutIntegration
                            + " : la comptabilite a repondu, donc l'evenement etait bien parti. "
                            + "Liberer la reservation ferait republier un etat deja pris en "
                            + "charge (RG-13). Refus.");
        }
        this.transmisComptabilite = false;
        this.dateReservationTransmission = null;
    }

    /**
     * Inscrit sur l'etat la suite que la comptabilite lui a donnee (Sprint 5.2, contrat
     * d'API section 7.2).
     *
     * <h2>Visibilite paquet, comme {@link #appliquerStatut(StatutEnum)}</h2>
     *
     * <p>Le statut d'integration a lui aussi ses transitions permises — {@code INTEGRE} et
     * {@code REJETE} sont definitifs, aucun accuse ne fait regresser un statut — et elles
     * sont tenues par {@link TransitionIntegration}. Fermer ce mutateur fait verifier par
     * le compilateur que rien ne les contourne : aucun service applicatif ne peut ecrire un
     * statut d'integration sans que la table des transitions l'ait juge legal.
     *
     * <p>C'est exactement la discipline du statut de circuit, et pour la meme raison : le
     * statut d'integration est ce que lit le suivi (US-15) pour dire si un etat a ete paye.
     *
     * <h2>Les quatre champs avancent ensemble</h2>
     *
     * <p>Ils viennent d'un seul et meme accuse et n'ont aucun sens separement : une
     * reference comptable sans son statut ne dit pas si l'etat a ete pris en charge ou
     * refuse. Les ecrire par quatre mutateurs distincts autoriserait des combinaisons que
     * la comptabilite n'a jamais envoyees.
     *
     * <p><b>Ecrire un {@code null} recu est deliberement possible</b> : un accuse
     * d'integration ne porte pas de motif, et le laisser subsister effacerait la trace du
     * rejet precedent tout en gardant sa justification — un etat integre avec un motif de
     * refus affiche a cote.
     *
     * @throws IllegalStateException si l'etat n'a jamais ete transmis. Dernier garde-fou,
     *         double de la contrainte {@code ck_processus_integration_apres_transmission} :
     *         la comptabilite ne peut pas avoir traite ce qu'elle n'a pas recu
     */
    void appliquerAccuseComptable(StatutIntegrationEnum statutRecu, String referenceComptable,
            LocalDateTime dateTraitement, String motifIntegration) {

        if (!transmisComptabilite) {
            throw new IllegalStateException(
                    "L'etat " + id + " n'a jamais ete transmis a la comptabilite : aucun statut "
                            + "d'integration ne peut y etre inscrit (RG-13, contrainte "
                            + "ck_processus_integration_apres_transmission).");
        }
        this.statutIntegration = statutRecu;
        this.referenceComptable = referenceComptable;
        this.dateTraitement = dateTraitement;
        this.motifIntegration = motifIntegration;
    }

    // --- Lecture ---------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public Integer getMoisPaiement() {
        return moisPaiement;
    }

    public Integer getAnneePaiement() {
        return anneePaiement;
    }

    public String getCodeUnite() {
        return codeUnite;
    }

    public TypeProcessusEnum getTypeProcessus() {
        return typeProcessus;
    }

    public Long getIdProcessusOrigine() {
        return idProcessusOrigine;
    }

    public String getMotifOuverture() {
        return motifOuverture;
    }

    public int getMontantTotal() {
        return montantTotal;
    }

    public StatutEnum getStatut() {
        return statut;
    }

    public boolean isTransmisComptabilite() {
        return transmisComptabilite;
    }

    public StatutIntegrationEnum getStatutIntegration() {
        return statutIntegration;
    }

    public String getReferenceComptable() {
        return referenceComptable;
    }

    public LocalDateTime getDateTraitement() {
        return dateTraitement;
    }

    /**
     * Instant de la reservation de transmission, nul tant que rien n'a ete reserve.
     * Avec un statut d'integration nul, son anciennete distingue un etat en transit
     * normal d'une publication d'issue incertaine (Sprint 5.3).
     */
    public LocalDateTime getDateReservationTransmission() {
        return dateReservationTransmission;
    }

    public String getMotifIntegration() {
        return motifIntegration;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

}
