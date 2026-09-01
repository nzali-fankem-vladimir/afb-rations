# Résumé Sprint 4.4 — Second niveau, retour motivé et séparation des tâches

**Service :** service-workflow uniquement
**Date :** 1er septembre 2026
**Config :** **Opus / effort élevé sur l'intégralité du sous-sprint**, conformément au
guide §1 — « RG-11 et RG-12 sont deux règles de contrôle interne dont la violation ne
produit aucune erreur visible ». Aucun changement de modèle en cours de route.

**Statut :** livré, **tests au vert**, **vérification manuelle réelle faite et
conforme** (quatre services démarrés, jetons Keycloak réels, PDF et topic Kafka lus —
voir la section dédiée en fin de document).

`mvn -pl service-workflow test` → **BUILD SUCCESS, 231 tests, 0 échec**
(180 au Sprint 4.3, **+51**).
Backend complet `mvn clean test` → **BUILD SUCCESS, 474 tests, 0 échec**
(423 au Sprint 4.3), aucune régression sur les autres services.

**Ce sous-sprint clôt le Sprint 4.** Le circuit de validation est complet.

---

## Le cœur du sous-sprint

Trois mécanismes s'ajoutent, et le circuit se referme.

Le **Directeur Réseau** peut valider les états au-delà du seuil. Sa signature devient
la troisième sur la pièce jointe, l'étape `VALIDATION_DR` est enregistrée, et l'état
est clôturé — **sans aiguillage** : après le second visa, il n'y a plus d'échelon.

Le **retour motivé** ramène l'état à l'agent, `POST /processus/{id}/retour`. Le motif
est obligatoire et une suite d'espaces n'en est pas un. Un retour du Directeur Réseau
redescend **directement à la saisie**, jamais au Chef d'Unité.

La **séparation des tâches** (RG-12) refuse qu'une même personne agisse deux fois sur
la version du dossier qui est en circuit — en `403 SEPARATION_TACHES`, un code distinct
des deux autres refus en 403.

`transmis_comptabilite` reste **faux sur les deux branches** : la transmission est le
Sprint 5.

---

## ⚠️ La correction décisive apportée par l'utilisateur

**Le guide, comme la formulation initiale de la question, conduisait à un blocage
définitif que la décision de l'utilisateur a évité.**

L'étape 1 proposait trois lectures de RG-12, et la lecture retenue — « une personne
n'agit qu'une fois par dossier » — aurait été appliquée à **toute la vie du
processus**. L'utilisateur a vu ce que cela produit :

> La portée d'un DA est limitée à sa propre unité (Sprint 1.1), et beaucoup d'unités
> n'ont qu'un seul DA. Le DA de Bafoussam retourne l'état de juillet pour une erreur
> de saisie. L'agent corrige, resoumet. Le seul DA habilité sur cette unité est celui
> qui vient de le retourner : **bloqué à vie**, sans aucune échappatoire. Le contrôle
> censé protéger le circuit le condamnerait.

**Ce qui a été implémenté à la place.** Le contrôle porte sur le **cycle courant** : les
étapes de rang supérieur ou égal à la dernière `SOUMISSION_AGENT`. Un retour clôt un
cycle ; ce qui y a été fait ne pèse plus contre personne au cycle suivant.

L'esprit de RG-12 est intact — sur la version en circuit, une personne n'agit qu'une
fois — et le blocage disparaît. Deux tests le verrouillent dans les deux sens :
`CycleValidationTest.leRetourClotUnCycle` (le DA retrouve sa liberté) et
`CircuitCompletIT.leDecoupageEnCyclesNeRelachePasLaRegle` (l'agent qui vient de
resoumettre reste bloqué).

---

## ⚠️ Le défaut trouvé en lisant le code du 4.2, absent du guide

**En l'état, aucune resoumission après retour n'aurait abouti.**

`SoumissionService` refusait toute soumission si une pièce jointe existait
(`409 PIECE_JOINTE_EXISTANTE`, Sprint 4.2). Après un retour, la pièce jointe existe :
le test 16 du guide — « après correction, la resoumission repart du début du circuit »
— était impossible à satisfaire.

Le point a été porté à l'utilisateur avant codage, avec ses trois issues. Décision :
**le document est régénéré depuis l'état corrigé** et remplace l'ancien, le compteur
repartant à 1. Motif : après correction les montants ont changé — garder le document
d'origine ferait valider au Chef d'Unité un PDF qui ne correspond plus au dossier.

