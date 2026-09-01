# Résumé Sprint 4.3 — Validation du Chef d'Unité et aiguillage au seuil

**Service :** service-workflow uniquement
**Date :** 1er septembre 2026
**Config :** **Opus / effort élevé sur l'intégralité du sous-sprint**, conformément
au guide §1 qui le prescrit sans exception — « c'est ici que se joue RG-08, la règle
dont une erreur passe inaperçue en test superficiel et pose un problème de contrôle
interne en production ». Aucun changement de modèle en cours de route.

**Statut :** livré, **tests au vert**, **vérification manuelle réelle faite et
conforme** (services démarrés, jetons Keycloak réels — voir la section dédiée en
fin de document).
`mvn -pl service-workflow test` → **BUILD SUCCESS, 180 tests, 0 échec** (147 au
Sprint 4.2, **+33**).
Backend complet `mvn clean test` → **BUILD SUCCESS, 423 tests, 0 échec** (390 au
Sprint 4.2), aucune régression sur les autres services.

---

## Le cœur du sous-sprint

Le Chef d'Unité peut désormais valider l'état de son unité. Trois choses se
produisent : sa **signature s'ajoute** à la pièce jointe existante — le compteur
passe à deux —, l'étape **`VALIDATION_DA`** est enregistrée, et **RG-08 décide de la
suite** : au plus le seuil, l'état est clôturé ; au-delà, il monte au Directeur
Réseau.

Un état clôturé ici n'est **pas** transmis à la comptabilité. Ni le retour à
l'agent, ni le second niveau de validation, ni la séparation des tâches : ce sont les
sous-sprints 4.4 et 5.

---

## ⚠️ Le point où le guide et le projet divergent

**Le guide §6 étape 5 demande de renseigner `date_cloture`. Cette colonne n'existe
pas, et ce n'est pas un oubli.**

Le Sprint 4.1 a explicitement tranché — avec l'utilisateur, avant tout codage — que
cinq colonnes annoncées par les guides du Sprint 4 n'existaient ni au dictionnaire
(CLAUDE.md §4) ni dans la migration V1, et qu'aucune migration ne les ajouterait.
`date_cloture` est l'une d'elles (CLAUDE.md §17, ligne 4.1).

Hibernate tourne en `ddl-auto: validate` : ajouter le champ à l'entité sans migration
ferait échouer le démarrage du service.

**Ce qui tient lieu de date de clôture.** L'horodatage de l'étape
`etape_workflow.date_creation` de la ligne `VALIDATION_DA` — c'est-à-dire la ligne
qui a *provoqué* la clôture — et le journal d'audit. L'information n'est donc pas
perdue ; elle vit à l'endroit qui la produit, plutôt que recopiée sur le processus.

**Décision maintenue, pas rouverte** : elle avait déjà été prise avec l'utilisateur.
Elle est signalée ici pour que la divergence avec le guide ne surprenne personne à la
relecture.

---

## Les trois décisions tranchées avec l'utilisateur (étape 2 du guide)

Le guide impose de s'arrêter avant d'écrire le service de lecture du seuil. Les trois
questions ont été posées et tranchées.

### Q1 — Paramètre absent ou désactivé → **refuser la validation**

`500 SEUIL_INDISPONIBLE`, journalisé au préfixe repérable `SEUIL INDISPONIBLE`.
Aucune valeur de repli.

**L'utilisateur a accepté le compromis en y ajoutant une exigence** : consigner dans
`docs/points-en-attente.md` que ce paramètre est un **point de défaillance unique
pour tout le circuit de validation**, à surveiller en priorité en production. C'est
fait — une section entière y détaille les quatre mesures attendues de l'exploitation,
dont une alerte sur le préfixe de log et la restriction de l'écriture sur
`parametre_systeme`.

### Q2 — Valeur illisible → **le même refus**, avec une précision technique exigée

L'utilisateur a demandé confirmation que **toute** erreur de conversion, **y compris
un dépassement de capacité**, retombe sur le même format de refus et jamais sur une
erreur brute non gérée.

