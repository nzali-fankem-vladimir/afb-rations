package cm.afrilandfirstbank.rations.workflow.application;

import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.domaine.ParametreSysteme;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ParametreIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ParametreNonModifiableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ServiceIdentiteIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ValeurParametreInvalideException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ParametreSystemeRepository;

/**
 * Ecriture de trois parametres systeme, reservee a l'ADMIN (guide 7F.6, etape
 * 6). Ajout backend scope, tranche avec l'utilisateur en ouverture d'etape :
 * sans lui, le seuil d'aiguillage, le delai de regularisation et le compte de
 * charge restaient modifiables uniquement par {@code UPDATE} SQL direct, et
 * aucun sprint backend n'est plus prevu pour combler ce manque (les sprints 8
 * et 9 restants portent le deploiement et la recette, pas de regle metier).
 * Voir {@code docs/decisions/2026-09-17-endpoint-ecriture-parametres-systeme.md}.
 *
 * <h2>Trois codes, et pas un de plus</h2>
 *
 * <p>{@link #CODES_MODIFIABLES} exclut deliberement {@code RATTRAPAGE_ACTIF} :
 * ce drapeau reste gouverne par sa propre doctrine (CLAUDE.md section 7,
 * ouvert par migration V8, ferme par un {@code UPDATE} hors module en cas
 * d'urgence) — voir {@link ParametreNonModifiableException}.
 *
 * <h2>Meme lecture stricte que le seuil d'aiguillage (Sprint 4.3)</h2>
 *
 * <p>Les deux parametres entiers ({@code SEUIL_AIGUILLAGE_DR},
 * {@code DELAI_REGULARISATION_JOURS}) reprennent la conversion stricte de
 * {@code SeuilService} : ni separateur de milliers, ni decimale, ni valeur
 * negative. Ce n'est pas une seconde comparaison montant/seuil (interdite par
 * CLAUDE.md section 15) — {@code AiguillageService} reste le seul endroit qui
 * compare un montant a ce seuil — mais une validation de <b>forme</b> avant
 * ecriture, necessairement distincte puisque {@code SeuilService} ne lit
 * jamais en dehors d'une validation.
 *
 * <h2>Ordre des operations</h2>
 *
 * <p>Le format est verifie <b>avant</b> tout appel reseau : une valeur
 * illisible se refuse sans deranger le service Identite, meme doctrine que
 * {@code RetourService} pour le motif obligatoire (RG-10). L'acteur n'est
 * resolu qu'ensuite, pour porter l'evenement d'audit.
 */
@Service
public class ParametreAdminService {

    private static final String ACTION_MODIFICATION_PARAMETRE = "MODIFICATION_PARAMETRE";
    private static final String ENTITE_CIBLE = "parametre_systeme";

    /**
     * Les trois seuls codes modifiables par cet endpoint. {@code RATTRAPAGE_ACTIF}
     * en est exclu (voir javadoc de classe).
     */
    public static final Set<String> CODES_MODIFIABLES = Set.of(
            SeuilService.CODE_SEUIL_AIGUILLAGE,
            FonctionnaliteService.CODE_DELAI_REGULARISATION_JOURS,
            FonctionnaliteService.CODE_COMPTE_CHARGE);

    private final ParametreSystemeRepository parametreSystemeRepository;
    private final ProfilClient profilClient;
    private final PublicateurAudit publicateurAudit;

