# Résumé Sprint de rattrapage — Construction du service Audit

**Date :** 4 septembre 2026 (même journée que le Sprint 6.3, sprint suivant,
hors séquence numérotée — voir avertissement de nommage du guide)
**Objet du guide :** construire le septième service fonctionnel du module,
jamais livré depuis le Sprint 0 : consommateur du topic d'audit, persistance
immuable, deux endpoints de lecture
**Ce qui a réellement été fait :** l'intégralité du guide — consommateur
tolerant reader, entité et repository immuables, deux endpoints de lecture,
reprise réelle du topic depuis l'offset 0, documentation complétée

---

## En une phrase

Le service Audit, absent depuis le Sprint 0 malgré six services publiant sur
`rations.audit.evenement` depuis fin août, est désormais opérationnel :
**193 événements réels** (176 constatés au Sprint 6.3, plus ceux publiés
depuis) ont été rejoués depuis l'offset 0 et persistés dans `audit_log`,
vérifié après un redémarrage réel du service — aucune perte, aucun doublon.

---

## Décisions prises en cours de route, avec l'utilisateur

| # | Question | Décision | Qui a tranché |
| --- | --- | --- | --- |
| Q1 (point A-01) | Ajouter un champ `login_acteur` recherchable, ou documenter la limite ? | **Option B** : aucun champ ajouté. Deux défauts de producteur distincts consignés à la place (absence totale de capture côté Saisie ; incohérence de clé `login`/`auteur`), avec le tableau exact des 15 actions concernées | Utilisateur |
| Q2 | Rôles des deux endpoints de lecture | **`ARH`, `DRH`, `ADMIN` uniquement**, Directeur Réseau explicitement écarté tant que sa portée nationale reste provisoire | Utilisateur |
| Q3 | Filtre de `GET /audit/processus/{id}` | **`entite_cible = 'processus_mensuel'` strict**, jamais `id_entite` seul (risque de collision numérique entre entités sans rapport) | Utilisateur, confirmant ma recommandation |
| Q4 | Redémarrage de `rations-kafka` (conteneur passé `unhealthy`) | **Autorisé** — volume Docker persistant, aucune perte de données constatée après coup | Utilisateur |

### Une correction que je me dois à moi-même, faite en cours de route

En posant la question du filtre de `GET /audit/processus/{id}`, j'avais
d'abord avancé une incohérence entre les clés `login` et `loginCible`.
Vérification faite sur les messages réels du topic avant de répondre : c'était
inexact — `loginCible` n'apparaît que sur `ATTRIBUTION_ROLE`, dont
`idUtilisateur` **n'est pas nul** (c'est l'administrateur auteur du geste,
`loginCible` désigne la cible du changement de rôle, pas l'auteur). La vraie
incohérence oppose `login` à `auteur`. Corrigé dans les deux documents avant
qu'ils ne soient écrits, pas après.

---

## Ce qui a été construit

### Étape 2 — Dépendance Kafka et activation

`spring-kafka` ajouté au `pom.xml`. `@EnableKafka` posé explicitement dans
`ConfigurationConsommateurAudit` (le projet déclare `spring-kafka` sans le
starter, donc l'auto-configuration qui poserait cette annotation n'est pas
entraînée — piège déjà rencontré une fois, Sprint 5.2). Groupe de
consommateurs `rations-audit-lecture`, distinct de
`rations-transmission-accuse`.