**Confirmé et vérifié par les tests.** `Long.parseLong` sur la valeur ébarbée : cette
seule capture couvre le texte, la chaîne vide, le séparateur de milliers, la
décimale, la notation exponentielle et le dépassement de capacité. Le signe est
vérifié juste après. `SeuilIndisponibleException` est **l'unique sortie en échec** de
la méthode. Le test `valeurIllisible` passe huit valeurs pathologiques, dont
`99999999999999999999999999`, et exige le même refus pour toutes.

Un seuil négatif est refusé — il ferait monter *absolument tout* au Directeur Réseau
sans jamais produire d'erreur. Un seuil de zéro est en revanche **accepté** : c'est
une règle intelligible.

### Q3 — **Relu à chaque validation**, jamais mis en cache

Tranché sans réserve. CT-18 l'exige, c'est la doctrine du Sprint 1.3 pour
l'habilitation, et le coût — un `SELECT` sur trois lignes indexées, dans un geste
mensuel qui enchaîne déjà deux appels réseau — est invisible.

---

## Comment la borne est verrouillée

**Le comportement, confirmé avant tout codage :**

| Montant total enregistré | Décision |
| --- | --- |
| seuil − 1 | `CLOTURE` |
| **seuil exactement** | **`CLOTURE`** |
| **seuil + 1** | **`EN_ATTENTE_DR`** |

**Trois dispositifs pour qu'une inversion ne puisse pas passer :**

1. **Une seule comparaison dans tout le module.** `AiguillageService.aiguiller` porte
   la seule ligne qui compare un montant à un seuil. Le service de validation, le
   contrôleur et la machine à états reçoivent une décision déjà prise. Une revue de
   RG-08 se fait en lisant un fichier.
2. **Le sens est porté par les noms.** `montantTotal > seuil` →
   `ENVOI_DIRECTEUR_RESEAU`, mot pour mot la phrase de RG-08. La décision est une
   énumération nommée d'après sa *conséquence*, pas un booléen qu'on lit juste une
   fois sur deux. Le `switch` qui l'applique est exhaustif : ajouter une issue ferait
   échouer la compilation.
3. **La preuve par balayage.** Un test parcourt une plage encadrant le seuil et exige
   que la décision **bascule exactement une fois**, entre `seuil` et `seuil + 1`. Une
   inversion, un décalage d'une unité ou une comparaison retournée déplacent ce point
   et font tomber le test — *même si les cas nommés avaient été « ajustés » en même
   temps pour les faire repasser*.

---

## Décisions prises en cours de route

