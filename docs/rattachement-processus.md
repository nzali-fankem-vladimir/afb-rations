# Rattachement de la fiche journalière au processus mensuel

Module Paiement des Rations et du Transport de la Garde Armée — décision Sprint 3.1, étape 5

**Statut :** tranchée. **À reporter dans CLAUDE.md §17 à la clôture du Sprint 3.**

---

## 1. Le problème

`fiche_journaliere.id_processus` désigne un `processus_mensuel` qui vit dans la
base `rations_workflow`, celle du service Workflow. **Aucune clé étrangère n'est
possible entre deux bases** : c'est une référence logique inter-services.

La saisie ne peut pas commencer sans processus : une fiche doit être rattachée à
un processus mensuel existant, pour une unité et une période données. Trois
questions en découlent, qu'aucun document du projet ne tranchait :

1. qui crée le processus, et à quel moment ?
2. comment garantir qu'aucune fiche ne référence un processus inexistant ?
3. comment la Saisie sait-elle qu'un processus est encore modifiable, alors que
   son statut vit dans l'autre service ?

---

## 2. Ce qui était déjà fixé

Le choix est plus contraint qu'il n'y paraît. Trois éléments antérieurs :

| Source | Ce qu'elle impose |
|---|---|
| Contrat d'API §5 | `POST /processus` — « Déclenche un processus normal ou complémentaire. **Rôle AGENT_UNITE** » |
| US-03, critère 1 | « **L'agent lance le processus** "FRAIS TAXI ET RATION ARMEE" et choisit un jour au calendrier » |
| Contrat d'API §3 | `POST /saisie/lignes` reçoit déjà un `idFicheJournaliere` : le client tient un identifiant obtenu en amont |

Le déclenchement du processus est donc **un acte explicite de l'agent, dans le
service Workflow**. Ce n'est pas une décision de ce sprint, c'est un préalable.

---

## 3. Décision 1 — L'agent déclenche, la Saisie vérifie

**Option retenue (option 1 des trois examinées).**

```
agent  --> POST /processus                      (Workflow)   => idProcessus
agent  --> POST /saisie/fiches {idProcessus, dateJour}
             Saisie --> GET /processus/{id}     (Workflow)   => statut, codeUnite, mois, annee
             Saisie --> GET /identite/habilitation?codeUnite=...  (Identité)
             => fiche ouverte ou récupérée
```

Le service Saisie **ne crée jamais de processus**. Il en vérifie un, puis s'y
rattache.

### Réponses aux trois questions

