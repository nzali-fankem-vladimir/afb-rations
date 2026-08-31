package cm.afrilandfirstbank.rations.saisie.application;

import cm.afrilandfirstbank.rations.saisie.domaine.Beneficiaire;
import cm.afrilandfirstbank.rations.saisie.domaine.LignePrestation;

/**
 * Une ligne de prestation et le beneficiaire qu'elle designe, rapproches pour la
 * couche api.
 *
 * <p><b>Pourquoi ce type existe.</b> {@code LignePrestation} ne porte qu'un
 * {@code idBeneficiaire}, sans association JPA : c'est un choix delibere du
 * Sprint 3.1, la table etant celle de fort volume ou RG-04 et RG-15 comparent des
 * identifiants sans jamais naviguer vers le beneficiaire
 * ({@code docs/decisions/2026-08-28-identifiants-plats-dans-service-saisie.md}).
 * L'api, elle, doit afficher un nom.
 *
 * <p>Le rapprochement est donc explicite, fait une fois pour toute une fiche
 * (une requete pour les lignes, une pour les beneficiaires), plutot que par un
 * chargement transitif qui produirait une requete par ligne.
 *
 * <p>Ce record vit dans la couche {@code application} et non dans {@code api} :
 * il porte des entites du domaine, pas des champs de reponse HTTP. C'est
 * {@code LigneResponse.depuis(...)} qui franchit la frontiere.
 */
public record LigneAvecBeneficiaire(LignePrestation ligne, Beneficiaire beneficiaire) {
}
