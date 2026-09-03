# Résumé Sprint 5.3 — Unicité de transmission et clôture du Sprint 5

**Services :** service-transmission (verrou respecté, consultation), service-workflow (verrou tenu, migration V5)
**Date :** 3 septembre 2026
**Config :** **Opus / effort élevé sur l'intégralité du sous-sprint.** Le guide §1 prévoyait
un retour en Sonnet effort moyen à l'étape 6 ; l'utilisateur a demandé de rester sur Opus
jusqu'à la fin. Aucun changement de modèle en cours de route.

**Statut :** livré, **tests au vert**, **vérification manuelle réelle faite et conforme** —
quatre services démarrés, broker réel, six demandes HTTP simultanées, broker éteint puis
relancé. Voir la section dédiée en fin de document. **Aucun défaut trouvé.**

| Suite | Avant (5.2) | Après (5.3) | Écart |
|---|---|---|---|
| `service-transmission` | 79 | **97** | +18 |
| `service-workflow` | 282 | **290** | +8 |
| **Backend complet** | 604 | **630** | **+26** |

`mvn test` → **BUILD SUCCESS, 630 tests, 0 échec**, aucune régression sur les huit modules.

**Une migration : `V5__processus_reservation_transmission.sql`** — colonne
`date_reservation_transmission` et sa contrainte de cohérence. Additive, appliquée par
Flyway au lancement des tests (« Successfully validated 5 migrations »).

---

## Le cœur du sous-sprint

À la fin du Sprint 5.2, le module publiait et consommait, **mais rien n'empêchait une
seconde publication du même état**. Le point d'accroche de RG-13 était marqué en commentaire
dans `TransmissionService`, et le drapeau `transmis_comptabilite` était écrit *après coup*,
par un autre service que celui qui publie.

La conséquence d'une violation n'est pas technique : la comptabilité reçoit deux fois le même
état, produit deux jeux d'écritures, et **les mêmes bénéficiaires sont payés deux fois**.

Le verrou est désormais posé, et il résiste aux cinq chemins qui menaient à une double
transmission.

---

## Les décisions du sous-sprint

### 1. Les cinq chemins d'une seconde transmission, énumérés avant tout code

| # | Chemin | Fermé par |
|---|---|---|
| 1 | Rejeu de la clôture par le Workflow | la réservation : le second appel rend `DEJA_TRANSMISE` |
| 2 | **Appel manuel** de `POST /transmission/processus/{id}` (CT-22) | idem — d'où le fait que le verrou soit demandé **dans le chemin de requête** du service Transmission |
| 3 | Deux instances traitant la même clôture | le verrou de ligne PostgreSQL |
| 4 | Reprise après l'incident ambigu du Sprint 5.1 | la réservation reste posée sur un échec incertain |
| 5 | Réessai automatique mal classé du 5.1 | le verrou est en aval de la classification |
| 6 | Rejeu du topic côté comptabilité | **hors périmètre** — question ouverte côté DFT |

Le chemin 2 a commandé toute l'architecture : un verrou posé en amont par le Workflow serait
entièrement contourné par un `curl` direct sur le port 8086.

### 2. L'ordre : réserver, publier, confirmer *(question posée, tranchée par l'utilisateur)*

Les deux ordres possibles laissent chacun un risque.

| Ordre | Risque |
|---|---|
| Publier puis marquer (5.1) | si le marquage échoue, la demande suivante **republie** → double paiement, **irréversible** |
| **Réserver puis publier** (retenu) | un état réputé transmis sans l'être → impayé, **visible et réparable** |

**Retenu : réserver d'abord**, parce que son risque se voit et se répare.

> **Ajustement exigé par l'utilisateur, et intégré.** La détectabilité promise avait un
> vrai défaut : « transmis mais statut d'intégration nul » ne distinguait pas un état publié
> il y a 200 ms d'un état publié il y a trois jours dont on ignore l'issue — même signature
> en base. L'alerte se serait noyée dans le trafic normal, ou ne se serait jamais déclenchée.
> D'où la colonne **`date_reservation_transmission`** (migration V5), qui apporte l'âge.

Quatre situations désormais lisibles en base :