| Question | Réponse |
|---|---|
| **Workflow injoignable ?** | **Refus conservateur (fail-closed).** Timeout, connexion refusée, `5xx`, corps illisible → la fiche ou la ligne est refusée, avec un message technique **distinct** du refus métier. Même doctrine que le service Identité (Sprint 1.3) et le service Grilles (Sprint 2.4) : ce n'est pas une nouvelle classe de panne, Grilles bloque déjà la saisie de la même façon. |
| **Aucune fiche vers un processus inexistant ?** | La fiche n'est créée **qu'après** un `200` de `GET /processus/{id}`. Pas de clé étrangère, mais une **référence vérifiée à l'écriture**. Un processus n'est jamais supprimé (`CLOTURE` est terminal, aucun `DELETE` au contrat d'API) : l'orphelin n'a pas de chemin d'apparition. |
| **Processus encore modifiable ?** | La Saisie **demande**, elle ne suppose pas. `GET /processus/{id}` renvoie le `statut` **et** le `code_unite` : un seul appel sert les deux besoins (contrôle de statut et habilitation RG-12). |

### Options écartées

**Option 2 — le service Saisie crée le processus de façon transparente.**
Trois raisons, dont une rédhibitoire :

1. Elle contredit le contrat d'API et US-03 : le déclenchement est un acte de
   l'agent, tracé, avec son rôle.
2. **Elle rend l'état COMPLEMENTAIRE impossible par ce chemin.** Saisie devrait
   supposer `typeProcessus = NORMAL` systématiquement ; elle ne peut pas fournir
   `idProcessusOrigine` ni `motifOuverture`, que seul l'agent connaît. La
   régularisation (RG-15, Sprint 8) serait condamnée.
3. Une écriture qui en déclenche une autre dans un service voisin brouille le
   journal d'audit : qui a créé le processus, l'agent ou le service ?

**Option 3 — statut du processus projeté localement par Kafka.** Workflow
publierait ses changements d'état, Saisie tiendrait une copie locale et lirait
sans appel réseau. Elle survivrait à une panne de Workflow — son seul avantage
réel. Écartée pour trois motifs :

1. **Cohérence différée sur une donnée financière.** La consolidation (RG-06,
   sous-sprint 3.4) calcule `montant_total` à partir des lignes. Si une ligne
   entre après la soumission, le montant validé par le Chef d'Unité n'est plus
   celui qui part en comptabilité. Un décalage de quelques secondes suffit :
   c'est un défaut de contrôle interne, pas une gêne d'exploitation.
2. Elle exigerait un **quatrième topic Kafka**. CLAUDE.md §9 en fixe trois. Un
   changement d'architecture transverse n'a pas sa place dans un sous-sprint de
   domaine — même raisonnement que le refus d'un module partagé pour les
   énumérations (décision Sprint 2.1).
3. Elle recrée dans Saisie une projection du domaine de Workflow, ce que le
   guide §10 désigne explicitement comme une faute d'architecture.

---

## 4. Décision 2 — Vérification à **chaque écriture**, jamais mise en cache

Le contrôle de statut a lieu à **chaque `POST` / `PUT` / `DELETE`** portant sur
une ligne de prestation, pas seulement à l'ouverture de la fiche.

### Motif

`POST /saisie/lignes` reçoit `idFicheJournaliere` **directement** : rien
n'oblige le client à repasser par `POST /saisie/fiches`. Un contrôle limité à
l'ouverture laisserait une fiche ouverte le 15 écrivable le 20, y compris après
la soumission de l'état au Chef d'Unité — précisément le trou décrit au §3,
option 3.

C'est la stricte application de la doctrine déjà posée pour l'habilitation
(Sprint 1.3) : **aucun cache sur une donnée qui peut devenir obsolète en cours
de saisie.** Le statut d'un processus en est une.

### Coût assumé, à surveiller

Écrire une ligne de prestation mobilisera désormais **trois dépendances
synchrones empilées** :

| Appel | Origine de la décision |
|---|---|
| `GET /grilles/active` (montant, RG-03) | Sprint 2.4 |
| `GET /processus/{id}` (statut + code unité) | **ce document** |
| `GET /identite/habilitation?codeUnite=…` (RG-12) | Sprint 1.3 |

auxquelles s'ajoutent les contrôles RG-04 et RG-15 en base. **La saisie devient
le point du module le plus sensible à une panne.** C'est le compromis déjà
accepté deux fois, assumé une troisième — mais à garder en tête si la latence
devient sensible. La réponse serait alors de fiabiliser ces services, pas
d'assouplir la règle de refus.

### Conséquence à ne pas oublier au Sprint 3.2

Le service Saisie devient consommateur de `GET /identite/habilitation`. Il
**doit donc publier un événement `ACCES_REFUSE`** sur verdict négatif *et* sur
indisponibilité du service Identité (guide §0.2, CLAUDE.md §9.2,
`docs/appel-habilitation.md`). Le service Identité ne trace pas ces refus :
**si le consommateur ne publie pas, le refus n'est tracé nulle part**, et
l'exigence CT-04 n'est pas tenue, en silence.

---

## 5. Décision 3 — Recopie figée de `code_unite`, `mois` et `annee` sur la fiche

**Migration `V3` additive sur `fiche_journaliere`, décidée maintenant,
implémentée au Sprint 3.2** (avec la première écriture réelle de fiche).

```sql
ALTER TABLE fiche_journaliere
    ADD COLUMN code_unite     VARCHAR(5),
    ADD COLUMN mois_paiement  INTEGER,
    ADD COLUMN annee_paiement INTEGER;
```

### Pourquoi — RG-15 est infaisable sans

RG-15 interdit qu'une ligne reproduise une combinaison
(bénéficiaire, journée, nature, session) déjà présente dans **un autre état de
la même unité et de la même période**. Son contrôle porte, dit CLAUDE.md §6,
« sur `processus_mensuel` + `fiche_journaliere` + `ligne_prestation` ».

Or `processus_mensuel` est dans l'autre base. **Le service Saisie ne peut pas
regrouper ses fiches par unité et par période** : il ne connaît que des
`id_processus` opaques. Sans cette copie, RG-15 exigerait un appel réseau vers
Workflow pour chaque processus candidat à la comparaison — sur le chemin
d'écriture d'une ligne.

### Pourquoi la copie ne périme pas

C'est le motif « libellé recopié et figé » du Sprint 2.2
(`docs/decisions/2026-08-27-libelle-acteur-grille.md`), mais avec une garantie
plus forte : **ces trois valeurs sont immuables pour un processus donné**.
L'index `ux_processus_normal_par_periode` impose un seul processus `NORMAL` par
`(code_unite, mois_paiement, annee_paiement)` — le triplet fait partie de
l'identité du processus, il ne peut pas changer sous lui.

Le `statut`, lui, **n'est pas copié** : il est mutable, c'est exactement pour
cela qu'il est redemandé à chaque écriture (§4).

### Coût

**Nul en appels réseau.** `GET /processus/{id}` est déjà appelé pour le statut
(§3) : la capture du triplet se fait dans la même réponse.

Colonnes **nullables** en base, comme `libelle_createur` au Sprint 2.2 : ce sont
des copies de confort de lecture, `id_processus` reste la donnée de référence.

---

## 6. Contrainte d'exécution — **le service Workflow n'existe pas encore**

**Point relevé par l'utilisateur à l'arbitrage, et qui traverse les trois
décisions ci-dessus.**

L'ordre d'implémentation (CLAUDE.md §14) place **Saisie au Sprint 3** et
**Workflow au Sprint 4**, donc *après*. Or les trois décisions supposent un
appel réseau vers `GET /processus/{id}` — un endpoint d'un service qui, à ce
jour, n'a pas une ligne de code métier : `service-workflow/src` ne contient que
`SecurityConfig`, `RoleJwtConverter` et ses migrations Flyway.

**Cela ne change pas les décisions**, qui restent les bonnes sur le fond. Cela
change la façon de les vérifier.

### Ce qui est décidé en conséquence

1. Le client HTTP `ClientWorkflow` est **écrit au Sprint 3.2**, sur le modèle de
   `ClientIdentite` (service-grilles) : `RestClient`, délais bornés court,
   relais tel quel de l'en-tête `Authorization` de l'utilisateur final, refus
   conservateur sur toute réponse autre qu'un `200` exploitable.
2. Il est **testé contre un bouchon pendant tout le Sprint 3**. L'intégration
   réelle ne sera vérifiable qu'au Sprint 4.
3. **Aucun critère de validation du Sprint 3 ne doit prétendre que
   l'intégration Saisie → Workflow fonctionne.** Elle est *codée selon un
   contrat écrit*, elle n'est pas *vérifiée*. La distinction doit apparaître
   telle quelle dans les résumés de sous-sprint.

### Le piège précis à éviter

La pratique actuelle du projet est de **mocker la classe cliente elle-même**
dans les tests de service (`ClientIdentite` est mocké dans `GrilleServiceTest`).
Aucun bouchon HTTP n'existe aujourd'hui dans le dépôt — vérifié : ni WireMock,
ni `MockRestServiceServer`.

Cette pratique suffisait pour Grilles → Identité parce que `service-identite`
**existait** : l'intégration réelle a été vérifiée à la main aux Sprints 2.2 et
2.3. Pour Saisie → Workflow, cette vérification manuelle est **impossible avant
le Sprint 4**. Mocker `ClientWorkflow` ne testerait donc plus rien de ce qui
peut casser : l'URL, le mapping JSON de la réponse, la traduction des codes
d'erreur, le comportement au timeout. Ce sont exactement les défauts qui
« marchent en test » et se découvrent en intégration.

### Bouchon retenu

**`MockRestServiceServer`** (Spring Test), lié au `RestClient.Builder` du
client. Motifs : aucune dépendance nouvelle (`spring-boot-starter-test` la porte
déjà), et il teste le client réel — URL construite, en-têtes envoyés,
désérialisation de la réponse, traduction des statuts.

Contrainte de conception à respecter au 3.2 : `ClientWorkflow` doit accepter un
`RestClient.Builder` **injecté**, et non le construire en dur comme le fait
`ClientIdentite` aujourd'hui — sans quoi le bouchon ne peut pas s'y attacher.

Ce que `MockRestServiceServer` **ne couvre pas**, et qui reste au Sprint 4 :
timeout réseau réel, connexion refusée, et surtout la conformité de la réponse
que Workflow produira effectivement. Si WireMock devait être introduit plus tard
pour couvrir le premier point, ce serait une décision du Sprint 4, pas de
celui-ci.

---

## 7. Résumé opposable

1. L'agent déclenche le processus via `POST /processus` (Workflow). La Saisie
   n'en crée jamais.
2. `POST /saisie/fiches` reçoit un `idProcessus` et le **vérifie** auprès de
   Workflow avant toute écriture.
3. Le statut est **revérifié à chaque écriture de ligne**, jamais mis en cache.
4. `code_unite`, `mois_paiement`, `annee_paiement` sont **recopiés et figés**
   sur la fiche à son ouverture, pour rendre RG-15 faisable localement. Le
   `statut` n'est jamais copié.
5. Toute réponse autre qu'un `200` exploitable de Workflow → **refus**, avec un
   message technique distinct du refus métier.
6. Le service Saisie devient consommateur de `/identite/habilitation` : il
   **doit publier `ACCES_REFUSE`** sur verdict négatif et sur indisponibilité.
7. `ClientWorkflow` est écrit au 3.2 et **testé contre un bouchon** ;
   l'intégration réelle n'est vérifiée qu'au **Sprint 4**, et aucun document du
   Sprint 3 ne doit affirmer le contraire.
