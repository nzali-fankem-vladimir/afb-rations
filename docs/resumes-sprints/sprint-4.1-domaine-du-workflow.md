# Résumé Sprint 4.1 — Domaine du workflow : processus et étapes

**Service :** service-workflow (+ suppression du bouchon dans service-saisie)
**Date :** 31 août 2026
**Config :** Sonnet / Moyen pour les étapes 0 à 3, **Opus / Élevé** à partir de
l'étape 4, comme le prescrit le guide §1.

**Statut :** livré et **vérifié à la main**, services réellement démarrés.
`mvn -pl service-workflow test` → **BUILD SUCCESS, 53 tests, 0 échec**. Build
complet du backend séquentiel → **exit 0**, aucune régression (`service-saisie`
76 → 73 tests, les 3 du bouchon supprimé). Cartographie relancée : **3393 nœuds,
7034 liens, 197 communautés** (contre 3073 / 6122 / 181 en début de sous-sprint).

---

## Le cœur du sous-sprint

Le processus mensuel est l'objet autour duquel tout le module s'organise. Ce
sous-sprint pose son domaine — entités, machine à états ET01 — et ses deux
premiers gestes : ouvrir l'état du mois, et le consulter. **Ni soumission, ni
validation, ni retour** : ce sont les sous-sprints 4.2 à 4.4.

Trois propriétés structurelles ont été posées, chacune verrouillée par un test :

1. **CLOTURE est terminal.** Aucune transition n'en repart, pas même vers
   RETOURNE « pour corriger ». Un état qu'on peut rouvrir peut être reclos, donc
   **retransmis** à la comptabilité — un double paiement (RG-13). Le message
   d'erreur ne dit pas seulement que c'est interdit, il dit pourquoi et indique la
   voie légitime : un état COMPLEMENTAIRE qui référence l'original sans le
   rouvrir.
2. **La machine à états n'arbitre pas le montant.** Les deux issues depuis
   `EN_ATTENTE_DA` sont exposées séparément et ne prennent que le processus en
   paramètre : elles n'ont **rien à comparer, donc rien à arbitrer**. Le seuil
   (RG-08) sera lu par le service d'aiguillage du 4.3, dans `parametre_systeme`.
3. **Toute transition passe par la machine.** Le constructeur de
   `ProcessusMensuel` et son mutateur de statut sont en visibilité paquet :
   l'invariant est vérifié par le compilateur, pas confié à une convention.

---

## ⚠️ L'écart relevé avant tout codage — entités contre guide

Les prompts des étapes 1 et 2 du guide listaient **cinq colonnes qui n'existent
ni au dictionnaire (CLAUDE.md §4) ni dans les tables du Sprint 0.5** :

| Entité | Annoncé par le guide | Réalité |
|---|---|---|
| `ProcessusMensuel` | `date_declenchement`, `date_cloture`, `id_createur` | seule `date_creation` s'ajoute |
| `EtapeWorkflow` | `date_action` | seule `date_creation` |
| `ParametreSysteme` | `date_modification` | **aucun horodatage** — §4 le dit explicitement |

Hibernate tournant en `ddl-auto: validate`, une entité portant ces champs aurait
**empêché le service de démarrer**. Question posée avant d'écrire une ligne,
**tranchée par l'utilisateur : suivre le schéma existant**, aucune migration V3.
Rien n'est perdu — le « qui » et le « quand » sont portés par le journal d'audit
et, aux sous-sprints suivants, par `etape_workflow.id_acteur`.

---

## Décisions prises en cours de route

