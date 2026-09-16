# Résumé Sprint 7F.1 — Socle et composants de base (frontend)

**Date :** 16 septembre 2026
**Objet du guide :** poser les composants d'interface réutilisables, adaptés
depuis le frontend de référence DOTTEL, typés en TypeScript strict
**Ce qui a réellement été fait :** l'intégralité du guide (5 étapes), plus la
correction de deux écarts constatés en cours de route sur le socle existant
(mode strict TypeScript absent, composants d'abord écrits au mauvais endroit)

---

## En une phrase

Le frontend dispose désormais d'une boîte à outils de composants typés
(bouton, champs de formulaire, sélecteur de période, tableau générique, carte,
alerte, modale, liste vide, badge de statut, affichage d'erreur) inspirée de
DOTTEL mais adaptée aux écarts réels de ce module — aucun écran fonctionnel
n'est branché dessus, c'est l'objet des sous-sprints suivants.

---

## Ce qui a été vérifié avant tout codage

**État réel des deux dépôts, pas mémoire.** Le guide lui-même signalait qu'une
version antérieure décrivait un DOTTEL obsolète (simulation Keycloak par route
de login) ; l'inspection du 16 septembre a confirmé que DOTTEL utilise
désormais un vrai Keycloak (PKCE écrit à la main), et que ce projet en a un
second, indépendant (`keycloak-js`) — les deux fonctionnels, aucun à remplacer.

**Format de pagination et format d'erreur vérifiés dans le code backend**,
pas supposés : `PageResponse.java` (service-identite) et `ErreurApiDto.java`
(service-workflow) lus directement avant de typer `Tableau` et
`AffichageErreur`. Les codes d'erreur de la table de correspondance
(`DOUBLON_LIGNE`, `DOUBLON_INTER_ETATS`, `GRILLE_INDISPONIBLE`,
`MOTIF_OBLIGATOIRE`, `SEPARATION_TACHES`, `ETAT_NON_MODIFIABLE`) sont les
chaînes exactes retournées par `GestionnaireErreursApi` des services Saisie et
Workflow, pas des noms reconstitués depuis CLAUDE.md.

---

## Arbitrages tranchés avec l'utilisateur

### 1. Dépendances : 4 ajoutées, 2 écartées

`class-variance-authority`, `lucide-react`, `clsx`, `tailwind-merge` ajoutées
(nécessaires aux composants repris). `@radix-ui/react-label` et
`@radix-ui/react-checkbox` **volontairement écartées** : aucun composant de ce
sprint n'en a besoin (le Label natif suffit, aucune case à cocher n'est dans
le périmètre). À reposer si un sous-sprint futur introduit une case à cocher.

### 2. Police : Source Sans 3 reprise de DOTTEL

Le motif juridique qui a fait écarter Bookman Old Style pour le PDF (Sprint
4.2 — police Monotype sous licence, non redistribuable) s'applique à
l'identique au web. Source Sans 3 (licence SIL Open Font Licence, déjà
vérifiée par DOTTEL) est reprise : fichiers `.woff2` copiés depuis DOTTEL
(lecture chez DOTTEL, écriture uniquement dans ce projet), déclarée dans
`index.css`.

### 3. Palette de statuts : 4 variantes, rouge réservé à 3 cas négatifs

Proposée et validée : `neutre` (gris), `attente` (ambre), `positif`
(émeraude), `negatif` (rouge — réservé aux 3 seuls cas qui appellent une
action corrective : `RETOURNE`, `REJETEE`, `REJETE`). Conforme à la charte,
qui réserve le rouge aux accents.

---

## Écarts constatés et corrigés en cours de sprint

**`tsconfig.app.json` ne déclarait pas `strict: true`** depuis le Sprint 0.3,
en contradiction avec CLAUDE.md section 12 (« typage strict, pas de `any` »).
Corrigé : tout le code de ce sprint compile en mode strict, vérifié par un
`tsc -b --force` complet (cache incrémental vidé), 0 erreur.

