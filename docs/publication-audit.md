# Publication des événements d'audit

Module Paiement des Rations et du Transport de la Garde Armée — décision Sprint 1.3

Convention applicable aux **six services métier**. Le journal lui-même vit dans
le service Audit, base `rations_audit`, alimenté par le topic
`rations.audit.evenement` (CLAUDE.md section 9.2). Cette note décrit le côté
producteur.

**Un appel REST vers le service Audit est interdit**, y compris en repli après un
échec de publication (CLAUDE.md section 15).

---

## 1. Comment un service publie

### 1.1 Dépendance

```xml
<dependency>
    <groupId>cm.afrilandfirstbank.rations</groupId>
    <artifactId>rations-audit-commun</artifactId>
    <version>${project.version}</version>
</dependency>
```

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
```

Rien d'autre à câbler : l'autoconfiguration du module rend `PublicateurAudit`
injectable.

### 1.2 Appel

```java
publicateurAudit.publier(EvenementAudit.de(
        appelant.getId(),          // null si l'auteur n'a pas de profil local
        "VALIDATION_DA",           // verbe métier, en majuscules
        "processus_mensuel",       // table ou agrégat visé
        processus.getId(),         // null si l'action ne vise pas une entité
        adresseIp,
        DeltaAudit.nouveau()
                .champ("statut", statutAvant, statutApres)
                .contexte("codeUnite", processus.getCodeUnite())
                .enJson()));
