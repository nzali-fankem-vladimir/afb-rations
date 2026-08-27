# SPRINT 8.2

## Conteneurisation et publication des images

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Produire une image Docker par service et les publier sur le registre privé |
| **Livrable** | Dockerfiles, composition complète, images publiées sur Harbor |
| **Durée** | Une journée |
| **Prérequis** | Sprint 8.1 validé et commité |
| **Sprint suivant** | 8.3, déploiement Kubernetes |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Dockerfiles (étapes 2-4) | Sonnet | Moyen |
| Composition et publication (étapes 5-6) | Sonnet | Moyen |

Aucun changement manuel.

## 2. Outil de cartographie

Sans objet : ce sous-sprint produit de la configuration d'infrastructure.

## 3. Contexte

Le module fonctionne en développement, chaque service lancé depuis l'IDE. Ce sous-sprint le rend déployable : une image par service, plus une pour le frontend, publiées sur le registre privé Harbor de la banque.

Le Sprint 0.5 avait produit une composition Docker limitée à l'infrastructure, PostgreSQL, Kafka et Keycloak. Elle s'étend ici aux services applicatifs, ce qui permet de faire tourner l'ensemble sans IDE et de valider le comportement en conteneur avant le déploiement.

Deux exigences de sécurité s'appliquent, héritées des standards de la DSI : les conteneurs ne tournent pas en utilisateur privilégié, et aucun secret n'est inscrit dans une image.

## 4. Objectifs

- Un Dockerfile multi-étapes par service backend
- Un Dockerfile pour le frontend, servi par un serveur statique
- Utilisateur non privilégié et sonde de santé dans chaque image
- Composition Docker complète, infrastructure et services
- Images publiées sur le registre Harbor

## 5. Règles concernées

Aucune règle métier. Document maître section 7.6 : aucun secret dans le code, la configuration ou les images.

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis CLAUDE.md, section 2 pour la stack et section 12
pour les conventions. Confirme en 3 lignes la version de Java et le
mode de conteneurisation retenu.

CONTEXTE DE CETTE SESSION : Sprint 8.2, conteneurisation. La
passerelle et le registre sont en service depuis le Sprint 8.1. On
produit maintenant les images.
SERVICE CONCERNE : les huit modules backend, plus le frontend.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Aucun secret dans une image, aucun utilisateur privilegie.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : propose le Dockerfile d'un service backend, en
prenant service-identite comme gabarit. Construction multi-etapes,
image de base adaptee a Java 21, utilisateur non privilegie, sonde de
sante, port expose. Montre-le moi avant de le decliner sur les autres
services.
```

### Étape 2. Dockerfile de référence

```
Une fois le gabarit valide, affine-le :

- Etape de construction : compilation Maven, avec mise en cache des
  dependances pour eviter de les retelecharger a chaque build.
- Etape d'execution : image legere, seulement le jar et ce qui lui
  est necessaire.
- Utilisateur non privilegie, cree explicitement.
- Sonde de sante interrogeant l'endpoint actuator.
- Aucune variable sensible : elles seront injectees au demarrage.

Montre le fichier final.
```

### Étape 3. Déclinaison aux autres services

```
Decline le gabarit sur les sept autres modules backend : les cinq
services restants, la passerelle et le registre.

Adapte le port expose de chacun, selon l'attribution du Sprint 0.1.

Si un service a un besoin particulier, signale-le plutot que de
l'ignorer : le service Reporting genere des documents, le service
Transmission dialogue avec Kafka.

Montre les fichiers un par un.
```

### Étape 4. Dockerfile du frontend

```
Cree le Dockerfile du frontend :

- Etape de construction : installation des dependances et build de
  production.
- Etape d'execution : serveur statique servant les fichiers produits.

Question a trancher : l'url de la passerelle est figee au moment du
build par Vite. Comment permettre de deployer la meme image en
recette et en production, qui n'ont pas la meme url ? Presente les
options, notamment l'injection au demarrage du conteneur. J'arbitre.

Montre le fichier.
```

### Étape 5. Composition complète

```
Etends la composition Docker du Sprint 0.5 :

Aux trois briques d'infrastructure existantes, ajoute les huit
modules backend et le frontend.

Prevois :
- l'ordre de demarrage : infrastructure, puis registre, puis
  services, puis passerelle, puis frontend
- les conditions de demarrage fondees sur les sondes de sante, pas
  sur des temporisations