**Erreur d'emplacement corrigée avant le commit.** Les 16 composants ont
d'abord été écrits dans `components/ui/`, par calque réflexe sur la structure
de DOTTEL. Le dossier réservé par ce projet depuis le Sprint 0.3 est
`components/communs/` (visible dans l'état des lieux même du guide). Les
fichiers ont été déplacés avant tout commit ; aucune trace de `components/ui/`
ne subsiste.

**Décisions consignées** dans
`docs/decisions/2026-09-16-socle-frontend-composants-de-base.md` pour que les
sous-sprints 7F suivants réutilisent ces conventions sans les redécouvrir :
emplacement des composants, dépendances retenues/écartées, police, échelle de
couleurs, mode strict, palette de badges, format de pagination réel, table de
correspondance des erreurs.

---

## Ce qui a été livré, étape par étape

| Étape | Contenu |
| --- | --- |
| 1 | Inventaire des 16 composants `ui/` de DOTTEL, verdict par composant (reprendre en typant / adapter / écarter ce sprint), 2 arbitrages tranchés |
| 2 | `ChampTexte` (champ de saisie + sélecteur de date), `ChampListe` (liste déroulante), `ChampMontant` (toujours lecture seule, prop non exposée pour le garantir structurellement), `SelecteurPeriode` (deux bornes indépendantes, aucune durée imposée) |
| 3 | `Tableau` générique, signature présentée et validée avant implémentation, typé sur le format réel `PageResponse<T>` (pas la forme ad hoc de DOTTEL) |
| 4 | `AffichageErreur` + table `LIBELLES_ERREUR` extensible, `DOUBLON_LIGNE` et `DOUBLON_INTER_ETATS` distingués, `manques` (422 `ETAT_INCOMPLET`) rendu en liste |
| 5 | `StatutIntegrationEnum` ajoutée à `enums.ts` (statut nul explicitement géré, « Non transmis »), `Badge` + 3 composants dérivés (`BadgeStatutProcessus`, `BadgeStatutGrille`, `BadgeStatutIntegration`), palette présentée et validée |

Composants support ajoutés en cours de route, nécessaires aux objectifs
« modale », « alerte », « liste vide » du guide mais sans étape numérotée
dédiée : `Card`, `Alert`/`AlertDescription`, `Modale` (piège de focus
accessible via `useFocusTrap`), `MessageListeVide`, `Label`, `Button`,
`Input`, `Select`.

**Écartés délibérément de ce sprint** (verdicts justifiés dans l'inventaire
de l'Étape 1) : `Textarea` (motif de retour/rejet, hors périmètre — sprint
Workflow/Grilles), `VoirMotifModal` (composant métier spécifique DOTTEL),
`Checkbox` (aucun besoin identifié), `LienRetour` et `Logo` (relèvent du
layout, Sprint 7F.2).

---

## Vérification des critères de validation du guide

| Critère | Statut |
| --- | --- |
| Inventaire DOTTEL réalisé, verdicts justifiés | ✅ Fait |
| Composants de formulaire typés, variante montant en lecture seule | ✅ Fait |
| Tableau générique consommant le format de pagination du backend | ✅ Vérifié |
| Affichage d'erreur avec messages en français | ✅ Vérifié |
| `StatutIntegrationEnum` ajoutée, statut nul géré | ✅ Fait |
| Badge couvrant les statuts des trois énumérations | ✅ Vérifié |
| Arbitrages dépendances et police présentés et tranchés | ✅ Fait |
| `DOUBLON_LIGNE` et `DOUBLON_INTER_ETATS` distingués | ✅ Vérifié |
| Sélecteur de période à deux bornes, sans durée imposée | ✅ Fait |
| Aucune écriture dans le dépôt DOTTEL | ✅ Vérifié (git status DOTTEL inspecté) |
| Palette de statuts sobre, conforme à la charte | ✅ Fait |
| Aucun fichier `.jsx`, aucun `any` | ✅ Vérifié |
| `npm run build` réussi | ✅ Vérifié (+ `tsc -b --force` complet, 0 erreur) |

---

## Fichiers créés ou modifiés

**Configuration / thème** : `package.json` (+4 dépendances), `tsconfig.app.json`
(`strict: true`), `index.css` (police + échelle `primary-*`/`neutral-*`).

**Composants** (`src/components/communs/`) : `Button`, `Label`, `Input`,
`Select`, `Card`, `Alert`, `Modale`, `MessageListeVide`, `ChampTexte`,
`ChampListe`, `ChampMontant`, `SelecteurPeriode`, `Tableau`, `Badge`,
`AffichageErreur`.

**Support** : `src/hooks/useFocusTrap.ts`, `src/utils/cn.ts`,
`src/utils/messagesErreur.ts`, `src/types/pagination.ts`.

**Modifiés** : `src/types/enums.ts` (`StatutIntegrationEnum`,
`CodeManqueEnum`), `src/api/apiClient.ts` (`ApiErrorResponse.manques`,
`ManqueCompletude`).

**Assets** : `src/assets/fonts/` (4 fichiers `.woff2` + licence, copiés de
DOTTEL).

**Documentation** :
`docs/decisions/2026-09-16-socle-frontend-composants-de-base.md`.

---

## Prochaine étape

Sprint 7F.2 : layout global et navigation (`AppLayout`, `Sidebar`,
`PageHeader`), première utilisation réelle des composants de ce sprint.