```

`serviceEmetteur` n'est **pas** renseigné par l'appelant : le producteur
l'estampille depuis `spring.application.name`. Le faire remplir à la main par six
services garantirait qu'un l'oublie, et une trace centralisée sans origine ne
vaut plus grand-chose.

### 1.3 Ordre des opérations

Document maître section 7.3 : la journalisation vient **après** la modification
d'état, jamais avant. Un événement publié avant une modification qui échoue
enregistrerait une opération qui n'a pas eu lieu.

Le module renforce cette règle : quand une transaction est active, l'envoi réel
sur le topic n'a lieu qu'**après son commit**
(`@TransactionalEventListener(AFTER_COMMIT)`). Un rollback ne laisse donc pas
derrière lui la trace d'une opération annulée.

`fallbackExecution = true` : un événement émis **hors transaction** part
immédiatement. C'est le cas d'un refus d'accès, qui survient avant toute
ouverture de transaction.

---

## 2. Ce que le service appelant n'a pas à faire

| À ne pas faire | Pourquoi |
|---|---|
| Entourer `publier()` d'un `try/catch` | Le contrat garantit qu'il ne lève jamais. Un `catch` ici ne protégerait de rien et laisserait croire l'inverse. |
| Attendre une confirmation | La publication est asynchrone par construction. Aucun `get()`, aucun `join()`. |
| Vérifier que le service Audit a consommé | Le producteur ne le sait pas et n'a pas à le savoir. |
| Réécrire un producteur local | Les cinq couches de protection ci-dessous vivent à un seul endroit. Six copies, c'est six occasions qu'une disparaisse. |
| Surcharger `spring.kafka.producer.*` en espérant régler l'audit | Le producteur d'audit a sa propre `ProducerFactory`, volontairement isolée. |

---

## 3. Ce qui se passe quand le broker est indisponible

Cinq couches, aucune facultative. Retirer l'une d'elles rouvre un chemin par
lequel une panne d'audit remonte jusqu'à l'utilisateur.

| # | Couche | Ce qu'elle empêche |
|---|---|---|
| 1 | `max.block.ms` = 2 s | Kafka bloque le thread appelant en attente des métadonnées, **jusqu'à 60 s par défaut**. Broker éteint, une attribution de rôle prendrait une minute : l'opération « n'échouerait » pas, mais serait inutilisable. |
| 2 | `try/catch` synchrone autour de l'envoi | Sérialisation impossible, `max.block.ms` dépassé, producteur fermé : ces exceptions sont levées sur le thread appelant. |
| 3 | Callback d'échec journalisant | L'échec de livraison survient sur le thread réseau de Kafka. Le futur n'est jamais attendu. |
| 4 | Listener `@Async` + `catch (Throwable)` | Sans `@Async`, un listener `AFTER_COMMIT` s'exécute sur le thread appelant, dans `afterCommit()` : une exception y remonte à travers le commit et devient une **erreur HTTP alors que la modification est déjà en base**. Décision réussie, rapportée comme échec. |
| 5 | Handler de rejet non levant | `@Async` rouvre la même porte : file pleine → `TaskRejectedException`, encore sur le thread appelant, encore dans `afterCommit()`. Le handler journalise et abandonne l'événement au lieu de lever. |

**Résultat observable, broker arrêté :** l'opération métier aboutit normalement
et dans son temps habituel. Une ligne `WARN` apparaît dans les journaux du
service :

```
AUDIT PERDU a la livraison : action=ATTRIBUTION_ROLE entite=utilisateurs idEntite=14
topic=rations.audit.evenement. L'operation metier a abouti.
```

Les messages de perte commencent tous par `AUDIT PERDU`, pour être repérables
d'une seule recherche en supervision.

---

## 4. Ce qui est tracé, et ce qui ne l'est pas

**Tracé :**

- toute création, modification ou suppression d'un objet métier, avec le delta
  avant/après complet — y compris les champs inchangés, pour qu'« inchangé » ne
  se confonde pas avec « pas considéré » ;
- toute validation, tout retour, toute clôture ;
- **tout refus d'accès** (CT-04, US-02) : rôle insuffisant, habilitation absente,
  action hors périmètre. Une tentative hors périmètre est une information de
  sécurité, pas un rejet à ignorer.

**Non tracé :**

- les lectures simples, qui produiraient un volume sans valeur de contrôle ;
- les erreurs d'usage (400, 404, 409) : ce sont des maladresses, pas des
  tentatives ;
- les 401 sans jeton, refusés par le filtre de sécurité en amont des
  contrôleurs. Une requête anonyme est une absence d'authentification, pas une
  action hors périmètre.

### Cas particulier — les refus sur `/identite/habilitation`

Le service Identité **ne trace pas** le verdict `autorise: false` : il répond à
une question, il ne refuse pas une action. **C'est le service consommateur qui
refuse, et qui publie `ACCES_REFUSE`** — sur verdict négatif comme sur
indisponibilité du service Identité (refus conservateur,
`docs/appel-habilitation.md`).

Si le consommateur ne publie pas, **le refus n'est tracé nulle part** et
l'exigence CT-04 n'est pas tenue, en silence. Règle inscrite dans CLAUDE.md
sections 9.2 et 15, et rappelée dans le guide de chaque sprint concerné.

---

## 5. Risque résiduel assumé — perte d'événements

Le mécanisme ne garantit pas la livraison. Trois pertes possibles :

1. **Broker durablement indisponible** au-delà de `delivery.timeout.ms` (30 s).
2. **File d'attente saturée** : l'événement est abandonné plutôt que mis en
   attente bloquante.
3. **Arrêt de la JVM entre le commit et l'envoi** : la modification est en base,
   l'événement ne partira jamais.

Les trois sont journalisées, sauf la troisième — un processus arrêté n'écrit
plus.

**Pourquoi c'est le risque le plus gênant des deux possibles.** L'alternative
écartée (publier dans la transaction) produirait des traces **fausses** en cas de
rollback. On a retenu le risque de trace **manquante**. Ce choix mérite d'être
nommé pour ce qu'il est : une trace fausse se détecte par recoupement avec l'état
réel — `utilisateurs.role` contredit `audit_log` —, tandis qu'**une trace
manquante ne se détecte pas du tout**. Rien ne signale une absence.

Le risque conservé est donc le plus difficile à repérer. C'est un compromis de
coût pour ce sprint, pas un état correct.

**Réponse de fond : l'outbox transactionnel.** Écrire l'événement dans une table
locale, dans la même transaction que la modification, puis le relayer vers Kafka
par un processus séparé. Ni trace fausse, ni trace manquante. Coût : une table et
un relais supervisé par service. À reprendre si le contrôle interne exige une
garantie d'exhaustivité — consigné dans `docs/points-en-attente.md`.

---

## 6. Périmètre du module `rations-audit-commun`

Le module **ne contient que la publication d'audit** : la charge de l'événement,
le port, le producteur, sa configuration. Aucun type métier, aucun DTO, aucun
utilitaire.

Cette contrainte est **vérifiée au build**, pas confiée à cette note :

- `maven-enforcer-plugin` interdit les dépendances web, persistance, sécurité et
  toute dépendance vers un `service-*` ;
- `PerimetreDuModuleTest` fait échouer le build si une classe apparaît hors du
  paquet `commun.audit`, si un type non attendu est ajouté, ou si un import
  interdit apparaît.

**Portée réelle de cette garantie.** Elle rend la dérive impossible *par
accident*, pas impossible tout court : qui veut ajouter un type métier peut
éditer le test. C'est strictement plus fort qu'une convention écrite, strictement
plus faible que l'isolation du service Audit — où l'absence d'identifiants de
connexion rend l'écriture physiquement impossible. Elle transforme une dérive
silencieuse en acte délibéré, visible en revue.

**`service-audit` ne dépend pas de ce module.** Le consommateur lit le topic en
*tolerant reader*, avec son propre type. Sinon un changement de schéma imposerait
un pas cadencé à sept services, et le service Audit ne pourrait plus relire les
messages déjà présents dans le topic.

---

## 7. Réglages

Préfixe `rations.audit`. Les valeurs par défaut ne sont pas neutres : ce sont
elles qui font tenir la garantie. Les modifier sans comprendre pourquoi elles
sont là revient à la désactiver.

| Propriété | Défaut | Rôle |
|---|---|---|
| `topic` | `rations.audit.evenement` | Destination. Point DSI **D-07** : nommage en environnement partagé non arrêté. Surchargeable par `RATIONS_AUDIT_TOPIC`. |
| `blocage-maximal` | `2s` | `max.block.ms`. **Le réglage le plus important du module.** |
| `delai-de-livraison` | `30s` | `delivery.timeout.ms` |
| `delai-de-requete` | `10s` | `request.timeout.ms` |
| `threads` | `2` | Pool dédié, non partagé avec le trafic HTTP |
| `capacite-file` | `500` | Au-delà, les événements sont abandonnés avec une trace |
| `actif` | `true` | `rations.audit.actif=false` désactive entièrement la publication |
