# Unicité de transmission : l'ordre réserver / publier / confirmer, et où vit le verrou

**Date :** 3 septembre 2026
**Sprint :** 5.3, unicité de transmission et clôture du Sprint 5
**Statut :** tranchée avec l'utilisateur avant tout codage
**Règles :** RG-13, US-12, CT-22

---

## 1. Le problème

RG-13 dit qu'un état validé n'est transmis qu'une seule fois. La conséquence d'une
violation n'est pas technique : la comptabilité reçoit deux fois le même état, produit
deux jeux d'écritures, et **les mêmes bénéficiaires sont payés deux fois**.

À la fin du Sprint 5.2, il n'existait aucun verrou. Le point d'accroche était marqué en
commentaire dans `TransmissionService`, et `transmis_comptabilite` était écrit *après
coup*, dans un autre service que celui qui publie.

### Les chemins d'une seconde transmission, énumérés avant tout code

| # | Chemin | Fermé par |
|---|---|---|
| 1 | Rejeu de la clôture par le service Workflow | la réservation : le second appel rend `DEJA_TRANSMISE` |
| 2 | **Appel manuel** de `POST /transmission/processus/{id}` (CT-22) | idem — d'où le fait que le verrou soit demandé **dans le chemin de requête** du service Transmission, et non en amont par le Workflow |
| 3 | Deux instances traitant la même clôture | le verrou de ligne PostgreSQL : la seconde transaction attend le commit de la première, puis lit le drapeau déjà posé |
| 4 | Reprise après l'incident ambigu du Sprint 5.1 | la réservation **reste posée** sur un échec incertain : elle refuse la reprise |
| 5 | Réessai automatique mal classé du Sprint 5.1 | le verrou est en aval de la classification : il protège même si celle-ci se trompe |
| 6 | Rejeu du topic par le module de comptabilisation | **hors périmètre du module** : rien ne dit qu'il dédoublonne par `idProcessus`, question ouverte côté DFT |

Le chemin 2 est décisif : il impose que le verrou soit revendiqué *pendant* la requête au
service Transmission. Un verrou posé en amont par le Workflow serait entièrement
contourné par un `curl` direct sur le port 8086.

---

## 2. Décision 1 — l'ordre : réserver, publier, confirmer

Les deux ordres possibles laissent chacun un risque résiduel ; il fallait choisir lequel
porter.

| Ordre | Risque si l'étape 2 échoue |
|---|---|
| Publier puis marquer (Sprint 5.1) | l'état reste réputé non transmis et la demande suivante **republie** → double paiement, **irréversible** |
| **Réserver puis publier** (retenu) | l'état est réputé transmis sans l'être → un état impayé, **visible et réparable** |

Le second est retenu **parce que son risque se voit et se répare**, et l'autre non.

Trois gestes, dans cet ordre :

```
RESERVER   transmis_comptabilite : false -> true, horodatage posé
           statut_integration    : reste NUL
               |
               |  publication sur rations.etat.valide
               v
CONFIRMER  statut_integration    : NUL -> EN_ATTENTE

LIBERER    transmis_comptabilite : true -> false, horodatage effacé
           (uniquement sur la PREUVE qu'aucun événement n'est parti)
```

La réservation est placée **le plus tard possible** : après la lecture de l'en-tête, le
contrôle de statut, la lecture du détail et le contrôle de complétude. Tous les refus
possibles sont épuisés avant qu'un état ne soit réputé transmis.

### Réduction du risque résiduel : la libération sur preuve

`ResultatPublication` est scindé en deux échecs, et la distinction **est** la règle :

| Issue | Un message a-t-il atteint le broker ? | Verrou |
|---|---|---|
| `Publiee` | oui, confirmé | **confirmé** |
| `EchecAvantEnvoi` | **non** — sérialisation impossible, `send()` lève dès l'appel | **libéré**, une reprise est possible |
| `EchecIssueIncertaine` | **peut-être** — délai d'accusé dépassé, échec de livraison | **conservé** |

C'est le symétrique exact de `ResultatDemandeTransmission` côté Workflow (Sprint 5.1). La
règle est la même des deux côtés : **ne jamais republier ce qui a pu partir**.

Le cas `EchecAvantEnvoi` couvre le plus fréquent — broker arrêté, `send()` lève après
`max.block.ms`. Y laisser le verrou figerait un état clôturé, réputé transmis et jamais
payé, alors qu'on *sait* qu'il n'est pas parti.

### Détectabilité : l'horodatage de réservation (migration V5)

Sans lui, deux situations ont **exactement la même signature en base** :

* un état publié il y a 200 ms, dont l'accusé comptable va arriver ;
* un état publié il y a trois jours, dont personne ne sait s'il est parti.

Une supervision bâtie sur « `transmis_comptabilite = TRUE AND statut_integration IS NULL` »
se noierait donc dans le trafic normal, ou ne se déclencherait jamais. La colonne
`date_reservation_transmission` apporte l'âge, qui est ce qui les sépare :

