package cm.afrilandfirstbank.rations.workflow.domaine;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import cm.afrilandfirstbank.rations.workflow.domaine.exception.EtatNonClotureException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.MotifOuvertureRequisException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.MotifRetourRequisException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.TransitionProcessusInterditeException;

/**
 * Machine a etats du processus mensuel (diagramme ET01, RG-07).
 *
 * <p>Les neuf transitions du circuit, et rien d'autre :
 *
 * <pre>
 *   (creation)       -> EN_COURS_SAISIE   declencher(...)                     l'agent ouvre le mois
 *   (creation)       -> EN_COURS_SAISIE   ouvrirComplementaire(origine, motif) regularisation (6bis.1)
 *   EN_COURS_SAISIE  -> SOUMIS            soumettre(...)                      informations completes
 *   SOUMIS           -> EN_ATTENTE_DA     transfererAuChefUnite(...)          signature agent apposee
 *   EN_ATTENTE_DA    -> CLOTURE           cloturerApresValidationChefUnite(...)
 *   EN_ATTENTE_DA    -> EN_ATTENTE_DR     aiguillerVersDirecteurReseau(...)
 *   EN_ATTENTE_DA    -> RETOURNE          retournerParChefUnite(..., motif)
 *   EN_ATTENTE_DR    -> CLOTURE           cloturerApresValidationDirecteurReseau(...)
 *   EN_ATTENTE_DR    -> RETOURNE          retournerParDirecteurReseau(..., motif)
 *   RETOURNE         -> EN_COURS_SAISIE   reprendreParAgent(...)              reprise apres correction
 * </pre>
 *
 * <p>Toute autre transition leve {@link TransitionProcessusInterditeException}.
 *
 * <h2>CLOTURE est terminal</h2>
 *
 * <p>Aucune transition ne repart de {@link StatutEnum#CLOTURE}, pas meme vers
 * {@link StatutEnum#RETOURNE} « pour corriger ». Ce n'est pas une rigidite
 * gratuite : un etat qu'on peut rouvrir peut etre reclos, donc <b>retransmis</b> a
 * la comptabilite — un double paiement (RG-13). Une correction sur une periode
 * close se fait par un etat COMPLEMENTAIRE qui reference l'original sans le
 * rouvrir (CLAUDE.md sections 7 et 15). Les tests 13 et 14 de
 * {@code TransitionProcessusTest} verrouillent ce point.
 *
 * <h2>Cette machine n'arbitre pas le montant</h2>
 *
 * <p>Depuis {@link StatutEnum#EN_ATTENTE_DA}, ET01 prevoit deux issues de
 * validation : la cloture directe si le montant ne depasse pas le seuil, l'envoi
 * au Directeur Reseau sinon (RG-08). <b>Les deux transitions sont exposees
 * separement, et aucune ne lit un montant ni un seuil.</b> Le choix entre elles
 * appartient au service d'aiguillage du sous-sprint 4.3, qui lit le seuil dans
 * {@code parametre_systeme} — jamais code en dur (CLAUDE.md section 15).
 *
 * <p>Melanger les deux — une methode {@code valider(processus, montant, seuil)}
 * qui trancherait ici — rendrait la machine dependante d'un parametre de
 * configuration : elle ne pourrait plus etre relue comme la traduction fidele
 * d'ET01, et un changement de seuil deviendrait une modification du domaine.
 *
 * <h2>Ce que cette classe ne fait pas</h2>
 *
 * <p>Elle ne cree aucune ligne {@code etape_workflow}, n'apposant donc aucune
 * signature (RG-09) et n'enregistrant aucun motif de retour : {@code motif_retour}
 * et {@code signature_numerique} vivent sur l'etape, pas sur le processus. Elle
 * n'ecrit pas non plus le {@code montant_total}. Ces effets relevent des services
 * applicatifs des sous-sprints 4.2 a 4.4 ; ici on ne decide que de la legalite du
 * changement de statut, et on l'applique.
 */
public final class TransitionProcessus {

    /** Statut d'un processus qui vient de naitre — premiere transition d'ET01. */
    public static final StatutEnum STATUT_INITIAL = StatutEnum.EN_COURS_SAISIE;

    private static final Set<StatutEnum> AUCUNE_ISSUE = figees();

    private static final Map<StatutEnum, Set<StatutEnum>> TRANSITIONS_AUTORISEES;

