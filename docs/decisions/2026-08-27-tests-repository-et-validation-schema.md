# Tests de repository et validation de schéma (Spring Boot 4)

**Date :** 27 août 2026
**Sprint :** 2.1, domaine Grille tarifaire
**Statut :** tranchée — s'applique à tous les services suivants

## Contexte

Le Sprint 2.1 introduit le premier test de repository du projet
(`GrilleTarifaireRepositoryTest`). Deux points découverts à cette occasion
concernent tous les services qui écriront des tests JPA (Saisie au 3.x,
Workflow au 4.x, Reporting au 6.x, Audit).

## Décisions

### 1. `@DataJpaTest` tourne contre la vraie base PostgreSQL, pas une base embarquée

`@DataJpaTest` + `@AutoConfigureTestDatabase(replace = Replace.NONE)`, en
s'appuyant sur la `datasource` du profil `dev` (conteneur Docker du Sprint
0.5) et sur Flyway pour créer le schéma.

**Motif :** les migrations sont spécifiques à PostgreSQL (index partiel
`WHERE ...`, `date_trunc`). Une base H2/HSQLDB embarquée les ferait échouer.
Les tests de données de référence portent d'ailleurs sur le jeu inséré par
les migrations `V2` de chaque service.

**Conséquence :** ces tests exigent les conteneurs `rations-postgres` (et,
selon le service, `rations-kafka`) démarrés. Un run de test peut appliquer une
nouvelle migration à la base de dev — c'est voulu.

### 2. Découpage des starters de test en Spring Boot 4

Les tranches de test ne sont plus toutes dans `spring-boot-starter-test`.
Pour `@DataJpaTest`, ajouter :

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa-test</artifactId>
    <scope>test</scope>
</dependency>
```

Et les imports ont changé de package :

| Annotation | Package Boot 4 |
|---|---|
| `@DataJpaTest` | `org.springframework.boot.data.jpa.test.autoconfigure` |
| `@AutoConfigureTestDatabase` | `org.springframework.boot.jdbc.test.autoconfigure` |
| `@WebMvcTest` | `org.springframework.boot.webmvc.test.autoconfigure` (déjà utilisé par service-identite via `spring-boot-starter-webmvc-test`) |

### 3. `spring.jpa.hibernate.ddl-auto: validate` par service

Posé dans `application-dev.yml` de service-grilles. Hibernate vérifie au
démarrage que chaque entité correspond à une table produite par Flyway ;
il ne génère ni ne modifie jamais le schéma. À reprendre dans chaque
service portant des entités JPA.

## Lien

Voir aussi
`docs/decisions/2026-08-27-partage-enumerations-nature-session.md`
(duplication assumée de `NatureEnum` / `SessionEnum`, entités du Sprint 3.1).
