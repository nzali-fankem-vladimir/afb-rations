# Socle frontend : conventions posées au Sprint 7F.1

**Date :** 16 septembre 2026
**Sprint :** 7F.1, socle et composants de base
**Statut :** appliqué, à respecter par tous les sous-sprints 7F suivants

---

## 1. Emplacement des composants : `components/communs`, pas `components/ui`

DOTTEL range ses composants de base dans `src/components/ui/`. Ce projet a
réservé `src/components/communs/` dès le Sprint 0.3 (état des lieux vérifié en
tête du guide 7F.1). Les 16 fichiers de ce sprint (Button, Input, Label,
ChampTexte, ChampListe, ChampMontant, SelecteurPeriode, Tableau, Card, Alert,
Modale, MessageListeVide, Badge, AffichageErreur) vivent donc dans
`components/communs/`, jamais dans un dossier `ui/` recréé par calque de
DOTTEL. Erreur commise puis corrigée en cours de sprint (fichiers écrits une
première fois dans `components/ui/`, déplacés avant le commit) : **la
convention de nommage d'un projet source ne l'emporte jamais sur celle,
antérieure, du projet cible.**

## 2. Dépendances : quatre ajoutées, deux volontairement écartées

Ajoutées : `class-variance-authority`, `lucide-react`, `clsx`,
`tailwind-merge`. Nécessaires aux composants repris (variantes de Button/
Badge/Alert, icônes, fusion de classes Tailwind).

**Écartées à ce sprint** : `@radix-ui/react-label` (un `<label htmlFor>` natif
suffit, aucune composition avancée requise) et `@radix-ui/react-checkbox`
(aucune case à cocher dans le périmètre 7F.1). **Si un sous-sprint futur a
besoin d'une case à cocher** (candidat le plus probable : sélection multiple
en Reporting ou en Audit), l'arbitrage Radix est à reposer à ce moment, pas à
réinstaller par réflexe de calque sur DOTTEL.

## 3. Police : Source Sans 3 reprise de DOTTEL

Fichiers (`.woff2`, 4 graisses, licence SIL Open Font) copiés depuis
`afb-dottel-mm/frontend/src/assets/fonts/` vers ce projet — une lecture chez
DOTTEL suivie d'une écriture ici, aucune écriture dans le dépôt source. Motif :
Bookman Old Style (charte §8.2) est une police Monotype sous licence
commerciale, déjà écartée pour le PDF au Sprint 4.2 pour ce même motif — il
vaut pour le web. `@font-face` et `--font-sans` déclarés dans `index.css`,
appliqués sur `body`.

## 4. Thème Tailwind étendu : échelle `primary-*` / `neutral-*`

`index.css` ne portait que trois couleurs nommées (`rouge-principal`,
`rouge-secondaire`, `noir-charte`, Sprint 0.3), insuffisant pour les
variantes de composants (`bg-primary-500`, `border-neutral-500`, etc.).
L'échelle complète (`primary-50` à `950`, `neutral-50` à `950`) est reprise
telle quelle de DOTTEL : `primary-500` vaut `#E30613`, identique à
`rouge-principal` — même rouge institutionnel AFB, même programme. Les trois
tokens historiques sont conservés tels quels, sans renommage.

## 5. `tsconfig.app.json` : `strict: true` activé

Absent depuis le Sprint 0.3 malgré CLAUDE.md section 12 (« typage strict, pas
de `any` »). Corrigé ce sprint. **Tout code frontend écrit après le 16
septembre 2026 est donc vérifié en mode strict** ; un sous-sprint qui verrait
apparaître de nouvelles erreurs de compilation sur du code plus ancien
touchera probablement ce changement, pas une régression de ce sprint.

## 6. Palette de badges : 4 variantes seulement, rouge réservé aux 3 cas négatifs

`components/communs/Badge.tsx` expose une primitive à 4 variantes
(`neutre`/`attente`/`positif`/`negatif`) et trois composants dérivés
(`BadgeStatutProcessus`, `BadgeStatutGrille`, `BadgeStatutIntegration`) qui
portent chacun leur propre table de correspondance statut → variante + libellé
français. **Tout écran affichant un statut de processus, de grille ou
d'intégration comptable doit réutiliser ces trois composants**, jamais
recréer un badge ad hoc ou une 5ᵉ couleur — la charte réserve le rouge aux
accents, et la palette a été proposée et validée sur cette base.
`BadgeStatutIntegration` gère explicitement le cas `statut === null` (« Non
transmis ») : CLAUDE.md section 5 est clair, aucune valeur de
`StatutIntegrationEnum` ne représente « jamais transmis ».

## 7. Le tableau générique consomme le format réel de pagination, pas celui de DOTTEL

`src/types/pagination.ts` déclare `PageResponse<T>` sur le format exact du
backend (`content, page, size, totalElements, totalPages, dernierePage`,
Sprint 1.2) — **pas** la forme ad hoc de DOTTEL (`{ total, taille, page,
onChangerPage }`, qui recalculait `totalPages` côté front). Les sous-sprints
qui consomment une liste paginée (7F.6 suivi, 7F.7 reporting, 7F.9 audit,
grilles, utilisateurs) doivent typer leurs réponses d'API directement sur
`PageResponse<T>` et passer `page`, `totalPages`, `totalElements`,
`dernierePage` tels quels au `Tableau` — ne jamais recalculer `totalPages`
côté frontend, le backend le fait déjà.

## 8. Table de correspondance des codes d'erreur : extensible, un seul endroit

`src/utils/messagesErreur.ts` porte `LIBELLES_ERREUR`, volontairement limitée
aux codes déjà rencontrés (`DOUBLON_LIGNE`, `DOUBLON_INTER_ETATS`,
`GRILLE_INDISPONIBLE`, `MOTIF_OBLIGATOIRE`, `SEPARATION_TACHES`,
`ETAT_NON_MODIFIABLE`). **Chaque sous-sprint qui introduit un écran capable de
déclencher un nouveau code d'erreur doit l'ajouter à cette table**, jamais
créer une seconde table ailleurs. Le composant `AffichageErreur` affiche
toujours le libellé (titre court, généricité voulue) **et** le `message` du
backend tel quel en dessous — jamais l'un sans l'autre, en particulier pour
`DOUBLON_INTER_ETATS` (RG-15) dont le message nomme l'état en conflit.

---

**Références :** guide `sprint actuel.md` (7F.1), CLAUDE.md sections 2, 5, 11,
12, 15 ; `docs/decisions/2026-09-09-rythme-de-paiement-et-maille-de-la-periode.md`
(intervalle de dates, repris par `SelecteurPeriode`).
