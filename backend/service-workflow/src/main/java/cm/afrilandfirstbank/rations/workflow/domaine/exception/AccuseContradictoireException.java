package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Un accuse comptable contredit un statut d'integration deja recu. Rendue en
 * {@code 409 ACCUSE_CONTRADICTOIRE} (Sprint 5.2).
 *
 * <p><b>{@code 409} et non {@code 422}</b>, contrairement a
 * {@link ProcessusNonTransmisException} : il y a bien ici <b>deux affirmations
 * concurrentes sur la meme ressource</b>, et c'est la definition d'un conflit. La
 * distinction est celle du Sprint 4.1 entre {@code PROCESSUS_EXISTANT} ({@code 409},
 * quelque chose est duplique) et {@code FONCTIONNALITE_NON_OUVERTE} ({@code 422}, une
 * regle refuse).
 *
 * <p><b>Le module refuse et signale, il n'arbitre jamais</b> — meme doctrine
 * qu'{@code INCOHERENCE_GRILLE} au Sprint 2.4. Il n'a aucun moyen de savoir lequel des
 * deux accuses dit vrai ; ecraser en silence ferait passer un etat paye pour rejete, ou
 * l'inverse, sans que personne ne le voie. Rien n'est ecrit, et le service Transmission
 * trace le refus sous le prefixe {@code ACCUSE CONTRADICTOIRE}.
 */
public class AccuseContradictoireException extends RuntimeException {

    public AccuseContradictoireException(String message) {
        super(message);
    }

}
