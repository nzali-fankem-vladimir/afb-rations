# Résumé Sprint 6.3 — Couverture du journal d'audit

**Date :** 4 septembre 2026
**Objet du guide :** vérifier la couverture de publication d'audit sur les sept
services, et exposer la consultation du journal
**Ce qui a réellement été fait :** la vérification de couverture, la correction
des omissions, la correction d'un défaut de schéma bloquant — **et le constat que
le service Audit n'a jamais été construit**, ce qui rend la seconde moitié du
guide sans objet

---

## En une phrase

Le sprint a découvert que **la chaîne d'audit n'a jamais eu de seconde moitié** :
176 événements métier réels sont sur le topic Kafka, `audit_log` contient 0 ligne,
et `service-audit` est resté le squelette du Sprint 0. La couverture de
publication a été vérifiée et complétée, le schéma qui aurait rejeté 21 traces
sur 30 a été corrigé, et la construction du service Audit devient le prochain
sprint, bloquant.

---

## Le fait dominant, établi par inspection factuelle

L'utilisateur a demandé une vérification indépendante avant tout arbitrage. Six
points, six commandes.

| Question | Réponse | Preuve |
| --- | --- | --- |
| `service-audit` a-t-il un consommateur, une entité, un repository, un contrôleur ? | **Aucun des quatre** | `grep "@KafkaListener\|@Entity\|JpaRepository\|@RestController"` → code retour 1 |
| `spring-kafka` dans son `pom.xml` ? | **Absent** | `grep -n kafka` → code retour 1 |
| `SELECT COUNT(*) FROM audit_log` | **0** | table créée le 26/08, jamais alimentée |
| Un sprint Audit entre 1.3 et 2.1 ? | **Aucun** | les deux commits sont adjacents, même jour |
| `id_utilisateur` / `id_entite` en `NOT NULL` ? | **Les deux** | fichier V1 + `information_schema` |
| Des nuls possibles dans le code réel ? | **21/30** et **7/30** | `null` littéraux, pas des cas de bord |

Complément décisif, non demandé mais déterminant :

```
rations.audit.evenement : offset le plus ancien 0, offset de fin 176
groupes de consommateurs du broker : rations-transmission-accuse (seul)
```

**176 messages réels, aucun consommateur.** Extrait authentique :

```json
{"idUtilisateur":6,"serviceEmetteur":"service-identite","action":"ATTRIBUTION_ROLE",
 "entiteCible":"utilisateurs","idEntite":2,"dateAction":"2026-08-27T12:24:35.6309673", …}
```

### Origine : un oubli de planning, traçable

La note d'intégration du service Audit (§7) demandait d'insérer un sprint dédié
« entre le Sprint 1 et le Sprint 2, durée deux jours ». Le planning exécuté ne
l'a jamais reçue. CLAUDE.md §14 place pourtant le service Audit en deuxième
position, avec le bon motif : « les services suivants publient des événements
dès leurs premières écritures, il faut donc que le consommateur existe ».

### Une correction que je dois à l'utilisateur

J'avais avancé qu'une partie des 176 événements était probablement déjà expirée.
**C'était faux.** L'offset le plus ancien est toujours 0 : Kafka purge par
segment entier et ne supprime jamais le segment actif. Rien n'est perdu, tout est
rejouable — mais la fenêtre se referme au premier basculement de segment.

---

## Le trou de méthode, et la règle qui en sort

**La vraie question n'est pas « pourquoi le service manque » mais « comment sept
sprints ont-ils validé leurs critères d'audit ».**

Ce qui était réellement observé pour les cocher : aucune exception levée, aucun
`WARN AUDIT PERDU`, un test à double vérifiant l'appel à `publier(...)`. Les
trois sont vrais — **et restent vrais quand le journal n'enregistre rien**.

Le producteur est conçu pour ne jamais faire échouer le métier ; cinq couches y
veillent. C'est une qualité, et c'est précisément ce qui rend « aucune erreur »
inutilisable comme preuve. **Un dispositif dont la panne est silencieuse par
construction ne se valide pas par le silence.**

→ `docs/decisions/2026-09-04-verification-reelle-de-l-audit.md` : tout critère de
validation mentionnant l'audit exige désormais une requête sur `rations_audit`.

---

## Décisions prises en cours de sprint

