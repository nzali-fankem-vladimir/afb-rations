# Résumé Sprint 8.2: Conteneurisation et publication des images

**Date :** 21 septembre 2026
**Objet du guide :** produire une image Docker par module, composer l'ensemble, documenter la publication sur Harbor
**Ce qui a réellement été fait :** les 6 étapes du guide, plus un cahier des charges élargi par l'utilisateur en cours de route (composition **autosuffisante** pour un second développeur) et un correctif de la bibliothèque d'audit

---

## En une phrase

Le module se lance désormais en une commande, `docker compose up -d` dans `infra/docker`, sur un poste neuf, sans rien installer ni configurer : dix images non privilégiées et sans secret, un Keycloak propre au module, Kafka et ses topics, tout démarré dans l'ordre par les sondes de santé, et un frontend dont les adresses se posent au démarrage du conteneur plutôt qu'au build.

---

## Décisions prises en cours de route (arbitrées par l'utilisateur)

| Point | Décision |
| --- | --- |
| Plugin Spring Boot (étape 1) | **Module par module** dans les neuf pom, pas dans le parent |
| Image d'exécution (étape 1) | **Alpine** (JRE Temurin), `wget` et polices vérifiés présents |
| URL du frontend (étape 4) | **Option A** : `/config.js` écrit au démarrage depuis les variables du conteneur |
| Poste (étape 5) | **Go** aux trois gestes : arrêt des 10 JVM et de Vite, recréation de Kafka avec archive, registre Eureka allumé |
| Périmètre élargi (étape 5) | La composition doit permettre à un **autre développeur de n'avoir rien à faire** : Keycloak propre, topics créés, tout démarre |
| Perte du premier audit | **Option A** : préchauffage du producteur. **Outbox transactionnel** planifié comme sprint dédié avant la production |

Changement de modèle demandé par le guide : aucun (Sonnet, effort moyen, honoré).

---

## Étape par étape

### Étape 1: Ouverture et gabarit

Trois constats avant d'écrire une ligne : **aucun jar exécutable n'existait** (le backend ne déclarait `spring-boot-maven-plugin` nulle part, les services tournaient depuis `target/classes`) ; les ports 8080 à 8087, 8761 et 5173 étaient tous occupés par des JVM lancées à la main ; le poste n'avait que 1,3 Go libres. Le guide disait « huit » Dockerfiles : il y en a **neuf** backend plus le frontend, soit dix images.

### Étape 2: Gabarit vérifié en réel

Multi-étapes : Maven en cache BuildKit, exécution sur JRE Alpine, utilisateur **numérique** 10001, sonde `wget`. Premier build échoué : installer la bibliothèque n'installe pas son pom parent. Corrigé (`install -N`), puis image lancée : `healthy`, 335 Mo en marche, Flyway valide sans rien modifier.

### Étape 3: Déclinaison

Huit Dockerfiles. Le Workflow reçoit un répertoire de documents signés accessible en écriture à l'utilisateur non privilégié. Audit, passerelle et registre **n'embarquent pas** la bibliothèque : une dépendance glissée un jour ferait échouer leur build. Mon premier script de génération, déformé par l'outil, avait vidé les huit fichiers ; regénérés depuis des gabarits, `diff` fidèle au gabarit approuvé.

### Étape 4: Frontend

Le guide parlait d'**une** adresse figée au build : il y en avait **quatre** (passerelle et trois Keycloak). Module `configurationExecution.ts`, script de démarrage qui **refuse** une variable absente ou piégée (`"};alert(1);//` refusée), nginx non privilégié, `.dockerignore` qui exclut le `.env` local. Image de 82 Mo, aucune adresse `localhost` dans le paquet. `.gitattributes` ajouté : sans lui, votre `core.autocrlf=true` aurait réécrit le script en CRLF.

### Étape 5: Composition

Deux problèmes de fond, **prouvés avant d'écrire** : le jeton réel porte `iss=localhost:8180` que les conteneurs ne joignent pas (**401 sans correctif, 200 avec**, par une seule variable d'environnement) ; Kafka n'annonçait que `localhost:9092` (double adresse). Puis l'exigence élargie : Keycloak propre au module, `kafka-init`, realm importé automatiquement.

**Preuve de démarrage à froid** sur une pile jetable (autre nom de projet, volumes neufs, ports décalés) : elle a trouvé cinq défauts, détaillés plus bas. Parcours complet joué dessus par API avec de vrais jetons, sans salir votre base ni le journal d'audit immuable. Puis lancement de la vraie composition sur vos volumes.

### Étape 6: Publication Harbor

`docs/publication-images.md` : nommage, étiquetage, connexion, marquage, publication, vérification. Rédigée **sans script**, avec les marqueurs `A_CONFIRMER_DSI_*`. **Écart au guide** : il renvoie « les identifiants de publication » au document maître section 10, qui ne liste que le pipeline (D-09) ; le projet Harbor et le compte robot y sont ajoutés comme nouveaux points.