| transmis | statut d'intégration | Signification |
|---|---|---|
| false | nul | jamais transmis |
| **true** | **nul** | **réservé, publication non confirmée** ← surveillé par l'âge |
| true | EN_ATTENTE | publié, sans accusé comptable |
| true | INTEGRE / REJETE | la comptabilité a répondu |

### 3. Où vit le verrou *(question posée, tranchée par l'utilisateur)*

**Côté service Workflow, dans `processus_mensuel`**, la seule source de vérité. Le service
Transmission n'a pas de base : une mémoire locale disparaîtrait au premier redémarrage — au
moment précis où un rejeu est le plus probable — et ne serait pas partagée entre deux
instances.

Nouvel endpoint **interne** `PUT /processus/{id}/transmission`, trois étapes dans un seul
corps : `RESERVATION`, `CONFIRMATION`, `LIBERATION`.

> **Point de vigilance soulevé par l'utilisateur et vérifié.** Cet endpoint reste
> strictement interne, jamais routé par la passerelle — laquelle ne définit aujourd'hui
> **aucune route** (Sprint 8). Il ne contredit pas le contrat §7 (« le service Transmission
> n'expose aucun endpoint de déclenchement ») : il ne déclenche rien, il tient un drapeau,
> et il vit dans le service Workflow, pas dans le service Transmission.

### 4. La résistance à la concurrence : verrou de ligne PostgreSQL

`SELECT ... FOR UPDATE` (`LockModeType.PESSIMISTIC_WRITE`) sur la ligne du processus. Deux
transactions concurrentes sont sérialisées : la seconde attend le commit de la première,
puis lit le drapeau **déjà posé**.

**Pourquoi pas un `UPDATE ... WHERE transmis_comptabilite = false`** — atomique lui aussi,
et plus court. Parce qu'il écrirait **par-dessus** l'entité : ni le mutateur en visibilité
paquet, ni l'arbitrage de `VerrouTransmission` ne seraient traversés, l'invariant « toute
mutation de ce drapeau passe par le domaine » deviendrait une convention au lieu d'être
vérifié par le compilateur, et le delta d'audit serait perdu.

Transactions **courtes et sans appel réseau** (`REQUIRES_NEW`) : tenir la ligne verrouillée
pendant la publication Kafka — sept secondes au pire — bloquerait toute lecture concurrente
du même dossier.

### 5. La libération, uniquement sur preuve

`ResultatPublication` est scindé, et la distinction **est** la règle :

| Issue | Un message a-t-il atteint le broker ? | Verrou |
|---|---|---|
| `Publiee` | oui, confirmé | **confirmé** |
| `EchecAvantEnvoi` | **non** — sérialisation impossible, `send()` qui lève dès l'appel | **libéré**, reprise possible |
| `EchecIssueIncertaine` | **peut-être** — délai d'accusé dépassé | **conservé** |

Symétrique exact de `ResultatDemandeTransmission` (5.1), et même règle : **ne jamais
republier ce qui a pu partir**. Le cas `EchecAvantEnvoi` couvre le plus fréquent — broker
arrêté : y laisser le verrou figerait un état clôturé, réputé transmis et jamais payé, alors
qu'on *sait* qu'il n'est pas parti.

### 6. Une seconde demande n'est pas une erreur

`200` avec `resultat: DEJA_TRANSMIS`, jamais un code d'erreur. Un rejeu légitime existe, et
une erreur technique ferait croire à une panne — donc inciterait à réessayer, c'est-à-dire au
geste exact à décourager. Idiome `APPLIQUE` / `DEJA_APPLIQUE` du Sprint 5.2.

Côté Workflow, une **quatrième issue** `DejaTransmise` entre dans le type scellé
`ResultatDemandeTransmission` ; le `switch` exhaustif a désigné lui-même les deux endroits à
corriger. Le champ `transmis` y vaut **vrai** : du point de vue du valideur comme de la
comptabilité, l'état est bien parti — une fois, et une seule. Le rendre faux enverrait
quelqu'un le retransmettre.

### 7. La consultation ouverte à l'ARH *(question posée, l'utilisateur a renversé ma recommandation)*

Ma recommandation était d'ajouter l'ARH aux rôles de `GET /processus/{id}`. **L'utilisateur
a tranché l'inverse, et à raison** : cet endpoint rend le dossier complet — montant, motif de
retour, type, période — et l'ARH a une portée **nationale** (Sprint 1.1). Elle aurait obtenu
la lecture intégrale de tous les dossiers de toutes les unités pour un besoin de quatre
champs, avec en prime un second chemin d'accès concurrent du Reporting du Sprint 6.

**Retenu : un endpoint interne étroit** `GET /processus/{id}/integration`, six champs, rien
du dossier. Même parti qu'au Sprint 1.3 pour `GET /identite/habilitation`.

### 8. Cinq situations, jamais un champ vide sans explication

Le contrat n'expose que `statutIntegration`, à trois valeurs, **nul dans deux situations
sans rapport**. D'où `situation` et `message`, ajoutés **en fin** de réponse :

| Situation | Ce que le message dit |
|---|---|
| `NON_TRANSMIS` | normal si l'état n'est pas clôturé ; sinon, transmission manquée à signaler |
| `PUBLICATION_NON_CONFIRMEE` | récent = envoi en cours ; ancien = issue incertaine, à lever à la main |
| `EN_ATTENTE_ACCUSE` | publié, la comptabilité n'a pas encore répondu |
| `INTEGRE` | pris en charge, avec sa référence |
| `REJETE` | refusé, avec son motif ; traitement avec la DFT |

Le refus de portée est **relayé tel quel** en `403 UTILISATEUR_NON_HABILITE`, distinct
d'`ACCES_REFUSE` (rôle) — distinction du Sprint 4.4.

---

## Le défaut de sécurité trouvé en chemin, et corrigé

Le chemin `/processus/*/integration` porte désormais **deux endpoints de natures opposées** :
le `PUT` de l'accusé comptable, gardé par le secret partagé du Sprint 5.2, et le `GET` du
statut, appelé sur un jeton.

La chaîne de sécurité dédiée du 5.2 déclarait `securityMatcher(CHEMIN_INTEGRATION)` — **sans
verbe**. Elle aurait donc capté le `GET` et lui aurait réclamé un secret que le service
Transmission ne présente pas sur cette route : refus en `401`, sans que rien n'explique
pourquoi. Le `securityMatcher` est restreint à `PUT`
(`PathPatternRequestMatcher.pathPattern(HttpMethod.PUT, ...)`).

**Même famille que les défauts des Sprints 5.1 et 5.2** — le bean `ObjectMapper` absent,
puis `@EnableKafka` manquant : un câblage qui ne se voit qu'en assemblant, invisible aux
tests unitaires.

---

## Les onze tests du sous-sprint

**Unicité** (`UniciteTransmissionTest`, service-transmission, 8 tests)

| # | Test | Ce qu'il établit |
|---|---|---|
| 1 | première transmission | le message part, le verrou est posé **puis confirmé** |
| 2 | seconde demande | aucune publication, réponse explicite contenant « déjà transmis » et « RG-13 » |
| 3 | **le topic ne reçoit qu'un message après deux demandes** | le résultat observable côté comptabilité, pas l'état interne |
| 5a | échec **prouvé** sans envoi | verrou libéré, **et la reprise aboutit** |
| 5b | échec d'issue **incertaine** | verrou conservé, la reprise est refusée même broker revenu |
| — | verrou indisponible | rien n'est publié, `503` — refus conservateur |
| — | refus sur lecture de l'en-tête | l'économie fonctionne sans même solliciter le verrou |

| # | Test | Ce qu'il établit |
|---|---|---|
| 4 | **huit demandes simultanées** | un seul message publié, sept refus explicites |

**Le verrou contre la vraie base** (`VerrouTransmissionServiceIT`, service-workflow, 6 tests,
transactions réellement commitées) : réservation puis confirmation, seconde réservation
refusée, état non clôturé refusé en `422`, libération rendant la reprise possible, libération
refusée après accusé, et **huit transactions PostgreSQL simultanées → une seule réservation
accordée**.

**Consultation** (`ConsultationIntegrationServiceTest`, 10 tests) : les tests 6 à 10 du guide,
plus la cinquième situation née du verrou, le rejet sans motif, et les trois refus (404, 403
portée, 403 rôle, 503).

**Bout en bout** (`CircuitCompletIT`, test 11) : déclenchement → saisie consolidée →
soumission signée → validation → clôture → transmission avec verrou réel → **seconde demande
refusée** → accusé comptable appliqué → `INTEGRE` → libération refusée.

> **La part non automatisable du test 11** : le passage par un vrai broker Kafka. Le pom
> épingle `spring-kafka` en 3.3.0 alors que les `kafka-clients` sont en 4.2.1, et
> `@EmbeddedKafka` échoue sur une classe déplacée par Kafka 4 (dette **T-01**, arbitrée au
> Sprint 5.2). Elle se fait à la vérification manuelle, comme le guide le prescrit à sa §8.

---

## Ce que la cartographie a vérifié (étape 6)

`py -3.14 -m graphify update .` → 5357 nœuds, 13181 arêtes, 259 communautés.

| Contrôle | Résultat |
|---|---|
| `service-transmission` accède-t-il à une base ? | **Non.** Aucune `DataSource`, `JpaRepository`, `@Entity` ni Flyway — seulement des commentaires disant qu'il n'y en a pas |
| Classe d'écriture comptable ? | **Aucune** dans tout le projet |
| Termes `ecriture` / `debit` / `credit` / `cbs` en code actif ? | **Aucun.** Uniquement des messages et du javadoc expliquant ce que le module ne fait pas, ou « écriture » au sens d'écrire sur disque |
| Endpoint de consultation du contrat | **Présent** : `GET /transmission/processus/{id}` |
| Endpoint créé par anticipation ? | **Aucun.** `service-reporting` n'a aucun contrôleur |

Décompte du contrat tenu : identité 3, saisie 5, grilles 5, workflow 6, transmission 1,
reporting 0. Plus **six endpoints internes** hors passerelle, dont deux nés ici.

---

## Écarts consignés (relevés à l'étape 6, portés en documentation)

* Le service Workflow compte désormais **trois** endpoints internes au lieu d'un.
* `StatutTransmissionResponse` ajoute `situation` et `message` au contrat §7.
* `TransmissionResponse` (POST interne) ajoute `resultat` et `message` ; `partition` et
  `offset` deviennent nullables — nuls sur un refus, où rien n'est parti.
* Délai de lecture Workflow → Transmission : **20 → 35 s** ; borne défensive d'attente :
  **40 → 55 s**. Conséquence arithmétique des deux appels du verrou (pire cas de l'appelé :
  17 → 27 s), pas un réglage.
* Colonne `date_reservation_transmission` ajoutée au dictionnaire §4.
* **`transmis_comptabilite` est désormais posé avant la publication**, ce qui révise une
  phrase de CLAUDE.md §15 écrite au Sprint 5.1. Corrigée.
* `EnregistrementTransmission` (5.1) **supprimé** : le Workflow ne pose plus le drapeau après
  coup.

---

## Risque résiduel assumé et consigné

Un état dont la publication se termine de façon **ambiguë** reste marqué transmis sans
l'être. Il n'est **jamais rejoué automatiquement** — aucune reprise programmée n'est
possible, le realm n'ayant pas de compte de service — et se lève à la main **après
vérification du topic**.

```sql
SELECT id, code_unite, mois_paiement, annee_paiement, date_reservation_transmission
  FROM processus_mensuel
 WHERE statut = 'CLOTURE' AND transmis_comptabilite = TRUE
   AND statut_integration IS NULL
   AND date_reservation_transmission < NOW() - INTERVAL '15 minutes';
```

Deux préfixes de journal l'accompagnent : `TRANSMISSION ISSUE INCERTAINE` (l'état a
peut-être été publié) et `CONFIRMATION TRANSMISSION MANQUEE` (l'état **est** publié, le
verrou n'a pas pu être confirmé — fausse alerte à venir).

Comme au Sprint 5.1, **le journal d'audit ne peut pas servir de filet quand la panne est
Kafka lui-même** : la trace passe par le même broker. La requête SQL fait foi. Consigné dans
`docs/points-en-attente.md`, en surveillance prioritaire.

---

## Fichiers du sous-sprint

**service-workflow — créés**

| Fichier | Rôle |
|---|---|
| `db/migration/V5__processus_reservation_transmission.sql` | horodatage de réservation + contrainte |
| `domaine/VerrouTransmission.java` | arbitrage des trois gestes, table des états |
| `domaine/exception/EtatNonClotureException.java` | `422 ETAT_NON_CLOTURE` |
| `application/VerrouTransmissionService.java` | les trois transactions courtes, l'audit |
| `application/ResultatVerrouTransmission.java` | les cinq issues |
| `api/dto/EtapeVerrouTransmission.java`, `VerrouTransmissionRequest/Response.java` | l'endpoint interne |
| `api/dto/IntegrationProcessusResponse.java` | le bloc d'intégration, six champs |
| `src/test/.../VerrouTransmissionServiceIT.java` | le verrou contre PostgreSQL |

**service-workflow — modifiés** : `ProcessusMensuel` (trois gestes en visibilité paquet à la
place d'un), `ProcessusMensuelRepository` (verrou de ligne), `ProcessusService`
(`consulterIntegration`), `ProcessusController` (deux endpoints), `SecurityConfig` (verbe
`PUT`), `GestionnaireErreursApi`, `DeclenchementTransmission`, `ResultatDemandeTransmission`,
`ResultatTransmissionCloture`, `TransmissionHttpClient`, `ReponseTransmission`,
`ConfigurationTransmission`, `ValidationService` (javadoc). **Supprimé** :
`EnregistrementTransmission.java`.

**service-transmission — créés**

| Fichier | Rôle |
|---|---|
| `application/UniciteTransmissionService.java` | RG-13 vu du service qui publie |
| `application/VerrouTransmissionClient.java`, `ResultatVerrou.java` | le port et ses trois issues |
| `application/ConsultationIntegrationService.java` | les cinq situations |
| `application/IntegrationProcessus.java`, `IntegrationProcessusClient.java`, `ResultatIntegrationProcessus.java`, `SituationIntegration.java`, `StatutIntegrationConsulte.java` | la lecture du bloc d'intégration |
| `api/dto/StatutTransmissionResponse.java` | le corps du `GET` du contrat |
| `domaine/exception/AccesHorsPorteeException.java` | `403 UTILISATEUR_NON_HABILITE` |
| `infrastructure/workflow/VerrouTransmissionHttpClient.java`, `IntegrationProcessusHttpClient.java` | les deux appels sortants |
| `src/test/.../UniciteTransmissionTest.java`, `ConsultationIntegrationServiceTest.java` | 18 tests |

**service-transmission — modifiés** : `TransmissionService` (réservation, publication,
confirmation), `ResultatPublication` (deux natures d'échec), `ResultatTransmission` (type
scellé), `EtatValideProducer`, `TransmissionResponse`, `TransmissionController`,
`GestionnaireErreursApi`.

**Documentation** : `CLAUDE.md` (§4, §9.1, §11, §15, §17 — 13 décisions ajoutées),
`docs/decisions/2026-09-03-unicite-de-transmission-et-verrou.md`,
`docs/points-en-attente.md`.

---

## Vérification manuelle — faite le 3 septembre 2026, conforme

**Montage** : `rations-postgres`, `rations-kafka` et `dottel-keycloak` en conteneurs ;
`service-identite` (8081), `service-saisie` (8082), `service-workflow` (8084) et
`service-transmission` (8086) démarrés par `mvn spring-boot:run`. Jetons réels obtenus du
realm `afb-rations-dev` (`paul_essama` chef d'unité 00002, `claire_nkolo` ARH,
`agnes_tchinda` DRH, `pierre_belinga` agent sans profil local). Migration **V5 appliquée**
par Flyway au démarrage, colonne et contrainte vérifiées en base.

### Les onze contrôles

| # | Contrôle | Résultat |
|---|---|---|
| A | Consultation d'un état clôturé **jamais transmis** (1318) | `situation: NON_TRANSMIS`, tous les champs nuls **avec l'explication de pourquoi** |
| B | **CT-22 — deux demandes successives** (1318) | 1ʳᵉ : `resultat: TRANSMIS`, offset 3. 2ᵈᵉ : **`200` avec `resultat: DEJA_TRANSMIS`** et le message nommant l'état, l'unité et RG-13 |
| C | **Comptage sur le topic** après ces deux demandes | **1 seul message** portant `idProcessus 1318` |
| D | État en base après transmission | `transmis_comptabilite = t`, `statut_integration = EN_ATTENTE`, **`date_reservation_transmission` renseignée** (colonne V5) |
| E | Consultation après transmission | `situation: EN_ATTENTE_ACCUSE`, référence et date nulles, message expliquant pourquoi |
| F | **Accusé comptable publié à la main** sur `rations.etat.accuse` | statut porté à `INTEGRE`, référence `CPT-2027-05-001318` et date de traitement inscrites |
| G | Consultation par le **chef d'unité** puis par l'**ARH** | réponses identiques, `situation: INTEGRE` — la portée nationale de l'ARH fonctionne |
| H | Situation `REJETE` (état 1317) | motif de rejet rendu, message renvoyant vers la DFT |
| I | **Les trois refus** | DRH → `403 ACCES_REFUSE` (rôle) ; agent sans profil → `403 UTILISATEUR_NON_HABILITE` (portée) ; id inconnu → `404 PROCESSUS_INTROUVABLE`. **Trois codes distincts, comme prévu** |
| J | **Broker éteint**, transmission de 1319 | `503 PUBLICATION_ECHOUEE` en 3,5 s ; journal `PUBLICATION ETAT VALIDE EN ECHEC ... Le message n'a jamais ete remis au client Kafka` ; **verrou libéré** : `transmis_comptabilite = f`, horodatage effacé |
| K | **Reprise après retour du broker** | `resultat: TRANSMIS`, offset 4 — la libération a bien rendu la reprise possible |

### Le contrôle décisif : la concurrence réelle

**Six requêtes HTTP simultanées** sur le même état clôturé, deux fois (états 1320 et 109) :

```
fil 1 : TRANSMIS
fil 2 : DEJA_TRANSMIS
fil 3 : DEJA_TRANSMIS
fil 4 : DEJA_TRANSMIS
fil 5 : DEJA_TRANSMIS
fil 6 : DEJA_TRANSMIS
```

Comptage sur le topic après coup : **1 seul message par état**. Le verrou de ligne
PostgreSQL a départagé les six fils en **33 millisecondes** (écart entre
`datePremiereTransmission` et la trace du premier refus).

### Traçabilité constatée sur le topic d'audit

| Action | Occurrences |
|---|---|
| `TRANSMISSION_COMPTABLE` (confirmations) | 7 |
| `TRANSMISSION_ETAT_VALIDE` (publications) | 7 |
| `TRANSMISSION_DOUBLON_REFUSEE` | 6 |
| `INTEGRATION_COMPTABLE` (accusés appliqués) | 4 |

La trace d'un refus de doublon porte tout ce qu'un contrôle interne demande :

```json
{"motif":"ETAT_DEJA_TRANSMIS","datePremiereTransmission":"2026-09-03T21:23:26.733913",
 "statutIntegration":null,"referenceComptable":null,"codeUnite":"00002",
 "moisPaiement":7,"anneePaiement":2027,"montantTotal":2000}
```

### Deux observations de la vérification

**1. Le comportement du Sprint 5.2 s'est confirmé par accident.** Mon premier accusé,
publié via PowerShell, portait un **BOM UTF-8** en tête. Le consommateur l'a traité
exactement comme prévu : `ACCUSE ILLISIBLE`, message avancé, **aucun blocage de la
partition**, et les accusés suivants sont passés. C'est précisément le scénario que la
désérialisation en chaîne du 5.2 existe pour éviter. Republié sans BOM, l'accusé s'est
appliqué normalement.

**2. `TRANSMISSION_RESERVATION_LIBEREE` n'apparaît pas sur le topic** — parce que la
libération a eu lieu **pendant que Kafka était éteint**. La trace d'audit passe par le même
broker, en publication non bloquante. C'est le constat déjà consigné au Sprint 5.1 :
*quand la panne EST Kafka, la trace d'audit de l'échec tombe avec lui*. Les deux autres
filets ont survécu — le journal au préfixe (`Reservation de transmission de l'etat 1319
levee : aucun evenement n'est parti`) et l'état en base (drapeau à faux). Rien à corriger,
mais un argument de plus pour l'outbox transactionnel ouvert depuis le Sprint 1.3, et une
raison de plus de ne pas fonder la supervision sur le journal d'audit.

### Un détail attendu, vérifié

Les états transmis **avant** ce sprint (1316, 1317, 1321) portent `dateTransmission: null`
en consultation : la migration V5 est purement additive et ne réécrit pas l'historique,
comme son en-tête le dit. Le message de la consultation reste correct sans cette valeur.
