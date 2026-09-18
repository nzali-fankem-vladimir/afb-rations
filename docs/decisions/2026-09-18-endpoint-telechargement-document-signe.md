# Téléchargement du document PDF signé

**Date :** 18 septembre 2026
**Sprint :** rattrapage post-7F.6, demande n°8 de la vérification visuelle
**Statut :** implémenté. Ferme le point ouvert au Sprint 7F.5
(`docs/points-en-attente.md`, section « PDF signé — endpoint de téléchargement
reporté »).

## Décision

Nouvel endpoint `GET /processus/{id}/document`, service Workflow, réservé aux
trois rôles du circuit (`AGENT_UNITE`, `CHEF_UNITE_DA`, `DIRECTEUR_RESEAU_DR`) —
le minimum recommandé par le point ouvert. L'ARH et la DRH n'y ont pas accès :
aucun besoin exprimé, à rouvrir si le métier en formule un.

## Ce qui a été construit, contre les quatre exigences du point ouvert

| Exigence (point ouvert du 7F.5) | Réalisation |
| --- | --- |
| Endpoint côté service Workflow | `ProcessusController.telechargerDocument`, réutilise `StockageDocuments.lire` (déjà existant, Sprint 4.2) |
| Vérification de portée, unité par unité | `DocumentTelechargementService` réutilise `ProcessusService#consulter`, qui fait déjà ce contrôle — pas un second appel au service Identité |
| Événement d'audit obligatoire | `TELECHARGEMENT_DOCUMENT`, sur le modèle exact de `EXPORT_RAPPORT` (service Reporting, Sprint 6.3) : `idUtilisateur` laissé nul (même raisonnement — un appel supplémentaire au service Identité pour un geste de lecture) |
| Rôle des trois acteurs du circuit a minima | Fait, ARH/DRH non ouverts |

## Refus, un code par situation

- `404 PROCESSUS_INTROUVABLE` — aucun processus (relayé par `ProcessusService`).
- `404 PIECE_JOINTE_INTROUVABLE` — le processus existe, l'appelant y a portée,
  mais aucune pièce jointe n'a encore été produite (état jamais soumis).
  Nouvelle exception, distincte de `DOCUMENT_NON_PRODUIT` (500, panne
  d'écriture ou de relecture sur un fichier que la base atteste exister) :
  l'une est un état légitime du dossier, l'autre une panne d'infrastructure.
- `403 UTILISATEUR_NON_HABILITE` — hors portée, relayé.
- `500 DOCUMENT_NON_PRODUIT` — le stockage ne peut pas relire un fichier que
  la base atteste exister (code déjà existant, réutilisé tel quel).

## Frontend

`api/processusApi.ts#telechargerDocument` : requête `responseType: 'blob'`,
nom de fichier lu sur l'en-tête `Content-Disposition`. **Correction associée
dans `apiClient.ts`** : l'intercepteur d'erreur ne décodait pas les réponses
d'erreur d'un appel en `responseType: 'blob'` (elles arrivent elles-mêmes sous
forme de `Blob`, jamais de JSON déjà décodé). Sans cette correction,
`AffichageErreur` aurait reçu un `Blob` au lieu du format d'erreur uniforme,
sur ce nouvel endpoint et sur tout futur export binaire (rapports, Sprint
7F.7). `utils/declencherTelechargement.ts` porte le déclenchement du
téléchargement navigateur, réutilisable.

Bouton « Télécharger le document » sur `ExamenProcessusPage` (le cas exact du
point ouvert : relire le document avant un second visa) et sur
`ConsultationEtatTab` (l'agent relit son propre document soumis).

## Contrôles

`mvn -pl service-workflow clean test` : 363/363. `DocumentTelechargementServiceTest`
(5 cas) couvre le nominal, les trois refus (introuvable, hors portée, aucune
pièce jointe) et la panne de lecture. `tsc -b --force`, `oxlint`, `npm run build` :
0 erreur côté frontend.