    public ParametreAdminService(ParametreSystemeRepository parametreSystemeRepository,
            ProfilClient profilClient, PublicateurAudit publicateurAudit) {
        this.parametreSystemeRepository = parametreSystemeRepository;
        this.profilClient = profilClient;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Lit un parametre, quel que soit son code -- y compris hors de
     * {@link #CODES_MODIFIABLES} (l'ADMIN peut consulter {@code RATTRAPAGE_ACTIF},
     * il ne peut simplement pas l'ecrire ici). Sert l'ecran d'administration a
     * afficher la valeur courante avant modification : l'ecrire a l'aveugle
     * exposerait a un ecrasement non voulu.
     *
     * <p>Aucune trace d'audit : une lecture de travail n'en publie pas
     * (CLAUDE.md section 9.2).
     *
     * @throws ParametreIntrouvableException aucune ligne ne porte ce code
     */
    public ParametreSysteme consulter(String code) {
        return parametreSystemeRepository.findByCode(code)
                .orElseThrow(() -> new ParametreIntrouvableException(code));
    }

    /**
     * Modifie la valeur d'un parametre modifiable.
     *
     * @throws ParametreNonModifiableException code hors de {@link #CODES_MODIFIABLES}
     * @throws ParametreIntrouvableException aucune ligne ne porte ce code
     * @throws ValeurParametreInvalideException format invalide pour ce code
     * @throws AgentNonHabiliteException aucun profil local ouvert pour l'appelant
     * @throws ServiceIdentiteIndisponibleException service Identite injoignable
     */
    @Transactional
    public ParametreSysteme modifier(String code, String nouvelleValeur, String enteteAutorisation,
            String adresseIp) {

        if (!CODES_MODIFIABLES.contains(code)) {
            throw new ParametreNonModifiableException(code);
        }

        ParametreSysteme parametre = parametreSystemeRepository.findByCode(code)
                .orElseThrow(() -> new ParametreIntrouvableException(code));

        // Format avant reseau : une valeur illisible se refuse sans appeler
        // Identite (meme ordre que RetourService pour le motif, RG-10).
        String valeurValidee = valeurValidee(code, nouvelleValeur);

        ActeurSignataire acteur = profilOuRefus(enteteAutorisation);

        String valeurAvant = parametre.getValeur();
        parametre.changerValeur(valeurValidee);
        ParametreSysteme enregistre = parametreSystemeRepository.save(parametre);

        publierModification(enregistre, acteur, valeurAvant, adresseIp);

        return enregistre;
    }

    // --- Validation de forme, par code -------------------------------------------

    private String valeurValidee(String code, String valeur) {
        String texte = valeur == null ? "" : valeur.strip();

        if (texte.isEmpty()) {
            throw new ValeurParametreInvalideException(
                    "La valeur du parametre " + code + " ne peut pas etre vide.");
        }

        boolean estEntier = code.equals(SeuilService.CODE_SEUIL_AIGUILLAGE)
                || code.equals(FonctionnaliteService.CODE_DELAI_REGULARISATION_JOURS);

        if (estEntier) {
            long entier;
            try {
                entier = Long.parseLong(texte);
            } catch (NumberFormatException conversionImpossible) {
                throw new ValeurParametreInvalideException(
                        "La valeur « " + texte + "» du parametre " + code + " n'est pas un nombre "
                                + "entier exploitable. Attendu : des chiffres uniquement, sans "
                                + "espace, sans separateur de milliers et sans decimale.");
            }
            if (entier < 0) {
                throw new ValeurParametreInvalideException(
                        "La valeur du parametre " + code + " ne peut pas etre negative ("
                                + entier + " demande).");
            }
        }

        // COMPTE_CHARGE_RATIONS : texte libre, non vide -- c'est un numero de
        // compte du plan comptable, pas necessairement numerique.
        return texte;
    }

    // --- Acteur -------------------------------------------------------------------

    private ActeurSignataire profilOuRefus(String enteteAutorisation) {
        ResultatProfil resultat = profilClient.obtenir(enteteAutorisation);

        return switch (resultat) {
            case ResultatProfil.ProfilObtenu obtenu -> obtenu.acteur();
            case ResultatProfil.ProfilAbsent refus -> throw new AgentNonHabiliteException(
                    "La modification d'un parametre systeme exige un profil ouvert dans le "
                            + "module : " + refus.motif() + ".");
            case ResultatProfil.ServiceIdentiteIndisponible panne ->
                    throw new ServiceIdentiteIndisponibleException(
                            "Le service Identite est momentanement indisponible : la modification "
                                    + "est refusee par precaution (" + panne.motifTechnique()
                                    + "). Rien n'a ete modifie. Reessayez dans un instant.");
        };
    }

    // --- Audit ----------------------------------------------------------------------

    /**
     * Trace la modification avec l'ancienne et la nouvelle valeur. C'est le
     * manque que {@code docs/points-en-attente.md} signalait explicitement pour
     * {@code SEUIL_AIGUILLAGE_DR} avant cet ajout : un controle interne qui
     * voudrait savoir qui a change une valeur qui commande le niveau
     * d'approbation de la banque, et quand, le trouve desormais ici.
     */
    private void publierModification(ParametreSysteme parametre, ActeurSignataire acteur,
            String valeurAvant, String adresseIp) {

        publicateurAudit.publier(EvenementAudit.de(
                acteur.id(),
                ACTION_MODIFICATION_PARAMETRE,
                ENTITE_CIBLE,
                parametre.getId(),
                adresseIp,
                DeltaAudit.nouveau()
                        .champ("valeur", valeurAvant, parametre.getValeur())
                        .contexte("code", parametre.getCode())
                        .contexte("auteur", acteur.login())
                        .contexte("role", acteur.role())
                        .enJson()));
    }

}