```sql
SELECT id, code_unite, mois_paiement, annee_paiement
  FROM processus_mensuel
 WHERE statut = 'CLOTURE'
   AND transmis_comptabilite = TRUE
   AND statut_integration IS NULL
   AND date_reservation_transmission < NOW() - INTERVAL '15 minutes';
```

**Une seule colonne, pas un état de réservation dédié** : le couple
(`transmis_comptabilite`, `statut_integration`) porte déjà les quatre situations, il ne
manquait que le temps.

| transmis | statut d'intégration | Signification |
|---|---|---|
| false | nul | jamais transmis |
| **true** | **nul** | **réservé, publication non confirmée** |
| true | EN_ATTENTE | publié, la comptabilité n'a pas encore répondu |
| true | INTEGRE / REJETE | la comptabilité a répondu |

---

## 3. Décision 2 — où vit le verrou, et la résistance à la concurrence

**Le verrou vit côté service Workflow**, qui détient `processus_mensuel`, la seule source
de vérité. Le service Transmission n'a pas de base : une mémoire locale disparaîtrait au
premier redémarrage — c'est-à-dire au moment précis où un rejeu est le plus probable — et
ne serait de toute façon pas partagée entre deux instances. C'est le raisonnement du
Sprint 5.2 pour l'idempotence de l'accusé, appliqué à l'autre sens de l'échange.

Nouvel endpoint **interne** : `PUT /processus/{id}/transmission`, trois étapes
(`RESERVATION`, `CONFIRMATION`, `LIBERATION`) dans un seul corps.

* **Un seul endpoint, trois étapes** : les trois gestes portent sur la même ressource — le
  drapeau de transmission d'un état. Trois routes auraient découpé un seul mécanisme, et
  l'ordre dans lequel elles s'appellent aurait dû être reconstitué mentalement.
* **`PUT`** parce que chaque étape est idempotente, et le verbe le dit avant tout
  commentaire (idiome du Sprint 5.2 pour `PUT /processus/{id}/integration`).
* **`200` y compris sur `DEJA_TRANSMISE`** : une seconde demande n'est pas forcément une
  anomalie, un rejeu légitime existe, et une erreur technique ferait croire à une panne.
  Le champ `resultat` porte la distinction — idiome `APPLIQUE` / `DEJA_APPLIQUE`.
* **Jeton relayé, pas de secret partagé** : cet appel naît d'une requête HTTP d'un
  valideur qui vient de clôturer, un utilisateur final existe. Le secret du Sprint 5.2 ne
  s'imposait que faute d'utilisateur derrière un message Kafka.

### Le mécanisme : verrou de ligne PostgreSQL

`ProcessusMensuelRepository.verrouillerPourTransmission` charge la ligne en
`SELECT ... FOR UPDATE` (`LockModeType.PESSIMISTIC_WRITE`). Deux transactions concurrentes
sont alors sérialisées : la seconde **attend** le commit de la première, puis relit le
drapeau **déjà posé** et se voit refuser.

**Pourquoi un verrou plutôt qu'un `UPDATE ... WHERE transmis_comptabilite = false`.** Les
deux sont atomiques. Mais l'ordre SQL de masse écrirait *par-dessus* l'entité : ni le
mutateur en visibilité paquet de `ProcessusMensuel`, ni l'arbitrage de
`VerrouTransmission` ne seraient traversés, et l'invariant « toute mutation de ce drapeau
passe par le domaine » deviendrait une convention au lieu d'être vérifié par le
compilateur. Le delta d'audit — l'avant et l'après — ne serait pas lisible non plus.

Les trois gestes vivent dans des **transactions courtes et sans appel réseau**
(`REQUIRES_NEW`) : tenir la ligne verrouillée pendant la publication Kafka — sept secondes
au pire — bloquerait toute lecture concurrente du même dossier.

### Conséquence : le Workflow ne pose plus le drapeau après coup

`EnregistrementTransmission` (Sprint 5.1) est **retiré**. Le cycle de vie du drapeau est
piloté de bout en bout par le service qui publie, seul à savoir ce qui est réellement
parti. La trace d'audit `TRANSMISSION_COMPTABLE` est publiée à la confirmation, là où elle
atteste d'un fait et non d'une intention.

### Conséquence : le budget de temps

Deux appels s'ajoutent au chemin. Pire cas de l'appelé, par construction :

| Opération | Borne |
|---|---|
| en-tête au service Workflow | 5 s |
| détail au service Saisie | 5 s |
| **réservation du verrou** | 5 s |
| publication et attente de l'accusé | 7 s |
| **confirmation du verrou** | 5 s |
| **total** | **27 s** |

Le délai de lecture Workflow → Transmission passe donc de 20 à **35 s**, et la borne
défensive d'attente du fil appelant de 40 à **55 s**. Ce n'est pas un réglage
d'environnement mais une conséquence arithmétique : un délai trop court couperait la
réponse au moment où elle importe le plus — après la publication, avant la confirmation —
et transformerait une panne claire en situation **ambiguë**, donc non réessayable. Cas
nominal inchangé, environ 200 ms.