    static {
        Map<StatutEnum, Set<StatutEnum>> m = new EnumMap<>(StatutEnum.class);
        m.put(StatutEnum.EN_COURS_SAISIE, figees(StatutEnum.SOUMIS));
        m.put(StatutEnum.SOUMIS, figees(StatutEnum.EN_ATTENTE_DA));
        m.put(StatutEnum.EN_ATTENTE_DA, figees(
                StatutEnum.CLOTURE, StatutEnum.EN_ATTENTE_DR, StatutEnum.RETOURNE));
        m.put(StatutEnum.EN_ATTENTE_DR, figees(StatutEnum.CLOTURE, StatutEnum.RETOURNE));
        m.put(StatutEnum.RETOURNE, figees(StatutEnum.EN_COURS_SAISIE));
        // Terminal : l'ensemble vide n'est pas un oubli, c'est la regle.
        m.put(StatutEnum.CLOTURE, AUCUNE_ISSUE);
        TRANSITIONS_AUTORISEES = Map.copyOf(m);
    }

    /**
     * Ensemble non modifiable, d'iteration deterministe (ordre de declaration de
     * {@link StatutEnum}, propriete d'{@link EnumSet}) : les messages d'erreur
     * enumerent donc toujours les issues dans le meme ordre.
     */
    private static Set<StatutEnum> figees(StatutEnum... cibles) {
        EnumSet<StatutEnum> ensemble = EnumSet.noneOf(StatutEnum.class);
        ensemble.addAll(Arrays.asList(cibles));
        return Collections.unmodifiableSet(ensemble);
    }

    private TransitionProcessus() {
        // classe utilitaire
    }

    // --- Predicat --------------------------------------------------------------

    /**
     * Indique si le passage du statut {@code source} vers {@code cible} est prevu
     * par ET01.
     *
     * <p><b>Porte : les transitions d'etat a etat uniquement.</b> La creation
     * n'est pas interrogeable ici, et un {@code source} nul rend {@code false}
     * plutot que d'etre interprete comme « depuis rien ». Un statut nul est
     * quasiment toujours un defaut — une entite mal chargee — et le faire
     * ressembler a une creation legitime transformerait ce defaut en autorisation.
     * La creation passe par {@link #declencher(Integer, Integer, String)}, qui est
     * la seule facon de la produire.
     */
    public static boolean estAutorisee(StatutEnum source, StatutEnum cible) {
        if (source == null || cible == null) {
            return false;
        }
        return issuesDepuis(source).contains(cible);
    }

    // --- Les neuf transitions d'ET01 -------------------------------------------

    /**
     * <b>1. (creation) -&gt; EN_COURS_SAISIE.</b> L'agent declenche le processus
     * normal du mois pour son unite.
     *
     * <p>Seule porte de creation d'un {@link ProcessusMensuel} : son constructeur
     * est en visibilite paquet. Le processus naît au statut
     * {@link #STATUT_INITIAL}, montant total a zero, non transmis.
     *
     * <p>Cette methode ne verifie <b>pas</b> l'unicite du processus normal pour le
     * couple unite / periode : c'est une regle qui porte sur l'ensemble des
     * processus enregistres, pas sur le cycle de vie de celui-ci. Elle est
     * appliquee par le service de declenchement, avant l'appel
     * ({@code ux_processus_normal_par_periode} en filet).
     */
    public static ProcessusMensuel declencher(Integer moisPaiement, Integer anneePaiement,
            String codeUnite) {
        return new ProcessusMensuel(moisPaiement, anneePaiement, codeUnite);
    }

    /**
     * <b>1bis. (creation) -&gt; EN_COURS_SAISIE, type COMPLEMENTAIRE.</b> L'agent ouvre
     * une regularisation sur une periode close (Sprint 6bis.1, US-17, CT-34).
     *
     * <p>Seconde et derniere porte de creation d'un {@link ProcessusMensuel}. Elle ne
     * modifie <b>rien</b> sur l'etat d'origine : on ne rouvre jamais un etat clos
     * (CLAUDE.md sections 7 et 15).
     *
     * <h2>Ce qui est verifie ici, et ce qui ne l'est pas</h2>
     *
     * <p>Deux conditions seulement, et ce sont les deux que la machine a etats est
     * fondee a juger : l'origine est <b>close</b>, et un motif existe.
     *
     * <p>Le statut de l'origine appartient bien a cette classe : elle est ce qui dit,
     * dans ce module, ce qu'un statut permet. La placer ici la rend <b>verifiee par le
     * compilateur</b> plutot que par discipline — aucun chemin de code ne peut ouvrir
     * un complementaire sur un etat encore en circuit, meme en contournant le service.
     *
     * <p>Tout le reste — drapeau {@code RATTRAPAGE_ACTIF}, delai de regularisation,
     * portee d'acces, concordance de l'unite et de la periode declarees — releve de
     * {@code OuvertureComplementaireService} : ce sont des regles d'ouverture, pas des
     * regles de cycle de vie, et les faire entrer ici rendrait la machine dependante
     * d'un parametre de configuration (meme raisonnement que pour le seuil, RG-08).
     *
     * @param origine etat clos que ce complementaire regularise
     * @param motif motif d'ouverture, exige non vide — une suite d'espaces n'en est pas
     *        un. Seule trace du signalement, ce module n'ayant pas d'entite Reclamation
     * @throws EtatNonClotureException si l'origine n'est pas {@link StatutEnum#CLOTURE}
     * @throws MotifOuvertureRequisException si le motif est absent ou vide
     */
    public static ProcessusMensuel ouvrirComplementaire(ProcessusMensuel origine, String motif) {
        exigerOrigineClose(origine);
        exigerMotifOuverture(motif);
        return new ProcessusMensuel(origine, motif);
    }

