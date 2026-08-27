# Vérification d'habilitation inter-services

Module Paiement des Rations et du Transport de la Garde Armée — décision Sprint 1.3

Cette note décrit comment un service métier interroge le service Identité pour
savoir si un utilisateur a le droit d'agir sur un dossier rattaché à un code
unité. Elle ne s'implémente pas encore chez les consommateurs : ils n'ont pas
de code métier. Elle fixe le contrat qu'ils suivront à partir du Sprint 4
(service Workflow, vérification RG-12).

---

## 1. L'endpoint

```
GET /identite/habilitation?codeUnite=<code>
```

- Endpoint **interne**, hors des trois endpoints du contrat passerelle du
  service Identité (CLAUDE.md section 11 : `/identite/moi`,
  `/identite/utilisateurs`, `/identite/utilisateurs/{id}/role`). Il n'est pas
  destiné au frontend ni exposé comme ressource métier : il répond à une
  question d'habilitation posée par un autre service.
- Paramètre `codeUnite` obligatoire, format du référentiel des codes guichets
  (`VARCHAR(5)`). Absent ou mal formé : `400`.

### Réponse `200`

```json
{
  "login": "jean_mbarga",
  "role": "AGENT_UNITE",
  "codeUniteDemande": "00002",
  "autorise": true,
  "porteeNationale": false
}
```

| Champ | Sens |
|---|---|
| `login` | login annuaire de l'utilisateur résolu depuis le jeton |
| `role` | rôle applicatif local, une valeur de `RoleEnum` |
| `codeUniteDemande` | le `codeUnite` reçu en paramètre, renvoyé tel quel |
| `autorise` | `true` si l'utilisateur peut agir sur un dossier de ce code unité |
| `porteeNationale` | `true` pour un rôle à portée nationale (`DIRECTEUR_RESEAU_DR`, `ARH`, `DRH`, `ADMIN`) ; dans ce cas `autorise` vaut toujours `true` |

La décision s'appuie sur `PorteeAccesService` (Sprint 1.1), sans la dupliquer.
La portée **varie par rôle** : `AGENT_UNITE` et `CHEF_UNITE_DA` sont limités à
leur propre code unité, les autres rôles ont une portée nationale. Le cas
`DIRECTEUR_RESEAU_DR` est un raccourci provisoire (décision Sprint 1.1,
`docs/decisions/2026-08-26-portee-acces-directeur-reseau.md`) : quand le métier
définira le découpage en réseaux, seul `PorteeAccesService` change, pas ce
contrat.

### Autres codes de retour

| Code | Cas |
|---|---|
| `400` | `codeUnite` absent ou mal formé |
| `401` | jeton absent, invalide ou expiré (refusé par le filtre de sécurité, en amont du contrôleur) |
| `403` | jeton valide mais aucun profil local ouvert pour ce compte, ou profil désactivé (`UtilisateurNonHabiliteException`) |

Format d'erreur uniforme du projet : `{ timestamp, status, code, message, path }`.

---

## 2. Propagation du jeton — le service appelant relaie le jeton de l'utilisateur

Le service appelant **transmet l'en-tête `Authorization: Bearer <jeton>` de
l'utilisateur final**, tel quel, sans le réémettre ni le modifier.

```
GET /identite/habilitation?codeUnite=00002
Authorization: Bearer <jeton de l'agent qui a déclenché l'action>
```

Il ne s'authentifie **pas** avec un compte de service qui lui serait propre.

Motifs, vérifiés sur le realm de développement `afb-rations-dev`
(`infra/keycloak/realm-afb-rations-dev.json`) :

- CLAUDE.md section 10 n'impose pas d'identité machine-à-machine. Le realm de
  dev n'en contient aucune : un seul client, `rations-frontend`, public,
  `serviceAccountsEnabled: false`. Introduire un flux *client credentials*
  exigerait un nouveau client confidentiel et une modification du realm.
- Le mapper d'audience `audience-rations-api` place `aud: rations-api` dans
  l'access token. Le même jeton utilisateur est donc accepté tel quel par le
  Resource Server du service Identité (`application-dev.yml`,
  `audiences: rations-api`). Aucune configuration supplémentaire.