Un second défaut est tombé avec celui-là : `EnregistrementSoumission` fixait le rang
de l'étape de soumission à `1`. Une resoumission aurait produit deux étapes de rang 1,
et le découpage en cycles de RG-12 n'aurait plus su laquelle est la dernière
soumission. Le rang est désormais calculé partout.

---

## Les trois décisions tranchées avec l'utilisateur

### Q1 — RG-12 : **les deux cumuls interdits**, sur le **cycle courant**

Celui qui a soumis ne valide à aucun niveau ; celui qui a validé à un niveau ne valide
pas au suivant. C'est mot pour mot la formulation du contrat d'API §5 (« le validateur
a déjà agi sur le dossier »), et les deux lectures partielles laissaient chacune une
porte par laquelle une seule personne engageait la banque de bout en bout.

**Cumul de rôles : refus strict.** Précision de fait relevée en cours d'analyse :
`utilisateurs.role` est **une seule colonne** — dans ce module, un compte porte un rôle
à la fois. Le cumul simultané n'existe pas ; seul un changement de rôle dans le temps
est possible, et RG-12 le refuse alors, ce qui est le comportement attendu. Le
déblocage est organisationnel. Limite consignée en points ouverts.

### Q2 — Le niveau de validation est désigné par le **statut**, pas par le rôle

Un seul endpoint sert les deux visas. Le statut du dossier désigne le niveau attendu ;
le rôle de l'appelant est ensuite vérifié **contre** lui.

Trois motifs : le statut est détenu par le service et inchangeable de l'extérieur,
alors qu'un rôle peut être modifié par un administrateur entre deux gestes ; RG-07
décrit le parcours du dossier, pas la qualité de qui le regarde ; et les messages de
refus nomment alors l'état réel du dossier plutôt que le rôle de qui se présente.

### Q3 — La reprise est portée par la **resoumission**, aucun septième endpoint

