# CLAUDE.md — Projet Paiement des Rations et du Transport de la Garde Armée

# AFRILAND HORIZON 2030 — Module INTRA

# À lire intégralement avant toute action de code.

---

## 1. CONTEXTE DU PROJET

Digitalisation du paiement des frais de ration et de transport des agents de la garde armée d'Afriland First Bank. Aujourd'hui le processus est manuel : saisie papier jour par jour, consolidation mensuelle à la main, validations par visas physiques, remontée hiérarchique selon le montant.

Le module dématérialise ce cycle, de la saisie journalière jusqu'à la mise à disposition de l'état validé pour la comptabilité. Il fait partie d'un programme plus large piloté par la Direction Financière et Trésorerie (dotations téléphoniques, solde de tout compte, prime de stage, primes de permanence, rations et transport garde armée).

Le module s'intègre au portail interne INTRA en application liée autonome, avec un realm Keycloak partagé.

---

## 2. STACK TECHNIQUE (décisions figées, ne pas dévier)

- **Architecture intérieure : microservices**, chaque service déployable et conteneurisé indépendamment. Pas de monolithe.
- **Backend :** Spring Boot 4.1.0, Java 21 (Temurin), Maven.
- **Frontend :** React 19 + TypeScript, Vite, Tailwind CSS, axios. Pas de JavaScript nu.
- **Base de données :** PostgreSQL 16, une base par service.
- **ORM :** Spring Data JPA / Hibernate.
- **Migrations :** Flyway, un jeu de migrations versionné par service.
- **Sécurité :** Spring Security + OAuth2 Resource Server, adossé à Keycloak. Flux Authorization Code + PKCE. Sessions stateless.
- **Passerelle :** API Gateway + service registry / configuration.
- **Messagerie :** Apache Kafka pour l'échange avec la comptabilité.
- **PDF :** iText 8. **Excel :** Apache POI.
- **Doc API :** Springdoc OpenAPI (swagger-ui par service, accès interne).
- **Conteneurisation :** Docker, registre privé Harbor, orchestration Kubernetes.

Ne jamais réintroduire : EHR, enrôlement, fonctions éligibles, mot de passe applicatif, troisième niveau de validation RH. Ces éléments viennent d'autres modules et ne concernent pas ce projet.

---

## 3. DÉCOUPAGE EN MICROSERVICES (6 services + passerelle + registre)

| Service | Base | Responsabilité |
| --- | --- | --- |
| Identité et habilitations | BD Identité | Projection locale des comptes annuaire, rôles, code unité, vérification des habilitations à partir du jeton. |
| Saisie | BD Saisie | Fiches journalières, lignes de prestation, contrôles de doublon RG-04 et RG-15. |
| Grilles tarifaires | BD Grilles | Cycle de vie des grilles, validation ARH puis DRH, résolution du montant. |
| Workflow et validation | BD Workflow | Processus mensuel, étapes, aiguillage au seuil, pièce jointe, clôture. |
| Reporting | (lecture) | Suivi des statuts, historique, export PDF et Excel. |
| Transmission comptable | Stockage / topics | Publie l'état validé et consomme l'accusé comptable via Kafka. |

Communication synchrone en REST via la passerelle. Communication asynchrone via Kafka uniquement pour l'échange comptable.

Structure interne de chaque service (dépendances vers l'intérieur, le domaine ne dépend d'aucune couche technique) :

```
service-xxx/
  api/            controllers REST + DTO
  application/    services applicatifs, orchestration
  domaine/        entités, règles de gestion
  infrastructure/ repositories JPA, clients REST, producteurs/consommateurs Kafka
```

---

## 4. MODÈLE DE DONNÉES : 10 TABLES

Conventions : PostgreSQL 16, `snake_case`, PK `id BIGINT` auto-incrémentée, dates en `TIMESTAMP`, montants en entier (FCFA).

