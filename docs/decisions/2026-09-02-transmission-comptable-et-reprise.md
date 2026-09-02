# Transmission comptable : emplacement du statut, origine de la charge, échec après clôture

**Sprint 5.1 — 2 septembre 2026.** Décisions arbitrées avec l'utilisateur avant tout
codage, sur les cinq points que le guide laissait ouverts.

---

## 1. Où vit le statut d'intégration comptable

### L'écart

Le contrat d'API (§7, §7.2) expose, par `GET /transmission/processus/{id}`, un
**statut d'intégration à trois valeurs** (`EN_ATTENTE`, `INTEGRE`, `REJETE`), une
**référence comptable**, une **date de traitement** et un **motif** en cas de rejet.

Le dictionnaire de données (CLAUDE.md §4) ne prévoit qu'un **booléen**
`transmis_comptabilite` sur `processus_mensuel`. Et le service Transmission **n'a pas
de base propre** (CLAUDE.md §3). Un booléen ne porte ni trois valeurs, ni une
référence, ni une date : il fallait trancher.

### La décision — colonnes additionnelles sur `processus_mensuel`

Migration **`V4__processus_statut_integration.sql`** dans service-workflow, purement
additive : `statut_integration`, `reference_comptable`, `date_traitement`,
`motif_integration`. Le service Transmission les lit et les écrit **par l'API du
service Workflow, jamais par sa base** (diagramme AR04).

> **Écart au guide, signalé.** Le guide annonce un `V3__statut_integration.sql`. **V3
> est déjà pris** par `piece_jointe.nombre_signatures` (Sprint 4.2). C'est donc V4.

### Pourquoi

1. **`transmis_comptabilite` vit déjà sur cette ligne.** Le drapeau de RG-13 et le
   statut d'intégration décrivent le même fait — où en est cet état vis-à-vis de la
   comptabilité. Les séparer dans deux bases laisserait deux moitiés de vérité à tenir
   cohérentes à la main, sans transaction commune pour les y obliger.
2. **Le Sprint 5.2 le suppose déjà** : son guide pose que la mise à jour du processus
   se fait « par appel à l'API du service Workflow, jamais par accès direct à sa base ».
3. **Une base propre à Transmission** aurait obligé à corriger CLAUDE.md §3
   (« Transmission : topics Kafka », sans base) et §4 (dix tables), à créer une onzième
   table, une base et son initialisation. Beaucoup d'architecture pour quatre colonnes.

### Tout est nullable, délibérément

Un état jamais transmis n'a **aucun** statut d'intégration — pas `EN_ATTENTE`.
`EN_ATTENTE` signifie « publié sur `rations.etat.valide`, la comptabilité n'a pas
encore accusé réception » : c'est une étape de l'échange, pas un état initial. Une
valeur par défaut ferait croire, sur tout état en cours de saisie, qu'il attend
quelque chose de la comptabilité.

Deux contraintes en base :
- `ck_processus_statut_integration` — domaine des trois valeurs ;
- `ck_processus_integration_apres_transmission` — **pas de statut d'intégration sans
  transmission** : la comptabilité ne peut pas avoir un avis sur un état qu'elle n'a
  jamais reçu. La réciproque n'est pas imposée : entre la publication et l'écriture du
  drapeau, il existe un instant où l'état est transmis sans statut.

### Conséquence assumée

Le service Transmission dépend du service Workflow pour lire et écrire ces valeurs.
C'est le même couplage que Workflow → Saisie pour la consolidation (Sprint 3.4).

---

## 2. Qui rassemble les données de la charge

### La décision — le service Transmission va les chercher

Le Workflow n'envoie que l'**identifiant du processus**. Le service Transmission relit
l'en-tête auprès du Workflow (`GET /processus/{id}`), puis le détail auprès de la
Saisie (`GET /saisie/processus/{id}/etat`), et assemble la charge.

### Pourquoi, alors que cela fait un appel de plus

| | Appels | Transits volumineux |
| --- | --- | --- |
| **Transmission va chercher** (retenu) | 3, dont 2 légers | **1** |
| Workflow transmet tout | 2 | **2** |

Trois raisons, dans l'ordre de leur poids :

1. **Ce service peut vérifier lui-même ce qu'il publie.** Il relit le statut à la
   source et **refuse tout ce qui n'est pas `CLOTURE`**. Un endpoint interne qui
   publierait vers la comptabilité sur la seule foi d'un corps fourni par l'appelant
   serait un trou : le montant qui part en paiement viendrait de la requête.
2. **Le Workflow n'a rien à savoir de la forme de la charge comptable** (contrat §7.1),
   qui n'est pas sa responsabilité (CLAUDE.md §3).