- La question posée est « **cet utilisateur** a-t-il le droit ? ». Elle se
  répond naturellement à partir du jeton de cet utilisateur : le service
  Identité le résout comme pour `GET /identite/moi`, puis applique la portée.
- `code_unite` n'est pas dans le jeton (CLAUDE.md section 10 : géré localement,
  pas dans l'annuaire). Un consommateur ne peut donc pas trancher seul
  l'habilitation d'un rôle à portée locale : l'appel au service Identité est
  nécessaire quel que soit le design.

Point de vigilance pour la DSI : s'il y a un jour des clients Keycloak par
service, le mapper d'audience devra continuer d'inclure `rations-api` dans les
jetons, sans quoi le service Identité rejettera les appels.

---

## 3. Comportement en cas d'indisponibilité du service Identité — refus conservateur

**Si le service Identité ne répond pas `200`, le consommateur refuse l'action.**
Fail-closed, sans exception :

- timeout, connexion refusée, `5xx`, réponse illisible → l'action métier est
  **refusée**, comme si `autorise` valait `false` ;
- **aucun cache** d'une réponse positive antérieure. Une donnée d'habilitation
  peut avoir changé entre deux appels (attribution de rôle, changement de code
  unité, désactivation de profil) ; autoriser sur une valeur potentiellement
  obsolète contredirait RG-12 et le principe bancaire du refus par défaut ;
- **aucun appel de repli au service Audit**, ni à aucun autre service, pour
  « deviner » la réponse.

### Le compromis assumé

Cet appel synchrone crée une **dépendance de disponibilité** : le service
Workflow ne peut pas trancher une validation si le service Identité est
indisponible. C'est un couplage synchrone sur le chemin critique, la catégorie
de dépendance que le choix d'un journal d'audit asynchrone (topic Kafka,
CLAUDE.md section 9.2) écarte précisément.

Ce n'est pas une contradiction masquée. Les deux cas ne sont pas symétriques :

- L'audit est un **effet de bord** d'observabilité. Rien en aval ne dépend de
  son résultat : il peut être « publier et poursuivre ». Le rendre synchrone
  laisserait une panne de journalisation bloquer du travail réel, sans raison.
- L'habilitation est une **précondition de la décision métier**. « Cet acteur
  peut-il valider ce dossier ? » doit être répondu avant le changement d'état,
  de façon bloquante, par nature. On ne peut pas « publier et poursuivre » une
  vérification de droit.
- La dépendance n'est pas créée par le choix d'un endpoint dédié : `code_unite`
  ne vit que dans le service Identité. Le choix fixe seulement **où s'exécute la
  règle** — dans le service qui en est propriétaire, plutôt que recopiée dans
  six services.
- Un appel synchrone à un service d'identité ou d'autorisation est l'une des
  rares dépendances synchrones admises en microservices (passerelle d'auth,
  introspection de jeton, sidecar de politique).

**Coût résiduel, non atténué :** une panne du service Identité bloque les
validations dans le service Workflow. La voie d'atténuation habituelle — cache
court de la portée côté appelant — est **écartée** ici au profit du refus
conservateur strict (décision Sprint 1.3). Si ce coût devient un problème, la
réponse est de fiabiliser le service Identité (redondance, sonde), pas
d'assouplir la règle de refus.

---

## 4. Ce que le consommateur trace

Un refus d'accès est une information de sécurité (CT-04, US-02) : le
consommateur publie un événement d'audit sur `rations.audit.evenement` quand
une action est refusée faute d'habilitation, via le producteur d'audit décrit
dans `docs/publication-audit.md`. Un refus dû à l'indisponibilité du service
Identité est tracé de la même manière, avec un motif distinct.

---

## 5. Résumé pour un service consommateur

1. Récupérer l'en-tête `Authorization` de la requête utilisateur entrante.
2. Appeler `GET /identite/habilitation?codeUnite=<code du dossier>` en relayant
   cet en-tête tel quel.
3. `200` avec `autorise = true` → poursuivre.
4. Tout autre cas — `200` avec `autorise = false`, `4xx`, `5xx`, timeout,
   connexion refusée → **refuser l'action métier** et publier l'événement
   d'audit du refus.
5. Ne jamais mémoriser une réponse positive pour un appel ultérieur.
