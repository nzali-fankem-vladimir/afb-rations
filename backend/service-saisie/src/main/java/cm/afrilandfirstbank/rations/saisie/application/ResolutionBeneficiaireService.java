package cm.afrilandfirstbank.rations.saisie.application;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.saisie.domaine.Beneficiaire;
import cm.afrilandfirstbank.rations.saisie.infrastructure.BeneficiaireRepository;

/**
 * Retrouve ou crée le bénéficiaire d'une ligne de prestation, au fil de la
 * saisie (US-03). Il n'y a <b>aucun enrôlement</b> : ce service est le seul point
 * par lequel un bénéficiaire entre dans la base.
 *
 * <p>Deux décisions de l'étape 4 du Sprint 3.1, prises avec l'utilisateur et
 * consignées dans
 * {@code docs/decisions/2026-08-28-resolution-beneficiaire-et-incoherence-nom.md} :
 *
 * <ol>
 *   <li><b>Identité = numéro de compte courant seul.</b> Un même compte ressaisi
 *       avec une faute de frappe dans le nom retrouve le bénéficiaire existant,
 *       plutôt que d'en créer un doublon qui fausserait RG-04.</li>
 *   <li><b>Compte connu, nom divergent → on conserve l'existant sans le modifier,
 *       et on trace.</b> Le compte fait foi ; l'écart nom↔compte peut être une
 *       faute de frappe bénigne comme une erreur de compte (grave), et le service
 *       ne peut pas les distinguer. Il ne bloque pas la saisie, mais émet une
 *       trace vérifiable a posteriori : un log {@code WARN} au préfixe repérable
 *       {@link #PREFIXE_INCOHERENCE} <i>et</i> un événement d'audit
 *       {@code INCOHERENCE_BENEFICIAIRE}.</li>
 * </ol>
 *
 * <p><b>La trace est un filet a posteriori, pas un contrôle préventif.</b> Rien
 * ici n'empêche une ligne incohérente d'être enregistrée puis transmise. Le
 * point dur reste ouvert pour le Sprint 4 : le DA/DR qui valide un état devrait
 * voir les lignes marquées {@code INCOHERENCE_BENEFICIAIRE} avant de clôturer.
 * Voir la décision citée, section « Point ouvert ».
 */
@Service
public class ResolutionBeneficiaireService {

    private static final Logger log = LoggerFactory.getLogger(ResolutionBeneficiaireService.class);

    /**
     * Préfixe de log repérable en exploitation, même convention que
     * {@code INCOHERENCE GRILLE} (service Grilles) et {@code AUDIT PERDU}
     * ({@code rations-audit-commun}).
     */
    static final String PREFIXE_INCOHERENCE = "INCOHERENCE BENEFICIAIRE";

    private static final String ENTITE_CIBLE = "beneficiaires";
    private static final String ACTION_CREATION = "CREATION_BENEFICIAIRE";
    private static final String ACTION_INCOHERENCE = "INCOHERENCE_BENEFICIAIRE";

    private final BeneficiaireRepository beneficiaireRepository;
    private final PublicateurAudit publicateurAudit;