3. **Résistance à une panne de la Saisie** — la question explicite du guide. Les lignes
   viennent de la Saisie dans les deux cas, donc la panne bloque la transmission des
   deux côtés. La différence est *qui* la subit. Si le Workflow assemblait, la panne
   surviendrait **dans le chemin de clôture** : il faudrait soit faire échouer une
   clôture déjà décidée — aiguillage appliqué, document signé —, soit faire porter au
   Workflow un mécanisme de reprise qui n'est pas son métier. Ici, la clôture est déjà
   acquise et c'est le service Transmission, dont c'est la responsabilité, qui subit
   l'échec.

Le détail ne traverse par ailleurs le réseau qu'**une** fois au lieu de deux.

---

## 3. Clé de partition et acquittement

### Clé — l'identifiant du processus

Kafka ne garantit l'ordre qu'à l'intérieur d'une partition, et deux messages de même
clé y tombent ensemble. Tout ce qui concerne un même état arrive donc dans l'ordre
d'envoi — ce qui comptera dès qu'un état pourra donner lieu à plusieurs messages :
reprise après incident, ou état complémentaire du Sprint 6bis.

Le **code unité** aurait concentré l'activité d'une grosse agence sur une seule
partition, pour un ordre entre dossiers indépendants qui ne signifie rien. **Aucune
clé** aurait été parfaitement équilibré, mais sans aucune garantie d'ordre — et
l'effet aurait été invisible en développement, où le topic n'a qu'une partition.

### Acquittement — `acks=all`

Le broker ne confirme qu'après écriture sur **toutes les répliques synchronisées**.
Avec `acks=1`, un leader qui tombe avant la recopie perdrait un message que le
producteur a cru reçu : l'état serait marqué transmis, donc figé et plus modifiable,
sans que la comptabilité l'ait jamais vu. Les quelques millisecondes gagnées n'ont
aucun sens dans un geste mensuel. C'est aussi le réglage du producteur d'audit
(Sprint 1.3) — une seule convention dans le module.

`enable.idempotence=true` est posé **explicitement** : sans elle, un réessai interne du
client après un acquittement perdu en chemin écrirait le message deux fois sur le
topic, et le contrôle applicatif d'unicité du Sprint 5.3 ne verrait rien.

### Sérialisation en chaîne, pas en objet

Clé et valeur en `StringSerializer`, la charge étant convertie en JSON à la main. On
n'utilise **pas** le `JsonSerializer` de spring-kafka : il ajoute des en-têtes de type
portant le **nom de classe Java** de la charge, que le module de comptabilisation —
écrit par une autre équipe, peut-être dans une autre technologie — n'a aucune raison de
connaître. Le contrat §7.1 décrit un objet JSON, pas un objet Java sérialisé. Un test
vérifie qu'aucun `cm.afrilandfirstbank` ne fuit dans le message.

---

## 4. Échec de transmission après une clôture réussie

C'est le scénario le plus dangereux du sous-sprint : un état **clôturé, donc figé et
plus corrigeable, mais jamais transmis, donc jamais payé**, et rien ne le signale.

### La décision — la clôture n'est jamais annulée, l'échec est signalé trois fois

**Faire échouer la clôture entière est écarté.** Le document PDF porte déjà le visa,
écrit sur disque hors transaction (Sprint 4.2) : l'annuler laisserait un document signé
pour une validation inexistante. Cela ferait dépendre toute validation de la banque de
la disponibilité de Kafka. Et Kafka et PostgreSQL ne partagent de toute façon aucune
transaction : l'annulation ne serait jamais fiable. **La clôture est la décision métier,
la transmission en est la conséquence.**

Dispositif retenu :

1. **Un réessai** — mais seulement sur les échecs dont on sait qu'aucun message n'est
   parti (§5 ci-dessous) ;
2. **`transmis_comptabilite` reste à faux** : l'état reste retrouvable par requête ;
3. **Trois traces** : journal au préfixe `TRANSMISSION MANQUEE`, événement d'audit
   `TRANSMISSION_MANQUEE` dans une base qu'aucun service métier ne peut réécrire, et le
   résultat rendu au valideur dans la réponse de sa validation.

### Le valideur attend, et voit le résultat

Champ `transmission` ajouté **en fin** de `ValidationResponse`, comme `motifRetour` au
Sprint 4.4 : les sept champs antérieurs gardent nom, type et ordre.

**Motif décisif** : aucune reprise automatique n'est possible — le realm n'a qu'un
client public, `serviceAccountsEnabled: false` (vérifié). La personne qui clôture est
donc, aujourd'hui, la **seule** à pouvoir apprendre qu'un état n'est pas parti. Ne rien
lui dire laisserait un état figé et impayé passer inaperçu jusqu'à réclamation.

### Un seul point de branchement pour les deux clôtures