- **utilisateurs** — projection locale du compte annuaire. `login` (prenom_nom), `sub_keycloak`, `role`, `code_unite`. AUCUN mot de passe.
- **beneficiaires** — agent servi. `nom`, `prenom`, `num_compte_courant`, `code_agence`. Créé au fil des saisies, pas d'enrôlement.
- **grille_tarifaire** — `nature`, `session`, `montant_fcfa`, `statut_validation`, `id_createur` (ARH), `id_validateur` (DRH). Une seule grille ACTIVE avec `date_fin NULL` par couple (nature, session).
- **processus_mensuel** — `mois_paiement`, `annee_paiement`, `code_unite`, `type_processus`, `id_processus_origine`, `motif_ouverture`, `montant_total`, `statut`, `transmis_comptabilite`. Un seul processus NORMAL par (code_unite, mois, année) ; plusieurs COMPLEMENTAIRE possibles.
- **fiche_journaliere** — `id_processus`, `date_jour`, `statut`. Unicité (id_processus, date_jour).
- **ligne_prestation** — `id_fiche_journaliere`, `id_beneficiaire`, `nature`, `session`, `montant_applique`. Montant figé à la saisie.
- **etape_workflow** — `id_processus`, `id_acteur`, `ordre_etape`, `nom_etape`, `statut_etape`, `motif_retour`, `signature_numerique`.
- **piece_jointe** — UN SEUL document par processus (`id_processus` unique), enrichi progressivement des signatures.
- **parametre_systeme** — `code`, `libelle`, `valeur`, `actif`. Porte le seuil d'aiguillage.
- **audit_log** — journal immuable. `id_utilisateur`, `action`, `entite_cible`, `id_entite`, `date_action`, `adresse_ip`, `detail_json`.

**CODE AGENCE ≠ CODE UNITE.** Même format (VARCHAR(5), référentiel des codes guichets Afriland), mais rôles distincts. `code_agence` = agence de domiciliation du compte du bénéficiaire (ligne de crédit). `code_unite` = unité qui supporte la charge (ligne de débit). Toujours les distinguer.

---

## 5. ÉNUMÉRATIONS (9)

```
RoleEnum          : AGENT_UNITE | CHEF_UNITE_DA | DIRECTEUR_RESEAU_DR | ARH | DRH | ADMIN
StatutEnum        : EN_COURS_SAISIE | SOUMIS | EN_ATTENTE_DA | EN_ATTENTE_DR | RETOURNE | CLOTURE
TypeProcessusEnum : NORMAL | COMPLEMENTAIRE
NatureEnum        : RATION | TRANSPORT
SessionEnum       : JOUR | SOIR
NomEtapeEnum      : SOUMISSION_AGENT | VALIDATION_DA | VALIDATION_DR
StatutEtapeEnum   : EN_ATTENTE | VALIDEE | RETOURNEE
StatutFicheEnum   : EN_SAISIE | ENREGISTREE
StatutGrilleEnum  : BROUILLON | EN_ATTENTE_DRH | ACTIVE | REJETEE
```

---

## 6. RÈGLES MÉTIER CRITIQUES (appliquer sans exception)

- **RG-01** Nature : chaque ligne est RATION ou TRANSPORT exclusivement.
- **RG-02** Session : chaque ligne est JOUR ou SOIR exclusivement.
- **RG-03** Montant automatique : repris de la grille ACTIVE (nature, session). Jamais saisi à la main.
- **RG-04** Unicité journalière : pas deux fois le même bénéficiaire pour la même journée (même nature, même session).
- **RG-05** Fiche vierge par jour : chaque nouveau jour ouvre une fiche réinitialisée.
- **RG-06** Consolidation mensuelle automatique par unité.
- **RG-07** Validation séquentielle : agent → DA → DR le cas échéant. Aucun saut de niveau.
- **RG-08** Seuil d'aiguillage : après validation DA, état ≤ 100 000 XAF clôturé et transmis ; > 100 000 XAF envoyé au DR. Seuil lu dans `parametre_systeme`, jamais codé en dur.
- **RG-09** Signature numérique horodatée à chaque validation, sur la pièce jointe unique.
- **RG-10** Motif obligatoire pour tout rejet ou retour.
- **RG-11** Retour toujours vers l'agent d'unité, quel que soit le niveau d'origine.
- **RG-12** Séparation des tâches : un même utilisateur ne cumule pas saisie et validation multi-niveau d'un même dossier, et ne choisit pas son N+1.
- **RG-13** Transmission unique : un état validé n'est transmis qu'une fois (`transmis_comptabilite`).
- **RG-14** Validation DRH des grilles : toute grille passe EN_ATTENTE_DRH, la DRH valide (ACTIVE, ancienne fermée) ou rejette avec motif. Une seule active par (nature, session).
- **RG-15** Unicité inter-états : aucune ligne ne peut reproduire une combinaison (bénéficiaire, journée, nature, session) déjà présente dans un autre état de la même unité et de la même période. Contrôle sur `processus_mensuel` + `fiche_journaliere` + `ligne_prestation`.