    public ResolutionBeneficiaireService(BeneficiaireRepository beneficiaireRepository,
                                         PublicateurAudit publicateurAudit) {
        this.beneficiaireRepository = beneficiaireRepository;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Retourne le bénéficiaire correspondant au compte fourni, en le créant s'il
     * n'existe pas encore.
     *
     * @param nom              nom saisi par l'agent
     * @param prenom           prénom saisi par l'agent
     * @param numCompteCourant numéro de compte courant — <b>seul critère
     *                         d'identité</b>
     * @param codeAgence       agence de domiciliation du compte (utilisée
     *                         uniquement à la création)
     */
    @Transactional
    public Beneficiaire resoudre(String nom, String prenom, String numCompteCourant, String codeAgence) {
        return resoudre(nom, prenom, numCompteCourant, codeAgence, null);
    }

    /**
     * Même opération, avec l'adresse d'origine de la requête pour les traces
     * d'audit. Même surcharge que {@code CreationLigneService.creer} au
     * Sprint 3.3 : l'appel de base continue de servir les tests écrits avant que
     * ce service ait un contexte HTTP.
     *
     * @param adresseIp origine de la requête, ou {@code null} si inconnue
     */
    @Transactional
    public Beneficiaire resoudre(String nom, String prenom, String numCompteCourant, String codeAgence,
                                 String adresseIp) {
        Optional<Beneficiaire> existant = beneficiaireRepository.findByNumCompteCourant(numCompteCourant);
        if (existant.isPresent()) {
            Beneficiaire beneficiaire = existant.get();
            signalerSiIdentiteDivergente(beneficiaire, nom, prenom, adresseIp);
            return beneficiaire;
        }
        Beneficiaire cree = beneficiaireRepository.save(
                new Beneficiaire(nom, prenom, numCompteCourant, codeAgence));
        tracerCreation(cree, adresseIp);
        return cree;
    }

    /**
     * Trace l'entrée d'un bénéficiaire dans la base (omission relevée au
     * Sprint 6.3, étape 2).
     *
     * <p>Ce service est le <b>seul point</b> par lequel un bénéficiaire est créé,
     * et il n'y a aucun enrôlement en amont (CLAUDE.md §4) : la ligne naît de la
     * saisie d'un agent, sans validation préalable de personne. Or elle porte le
     * nom et le {@code num_compte_courant} qui recevront l'argent — c'est la
     * ligne de crédit de l'ordre de paiement. Sa création est donc un fait à
     * tracer au même titre que la ligne de prestation qui s'y rattache, et non un
     * effet de bord technique de celle-ci.
     *
     * <p>Le delta est un avant/après depuis {@code null} : à ce stade il n'existe
     * pas d'état antérieur, et c'est exactement ce que la trace doit dire.
     *
     * <p>{@code idUtilisateur} reste nul, décision du Sprint 3.3 reconduite
     * (le résoudre imposerait un quatrième appel réseau sur le chemin d'écriture
     * d'une ligne). Limite consignée dans {@code docs/points-en-attente.md},
     * point A-01.
     */
    private void tracerCreation(Beneficiaire cree, String adresseIp) {
        log.info("Nouveau beneficiaire enregistre : {} {} (compte {}, agence {}, id {}).",
                cree.getNom(), cree.getPrenom(), cree.getNumCompteCourant(),
                cree.getCodeAgence(), cree.getId());

        publicateurAudit.publier(EvenementAudit.de(
                null,
                ACTION_CREATION,
                ENTITE_CIBLE,
                cree.getId(),
                adresseIp,
                DeltaAudit.nouveau()
                        .champ("nom", null, cree.getNom())
                        .champ("prenom", null, cree.getPrenom())
                        .champ("numCompteCourant", null, cree.getNumCompteCourant())
                        .champ("codeAgence", null, cree.getCodeAgence())
                        .contexte("origine", "cree au fil de la saisie, sans enrolement prealable")
                        .enJson()));
    }

    /**
     * Compare l'identité saisie à celle enregistrée. En cas d'écart réel — après
     * normalisation —, laisse le bénéficiaire intact et émet la double trace.
     */
    private void signalerSiIdentiteDivergente(Beneficiaire existant, String nomSaisi, String prenomSaisi,
                                              String adresseIp) {
        boolean memeIdentite = memeValeur(existant.getNom(), nomSaisi)
                && memeValeur(existant.getPrenom(), prenomSaisi);
        if (memeIdentite) {
            return;
        }

        log.warn("{} : compte {} enregistre au nom de \"{} {}\", saisi cette fois \"{} {}\". "
                        + "Ligne rattachee au beneficiaire existant (id {}), non modifie.",
                PREFIXE_INCOHERENCE, existant.getNumCompteCourant(),
                existant.getNom(), existant.getPrenom(), nomSaisi, prenomSaisi, existant.getId());

        // Contrat de PublicateurAudit : ne lève jamais, ne bloque pas. Rien à
        // rattraper ici (CLAUDE.md section 9.2). idUtilisateur reste nul
        // (décision Sprint 3.3) ; adresseIp est désormais transmise depuis le
        // contrôleur, correction du Sprint 6.3.
        publicateurAudit.publier(EvenementAudit.de(
                null,
                ACTION_INCOHERENCE,
                ENTITE_CIBLE,
                existant.getId(),
                adresseIp,
                DeltaAudit.nouveau()
                        .contexte("numCompteCourant", existant.getNumCompteCourant())
                        .champ("nom", existant.getNom(), nomSaisi)
                        .champ("prenom", existant.getPrenom(), prenomSaisi)
                        .contexte("resolution", "beneficiaire existant conserve, non modifie")
                        .enJson()));
    }

    /**
     * Égalité de deux libellés après normalisation : espaces de début/fin et
     * multiples réduits, casse neutralisée, accents retirés. {@code null} est
     * traité comme la chaîne vide. Sans cette normalisation, {@code "  NGUEMA"}
     * et {@code "Nguéma"} déclencheraient une fausse incohérence et rendraient
     * l'audit inexploitable.
     */
    static boolean memeValeur(String a, String b) {
        return normaliser(a).equals(normaliser(b));
    }

    private static String normaliser(String valeur) {
        if (valeur == null) {
            return "";
        }
        String sansAccent = Normalizer.normalize(valeur, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return sansAccent.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

}