---

## 4. Décision 3 — la consultation ouverte à l'ARH, sans élargir le dossier

Le contrat §7 ouvre `GET /transmission/processus/{id}` aux « rôles ARH et circuit ». Or le
service Transmission n'a pas de base, et `GET /processus/{id}` du Workflow est réservé aux
seuls rôles du circuit.

**Option écartée : ajouter l'ARH aux rôles de `GET /processus/{id}`.** Cet endpoint rend le
dossier complet — montant total, motif du retour en cours, type, période. L'ARH ayant une
portée **nationale** (Sprint 1.1), elle aurait obtenu la lecture intégrale de tous les
dossiers de toutes les unités pour un besoin qui n'en demandait que quatre champs. Le
module a déjà tranché ainsi au Sprint 1.3 : `GET /identite/habilitation` est
volontairement étroit et ne répond qu'à une question précise. S'y ajoutait une collision
avec le Sprint 6 Reporting, lui aussi ouvert à l'ARH : deux chemins d'accès au même type
d'information finissent par diverger.

**Option retenue : un endpoint interne étroit**, `GET /processus/{id}/integration`, qui ne
rend que le bloc d'intégration (six champs) et rien du dossier. Portée d'accès vérifiée
unité par unité auprès du service Identité, comme partout ailleurs.

### Effet de bord de sécurité, corrigé au passage

Le même chemin `/processus/*/integration` porte désormais deux endpoints de natures
opposées : le `PUT` de l'accusé comptable, gardé par le secret partagé du Sprint 5.2, et le
`GET` du statut, appelé sur un jeton. La chaîne de sécurité dédiée du Sprint 5.2, qui
matchait le chemin **tous verbes confondus**, aurait capté le `GET` et lui aurait réclamé
un secret que le service Transmission ne présente pas sur cette route — refus en `401`,
sans que rien n'explique pourquoi. Le `securityMatcher` est donc restreint au verbe `PUT`.

### Cinq situations, jamais un champ vide sans explication

Le contrat n'expose que `statutIntegration`, à trois valeurs. Or celui-ci est **nul** dans
deux situations qui n'ont rien à voir. Un champ `situation` et un champ `message` sont
donc ajoutés **en fin** de réponse — ajout additif, comme `motifRetour` au Sprint 4.4 et
`manques` au Sprint 4.2 :

| Situation | Ce que le lecteur doit comprendre |
|---|---|
| `NON_TRANSMIS` | normal tant que l'état n'est pas clôturé ; anormal sinon |
| `PUBLICATION_NON_CONFIRMEE` | récent = envoi en cours ; ancien = issue incertaine, à lever à la main |
| `EN_ATTENTE_ACCUSE` | publié, la comptabilité n'a pas encore répondu |
| `INTEGRE` | pris en charge, avec sa référence |
| `REJETE` | refusé, avec son motif ; le traitement se fait avec la DFT |

Le refus de portée est **relayé tel quel** en `403 UTILISATEUR_NON_HABILITE`, distinct
d'`ACCES_REFUSE` (rôle insuffisant) : la distinction du Sprint 4.4 vaut ici aussi, les deux
appellent deux gestes différents.

---

## 5. Ce qui a été vérifié, et ce qui ne l'est pas

**Prouvé par les tests** : huit demandes concurrentes au service Transmission ne
produisent qu'un seul message ; huit transactions concurrentes contre la vraie base
PostgreSQL ne produisent qu'une seule réservation ; un échec prouvé sans envoi libère le
verrou et la reprise aboutit ; un échec ambigu le conserve et la reprise est refusée.

**Non automatisable** : le bout en bout par un vrai broker Kafka. Le pom épingle
`spring-kafka` en 3.3.0 alors que les `kafka-clients` sont en 4.2.1, et `@EmbeddedKafka`
échoue sur une classe déplacée par Kafka 4 (dette T-01, arbitrée au Sprint 5.2). Cette part
du test 11 se fait à la vérification manuelle sur le conteneur réel, comme le guide le
prescrit.

**Risque résiduel assumé, et consigné** : un état dont la publication se termine de façon
ambiguë reste marqué transmis sans l'être. Il ne se rejoue pas automatiquement — aucune
reprise programmée n'est possible, le realm n'ayant pas de compte de service — et se lève
à la main après vérification du topic. La requête de supervision ci-dessus est le seul
moyen de le détecter ; elle doit être surveillée au même titre que
`statut = 'CLOTURE' AND transmis_comptabilite = false`.

---

## 6. Liens

* `docs/decisions/2026-09-02-transmission-comptable-et-reprise.md` (Sprint 5.1)
* `docs/decisions/2026-09-02-consommation-accuse-comptable.md` (Sprint 5.2)
* `docs/dispositifs_provisoires.md` §3bis (secret partagé)
* `docs/points-en-attente.md` (dette T-01, compte de service Keycloak, dédoublonnage côté DFT)
