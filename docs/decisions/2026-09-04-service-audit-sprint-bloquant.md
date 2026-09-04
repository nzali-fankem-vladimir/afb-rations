# La construction du service Audit devient le prochain sprint, bloquant

**Sprint 6.3 — 4 septembre 2026** · Décision tranchée avec l'utilisateur,
étape 2, après inspection factuelle.

**Statut :** arrêtée. Le Sprint 6.3 reste dans son objet (couverture) ; la
construction du service Audit devient le sprint suivant, **avant 6bis et
avant 7F**.

---

## 1. Ce qui a été constaté

`backend/service-audit/` est un squelette du Sprint 0. Son `src` contient quatre
fichiers `.java` — la classe d'application, `RoleEnum`, `RoleJwtConverter`,
`SecurityConfig` — quatre dossiers vides marqués par `.gitkeep`, deux fichiers
YAML et une migration. Il n'a pas de `src/test`.

```
$ grep -rn "@KafkaListener\|@Entity\|JpaRepository\|@RestController" service-audit/src
[code retour 1 — aucune occurrence]

$ grep -n "kafka" service-audit/pom.xml
[code retour 1 — aucune occurrence]
```

Conséquence : `audit_log` contient 0 ligne, et le seul groupe de consommateurs
enregistré sur le broker est `rations-transmission-accuse` (Sprint 5.2). **La
moitié amont de la chaîne fonctionne, la moitié aval n'existe pas.**

## 2. Pourquoi ce n'était prévu nulle part

Ce n'est pas un oubli de codage mais un oubli de planning, et il est traçable.
La note d'intégration du service Audit
(`docs/initialisation projet/Note_de_mise_a_jour_documentaire_Service_Audit (1).md`,
§7) demandait :

> « Insérer une ligne de sprint pour le service Audit entre le Sprint 1 et le
> Sprint 2. Durée estimée : deux jours. »

Le planning exécuté ne l'a jamais reçue. `git log` montre `sprint-1.3`
(3099637) et `sprint-2.1` (755e8b2) **adjacents, le même jour**, sans rien entre
eux ; `backend/service-audit/` n'a plus été touché depuis `sprint-0.6`.

CLAUDE.md §14 place pourtant le service Audit en deuxième position de l'ordre
d'implémentation, avec son motif : *« les services suivants publient des
événements d'audit dès leurs premières écritures, il faut donc que le
consommateur existe. »* La consigne était juste ; elle n'a pas été appliquée.

## 3. Pourquoi ne pas le construire dans le Sprint 6.3

Le guide 6.3 porte sur la **vérification de couverture**, et l'annonce
lui-même : *« Ce sous-sprint devient donc un sprint de vérification de
couverture et de finalisation de la consultation, pas de construction. »* Il est
dimensionné pour une journée.

Y glisser la construction d'un septième service — consommateur, entité,
repository, deux endpoints, tests d'immuabilité — mélangerait deux objets
distincts dans le même historique Git. C'est exactement l'erreur refusée au
Sprint 5.1, où la montée de version Kafka avait été écartée de la logique métier
de RG-13 pour la même raison.

**Argument décisif retenu par l'utilisateur :** le sprint de construction devra
concevoir l'entité JPA, les index de recherche et la structure de consultation
d'un seul tenant. Décider aujourd'hui de la forme de l'écriture — un champ
`login_acteur` par exemple — avant d'avoir conçu la lecture ne fait pas
l'économie d'une troisième migration, il la garantit. Le schéma se décide une
fois, avec les deux côtés sous les yeux.

## 4. Pourquoi bloquant, et pas « quand on aura le temps »

Le cahier des charges exige une historisation complète et des journaux générés
automatiquement, chaque opération étant associée à un utilisateur, une date et
une heure. Dans un module bancaire, laisser ce trou ouvert sans échéance fixée
n'est pas un arbitrage de priorité, c'est un défaut de conformité sans date de
résolution.

S'y ajoute un motif de récupérabilité, vérifié :

```
offset le plus ancien : rations.audit.evenement:0:0
offset de fin         : rations.audit.evenement:0:176
retention.ms          : 604800000  (7 jours)
```