| Décision | Motif |
| --- | --- |
| **L'aiguillage est lu et décidé AVANT l'estampage du document** | Un seuil illisible doit arrêter la validation avant qu'un visa ne soit gravé dans le PDF. Dans l'ordre inverse, la pièce archivée affirmerait une validation que la base ne connaîtrait jamais. Même raisonnement qu'au 4.2 pour la complétude. Le test `seuilIllisible` vérifie que la taille du fichier ne bouge pas. |
| **Une garde de montant dans la transaction** (`exigerMontantInchange`) | Rend explicite l'invariant de RG-08 : la décision appliquée correspond au montant *encore* enregistré. Elle ne devrait jamais se déclencher, et c'est pourquoi elle est peu coûteuse. Sans elle, l'invariant reposerait sur un raisonnement juste aujourd'hui et fragile à la prochaine évolution du circuit. |
| **`ordreEtapeSuivant` calcule le rang, il ne le fixe pas à 2** | Un état retourné puis resoumis (4.4) repassera par le chef d'unité et comptera plus de trois pas. Un rang constant produirait deux étapes de même ordre et rendrait l'historique ambigu. |
| **Bloc transactionnel dans une classe à part** (`EnregistrementValidation`) | Raison technique d'abord, reprise du 4.2 : `@Transactional` sur une méthode appelée depuis la même classe n'a aucun effet avec les mandataires Spring, et rien ne le signalerait. Raison de lisibilité ensuite : la frontière entre appels réseau, écriture disque et écritures en base devient visible dans la structure. |
| **Endpoint réservé à `CHEF_UNITE_DA` à ce sous-sprint** | Le contrat le destine aux deux valideurs. Ouvrir le rôle du Directeur Réseau avant de servir la transition `EN_ATTENTE_DR → CLOTURE` (4.4) le placerait devant un refus de statut incompréhensible. |
| **Pièce jointe absente sur un état en attente → `500`, pas un refus métier** | C'est une incohérence — la soumission ne peut pas avoir abouti sans produire de document. Le chef d'unité n'a rien fait de faux ; un `422` l'enverrait chercher une faute inexistante. Même parti qu'au 2.4 pour `INCOHERENCE_GRILLE`. |
| **`ValidationResponse` : les 5 champs du contrat, plus 2 blocs de témoignage** | Ajout **additif**, sur le modèle du champ `manques` au 4.2 : `idProcessus`, `statut`, `montantTotal`, `aiguillage`, `seuilApplique` restent présents, aux mêmes noms et aux mêmes types. `pieceJointe` et `etape` permettent au chef d'unité de constater les trois effets de son geste sans relire la base. |
| **L'audit enregistre le seuil appliqué, pas seulement la décision** | Un contrôle interne qui relit la trace six mois plus tard doit pouvoir refaire la comparaison lui-même. Sans le seuil du jour, la trace dirait qu'un état de 84 000 FCFA a été clôturé, sans permettre de juger si c'était la bonne décision. |

Le détail complet et les options écartées vivent dans
`docs/decisions/2026-09-01-seuil-aiguillage-et-borne.md`.

---

## Ce qui a été fait — critères de validation du guide (§11)

| Critère | Statut |
| --- | --- |
| Comportement à la borne confirmé avant codage | ✅ Fait — table des trois cas présentée et validée avant la première ligne de code |
| Trois décisions sur le paramètre tranchées | ✅ Fait — arrêt réel, questions posées, réponses de l'utilisateur appliquées |
| Seuil lu en base, jamais en dur | ✅ Vérifié — `grep -rn "100000\|100_000" backend/service-workflow/src/` ne rend **que** la ligne de la migration V2, c'est-à-dire le paramètre lui-même |
| Aiguillage testable isolément | ✅ Fait — `AiguillageServiceTest` ne persiste aucun processus ; il éprouve la règle sur des montants construits en mémoire |
| Tests de borne exacte passants | ✅ Vérifié — cas 3 et 4 du guide, plus le balayage à une seule bascule |
| CT-18 vérifié par modification réelle du paramètre | ✅ Vérifié — deux fois : sur le service isolé, et **de bout en bout** sur deux validations de part et d'autre d'une modification en base |
| Signature du DA apposée sur la pièce existante | ✅ Vérifié — le test extrait le texte rendu du PDF et exige que **les deux visas coexistent** |
| Étape `VALIDATION_DA` créée | ✅ Vérifié — `VALIDEE`, signée, ordre 2, acteur du profil |
| Clôture sans transmission | ✅ Vérifié — `transmis_comptabilite` toujours faux après clôture |
| Quinze tests passants | ✅ Dépassé — **33 tests** ajoutés (voir ci-dessous) |

---

## Tests et vérifications du guide (§9)

| Vérification | Résultat |
| --- | --- |
| `mvn -pl service-workflow test` | **BUILD SUCCESS**, 180 tests |
| Les quinze tests du sous-sprint | 33 tests, tous passants |
| Montant exactement égal au seuil | Clôture directe ✅ |
| Montant seuil plus un | Transfert au DR ✅ |
| Seuil modifié en base | Aiguillage suivant la nouvelle valeur ✅ (isolé **et** de bout en bout) |
| Aucune valeur de seuil en dur | Vérifié par `grep` **et** par un test permanent |
| Deux signatures après validation DA | Vérifié, sur le fichier réel ✅ |
| Une seule pièce jointe | Vérifiée — enrichie, jamais recréée ✅ |
| État clôturé : `transmis_comptabilite` | Toujours faux ✅ |
| Validation hors statut ou hors rôle | Refusée ✅ |

