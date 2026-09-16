package cm.afrilandfirstbank.rations.workflow.application;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.workflow.domaine.ParametreSysteme;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.DelaiRegularisationIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ParametreSystemeRepository;

/**
 * Lecture des drapeaux de fonctionnalite et du delai de regularisation dans
 * {@code parametre_systeme} (Sprint 6bis.1,
 * {@code docs/dispositifs_provisoires.md} section 1).
 *
 * <h2>Deux parametres, deux comportements opposes en cas d'absence</h2>
 *
 * <p>Ce n'est pas une incoherence, c'est la meme regle appliquee a deux questions
 * differentes : <b>en l'absence d'information, on choisit toujours l'issue qui ne
 * peut rien casser</b>.
 *
 * <ul>
 *   <li>{@link #rattrapageActif()} — l'absence rend {@code false}, jamais une
 *       exception. « Fermer » est toujours la reponse sure : une fonctionnalite
 *       fermee ne fait rien, et c'est exactement l'etat attendu tant que le metier
 *       n'a pas confirme le besoin (point M-01).</li>
 *   <li>{@link #delaiRegularisationJours()} — l'absence <b>leve</b>, comme le seuil
 *       d'aiguillage au Sprint 4.3. Ici, aucune valeur par defaut n'est sure : un
 *       delai devine trop long ouvrirait des regularisations sur des periodes que
 *       le metier voulait figees, un delai trop court les refuserait toutes. Le
 *       refus est la seule issue qui n'invente rien.</li>
 * </ul>
 *
 * <h2>Une absence ne doit jamais etre silencieuse</h2>
 *
 * <p>Le drapeau ferme est le comportement normal <b>aujourd'hui</b> : la ligne
 * existe et porte {@code false}. Si cette ligne venait a disparaitre de la base, la
 * fonctionnalite resterait eteinte pour toujours et personne ne s'en apercevrait —
 * le jour ou le metier confirmerait, la mise a jour d'une ligne inexistante
 * n'ouvrirait rien. D'ou un {@code WARN} <b>distinct</b> pour l'absence, qu'aucune
 * fermeture deliberee ne declenche. Meme convention de prefixe reperable que
 * {@code SEUIL INDISPONIBLE} (Sprint 4.3) et {@code INCOHERENCE GRILLE}
 * (Sprint 2.4) : une seule recherche dans les journaux les retrouve tous.
 *
 * <h2>Relu a chaque appel, jamais mis en cache</h2>
 *
 * <p>Doctrine du Sprint 1.3 pour l'habilitation, redite au Sprint 4.3 pour le
 * seuil. Le dispositif tient sa valeur d'etre <b>reversible en une mise a jour de
 * parametre, sans redeploiement</b> ({@code docs/dispositifs_provisoires.md}
 * section 1.5) : un cache lui retirerait precisement cette propriete, et une
 * fermeture d'urgence resterait sans effet jusqu'au redemarrage.
 */
@Service
public class FonctionnaliteService {

    /** Code du drapeau, tel que la migration V2 l'inscrit. */
    public static final String CODE_RATTRAPAGE_ACTIF = "RATTRAPAGE_ACTIF";

    /** Code du compte de charge, tel que la migration V7 l'inscrit (Maille 2). */
    public static final String CODE_COMPTE_CHARGE = "COMPTE_CHARGE_RATIONS";

    /** Code du delai de regularisation, tel que la migration V2 l'inscrit. */
    public static final String CODE_DELAI_REGULARISATION_JOURS = "DELAI_REGULARISATION_JOURS";

    private static final String PREFIXE_JOURNAL_DRAPEAU = "DRAPEAU FONCTIONNALITE INTROUVABLE";
    private static final String PREFIXE_JOURNAL_DELAI = "DELAI REGULARISATION INDISPONIBLE";

    private static final String VALEUR_OUVERTE = "true";
    private static final String VALEUR_FERMEE = "false";

    private static final Logger journal = LoggerFactory.getLogger(FonctionnaliteService.class);

    private final ParametreSystemeRepository parametreSystemeRepository;

    public FonctionnaliteService(ParametreSystemeRepository parametreSystemeRepository) {
        this.parametreSystemeRepository = parametreSystemeRepository;
    }