| # | Décision | Trace | Impact sprints suivants |
|---|---|---|---|
| 1 | **Entités conformes au dictionnaire et aux tables du Sprint 0.5**, pas aux prompts du guide. Tranché avec l'utilisateur avant codage. | `docs/decisions/2026-08-31-domaine-du-workflow-et-machine-a-etats.md` §1 | Si 4.2/4.3 a besoin d'horodater la clôture autrement que par l'audit, ce sera une migration additive assumée, pas un rattrapage silencieux. |
| 2 | **La création est la 1ʳᵉ des neuf transitions** : elle passe par `TransitionProcessus.declencher`, constructeur d'entité en visibilité paquet. `estAutorisee` ne couvre que les 8 transitions état → état et rend `false` sur source nulle — un statut nul est un défaut, pas une création. | §2 | Les services 4.2 à 4.4 ne peuvent pas contourner la machine à états : le compilateur les en empêche. |
| 3 | **La machine n'arbitre pas le montant**, vérifié structurellement (test 19 : aucun nom de méthode ne contient « montant » / « seuil » ; les deux transitions concurrentes ne prennent que le processus). | §3 | 4.3 ajoute le service d'aiguillage **sans toucher** à la machine. |
| 4 | **Type COMPLEMENTAIRE refusé sans lire `RATTRAPAGE_ACTIF`** (`422 FONCTIONNALITE_NON_OUVERTE`), contrôle placé avant l'appel réseau. Lire le drapeau signifierait « si ce paramètre passe à vrai, ceci fonctionne » — faux au 4.1, où aucun contrôle de l'état complémentaire n'existe. | §4 | Sprint 6bis : c'est là que le drapeau sera consulté, quand il aura quelque chose à ouvrir. |
| 5 | **Deux codes ajoutés au contrat** : `PROCESSUS_EXISTANT` (409, quelque chose est bien dupliqué) et `FONCTIONNALITE_NON_OUVERTE` (422). Le refus vient du **service**, dont le message nomme le dossier déjà ouvert ; l'index partiel reste le filet en cas de course. | §5, CLAUDE.md §11 | Contrat d'API complété. |
| 6 | **`GET /processus/{id}/etat` porte deux montants distincts** : `montantTotalPorte` (enregistré) et `montantTotalFcfa` (calculé à l'instant par Saisie). Les fondre ferait perdre l'information de savoir si le chiffre engage le circuit ou photographie une saisie en cours. Un `montantTotalFcfa` **nul est un refus, jamais un zéro**. | §6 | 4.2 : à la soumission, `montantTotalPorte` reçoit `montantTotalFcfa` et les deux coïncident. |
| 7 | **`declencher` transactionnelle** (avec l'appel réseau en tête), **`consulter` et `consulterEtat` ne le sont pas** : elles enchaînent des appels réseau, et une transaction immobiliserait une connexion pendant tout ce temps. | §7 | Doctrine à reprendre pour les endpoints d'écriture de 4.2 à 4.4. |
| 8 | **`EtatConsolide` vit dans `application`**, avec ses annotations Jackson, plutôt que dupliqué en infrastructure. Sur un arbre à quatre niveaux, la recopie n'ajouterait aucune décision, seulement des occasions d'oublier un champ. | §8 | Sprint 5 : la charge du topic `rations.etat.valide` se construit directement depuis ce type (`docs/appel-consolidation.md` §3.1). |
| 9 | **Bouchon du Sprint 3.3 supprimé** (B-01 à B-04 soldées). Écart au périmètre « service-workflow uniquement » du guide, **tranché avec l'utilisateur**. Le bouchon n'existait que parce que le port 8084 ne répondait à personne. | §9, `docs/decisions/…-bouchon-workflow-….md` §5 | Le service Saisie exige désormais un service Workflow qui tourne. C'est le comportement voulu (refus conservateur). |

---

## Ce qui a été fait — critères de validation du guide (§11)

| Critère | Statut | Détail |
|---|---|---|
| Trois entités conformes au dictionnaire | ✅ | `ProcessusMensuel`, `EtapeWorkflow`, `ParametreSysteme`, strictement alignées sur les tables V1/V2 — écart du guide relevé et tranché (décision 1) |
| Quatre énumérations conformes | ✅ | `StatutEnum` (6), `TypeProcessusEnum` (2), `NomEtapeEnum` (3), `StatutEtapeEnum` (3), conformes à CLAUDE.md §5 |
| Machine à états couvrant les neuf transitions d'ET01 | ✅ | 8 arêtes état → état + la création ; tests 1 à 9 |
| Six transitions interdites levant une erreur | ✅ | Tests 10 à 15, chacun vérifiant **aussi que le statut reste inchangé** après le refus |
| CLOTURE verrouillé | ✅ | Tests 13 et 14, plus le test 16 qui généralise : **aucune sortie de CLOTURE quelle que soit la cible** |
| Unicité du processus normal contrôlée par le service | ✅ | Message nommant le processus existant et son statut (test 3, contre la vraie base) ; index partiel en filet, traduit lui aussi en 409 |
| Type COMPLEMENTAIRE refusé | ✅ | `422 FONCTIONNALITE_NON_OUVERTE`, **sans même interroger le service Identité** (test 5) |
| État consolidé obtenu par appel d'API | ✅ | `ConsolidationClient` / `ConsolidationHttpClient` ; **aucune source de données vers `rations_saisie`**, vérifié par grep et par cartographie |
| Portée d'accès appliquée | ✅ | `HabilitationService`, porte unique ; sur le déclenchement (unité demandée) et sur la consultation (unité **du processus**) — tests 2, 6, 11, 12, 16 |
| Aucun endpoint de soumission ou de validation | ✅ | Test 17 de l'IT : les trois chemins des sous-sprints suivants répondent 404 |

## Tests et vérifications du guide (§9)

| Vérification | Résultat |
|---|---|
| `mvn -pl service-workflow test` | **BUILD SUCCESS, 53 tests** |
| Quinze tests de la machine à états | **20** — les 15 demandés + 5 verrouillages |
| Transitions interdites | Erreur explicite, nommant les issues autorisées depuis l'état courant |
| CLOTURE terminal | Aucune transition sortante (test 16, sur les 6 cibles) |
| Déclenchement nominal | 201, `EN_COURS_SAISIE`, en-tête `Location` |
| Second déclenchement même unité et période | 409 `PROCESSUS_EXISTANT` |
| Type COMPLEMENTAIRE demandé | 422, message renvoyant à la DRH |
| Déclenchement hors portée | 403 `UTILISATEUR_NON_HABILITE`, publié en audit |
| État consolidé | Données provenant du service Saisie, recopiées sans recalcul |
| Service Saisie injoignable | 503 `SERVICE_SAISIE_INDISPONIBLE`, jamais un total supposé |
| Aucun accès à la base du service Saisie | ✅ aucun import inter-service, une seule dépendance interne (`rations-audit-commun`), aucune source de données étrangère |

---

## Les 53 tests

**`TransitionProcessusTest` — 20 tests** (machine à états, sans base ni réseau)

| # | Objet |
|---|---|
| 1-9 | Les neuf transitions d'ET01, chacune vérifiée sur le statut résultant |
| 10 | `EN_COURS_SAISIE → EN_ATTENTE_DA` — saut de la soumission |
| 11 | `SOUMIS → CLOTURE` — saut des validations |
| 12 | `EN_ATTENTE_DA → EN_ATTENTE_DA` |
| 13 | `CLOTURE → RETOURNE` — **motif volontairement renseigné**, sans quoi le test passerait pour la mauvaise raison |
| 14 | `CLOTURE → EN_COURS_SAISIE` |
| 15 | `RETOURNE → EN_ATTENTE_DA` — saut de la resoumission |
| 16 | CLOTURE n'a aucune sortie, sur les 6 cibles |
| 17 | **La table entière confrontée à ET01** : les 36 couples possibles, exactement 8 arêtes |
| 18 | RG-10 : aucun retour sans motif, côté DA comme DR, sur `null` / `""` / `"   "` |
| 19 | Aucune méthode ne reçoit un montant ni un seuil |
| 20 | `estAutorisee` refuse une source nulle plutôt que d'y voir une création |

**`ProcessusServiceTest` — 16 tests, contre la vraie base `rations_workflow`**
(l'unicité repose sur l'index partiel autant que sur le code ; seuls les deux
appels réseau sont simulés). Jeux d'essai en **année 2099**, pour qu'aucune
donnée réelle ne puisse entrer en collision sur l'index.

| # | Objet |
|---|---|
| 1 | Déclenchement nominal : statut, type, montant 0, non transmis, réellement en base |
| 2 | Portée vérifiée sur l'unité demandée, avec le jeton de l'appelant |
| 3 | Second déclenchement : refus, message nommant le dossier existant, rien de créé |
| 4 | Le refus est **borné au couple** : autre mois et autre unité restent ouvrables |
| 5 | COMPLEMENTAIRE : refus, **Identité jamais interrogé**, aucun audit |
| 6 | Agent hors portée : refus, rien de créé |
| 7 | Identité muet : refus conservateur, rien de créé |
| 8 | Déclenchement tracé (action, entité, id, IP, delta, login) |
| 9 | Un déclenchement refusé ne laisse **aucune trace de succès** |
| 10 | Processus inexistant : 404 **sans question d'habilitation** |
| 11 | Portée vérifiée sur l'unité **du processus**, jamais sur un paramètre |
| 12 | Consultation hors portée : refus |
| 13 | L'état consolidé est demandé avec l'identifiant **et** l'unité du processus |
| 14 | État sans aucune fiche : rendu normalement, ce n'est pas une erreur |
| 15 | Saisie muette : refus conservateur, jamais un total supposé |
| 16 | Hors portée : **Saisie n'est jamais appelé** |

**`ProcessusControllerIT` — 17 tests**, chaîne jeton → sécurité → contrôleur →
gestionnaire d'erreurs.

| # | Objet |
|---|---|
| 1 | Sans jeton : 401, service jamais appelé, **aucun audit** (un 401 n'est pas une tentative) |
| 2 | `AGENT_UNITE` : 201, `Location`, corps complet |
| 3 | Le jeton est relayé **tel quel** au service |
| 4 | `CHEF_UNITE_DA` sur le déclenchement : 403 `ACCES_REFUSE`, **publié en audit** |
| 5-6 | Mois hors bornes, code unité mal formé : 400 `REQUETE_INVALIDE` |
| 7 | COMPLEMENTAIRE : 422 `FONCTIONNALITE_NON_OUVERTE` |
| 8 | Doublon : 409 `PROCESSUS_EXISTANT`, message nommant le processus |
| 9 | Hors portée : 403 `UTILISATEUR_NON_HABILITE`, publié en audit (CT-04) |
| 10 | Identité muet : **503 et non 403** — la panne doit rester visible |
| 11 | **Les cinq champs lus par le service Saisie (action B-04)** |
| 12 | Les trois rôles du circuit consultent |
| 13 | Rôle hors circuit (DRH) : 403 |
| 14 | Processus inexistant : 404, **non tracé** |
| 15 | État consolidé : les deux moitiés de RG-06, montants **entiers** |
| 16 | Saisie muette : 503 `SERVICE_SAISIE_INDISPONIBLE` |
| 17 | **Aucun endpoint de soumission, validation ou retour** (404 sur les trois) |

---

## Héritage du Sprint 1.3 (guide §0) — tenu

| Obligation | Fait |
|---|---|
| Dépendance `rations-audit-commun` au `pom.xml` | ✅ ajoutée, avec le commentaire d'usage |
| `spring.kafka.bootstrap-servers` dans `application-dev.yml` | ✅ avec l'avertissement de ne pas surcharger les bornes du producteur |
| Aucun producteur d'audit local, aucun appel REST au service Audit | ✅ vérifié par grep |
| **Publication de `ACCES_REFUSE`** — le service Workflow est le premier consommateur réel de `/identite/habilitation` | ✅ centralisée dans `GestionnaireErreursApi`, **trois motifs distincts** : `ROLE_INSUFFISANT`, `HABILITATION_ABSENTE`, `IDENTITE_INDISPONIBLE` |

Le point le plus lourd de conséquence : **si `GestionnaireErreursApi` ne publiait
pas, le refus ne serait tracé nulle part** — le service Identité ne trace pas ses
propres verdicts négatifs. C'est écrit en tête du fichier, pour que personne ne
retire ces trois lignes en croyant nettoyer.

---

## Fichiers

**Créés — service-workflow (25 fichiers de production, 3 de test)**

| Chemin | Objet |
|---|---|
| `domaine/ProcessusMensuel.java` | Entité centrale ; constructeur et mutateur en visibilité paquet |
| `domaine/EtapeWorkflow.java` | Squelette du circuit ; `id_acteur` en identifiant simple |
| `domaine/ParametreSysteme.java` | Seuil RG-08 et drapeaux ; **sans horodatage**, conforme à §4 |
| `domaine/TransitionProcessus.java` | **Machine à états ET01** — le fichier central du sous-sprint |
| `domaine/{Statut,TypeProcessus,NomEtape,StatutEtape}Enum.java` | Les quatre énumérations |
| `domaine/exception/` (7 fichiers) | `TransitionProcessusInterdite`, `MotifRetourRequis`, `ProcessusIntrouvable`, `ProcessusExistant`, `FonctionnaliteNonOuverte`, `AgentNonHabilite`, `ServiceIdentiteIndisponible`, `ServiceSaisieIndisponible` |
| `infrastructure/{ProcessusMensuel,EtapeWorkflow,ParametreSysteme}Repository.java` | Les trois repositories |
| `infrastructure/config/ConfigurationAppelsSortants.java` | 2 s / 3 s, aucun réessai, portée prototype |
| `infrastructure/identite/{HabilitationHttpClient,HabilitationReponse}.java` | Appel `/identite/habilitation` |
| `infrastructure/saisie/ConsolidationHttpClient.java` | Appel `/saisie/processus/{id}/etat` |
| `application/{HabilitationClient,ResultatHabilitationUnite,HabilitationService}.java` | Portée d'accès, porte unique |
| `application/{ConsolidationClient,ResultatConsolidation,EtatConsolide}.java` | Port de consolidation |
| `application/ProcessusService.java` | Déclenchement, consultation, état consolidé |
| `api/ProcessusController.java` | Les trois endpoints |
| `api/{GestionnaireErreursApi,ErreurApiDto}.java` | Format d'erreur uniforme + publication CT-04 |
| `api/dto/{DeclenchementProcessusRequest,ProcessusResponse,EtatProcessusResponse}.java` | DTO d'entrée et de sortie |
| `test/…/TransitionProcessusTest.java` | 20 tests |
| `test/…/ProcessusServiceTest.java` | 16 tests, vraie base |
| `test/…/ProcessusControllerIT.java` | 17 tests, chaîne HTTP |

**Modifiés**

| Chemin | Modification |
|---|---|
| `service-workflow/pom.xml` | `rations-audit-commun` + 3 starters de test (webmvc, security, data-jpa) |
| `service-workflow/application-dev.yml` | Kafka, `jpa.ddl-auto: validate`, `open-in-view: false`, `app.identite.url`, `app.saisie.url` |
| `service-saisie/…/VerificationProcessusHttpClient.java` | `@Profile` retiré ; javadoc mise à jour — le service appelé existe |
| `service-saisie/…/VerificationProcessusClient.java` | Javadoc : le bouchon a été supprimé |
| `service-saisie/application-dev.yml` | Bloc `app.workflow.bouchon` et mode d'emploi retirés |
| `CLAUDE.md` | §11 (endpoints livrés + 2 codes d'erreur) et §17 (8 lignes de décisions) |
| `docs/decisions/…-bouchon-workflow-….md` | §5 : B-01, B-02, B-04 soldées |

**Supprimés**

| Chemin | Motif |
|---|---|
| `service-saisie/…/BouchonVerificationProcessus.java` | Action B-01 — sa raison d'être a disparu |
| `service-saisie/…/BouchonVerificationProcessusTest.java` | Idem (service-saisie passe de 76 à 73 tests) |

**Créés — documentation**

| Chemin | Objet |
|---|---|
| `docs/decisions/2026-08-31-domaine-du-workflow-et-machine-a-etats.md` | Les neuf décisions du sous-sprint |

---

## Points ouverts

| # | Point | Pour qui |
|---|---|---|
| **C-02** | Reporter `montantTotalFcfa` tel quel dans `montant_total` ; ne **jamais** enregistrer un montant partiel, à zéro ou repris d'une lecture antérieure. Le refus (`503`) est déjà en place ; l'écriture reste à faire. | Sprint 4.2 |
| **C-03** | Refuser la soumission d'un état vide, sur `nombreLignes == 0`. La donnée est disponible dans la réponse ; la règle appartient à Workflow. | Sprint 4.2 |
| **C-04** | Fiches antérieures à la migration V3 côté Saisie (`code_unite` nul) : non recoupables. Nettoyage ou renoncement explicite. **Non traité au 4.1.** | Sprint 4 ou nettoyage de données |
| **W-01** | `HttpStatus.UNPROCESSABLE_ENTITY` est déprécié depuis Spring 7. Utilisé ici comme dans service-grilles et service-saisie : à traiter d'un coup sur les trois services, pas dans un seul. | Dette technique |
| **W-02** | Aucune règle métier n'interdit d'ouvrir un état sur une période **future** (août 2030 est accepté). Non spécifié par le cahier des charges — à confirmer avec le métier. | Métier |

---

## Vérification manuelle réelle — cinq services démarrés, jetons Keycloak réels

Faite par l'assistant à la demande de l'utilisateur (« effectue les étapes de la
vérification visuelle toi-même et si tout est ok tu commit »). PostgreSQL,
`dottel-keycloak` et Kafka démarrés (topics créés par `infra/docker/kafka-topics.sh`).
Services Identité (8081), Saisie (8082), Grilles (8083), Workflow (8084) lancés
réellement, jetons obtenus par grant `password` sur le realm `afb-rations-dev`
(`jean_mbarga` AGENT_UNITE/00002, `paul_essama` CHEF_UNITE_DA/00002,
`sylvie_atangana` DR national, `agnes_tchinda` DRH).

### Les scénarios du guide (§8, §9)

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 1 | `POST /processus` nominal (agent, 09/2026, 00002) | `201`, `EN_COURS_SAISIE`, `Location: /processus/109`, `montantTotal:0`, `transmisComptabilite:false`, `typeProcessus:NORMAL` | ✅ |
| 2 | Rejouer le même appel | `409 PROCESSUS_EXISTANT` — *« processus n° 109, statut EN_COURS_SAISIE. Rejoignez ce dossier plutôt que d'en ouvrir un second. »* | ✅ le refus vient du service, nomme le dossier |
| 3 | `typeProcessus: COMPLEMENTAIRE` | `422 FONCTIONNALITE_NON_OUVERTE` — renvoie à la DRH | ✅ |
| 4 | Agent 00002 déclenche sur `codeUnite:00007` | `403 UTILISATEUR_NON_HABILITE` | ✅ hors portée |
| 5 | `GET /processus/109` (agent) | `200`, les **cinq champs B-04** présents (`idProcessus`, `statut`, `codeUnite`, `moisPaiement`, `anneePaiement`) | ✅ |
| 6 | `GET /processus/109/etat` (agent) | `200`, **`montantTotalPorte:0` ET `montantTotalFcfa:0`**, `journees:[]` | ✅ les deux montants distincts |
| 7 | Même appel, `CHEF_UNITE_DA` | `200` | ✅ rôle du circuit |
| 8 | Même appel, `DRH` (hors circuit) | `403 ACCES_REFUSE` | ✅ |
| 9 | `GET /processus/999999` | `404 PROCESSUS_INTROUVABLE` | ✅ |
| 10 | `POST /processus` sans jeton | `401` | ✅ |
| 11 | `/etat` avec **service Saisie éteint** | `503 SERVICE_SAISIE_INDISPONIBLE` — *« Aucun montant n'est supposé ni repris d'une lecture antérieure. »* | ✅ refus conservateur, jamais un zéro |
| 12 | `POST /processus` avec Saisie éteint | `201` | ✅ le déclenchement ne dépend pas de Saisie |
| 13 | `POST /processus` avec **service Identité éteint** | **`503 SERVICE_IDENTITE_INDISPONIBLE`, pas `403`** | ✅ la panne reste visible |
| 14 | `GET /processus/109` avec Identité éteint | `503 SERVICE_IDENTITE_INDISPONIBLE` | ✅ |
| 15 | Swagger `/v3/api-docs` | **exactement 3 opérations** : `POST /processus`, `GET /processus/{id}`, `GET /processus/{id}/etat` | ✅ rien créé par anticipation |

### B-03 — l'intégration réelle Saisie → Workflow, que le bouchon ne prouvait pas

| Contrôle | Résultat |
|---|---|
| `POST /saisie/fiches` `{idProcessus:109, dateJour:"2026-09-03"}` | **`201`** — fiche 651 créée. Le vrai `GET /processus/109` de Workflow est lu correctement par le client de Saisie : URL, mapping JSON, statut `EN_COURS_SAISIE` reconnu comme modifiable. |
| Recopie unité / période sur la fiche | `codeUnite:00002`, `moisPaiement:9`, `anneePaiement:2026` — propagés depuis la réponse de Workflow |
| `POST /saisie/lignes` (RATION/JOUR) puis `GET /processus/109/etat` | `montantApplique:1500` (grille active), puis **`montantTotalFcfa:1500` / `montantTotalPorte:0`** — RG-06 partagée, les deux moitiés assemblées, montants **entiers** |

Le premier essai de la ligne, service Grilles éteint, a rendu `503
SERVICE_GRILLES_INDISPONIBLE` côté Saisie — confirmation incidente que Saisie
refuse une ligne sans montant connu.

### Chaîne d'audit — Kafka démarré, consommateur sur `rations.audit.evenement`

**9 événements `service-workflow`**, aucun `AUDIT PERDU` :

| Événement | Nombre | Contenu vérifié |
|---|---|---|
| `DECLENCHEMENT_PROCESSUS` | 3 | `entiteCible:processus_mensuel`, `idEntite` renseigné, `adresseIp`, delta avant/après complet (`statut` null→`EN_COURS_SAISIE`, type, mois, année, `codeUnite`), contexte `auteur` + `role` |
| `ACCES_REFUSE` / `ROLE_INSUFFISANT` | 2 | DRH sur `/etat` — `login`, `chemin`, `methode`, `detail` |
| `ACCES_REFUSE` / `HABILITATION_ABSENTE` | 2 | agent hors unité |
| `ACCES_REFUSE` / `IDENTITE_INDISPONIBLE` | 2 | service Identité éteint, sur `POST /processus` **et** `GET /processus/{id}` |

`idUtilisateur` est `null` (décision Sprint 3.3 : `/identite/habilitation` ne rend
qu'un `login`). Une **consultation autorisée (`200`) n'émet aucun événement** —
une lecture n'est pas une action sensible.

### Nettoyage

Services applicatifs arrêtés après vérification. `rations-kafka` **ré-arrêté**
pour restaurer l'état antérieur à la session (il n'était pas lancé). `rations-postgres`
et `dottel-keycloak` laissés actifs. **Commit `5dbc639` créé.**

---

## Suite

**Sprint 4.2 — soumission et pièce jointe.** La machine à états, les repositories
et le client de consolidation sont en place ; 4.2 ajoute la transition
`EN_COURS_SAISIE → SOUMIS`, le report du montant total, le refus de l'état vide
(C-03) et la pièce jointe unique avec sa première signature (RG-09).