---

## Défauts trouvés et corrigés, invisibles aux tests

| # | Défaut | Comment il s'est vu | Correctif |
| --- | --- | --- | --- |
| 1 | La sonde de Kafka lance une JVM entière : « unhealthy » sous charge, Compose renonce | Preuve à froid | `timeout: 30s`, 20 tentatives |
| 2 | Sondes des services trop courtes : un contexte Spring met plus de 70 s quand sept JVM démarrent ensemble | Preuve à froid | `start_period: 240s`, 10 tentatives |
| 3 | Le realm versionné **n'était pas importable** : deux champs `_commentaire` refusés par Keycloak | Keycloak en boucle de redémarrage | Déplacés dans `attributes.commentaire`, texte intégralement conservé |
| 4 | `kafka-topics.sh` appelait encore `docker exec` dans sa liste finale, absent du conteneur | `kafka-init` sorti en 127 | Second appel corrigé |
| 5 | **Le premier événement d'audit de chaque service démarré était perdu** (connexion à 2,3 s pour une borne de 2 s) | `AUDIT PERDU` dans les journaux, 4 événements manquants sur 14 | `PrechauffageProducteurAudit`, borne **non** relevée |

Un sixième point est né d'une vérification de fond : **votre base porte les `sub` de DOTTEL**, et un Keycloak neuf en génère d'autres, ce que le service Identité refuse en 403. Les `sub` des sept comptes liés sont fixés dans le realm ; prouvé : les jetons de `jean_mbarga` et `paul_essama` portent exactement ceux de votre base.

Erreurs de ma part, corrigées : mon script de parcours supposait `id` là où la réponse porte `idProcessus`, et une grille à créer alors qu'un jeu de départ en fournit une ; un premier `docker compose build` avait échoué sur un `TLS handshake timeout` de Docker Hub (transitoire), relancé avec moins de builds en parallèle.

---

## Contrôles finaux

| Contrôle | Résultat |
| --- | --- |
| `docker compose build` | **Exit 0, dix images** construites |
| Backend `mvn clean test`, 11 modules | **BUILD SUCCESS**, 0 échec ; bibliothèque d'audit à 25 tests dont 7 nouveaux |
| Frontend `tsc -b`, `oxlint` | 0 erreur, 0 avertissement |
| Démarrage à froid, volumes vides | 13 conteneurs `healthy`, `up` retourne 0 |
| Vraie composition, vos volumes | 14 conteneurs `healthy`, `up` retourne 0 en 7 min |
| Utilisateur dans les 10 conteneurs | `rations` uid 10001 ; frontend `nginx` uid 101 |
| Secrets dans l'historique des 10 images | 0 occurrence |
| Données conservées | 406 lignes d'audit, 7 profils, 23 processus, volume Kafka 37,6 Mo (37,5 avant), même identifiant de cluster |
| Documents signés | 20 chemins en base sur 20 présents dans le volume |
| Connexion avec vos profils liés | `jean_mbarga` et `martin_fouda` : 200 ; `thomas_ndzana` : 403 (invariant du Sprint 0.4) |
| Cycle sur services tout juste démarrés | **8 événements sur 8** en base, 0 `AUDIT PERDU` |
| Mémoire en marche | 11 à 344 Mo par conteneur, tous sous leur plafond |

**Tailles** (affichées par Docker, base Alpine partagée) : frontend 82 Mo, registre 398 Mo, passerelle 404 Mo, Transmission 440 Mo, Reporting 479 Mo, Identité, Saisie, Grilles et Audit 499 Mo, Workflow 505 Mo.

---

## Vérification des critères de validation du guide

| Critère (guide §9 et §11) | Statut |
| --- | --- |
| Neuf Dockerfiles, construction multi-étapes | **Fait** (neuf backend plus le frontend) |
| Image du service Audit construite | **Vérifié** |
| `rations-audit-commun` sans image | **Vérifié** |
| Utilisateur non privilégié dans chaque image | **Vérifié**, `docker exec` sur les 10 conteneurs |
| Sonde de santé dans chaque image | **Vérifié**, 14 conteneurs `healthy` |
| Aucun secret dans les images | **Vérifié**, `docker history` sur les 10 |
| Composition démarrant l'ensemble | **Vérifié**, à froid et sur vos données |
| Ordre de démarrage fondé sur les sondes | **Vérifié** (`service_healthy`, aucune temporisation) |
| Décision sur l'URL du frontend | **Fait**, option A |
| Parcours complet en conteneur | **Vérifié par API** avec de vrais jetons, **puis à l'écran par l'utilisateur** (étapes 1 à 7 de la vérification visuelle) |
| Procédure de publication, valeurs signalées | **Fait** |
| Taille des images raisonnable | **Vérifié** (multi-étapes, aucune image ne porte Maven ni les sources) |
| Frontend : URL de la passerelle résolue | **Vérifié** (`config.js` servi, aucune adresse figée dans le paquet) |

