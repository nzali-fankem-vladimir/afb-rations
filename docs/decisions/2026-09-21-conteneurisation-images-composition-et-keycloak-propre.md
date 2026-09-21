# Conteneurisation : images, composition autosuffisante, Keycloak propre au module

**Date :** 21 septembre 2026
**Sprint :** 8.2, conteneurisation et publication des images
**Statut :** appliqué, vérifié en réel (démarrage à froid sur volumes vides, parcours complet, vraie composition). À respecter par les Sprints 8.3 (Kubernetes) et 9 (recette).

---

## 1. Une image par module, construite DANS l'image, contexte `backend/`

Dix images : sept services, passerelle, registre, frontend. `rations-audit-commun` est une
bibliothèque et **n'a aucune image**.

- Construction multi-étapes : compilation Maven dans l'image (cache BuildKit du dépôt
  Maven), exécution sur `eclipse-temurin:21-jre-alpine`. Le poste n'a pas à compiler.
- Le contexte de build est `backend/` : le pom parent et la bibliothèque en sont hors. Ordre
  dans un seul `RUN` : parent seul (`-N`), bibliothèque, module. Séparer les étapes
  laisserait une couche « bibliothèque » en cache alors que le dépôt qui la contient serait
  vide. Défaut trouvé au premier build : installer la bibliothèque n'installe pas son pom
  parent.
- **Aucun jar exécutable n'existait** : le backend ne déclarait `spring-boot-maven-plugin`
  nulle part (les services tournaient depuis `target/classes`). Le plugin est déclaré
  **module par module** dans les neuf pom, pas dans le parent : une bibliothèque
  repackagée devient inutilisable comme dépendance.
- **Garde structurelle** : les Dockerfiles d'Audit, de la passerelle et du registre ne
  copient pas la bibliothèque. Si l'un d'eux en gagnait la dépendance, son build échouerait.
  Même discipline que `PerimetreDuModuleTest` (1.3).
- Utilisateur non privilégié, identifiant **numérique** `10001` (frontend : `101`), créé
  explicitement. Un orchestrateur ne peut pas prouver qu'un nom n'est pas root, seul un
  identifiant le prouve : c'est ce que vérifiera `runAsNonRoot` au Sprint 8.3.
- Sonde : `wget` sur `127.0.0.1:${SERVER_PORT}/actuator/health` (pas `localhost`, que busybox
  peut résoudre en IPv6). Le JRE Alpine de Temurin porte déjà `wget`, les polices et
  `libfreetype` : `autoSizeColumn` de POI (export Excel du Reporting) y fonctionne, vérifié.