    /**
     * L'ouverture d'un etat complementaire est-elle ouverte ?
     *
     * <p><b>Ne leve jamais.</b> Toute situation autre qu'un {@code 'true'} franc rend
     * {@code false} : parametre absent, parametre desactive, valeur illisible. Une
     * fonctionnalite sensible ne s'ouvre pas sur un doute
     * ({@code docs/dispositifs_provisoires.md} section 1.3, point de vigilance du
     * guide 6bis.1).
     *
     * <p>Les trois situations anormales sont journalisees en {@code WARN} avec des
     * messages distincts — disparition de la ligne, desactivation, valeur illisible —
     * parce qu'elles appellent trois gestes differents de l'administrateur. La
     * fermeture deliberee ({@code 'false'}), elle, ne journalise rien : c'est l'etat
     * nominal du module aujourd'hui, et le signaler noierait les trois autres.
     */
    public boolean rattrapageActif() {
        Optional<ParametreSysteme> actif =
                parametreSystemeRepository.findByCodeAndActifTrue(CODE_RATTRAPAGE_ACTIF);

        if (actif.isEmpty()) {
            journaliserDrapeauIndisponible();
            return false;
        }

        String valeur = valeurNormalisee(actif.get());

        // Comparaison insensible a la casse, mais a rien d'autre. « TRUE » est sans
        // ambiguite la meme intention que « true » — la valeur est ecrite a la main par
        // un administrateur, et refuser sur une majuscule laisserait la fonctionnalite
        // fermee avec un simple avertissement au journal, c'est-a-dire un echec presque
        // silencieux. « oui », « 1 » ou « vrai » restent refuses : la ou l'intention est
        // claire on la suit, la ou elle demande une interpretation on refuse.
        if (VALEUR_OUVERTE.equalsIgnoreCase(valeur)) {
            return true;
        }
        if (!VALEUR_FERMEE.equalsIgnoreCase(valeur)) {
            journal.warn("{} : le parametre {} porte la valeur « {} », qui n'est ni « {} » ni "
                    + "« {} ». La fonctionnalite est tenue pour FERMEE : une valeur illisible ne "
                    + "peut pas ouvrir une fonctionnalite qui touche au paiement.",
                    PREFIXE_JOURNAL_DRAPEAU, CODE_RATTRAPAGE_ACTIF, valeur,
                    VALEUR_OUVERTE, VALEUR_FERMEE);
        }
        return false;
    }

    /**
     * Le delai, en jours, pendant lequel une periode close reste regularisable
     * (point M-02, valeur provisoire de 90 jours).
     *
     * <p>Lecture stricte, mot pour mot celle du seuil d'aiguillage au Sprint 4.3 :
     * {@link Long#parseLong(String)} sur la valeur ebarbee, dont la meme capture
     * couvre le texte, la chaine vide, le separateur de milliers, la decimale
     * <b>et le depassement de capacite</b> ; le signe est verifie juste apres.
     * <b>Aucune autre exception ne sort de cette methode.</b>
     *
     * <p>Un delai negatif est refuse : il refuserait <i>toute</i> regularisation sans
     * qu'aucune erreur ne le signale, exactement comme un seuil negatif ferait monter
     * tout au directeur reseau. Un delai de zero est en revanche accepte — c'est une
     * configuration intelligible : seule une periode close le jour meme reste
     * regularisable.
     *
     * @throws DelaiRegularisationIndisponibleException parametre absent, desactive, ou
     *         valeur qui n'est pas un entier positif
     */
    public long delaiRegularisationJours() {
        ParametreSysteme parametre = parametreSystemeRepository
                .findByCodeAndActifTrue(CODE_DELAI_REGULARISATION_JOURS)
                .orElseThrow(this::delaiIntrouvable);

        return valeurEntiere(valeurNormalisee(parametre));
    }

    // --- Lecture stricte ---------------------------------------------------------

    private String valeurNormalisee(ParametreSysteme parametre) {
        return parametre.getValeur() == null ? "" : parametre.getValeur().strip();
    }

    /**
     * Deux silences a ne pas confondre : la ligne a disparu de la table, ou elle est
     * la mais desactivee. Les deux ferment la fonctionnalite ; seule la premiere
     * signale une perte de configuration, et le message le dit.
     */
    private void journaliserDrapeauIndisponible() {
        boolean ligneExiste = parametreSystemeRepository
                .findByCode(CODE_RATTRAPAGE_ACTIF).isPresent();

        if (ligneExiste) {
            journal.warn("{} : le parametre {} existe mais il est DESACTIVE (actif = false). La "
                    + "fonctionnalite est tenue pour fermee. Pour l'ouvrir, il faut reactiver la "
                    + "ligne en plus d'y porter « {} ».",
                    PREFIXE_JOURNAL_DRAPEAU, CODE_RATTRAPAGE_ACTIF, VALEUR_OUVERTE);
            return;
        }

        journal.warn("{} : AUCUNE ligne de code {} dans parametre_systeme. La fonctionnalite est "
                + "tenue pour fermee, ce qui est l'issue sure — mais la ligne posee par la "
                + "migration V2 a disparu, et tant qu'elle manque, l'UPDATE prevu au jour de la "
                + "confirmation metier (point M-01) n'ouvrirait rien du tout.",
                PREFIXE_JOURNAL_DRAPEAU, CODE_RATTRAPAGE_ACTIF);
    }