**Réserves.** L'utilisateur a joué les parcours à l'écran ; l'étape 8 (DOTTEL intact) a été **vérifiée par moi** à sa demande de me faire confiance : conteneur actif depuis le 16 septembre, 0 redémarrage, realms `master`, `dottel-dev` et `afb-rations-dev` répondant en 200. La **publication sur Harbor n'a pas été exécutée** (accès inconnus). Le démarrage sur un **autre poste** est simulé par la preuve à froid, pas joué sur une seconde machine.

---

## Points ouverts consignés

- **Outbox transactionnel** : sprint dédié avant la production, avec l'arbitrage des deux services sans base (Reporting, Transmission). Le préchauffage ne garantit rien si le broker est absent (`docs/points-en-attente.md`).
- **Profil de production absent** : les images tournent en profil `dev` (comptes de test `V1000`). À traiter au Sprint 8.3 avant toute publication.
- **Harbor** : adresse (D-02), projet et compte robot (nouveaux), pipeline (D-09).
- **`startupProbe`** d'au moins 240 s à prévoir au Sprint 8.3, pour la même raison que le budget des sondes ici.
- **Content-Security-Policy** du frontend et **sécurité du registre Eureka**, avec la DSI (D-08).

## Vérification visuelle et suites, après le résumé

L'utilisateur a joué les étapes de vérification à l'écran (connexion via le Keycloak de la composition sur le port 8181, états existants, téléchargement d'un document signé, exports, journal d'audit, refus de `thomas_ndzana`, appels réseau uniquement vers la passerelle). Aucun défaut signalé. Il a choisi l'**option A** pour le cache de build et demandé la mise à jour de deux guides d'exploitation locaux.

## Espace disque

Le poste n'avait que **8 Go libres sur 235** en fin de sprint.

| Élément | Avant nettoyage | Après |
| --- | --- | --- |
| Cache de build (dans Docker) | 9,45 Go | **0** (option A : `docker builder prune -a`) |
| Volumes anonymes de ce sprint | 12 | supprimés |
| Image `alpine` temporaire | 13 Mo | supprimée |
| Images du module (10) | 5,19 Go affichés, environ 3 Go réels | conservées, nécessaires |
| Volumes du module | 1,27 Go | conservés, ce sont les données |
| **`docker_data.vhdx` (ce qui occupe C:)** | 17,1 Go | **17,1 Go** |
| **Disque C: libre** | 8,0 Go | **7,9 Go** |

**La purge a libéré l'espace à l'intérieur de Docker mais pas sur C:** sous Windows, le disque
virtuel `docker_data.vhdx` ne rétrécit pas tout seul. Récupérer environ 9 Go sur C: exige de
le compacter (Docker Desktop fermé, `wsl --shutdown`, puis `Optimize-VHD` ou `diskpart`
depuis un PowerShell administrateur). Cela arrête Docker et la pile, dont les données sont
conservées. **Non fait**, faute d'accord explicite : il faut un terminal élevé, et l'arrêt de
la pile que l'utilisateur vient de valider.

Les anciens volumes (6), conteneurs arrêtés (13) et images (Kafka 3.8.0, `dottel-backend`) ne
sont pas de ce sprint et n'ont pas été touchés. L'archive du volume Kafka
(`C:\Users\Pro\sauvegardes-rations\kafka-data-2026-09-21.tgz`, 7 Mo) peut être supprimée.

## Documents produits

- `docs/decisions/2026-09-21-conteneurisation-images-composition-et-keycloak-propre.md` : les décisions et leurs motifs
- `docs/publication-images.md` : procédure Harbor
- `docs/points-en-attente.md` : outbox, Harbor, profil de production
- `CLAUDE.md` : section 3 complétée, neuf lignes en section 17, trois garde-fous en section 15
- `README.md` : démarrage complet avec Docker
- `infra/keycloak/README.md` : contraintes du realm importé
- `infra/docker/.env.example` : variables documentées, aucune obligatoire
- **Deux guides d'exploitation locale, non versionnés** (décision de l'utilisateur, ignorés par Git) :
  `docs/GUIDE_LANCEMENT_ET_VERIFICATIONS.md` (deux modes de lancement, mode Docker complet,
  dépannage, sauvegardes de volumes) et `docs/LIBERATION_PERIODES_ET_NETTOYAGE_DEMO.md`
  (emplacement des PDF selon le mode, nettoyage du volume, chiffres Kafka et audit corrigés).
  **Chaque commande nouvelle a été exécutée avant d'être écrite**, sur des volumes jetables
  quand elle était destructive. Corrections de fond apportées : l'offset de fin d'un topic
  n'est pas le nombre de messages lisibles (rétention de 7 jours), et un `docker exec` échoue
  sur un conteneur arrêté (le nettoyage passe donc par `docker run` sur le volume).
