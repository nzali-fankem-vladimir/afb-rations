package cm.afrilandfirstbank.rations.transmission.application;

import cm.afrilandfirstbank.rations.transmission.domaine.EtatValideEvent;

/**
 * Port sortant : « mets cet etat valide a la disposition de la comptabilite ».
 *
 * <p><b>Pourquoi une interface</b>, alors que le producteur Kafka est deja isole dans
 * l'infrastructure : pour que l'orchestration de la transmission — l'ordre des
 * controles, le moment ou le drapeau de RG-13 est pose — soit eprouvable sans broker.
 * Meme justification qu'aux Sprints 3.2 et 3.4 pour les clients HTTP.
 *
 * <p><b>La publication est attendue.</b> L'implementation ne rend la main qu'une fois
 * le broker ayant accuse reception, ou le delai depasse. C'est l'inverse exact de la
 * publication d'audit, qui part sans attendre : ici, le resultat commande l'ecriture
 * du drapeau {@code transmis_comptabilite}, et un drapeau pose sans certitude
 * produirait un etat fige, repute transmis et jamais paye.
 */
public interface PublicateurEtatValide {

    /**
     * @param charge charge deja controlee par {@link ConstructionChargeService} —
     *        publier une charge non controlee n'a aucun sens : rien ne rattrape un
     *        evenement parti
     * @return l'une des deux issues, jamais {@code null} et jamais une exception
     */
    ResultatPublication publier(EtatValideEvent charge);

}