| # | Question | Décision | Qui a tranché |
| --- | --- | --- | --- |
| Q1 | Construire le service Audit dans ce sprint ? | **Non.** 6.3 reste dans son objet ; la construction devient le **prochain sprint, bloquant, avant 6bis et 7F** | Utilisateur, contre ma recommandation |
| Q2 | `NOT NULL` sur `id_utilisateur` / `id_entite` | **Migration V2 nullable**, aucune valeur sentinelle | Accord |
| Q3 | Les trois omissions | **Corrigées**, plus Reporting en revue exhaustive | Accord |
| Q3bis | Champ d'acteur recherchable (`login_acteur`) | **Reporté** au sprint de construction | Utilisateur, contre ma recommandation |

### Q1 — pourquoi l'utilisateur a eu raison

Mon argument était « c'est ce que le guide décrit à partir de l'étape 5 ».
L'argument retenu est plus fort : construire le service Audit ici mélangerait
deux objets dans le même historique Git — l'erreur refusée au Sprint 5.1 quand la
montée de version Kafka avait été écartée de la logique de RG-13. Un sprint
dimensionné pour une journée de vérification ne devient pas un sprint de
construction d'un septième service.

Mais **bloquant**, pas « quand on aura le temps » : dans un module bancaire, un
défaut de conformité sans date de résolution n'est pas un arbitrage de priorité.

### Q3bis — pourquoi l'utilisateur a encore eu raison

Je recommandais d'ajouter `login_acteur` maintenant, en profitant de la migration
V2 déjà ouverte. Deux objections décisives :

1. **Ce n'est pas « un champ et une colonne », c'est 21 points dans 5 services.**
   Le champ concerne toutes les traces sans `idUtilisateur`, pas seulement les 6
   de Saisie.
2. **Ce serait décider de la forme de l'écriture avant d'avoir conçu la lecture.**
   Le sprint de construction devra arrêter l'entité, les index et la structure de
   consultation ; s'il découvre alors qu'il faut aussi le **rôle** de l'acteur, il
   faudra une V3 de toute façon. « Ça évite une migration » ne tient que si le
   design est parfait aujourd'hui.

La limite est consignée sans être masquée : `docs/points-en-attente.md`,
point **A-01**, avec la requête JSONB de contournement.

---

## Ce qui a été livré

### Migration V2 sur `rations_audit`

`id_utilisateur` et `id_entite` deviennent nullables. Le défaut n'était pas
théorique — opposé au schéma, un message `ACCES_REFUSE` réellement présent sur le
topic était rejeté :

```
ERROR: null value in column "id_utilisateur" of relation "audit_log"
       violates not-null constraint
```