- JVM : `-XX:MaxRAMPercentage=75` (calée sur la limite du conteneur) et
  `-XX:+ExitOnOutOfMemoryError` (un service à court de mémoire s'arrête et est relancé).

## 2. Frontend : configuration lue au démarrage, pas figée au build (option A)

**Quatre** variables étaient figées par Vite, pas une : adresse de la passerelle et trois
variables Keycloak (adresse, realm, client). Au démarrage, le conteneur écrit `/config.js`
depuis `API_BASE_URL`, `KEYCLOAK_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_CLIENT_ID` ; la page le
charge avant l'application, qui lit `window.__CONFIG__` puis se replie sur le `.env` de
développement (`src/config/configurationExecution.ts`). Une seule image pour la recette et
la production.

- Le script de démarrage **refuse de démarrer** si une variable manque, et n'accepte que des
  caractères d'adresse (un guillemet briserait le fichier généré ou y injecterait du code).
- `.dockerignore` exclut `.env` : Vite le lit au build et aurait inscrit des adresses
  `localhost` dans le paquet. Vérifié : aucune dans le paquet.
- `config.js` n'est jamais mis en cache ; les fichiers `assets/` (noms hachés) le sont un an.
- **Aucune Content-Security-Policy pour l'instant** : elle doit nommer la passerelle et
  Keycloak, dont les adresses dépendent de l'environnement. À reprendre avec la DSI (D-08).
- Le frontend n'a pas de framework de test : vérifié par `tsc`, `oxlint`, le build et un vrai
  conteneur. Ajouter un cadre de test est un choix à part.

## 3. Composition autosuffisante : un poste neuf n'a rien à faire

Exigence de l'utilisateur : l'autre développeur lance `docker compose up -d` et tout démarre.

- **Keycloak propre à la composition** (`rations-keycloak`), realm importé automatiquement
  depuis `infra/keycloak/realm-afb-rations-dev.json`, sans volume (le fichier est la source).
  **Aucun lien avec `dottel-keycloak`** : conteneur et port distincts. Sur un poste où
  DOTTEL occupe 8180, `KEYCLOAK_PORT=8181` dans `.env` ; l'émetteur des jetons et l'adresse
  donnée au frontend s'en déduisent.
- **Le realm n'était pas importable tel quel** : deux champs `_commentaire` sont refusés à
  l'import au démarrage (le script `init-realm.ps1` passait par l'API d'administration,
  plus tolérante). Le texte a été déplacé dans `attributes.commentaire` de chaque compte
  (l'avertissement « ne jamais ouvrir de profil à `thomas_ndzana` » reste contre le compte),
  intégralement conservé, le reste du realm identique.
- **Les `sub` des sept comptes liés sont fixés dans le realm** (`id` de chaque utilisateur),
  relevés dans la table `utilisateurs` de la base de développement. Sans cela, un Keycloak neuf
  génère d'autres identifiants et le service Identité **refuse** les profils déjà liés
  (« deux comptes revendiquant le même login », décision du Sprint 0.4) : la vérification
  visuelle sur le poste du développeur aurait échoué en 403. Avec eux, le Keycloak de la
  composition est un remplaçant direct de celui de DOTTEL pour ce realm, et la liaison au
  premier login est reproductible d'un poste à l'autre. Prouvé : les jetons de `jean_mbarga` et
  `paul_essama` portent exactement le `sub` enregistré en base. `thomas_ndzana`, sans profil,
  n'a pas d'`id` figé (il n'y en a rien à lier, et il doit rester refusé).
- **Validation des jetons depuis un conteneur** : le jeton porte `iss=http://localhost:<port>`
  (le navigateur le demande) mais `localhost` est le conteneur lui-même. On sépare l'émetteur
  attendu (`KEYCLOAK_ISSUER_URI`) et l'adresse des clés
  (`SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI`, par le réseau interne). Sans cela,
  tout jeton est refusé en 401. Prouvé avec un vrai jeton : 200 avec la variable, 401 sans.
  **Aucun code modifié.** Nom relaxé : `jwk-set-uri` devient `JWKSETURI` (tirets retirés).
- **Kafka annonce deux adresses** : `kafka:19092` (conteneurs) et `localhost:9092` (poste).
  Avec la seule `localhost:9092` du Sprint 0.5, un conteneur est renvoyé vers lui-même.
  Une fois les variables `KAFKA_*` posées, le fichier de configuration par défaut de l'image
  n'est plus lu : tout est donc obligatoire. Volume conservé (topics et offsets).
- **`kafka-init`** crée les topics au démarrage, en exécutant `kafka-topics.sh`, qui reste le
  **seul endroit** où les noms sont écrits (D-07). Le script a désormais deux modes (`docker
  exec` depuis le poste, ou `KAFKA_LOCAL=1` dans un conteneur).
- **Registre allumé** dans la composition (`EUREKA_ENABLED=true`), routage `lb://`.
  Repli documenté dans `.env.example` : registre éteint et sept adresses fixes.
- **Ports** : passerelle (8080) et frontend (5173) exposés ; registre, Keycloak et services
  publiés sur `127.0.0.1` seulement.
- **Mémoire plafonnée** : 512 Mo par service (mesuré 173 à 335 Mo), 384 Mo passerelle et
  registre, 768 Mo Keycloak, 128 Mo frontend, tas de Kafka ramené de 1 Go à 512 Mo.
- **Secret partagé** `INTEGRATION_CLE_INTERNE` : une seule variable alimente le Workflow et
  la Transmission, qui doivent recevoir la même valeur.
- **Volume nommé** `rations-pieces-jointes` pour les états signés (RG-09), racine
  `/donnees/pieces-jointes` créée au nom de l'utilisateur non privilégié.

## 4. Démarrage : tolérance, pas temporisation

Les conditions de démarrage reposent sur les sondes (`service_healthy`), jamais sur une durée.
La preuve à froid a pourtant révélé que **les sondes elles-mêmes étaient trop strictes** sur
un poste neuf, où sept JVM et Keycloak démarrent ensemble et saturent chacun un cœur :

- un contexte Spring met plus de 70 s (mesuré) : budget des services porté à
  `start_period: 240s`, 10 tentatives (la sonde déclare le service sain dès sa première
  réponse ; seul un échec **après** cette période compte) ;
- la sonde de Kafka lance une JVM entière : `timeout: 30s`, 20 tentatives.

Sans cela, Compose déclarait « unhealthy » des services qui fonctionnaient et renonçait à
démarrer la passerelle. **Au Sprint 8.3, une `startupProbe` généreuse (au moins 240 s) est
nécessaire pour la même raison.**

## 5. Le premier événement d'audit après chaque démarrage se perdait

Mesuré : le producteur est créé au premier envoi, dont la connexion et les métadonnées
prennent environ 2,3 s sur ce poste, au-delà des 2 s de `max.block.ms`. Le service Audit ne
perdait rien (11 lignes en base pour 11 messages). Ce n'est pas la composition : le CLI Kafka
met le même temps sur les deux adresses.

Réponse : `PrechauffageProducteurAudit` interroge le broker à `ApplicationReadyEvent`, sur le
pool d'audit, avec trois tentatives, sans jamais lever. **La borne de 2 s n'est pas relevée**
(CLAUDE.md §15). Limites, consignées dans `docs/points-en-attente.md` : aucune garantie si le
broker est absent, ni pour un événement émis avant la fin du préchauffage. **L'outbox
transactionnel est planifié comme sprint dédié avant la production**, avec l'arbitrage des
deux services sans base (Reporting, Transmission).

## 6. Fins de ligne : `.gitattributes`

`core.autocrlf=true` (poste Windows) réécrit les fichiers en CRLF au checkout. Un script shell
en CRLF échoue dans un conteneur Linux (`#!/bin/sh\r`). `*.sh text eol=lf` couvre le script du
frontend, `kafka-topics.sh` et `postgres-init/01-init-databases.sh`, ce dernier étant exécuté
par PostgreSQL au premier démarrage : un CRLF aurait empêché la création des bases chez
l'autre développeur, sans message clair.

## 7. Ce qui reste, et où le traiter

| Point | Où |
|---|---|
| Profil `dev` seul dans les images (comptes de test `V1000`, valeurs de repli) | Sprint 8.3, `docs/points-en-attente.md` |
| Adresse Harbor, projet, compte robot (D-02), pipeline (D-09) | DSI, `docs/publication-images.md` |
| Sécurité du registre Eureka (aucune) | Sprint 8.3 / D-08 |
| Outbox transactionnel | Sprint dédié avant la production |
| Content-Security-Policy du frontend | Avec la DSI (D-08) |
| Données des états déjà signés sur les postes de développement | Non migrées : le volume démarre vide |

## 8. Leçons de méthode

- **Une preuve à froid sur pile jetable** (autre nom de projet, volumes neufs, ports décalés)
  a trouvé cinq défauts que ni les tests ni la vraie composition n'auraient montrés : sonde
  Kafka, sondes de services, realm non importable, script des topics (un second appel à
  `docker exec` que je n'avais pas vu), perte du premier événement d'audit. Elle sert aussi à
  jouer le parcours complet sans salir la base ni le journal d'audit immuable.
- **Le parcours de test doit s'écrire d'après le code, pas de mémoire** : mes premières
  versions supposaient `id` là où la réponse porte `idProcessus`, et une grille à créer alors
  qu'un jeu de départ en fournit une. Deux erreurs de script, pas du système.
- **`docker compose down -v` sans le fichier de surcharge viserait les vrais volumes.** La
  pile jetable se détruit par `down` puis `docker volume rm` sur les noms explicites.
- Une modification de configuration ne se valide pas par le diff : elle se valide en la
  démarrant (même famille que le Sprint 8.1).

## 9. Espace disque (relevé du 21 septembre 2026)

Le poste de développement n'avait que **8 Go libres sur 235** en fin de sprint : l'espace compte.

| Élément | Taille | Nature |
|---|---|---|
| Disque virtuel de Docker (`docker_data.vhdx`) | 17,1 Go | Ce qui occupe réellement le disque C: |
| Images du module (10) | environ 3 Go réels (4,7 Go affichés, la base Alpine étant partagée) | Nécessaires |
| Cache de build | 9,4 Go, dont 8,5 Go récupérables | Maven, npm, images Maven et Node |
| Volumes du module | 1,3 Go | Bases, topics, documents signés : à ne jamais supprimer |

- Nettoyé pendant le sprint : 12 volumes anonymes laissés par les conteneurs des piles
  jetables, et l'image `alpine` tirée pour l'archive Kafka. Les anciens volumes, conteneurs et
  images (DOTTEL, autres projets) n'ont pas été touchés.
- **Le cache de build est le seul poste réellement inutile une fois les images construites.**
  Il ne sert qu'à accélérer une reconstruction. Le purger libère jusqu'à 8,5 Go dans Docker,
  au prix de quelques dizaines de minutes au prochain build (téléchargement des dépendances).
- **Sous Windows, purger ne rend pas l'espace à C: tout seul** : le fichier
  `docker_data.vhdx` ne rétrécit pas. Il faut le compacter (Docker Desktop, réglages
  Ressources, ou `Optimize-VHD` après `wsl --shutdown`, ce qui arrête Docker et toute la pile).
- **Purgé le 2026-09-21, sur décision de l'utilisateur (`docker builder prune -a`)** : cache
  de 9,45 Go à 0, images et pile intactes. **Constat mesuré : `docker_data.vhdx` est resté à
  17,1 Go et C: à 7,9 Go libres.** L'espace est libre dans Docker, pas sur le disque : sans
  compactage du disque virtuel, la purge ne rend rien à Windows. Compactage non fait (arrêt de
  Docker et de la pile, terminal administrateur), à décider par l'utilisateur.
- L'archive du volume Kafka (`C:\Users\Pro\sauvegardes-rations\kafka-data-2026-09-21.tgz`,
  7,3 Mo) peut être supprimée une fois le sprint validé.