---

## Les 33 tests du sous-sprint

### `AiguillageServiceTest` — 13 tests, contre la vraie base

**Comparaison au seuil (7)** — montant très inférieur ; seuil − 1 ; **seuil
exactement** ; **seuil + 1** ; montant très supérieur ; **une seule bascule sur la
plage, exactement entre `seuil` et `seuil + 1`** ; et le test de garde ci-dessous.

**Lecture du paramètre (6)** — seuil modifié en base, l'aiguillage suit (CT-18) ;
paramètre désactivé, refus ; paramètre absent, refus ; huit valeurs illisibles
(texte, vide, espaces, séparateur de milliers, décimale, exponentielle, négatif,
**dépassement de capacité**), refus uniforme ; seuil à zéro accepté, borne exacte ;
espaces en bord tolérés.

**Le test de garde (1)** — `aucuneValeurDeSeuilEnDur` relit les sources de
`AiguillageService`, `SeuilService` et de ses propres tests, et exige que la valeur
configurée n'y figure nulle part. Il ne remplace pas la recherche manuelle du guide,
il la rend **permanente**.

### `ValidationServiceTest` — 13 tests, vraie base et vrai stockage

**Nominal (5)** — validation sous le seuil : `CLOTURE`, deux signatures, étape
`VALIDATION_DA` d'ordre 2 signée ; validation au-dessus : `EN_ATTENTE_DR` ; clôture
sans transmission ; **le document porte les deux visas** (texte extrait par iText, et
non octets bruts) ; l'audit trace décision, montant et seuil.

**Refus (8)** — processus inconnu sans interroger Identité ; état en cours de saisie ;
état déjà clôturé, **document intact** ; hors portée d'accès, document intact ;
service Identité muet, refus conservateur ; **seuil illisible : refus avant toute
écriture** ; document absent sur un état en attente.

**CT-18 de bout en bout (1)** — deux dossiers au **même montant** validés de part et
d'autre d'une modification du paramètre : le premier se clôt, le second monte au
Directeur Réseau.

### `ProcessusControllerIT` — 7 tests ajoutés (33 au total dans la classe)

Réponse sous le seuil, avec les cinq champs du contrat vérifiés un à un ; réponse
au-dessus du seuil ; **rôle autre que `CHEF_UNITE_DA` → 403 pour les trois rôles
testés, avec publication du refus en audit (CT-04)** ; hors portée → 403
`UTILISATEUR_NON_HABILITE` ; hors statut → 422 `TRANSITION_INTERDITE` ; seuil
illisible → **500 `SEUIL_INDISPONIBLE`, et non un refus métier** ; jeton relayé tel
quel.

Le seuil du jeu d'essai de ces tests est **délibérément différent** de la valeur
configurée, pour que la réponse ne puisse pas passer par coïncidence si elle rendait
une constante.

---

## Deux défauts trouvés par les tests, et corrigés

**1. Une valeur de seuil en dur dans un message d'erreur.** `SeuilService` donnait
`100000` en exemple dans le message expliquant le format attendu. Le test de garde
l'a relevé. Corrigé : le message décrit désormais le format sans citer de nombre.

**2. Le test de garde s'est pris lui-même en défaut.** Son propre commentaire citait
la valeur pour expliquer pourquoi il ne fallait pas l'écrire. Reformulé — et une
garde a été ajoutée : sous quatre chiffres, le test ne conclut rien, car une valeur
courte se retrouverait par hasard dans n'importe quel fichier.

Les deux sont de bons signes : le dispositif a fonctionné avant même d'avoir servi.

---

## Fichiers

**Créés (8)**

