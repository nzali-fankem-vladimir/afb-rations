# `idUtilisateur` reste nul dans les traces d'audit du service Saisie

**Sprint 3.3 — 31 août 2026** · Décision tranchée avec l'utilisateur, étape 4.

**Statut :** appliquée. `adresseIp` est renseignée, `idUtilisateur` reste nul —
délibérément, pas par oubli.

---

## 1. Le point soulevé

Le résumé du Sprint 3.2 notait, en « Suite » : le Sprint 3.3 devrait renseigner
`idUtilisateur` et `adresseIp` dans les traces d'audit du service Saisie, nuls
jusque-là faute de contexte HTTP.

Avec les cinq endpoints écrits, le contexte HTTP existe. `adresseIp` est
renseignée sans coût (`HttpServletRequest.getRemoteAddr()`). `idUtilisateur`
pose un problème différent.

## 2. Pourquoi `idUtilisateur` ne peut pas être résolu sans coût

Le seul appel d'habilitation du service Saisie est
`GET /identite/habilitation?codeUnite=...` (Sprint 1.3). Sa réponse
(`docs/appel-habilitation.md` §1) porte `login`, `role`, `codeUniteDemande`,
`autorise`, `porteeNationale` — **aucun identifiant numérique**. Vérifié côté
serveur : `ResultatHabilitation` (service Identité) ne porte pas non plus
d'`id`.

Résoudre `idUtilisateur` exigerait un appel supplémentaire à
`GET /identite/moi`, qui le rend. Ce serait un **quatrième appel synchrone** sur
le chemin d'écriture d'une ligne, qui en empile déjà trois — Grilles, Workflow,
Identité-habilitation (`docs/rattachement-processus.md` §4, déjà signalé comme
« le point du module le plus sensible à une panne »).

**La différence avec le service Grilles.** `ClientIdentite.resoudreAuteur` y
fait déjà cet appel — mais pour un besoin métier antérieur à l'audit :
`grille_tarifaire.id_createur` est `NOT NULL` (Sprint 2.2). L'identifiant est un
sous-produit d'un appel de toute façon nécessaire. Rien de comparable n'existe
côté Saisie : `ligne_prestation` et `fiche_journaliere` ne portent **aucune**
colonne d'auteur au dictionnaire (CLAUDE.md §4). Ajouter l'appel ici le ferait
exister uniquement pour l'audit.

## 3. La décision

**`idUtilisateur` reste nul** dans les cinq traces d'audit du sprint
(`OUVERTURE_FICHE_JOURNALIERE`, `CREATION_LIGNE_PRESTATION`,
`MODIFICATION_LIGNE_PRESTATION`, `SUPPRESSION_LIGNE_PRESTATION`, et
`ACCES_REFUSE` côté `GestionnaireErreursApi`).

**`adresseIp` est renseignée** partout où le contrôleur la connaît.

Le login reste néanmoins traçable dans le cas du refus : `GestionnaireErreursApi`
le lit directement du jeton (`SecurityContextHolder`, sans appel réseau,
comme côté Grilles) et le place en contexte du delta. Pour les traces de succès
(création, modification, suppression, ouverture), il n'est pas repris : ce
service ne détient le login que via `AgentHabilite.login()`, produit à
l'intérieur d'`EtatModifiableService` et non remonté à l'appelant — l'ajouter
demanderait de faire transiter cette valeur à travers `FicheJournaliereService`
et `LigneService`, pour un gain d'attribution jugé secondaire au regard du coût
de complexité. Point réouvrable si l'exploitation constate qu'une trace de
création sans auteur nommé est gênante en pratique.

## 4. Reconsidérer plus tard, pas maintenant

Si un besoin métier venait un jour à exiger un identifiant d'auteur sur
`ligne_prestation` ou `fiche_journaliere` (colonne `id_agent`, par exemple, sur
le modèle de `id_createur` chez Grilles), l'appel à `GET /identite/moi`
deviendrait un sous-produit nécessaire, comme chez Grilles, et
`idUtilisateur` se résoudrait sans coût additionnel dédié à l'audit. Tant que ce
besoin métier n'existe pas, l'ajouter uniquement pour l'audit inverserait la
hiérarchie : coder une dépendance réseau pour la traçabilité d'un service déjà
identifié comme point sensible, sans que rien dans le schéma ne l'exige.