Après V2, appliquée par Flyway : le même message passe (`INSERT 0 1`). Sans effet
de bord — les trois index sont des B-tree simples, non uniques, non partiels, et
PostgreSQL y indexe nativement les nuls (vérifié sur `pg_indexes` et
`pg_constraint` avant la migration, à la demande de l'utilisateur).

### Trois omissions corrigées, un commit par service

| Service | Correction | Pourquoi c'est un fait à tracer |
| --- | --- | --- |
| **Saisie** | `CREATION_BENEFICIAIRE` | Seul point par lequel un bénéficiaire entre en base, **sans aucun enrôlement en amont**. La ligne porte le nom et le compte qui recevront l'argent. |
| **Saisie** | `adresseIp` sur modification et suppression de ligne | Elle était nulle alors que la création la renseignait — trois écritures sur la même entité, deux façons de les tracer, le contrôleur détenant déjà l'information. Un oubli, pas un choix. |
| **Identité** | `LIAISON_COMPTE_KEYCLOAK` | Le seul moment où une identité annuaire prend le contrôle d'une habilitation, et il survient **sans qu'aucun administrateur n'agisse**. Après coup, `sub_keycloak` porte la valeur finale, pas son histoire. |
| **Reporting** | `GENERATION_RAPPORT`, `EXPORT_RAPPORT` | Le service ne publiait **rien**, malgré `rations-audit-commun` déjà déclaré à son `pom.xml`. |

### Revue exhaustive du Reporting (demandée explicitement)

Les quatre endpoints ont été examinés, pas seulement les deux du tableau du guide.

| Endpoint | Tracé ? | Motif |
| --- | --- | --- |
| `GET /reporting/rapports` | **oui** | Agrège une portée entière |
| `GET /reporting/rapports/export` | **oui** | Seul geste du module produisant un fichier qui **quitte définitivement le périmètre applicatif**, où plus aucune habilitation ne le protège |
| `GET /reporting/demandes` | non | Lecture de travail quotidienne |
| `GET /reporting/processus/{id}/historique` | non | Idem |

La ligne retenue : **on trace ce qui modifie un état, ce qui agrège une portée
entière, ou ce qui fait sortir un fichier — pas ce qui affiche un dossier.** Même
raisonnement que pour `date_dernier_acces`, écarté d'un commun accord.

`ACCES_REFUSE` reste non publié par le Reporting, décision antérieure conservée :
il ne consomme jamais `/identite/habilitation` lui-même et relaie des refus déjà
tracés à leur source.

### Points de vigilance du guide, vérifiés

- **`service_emetteur` renseigné par les sept services** : garanti par
  construction, pas par discipline — le producteur l'estampille depuis
  `spring.application.name`, aucun service ne peut publier une trace anonyme.
- **Ordre modification puis publication** (document maître §7.3) : garanti par
  `@TransactionalEventListener(AFTER_COMMIT)`.
- **Base d'audit isolée** :
  `grep -rn "rations_audit" backend/ --include=*.yml | grep -v service-audit`
  → aucun résultat. Le script d'init PostgreSQL crée un rôle propriétaire par
  base : un service ne peut pas se connecter à celle d'un autre.
- **Aucun appel REST vers le service Audit** introduit par les corrections.

---

## Robustesse — un scénario sur trois éprouvable

`docs/robustesse-audit.md` consigne le détail. Résumé honnête :

| Scénario | Statut |
| --- | --- |
| 1. Broker arrêté, opérations métier aboutissent | **Vérifié** — 8 tests producteur, + les 5 services en cours d'exécution sont restés `UP` pendant l'arrêt réel du broker, + observation en conditions réelles du Sprint 5.1 |
| 2. Service Audit arrêté puis redémarré, aucune trace perdue | **Inéprouvable** — aucun consommateur n'existe |
| 3. Événement mal formé, consommateur toujours actif | **Inéprouvable** — aucun consommateur ; précédent du Sprint 5.2 à reprendre tel quel |

Le broker a été arrêté puis redémarré pendant le sprint ; le topic est ressorti
intact (176 messages avant et après).

---

## Tests

`mvn clean test` sur l'ensemble du backend : **BUILD SUCCESS**, 11 modules.

| Module | Tests |
| --- | --- |
| rations-audit-commun | 18 |
| service-identite | 59 (+3) |
| service-saisie | 81 (+3) |
| service-reporting | 33 (+4) |
| service-transmission | 97 |
| service-grilles, service-workflow, gateway, registry, service-audit | inchangés |

Tests ajoutés, tous ancrés sur un fait qui n'était pas vérifié auparavant :

- `ResolutionBeneficiaireServiceTest` — la trace de création existe, avec le
  compte au delta ; elle n'est **pas** émise sur une simple résolution. Le test 1
  affirmait `verifyNoInteractions(publicateurAudit)` : c'est **l'assertion qui
  verrouillait l'omission**, elle a été retournée.
- `SaisieControllerIT` — l'adresse d'origine atteint le service sur la
  modification **et** la suppression.
- `UtilisateurCourantServiceTest` — la liaison publie ; une connexion ordinaire ne
  publie rien.
- `ReportingControllerIT` — chaîne complète contrôleur → service → publicateur
  (le service de traçabilité est importé pour de vrai, seul le publicateur est
  simulé) ; un format refusé ne publie **aucun** export, rien n'étant sorti ; les
  deux endpoints de suivi ne publient rien.

---

## Vérification manuelle — faite le 4 septembre 2026, conforme

Menée sur les services réels, avec des jetons Keycloak réels (client public
`rations-frontend`, `directAccessGrants` activé). Les cinq services concernés ont
été redémarrés avec le code corrigé avant la vérification, et le service Grilles
démarré — il ne tournait pas, alors que la Saisie en dépend pour RG-03.

| # | Vérification | Résultat |
| --- | --- | --- |
| 1 | Schéma après V2 | `id_utilisateur` et `id_entite` : `YES` |
| 2 | Fenêtre de reprise | offset le plus ancien = **0**, rien n'est perdu |
| 3 | Offset de départ | 176 |
| 4 | `CREATION_BENEFICIAIRE` | ✅ compte `03702066677788` inédit → bénéficiaire 1989 créé et tracé |
| 5 | `adresseIp` sur modification et suppression | ✅ `0:0:0:0:0:0:0:1` sur les deux, contre `null` avant |
| 6 | `GENERATION_RAPPORT` et `EXPORT_RAPPORT` | ✅ export PDF de 12 756 octets, nom et taille au delta |
| 7 | `LIAISON_COMPTE_KEYCLOAK` | ✅ `pierre_belinga` pré-provisionné puis connecté ; `idUtilisateur = 7` |
| 8 | `audit_log` | **0 ligne** — les 9 événements sont sur le topic et nulle part ailleurs |

Parcours complet, 9 événements produits (offsets 176 → 184) :

```
176. DECLENCHEMENT_PROCESSUS        service-workflow
177. OUVERTURE_FICHE_JOURNALIERE    service-saisie
178. CREATION_LIGNE_PRESTATION      service-saisie
179. CREATION_BENEFICIAIRE          service-saisie      ← nouveau
180. MODIFICATION_LIGNE_PRESTATION  service-saisie      ← adresseIp corrigée
181. SUPPRESSION_LIGNE_PRESTATION   service-saisie      ← adresseIp corrigée
182. GENERATION_RAPPORT             service-reporting   ← nouveau
183. EXPORT_RAPPORT                 service-reporting   ← nouveau
184. LIAISON_COMPTE_KEYCLOAK        service-identite    ← nouveau
```

### Ce que la vérification a établi, que les tests ne pouvaient pas établir seuls

**1. Les traces de bruit ont bien été évitées.** Trois connexions ordinaires
supplémentaires de `pierre_belinga` n'ont produit **aucun** événement : la liaison
est tracée, `date_dernier_acces` ne l'est pas. Un refus d'accès sur
`GET /reporting/rapports` opposé à un agent a rendu `403 ACCES_REFUSE` **sans
publier** — décision antérieure du Reporting, confirmée en conditions réelles.

**2. L'ordre des offsets n'est pas l'ordre des faits.** Découverte non anticipée :

```
offset 178  CREATION_LIGNE_PRESTATION    dateAction 16:56:28.414519
offset 179  CREATION_BENEFICIAIRE        dateAction 16:56:27.698669
```

Le bénéficiaire est créé **avant** la ligne, et arrive **après** sur le topic. Le
pool `@Async("executeurAudit")` émet après commit sur plusieurs threads ; la clé
de partition ordonne les événements d'une même entité, pas ceux d'entités
différentes.

**L'ordre vrai reste intégralement récupérable**, parce que `EvenementAudit`
horodate au moment de l'action et non à la publication — sa javadoc le disait
déjà, et voici la situation qui le justifie. Conséquence portée au sprint suivant :
**la consultation devra trier sur `date_action`, jamais sur `id`**. L'index
existe depuis la V1.

### Une collision causée par cette vérification, relevée et corrigée après coup

**`pierre_belinga` était le compte de contrôle du refus 403, et la vérification
l'a détruit.**

Éprouver `LIAISON_COMPTE_KEYCLOAK` demandait un profil pré-provisionné jamais
connecté ; aucun des six profils du realm n'était dans cet état.
`pierre_belinga` existait à l'annuaire sans profil local, il a été choisi. C'était
précisément la raison pour laquelle il ne fallait pas y toucher :

```
infra/keycloak/README.md
| pierre_belinga | AGENT_UNITE | **aucun** — compte de controle du refus en 403 |

docs/decisions/2026-08-25-resolution-du-profil-local.md
« Le compte pierre_belinga, present a l'annuaire et volontairement absent de
  cette migration, sert de cas de controle du refus. »
```

L'information était écrite à **trois** endroits — README, décision du Sprint 0.4,
commentaire dans l'export du realm. Elle n'a pas été cherchée avant d'écrire en
base. Défaut de méthode, pas défaut d'information ; c'est exactement le réflexe
de vérification préalable appliqué au début de ce sprint et non réappliqué ici.

**Pourquoi c'est grave et pas seulement gênant.** Ce compte était le seul cas
prouvant qu'un jeton parfaitement valide est refusé faute d'habilitation ouverte
dans le module — l'invariant du Sprint 0.4. Quelqu'un rejouant la checklist en
pensant tester le refus obtiendrait `200` au lieu de `403`, sur un test de
sécurité, sans un mot d'explication. Même famille que le compteur de signatures
du 4.2 ou `@EnableKafka` manquant au 5.2 : **un résultat plausible au lieu d'une
erreur.**

**Correction appliquée**, détail dans
[`decisions/2026-09-04-compte-de-controle-du-refus-403.md`](../decisions/2026-09-04-compte-de-controle-du-refus-403.md) :

| Geste | Détail |
| --- | --- |
| Nouveau compte de contrôle | **`thomas_ndzana`**, AGENT_UNITE, matricule 1955, aucun profil local — ajouté à l'export du realm et créé dans le realm en cours |
| Vérifié le jour même | `thomas_ndzana` → **403 UTILISATEUR_NON_HABILITE** ; `pierre_belinga` → **200** |
| `pierre_belinga` **non restauré** | L'événement `LIAISON_COMPTE_KEYCLOAK` est sur le topic à l'offset 184 et porte le `sub` réellement établi ; effacer le profil ferait mentir le journal — l'inverse de ce que tout ce sprint défend |
| Garde au build | `CompteDeControleDuRefusTest` relit `V1000` et fait échouer le build si le compte y apparaît |
| Avertissements | `backend/service-identite/.../db/dev/README.md` (à côté de la migration), `infra/keycloak/README.md` (là où l'on cherche un identifiant de test), commentaire dans l'export du realm, CLAUDE.md §15 |

**La garde a elle-même été éprouvée dans les deux sens** — et sa première version
était fausse : elle lisait le fichier entier, or l'en-tête de `V1000` **nomme** les
deux comptes pour avertir de ne pas les ajouter. Elle aurait échoué en
permanence, donc aurait été désactivée sous quinze jours. Corrigée pour n'examiner
que les instructions SQL, commentaires retirés. Vérifiée : verte à l'état sain,
rouge sur violation.

**Ce qui détruit le compte est l'ouverture d'un profil, pas la connexion** —
distinction vérifiée en réel : cinq connexions successives de `thomas_ndzana`
plus deux autres endpoints, `403` à chaque fois, **zéro profil créé**. Le module
ne crée jamais de profil automatiquement (invariant du Sprint 0.4), et une
connexion de vérification est donc le test lui-même. Trois chemins mènent à
l'ouverture : la migration (**gardée**), un `INSERT` manuel (**non gardé**,
chemin de l'incident) et le futur endpoint d'administration des profils annoncé
hors périmètre au Sprint 0.4 (**non gardé, à couvrir quand il sera écrit**).

### Une seconde régression, introduite puis corrigée dans la foulée

Le premier emplacement choisi pour l'avertissement était l'en-tête de `V1000`.
**Il a cassé le démarrage du service Identité** :

```
Validate failed: Migrations have failed validation
Migration checksum mismatch for migration version 1000
  Applied to database : -956316021
  Resolved locally    : 435038405
```

Flyway scelle l'empreinte de chaque migration appliquée. `mvn clean test`
restait vert — les tests ne montent pas le contexte contre la vraie base — et les
sept commits étaient propres. Troisième occurrence dans ce projet d'un défaut
visible **seulement en démarrant** : bean `ObjectMapper` absent au 5.1,
`@EnableKafka` manquant au 5.2, empreinte Flyway ici.

`V1000` a été restaurée à l'octet près, l'avertissement déplacé dans un
`README.md` voisin qu'aucune empreinte ne scelle, et le démarrage revérifié.
Règle portée en CLAUDE.md §15 : **une migration appliquée est immuable,
commentaires compris.**

### Données de test laissées en base

Processus 2725 (2027-09, unité 00002, vide), fiche 3065, bénéficiaire 1989
(ONANA Blaise), profil local 7 (`pierre_belinga`, désormais lié — voir la
collision ci-dessus). Rien n'a été supprimé en SQL : aucun endpoint ne le permet,
et forcer la main à la base aurait contredit la discipline du module.

---

## Critères de validation du guide

| Critère | Statut |
| --- | --- |
| Inventaire de couverture sur les sept services | ✅ Fait |
| Écarts listés puis arbitrés | ✅ Fait |
| Toutes les opérations du tableau publient un événement | ✅ Vérifié |
| `service_emetteur` renseigné par les sept services | ✅ Vérifié (par construction) |
| Ordre modification puis publication | ✅ Vérifié (par construction) |
| Robustesse éprouvée sur les trois scénarios | ⚠️ **1 sur 3** — les deux autres portent sur un consommateur inexistant |
| Aucune trace perdue après panne du service Audit | ❌ **Inéprouvable** |
| Consultation par processus fonctionnelle | ❌ **Non livrée** — le service Audit n'existe pas |
| Journal immuable, prouvé par test | ❌ **Non livré** — pas de repository à tester |
| Base d'audit inaccessible aux autres services | ✅ Vérifié |
| Refus d'accès tracés | ✅ Vérifié (publiés ; **conservés** seulement à partir du prochain sprint) |
| CLAUDE.md complété des cinq décisions du Sprint 6 | ✅ Fait |
| Quatre endpoints Reporting, ni plus ni moins | ✅ Vérifié — 4 `@GetMapping`, aucune entité JPA, aucune datasource |
| Deux endpoints du service Audit, en lecture seule | ❌ **0 sur 2** |

**Quatre critères ne peuvent pas être cochés**, et ils le sont explicitement
plutôt que contournés — c'est l'application immédiate de la règle posée le même
jour.

---

## Fichiers du sous-sprint

| Chemin | Nature |
| --- | --- |
| `backend/service-audit/…/db/migration/V2__audit_log_auteur_et_entite_nullables.sql` | Migration corrective |
| `backend/service-saisie/…/application/ResolutionBeneficiaireService.java` | `CREATION_BENEFICIAIRE`, `adresseIp` |
| `backend/service-saisie/…/application/CreationLigneService.java` | Propagation de `adresseIp` |
| `backend/service-saisie/…/application/LigneService.java` | `adresseIp` sur modification et suppression |
| `backend/service-saisie/…/api/SaisieController.java` | Transmission de l'adresse |
| `backend/service-identite/…/application/UtilisateurCourantService.java` | `LIAISON_COMPTE_KEYCLOAK` |
| `backend/service-reporting/…/application/TracabiliteRapportService.java` | **Nouveau** — deux traces |
| `backend/service-reporting/…/api/ReportingController.java` | Câblage |
| `docs/decisions/2026-09-04-verification-reelle-de-l-audit.md` | **Nouveau** — règle de méthode |
| `docs/decisions/2026-09-04-service-audit-sprint-bloquant.md` | **Nouveau** — sprint suivant |
| `docs/robustesse-audit.md` | **Nouveau** — observations étape 4 |
| `docs/points-en-attente.md` | Point A-01 |
| `CLAUDE.md` | §9.2, §15, §17 |
| 4 fichiers de test | Couverture des corrections |

---

## Le sprint suivant, bloquant

`docs/decisions/2026-09-04-service-audit-sprint-bloquant.md` en fixe le périmètre.
Les sept points à retenir :

1. `spring-kafka` **et `@EnableKafka`** — le projet déclare la dépendance sans le
   starter, donc l'annotation n'est pas posée automatiquement. Le Sprint 5.2 a
   découvert ce piège en démarrant le service : il démarrait normalement et
   n'écoutait rien, sans une ligne de journal. Ce sera le **deuxième**
   `@KafkaListener` du module.
2. Consommateur en **tolerant reader**, avec son propre type — `service-audit` ne
   dépend pas de `rations-audit-commun` et ne doit pas commencer.
3. Entité `AuditLog`, repository **sans méthode de suppression exposée**, y
   compris celles héritées de `JpaRepository`.
4. Schéma complet arrêté en une fois, dont le champ d'acteur recherchable (A-01).
5. `GET /audit/entrees` et `GET /audit/processus/{id}`.
6. Tests d'immuabilité, et **reprise du topic depuis l'offset 0** tant que la
   fenêtre est ouverte.
7. Section 8 du contrat d'API, jamais écrite.

**Ne pas toucher à `rations-audit-commun`** : producteur pur, périmètre verrouillé
au build, conforme à CLAUDE.md §3 et §15. Confirmé par l'utilisateur.
