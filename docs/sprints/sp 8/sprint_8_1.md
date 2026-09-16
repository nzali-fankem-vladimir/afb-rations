# SPRINT 8.1

## Passerelle API et registre de services

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Mettre en service la passerelle et le registre, point d'entrée unique du module |
| **Livrable** | Routage vers les sept services, découverte, CORS centralisé, propagation du jeton |
| **Durée** | Une à deux journées |
| **Prérequis** | Sprint 7F.7 validé et commité |
| **Sprint suivant** | 8.2, conteneurisation |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Registre et découverte (étapes 2-3) | Sonnet | Moyen |
| Routage et sécurité de la passerelle (étapes 4-6) | Opus | Moyen |

**Changement manuel à l'étape 4.** Le routage touche à la propagation du jeton et au CORS : passer en Opus, effort moyen.

## 2. Outil de cartographie

```
py -3.14 -m graphify update .
```

## 3. Contexte

> **Ajustement du 16 septembre 2026 — le module compte SEPT services, pas six.** Ce guide a
> été écrit avant la construction du **service Audit** (port **8087**, base `rations_audit`),
> bâti hors séquence lors du sprint de rattrapage du 4 septembre 2026. **Aucun des trois guides
> du Sprint 8 ne le mentionnait.** Il doit être routé, enregistré, conteneurisé et déployé comme
> les autres — un module livré sans lui n'aurait plus de journal immuable, exigence du cahier
> des charges (CLAUDE.md §3).

Les sept services fonctionnent, mais chacun sur son port, et le frontend les appelle directement. Ce sous-sprint met en service ce qui avait été créé en squelette au Sprint 0.2 : la passerelle devient le point d'entrée unique, le registre assure la découverte.

Deux conséquences pratiques. Le frontend ne connaît plus qu'une seule adresse, celle de la passerelle. Et le CORS, jusqu'ici configuré service par service, se centralise : une configuration à deux endroits produirait des en-têtes en double et des rejets navigateur difficiles à diagnostiquer.

Le préfixe `/api` du contrat d'API prend ici tout son sens : c'est le préfixe porté par la passerelle.

## 4. Objectifs

- Registre de services opérationnel, les sept services s'y enregistrant
- Passerelle routant vers chaque service selon le chemin du contrat d'API
- CORS centralisé sur la passerelle, retiré des services
- Propagation du jeton vers les services, sans altération
- Frontend reconfiguré pour n'appeler que la passerelle

## 5. Règles concernées

Aucune règle métier. Deux contraintes du document maître :

- Section 3 : la passerelle est le point d'entrée unique, les services ne sont pas exposés directement.
- Section 7.6 : aucune adresse ni aucun secret en dur, tout passe par des variables d'environnement.

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis CLAUDE.md, sections 3 et 11. Confirme en 3 lignes le
role de la passerelle et les prefixes des sept services.

CONTEXTE DE CETTE SESSION : Sprint 8.1, passerelle et registre. Les
modules gateway et registry existent en squelette depuis le Sprint
0.2 : on les met en service.
SERVICE CONCERNE : gateway et registry, plus la configuration des sept
services.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Aucune adresse ni port en dur : variables d'environnement.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : propose la table de routage de la passerelle. Pour
chaque prefixe du contrat d'api, le service cible. Verifie que tu
couvres bien les sept services et que tu n'inventes aucun chemin.
Montre-la moi avant toute configuration.

N'OUBLIE PAS LE SERVICE AUDIT (8087), construit apres la redaction de
ce guide : il expose deux endpoints du contrat, GET /audit/entrees et
GET /audit/processus/{id}, reserves a ARH, DRH et ADMIN.

ATTENTION a une exception : GET /parametres/fonctionnalites (service
Workflow) n'est PAS dans le contrat des 26 endpoints, mais c'est le
FRONTEND qui l'appelle, pour savoir si la regularisation est ouverte
(Sprint 7F.7). Il doit donc etre ROUTE. Le classer "interne" parce
qu'il est hors contrat masquerait la regularisation a tous les agents,
sans aucune erreur visible.
```

### Étape 2. Registre de services

```
Mets en service le registre :