Le circuit clôture à deux endroits : validation du chef d'unité sous le seuil, et
validation du directeur réseau. Le déclenchement n'est **pas** écrit deux fois : il est
branché sur ce qui les définit tous les deux — le statut atteint vaut `CLOTURE`
(`ValidationService.transmettreSiCloture`).

Deux appels séparés se seraient ressemblés à s'y méprendre, et il aurait suffi d'en
oublier un pour qu'une moitié des états de la banque ne parte jamais en paiement, sans
aucune erreur visible. Une troisième voie de clôture ajoutée plus tard passera
automatiquement par cette ligne.

---

## 5. La règle de réessai — ce qui empêche un double paiement

**Point soulevé par l'utilisateur, et qui a modifié la conception.**

### Pourquoi l'idempotence du producteur ne suffit pas

`enable.idempotence=true` protège des réessais **internes** du client Kafka à
l'intérieur d'un seul `send()` : le producteur numérote ses messages par partition et
par session, et le broker écarte un doublon. Un second `send()` **applicatif** est un
message neuf : rien ne le rattache au premier.

Or un échec de publication est **ambigu par nature** — un accusé peut se perdre après
que le broker a écrit le message. Réessayer produirait deux événements pour le même
état, donc deux jeux d'écritures comptables, donc un double paiement des mêmes
bénéficiaires.

### La règle, portée par le type et non par un commentaire

`ResultatDemandeTransmission` a **trois** cas, et la troisième est une règle de sécurité :

| Issue | Un message a-t-il pu atteindre le broker ? | Réessai |
| --- | --- | --- |
| `Transmise` | oui, confirmé | sans objet |
| `EchecAvantPublication` | **non**, aucun `send()` n'a eu lieu | **autorisé** |
| `EchecApresTentative` | **peut-être** | **interdit** |

Le `switch` qui décide est exhaustif : une quatrième issue ajoutée plus tard ferait
échouer la compilation, au lieu de tomber dans une branche par défaut qui réessaierait —
c'est-à-dire au lieu de choisir le risque de double paiement par inadvertance.

Le classement par défaut est le prudent : un code d'erreur inconnu, un délai HTTP
dépassé, une réponse illisible tombent dans `EchecApresTentative`. **Ne pas savoir n'est
pas savoir que non.**

### Écart assumé à la doctrine « aucun réessai » du Sprint 3.2

Cette doctrine visait la latence d'un appel synchrone dans la boucle de saisie, ligne
par ligne — le pire cas serait passé de 9 à 18 secondes contre une cible de 3. Ici, le
geste est mensuel et l'enjeu n'est pas une seconde d'attente : c'est un salaire versé
ou non. Ce n'est pas la même situation, donc pas une contradiction.

---

## 6. Budget de temps, chiffré

**Corrigé après vérification.** Les valeurs initialement écrites donnaient 35 s par
tentative et 109 s au total : hors de question pour un fil HTTP qui attend. Le
producteur de l'audit accorde 30 s à la livraison (`AuditProprietes`) — il le peut,
personne ne l'attend. Ici, quelqu'un attend.

| Poste | Valeur retenue |
| --- | --- |
| `GET /processus/{id}` et `GET /saisie/.../etat` | connexion 2 s, lecture 3 s |
| Kafka `max.block.ms` / `request.timeout.ms` / `delivery.timeout.ms` | 3 s / 3 s / **6 s** |
| Attente de l'accusé (`EtatValideProducer`) | **7 s** |
| Tentatives | **2 au total**, pause 2 s |

| Panne | 1ʳᵉ tentative | Réessai | **Total** |
| --- | --- | --- | --- |
| Service Transmission injoignable | ~2 s | oui | ~6 s |
| Workflow ou Saisie qui pend | 5 s | oui | **12 s** |
| Kafka muet | 7 s | **non** | 7 s |
| Les trois à leur limite en même temps | 17 s | **non** | **17 s** |
| Charge incomplète | ~0,2 s | non | 0,2 s |

**Cas nominal : ~150 ms. Pire cas d'un fil HTTP retenu : 17 s.**

### Une dérogation assumée à la convention 2 s / 3 s

Le client Workflow → Transmission a un délai de lecture de **20 s**. Ce n'est pas un
réglage d'environnement mais une conséquence arithmétique : l'appelé enchaîne lui-même
trois opérations bornées totalisant 17 s au pire. Un délai de 3 s couperait la réponse
au moment précis où elle importe le plus, et transformerait **chaque** panne en
situation ambiguë, donc non réessayable, alors qu'elle était peut-être parfaitement
claire. D'où sa présence en Java (`ConfigurationTransmission`) et non en YAML.

---

## 7. Le pool dédié, et pourquoi sa file est nulle