---

## 7. WORKFLOW ET AIGUILLAGE

```
Agent : saisie journalière → consolidation → soumission (signature agent)
      → Chef d'Unité (DA) : valide ou retourne (motif)
          si montant ≤ 100 000 XAF : CLOTURE + transmission comptable
          si montant > 100 000 XAF : → Directeur Réseau (DR) : valide ou retourne
              validation DR : CLOTURE + transmission comptable
```

Tout retour (DA ou DR) ramène l'état à l'agent (RETOURNE), jamais à un niveau intermédiaire. L'état CLOTURE est définitif.

**État complémentaire (régularisation).** Un bénéficiaire signale hors système un oubli sur un mois clôturé. L'agent ouvre un `processus_mensuel` de type COMPLEMENTAIRE qui référence l'état d'origine (jamais rouvert). À chaque ligne, RG-15 est vérifiée. Le circuit de validation est identique à un état normal. **Ce cas n'est dans aucun cahier des charges : à faire confirmer par le métier avant industrialisation.**

---

## 8. PÉRIMÈTRE DU MODULE

**Dans le périmètre :** saisie, consolidation, workflow, aiguillage, clôture, grilles, suivi, reporting, régularisation par état complémentaire.

**Hors périmètre :** la production des écritures comptables, l'impact CBS, la refonte du système comptable. Le module publie l'état validé et consomme l'accusé comptable, rien de plus.

Jamais dans ce module : génération d'écritures, schéma débit/crédit codifié (informatif seulement), utilisateur comptable, EHR, enrôlement.

---

## 9. ÉCHANGE COMPTABLE (Kafka, producteur ET consommateur)

- **Publie** sur `rations.etat.valide` à la clôture : période, code unité, type, montant total, et le détail des lignes (nom, prénom, compte courant, **code agence**, nature, session, montant).
- **Consomme** sur `rations.etat.accuse` l'accusé du module de comptabilisation : `statut_integration` (EN_ATTENTE | INTEGRE | REJETE), référence comptable, date. Met à jour le statut d'intégration du processus, remonté dans le suivi.

Le service Transmission n'expose pas d'endpoint de déclenchement : la transmission est déclenchée par le service Workflow à la clôture.

---

## 10. AUTHENTIFICATION KEYCLOAK

- Connexion = redirection du frontend vers Keycloak (Authorization Code + PKCE). Aucun service n'émet de jeton.
- Les services valident le jeton (OAuth2 Resource Server), en extraient l'identité (`sub_keycloak`) et le rôle.
- `utilisateurs` est une projection locale : `login`, `sub_keycloak`, `role`, `code_unite`. Le rôle applicatif et le code unité sont gérés localement, pas dans l'annuaire.
- **Ne jamais** ajouter de champ mot de passe, ni de route de login côté backend.
- En développement : realm de dev Keycloak sur localhost:8180 ; en production : realm AFB partagé.

---

## 11. CONTRATS API : 24 ENDPOINTS (préfixe passerelle /api)