- les variables d'environnement injectees, avec repli explicite
- les volumes nommes, deja poses au Sprint 0.5

Un demarrage fonde sur une temporisation fixe echoue des que la
machine est plus lente que prevu.

Montre le fichier.
```

### Étape 6. Publication sur Harbor

```
Redige la procedure de publication des images sur le registre Harbor
de la banque :

- Convention de nommage des images, incluant le nom du module.
- Convention d'etiquetage des versions.
- Commandes de connexion, de marquage et de publication.

L'adresse du registre et les identifiants de publication sont des
points en attente DSI, listes au document maitre section 10. Utilise
des valeurs de substitution explicites et signale-les.

Redige la procedure dans docs/ plutot que dans un script, tant que
les acces ne sont pas confirmes.
```

## 7. Fichiers à créer

| Chemin | Nature |
|---|---|
| `backend/<module>/Dockerfile` | Huit fichiers, un par module |
| `frontend/Dockerfile` | Image du frontend |
| `frontend/nginx.conf` ou équivalent | Configuration du serveur statique |
| `infra/docker/docker-compose.yml` | Extension aux services |
| `infra/docker/.env.example` | Variables documentées, sans valeur sensible |
| `docs/publication-images.md` | Procédure Harbor |

## 8. Commandes terminal

```bash
cd afb-rations/backend
mvn clean package -DskipTests

cd ../infra/docker
docker compose build
docker compose up -d
docker compose ps
```

Vérification des sondes de santé, service par service :

```bash
docker compose ps --format "table {{.Name}}\t{{.Status}}"
```

Vérification de l'utilisateur dans un conteneur :

```bash
docker exec -it rations-identite whoami
```

Attendu : un utilisateur non privilégié.

Recherche de secrets dans une image :

```bash
docker history rations-identite:latest --no-trunc | grep -i "password\|secret"
```

Attendu : aucun résultat.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `docker compose build` | Les neuf images construites |
| `docker compose up` | Tous les conteneurs démarrés et sains |
| Ordre de démarrage | Fondé sur les sondes, pas sur des temporisations |
| Utilisateur dans les conteneurs | Non privilégié |
| Secrets dans les images | Aucun |
| Parcours complet en conteneur | Fonctionnel |
| Taille des images | Raisonnable, grâce au multi-étapes |
| Frontend en conteneur | URL de la passerelle correctement résolue |

## 10. Points de vigilance

- **Aucun secret dans une image.** Une image publiée sur un registre est consultable par tous ceux qui y ont accès, et son historique conserve les couches intermédiaires. Un mot de passe inscrit puis supprimé reste visible.
- Les conteneurs ne tournent pas en utilisateur privilégié. C'est un standard de sécurité, et le vérifier prend une commande.
- L'ordre de démarrage se fonde sur les sondes de santé. Une temporisation fixe fonctionne sur le poste du développeur et échoue sur une machine plus lente ou plus chargée.
- L'URL de la passerelle est figée au build côté frontend. Sans mécanisme d'injection au démarrage, il faudrait une image par environnement, ce qui va contre l'idée même de conteneurisation.
- Les identifiants Harbor ne sont pas encore connus. Ne pas inventer d'adresse de registre : utiliser des valeurs de substitution explicites et les signaler.
- La construction multi-étapes n'est pas cosmétique : une image contenant Maven et les sources pèse plusieurs fois le poids nécessaire.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Neuf Dockerfiles, construction multi-étapes | Fait |
| Utilisateur non privilégié dans chaque image | Vérifié |
| Sonde de santé dans chaque image | Vérifié |
| Aucun secret dans les images | Vérifié |
| Composition démarrant l'ensemble | Vérifié |
| Ordre de démarrage fondé sur les sondes | Vérifié |
| Décision sur l'URL du frontend tranchée | Fait |
| Parcours complet fonctionnel en conteneur | Vérifié |
| Procédure de publication rédigée, valeurs de substitution signalées | Fait |

## 12. Commit

```bash
git add .
git commit -m "sprint-8.2: conteneurisation des services

- Neuf images en construction multi-etapes, utilisateur non privilegie
- Composition complete avec demarrage fonde sur les sondes de sante
- Aucun secret embarque dans les images
- Procedure de publication harbor documentee

Refs: document maitre sections 4 et 7.6"
```

---

**Fin du Sprint 8.2** — en attente de validation avant le Sprint 8.3
