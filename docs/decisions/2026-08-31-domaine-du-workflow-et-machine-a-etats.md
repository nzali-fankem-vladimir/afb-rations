# Domaine du workflow : entités, machine à états ET01 et premiers endpoints

**Date :** 2026-08-31 — Sprint 4.1
**Statut :** adoptée
**Portée :** service Workflow, et service Saisie pour la suppression du bouchon

---

## 1. Décision 1 — les entités suivent le dictionnaire, pas le guide

**Tranchée avec l'utilisateur, avant toute ligne de code.**

Les prompts des étapes 1 et 2 du guide 4.1 listent cinq colonnes qui n'existent
**ni dans le dictionnaire (CLAUDE.md §4), ni dans les tables créées au
Sprint 0.5** :

| Entité | Colonnes annoncées par le guide | Réalité |
|---|---|---|
| `ProcessusMensuel` | `date_declenchement`, `date_cloture`, `id_createur` | seule `date_creation` s'ajoute au dictionnaire |
| `EtapeWorkflow` | `date_action` | seule `date_creation` |
| `ParametreSysteme` | `date_modification` | **aucun horodatage** |

Le cas de `parametre_systeme` est le plus net : CLAUDE.md §4 dit explicitement
que cette table est « sans horodatage propre » (décision Sprint 0.7). Le guide la
contredit donc frontalement, tout en demandant par ailleurs des entités
« strictement conformes au dictionnaire ».

**Décision : les entités se conforment aux tables existantes.** Aucune migration
V3 n'est créée.

Trois raisons, dans cet ordre :

1. **Hibernate tourne en `ddl-auto: validate`.** Une entité portant ces champs
   ferait échouer le démarrage du service. Le désaccord ne serait pas resté
   théorique.
2. **Le dictionnaire fait autorité**, et c'est aussi ce que le guide demande dans
   la même phrase. Entre deux lectures du même prompt, on retient celle qui
   s'accorde avec CLAUDE.md et avec la base.
3. **Rien n'est perdu.** Le « qui a déclenché » et le « quand » sont portés par le
   journal d'audit (`DECLENCHEMENT_PROCESSUS`, avec `login`, `adresseIp` et
   `dateAction`) et, aux sous-sprints 4.2 à 4.4, par les lignes `etape_workflow`
   (`id_acteur`). Ajouter `id_createur` sur `processus_mensuel` créerait une
   seconde source pour la même information.

**Conséquence pour les sprints suivants.** Si 4.2 ou 4.3 découvre un besoin réel
d'horodater la clôture autrement que par l'audit, ce sera une migration additive
assumée, sur le modèle de `V4` côté Grilles — pas un rattrapage silencieux.

---

## 2. Décision 2 — la création est une transition, et le constructeur est fermé

ET01 compte **neuf** transitions, dont `(création) → EN_COURS_SAISIE`. Les huit
autres vont d'un état à un autre ; celle-ci n'a pas d'état source.

Deux effets :

- **`TransitionProcessus.declencher(...)` est la seule porte de création** d'un
  `ProcessusMensuel`. Le constructeur de l'entité est en **visibilité paquet**,
  comme l'est déjà son mutateur `appliquerStatut`. L'invariant « toute transition
  passe par la machine à états » devient vérifié par le compilateur au lieu d'être
  une convention que le prochain service applicatif pourrait ignorer.
- **`estAutorisee(source, cible)` ne couvre que les transitions état → état**, et
  rend `false` sur une source nulle.

Ce second point mérite d'être justifié : il aurait été tentant de faire de
`estAutorisee(null, EN_COURS_SAISIE)` la neuvième arête. On l'a écarté. Un statut
nul est presque toujours un défaut — une entité mal chargée, un champ oublié — et
lui donner la sémantique « depuis rien, donc création » transformerait ce défaut
en autorisation. Le refus par défaut vaut aussi pour les valeurs absentes.

Le test 17 (`tableConformeAEt01`) confronte la table entière — les 36 couples
possibles — à ET01, et exige exactement **huit** arêtes. Ajouter ou retirer une
transition de commodité fait échouer le build.

---

## 3. Décision 3 — la machine n'arbitre pas le montant, et c'est vérifié

Depuis `EN_ATTENTE_DA`, ET01 prévoit deux issues de validation : clôture directe
sous le seuil, envoi au Directeur Réseau au-delà (RG-08). **Les deux transitions
sont exposées séparément** — `cloturerApresValidationChefUnite` et
`aiguillerVersDirecteurReseau` — et **aucune ne lit un montant ni un seuil**.

Le choix entre elles appartient au service d'aiguillage du sous-sprint 4.3, qui
lira `SEUIL_AIGUILLAGE_DR` dans `parametre_systeme` — jamais codé en dur
(CLAUDE.md §15).