**Rien n'a encore été perdu.** L'offset le plus ancien est toujours 0 : les 176
événements sont intégralement présents, y compris ceux du 27 août, pourtant plus
vieux que la rétention. Kafka purge par segment entier et ne supprime jamais le
segment actif ; avec ce volume, tout tient encore dedans.

C'est une fenêtre, pas une garantie. Au premier basculement de segment, les plus
anciens deviennent éligibles à la suppression et sont perdus définitivement.
Construire le consommateur maintenant permet de **rejouer le topic depuis
l'offset 0** et de reconstituer l'intégralité du journal depuis le 27 août ; le
faire plus tard ne le permettra plus.

## 5. Périmètre du sprint à ouvrir

À traiter d'un seul tenant, entité et migration ensemble :

1. `spring-kafka` dans `service-audit/pom.xml`, et **`@EnableKafka`** — le
   projet déclare `spring-kafka` sans le starter, donc l'auto-configuration qui
   poserait cette annotation n'est pas entraînée (CLAUDE.md §15, leçon du
   Sprint 5.2 : le service démarre et n'écoute rien, sans une ligne de journal).
2. Consommateur du topic `rations.audit.evenement` en **tolerant reader**, avec
   son propre type : `service-audit` ne dépend pas de `rations-audit-commun`, et
   ne doit pas commencer à en dépendre (CLAUDE.md §15).
3. Entité `AuditLog`, repository **sans méthode de suppression exposée**, y
   compris celles héritées de `JpaRepository`.
4. Schéma complet arrêté en une fois — dont l'arbitrage reporté sur un champ
   d'acteur recherchable (`docs/points-en-attente.md`, point A-01).
5. Les deux endpoints de lecture `GET /audit/entrees` et
   `GET /audit/processus/{id}` (CLAUDE.md §11).
6. Tests d'immuabilité, et **reprise du topic depuis l'offset 0**.
7. Contrat d'API : la section 8 du service Audit n'a jamais été écrite (la note
   documentaire la classait priorité 5, « nécessaire avant le développement du
   service Audit »). Elle est à produire avec ce sprint.

### Trier sur `date_action`, jamais sur `id` ni sur l'offset

Constaté à la vérification manuelle du Sprint 6.3, sur des messages réels : deux
événements d'une même transaction sont arrivés sur le topic **dans l'ordre
inverse de leur production**.

```
offset 178  CREATION_LIGNE_PRESTATION    dateAction 16:56:28.414519
offset 179  CREATION_BENEFICIAIRE        dateAction 16:56:27.698669
```

Le bénéficiaire est bien créé **avant** la ligne qui le référence. L'inversion
vient du pool `@Async("executeurAudit")` : les événements sont émis après le
commit, sur plusieurs threads, et rien ne garantit leur ordre d'arrivée sur le
broker. La clé de partition (`entiteCible:idEntite`) ordonne les événements
d'une **même** entité, pas ceux d'entités différentes.

**L'ordre vrai reste intégralement récupérable** : `EvenementAudit` horodate au
moment de l'action et non à la publication — c'est écrit dans sa javadoc, et
c'est exactement la situation que cette précaution couvrait.

Conséquences pour ce sprint :

| Geste | Pourquoi |
| --- | --- |
| `GET /audit/processus/{id}` **ordonne sur `date_action`** | L'ordre d'insertion reflète l'ordre d'arrivée sur le topic, pas l'ordre des faits. Un journal de contrôle interne qui présente les faits dans le désordre est pire qu'inutile. |
| Idem pour `GET /audit/entrees` | Même raison. |
| L'index `idx_audit_log_date_action` existe déjà (V1) | Le tri est servi, rien à ajouter. |
| Ne jamais présumer que `id` croissant = chronologique | C'est vrai pour l'insertion, faux pour les faits. |

## 6. Ce que le Sprint 6.3 ne peut donc pas cocher

Consigné sans contournement dans le résumé du sprint : « consultation par
processus fonctionnelle », « journal immuable prouvé par test » et « aucune
trace perdue après panne du service Audit » ne sont **pas vérifiables** tant que
ce sprint n'est pas livré. Voir
[`2026-09-04-verification-reelle-de-l-audit.md`](2026-09-04-verification-reelle-de-l-audit.md).
