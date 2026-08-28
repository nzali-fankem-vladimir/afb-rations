package cm.afrilandfirstbank.rations.grilles.domaine.exception;

/**
 * Deux grilles ACTIVE couvrent la meme date pour un meme couple (nature,
 * session) : l'invariant de non-chevauchement est viole (Sprint 2.4).
 *
 * <p><b>Ce cas ne doit pas se produire.</b> Il est ecarte a l'ecriture par
 * l'index partiel {@code ux_grille_active_par_couple} et par la bascule atomique
 * du Sprint 2.3, qui ferme l'ancienne grille a la veille de la remplacante. Il
 * reste possible par une reprise de donnees ou une intervention directe en base.
 *
 * <p><b>Pourquoi lever plutot que choisir.</b> Le service pourrait retenir la
 * grille a la {@code dateDebut} la plus recente et poursuivre. Il servirait
 * alors un montant potentiellement faux que personne ne verrait passer — une
 * ligne de prestation serait figee, validee, puis transmise a la comptabilite au
 * mauvais tarif. Un refus visible vaut mieux qu'un montant plausible et faux
 * (RG-03).
 *
 * <p>Traduite par {@code GestionnaireErreursApi} en {@code 500} au format
 * d'erreur uniforme du projet, code {@code INCOHERENCE_GRILLE}, et tracee en log
 * au prefixe {@code INCOHERENCE GRILLE}. Laisser l'exception filer sans
 * l'intercepter produirait une reponse generique Spring Boot, hors du format
 * uniforme : on casserait le contrat d'erreur sans gagner la visibilite
 * recherchee.
 */
public class IncoherenceGrilleException extends RuntimeException {

    public IncoherenceGrilleException(String message) {
        super(message);
    }

}