**Vérifié en réel** (pas seulement absence d'erreur) : log de souscription
`Souscription audit effective : partitions assignees=[rations.audit.evenement-0]`
au démarrage, à quatre reprises distinctes du sprint.

### Étape 3 — Entité `AuditLog`

Huit colonnes, exactement conformes aux migrations V1 et V2 déjà commitées.
`detail_json` mappé en JSONB via `@JdbcTypeCode(SqlTypes.JSON)`, natif
Hibernate ORM 6.2+, sans dépendance tierce. Aucun setter au-delà du
constructeur.

Point A-01 tranché à cette étape, pas reporté (voir décisions ci-dessus).

### Étape 4 — Repository sans suppression

`RepositoryEcritureSeule<T, ID>` étend directement `Repository` (marqueur
racine sans méthode), ne déclare que `save` et `findById`.
`AuditLogRepository` n'étend que celui-ci.

**Découverte réelle en testant le garde-fou** : une première version ajoutait
aussi `JpaSpecificationExecutor`, pour préparer la recherche filtrée de
l'étape 6. Le test de garde a immédiatement révélé que les versions récentes
de Spring Data JPA y ont ajouté `delete(PredicateSpecification)` et
`delete(DeleteSpecification)` — une suppression de masse. Retiré ; la
recherche de l'étape 6 est portée par un fragment de lecture écrit à la main
(Criteria API).

### Étape 5 — Consommateur, tolerant reader

`AuditEvenementConsumer` + `MessageAuditEntrant` (type propre au service,
indépendant d'`EvenementAudit`). Deux niveaux de rejet tracé (préfixe
`AUDIT ENTREE REJETEE`, offset toujours avancé) : message JSON illisible, ou
champ obligatoire manquant (`action`, `entiteCible`, `dateAction`,
`serviceEmetteur`).

**Vérifié en réel** : les 193 événements du topic persistés sans aucun rejet.
**Graphify confirme** : 0 arête entre `service-audit` et `rations-audit-commun`
sur les 15 078 relations du graphe.

**Incident d'infrastructure traité pendant cette étape** : le conteneur
`rations-kafka` est passé `unhealthy` (52 sondes de santé consécutives en
échec), probablement saturé par les groupes de consommateurs transitoires
créés par mes propres commandes `kafka-console-consumer.sh` de vérification.
Redémarré avec l'autorisation de l'utilisateur ; les 193 événements étaient
intacts (volume Docker persistant `rations-kafka-data`).

### Étape 6 — Endpoints de lecture

`GET /audit/entrees` (recherche filtrable — service émetteur, action, entité
cible, id entité, utilisateur, plage de dates — paginée) et
`GET /audit/processus/{id}` (journal complet, niveau workflow). Rôles `ARH`,
`DRH`, `ADMIN`. Tri **jamais** contrôlable par le client : construit côté
serveur sur `date_action`, aucun paramètre `sort` accepté — délibérément
différent du pattern `Pageable`/`@SortDefault` utilisé ailleurs dans le
projet.

**Vérifié en réel contre le vrai realm Keycloak** (jetons obtenus pour
`martin_fouda`/ADMIN, `jean_mbarga`/AGENT_UNITE,
`sylvie_atangana`/DIRECTEUR_RESEAU_DR) :
- `GET /audit/entrees` renvoie les entrées dans l'ordre `192, 191, 193,
  190...` — preuve concrète que le tri est sur `date_action`, pas sur `id`.
- `GET /audit/processus/109` renvoie le parcours workflow exact, chronologique.
- `403` confirmé pour `AGENT_UNITE` et `DIRECTEUR_RESEAU_DR`.

### Étape 7 — Immuabilité et reprise

Trois preuves, toutes établies en réel :

1. **`AuditLogImmuabiliteTest`** (contre la vraie base) : insertion réelle,
   puis tentative de suppression **directe par `EntityManager.remove()`**,
   contournant délibérément le repository — échoue. Un garde `@PreRemove`
   a été ajouté sur l'entité elle-même : l'immuabilité n'est plus seulement
   une propriété du repository, mais de l'entité.
2. **`CablageConsommateurAuditTest`** (`ApplicationContextRunner`, sur le
   modèle du précédent Sprint 5.2 — `@EmbeddedKafka` inutilisable pour la
   même raison de versions incompatibles) : `@EnableKafka` actif,
   désérialisation en chaîne, groupe fixe, accusé après traitement, lecture
   depuis l'offset le plus ancien.
3. **Vérification manuelle décisive** : démarrage à froid (base vide, offset
   0) → **193 lignes** persistées (≥ 176 exigé), aucun rejet. Redémarrage du
   service → toujours 193 lignes, **zéro doublon exact**.

`docs/robustesse-audit.md` mis à jour : les scénarios 2 et 3, déclarés
« inéprouvables » au Sprint 6.3, passent à **Vérifié**.

### Étape 8 — Documentation et clôture

- Contrat d'API : nouvelle section 8 (service Audit) rédigée, section
  récapitulative renumérotée en section 9, total porté à 26 endpoints.
- CLAUDE.md : avertissement obsolète (« le consommateur n'existe pas »)
  remplacé par la confirmation opérationnelle ; six nouvelles lignes de
  décisions en section 17 ; une nouvelle interdiction en section 15
  (`JpaSpecificationExecutor` sur un repository immuable).
- `docs/points-en-attente.md` : point A-01 marqué tranché, deux points
  ouverts distincts ajoutés en cours de sprint (accès DR au journal ; portée
  de `GET /audit/processus/{id}`).

---

## État final laissé en place, délibérément

Les 193 événements réels sont **restés en base** à la fin du sprint — c'est
le livrable même de ce sprint, pas un artefact de test à nettoyer. Le groupe
de consommateurs `rations-audit-lecture` est à l'offset 193, lag 0.

---

## Critères de validation du guide

| Critère | Statut |
| --- | --- |
| Service Audit démarre et journalise sa souscription au topic | ✅ Vérifié, à quatre reprises |
| Consommateur en tolerant reader, sans dépendance à `rations-audit-commun` | ✅ Vérifié par Graphify (0 arête sur 15 078) |
| Aucune méthode de suppression accessible sur le repository | ✅ Vérifié par test (structurel + `@PreRemove` + base réelle) |
| `audit_log` contient au moins 176 lignes après reprise | ✅ Vérifié — 193 lignes |
| Les deux endpoints répondent, triés sur `date_action` | ✅ Vérifié en réel (jetons Keycloak réels, ordre non numérique confirmé) |
| Point A-01 tranché et consigné | ✅ Fait |
| Contrat d'API section 8 rédigée | ✅ Fait |
| CLAUDE.md et `docs/points-en-attente.md` à jour | ✅ Fait |
| Migrations V1 et V2 intactes | ✅ Vérifié — `git diff` vide sur les deux fichiers |

**Tous les critères sont cochés**, chacun sur la foi d'une vérification réelle
et non d'une absence de symptôme — conformément à la règle posée au
Sprint 6.3 (`docs/decisions/2026-09-04-verification-reelle-de-l-audit.md`).

---

## Fichiers du sprint

| Chemin | Nature |
| --- | --- |
| `backend/service-audit/pom.xml` | `spring-kafka`, `spring-boot-starter-data-jpa-test` |
| `backend/service-audit/.../domaine/AuditLog.java` | **Nouveau** — entité immuable |
| `backend/service-audit/.../infrastructure/RepositoryEcritureSeule.java` | **Nouveau** |
| `backend/service-audit/.../infrastructure/AuditLogRepository.java` | **Nouveau** |
| `backend/service-audit/.../infrastructure/AuditLogRechercheRepository.java` (+ `Impl`) | **Nouveau** — fragment Criteria API |
| `backend/service-audit/.../infrastructure/FiltreAuditEntrees.java` | **Nouveau** |
| `backend/service-audit/.../infrastructure/MessageAuditEntrant.java` | **Nouveau** — tolerant reader |
| `backend/service-audit/.../infrastructure/AuditEvenementConsumer.java` | **Nouveau** |
| `backend/service-audit/.../infrastructure/config/ConfigurationConsommateurAudit.java` | **Nouveau** |
| `backend/service-audit/.../application/RechercheAuditService.java` | **Nouveau** |
| `backend/service-audit/.../api/AuditController.java` | **Nouveau** |
| `backend/service-audit/.../api/dto/AuditEntreeResponse.java`, `PageResponse.java` | **Nouveau** |
| `backend/service-audit/src/test/...` (3 classes) | **Nouveau** — immuabilité, câblage, garde de suppression |
| `docs/decisions/2026-09-04-arbitrage-point-a-01-login-acteur.md` | **Nouveau** |
| `docs/points-en-attente.md` | Point A-01 tranché, 2 points ouverts ajoutés |
| `docs/robustesse-audit.md` | Scénarios 2 et 3 : vérifiés |
| `docs/initialisation projet/API_contract_...md` | Section 8 (Audit) rédigée, récapitulatif renuméroté (hors suivi Git) |
| `CLAUDE.md` | §9.2, §15, §17 |