- **Identité (3)** : `/identite/moi`, `/identite/utilisateurs`, `/identite/utilisateurs/{id}/role`.
- **Saisie (5)** : `/saisie/fiches`, `/saisie/fiches/{id}/lignes`, `/saisie/lignes` (POST/PUT/DELETE).
- **Grilles (5)** : `/grilles` (GET/POST), `/grilles/{id}/validation`, `/grilles/{id}/rejet`, `/grilles/active`.
- **Workflow (6)** : `/processus` (POST), `/processus/{id}`, `/processus/{id}/etat`, `/processus/{id}/soumission`, `/processus/{id}/validation`, `/processus/{id}/retour`.
- **Reporting (4)** : `/reporting/demandes`, `/reporting/processus/{id}/historique`, `/reporting/rapports`, `/reporting/rapports/export`.
- **Transmission (1)** : `/transmission/processus/{id}`.

JSON UTF-8, dates ISO 8601. Erreurs au format uniforme `{ timestamp, status, code, message, path }`. Codes usuels : 400, 401, 403, 404, 409 (doublon/unicité), 422 (règle de gestion).

---

## 12. CONVENTIONS DE CODE

- Java : packages `cm.afrilandfirstbank.rations.<service>.<couche>`, classes en PascalCase, méthodes en camelCase.
- SQL : `snake_case`, migrations Flyway `V<n>__description.sql` par service.
- REST : noms de ressources au pluriel, verbes HTTP standard, pas de verbe dans l'URL.
- DTO en entrée et en sortie, jamais d'entité JPA exposée directement.
- Requêtes paramétrées JPA uniquement, aucune concaténation de chaîne utilisateur.
- Validation des DTO entrants, erreurs explicites.
- Toute action sensible écrit dans `audit_log` (auteur, date, delta avant/après).
- Frontend : composants en PascalCase, hooks en `useXxx`, appels API centralisés (axios), typage strict (pas de `any`).
- Secrets hors du code : variables d'environnement (URL realm, DB, brokers Kafka, origines CORS). Fichiers d'environnement jamais versionnés.

---

## 13. RÉFÉRENTIEL DES CODES GUICHETS

`code_agence` et `code_unite` puisent dans le référentiel des codes guichets Afriland (VARCHAR(5)), maintenu hors du module. Exemples : `00001` Siège Yaoundé, `00002` Douala Bonanjo, `00003` Bafoussam, … `00050` Bandjoun. Le module le consomme comme domaine de valeurs.

---

## 14. ORDRE D'IMPLÉMENTATION SUGGÉRÉ

1. Service Identité + intégration Keycloak (socle d'authentification).
2. Service Grilles (prérequis du calcul des montants).
3. Service Saisie (fiches, lignes, RG-01 à RG-05, RG-04).
4. Service Workflow (soumission, validation, RG-07 à RG-13, aiguillage RG-08).
5. Service Transmission (Kafka publication + consommation).
6. Service Reporting (suivi, historique, exports).
7. État complémentaire (RG-15) une fois le besoin confirmé par le métier.
8. Passerelle + registre, conteneurisation, déploiement Kubernetes.

---

## 15. ERREURS À NE JAMAIS COMMETTRE

- Coder le seuil de 100 000 XAF en dur (toujours via `parametre_systeme`).
- Confondre ou fusionner `code_agence` et `code_unite`.
- Stocker un mot de passe ou créer une route de login backend.
- Générer des écritures comptables ou simuler un impact CBS dans ce module.
- Réintroduire l'enrôlement, l'EHR ou les fonctions éligibles.
- Laisser un montant saisi manuellement au lieu de la grille active.
- Permettre un retour vers un niveau intermédiaire au lieu de l'agent.
- Transmettre deux fois le même état à la comptabilité.
- Rouvrir un état clôturé pour une régularisation (créer un état COMPLEMENTAIRE à la place).
- Oublier le contrôle d'unicité inter-états (RG-15) à la saisie d'une ligne de régularisation.

---

## 16. POINTS À CONFIRMER AVEC LE MÉTIER

- Gestion de l'état complémentaire : fréquence réelle du besoin, délai pendant lequel une période close reste régularisable.
- Position de la comptabilité sur une seconde transmission portant sur une période déjà traitée.
- Namespace Kubernetes, conventions de nommage des déploiements et gestion des secrets (à arrêter avec la DSI).