L'état reste visiblement `RETOURNE` jusqu'à la resoumission — c'est ce qui permet à
l'agent de le reconnaître dans sa liste — et la transition `RETOURNE →
EN_COURS_SAISIE` est appliquée dans la transaction de resoumission, juste avant
`EN_COURS_SAISIE → SOUMIS`.

L'agent peut corriger ses lignes **immédiatement** : le service Saisie tient déjà
`RETOURNE` pour modifiable (décision Sprint 3.3). Rien à changer côté Saisie.

Un endpoint dédié en aurait fait un septième, contre le critère de validation « six
endpoints, ni plus ni moins ».

---

## Comment RG-11 est rendue structurelle

Le guide annonce RG-11 comme « le piège le plus probable du sous-sprint ». Elle n'est
pas respectée par discipline mais par construction :

- les deux transitions de retour d'ET01 mènent au **même et unique** statut
  `RETOURNE` — il n'existe aucune cible intermédiaire à choisir ;
- le `switch` sur le niveau est exhaustif : un troisième niveau ferait échouer la
  compilation plutôt que de laisser un cas sans effet ;
- `RetourResponse` rend le **statut atteint et le niveau d'origine côte à côte**, pour
  que le Directeur Réseau voie noir sur blanc que le dossier repart à l'agent ;
- le test 11 (`retourDirecteurReseauRamAneALAgent`) assère explicitement
  `isNotEqualTo(EN_ATTENTE_DA)`.

---

## Comment RG-10 est verrouillée : trois étages

| Étage | Refus | Ce qu'il protège |
| --- | --- | --- |
| `@NotBlank` sur `RetourRequest` | `400 REQUETE_INVALIDE` | le chemin HTTP, chaîne d'espaces comprise |
| `RetourService.exigerMotif` | `422 MOTIF_OBLIGATOIRE` | la règle, **avant tout appel réseau** |
| `TransitionProcessus` + `EtapeWorkflow.retournerAvecMotif` | exception | une étape `RETOURNEE` ne peut pas naître sans motif |

`isBlank` et non `isEmpty` : `"   "` est un champ présent et un motif absent.

Le motif est enregistré **aussi dans l'audit**, pas seulement dans
`etape_workflow.motif_retour` : l'étape vit dans une base que le module peut écrire, le
journal d'audit dans une base où aucun service métier n'a de droit de modification.

---

## Le refus RG-12 : un troisième code en 403, et pourquoi

| Code | Ce qu'il dit | Ce que la personne doit faire |
| --- | --- | --- |
| `ACCES_REFUSE` | votre rôle ne permet pas cette action | changer d'écran, vérifier son rôle |
| `UTILISATEUR_NON_HABILITE` | vous n'avez pas de droit sur cette unité | demander une habilitation |
| `SEPARATION_TACHES` | vous avez déjà agi sur ce dossier | **rien** — le dossier doit changer de mains |

Les confondre laisserait un chef d'unité réclamer indéfiniment une habilitation qu'il
possède déjà. Le refus est tracé en audit avec son propre motif : un contrôle interne
doit pouvoir compter les tentatives de cumul séparément des accès hors périmètre.

---

## Décisions prises en cours de route

| # | Décision | Conséquence |
| --- | --- | --- |
| 1 | **Aucun aiguillage au second niveau : le seuil n'est même pas lu.** | `aiguillage` et `seuilApplique` sont **nuls** dans `ValidationResponse` au second niveau — champs présents, sans valeur. Un test le prouve en rendant le paramètre illisible : la validation aboutit quand même. |
| 2 | **`RoleNonAttenduException` → `403 ACCES_REFUSE`**, code déjà au contrat. | Sa seule raison d'être est le **message** : « votre rôle ne permet pas cette action » serait faux pour un valideur du circuit ; le message nomme le niveau que le dossier attend. |
| 3 | **Aucun contrôle de séparation des tâches sur le retour.** | RG-12 interdit de *valider* un dossier qu'on a soutenu, pas de le refuser : un refus ne fait avancer aucun paiement. |
| 4 | **Le retour n'appose aucune signature et ne touche pas au document.** | RG-09 ne vaut que pour les validations. Vérifié par un test sur la taille réelle du fichier et sur le compteur. |
| 5 | **`motifRetour` ajouté en fin de `ProcessusResponse`**, rendu uniquement tant que le statut vaut `RETOURNE`. | Les onze champs antérieurs gardent nom, type et ordre — les cinq premiers sont un contrat inter-services avec la Saisie (action B-04). Une correction déjà faite, affichée sur un dossier reparti dans le circuit, se lirait comme un reproche en cours. |
| 6 | **`409 PIECE_JOINTE_EXISTANTE` conservé**, restreint au cas incohérent (pièce jointe sur un état `EN_COURS_SAISIE`). | Le code du contrat n'est pas retiré, il change de portée : il signale un état déjà soumis dont le statut aurait été changé hors machine à états. |
| 7 | **`EtapeWorkflowRepository.findByIdProcessusAndIdActeur` n'est pas utilisée par RG-12**, contrairement à ce qu'annonçait le Sprint 4.1. | Le découpage en cycles doit voir **tout** le parcours pour situer la dernière soumission. La méthode est conservée : elle répond à une autre question, utile au reporting du Sprint 6. |

---

## Ce qui a été fait — critères de validation du guide (§11)

| Critère | Statut | Où c'est vérifié |
| --- | --- | --- |
| Lecture de RG-12 arbitrée et documentée | ✅ Fait | `docs/decisions/2026-09-01-separation-des-taches-et-cycle-de-validation.md`, CLAUDE.md §6 et §17 |
| Cas du cumul de rôles tranché | ✅ Fait | Refus strict, limite consignée dans `docs/points-en-attente.md` |
| Séparation des tâches appliquée aux deux niveaux | ✅ Vérifié | `ValidationService` étape 7, tests `SeparationDesTaches` 1 à 3 |
| Refus distinct du refus pour rôle insuffisant | ✅ Vérifié | `ProcessusControllerIT` test 36, `SeparationTachesServiceTest` test 5 |
| Validation DR clôturant sans aiguillage | ✅ Vérifié | `SecondNiveau` tests 6 et 6b (seuil rendu illisible, la validation aboutit) |
| Retour ramenant toujours à l'agent | ✅ Vérifié | `RetourServiceTest` test 11, `isNotEqualTo(EN_ATTENTE_DA)` |
| Motif obligatoire et non vide | ✅ Vérifié | Tests 12, 13 (service) et 39, 40 (HTTP) |
| Reprise et resoumission fonctionnelles | ✅ Vérifié | `CircuitCompletIT` tests 15 et 16 |
| Circuit complet testé sur les deux branches | ✅ Vérifié | `CircuitCompletIT` tests 17 et 18 |
| Six endpoints du contrat | ✅ Vérifié | Cartographie + test 17 de `ProcessusControllerIT` (aucun chemin en trop) |
| CLAUDE.md complété des six décisions du Sprint 4 | ✅ Fait | §6 (RG-08, RG-09, RG-11, RG-12, contrôles de complétude), §11, §15, §17 |

---

## Étape 7 — les quatre vérifications de clôture du Sprint 4

Cartographie relancée : `py -3.14 -m graphify update .` → **4264 nœuds, 10250 arêtes,
214 communautés**.

| Vérification | Résultat |
| --- | --- |
| **Six endpoints du contrat, ni plus ni moins** | ✅ `POST /processus`, `GET /{id}`, `GET /{id}/etat`, `POST /{id}/soumission`, `POST /{id}/validation`, `POST /{id}/retour` — exactement six annotations de routage dans tout le service. |
| **Aucune valeur de seuil en dur** | ✅ Les quatre occurrences de « 100 000 » sont toutes en commentaire Javadoc explicatif, aucune dans du code exécuté. Verrouillé en plus par le test de garde `aucuneValeurDeSeuilEnDur`, qui relit les sources. |
| **service-workflow n'accède à la base d'aucun autre service** | ✅ Une seule source de données, `rations_workflow`. Les quatre entités JPA ne mappent que ses tables. Identité et Saisie sont joints en HTTP. |
| **Aucune transition ne sort de CLOTURE** | ✅ `TransitionProcessus` associe `CLOTURE` à l'ensemble vide, et le test 16 le généralise à toutes les cibles possibles. |

---

## Les 51 tests ajoutés

### `CycleValidationTest` — 10 tests, domaine pur

Le découpage du parcours en cycles. Le test central est le n° 8 : après un retour puis
une resoumission, le chef d'unité qui avait retourné n'a plus d'étape dans le cycle
courant — sans quoi l'unique DA de l'agence serait bloqué à vie.

### `SeparationTachesServiceTest` — 7 tests

Les cinq cas de séparation des tâches demandés par le guide, plus le cumul de rôles et
le dossier vierge. Le test 5 vérifie que le motif du refus **ne contient pas** les mots
« habilitation » ni « rôle ne permet pas ».

### `ValidationServiceTest` — 9 tests ajoutés (22 au total), vraie base et vrai stockage

`SecondNiveau` (6, 6b, 7, 8, 9, 9b) et `SeparationDesTaches` (1, 2, 3). Le dossier
« chez le directeur réseau » est construit **en faisant réellement valider par le chef
d'unité**, pas fabriqué à la main : le document porte donc vraiment deux visas, et le
parcours est celui que la production produira.

### `RetourServiceTest` — 13 tests, vraie base et vrai stockage

Les deux niveaux de retour, le motif à trois étages, la visibilité du motif, le
document intact, les refus de statut, de rôle et de portée, et l'audit.

### `CircuitCompletIT` — 5 tests d'intégration, services réels câblés entre eux

Les deux branches du seuil de bout en bout, le retour suivi de la reprise et de la
resoumission, et le pendant qui montre que le découpage en cycles **ne relâche pas** la
règle pour le soumissionnaire. Seuls les trois appels réseau sortants sont simulés ; le
stockage est un vrai répertoire, le seuil vient de la vraie table.

### `ProcessusControllerIT` — 7 tests ajoutés (40 au total)

Le 403 `SEPARATION_TACHES` distinct et tracé, l'ouverture au Directeur Réseau,
l'endpoint de retour, les deux refus de motif, le refus de rôle, et le motif visible
dans le détail.

**Trois tests existants ont été révisés**, et le motif de chaque révision est écrit
dans le fichier : « un état RETOURNE n'est pas soumissible » est devenu « est
resoumissible » ; « aucun endpoint de retour » est devenu « aucun chemin hors
contrat » ; « validation par un rôle autre que CHEF_UNITE_DA » a perdu le Directeur
Réseau, qui a désormais accès à l'endpoint.

---

## Fichiers

### Créés (10)

| Chemin | Nature |
| --- | --- |
| `domaine/CycleValidation.java` | Le découpage du parcours en cycles — support de RG-12 |
| `domaine/NiveauValidation.java` | Les deux niveaux et ce que chacun exige — « le statut désigne le niveau » |
| `domaine/exception/SeparationTachesException.java` | `403 SEPARATION_TACHES` |
| `domaine/exception/RoleNonAttenduException.java` | `403 ACCES_REFUSE` avec le message qui nomme le niveau |
| `application/SeparationTachesService.java` | Porte unique de RG-12 |
| `application/ResultatSeparationTaches.java` | Type scellé, sans champ booléen |
| `application/RetourService.java` | RG-10, RG-11 |
| `application/EnregistrementRetour.java` | Le seul bloc transactionnel du retour |
| `application/ResultatRetour.java` | Processus, étape, niveau d'origine |
| `api/dto/RetourRequest.java` + `api/dto/RetourResponse.java` | Le motif, et la réponse qui montre RG-11 |

### Modifiés (11)

| Chemin | Ce qui change |
| --- | --- |
| `application/ValidationService.java` | Deux niveaux, RG-12 branchée, aiguillage conditionnel |
| `application/EnregistrementValidation.java` | Niveau en paramètre, transition selon le niveau, audit adapté |
| `application/SoumissionService.java` | `RETOURNE` soumissible, document régénéré |
| `application/EnregistrementSoumission.java` | Reprise, pièce jointe régénérée, rang calculé |
| `application/SignatureService.java` | `regenererEtSigner` |
| `application/ProcessusService.java` | `DetailProcessus` avec le motif du retour en cours |
| `domaine/EtapeWorkflow.java` | `retournerAvecMotif` |
| `domaine/PieceJointe.java` | `regenererApresRetour` |
| `infrastructure/EtapeWorkflowRepository.java` | Recherche du dernier retour |
| `api/ProcessusController.java` | Endpoint de retour, validation ouverte au DR |
| `api/GestionnaireErreursApi.java` | Deux gestionnaires de plus, tous deux tracés |

### Documentation

| Chemin | Nature |
| --- | --- |
| `docs/decisions/2026-09-01-separation-des-taches-et-cycle-de-validation.md` | **Créé** — RG-12, le cycle courant, le cumul de rôles |
| `docs/decisions/2026-09-01-second-niveau-retour-et-reprise.md` | **Créé** — niveau par le statut, RG-11, reprise, document régénéré |
| `CLAUDE.md` | §6 (RG-08, RG-09, RG-11, RG-12, contrôles de complétude), §11, §15, §17 (12 lignes) |
| `docs/initialisation projet/API_contract_….md` | §1.4 : champ `manques` (avec exemple de charge) et tableau des trois codes de refus en 403 (**K-03 soldé**). ⚠️ **Ce dossier est dans `.gitignore` (ligne 64)** : la modification existe sur le disque mais n'est pas versionnée — c'est la politique du dépôt pour les documents d'initialisation, à ne pas confondre avec un oubli. |
| `docs/controles-completude.md` | K-03 et K-04 marqués soldés |
| `docs/points-en-attente.md` | Nouveau point : unité à un seul valideur |

---

## Points ouverts

| Point | Nature | Destinataire |
| --- | --- | --- |
| **Unité à un seul valideur** | Une personne qui soumet puis doit valider reste bloquée. Le découpage en cycles règle le cas fréquent (retour puis resoumission) ; celui-ci demande un suppléant ou une réattribution. | Métier |
| **K-05 — police du document** | Times-Roman au lieu de Bookman Old Style, question de licence Monotype. | DSI |
| **Surveillance du seuil** | Une ligne de `parametre_systeme` conditionne tout le circuit. Supervision du log `SEUIL INDISPONIBLE` demandée. | Exploitation |
| **Signature au sens juridique** | Mention horodatée + SHA-256, pas de cryptographie. Intégration au service de signature de la banque. | DSI |

---

## Vérification manuelle réelle — quatre services démarrés, jetons Keycloak réels

Faite par l'assistant à la demande de l'utilisateur (« réalise toi-même la vérification
manuelle et si tout est ok tu commits »).

PostgreSQL, `dottel-keycloak` et `rations-kafka` déjà démarrés. Services Identité
(8081), Saisie (8082), Grilles (8083), Workflow (8084) lancés réellement. Jetons
obtenus par grant `password` sur le realm `afb-rations-dev` : `jean_mbarga`
(AGENT_UNITE / 00002), `paul_essama` (CHEF_UNITE_DA / 00002), `sylvie_atangana`
(DIRECTEUR_RESEAU_DR, portée nationale), `claire_nkolo` (ARH). Les deux dossiers
`EN_ATTENTE_DR` laissés par le Sprint 4.3 ont servi de jeu d'essai naturel.

### La branche longue — validation du Directeur Réseau (dossier 110)

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 1 | `POST /processus/110/validation` (jeton DR) | `200`, **`statut: CLOTURE`**, **`aiguillage: null`**, **`seuilApplique: null`**, `nombreSignatures: 3`, étape `VALIDATION_DR` / `VALIDEE` / ordre 3 | ✅ pas d'aiguillage au second niveau |
| 2 | Page des visas du PDF (extraite par iText) | `jean_mbarga` / AGENT_UNITE / *17:48*, `paul_essama` / CHEF_UNITE_DA / *17:48*, **`sylvie_atangana` / DIRECTEUR_RESEAU_DR / *20:57*** | ✅ **les trois visas coexistent — le document est estampé, pas regénéré** |
| 3 | Base : `processus_mensuel` 110 | `CLOTURE` / 1 500 / **`transmis_comptabilite = f`** | ✅ RG-13 : la clôture ne transmet rien |
| 4 | Base : `etape_workflow` du 110 | 3 lignes — acteurs 1, 2 et 3, toutes `VALIDEE`, empreintes différentes | ✅ |

### RG-11 — le piège du sous-sprint (dossier 506)

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 5 | `POST /processus/506/retour` (jeton DR), motif renseigné | `200`, **`statut: RETOURNE`**, `niveauOrigine: DIRECTEUR_RESEAU`, étape `VALIDATION_DR` / **`RETOURNEE`** / ordre 3 portant le motif | ✅ **`RETOURNE`, jamais `EN_ATTENTE_DA`** |
| 6 | Base après le retour | `statut = RETOURNE` ; l'étape `RETOURNEE` a un `motif_retour` et **aucune `signature_numerique`** | ✅ un refus n'est pas un visa (RG-09) |
| 7 | PDF avant / après le retour | **13 503 → 13 503 octets**, compteur toujours à 2 | ✅ le retour ne touche pas au document |
| 8 | `POST /processus/1010/retour` (jeton **DA**, état `EN_ATTENTE_DA`) | `200`, `statut: RETOURNE`, `niveauOrigine: CHEF_UNITE`, étape `VALIDATION_DA` / `RETOURNEE` ordre 2 | ✅ les deux niveaux mènent au même statut |

### RG-10 — le motif obligatoire

| # | Corps envoyé | Résultat obtenu | Conforme |
|---|---|---|---|
| 9 | `{"motif":"     "}` | **`400 REQUETE_INVALIDE`** — *« motif : le motif du retour est obligatoire et ne peut pas etre vide (RG-10) »* | ✅ le contenu utile fait foi |
| 10 | `{}` | même refus | ✅ |
| 11 | `{"motif":null}` | même refus | ✅ |

### US-11 — le motif visible par l'agent

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 12 | `GET /processus/506` (jeton **agent**), état retourné | `statut: RETOURNE` et **`motifRetour: "Montant du 12 aout incoherent avec la grille en vigueur"`** | ✅ |
| 13 | `GET /processus/110` (état clôturé) | `motifRetour: null` | ✅ le motif ne survit pas au cycle |

### Reprise, correction, resoumission (dossier 506)

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 14 | L'agent ajoute une ligne sur l'état `RETOURNE` (service Saisie) | **`200`** — ligne 2632, TRANSPORT/SOIR, 3 000 FCFA, grille 12 | ✅ `RETOURNE` est bien modifiable (décision Sprint 3.3) |
| 15 | `POST /processus/506/soumission` (jeton agent) | `200`, **`statut: EN_ATTENTE_DA`**, montant **4 500** (corrigé), **`nombreSignatures: 1`**, étape `SOUMISSION_AGENT` **ordre 4** | ✅ le circuit repart du début (RG-07), le rang continue la série |
| 16 | PDF régénéré — page des visas | **`jean_mbarga` seul** ; cadres *Chef d'Unité* et *Directeur Réseau* **rigoureusement vides** | ✅ les visas d'avant le retour ont disparu avec l'ancien fichier |
| 17 | PDF régénéré — page de détail | **2 lignes**, `MBALLA Paul 1 500` + `NGUEMA Blaise 3 000`, **`TOTAL DU MOIS 4 500 FCFA`** | ✅ le document porte les montants corrigés |
| 18 | **`POST /processus/506/validation` par `paul_essama`, qui avait DÉJÀ validé ce dossier au cycle 1 (rang 2)** | **`200`, `CLOTURE`**, étape `VALIDATION_DA` **ordre 5** | ✅ **c'est la correction de l'utilisateur, prouvée en réel** : avec un contrôle portant sur toute la vie du processus, ce DA aurait été bloqué à vie |

### RG-12 — la séparation des tâches

**Constat de fait, rassurant :** avec les six comptes du realm, le refus
`SEPARATION_TACHES` **n'est pas atteignable par le circuit normal**. Les rôles sont
mutuellement exclusifs — un compte ne peut ni soumettre puis valider, ni valider aux
deux niveaux —, et le filtre de rôle du contrôleur devance donc toujours RG-12. **RG-12
est une seconde ligne de défense**, et c'est exactement son rôle.

Pour éprouver malgré tout le refus sur le service réel, l'état qu'un cumul de rôles
produirait a été **injecté en base** (une étape au nom du valideur dans le cycle
courant), puis retiré.

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 19 | Étape `VALIDATION_DR` injectée au nom de `sylvie_atangana`, puis elle valide | **`403 SEPARATION_TACHES`** — *« Vous avez deja valide cet etat au niveau du directeur reseau : il n'y a pas de troisieme visa (RG-12…) »* | ✅ code **distinct** de `ACCES_REFUSE` et `UTILISATEUR_NON_HABILITE` |
| 20 | L'étape `VALIDATION_DA` réattribuée au valideur, qui se présente au second niveau | **`403 SEPARATION_TACHES`** — *« Vous avez deja valide cet etat au niveau du chef d'unite : vous ne pouvez pas le valider une seconde fois… »* | ✅ le double visa est refusé |
| 21 | Effet de ces refus sur le dossier | **PDF inchangé (13 606 octets)**, statut toujours `EN_ATTENTE_DR`, **compteur toujours à 2** | ✅ **le refus précède l'estampage** |
| 22 | Après restauration, validation DR nominale | `CLOTURE`, **3 signatures**, trois visas lisibles dans le PDF | ✅ |

### Les autres refus

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 23 | `AGENT_UNITE` et `ARH` sur `/validation` | **`403 ACCES_REFUSE`** pour les deux | ✅ filtre de rôle |
| 24 | **`CHEF_UNITE_DA` sur un état `EN_ATTENTE_DR`** | `403 ACCES_REFUSE` — *« L'etat 01/2027 de l'unite 00002 attend la validation du **directeur reseau** (role DIRECTEUR_RESEAU_DR), et votre profil porte le role CHEF_UNITE_DA »* | ✅ **le message nomme le niveau attendu**, pas « votre rôle ne permet pas » |
| 25 | **`DIRECTEUR_RESEAU_DR` sur un état `EN_ATTENTE_DA`** | `403 ACCES_REFUSE` — *« …attend la validation du **chef d'unite** (role CHEF_UNITE_DA), et votre profil porte le role DIRECTEUR_RESEAU_DR »* | ✅ le statut commande, pas le rôle |
| 26 | Même cas sur `/retour` | `403 ACCES_REFUSE` — *« …est entre les mains du chef d'unite… »* | ✅ retourner et valider s'offrent au même acteur |
| 27 | `ARH` sur `/retour` | `403 ACCES_REFUSE` | ✅ |
| 28 | Retour sur un état `CLOTURE` | `422 TRANSITION_INTERDITE` — *« …Une régularisation passe par un état complémentaire… »* | ✅ |
| 29 | Retour sur un état déjà `RETOURNE` | `422 TRANSITION_INTERDITE` — *« Cet etat a deja ete retourne a l'agent et attend sa correction »* | ✅ |
| 30 | Chef d'unité 00002 sur un dossier de l'unité 00007 | **`403 UTILISATEUR_NON_HABILITE`** — *« role CHEF_UNITE_DA sans portee sur l'unite 00007 »* | ✅ le rôle n'est que le premier filtre |
| 31 | Retour sans jeton | `401` | ✅ |
| 32 | Retour sur un processus inexistant | `404 PROCESSUS_INTROUVABLE`, sans interroger Identité | ✅ |

### La chaîne d'audit, lue sur le topic Kafka

Consommation réelle de `rations.audit.evenement` (74 événements). Le contraste entre
les deux niveaux est net :

```json
// PREMIER niveau — le seuil et la décision sont tracés
{ "statut": {"avant":"EN_ATTENTE_DA","apres":"EN_ATTENTE_DR"},
  "niveau":"CHEF_UNITE", "etape":"VALIDATION_DA", "auteur":"paul_essama",
  "montantTotal":7000, "seuilApplique":1000,
  "aiguillage":"ENVOI_DIRECTEUR_RESEAU" }