- Configuration du serveur d'enregistrement dans le module registry.
- Enregistrement de chaque service au demarrage, avec son nom
  logique.

Le nom logique de chaque service sera utilise par la passerelle pour
le router : arrete-le maintenant et documente-le.

Question a trancher : le registre est-il indispensable en
developpement local, ou un routage par adresse fixe suffit-il ?
Presente les consequences des deux, notamment sur la complexite de
demarrage sur un poste a memoire limitee.

Montre la configuration.
```

### Étape 3. Vérification de la découverte

```bash
cd afb-rations/backend
mvn -pl registry spring-boot:run
```

Démarrer ensuite chaque service et vérifier qu'il apparaît dans le registre.

### Étape 4. Routage de la passerelle

**Étape en Opus, effort moyen.**

```
Configure le routage de la passerelle, selon la table validee a
l'etape 1.

Chaque route associe un prefixe du contrat d'api au service cible,
resolu par le registre ou par adresse fixe selon la decision de
l'etape 2.

Point d'attention : les endpoints internes, comme la consolidation du
service Saisie ou le declenchement de transmission, ne doivent PAS
etre exposes par la passerelle. Ils sont appeles de service a
service, pas depuis l'exterieur.

Liste-moi les endpoints que tu classes comme internes avant de
configurer les routes, que je confirme.

Point de depart, releve dans les controleurs le 16 septembre 2026 et a
reverifier (CLAUDE.md §11) :
- GET  /identite/habilitation
- GET  /identite/utilisateurs/libelles
- GET  /saisie/processus/{id}/etat
- GET  /saisie/processus/recherche
- GET  /processus/recherche
- GET  /processus/{id}/historique
- PUT  /processus/{id}/integration   (secret partage X-Cle-Interne)
- GET  /processus/{id}/integration
- PUT  /processus/{id}/transmission  (verrou de RG-13)
- POST /transmission/processus/{id}

ATTENTION AU PIEGE DES VERBES : /processus/{id}/integration porte un
PUT interne et un GET interne ; /transmission/processus/{id} porte un
POST INTERNE et un GET DU CONTRAT, ouvert au frontend. Une regle de
routage ecrite sur le chemin sans le verbe exposerait le POST ou
bloquerait le GET. Meme lecon qu'au Sprint 5.3 pour le securityMatcher.

Rappel : GET /parametres/fonctionnalites est hors contrat MAIS doit
etre route (voir la premiere action).

Montre la configuration.
```

### Étape 5. CORS centralisé

```
Centralise la configuration CORS sur la passerelle :

1. Configure les origines autorisees sur la passerelle, lues depuis
   une variable d'environnement.
2. Retire la configuration CORS de chacun des sept services.

Une configuration a deux endroits produit des en-tetes en double et
des rejets navigateur difficiles a diagnostiquer. Verifie qu'aucun
service ne conserve la sienne.

Montre les modifications.
```

### Étape 6. Propagation du jeton

```
Verifie que la passerelle transmet l'en-tete d'autorisation aux
services sans l'alterer.

Les services restent des Resource Server : ils valident le jeton
eux-memes. La passerelle ne le valide pas a leur place et ne le
remplace pas.

Question a trancher : la passerelle doit-elle rejeter une requete
sans jeton avant de router, ou laisser le service repondre 401 ?
Rejeter tot economise un appel, mais deplace une responsabilite de
securite vers la passerelle. Presente les deux, j'arbitre.

Montre la configuration.
```

### Étape 7. Reconfiguration du frontend

```
Reconfigure le frontend pour n'appeler que la passerelle :

L'url de base du client axios pointe desormais vers la passerelle,
avec le prefixe /api. Aucune adresse de service individuel ne doit
subsister dans le code frontend.