    /**
     * <b>2. EN_COURS_SAISIE -&gt; SOUMIS.</b> L'agent soumet son etat, les
     * informations etant completes.
     *
     * <p>La verification que l'etat n'est pas vide ({@code nombreLignes == 0},
     * point C-03 du Sprint 3.4) appartient au service de soumission, pas ici : la
     * machine dit si le <i>statut</i> permet la transition, pas si le contenu la
     * justifie.
     */
    public static void soumettre(ProcessusMensuel processus) {
        appliquer(processus, StatutEnum.SOUMIS);
    }

    /**
     * <b>3. SOUMIS -&gt; EN_ATTENTE_DA.</b> Signature de l'agent apposee, l'etat
     * est transfere au Chef d'Unite.
     */
    public static void transfererAuChefUnite(ProcessusMensuel processus) {
        appliquer(processus, StatutEnum.EN_ATTENTE_DA);
    }

    /**
     * <b>4. EN_ATTENTE_DA -&gt; CLOTURE.</b> Validation du Chef d'Unite sur un
     * etat qui ne depasse pas le seuil : cloture directe, sans passage par le
     * Directeur Reseau.
     *
     * <p><b>La comparaison au seuil n'est pas faite ici</b> (RG-08). Cette methode
     * applique une decision deja prise par le service d'aiguillage du sous-sprint
     * 4.3 ; elle ne lit ni {@code montantTotal}, ni {@code parametre_systeme}.
     */
    public static void cloturerApresValidationChefUnite(ProcessusMensuel processus) {
        appliquer(processus, StatutEnum.CLOTURE);
    }

    /**
     * <b>5. EN_ATTENTE_DA -&gt; EN_ATTENTE_DR.</b> Validation du Chef d'Unite sur
     * un etat au-dela du seuil : l'etat monte au Directeur Reseau.
     *
     * <p>Jumelle de {@link #cloturerApresValidationChefUnite(ProcessusMensuel)} :
     * meme statut de depart, meme geste metier, deux issues. Le choix entre elles
     * appartient au service d'aiguillage (sous-sprint 4.3), pas a la machine.
     */
    public static void aiguillerVersDirecteurReseau(ProcessusMensuel processus) {
        appliquer(processus, StatutEnum.EN_ATTENTE_DR);
    }

    /**
     * <b>6. EN_ATTENTE_DA -&gt; RETOURNE.</b> Le Chef d'Unite retourne l'etat a
     * l'agent, motif obligatoire (RG-10).
     *
     * <p>Le retour va <b>toujours</b> a l'agent, jamais a un niveau intermediaire
     * (RG-11) — ce que le statut cible unique {@link StatutEnum#RETOURNE} rend
     * structurel : il n'existe aucune autre cible a choisir.
     *
     * @param motif motif du retour, exige non vide ; il est enregistre sur la
     *        ligne {@code etape_workflow} par le service appelant, cette classe ne
     *        le stocke pas — mais l'exiger <i>en parametre de la transition</i>
     *        rend impossible de retourner un etat sans en avoir un
     */
    public static void retournerParChefUnite(ProcessusMensuel processus, String motif) {
        exigerMotif(motif);
        appliquer(processus, StatutEnum.RETOURNE);
    }

    /**
     * <b>7. EN_ATTENTE_DR -&gt; CLOTURE.</b> Validation du Directeur Reseau :
     * l'etat est clos. Terminal.
     */
    public static void cloturerApresValidationDirecteurReseau(ProcessusMensuel processus) {
        appliquer(processus, StatutEnum.CLOTURE);
    }

    /**
     * <b>8. EN_ATTENTE_DR -&gt; RETOURNE.</b> Le Directeur Reseau retourne l'etat,
     * motif obligatoire (RG-10).
     *
     * <p>Le retour ramene a l'agent, <b>pas au Chef d'Unite</b> (RG-11), bien que
     * le dossier soit passe par lui. C'est le point ou la regle est le plus facile
     * a enfreindre par commodite : renvoyer au niveau precedent semblerait naturel,
     * et serait faux.
     */
    public static void retournerParDirecteurReseau(ProcessusMensuel processus, String motif) {
        exigerMotif(motif);
        appliquer(processus, StatutEnum.RETOURNE);
    }