    private DelaiRegularisationIndisponibleException delaiIntrouvable() {
        journal.error("{} : aucun parametre actif de code {} dans parametre_systeme.",
                PREFIXE_JOURNAL_DELAI, CODE_DELAI_REGULARISATION_JOURS);
        return new DelaiRegularisationIndisponibleException(
                "Le delai de regularisation n'est pas configure : aucun parametre actif de code "
                        + CODE_DELAI_REGULARISATION_JOURS + " n'existe dans parametre_systeme. "
                        + "L'ouverture d'un etat complementaire est refusee plutot qu'accordee sur "
                        + "un delai suppose. Signalez-le a l'administrateur du module.");
    }

    private long valeurEntiere(String texte) {
        long delai;
        try {
            delai = Long.parseLong(texte);
        } catch (NumberFormatException conversionImpossible) {
            journal.error("{} : le parametre {} porte la valeur « {} », qui n'est pas un nombre "
                    + "entier exploitable.", PREFIXE_JOURNAL_DELAI,
                    CODE_DELAI_REGULARISATION_JOURS, texte);
            throw new DelaiRegularisationIndisponibleException(
                    "Le delai de regularisation est illisible : le parametre "
                            + CODE_DELAI_REGULARISATION_JOURS + " porte la valeur « " + texte
                            + " », qui n'est pas un nombre entier de jours. Attendu : des chiffres "
                            + "uniquement, sans espace et sans decimale. L'ouverture d'un etat "
                            + "complementaire est refusee plutot qu'accordee sur un delai suppose.",
                    conversionImpossible);
        }

        if (delai < 0) {
            journal.error("{} : le parametre {} porte un delai negatif ({}).",
                    PREFIXE_JOURNAL_DELAI, CODE_DELAI_REGULARISATION_JOURS, delai);
            throw new DelaiRegularisationIndisponibleException(
                    "Le delai de regularisation est invalide : le parametre "
                            + CODE_DELAI_REGULARISATION_JOURS + " porte la valeur " + delai
                            + ". Un delai negatif refuserait toute regularisation sans qu'aucune "
                            + "erreur ne le signale. L'ouverture est refusee.");
        }

        return delai;
    }


    /**
     * Le compte de charge sur lequel s'impute la depense, ou {@code null}.
     *
     * <h2>Tolerante en lecture, stricte a la publication</h2>
     *
     * <p><b>Ne leve jamais</b>, contrairement au delai de regularisation. Cette valeur
     * est portee par la reponse de {@code GET /processus/{id}}, que consulte aussi
     * l'agent d'unite : faire echouer la consultation d'un dossier parce qu'un
     * parametre <i>comptable</i> manque punirait quelqu'un qui n'y peut rien et n'a
     * rien a y voir.
     *
     * <p>Le refus vit a l'endroit ou il a un sens : {@code ConstructionChargeService}
     * (service Transmission) controle la charge avant de publier, et un compte absent
     * ou vide y produit une anomalie rendue en {@code 500 CHARGE_INCOMPLETE}. Publier
     * un message de paiement avec un compte de charge devine, c'est imputer de l'argent
     * sur le mauvais compte sans erreur visible — c'est la que le refus doit tomber,
     * pas a la lecture d'un dossier.
     *
     * <p>Relu a chaque appel, jamais mis en cache : un plan comptable evolue, et la
     * valeur doit pouvoir suivre par un simple {@code UPDATE}, sans redeploiement
     * (decision 6 du contenu de la charge comptable).
     */
    public String compteChargeRations() {
        String valeur = parametreSystemeRepository.findByCodeAndActifTrue(CODE_COMPTE_CHARGE)
                .map(this::valeurNormalisee)
                .orElse("");

        if (valeur.isEmpty()) {
            journal.warn("COMPTE DE CHARGE INDISPONIBLE : aucun parametre actif de code {} dans "
                    + "parametre_systeme, ou valeur vide. La consultation d'un dossier reste "
                    + "servie, mais AUCUN etat ne pourra etre transmis a la comptabilite tant "
                    + "que la ligne manque — le refus tombera a la publication, en "
                    + "CHARGE_INCOMPLETE.", CODE_COMPTE_CHARGE);
            return null;
        }
        return valeur;
    }

}
