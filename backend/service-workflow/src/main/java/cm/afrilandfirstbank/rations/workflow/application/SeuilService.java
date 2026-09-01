package cm.afrilandfirstbank.rations.workflow.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.workflow.domaine.ParametreSysteme;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.SeuilIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ParametreSystemeRepository;

/**
 * Lecture du seuil d'aiguillage dans {@code parametre_systeme} (RG-08).
 *
 * <h2>Le seul endroit du module ou une valeur de seuil est obtenue</h2>
 *
 * <p>RG-08 fait du seuil un parametre de <b>controle interne</b> : il determine le
 * niveau d'approbation requis pour engager la banque. Il doit donc pouvoir etre
 * releve ou abaisse par decision de gestion, sans redeploiement. Une valeur en dur
 * — meme en repli, meme dans un test — rendrait le parametre en base mensonger
 * tout en laissant croire qu'il gouverne (CLAUDE.md section 15).
 *
 * <h2>Trois decisions arbitrees au Sprint 4.3</h2>
 *
 * <ol>
 *   <li><b>Parametre absent ou desactive : refus</b>, jamais une valeur de repli.
 *       Voir {@link SeuilIndisponibleException} pour le motif, et le compromis
 *       assume — une erreur de configuration bloque le circuit, mais elle est
 *       visible immediatement.</li>
 *   <li><b>Valeur illisible : le meme refus</b>, avec le meme format de reponse.
 *       Un seul chemin d'echec, donc un seul comportement a connaitre. Aucune
 *       erreur brute ne remonte : {@link NumberFormatException} couvre le texte,
 *       la chaine vide, le separateur de milliers, la decimale <b>et le
 *       depassement de capacite</b> ; le signe est verifie juste apres.</li>
 *   <li><b>Relu a chaque validation, jamais mis en cache.</b> CT-18 exige qu'une
 *       modification du seuil prenne effet immediatement ; et c'est la meme
 *       doctrine qu'au Sprint 1.3 pour l'habilitation — aucun verdict de controle
 *       interne ne se sert d'une valeur potentiellement perimee. Le cout est un
 *       {@code SELECT} sur une table de trois lignes, indexee par code, dans un
 *       geste mensuel qui fait deja plusieurs secondes d'appels reseau.</li>
 * </ol>
 *
 * <h2>Ce service ne compare rien</h2>
 *
 * <p>Il rend une valeur. La comparaison au montant, coeur de RG-08, vit dans
 * {@link AiguillageService} et nulle part ailleurs.
 */
@Service
public class SeuilService {

    /**
     * Code du parametre, tel que la migration V2 l'inscrit. Ce n'est pas la valeur
     * du seuil : c'est la cle sous laquelle on va la chercher.
     */
    public static final String CODE_SEUIL_AIGUILLAGE = "SEUIL_AIGUILLAGE_DR";

    /**
     * Prefixe de journalisation repérable, meme convention que {@code INCOHERENCE
     * GRILLE} (Sprint 2.4) et {@code AUDIT PERDU} ({@code rations-audit-commun}) :
     * une recherche de ce libelle dans les journaux suffit a retrouver tous les
     * incidents de configuration du seuil.
     */
    private static final String PREFIXE_JOURNAL = "SEUIL INDISPONIBLE";

    private static final Logger journal = LoggerFactory.getLogger(SeuilService.class);

    private final ParametreSystemeRepository parametreSystemeRepository;

    public SeuilService(ParametreSystemeRepository parametreSystemeRepository) {
        this.parametreSystemeRepository = parametreSystemeRepository;
    }

    /**
     * Le seuil d'aiguillage en vigueur, en FCFA entiers.
     *
     * <p>Rendu en {@code long} alors que {@code processus_mensuel.montant_total} est
     * un {@code INTEGER} : la comparaison de {@link AiguillageService} se fait ainsi
     * en {@code long} des deux cotes, et aucun debordement ne peut inverser une
     * decision d'aiguillage.
     *
     * @throws SeuilIndisponibleException parametre absent, desactive, ou valeur qui
     *         n'est pas un entier positif. <b>Aucune autre exception ne sort de
     *         cette methode</b> : c'est l'unique chemin d'echec
     */
    public long seuilAiguillage() {
        ParametreSysteme parametre = parametreSystemeRepository
                .findByCodeAndActifTrue(CODE_SEUIL_AIGUILLAGE)
                .orElseThrow(this::parametreIntrouvable);

        return valeurEntiere(parametre.getValeur());
    }

    // --- Lecture stricte ---------------------------------------------------------

    private SeuilIndisponibleException parametreIntrouvable() {
        journal.error("{} : aucun parametre actif de code {} dans parametre_systeme.",
                PREFIXE_JOURNAL, CODE_SEUIL_AIGUILLAGE);
        return new SeuilIndisponibleException(
                "Le seuil d'aiguillage n'est pas configure : aucun parametre actif de code "
                        + CODE_SEUIL_AIGUILLAGE + " n'existe dans parametre_systeme. La validation "
                        + "est refusee plutot qu'appliquee sur une valeur supposee (RG-08). "
                        + "Signalez-le a l'administrateur du module.");
    }

    /**
     * Conversion stricte.
     *
     * <p><b>Ni separateur de milliers, ni decimale, ni valeur negative.</b> Les deux
     * premiers seraient des saisies approximatives dont on ne peut pas deviner
     * l'intention — {@code "100 000"} peut aussi bien vouloir dire cent mille qu'une
     * valeur tronquee. Le troisieme est plus grave : un seuil negatif ferait monter
     * <b>absolument tout</b> au Directeur Reseau sans jamais produire d'erreur, et
     * ce detournement du circuit passerait inapercu.
     *
     * <p>Un seuil de zero est en revanche accepte : c'est une configuration
     * intelligible — tout etat non nul requiert l'approbation du Directeur Reseau.
     */
    private long valeurEntiere(String valeur) {
        String texte = valeur == null ? "" : valeur.strip();

        long seuil;
        try {
            seuil = Long.parseLong(texte);
        } catch (NumberFormatException conversionImpossible) {
            journal.error("{} : le parametre {} porte la valeur « {} », qui n'est pas un nombre "
                    + "entier exploitable.", PREFIXE_JOURNAL, CODE_SEUIL_AIGUILLAGE, texte);
            throw new SeuilIndisponibleException(
                    "Le seuil d'aiguillage est illisible : le parametre " + CODE_SEUIL_AIGUILLAGE
                            + " porte la valeur « " + texte + " », qui n'est pas un nombre entier "
                            + "en FCFA. Attendu : des chiffres uniquement, sans espace, sans "
                            + "separateur de milliers et sans decimale. La validation est refusee "
                            + "plutot qu'appliquee sur une valeur supposee (RG-08).",
                    conversionImpossible);
        }

        if (seuil < 0) {
            journal.error("{} : le parametre {} porte un seuil negatif ({}).",
                    PREFIXE_JOURNAL, CODE_SEUIL_AIGUILLAGE, seuil);
            throw new SeuilIndisponibleException(
                    "Le seuil d'aiguillage est invalide : le parametre " + CODE_SEUIL_AIGUILLAGE
                            + " porte la valeur " + seuil + ". Un seuil negatif ferait monter tous "
                            + "les etats au Directeur Reseau sans qu'aucune erreur ne le signale. "
                            + "La validation est refusee.");
        }

        return seuil;
    }

}