Une méthode unique `valider(processus, montant, seuil)` aurait rendu la machine
dépendante d'un paramètre de configuration : elle ne se relirait plus comme la
traduction fidèle d'ET01, et un changement de seuil deviendrait une modification
du domaine.

Le test 19 le verrouille structurellement : aucun nom de méthode ne contient
« montant » ni « seuil », et les deux transitions concurrentes ne prennent que le
processus en paramètre — **elles n'ont rien à comparer, donc rien à arbitrer**.

---

## 4. Décision 4 — le type COMPLEMENTAIRE est refusé sans lire `RATTRAPAGE_ACTIF`

Le drapeau `RATTRAPAGE_ACTIF` existe en base depuis la migration V2, et
`docs/dispositifs_provisoires.md` §1.3 décrit son usage. Il aurait donc été
naturel de le lire ici.

**Décision : le refus est inconditionnel au Sprint 4.1.** Le drapeau n'est pas
consulté.

**Motif.** Lire le drapeau signifierait « si ce paramètre passe à vrai, ceci
fonctionne ». C'est faux au Sprint 4.1 : le code de l'état complémentaire
n'existe pas — ni la vérification de l'état d'origine, ni le délai de
régularisation, ni le contrôle d'unicité inter-états RG-15. Un basculement du
paramètre produirait alors des états complémentaires **sans aucun de leurs
contrôles**, c'est-à-dire exactement ce que le dispositif est censé empêcher.

Le drapeau sera consulté au Sprint 6bis, le jour où il aura quelque chose à
ouvrir. D'ici là, un dispositif qui ne protège rien vaut moins qu'un refus franc.

Le refus est rendu en **`422 FONCTIONNALITE_NON_OUVERTE`**, avec le message prévu
par `docs/dispositifs_provisoires.md` §1.3. Le contrôle est placé **avant** l'appel
au service Identité : inutile d'interroger le réseau pour une demande qui ne peut
pas aboutir (test 5 de `ProcessusServiceTest`).

---

## 5. Décision 5 — deux codes d'erreur ajoutés au contrat

| Code | Statut | Cas |
|---|---|---|
| `PROCESSUS_EXISTANT` | `409` | Un état NORMAL est déjà ouvert pour ce couple unité / période |
| `FONCTIONNALITE_NON_OUVERTE` | `422` | Type COMPLEMENTAIRE demandé (§4 ci-dessus) |

`409` et non `422` pour le premier : **quelque chose est bien dupliqué**, ce qui
est la distinction déjà posée au Sprint 2.3 entre conflit d'unicité et règle de
gestion. Le contrat d'API §1.3 range d'ailleurs explicitement les contraintes
d'unicité en 409.

Le refus vient du **service**, pas seulement de la base. L'index partiel
`ux_processus_normal_par_periode` garantit la règle, mais une contrainte violée
produirait un message technique illisible. Le message du service nomme le dossier
déjà ouvert — son identifiant et son statut — pour que l'agent sache qu'il doit
le rejoindre plutôt qu'en ouvrir un second. L'index reste le filet en cas de
course entre deux demandes simultanées, traduit lui aussi en `409`.

---

## 6. Décision 6 — deux montants distincts sur `GET /processus/{id}/etat`

