package cm.afrilandfirstbank.rations.saisie.domaine.exception;

/**
 * L'etat mensuel n'est plus modifiable : le processus est soumis, en cours de
 * validation, ou cloture. Aucune ligne ne peut y etre ajoutee, modifiee ou
 * supprimee.
 *
 * <p><b>Traduite en {@code 422 ETAT_NON_MODIFIABLE}</b>, et non en {@code 409}.
 * Le contrat d'API section 1.3 range « transition non permise » sous {@code 409},
 * mais le Sprint 2.3 a deja ecarte cette lecture pour
 * {@code TRANSITION_INTERDITE} : rien n'est duplique, aucune contrainte
 * d'unicite n'est violee — c'est une regle de gestion qui refuse. Retenir
 * {@code 409} ici ferait repondre differemment deux services du meme module a la
 * meme situation. Ecart consigne dans
 * {@code docs/decisions/2026-08-31-code-http-du-refus-sur-etat-non-modifiable.md}
 * pour que le contrat d'API soit corrige, plutot que contredit en silence.
 *
 * <p><b>Pourquoi le controle a lieu a CHAQUE ecriture</b>, et non une fois a
 * l'ouverture de la fiche : {@code POST /saisie/lignes} recoit
 * {@code idFicheJournaliere} directement, rien n'oblige un client a repasser par
 * {@code POST /saisie/fiches}. Un controle limite a l'ouverture laisserait une
 * fiche ouverte le 15 ecrivable le 20, y compris apres la soumission de l'etat
 * au Chef d'Unite — et le montant valide par celui-ci ne serait plus celui qui
 * part en comptabilite ({@code docs/rattachement-processus.md} section 4).
 *
 * <p>Le statut n'est jamais mis en cache : il est mutable, c'est exactement
 * pourquoi il est redemande.
 */
public class EtatNonModifiableException extends RuntimeException {

    public EtatNonModifiableException(String message) {
        super(message);
    }

}
