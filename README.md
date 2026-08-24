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
| Frontend | `frontend` | — | 3000 |

Base de données : PostgreSQL 16, port 5432, une base par service.

## Conventions de nommage

- **Packages Java** : `cm.afrilandfirstbank.rations.<service>.<couche>`
- **Couches par service** : `api`, `application`, `domaine`, `infrastructure`. Les dépendances vont vers l'intérieur, le domaine ne dépend d'aucune couche technique.
- **SQL** : `snake_case`. Migrations Flyway nommées `V<n>__description.sql`, un jeu par service.
- **REST** : ressources au pluriel, verbes HTTP standard, aucun verbe dans l'URL.
- **DTO** : en entrée et en sortie. Une entité JPA n'est jamais exposée en API.
- **Frontend** : composants en PascalCase, hooks en `useXxx`, appels API centralisés (axios), typage strict sans `any`.

## Commandes de démarrage

À compléter aux sous-sprints suivants (0.2 initialisation backend, 0.3 initialisation frontend).