La réponse porte **`montantTotalPorte`** (la valeur enregistrée sur
`processus_mensuel.montant_total`) et **`montantTotalFcfa`** (le total calculé à
l'instant par le service Saisie).

Ils diffèrent légitimement tant que l'état n'a pas été soumis : le montant porté
vaut alors `0`, puisque c'est la soumission (Sprint 4.2) qui y reportera le total.
Après soumission, l'écriture est fermée côté Saisie et les deux valeurs
coïncident.

**Les fondre en un seul champ ferait disparaître cette information** : on ne
saurait plus si le chiffre affiché est celui qui engage le circuit ou une
photographie de la saisie en cours. Sur un module de paiement, la différence
n'est pas cosmétique.

Workflow **recopie** le total de Saisie sans jamais le réadditionner : un seul
chemin de calcul, donc aucune divergence possible entre le détail affiché et le
total (décision Sprint 3.4 §6).

**Un `montantTotalFcfa` nul est un refus, jamais un zéro.** Zéro est une valeur
légitime — celle d'un état vide — et l'absence de valeur ne doit surtout pas s'y
confondre : les deux commanderaient le même aiguillage (« sous le seuil »), l'un à
juste titre, l'autre par accident. C'est exactement le montant partiel que
`docs/appel-consolidation.md` §6 interdit d'enregistrer.

---

## 7. Décision 7 — où les transactions commencent, et où elles ne commencent pas

| Méthode | Transactionnelle | Pourquoi |
|---|---|---|
| `declencher` | **oui** | Elle écrit. L'appel réseau à Identité est placé **en tête** : avec l'acquisition différée de connexion de Spring Boot, aucune connexion à la base n'est détenue pendant qu'il dure (doctrine Sprint 2.2). |
| `consulter` | **non** | Un `findById`, puis un appel réseau. Ouvrir une transaction autour immobiliserait une connexion pendant tout l'appel — le défaut écarté au Sprint 2.3 et redit au Sprint 3.4. |
| `consulterEtat` | **non** | Deux appels réseau s'enchaînent (Identité, puis Saisie). Même raison, en plus fort. |

`ProcessusMensuel` ne porte **aucune association paresseuse** : l'entité est
complète dès le `findById`, et `open-in-view` reste à `false`. Rien ne dépend donc
d'une transaction ouverte à la lecture.

---

## 8. Décision 8 — `EtatConsolide` vit dans la couche application

Le type que rend le port `ConsolidationClient` est déclaré dans `application`,
avec ses annotations `@JsonIgnoreProperties`, et l'adaptateur HTTP y lie
directement le JSON.

L'orthodoxie voudrait un type d'infrastructure recopié vers un jumeau applicatif.
Sur un arbre à quatre niveaux — état, journées, lignes, bénéficiaire — cette
recopie n'ajouterait **aucune décision**, seulement des occasions d'oublier un
champ. `@JsonIgnoreProperties` est un indice de liaison, pas une dépendance de
framework, et le rendre infrastructure obligerait la couche application à en
dépendre, dans le mauvais sens.

Le type reste un *tolerant reader* : Saisie peut enrichir sa réponse sans casser
Workflow. Tolérance à la **lecture** seulement — types boîtés partout, un champ
manquant se lit `null` et fait refuser (§6 ci-dessus).

---

## 9. Décision 9 — le bouchon du Sprint 3.3 est supprimé maintenant

**Tranchée avec l'utilisateur.** Le guide 4.1 annonce « service-workflow
uniquement », mais `docs/decisions/2026-08-31-bouchon-workflow-et-cablage-de-la-verification.md`
§5 inscrit les actions B-01 à B-04 comme **obligatoires au Sprint 4**.

| Réf | Action | État |
|---|---|---|
| B-01 | `BouchonVerificationProcessus.java` et son test supprimés ; `@Profile` retiré de `VerificationProcessusHttpClient` | **fait** |
| B-02 | Bloc `app.workflow.bouchon` et son mode d'emploi retirés d'`application-dev.yml` | **fait** |
| B-03 | Vérification en intégration réelle | **à la vérification manuelle du 4.1** |
| B-04 | `GET /processus/{id}` conforme aux cinq champs lus par `ProcessusReponse` | **fait**, verrouillé par un test |

**Motif.** Le bouchon n'existait que parce que le port 8084 ne répondait à
personne. Il répond depuis ce sous-sprint. Le laisser en place au motif d'un
périmètre de guide serait précisément la manière dont un dispositif provisoire
devient définitif — ce que son propre document de décision annonçait comme le
risque à surveiller. Et B-03 ne peut pas se vérifier avec le bouchon allumé : il
répond à la place du service qu'on veut éprouver.

**B-04 était le risque réel de cette décision de Sprint 3.3** : le contrat de
`GET /processus/{id}` avait été *déduit* du contrat d'API, sans que le corps de
réponse y soit décrit. Les cinq noms — `idProcessus`, `statut`, `codeUnite`,
`moisPaiement`, `anneePaiement` — sont désormais tenus par `ProcessusResponse` et
vérifiés par le test 11 de `ProcessusControllerIT`, avec un commentaire qui
explique ce qu'un renommage casserait : **toute écriture de ligne de prestation,
en `503`, sans aucune erreur de compilation nulle part**.

---

## 10. Ce qui est attendu des sous-sprints suivants

- **4.2 (soumission)** : reporter `montantTotalFcfa` tel quel dans
  `processus_mensuel.montant_total`, sans le réadditionner ; **refuser la
  soumission d'un état vide** sur `nombreLignes == 0` (point C-03 du Sprint 3.4) ;
  refuser si Saisie est muet, sans jamais enregistrer un montant partiel ou repris
  d'une lecture antérieure (point C-02).
- **4.3 (aiguillage)** : lire `SEUIL_AIGUILLAGE_DR` dans `parametre_systeme` et
  choisir entre les deux transitions déjà exposées depuis `EN_ATTENTE_DA`. Ne rien
  ajouter à la machine à états.
- **4.4 (RG-12)** : `EtapeWorkflowRepository.findByIdProcessusAndIdActeur` est en
  place pour le contrôle de séparation des tâches.
- **Point ouvert C-04**, non traité : les fiches antérieures à la migration V3
  côté Saisie portent un `code_unite` nul et ne sont pas recoupables. Nettoyage ou
  renoncement explicite, selon ce que le métier veut faire des essais des
  Sprints 3.1 et 3.2.