    /**
     * <b>9. RETOURNE -&gt; EN_COURS_SAISIE.</b> L'agent reprend l'etat pour
     * correction.
     *
     * <p>La reprise remet l'etat en saisie ; il devra etre resoumis et repasser
     * par tout le circuit (RG-07). Aucun raccourci ne ramene un etat repris
     * directement en {@link StatutEnum#EN_ATTENTE_DA} — c'est la transition
     * interdite n° 15 du plan de test.
     */
    public static void reprendreParAgent(ProcessusMensuel processus) {
        appliquer(processus, StatutEnum.EN_COURS_SAISIE);
    }

    // --- Mecanique interne -----------------------------------------------------

    private static Set<StatutEnum> issuesDepuis(StatutEnum source) {
        return TRANSITIONS_AUTORISEES.getOrDefault(source, AUCUNE_ISSUE);
    }

    /**
     * Verifie puis applique. La verification precede toujours l'ecriture : c'est
     * la discipline retenue au Sprint 2.3 pour les grilles — faire dependre la
     * correction d'un rollback plutot que de l'ordre des etapes rend le
     * raisonnement plus fragile qu'il n'y parait.
     */
    private static void appliquer(ProcessusMensuel processus, StatutEnum cible) {
        if (processus == null) {
            throw new IllegalArgumentException(
                    "Aucun processus fourni a la transition vers " + cible + ".");
        }
        exigerTransition(processus.getStatut(), cible);
        processus.appliquerStatut(cible);
    }

    private static void exigerTransition(StatutEnum source, StatutEnum cible) {
        if (source == null) {
            throw new IllegalStateException(
                    "Le processus ne porte aucun statut : transition vers " + cible
                    + " impossible a juger. La colonne statut est NOT NULL en base ; "
                    + "une valeur nulle signale une entite mal construite.");
        }
        if (estAutorisee(source, cible)) {
            return;
        }

        Set<StatutEnum> issues = issuesDepuis(source);
        if (issues.isEmpty()) {
            throw new TransitionProcessusInterditeException(
                    "Transition de statut interdite : " + source + " -> " + cible
                    + ". Le statut " + source + " est definitif : aucune transition n'en repart. "
                    + "Une regularisation sur une periode close passe par un etat "
                    + "COMPLEMENTAIRE referencant cet etat, jamais par sa reouverture "
                    + "(RG-13 : un etat valide n'est transmis qu'une fois).");
        }
        throw new TransitionProcessusInterditeException(
                "Transition de statut interdite : " + source + " -> " + cible
                + ". Depuis " + source + ", transitions autorisees : " + issues + " (ET01, RG-07).");
    }

    private static void exigerMotif(String motif) {
        if (motif == null || motif.isBlank()) {
            throw new MotifRetourRequisException(
                    "Le retour d'un etat a l'agent exige un motif (RG-10).");
        }
    }

    /**
     * Un complementaire ne se rattache qu'a un etat <b>clos</b>.
     *
     * <p>Un etat encore en circuit n'a pas besoin d'etre regularise : il peut etre
     * retourne a l'agent, corrige et resoumis (RG-11). Ouvrir un complementaire a cote
     * de lui creerait deux dossiers vivants sur la meme periode, dont les montants se
     * cumuleraient a l'insu du valideur.
     */
    private static void exigerOrigineClose(ProcessusMensuel origine) {
        if (origine == null) {
            throw new IllegalArgumentException(
                    "Aucun etat d'origine fourni a l'ouverture d'un etat complementaire.");
        }
        if (origine.getStatut() != StatutEnum.CLOTURE) {
            throw new EtatNonClotureException(
                    "L'etat d'origine " + origine.getId() + " porte le statut "
                            + origine.getStatut() + " : un etat complementaire ne se rattache qu'a "
                            + "un etat " + StatutEnum.CLOTURE + ". Un dossier encore dans le "
                            + "circuit se corrige par un retour a l'agent (RG-11), pas par une "
                            + "regularisation.");
        }
    }

    /**
     * Le motif d'ouverture, exige non vide — pendant de {@link #exigerMotif(String)}
     * pour le retour (RG-10).
     *
     * <p>Le controle porte sur le <b>contenu utile</b> : {@code isBlank} refuse la
     * chaine vide <i>et</i> la suite d'espaces, qu'{@code isEmpty} aurait laissee
     * passer. Sans motif, plus rien n'explique pourquoi une periode close a recu un
     * paiement complementaire : ce module n'a pas d'entite Reclamation, le signalement
     * du beneficiaire lui est exterieur (CLAUDE.md section 7).
     */
    private static void exigerMotifOuverture(String motif) {
        if (motif == null || motif.isBlank()) {
            throw new MotifOuvertureRequisException(
                    "L'ouverture d'un etat complementaire exige un motif : c'est la seule trace "
                            + "de ce qui a declenche la regularisation.");
        }
    }

}