**Point soulevé par l'utilisateur.** En fin de mois, plusieurs unités clôturent le même
jour. Si chaque clôture faisait le travail sur son propre fil HTTP, une panne de Kafka
immobiliserait autant de fils de Tomcat qu'il y a de clôtures simultanées, et
ralentirait des requêtes sans rapport.

**Dispositif** : exécuteur dédié `transmission-`, **4 fils**, **file de capacité nulle**.

Une file aggraverait le problème au lieu de le régler : quatre fils occupés 17 s chacun
et cinquante tâches en attente feraient patienter la dernière plus de trois minutes — et
son fil HTTP avec elle. La file nulle fait l'inverse : au-delà de quatre clôtures
simultanées en panne, les suivantes sont **refusées immédiatement**, la validation
répond aussitôt « non transmis », et l'état part en reprise. C'est le principe déjà posé
pour l'audit au Sprint 1.3 : refuser plutôt que faire attendre pour rien.

**Garantie chiffrable : au plus 4 fils HTTP bloqués à la fois, 17 s chacun**, sur les
200 de Tomcat.

### Le handler de rejet journalise **puis lève**

`CallerRunsPolicy` est exclue : elle ferait exécuter la transmission sur le fil de la
requête, c'est-à-dire rouvrirait par la fenêtre la porte que ce pool ferme.

La levée n'est pas un oubli : c'est le seul moyen que l'appelant apprenne le rejet
*immédiatement*. Un handler muet laisserait la tâche disparaître sans que personne ne
l'attende jamais — le fil appelant patienterait alors jusqu'à sa borne défensive, et la
file nulle aurait produit exactement l'attente inutile qu'elle était censée supprimer.
L'exception est rattrapée sur place et traduite en « non transmis ».

C'est la différence avec le pool d'audit, dont le handler ne lève jamais : là, personne
n'attend le résultat, et une levée remonterait à travers le commit d'une opération
métier.

### Un préfixe de journal distinct

`TRANSMISSION REJETEE POOL SATURE`, à ne pas confondre avec `TRANSMISSION MANQUEE`. En
supervision, « Kafka est en panne » et « le pool a été saturé par un pic de fin de mois »
appellent deux réactions différentes, et se confondraient sous un préfixe unique.

---

## 8. Le contrôle de complétude — le dernier filet

Une fois l'événement publié, le module n'a **aucun** moyen de le rattraper. D'où un
contrôle qui **refuse plutôt qu'il n'approche** : aucune valeur de repli, aucune ligne
écartée pour laisser passer les autres, aucun total recalculé pour le faire coïncider.

Quatorze contrôles, en trois familles : la racine, les lignes, la cohérence.

### Trois témoins pour un seul montant

Le total est confronté à **trois** valeurs qui doivent coïncider : le `montant_total`
enregistré sur le processus à la soumission, le `montantTotalFcfa` recalculé à l'instant
par la Saisie, et la somme des lignes réellement mises dans la charge. Deux suffiraient à
détecter un écart ; la troisième dit *de quel côté* il est.

### Construire d'abord, contrôler ensuite

Et non l'inverse : le contrôle porte alors sur exactement ce qui partirait. Une inversion
des deux codes, par exemple, échapperait entièrement à un contrôle des seules entrées.

### Une anomalie par contrôle

Jamais une par ligne fautive. Trois cents lignes sans code agence produisent une anomalie
qui en nomme cinq et compte le reste. Même discipline que le champ `manques` du
Sprint 4.2 — une liste se lit, une avalanche ne se lit pas.

### `500 CHARGE_INCOMPLETE`, et non `422`

Un `422` dirait « corrigez votre dossier ». Or il n'y a rien à corriger : l'état est
clôturé, donc figé. Une charge incomplète à ce stade signale un défaut du module
lui-même. Même parti qu'au Sprint 2.4 pour `INCOHERENCE_GRILLE` et au Sprint 4.3 pour
`SEUIL_INDISPONIBLE` : le service refuse et signale, il n'arbitre jamais.

Les anomalies voyagent **dans le message**, pas dans un sixième champ : contrairement à
`manques` (Sprint 4.2), le consommateur est ici un service et non une interface — personne
n'a à les afficher une à une. Les cinq champs du contrat restent les cinq champs du contrat.

---

## 9. Points ouverts créés par ce sous-sprint

Deux entrées ajoutées à `docs/points-en-attente.md` :

- **Dédoublonnage de `rations.etat.valide` côté comptabilité** — à poser à la DFT, à
  côté de M-03. Le module ne peut pas détecter qu'une publication ambiguë a abouti.
- **Reprise d'une transmission manquée** — impossible d'automatiser sans compte de
  service Keycloak. Décision DSI.
