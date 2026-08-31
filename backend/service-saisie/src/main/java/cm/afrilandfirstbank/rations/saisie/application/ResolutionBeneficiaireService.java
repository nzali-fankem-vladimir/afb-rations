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
        Optional<Beneficiaire> existant = beneficiaireRepository.findByNumCompteCourant(numCompteCourant);
        if (existant.isPresent()) {
            Beneficiaire beneficiaire = existant.get();
            signalerSiIdentiteDivergente(beneficiaire, nom, prenom);
            return beneficiaire;
        }
        return beneficiaireRepository.save(new Beneficiaire(nom, prenom, numCompteCourant, codeAgence));
    }

    /**
     * Compare l'identité saisie à celle enregistrée. En cas d'écart réel — après
     * normalisation —, laisse le bénéficiaire intact et émet la double trace.
     */
    private void signalerSiIdentiteDivergente(Beneficiaire existant, String nomSaisi, String prenomSaisi) {
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
        // rattraper ici (CLAUDE.md section 9.2). idUtilisateur / adresseIp sont
        // nuls : ce service n'a pas de contexte HTTP au Sprint 3.1 ; l'endpoint
        // du Sprint 3.2 les renseignera en enveloppant cet appel.
        publicateurAudit.publier(EvenementAudit.de(
                null,
                "INCOHERENCE_BENEFICIAIRE",
                "beneficiaires",
                existant.getId(),
                null,
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
