package cm.afrilandfirstbank.rations.saisie.application;

import java.time.LocalDate;
import cm.afrilandfirstbank.rations.saisie.domaine.StatutProcessusEnum;

/**
 * Les trois issues d'une verification de processus aupres du service Workflow.
 *
 * <p><b>Type scelle, sur le modele de {@code ResultatResolutionMontant}</b>
 * (Sprint 3.2, decision 1). Le motif y etait qu'un enregistrement plat a drapeau
 * rendait ecrivable le repli qui ruine la regle. Il vaut ici a l'identique :
 * seul {@link ProcessusVerifie} porte un statut et un code unite, et
 * {@code statut} y est un champ non nul. Le repli
 * {@code statut != null ? statut : EN_COURS_SAISIE} — celui qui laisserait
 * ecrire sur un etat cloture pendant une panne — n'est pas ecrivable.
 *
 * <p>Le {@code switch} des appelants est exhaustif sans {@code default} : un
 * quatrieme cas ferait echouer leur compilation, plutot que de tomber
 * silencieusement dans une branche fourre-tout sur la regle qui protege un etat
 * deja valide.
 */
public sealed interface ResultatVerificationProcessus {

    /**
     * Le service Workflow a repondu {@code 200}. Porte tout ce dont la Saisie a
     * besoin, obtenu en UN SEUL appel : le {@code statut} pour savoir si
     * l'ecriture est permise, et le triplet unite / debut / fin de periode pour la portee
     * d'acces (RG-12) et pour la recopie figee sur la fiche
     * ({@code docs/rattachement-processus.md} section 5).
     *
     * @param codeUnite unite qui supporte la charge — ligne de DEBIT, a ne
     *        jamais confondre avec le code agence du beneficiaire
     */
    record ProcessusVerifie(long idProcessus,
                            StatutProcessusEnum statut,
                            String codeUnite,
                            LocalDate dateDebut,
                            LocalDate dateFin) implements ResultatVerificationProcessus {

        public boolean estModifiable() {
            return statut.estModifiable();
        }
    }

    /** Workflow a repondu, et sa reponse est que ce processus n'existe pas. */
    record ProcessusIntrouvable(long idProcessus) implements ResultatVerificationProcessus {
    }

    /**
     * Workflow n'a rien repondu d'exploitable : panne, delai, corps illisible,
     * ou statut inconnu du present service. Refus conservateur.
     */
    record ServiceWorkflowIndisponible(String motifTechnique)
            implements ResultatVerificationProcessus {
    }

}
