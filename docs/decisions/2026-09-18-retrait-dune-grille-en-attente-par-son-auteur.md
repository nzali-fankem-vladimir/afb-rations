# Retrait d'une grille en attente par son propre auteur

**Date :** 18 septembre 2026
**Sprint :** rattrapage post-7F.6, demande n°7 de la vérification visuelle
(« l'ARH doit pouvoir modifier sa grille en attente »)
**Statut :** implémenté, sous une forme différente de la demande littérale —
voir arbitrage ci-dessous.

## Ce qui a été demandé, et ce qui a été construit

La demande initiale parlait de « modifier » une grille `EN_ATTENTE_DRH`.
**Aucun endpoint de ce module ne réécrit une grille en place** : la décision
du Sprint 2.2 (« modifier une grille ACTIVE crée une nouvelle ligne, jamais
une mise à jour en place ») s'étend ici par le même raisonnement, même si la
grille visée n'est pas encore `ACTIVE` — une réécriture en place effacerait la
proposition d'origine sans laisser de trace exacte dans l'historique.

**Retenu : retirer, puis reproposer.** Nouvel endpoint
`POST /grilles/{id}/retrait`, réservé à l'Analyste RH auteur de la
proposition précise. Il passe la grille au statut `REJETEE` — même mécanique
qu'un rejet de la Directrice RH (`TransitionGrille.rejeter`, motif obligatoire
RG-10) — puis l'Analyste RH soumet une nouvelle proposition corrigée par le
chemin déjà existant, `POST /grilles`.

## Pourquoi réutiliser le statut REJETEE plutôt qu'en ajouter un

`StatutGrilleEnum` est fixé par CLAUDE.md §5 à quatre valeurs
(`BROUILLON | EN_ATTENTE_DRH | ACTIVE | REJETEE`). Un retrait et un rejet
produisent la même conséquence observable pour les saisies et pour le
couple (nature, session) : cette proposition précise ne deviendra jamais
active. Les deux se distinguent déjà dans le journal d'audit, par l'action
(`RETRAIT_GRILLE` contre `REJET_GRILLE`) et par l'auteur de la décision — qui
est ici le créateur lui-même. Ajouter un cinquième statut pour ce seul écart
aurait touché l'énumération partagée, `BadgeStatutGrille` côté frontend, et
tout code qui commute déjà sur les quatre valeurs existantes : un coût
disproportionné à ce que la distinction apporterait réellement.

## Vérification de propriété : un troisième code de refus en 403

Seul l'auteur de la proposition peut la retirer, vérifié par comparaison
d'identifiants (`id_createur`), jamais par le libellé affiché — deux
Analystes RH pourraient porter un nom proche. Nouvelle exception
`GrilleNonProprietaireException` → `403 GRILLE_NON_PROPRIETAIRE`, distincte de
`ACCES_REFUSE` (rôle insuffisant) et de `UTILISATEUR_NON_HABILITE` (aucun
profil local) : même doctrine que `SEPARATION_TACHES` côté service Workflow
(RG-12) — un rôle correct sur une ressource qui n'appartient pas à
l'appelant est un troisième geste, pas l'un des deux premiers.

La propriété est vérifiée **avant** la transition de statut : un Analyste RH
qui n'est pas l'auteur voit un refus qui nomme précisément ce qui cloche,
plutôt qu'un refus de transition qui ne le concerne pas.

## Frontend

`api/grillesApi.ts#retirerGrille`, `pages/grilles/RetraitGrilleModale.tsx`
(même gabarit que `RejetGrilleModale`, motif obligatoire), bouton « Retirer ma
proposition » sur `GrillesTarifairesPage`, dans le bandeau de la proposition
en attente, visible pour le rôle ARH. La propriété n'est pas vérifiée côté
écran — le libellé affiché n'étant pas une identité fiable — le serveur
tranche et l'écran affiche son refus le cas échéant.

## Contrôles

`mvn -pl service-grilles clean test` : 104 tests, dont les 5 nouveaux de
`RetraitGrilleServiceTest` (nominal, non-auteur, grille introuvable, déjà
tranchée par la DRH, motif vide) tous verts. **Deux échecs préexistants et
non liés** dans `GrilleTarifaireRepositoryTest` : la base de développement
réelle porte désormais des données créées lors de vérifications manuelles
antérieures (une grille RATION/JOUR a été réellement validée pendant une
session de test, fermant la grille de référence du Sprint 0.5) — ce test
suppose l'état pristine du jeu de données V2 et casse dès que la base réelle
diverge, indépendamment de ce changement. `tsc -b --force`, `oxlint`,
`npm run build` : 0 erreur côté frontend.