Verifie ensuite le parcours complet a l'ecran : connexion, saisie,
validation, suivi.

Montre les modifications.
```

## 7. Fichiers à créer ou modifier

| Chemin | Nature |
|---|---|
| `backend/registry/src/main/resources/application.yml` | Configuration du registre |
| `backend/gateway/src/main/resources/application.yml` | Table de routage, CORS |
| `backend/*/src/main/resources/application.yml` | Enregistrement, retrait du CORS |
| `frontend/.env.example` | URL de la passerelle |
| `docs/routage-passerelle.md` | Table de routage et endpoints internes |

## 8. Commandes terminal

```bash
cd afb-rations/backend

mvn -pl registry spring-boot:run
mvn -pl gateway spring-boot:run
```

Vérification du routage à travers la passerelle :

```bash
curl -H "Authorization: Bearer <jeton>" http://localhost:8080/api/identite/moi
curl -H "Authorization: Bearer <jeton>" http://localhost:8080/api/grilles
curl -H "Authorization: Bearer <jeton>" "http://localhost:8080/api/reporting/demandes"
```

Vérification qu'un endpoint interne n'est pas exposé :

```bash
curl -i -H "Authorization: Bearer <jeton>" \
  http://localhost:8080/api/saisie/processus/1/etat
```

Attendu : non routé.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| Les sept services enregistrés, **service Audit compris** | Visibles dans le registre |
| `GET /parametres/fonctionnalites` routé malgré son absence du contrat | Vérifié |
| Routes internes et routes du contrat distinguées **par verbe** | Vérifié |
| Routage vers chaque service | Réponse correcte via la passerelle |
| Endpoints internes | Non exposés par la passerelle |
| CORS configuré uniquement sur la passerelle | Vérifié |
| Aucun en-tête CORS en double | Vérifié dans le navigateur |
| Jeton transmis sans altération | Services répondant normalement |
| Requête sans jeton | Comportement conforme à la décision |
| Frontend n'appelant que la passerelle | Aucune adresse de service dans le code |
| Parcours complet à l'écran | Fonctionnel |

## 10. Points de vigilance

- **Le CORS ne se configure qu'à un seul endroit.** Une configuration résiduelle sur un service produit des en-têtes en double, que le navigateur rejette avec un message peu explicite. C'est une perte de temps classique.
- Les endpoints internes ne doivent pas être exposés. La consolidation du service Saisie ou le déclenchement de transmission, accessibles depuis l'extérieur, permettraient de contourner le circuit de validation.
- La passerelle ne valide pas le jeton à la place des services. Ceux-ci restent des Resource Server : si la passerelle devenait le seul point de contrôle, un accès direct à un service contournerait toute la sécurité.
- Aucune adresse de service ne doit subsister dans le frontend. Un appel direct fonctionnerait en développement et échouerait en production, où seuls la passerelle est exposée.
- Le registre ajoute une brique à démarrer. Sur un poste à mémoire limitée, la décision de l'étape 2 a des conséquences pratiques quotidiennes.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Table de routage validée avant configuration | Fait |
| Décision sur l'usage du registre en local tranchée | Fait |
| Six services routés correctement | Vérifié |
| Endpoints internes identifiés et non exposés | Vérifié |
| CORS centralisé, retiré des services | Vérifié |
| Propagation du jeton sans altération | Vérifié |
| Décision sur le rejet précoce tranchée | Fait |
| Frontend appelant uniquement la passerelle | Vérifié |
| Parcours complet fonctionnel via la passerelle | Vérifié |

## 12. Commit

```bash
git add .
git commit -m "sprint-8.1: passerelle et registre de services

- Routage des sept services derriere un point d'entree unique
- Endpoints internes non exposes a l'exterieur
- Cors centralise sur la passerelle et retire des services
- Frontend reconfigure sur la passerelle

Refs: document maitre sections 3 et 11"
```

---

**Fin du Sprint 8.1** — en attente de validation avant le Sprint 8.2
