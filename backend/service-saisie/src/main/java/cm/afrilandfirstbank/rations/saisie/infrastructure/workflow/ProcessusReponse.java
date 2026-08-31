package cm.afrilandfirstbank.rations.saisie.infrastructure.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Corps attendu de {@code GET /processus/{id}} (service Workflow).
 *
 * <p><b>Contrat ecrit, non verifie.</b> Le service Workflow n'existe pas avant le
 * Sprint 4 : ces noms de champs sont repris du contrat d'API section 5, ou
 * {@code POST /processus} recoit {@code moisPaiement}, {@code anneePaiement} et
 * {@code codeUnite}, et ou l'exemple de validation rend {@code idProcessus} et
 * {@code statut}. Le Sprint 4 doit s'y conformer ou ce client cassera —
 * exactement le genre de defaut qui « marche en test » et se decouvre en
 * integration ({@code docs/rattachement-processus.md} section 6).
 *
 * <p><b>Tolerant reader.</b> {@link JsonIgnoreProperties} laisse le service
 * Workflow ajouter des champs (montant total, type de processus, statut
 * d'integration comptable) sans casser la Saisie : elle ne lit que les cinq
 * qui la concernent. Tolerance a la LECTURE uniquement — un champ manquant ou un
 * statut inconnu produit un refus, jamais une valeur par defaut permissive.
 *
 * <p>Types boites ({@code Long}, {@code Integer}) et non primitifs : un champ
 * absent doit se lire {@code null} et faire refuser, non se lire {@code 0} et
 * passer pour une valeur.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcessusReponse(
        Long idProcessus,
        String statut,
        String codeUnite,
        Integer moisPaiement,
        Integer anneePaiement) {
}