// SECOND niveau — NI seuil NI aiguillage : aucune comparaison n'a eu lieu
{ "statut": {"avant":"EN_ATTENTE_DR","apres":"CLOTURE"},
  "niveau":"DIRECTEUR_RESEAU", "etape":"VALIDATION_DR",
  "auteur":"sylvie_atangana", "montantTotal":7000,
  "empreinte":"SHA-256:681bef6f…" }

// RETOUR — le motif vit aussi dans la base d'audit
{ "statut": {"avant":"EN_ATTENTE_DR","apres":"RETOURNE"},
  "niveau":"DIRECTEUR_RESEAU", "etape":"VALIDATION_DR",
  "motifRetour":"Montant du 12 aout incoherent avec la grille en vigueur",
  "auteur":"sylvie_atangana" }
```

**CT-04 tenu, avec un motif distinct.** Les deux refus RG-12 sont publiés :

```
motif: SEPARATION_TACHES  login: sylvie_atangana  chemin: /processus/1009/validation
   detail: « Vous avez deja valide cet etat au niveau du directeur reseau… »
motif: SEPARATION_TACHES  login: sylvie_atangana  chemin: /processus/1009/validation
   detail: « Vous avez deja valide cet etat au niveau du chef d'unite… »
```

Un contrôle interne peut donc compter les tentatives de cumul **séparément** de
`ROLE_INSUFFISANT` et de `HABILITATION_ABSENTE`.

### Le contrat exposé

`GET /v3/api-docs` du service Workflow rend **exactement six opérations** :

```
POST /processus
GET  /processus/{id}
GET  /processus/{id}/etat
POST /processus/{id}/soumission
POST /processus/{id}/validation
POST /processus/{id}/retour
```

**Ni plus ni moins.** Aucun endpoint de reprise n'a été ajouté.

### Observation mineure, sans incidence

Le PDF d'un état resoumis porte, en en-tête, le statut du dossier **au moment où le
document est produit** — donc `RETOURNE` plutôt que `EN_ATTENTE_DA`. C'est un
comportement **hérité du Sprint 4.2** : le document est généré hors transaction, avant
la transition, précisément pour qu'un échec d'écriture n'engage rien. Cosmétique, sans
effet sur le circuit ni sur les montants. À corriger au Sprint 6 si le métier le relève.

### État de la base après vérification

Le seuil a été **remis à `100000`, `actif = true`**. Le dossier temporaire créé sur
l'unité 00007 pour éprouver la portée d'accès a été supprimé, et l'étape injectée pour
RG-12 retirée.

| id | période | statut | montant | transmis | signatures |
|---|---|---|---|---|---|
| 109 | 09/2026 | `CLOTURE` | 1 500 | `f` | 2 |
| 110 | 10/2026 | `CLOTURE` | 1 500 | `f` | **3** |
| 111 | 12/2026 | `CLOTURE` | 1 500 | `f` | 2 |
| 506 | 11/2026 | `CLOTURE` | **4 500** | `f` | 2 |
| 1009 | 01/2027 | `CLOTURE` | 7 000 | `f` | **3** |
| 1010 | 02/2027 | `RETOURNE` | 1 500 | `f` | 1 |

`transmis_comptabilite` vaut `f` **partout**, sur les deux branches du circuit : RG-13
est intacte à la fin du Sprint 4. Le dossier 1010, laissé `RETOURNE`, servira de jeu
d'essai naturel au Sprint 5.

**Aucun écart constaté. Les trente-deux scénarios sont conformes.**

---

## Suite

**Sprint 5.1 — transmission comptable.** C'est lui qui posera
`transmis_comptabilite` à la publication effective sur `rations.etat.valide` (RG-13).
Le Sprint 4 laisse le drapeau à `false` sur les deux branches du circuit, et deux tests
le vérifient.
