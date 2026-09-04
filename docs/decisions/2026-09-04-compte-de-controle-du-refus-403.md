# `pierre_belinga` n'est plus le compte de contrôle du refus 403

**Sprint 6.3 — 4 septembre 2026** · Collision relevée par l'utilisateur à la
relecture du sprint, corrigée immédiatement.

**Statut :** appliquée. Le compte de contrôle est désormais **`thomas_ndzana`**,
et une garde au build empêche que le cas se reproduise en silence.

---

## 1. Ce qui s'est passé

La vérification manuelle du Sprint 6.3 devait éprouver la nouvelle trace
`LIAISON_COMPTE_KEYCLOAK`, publiée à la première connexion d'un profil
pré-provisionné mais pas encore lié. Aucun des six profils du realm n'était dans
cet état — tous portaient déjà leur `sub_keycloak`.

`pierre_belinga` a été choisi parce qu'il existait à l'annuaire sans profil local.
Un profil lui a été ouvert en base, il s'est connecté, la liaison s'est établie,
et la trace a bien été publiée.

**C'était exactement le compte qu'il ne fallait pas toucher.**

## 2. Ce que ce compte servait, et qui était écrit noir sur blanc

`infra/keycloak/README.md`, tableau des comptes de test :

```
| pierre_belinga | AGENT_UNITE | **aucun** — compte de controle du refus en 403 |
```

`docs/decisions/2026-08-25-resolution-du-profil-local.md`, section
« Conséquences » :

> En developpement, les profils sont pre-provisionnes par la migration `V1000`
> du service Identite. Le compte `pierre_belinga`, present a l'annuaire et
> **volontairement absent** de cette migration, sert de cas de controle du refus.

L'export du realm portait même le commentaire dans l'entrée de l'utilisateur.
L'information existait à trois endroits ; elle n'a pas été cherchée avant d'écrire
en base. **C'est un défaut de méthode, pas un défaut d'information.**

Ce compte avait déjà servi comme cas de contrôle aux vérifications manuelles des
Sprints 5.3 et 6.1 (`403 UTILISATEUR_NON_HABILITE`, message renvoyant vers
l'administrateur).

## 3. Pourquoi c'est un vrai piège et pas une simple gêne

Le cas de contrôle du 403 est le **seul** qui prouve qu'un jeton Keycloak
parfaitement valide, émis par le bon realm, avec la bonne audience et un rôle
applicatif reconnu, est malgré tout refusé faute d'habilitation ouverte dans le
module. C'est l'invariant central de la décision du Sprint 0.4 :

> L'habilitation au module reste un acte d'administration explicite, elle ne
> découle pas de la seule existence d'un compte à l'annuaire.

Sans ce compte, la seule façon de vérifier cet invariant en bout en bout est de
créer un compte annuaire exprès — c'est-à-dire de refaire ce qui vient d'être
détruit.

**Le piège est silencieux, et c'est ce qui le rend grave.** Quelqu'un rejouant la
checklist d'environnement en pensant tester le refus obtiendrait :

```
pierre_belinga : HTTP 200
```

Un **succès à la place d'un refus**, sur un test de sécurité, sans le moindre
message expliquant pourquoi. Même famille que les défauts que ce projet a pris
soin d'éviter ailleurs : le compteur de signatures qui affirmerait une signature
absente du document (Sprint 4.2), l'index de recherche par utilisateur qui rend
un résultat d'apparence complète (point A-01), `@EnableKafka` manquant qui laisse
un service démarrer sans rien écouter (Sprint 5.2). **Tous ont en commun de
rendre un résultat plausible au lieu d'une erreur.**

## 4. La décision

### On ne restaure pas `pierre_belinga`, on le remplace

Supprimer son profil local et son `sub_keycloak` aurait rétabli l'ancien état.
Écarté, pour une raison de cohérence :

L'événement `LIAISON_COMPTE_KEYCLOAK` de la vérification du 6.3 est **sur le
topic**, à l'offset 184, et il porte le `sub` réellement établi. Effacer le profil
laisserait une trace d'audit attestant d'une liaison vers un profil inexistant —
le journal contredirait la base. Or tout ce sprint porte précisément sur le fait
que le journal doit dire vrai. Défaire une écriture pour préserver un décor de
test serait le geste inverse de celui qu'on vient de défendre.

`pierre_belinga` devient donc un septième compte habilité ordinaire — agent
d'unité 00002, profil local 7 —, et son changement de rôle est consigné ici.

### `thomas_ndzana` est le nouveau compte de contrôle

| | |
|---|---|
| Login | `thomas_ndzana` |
| Rôle annuaire | `AGENT_UNITE` |
| Matricule | 1955 |
| Profil local | **aucun, et jamais** |
| Mot de passe | `Rations2026`, comme les autres comptes de développement |

Ajouté à `infra/keycloak/realm-afb-rations-dev.json` et créé dans le realm en
cours d'exécution. Vérifié le jour même :

```
GET /identite/moi  (jeton thomas_ndzana)
→ 403 UTILISATEUR_NON_HABILITE
  "Aucun profil n'a ete ouvert dans le module pour ce compte."

GET /identite/moi  (jeton pierre_belinga)
→ 200
```

### Une garde au build, parce qu'un avertissement écrit ne suffit visiblement pas

L'information était déjà écrite à trois endroits et n'a pas empêché l'erreur. Un
quatrième commentaire n'aurait pas plus d'effet.

`CompteDeControleDuRefusTest` (service-identite) relit la migration `V1000` et
**fait échouer le build** si le login du compte de contrôle y apparaît. C'est la
discipline déjà appliquée par `PerimetreDuModuleTest` au périmètre de
`rations-audit-commun` et par `CleInterneJamaisJournaliseeTest` au secret partagé :

> Elle rend la dérive impossible **par accident**, pas impossible tout court. Qui
> veut ajouter ce compte peut éditer le test. Elle transforme un glissement
> silencieux en acte délibéré et visible en revue.

La garde ne couvre que la migration, pas un `INSERT` manuel en base — c'est
justement par là que l'erreur est passée. Elle est donc doublée d'un
avertissement en tête de `V1000` et dans le commentaire de l'utilisateur au sein
de l'export du realm, aux deux endroits qu'on ouvre avant d'y toucher.

## 5. Ce qu'il faut faire si l'on doit à nouveau tester une liaison

Le besoin qui a causé la collision est légitime et se représentera : éprouver
`LIAISON_COMPTE_KEYCLOAK` demande un profil pré-provisionné jamais connecté.

**Ne jamais prendre le compte de contrôle du refus.** Créer un compte annuaire
jetable pour l'occasion, ou ajouter une ligne à `V1000` pour un compte prévu à
cet usage. Les deux coûtent une minute ; la collision a coûté un cas de test de
sécurité.