| Chemin | Rôle |
| --- | --- |
| `application/SeuilService.java` | Lecture stricte du paramètre, seul point d'obtention du seuil |
| `application/AiguillageService.java` | RG-08 — **la seule comparaison montant/seuil du module** |
| `application/DecisionAiguillage.java` | Les deux issues, nommées d'après le contrat |
| `application/ResultatAiguillage.java` | La décision, le montant et le seuil qui l'ont produite |
| `application/ValidationService.java` | Orchestration hors transaction, point d'accroche RG-12 |
| `application/EnregistrementValidation.java` | Le seul bloc transactionnel de la validation |
| `application/ResultatValidation.java` | Les trois écritures produites, plus la décision |
| `api/dto/ValidationResponse.java` | Les 5 champs du contrat, plus 2 blocs de témoignage |

**Modifiés (2)** — `api/ProcessusController.java` (endpoint `POST /processus/{id}/validation`,
Javadoc de classe remise à jour) ; `api/GestionnaireErreursApi.java` (traduction de
`SeuilIndisponibleException` en `500 SEUIL_INDISPONIBLE`).

**Domaine (1 créé)** — `domaine/exception/SeuilIndisponibleException.java`.

**Tests (2 créés, 1 étendu)** — `AiguillageServiceTest`, `ValidationServiceTest`,
`ProcessusControllerIT`.

**Documentation (3)** — `docs/decisions/2026-09-01-seuil-aiguillage-et-borne.md`
(créé) ; `docs/points-en-attente.md` (section sur le point de défaillance unique,
à la demande de l'utilisateur) ; `CLAUDE.md` (§6 RG-08, §11 endpoints et codes,
§15 erreurs interdites, §17 neuf lignes de décisions 4.3).

**Aucune migration.** Le sous-sprint n'ajoute aucune colonne : `parametre_systeme`
porte déjà `SEUIL_AIGUILLAGE_DR` depuis la migration V2, et `nombre_signatures`
existe depuis V3.

---

## Points ouverts

- **`SEUIL_AIGUILLAGE_DR` est un point de défaillance unique.** Inscrit dans
  `docs/points-en-attente.md` avec les mesures attendues de l'exploitation. Le module
  trace le seuil **appliqué** à chaque validation, mais pas le **changement** du
  paramètre : `parametre_systeme` n'a ni horodatage ni auteur (CLAUDE.md §4). Un
  contrôle interne qui voudrait savoir qui a abaissé le seuil ne le trouvera pas ici.
- **La signature reste une mention horodatée doublée d'une empreinte SHA-256**, pas
  une signature au sens juridique. Point ouvert côté DSI, inchangé depuis le 4.2.
- **RG-12 n'est pas implémentée.** Son point d'accroche est marqué dans
  `ValidationService`, à l'endroit exact où il devra s'insérer.

---


---

## Vérification manuelle réelle — quatre services démarrés, jetons Keycloak réels

Faite par l'assistant à la demande de l'utilisateur (« réalise toi-même les étapes de
vérification visuelle et si tout est ok je veux que tu réalises le commit »).

PostgreSQL, `dottel-keycloak` et `rations-kafka` démarrés. Services Identité (8081),
Saisie (8082), Grilles (8083), Workflow (8084) lancés réellement. Jetons obtenus par
grant `password` sur le realm `afb-rations-dev` : `jean_mbarga` (AGENT_UNITE / 00002),
`paul_essama` (CHEF_UNITE_DA / 00002), `sylvie_atangana` (DIRECTEUR_RESEAU_DR),
`claire_nkolo` (ARH).

