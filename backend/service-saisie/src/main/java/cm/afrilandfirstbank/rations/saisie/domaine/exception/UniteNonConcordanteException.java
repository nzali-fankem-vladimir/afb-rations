package cm.afrilandfirstbank.rations.saisie.domaine.exception;

/**
 * Le code unite declare par l'appelant de la consolidation ne correspond pas a
 * celui recopie et fige sur les fiches du processus (migration V3).
 *
 * <p>Traduite en {@code 403 UNITE_NON_CONCORDANTE}, et <b>tracee en audit</b>
 * comme les autres refus d'acces.
 *
 * <h2>Pourquoi ce refus existe</h2>
 *
 * <p>{@code GET /saisie/processus/{id}/etat} recoit le code unite <b>en
 * parametre</b>, fourni par le service Workflow qui detient {@code
 * processus_mensuel} — et non lu sur les fiches, qui peuvent ne pas exister
 * encore (decision Sprint 3.4). C'est ce qui permet de verifier la portee
 * d'acces meme sur un etat vide.
 *
 * <p>Mais un parametre fourni par l'appelant ne peut pas etre cru sur parole.
 * Sans recoupement, un agent habilite sur {@code 00002} appellerait
 * {@code /saisie/processus/999/etat?codeUnite=00002} ou le processus 999
 * appartient en realite a {@code 00007} : il franchirait le controle
 * d'habilitation et recevrait les lignes d'une unite qui ne le regarde pas.
 *
 * <p>Le {@code code_unite} fige sur les fiches reste donc <b>l'autorite</b> ; le
 * parametre n'est qu'une <b>declaration a verifier</b>.
 *
 * <h2>Pourquoi 403 et non 422</h2>
 *
 * <p>Un desaccord peut venir d'un defaut du service appelant comme d'une
 * tentative de debordement de perimetre, et rien ne permet de les distinguer au
 * moment du refus. La doctrine du refus conservateur (Sprint 1.3) tranche :
 * refuser, et tracer. Un defaut reel de Workflow apparaitra bruyamment dans le
 * journal d'audit, avec un message nommant les deux unites.
 */
public class UniteNonConcordanteException extends RuntimeException {

    public UniteNonConcordanteException(String message) {
        super(message);
    }

}
