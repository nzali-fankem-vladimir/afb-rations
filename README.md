# afb-rations

Digitalisation du processus de paiement des frais de ration et de transport des agents de la garde armée d'Afriland First Bank. Le module dématérialise la saisie journalière, la consolidation mensuelle, le circuit de validation avec aiguillage selon le montant, la clôture, ainsi que la publication de l'état validé vers la comptabilité.

Module intégré au portail interne INTRA, adossé à un realm Keycloak partagé.

## Arborescence

```
afb-rations/
├── backend/
│   ├── gateway/              passerelle API
│   ├── registry/             registre de services
│   ├── service-identite/     identite et habilitations
│   ├── service-saisie/       fiches journalieres, lignes de prestation
│   ├── service-grilles/      grilles tarifaires
│   ├── service-workflow/     processus mensuel, validation, aiguillage
│   ├── service-reporting/    suivi, historique, exports
│   ├── service-transmission/ publication/consommation Kafka vers la comptabilite
│   └── pom.xml                POM parent, gestion des versions
├── frontend/                  React 19 + TypeScript
├── infra/
│   ├── docker/                composition locale
│   └── k8s/                   manifests de deploiement
├── docs/
├── CLAUDE.md
├── README.md
└── .gitignore
```

## Services, bases et ports

| Service | Dossier | Base de données | Port |
| --- | --- | --- | --- |
| Passerelle API | `gateway` | — | 8080 |
| Registre de services | `registry` | — | 8761 |
| Identité et habilitations | `service-identite` | `db_identite` | 8081 |
| Saisie | `service-saisie` | `db_saisie` | 8082 |
| Grilles tarifaires | `service-grilles` | `db_grilles` | 8083 |
| Workflow et validation | `service-workflow` | `db_workflow` | 8084 |
| Reporting | `service-reporting` | (lecture, pas de base propre) | 8085 |
| Transmission comptable | `service-transmission` | (pas de base relationnelle propre, stockage/topics) | 8086 |
| Frontend | `frontend` | — | 5173 |

Base de données : PostgreSQL 16, port 5432, une base par service.

## Conventions de nommage

- **Packages Java** : `cm.afrilandfirstbank.rations.<service>.<couche>`
- **Couches par service** : `api`, `application`, `domaine`, `infrastructure`. Les dépendances vont vers l'intérieur, le domaine ne dépend d'aucune couche technique.
- **SQL** : `snake_case`. Migrations Flyway nommées `V<n>__description.sql`, un jeu par service.
- **REST** : ressources au pluriel, verbes HTTP standard, aucun verbe dans l'URL.
- **DTO** : en entrée et en sortie. Une entité JPA n'est jamais exposée en API.
- **Frontend** : composants en PascalCase, hooks en `useXxx`, appels API centralisés (axios), typage strict sans `any`.

## Authentification

L'authentification est déléguée à Keycloak, adossé à l'annuaire de la banque. **Le module ne stocke aucun mot de passe et n'expose aucune route de login** : les services valident le jeton, en extraient l'identité et le rôle (CLAUDE.md section 10).

Le frontend se connecte par redirection, en Authorization Code avec PKCE S256, via un client public. Le rôle applicatif et le code unité ne viennent pas de l'annuaire : ils sont portés par la table `utilisateurs` du service Identité, alimentée par un administrateur.

Un compte annuaire valide sans profil local ouvert est refusé en 403. Voir [docs/decisions/2026-08-25-resolution-du-profil-local.md](docs/decisions/2026-08-25-resolution-du-profil-local.md).

Le realm de développement, ses six rôles et ses comptes de test sont versionnés dans [infra/keycloak/](infra/keycloak/), avec le script de recréation.

## Démarrage complet avec Docker (recommandé)

Le module démarre en entier depuis une seule commande, **sans rien installer ni configurer d'autre** : PostgreSQL, Kafka (et ses topics), Keycloak (realm importé), les sept services, la passerelle, le registre et le frontend sont dans la composition.

Prérequis : Docker Desktop (Compose v2), au moins **8 Go de mémoire alloués à Docker**, et les ports 8080, 5173, 5433, 9092 et 8180 libres.

```bash
cd infra/docker
docker compose up -d --build     # première fois : construction des 10 images (environ 20 minutes)
docker compose ps                # tout doit passer à "healthy" (compter 5 à 8 minutes à froid)
```

Puis ouvrir http://localhost:5173 et se connecter avec un compte de test, par exemple `jean_mbarga` / `Rations2026` (agent d'unité). Les autres comptes et leurs rôles sont dans [infra/keycloak/README.md](infra/keycloak/README.md).

- **Port 8180 déjà pris** (un autre Keycloak sur le poste) : créer `infra/docker/.env` contenant `KEYCLOAK_PORT=8181`. Tout le reste s'en déduit. Les autres variables sont documentées dans [infra/docker/.env.example](infra/docker/.env.example) ; aucune n'est obligatoire.
- **Arrêter** : `docker compose down`. Les données sont conservées. **Ne jamais ajouter `-v`** : cela supprime les bases, les topics Kafka et les documents signés.
- **Documentation d'API** de chaque service : `http://localhost:8081/swagger-ui.html` (8082 à 8087 pour les autres), accessible depuis le poste seulement.
- Les images et leur publication sur le registre Harbor : [docs/publication-images.md](docs/publication-images.md).

## Développement depuis l'IDE (sans Docker)

Prérequis : PostgreSQL 16 sur 5432, Keycloak sur 8180 avec le realm `afb-rations-dev`.

```bash
# Realm de developpement (a la premiere installation, ou apres suppression du conteneur)
cd infra/keycloak && ./init-realm.ps1

# Service Identite
cd backend && mvn -pl service-identite spring-boot:run     # http://localhost:8081

# Frontend
cd frontend && cp .env.example .env && npm install && npm run dev   # http://localhost:5173
```

Vérification rapide du refus sans jeton :

```bash
curl -i http://localhost:8081/identite/moi     # attendu : 401
```

## Variables d'environnement

Aucun secret dans le code. Les valeurs de repli des fichiers de développement sont des garde-fous documentés, pas des secrets.

| Variable | Rôle | Repli en développement |
| --- | --- | --- |
| `KEYCLOAK_ISSUER_URI` | Realm émetteur des jetons | `http://localhost:8180/realms/afb-rations-dev` |
| `KEYCLOAK_AUDIENCE` | Destinataire attendu du jeton | `rations-api` |
| `CORS_ALLOWED_ORIGINS` | Origine du frontend | `http://localhost:5173` |
| `DB_<SERVICE>_URL`, `_USERNAME`, `_PASSWORD` | Connexion à la base du service | voir `application-dev.yml` |

L'URL du realm de production reste un point en attente DSI.