### Le circuit nominal, de bout en bout

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 1 | `POST /processus/109/soumission` (agent) | `200`, `EN_ATTENTE_DA`, montant 1 500, **1 signature**, `2026/09/etat-rations-00002-202609-p109.pdf` | ✅ acquis du 4.2 |
| 2 | **CT-14** — `POST /processus/109/validation` (chef), seuil 100 000 | `200`, **`statut: CLOTURE`**, `aiguillage: SOUS_SEUIL_CLOTURE_DIRECTE`, **`seuilApplique: 100000`**, `nombreSignatures: 2`, étape `VALIDATION_DA` / `VALIDEE` / ordre 2 | ✅ |
| 3 | Taille du PDF avant / après validation | **12 917 → 13 504 octets** | ✅ le document a bien été enrichi |
| 4 | **Page des visas du PDF** (extraite par `pdftotext`) | `jean_mbarga` / `AGENT_UNITE` / *signé le 01/09/2026 à 17:46* **ET** `paul_essama` / `CHEF_UNITE_DA` / *signé à 17:47*. Cadre **Directeur Réseau (DR) rigoureusement vide** | ✅ **les deux visas coexistent — le document est estampé, pas regénéré** |
| 5 | Base : `processus_mensuel` id 109 | `CLOTURE` / `1500` / **`transmis_comptabilite = f`** | ✅ la clôture ne transmet rien |
| 6 | Base : `etape_workflow` du 109 | 2 lignes — `SOUMISSION_AGENT` ordre 1 (acteur 1) et `VALIDATION_DA` ordre 2 (acteur 2), les deux `VALIDEE` et signées d'empreintes **différentes** | ✅ |
| 7 | Rejouer la validation sur l'état clôturé | `422 TRANSITION_INTERDITE` — *« Cet état est clôturé, donc définitif. Une régularisation passe par un état complémentaire… »*. **PDF inchangé (13 504 octets), compteur toujours à 2** | ✅ |

### La borne, éprouvée sur le service réel

Le seuil a été déplacé en base pour amener un dossier de 1 500 FCFA **exactement à la
borne**, puis un franc au-dessus.

| # | Seuil en base | Montant | Résultat | Conforme |
|---|---|---|---|---|
| 8 | **1 500** | **1 500** (= seuil) | `SOUS_SEUIL_CLOTURE_DIRECTE` → **`CLOTURE`** | ✅ *au plus le seuil, donc clôture* |
| 9 | **1 499** | **1 500** (= seuil + 1) | `ENVOI_DIRECTEUR_RESEAU` → **`EN_ATTENTE_DR`** | ✅ *un franc au-dessus suffit* |

### CT-18 — la modification du seuil prise en compte sans redémarrage

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 10 | Dossier 109 validé avec seuil `100000` | `CLOTURE`, `seuilApplique: 100000` | ✅ |
| 11 | `UPDATE parametre_systeme SET valeur='1000'` — **aucun service redémarré** | — | — |
| 12 | Dossier 110, **même montant de 1 500 FCFA** | **`EN_ATTENTE_DR`**, `aiguillage: ENVOI_DIRECTEUR_RESEAU`, **`seuilApplique: 1000`** | ✅ **le même montant a changé de destination parce que le seuil a changé, et rien d'autre** |

### Les refus

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 13 | Seuil = `'cent mille'`, puis validation | **`500 SEUIL_INDISPONIBLE`** — message nommant le paramètre **et la valeur trouvée**, et rappelant le format attendu | ✅ |
| 14 | Effet de ce refus sur le dossier | **PDF inchangé (12 916 octets)**, statut toujours `EN_ATTENTE_DA`, **compteur toujours à 1** | ✅ **le refus précède l'estampage** |
| 15 | Log du service Workflow | `ERROR … SeuilService : SEUIL INDISPONIBLE : le parametre SEUIL_AIGUILLAGE_DR porte la valeur « cent mille »…` | ✅ préfixe repérable |
| 16 | Paramètre désactivé (`actif = false`) | `500 SEUIL_INDISPONIBLE` — *« aucun paramètre actif de code SEUIL_AIGUILLAGE_DR n'existe »*, log `SEUIL INDISPONIBLE : aucun parametre actif…` | ✅ un paramètre inactif est bien ignoré |
| 17 | Validation par `AGENT_UNITE`, `DIRECTEUR_RESEAU_DR`, `ARH` | **`403 ACCES_REFUSE`** pour les trois | ✅ le DR est refusé *à ce sous-sprint*, comme prévu |
| 18 | Validation sans jeton | `401` | ✅ |
| 19 | `POST /processus/999999/validation` | `404 PROCESSUS_INTROUVABLE` | ✅ |
| 20 | Chef d'unité 00002 sur un dossier de l'unité 00007 | **`403 UTILISATEUR_NON_HABILITE`** — *« rôle CHEF_UNITE_DA sans portée sur l'unité 00007 »* | ✅ le rôle n'est que le premier filtre |

### La chaîne d'audit, lue sur le topic Kafka

Consommation réelle de `rations.audit.evenement`. Les deux événements
`VALIDATION_PROCESSUS` correspondant aux deux cas de borne :

```json
{ "statut": {"avant":"EN_ATTENTE_DA","apres":"CLOTURE"},
  "auteur":"paul_essama", "role":"CHEF_UNITE_DA", "codeUnite":"00002",
  "montantTotal":1500, "seuilApplique":1500,
  "aiguillage":"SOUS_SEUIL_CLOTURE_DIRECTE",
  "empreinte":"SHA-256:1d75bf69…" }

{ "statut": {"avant":"EN_ATTENTE_DA","apres":"EN_ATTENTE_DR"},
  "auteur":"paul_essama", "role":"CHEF_UNITE_DA", "codeUnite":"00002",
  "montantTotal":1500, "seuilApplique":1499,
  "aiguillage":"ENVOI_DIRECTEUR_RESEAU",
  "empreinte":"SHA-256:3952cf41…" }
```

`serviceEmetteur: service-workflow`, `idUtilisateur: 2`, `entiteCible:
processus_mensuel`. **Les deux traces portent le montant ET le seuil appliqué** : un
contrôle interne peut refaire la comparaison lui-même, sans dépendre de la valeur
courante du paramètre. C'est exactement ce que la décision visait.

**CT-04 tenu.** Les refus sont publiés eux aussi :

```
motif: ROLE_INSUFFISANT      login: sylvie_atangana  chemin: /processus/506/validation
motif: ROLE_INSUFFISANT      login: claire_nkolo     chemin: /processus/506/validation
motif: HABILITATION_ABSENTE  login: paul_essama      chemin: /processus/507/validation
```

### Le contrat exposé

`GET /v3/api-docs` du service Workflow rend **exactement cinq opérations** :

```
POST /processus
GET  /processus/{id}
GET  /processus/{id}/etat
POST /processus/{id}/soumission
POST /processus/{id}/validation
```

`POST /processus/{id}/retour` est **absent** : rien n'a été créé par anticipation
pour le sous-sprint 4.4.

### État de la base après vérification

Le seuil a été **remis à `100000`, `actif = true`**. Les quatre dossiers d'essai
laissés en base :

| id | période | statut | montant | transmis | signatures |
|---|---|---|---|---|---|
| 109 | 09/2026 | `CLOTURE` | 1 500 | `f` | 2 |
| 110 | 10/2026 | `EN_ATTENTE_DR` | 1 500 | `f` | 2 |
| 111 | 12/2026 | `CLOTURE` | 1 500 | `f` | 2 |
| 506 | 11/2026 | `EN_ATTENTE_DR` | 1 500 | `f` | 2 |

Les deux dossiers `EN_ATTENTE_DR` serviront de jeu d'essai naturel au sous-sprint 4.4.
Le dossier temporaire créé sur l'unité 00007 pour éprouver la portée d'accès a été
supprimé.

**Aucun écart constaté. Les vingt scénarios sont conformes.**


## Suite

**Sprint 4.4** — second niveau (Directeur Réseau), retour à l'agent avec motif
(RG-10, RG-11), et séparation des tâches (RG-12). Le second niveau ne réutilisera
**pas** l'aiguillage : depuis `EN_ATTENTE_DR`, la seule issue de validation est la
clôture, il n'y a plus de seuil à comparer.

**Sprint 5** — transmission comptable. Les **deux** points de clôture — celui de ce
sous-sprint et celui du 4.4 — devront la déclencher, et **une seule fois** (RG-13).
