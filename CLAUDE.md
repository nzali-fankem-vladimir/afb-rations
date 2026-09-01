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
- **Passerelle :** Spring Cloud Gateway. **Registre :** Eureka. Spring Cloud 2025.1.2.
- **Messagerie :** Apache Kafka, pour l'échange avec la comptabilité et pour le journal d'audit.
- **PDF :** iText 8 (artefacts `kernel` et `layout`). **Excel :** Apache POI.
- **Doc API :** Springdoc OpenAPI (swagger-ui par service, accès interne).
- **Conteneurisation :** Docker, registre privé Harbor, orchestration Kubernetes.

Ne jamais réintroduire : EHR, enrôlement, fonctions éligibles, mot de passe applicatif, troisième niveau de validation RH. Ces éléments viennent d'autres modules et ne concernent pas ce projet.

---

## 3. DÉCOUPAGE EN MICROSERVICES (7 services + passerelle + registre)

| Service | Base | Port | Responsabilité |
| --- | --- | --- | --- |
| Identité et habilitations | BD Identité | 8081 | Projection locale des comptes annuaire, rôles, code unité, vérification des habilitations à partir du jeton. |
| Saisie | BD Saisie | 8082 | Fiches journalières, lignes de prestation, contrôles de doublon RG-04 et RG-15. |
| Grilles tarifaires | BD Grilles | 8083 | Cycle de vie des grilles, validation ARH puis DRH, résolution du montant. |
| Workflow et validation | BD Workflow | 8084 | Processus mensuel, étapes, aiguillage au seuil, pièce jointe, clôture. |
| Reporting | (lecture) | 8085 | Suivi des statuts, historique, export PDF et Excel. |
| Transmission comptable | (topics Kafka) | 8086 | Publie l'état validé et consomme l'accusé comptable. |
| **Audit** | **BD Audit** | **8087** | **Journal immuable de toutes les actions du module. Consomme les événements d'audit émis par les six autres services.** |

Passerelle 8080, registre 8761, frontend 5173 (port figé dans les URI de redirection du realm Keycloak, écart au port 3000 initialement prévu au Sprint 0.3 — voir section 17).

Communication synchrone en REST via la passerelle. Communication asynchrone via Kafka pour l'échange comptable et pour l'audit.

**Pourquoi un service Audit dédié.** Le cahier des charges exige un journal immuable. Un journal réparti en une table par service laisserait chaque service maître de ses propres traces, avec les pleins droits dessus. Un service dédié rend la base d'audit inaccessible en modification depuis les services métier : l'immuabilité devient une propriété de l'architecture, pas une discipline de code. C'est un argument de contrôle interne autant que d'architecture.

**Une bibliothèque partagée, pas un huitième service.** Le module Maven `rations-audit-commun` (Sprint 1.3) porte la publication d'audit reprise par les six services métier. Ce n'est pas un service : il n'a ni base, ni port, ni conteneur, et ne se déploie pas. C'est la seule mutualisation de code du backend, son périmètre est vérifié au build, et l'ajouter à un service ne crée aucune dépendance d'exécution vers un autre service. Voir `docs/publication-audit.md`.

Structure interne de chaque service (dépendances vers l'intérieur, le domaine ne dépend d'aucune couche technique) :

```
service-xxx/
  api/            controllers REST + DTO
  application/    services applicatifs, orchestration
  domaine/        entités, règles de gestion
  infrastructure/ repositories JPA, clients REST, producteurs/consommateurs Kafka
                  config/ SecurityConfig par service
```

---

## 4. MODÈLE DE DONNÉES : 10 TABLES

Conventions : PostgreSQL 16, `snake_case`, PK `id BIGINT` auto-incrémentée, dates en `TIMESTAMP`, montants en entier (FCFA).

Répartition par base :

| Base | Tables |
| --- | --- |
| `rations_identite` | `utilisateurs` |
| `rations_saisie` | `beneficiaires`, `fiche_journaliere`, `ligne_prestation` |
| `rations_grilles` | `grille_tarifaire` |
| `rations_workflow` | `processus_mensuel`, `etape_workflow`, `piece_jointe`, `parametre_systeme` |
| `rations_audit` | `audit_log` |

- **utilisateurs** — projection locale du compte annuaire. `login` (prenom_nom), `sub_keycloak`, `role`, `code_unite`. Complétée en pratique de `matricule`, `email`, `actif`, `date_dernier_acces` (décision Sprint 0.7, section 17). AUCUN mot de passe.
- **beneficiaires** — agent servi. `nom`, `prenom`, `num_compte_courant`, `code_agence`. Créé au fil des saisies, pas d'enrôlement.
- **grille_tarifaire** — `nature`, `session`, `montant_fcfa`, `statut_validation`, `id_createur` (ARH), `id_validateur` (DRH). Une seule grille ACTIVE avec `date_fin NULL` par couple (nature, session).
- **processus_mensuel** — `mois_paiement`, `annee_paiement`, `code_unite`, `type_processus`, `id_processus_origine`, `motif_ouverture`, `montant_total`, `statut`, `transmis_comptabilite`. Un seul processus NORMAL par (code_unite, mois, année) ; plusieurs COMPLEMENTAIRE possibles.
- **fiche_journaliere** — `id_processus`, `date_jour`, `statut`. Unicité (id_processus, date_jour).
- **ligne_prestation** — `id_fiche_journaliere`, `id_beneficiaire`, `nature`, `session`, `montant_applique`. Montant figé à la saisie.
- **etape_workflow** — `id_processus`, `id_acteur`, `ordre_etape`, `nom_etape`, `statut_etape`, `motif_retour`, `signature_numerique`.
- **piece_jointe** — UN SEUL document par processus (`id_processus` unique), enrichi progressivement des signatures. Complétée de `nombre_signatures` (migration V3, Sprint 4.2) : il compte les signatures **réellement écrites dans le fichier PDF**, et n'est incrémenté qu'**après confirmation d'écriture disque** (fsync + renommage atomique), jamais dans la même transaction que `etape_workflow` — sinon il ne mesurerait que le circuit, que `etape_workflow` décrit déjà.
- **parametre_systeme** — `code`, `libelle`, `valeur`, `actif`. Porte le seuil d'aiguillage et les drapeaux de fonctionnalité.
- **audit_log** — journal immuable, **base `rations_audit`, service Audit**. `id_utilisateur`, `service_emetteur`, `action`, `entite_cible`, `id_entite`, `date_action`, `adresse_ip`, `detail_json`. Aucune méthode de modification ni de suppression n'est exposée, y compris celles héritées par défaut du repository.

**Convention transverse.** Une colonne `date_creation` (`TIMESTAMP`, défaut `CURRENT_TIMESTAMP`) est ajoutée à chaque table métier pour l'audit technique, sauf `parametre_systeme` (sans horodatage propre) et `audit_log` (porte `date_action`). Décision Sprint 0.7, section 17.

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

**État complémentaire (régularisation).** Un bénéficiaire signale hors système un oubli sur un mois clôturé. L'agent ouvre un `processus_mensuel` de type COMPLEMENTAIRE qui référence l'état d'origine (jamais rouvert). À chaque ligne, RG-15 est vérifiée. Le circuit de validation est identique à un état normal. **Fonctionnalité livrée mais fermée par le drapeau `RATTRAPAGE_ACTIF` dans `parametre_systeme`, tant que le métier n'a pas confirmé.**

---

## 8. PÉRIMÈTRE DU MODULE

**Dans le périmètre :** saisie, consolidation, workflow, aiguillage, clôture, grilles, suivi, reporting, journal d'audit, régularisation par état complémentaire.

**Hors périmètre :** la production des écritures comptables, l'impact CBS, la refonte du système comptable. Le module publie l'état validé et consomme l'accusé comptable, rien de plus.

Jamais dans ce module : génération d'écritures, schéma débit/crédit codifié (informatif seulement), utilisateur comptable, EHR, enrôlement.

---

## 9. ÉCHANGES KAFKA (trois topics)

Noms de développement (Sprint 0.5) : `rations.etat.valide`, `rations.etat.accuse`, `rations.audit.evenement`. Nommage définitif en environnement partagé non arrêté (point DSI D-07, voir `docs/points-en-attente.md`) : ne pas figer ces noms ailleurs que dans `infra/docker/kafka-topics.sh`.

### 9.1 Échange comptable (producteur ET consommateur)

- **Publie** sur `rations.etat.valide` à la clôture : période, code unité, type, montant total, et le détail des lignes (nom, prénom, compte courant, **code agence**, nature, session, montant).
- **Consomme** sur `rations.etat.accuse` l'accusé du module de comptabilisation : `statut_integration` (EN_ATTENTE | INTEGRE | REJETE), référence comptable, date. Met à jour le statut d'intégration du processus, remonté dans le suivi.

Le service Transmission n'expose pas d'endpoint de déclenchement : la transmission est déclenchée par le service Workflow à la clôture.

### 9.2 Journal d'audit (six producteurs, un consommateur)

- **Les six services métier publient** sur `rations.audit.evenement` après chaque action significative : identifiant utilisateur, service émetteur, action, entité ciblée, identifiant d'entité, horodatage, adresse IP, delta avant/après.
- **Le service Audit consomme** ce topic et écrit dans `audit_log`.

**Le producteur est mutualisé, pas réécrit par service.** Le module Maven `rations-audit-commun` (décision Sprint 1.3) porte la charge de l'événement, le port `PublicateurAudit` et le producteur Kafka. Les six services métier en dépendent ; **le service Audit n'en dépend pas** (il consomme le topic en *tolerant reader*, avec son propre type, pour que le schéma puisse évoluer sans pas cadencé à sept services). Ne jamais réécrire un producteur d'audit local, ni surcharger les réglages du producteur du module — `max.block.ms` en tête : ce sont eux qui garantissent que l'audit ne fait jamais échouer le métier.

**Tout service consommateur de `GET /identite/habilitation` doit publier un événement `ACCES_REFUSE`** quand le verdict est négatif, et quand le service Identité est injoignable. Le service Identité ne trace pas ces refus : il répond à une question, il ne refuse pas l'action — c'est le consommateur qui refuse. **Si le consommateur ne publie pas, le refus n'est tracé nulle part**, et l'exigence CT-04 n'est pas tenue, en silence. Voir `docs/appel-habilitation.md`.

**L'écriture d'audit est asynchrone par construction.** Un service métier publie et poursuit son traitement : il n'attend aucune réponse et ne dépend pas de la disponibilité du service Audit. C'est ce qui garantit le principe selon lequel l'audit ne fait jamais échouer une opération métier. Un appel REST synchrone vers le service Audit est **interdit** : il réintroduirait exactement la dépendance que ce choix élimine.

---

## 10. AUTHENTIFICATION KEYCLOAK

- Connexion = redirection du frontend vers Keycloak (Authorization Code + PKCE). Aucun service n'émet de jeton.
- Les services valident le jeton (OAuth2 Resource Server), en extraient l'identité (`sub_keycloak`) et le rôle.
- `utilisateurs` est une projection locale : `login`, `sub_keycloak`, `role`, `code_unite`. Le rôle applicatif et le code unité sont gérés localement, pas dans l'annuaire.
- **Création du profil local (décision Sprint 0.4) : pré-provisionnement puis liaison automatique.** L'administrateur ouvre le profil (`login`, `role`, `code_unite`) sans connaître le `sub_keycloak`. À la première connexion, le service Identité rapproche le profil par le `login` et y inscrit le `sub` ; les connexions suivantes passent directement par le `sub`. Un jeton valide sans profil local correspondant est refusé (403) : l'habilitation au module reste un acte d'administration explicite, elle ne découle pas de la seule existence d'un compte à l'annuaire.
- **Ne jamais** ajouter de champ mot de passe, ni de route de login côté backend.
- En développement : realm de dev Keycloak sur localhost:8180 ; en production : realm AFB partagé.
- Chaque service porte une `SecurityConfig` dans `infrastructure/config`. La sonde `/actuator/health` est la seule route métier publique.
- **Documentation Springdoc** (`/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs`, `/v3/api-docs/**`) : « accès interne » (section 2) signifie exposée sans jeton, mais réservée au réseau interne — distinct des routes métier, qui restent toutes protégées par OAuth2. Chaque `SecurityConfig` doit donc les déclarer `permitAll()` en `GET`, à côté de la sonde de santé, pour rester consultable directement au navigateur.

---

## 11. CONTRATS API : 26 ENDPOINTS (préfixe passerelle /api)

- **Identité (3)** : `/identite/moi`, `/identite/utilisateurs`, `/identite/utilisateurs/{id}/role`.
  - Plus **un endpoint interne hors contrat passerelle** : `GET /identite/habilitation?codeUnite={code}` (décision Sprint 1.3). Il n'est pas destiné au frontend : il répond à la question « cet utilisateur peut-il agir sur un dossier de ce code unité ? » posée par un autre service. Le compte de trois endpoints reste celui du contrat exposé par la passerelle. Voir `docs/appel-habilitation.md`.
- **Saisie (5)** : `/saisie/fiches`, `/saisie/fiches/{id}/lignes`, `/saisie/lignes` (POST/PUT/DELETE).
  - Plus **un endpoint interne hors contrat passerelle** : `GET /saisie/processus/{id}/etat?codeUnite={code}` (décision Sprint 3.4). Il n'est pas destiné au frontend : il rend l'état mensuel consolidé au service Workflow, qui le reprend pour servir le `GET /processus/{id}/etat` du contrat. Même statut que `GET /identite/habilitation` : le compte de cinq endpoints reste celui du contrat exposé par la passerelle. Voir `docs/appel-consolidation.md`.
- **Grilles (5)** : `/grilles` (GET/POST), `/grilles/{id}/validation`, `/grilles/{id}/rejet`, `/grilles/active`. Les quatre premiers sont livres (Sprints 2.2 et 2.3) ; `/grilles/active` releve du Sprint 2.4.
- **Workflow (6)** : `/processus` (POST), `/processus/{id}`, `/processus/{id}/etat`, `/processus/{id}/soumission`, `/processus/{id}/validation`, `/processus/{id}/retour`. **Les quatre premiers sont livrés** (Sprints 4.1 et 4.2) ; les deux derniers relèvent des Sprints 4.3 et 4.4. Codes d'erreur ajoutés au contrat : `PROCESSUS_EXISTANT` (409) et `FONCTIONNALITE_NON_OUVERTE` (422) au Sprint 4.1 ; `ETAT_INCOMPLET` (422), `PIECE_JOINTE_EXISTANTE` (409) et `DOCUMENT_NON_PRODUIT` (500) au Sprint 4.2.
- **Reporting (4)** : `/reporting/demandes`, `/reporting/processus/{id}/historique`, `/reporting/rapports`, `/reporting/rapports/export`.
- **Transmission (1)** : `/transmission/processus/{id}`.
- **Audit (2)** : `/audit/entrees` (recherche filtrable), `/audit/processus/{id}` (journal d'un processus). **Lecture seule, aucun endpoint d'écriture : l'alimentation se fait exclusivement par le topic Kafka.**

JSON UTF-8, dates ISO 8601. Erreurs au format uniforme `{ timestamp, status, code, message, path }`. Codes usuels : 400, 401, 403, 404, 409 (doublon/unicité), 422 (règle de gestion).

**Un sixième champ facultatif `manques`** (décision Sprint 4.2, service Workflow uniquement) : liste de `{ code, message }` accompagnant le seul refus `422 ETAT_INCOMPLET`, **absent du JSON partout ailleurs**. CT-13 exige que les manques soient listés, pas concaténés en prose : le frontend doit pouvoir les rendre un à un et renvoyer l'agent vers la journée fautive. Les cinq champs du contrat restent présents, au même nom et au même type. Voir `docs/controles-completude.md`.

---

## 12. CONVENTIONS DE CODE

- Java : packages `cm.afrilandfirstbank.rations.<service>.<couche>`, classes en PascalCase, méthodes en camelCase.
- SQL : `snake_case`, migrations Flyway `V<n>__description.sql` par service.
- REST : noms de ressources au pluriel, verbes HTTP standard, pas de verbe dans l'URL.
- DTO en entrée et en sortie, jamais d'entité JPA exposée directement.
- Requêtes paramétrées JPA uniquement, aucune concaténation de chaîne utilisateur.
- Validation des DTO entrants, erreurs explicites.
- Toute action sensible publie un événement d'audit (auteur, date, delta avant/après) sur `rations.audit.evenement`.
- Frontend : composants en PascalCase, hooks en `useXxx`, appels API centralisés (axios), typage strict (pas de `any`).
- Secrets hors du code : variables d'environnement (URL realm, DB, brokers Kafka, origines CORS), avec repli explicite `changeme-in-development`. Fichiers d'environnement jamais versionnés.
- Dépôt Git : `core.longpaths=true` requis sous Windows, le chemin projet plus l'arborescence à quatre couches dépassant la limite de 260 caractères.

---

## 13. RÉFÉRENTIEL DES CODES GUICHETS

`code_agence` et `code_unite` puisent dans le référentiel des codes guichets Afriland (VARCHAR(5)), maintenu hors du module. Exemples : `00001` Siège Yaoundé, `00002` Douala Bonanjo, `00003` Bafoussam, … `00050` Bandjoun. Le module le consomme comme domaine de valeurs.

---

## 14. ORDRE D'IMPLÉMENTATION SUGGÉRÉ

1. Service Identité + intégration Keycloak (socle d'authentification).
2. **Service Audit** (socle de traçabilité, consommé par tous les suivants).
3. Service Grilles (prérequis du calcul des montants).
4. Service Saisie (fiches, lignes, RG-01 à RG-05).
5. Service Workflow (soumission, validation, RG-07 à RG-13, aiguillage RG-08).
6. Service Transmission (Kafka publication + consommation).
7. Service Reporting (suivi, historique, exports).
8. État complémentaire (RG-15), fonctionnalité fermée par drapeau jusqu'à confirmation métier.
9. Passerelle + registre, conteneurisation, déploiement Kubernetes.

Le service Audit remonte en deuxième position : les services suivants publient des événements d'audit dès leurs premières écritures, il faut donc que le consommateur existe.

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
- **Appeler le service Audit en REST synchrone depuis un service métier** (l'audit passe par le topic Kafka, jamais par un appel bloquant).
- **Exposer un endpoint d'écriture sur le service Audit** ou une méthode de suppression sur `audit_log`.
- **Réécrire un producteur d'audit dans un service** au lieu de dépendre de `rations-audit-commun`, ou y surcharger `max.block.ms` et les autres bornes du producteur.
- **Faire dépendre `service-audit` de `rations-audit-commun`** (le consommateur doit rester un *tolerant reader* avec son propre type).
- **Ajouter un type métier, un DTO ou un utilitaire dans `rations-audit-commun`** : il ne porte que la publication d'audit, et le build le vérifie.
- **Consommer `/identite/habilitation` sans publier `ACCES_REFUSE`** sur verdict négatif ou sur indisponibilité du service Identité : le refus deviendrait intraçable.
- **Mettre en cache une réponse d'habilitation**, ou autoriser une action quand le service Identité ne répond pas (le refus conservateur est la règle, section 9.2 et `docs/appel-habilitation.md`).

---

## 16. POINTS À CONFIRMER AVEC LE MÉTIER

- Découpage en réseaux pour la portée d'accès du Directeur Réseau (DR) : aucune spécification ne définit la notion de réseau ni le rattachement d'une unité à un réseau. Décision provisoire prise au Sprint 1.1 (voir section 17 et `docs/decisions/2026-08-26-portee-acces-directeur-reseau.md`) : portée nationale par défaut, comme ARH/DRH/ADMIN.
- Gestion de l'état complémentaire : fréquence réelle du besoin, délai pendant lequel une période close reste régularisable (valeur provisoire : 90 jours).
- Position de la comptabilité sur une seconde transmission portant sur une période déjà traitée.
- Namespace Kubernetes, conventions de nommage des déploiements et gestion des secrets (à arrêter avec la DSI).

---

## 17. DÉCISIONS PRISES EN COURS DE DÉVELOPPEMENT

| Sprint | Décision |
| --- | --- |
| 0.2 | `audit_log` géré par un **service Audit dédié** (7ᵉ service, port 8087, base `rations_audit`) plutôt qu'une table par service ou une base partagée. Motif : immuabilité garantie par l'architecture. |
| 0.2 | Passerelle **Spring Cloud Gateway**, registre **Eureka**, Spring Cloud 2025.1.2. |
| 0.2 | `spring-boot-starter-parent` comme parent Maven, versions hors Spring Boot en `dependencyManagement`. |
| 0.2 | `SecurityConfig` minimale par service, ouverte, jusqu'à l'intégration Keycloak du Sprint 0.4. |
| 0.2 | iText 8 déclaré via les artefacts `kernel` et `layout` (`itext-core` est un agrégat, pas une dépendance directe). |
| 0.2 | `git config core.longpaths true` appliqué localement au dépôt. |
| 0.5 | Écriture d'audit **asynchrone par topic Kafka** `rations.audit.evenement`, jamais par appel REST synchrone. |
| 0.6 | Documentation Springdoc (`/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs`, `/v3/api-docs/**`) déclarée `permitAll()` en `GET` dans chaque `SecurityConfig`, à côté de `/actuator/health`. Motif : « accès interne » ne veut pas dire authentifiée, et Swagger doit rester consultable directement au navigateur sans jeton Bearer. Les routes métier restent toutes protégées. |
| 0.3 | Frontend démarré sur le port **5173** au lieu de 3000 (planning initial) : port figé dans les URI de redirection du realm Keycloak, tout autre port ferait échouer la connexion sans message explicite. |
| 0.4 | Création du profil local : **pré-provisionnement par l'administrateur puis liaison automatique** au premier login. Rapprochement par le `login`, inscription du `sub_keycloak` à la première connexion. Un jeton valide sans profil correspondant est refusé (403). |
| 0.7 | Table `utilisateurs` complétée de `matricule`, `email`, `actif`, `date_dernier_acces`, au-delà du dictionnaire d'origine (section 4). Conservées et consignées plutôt que supprimées : jugées utiles à l'usage (contrôle de rôle insuffisant, désactivation de compte). |
| 0.7 | Colonne `date_creation` ajoutée par convention à la plupart des tables métier (absente du dictionnaire d'origine). Conservée et consignée comme convention transverse d'audit technique. |
| 1.1 | Portée d'accès du Directeur Réseau (DR) : **portée nationale par défaut**, comme ARH/DRH/ADMIN, faute de découpage en réseaux défini par le métier. Décision provisoire, réversible à coût faible. Voir `docs/decisions/2026-08-26-portee-acces-directeur-reseau.md`. |
| 1.2 | Attribution du rôle et du code unité (`PUT /identite/utilisateurs/{id}/role`), trois points de contrôle interne tranchés avec le métier : (1) un administrateur **ne peut pas modifier son propre rôle** sur cet endpoint (409), y compris s'il est seul administrateur actif — limite connue, résolution hors module (DSI) ; (2) le retrait du rôle ADMIN au **dernier administrateur actif** (`actif = true`, sans exiger de liaison Keycloak déjà établie) est bloqué (409) ; (3) **aucune invalidation de session** n'est ajoutée : le module étant stateless, le rôle est relu en base à chaque requête. La révocation immédiate du jeton Keycloak lui-même reste hors périmètre, consignée dans `docs/points-en-attente.md`. Voir `docs/decisions/2026-08-26-attribution-role-administrateur.md`. |
| 1.2 | Traçabilité des modifications de profil : publication temporairement journalisée via `PublicateurAuditEvenementLog` (SLF4J), faute d'infrastructure Kafka dans le backend. **Résolu au Sprint 1.3** : la classe et son port local ont été supprimés au profit du producteur Kafka de `rations-audit-commun`. |
| 1.2 | Format de pagination de référence du projet arrêté dans `PageResponse<T>` (service-identite) : `content, page, size, totalElements, totalPages, dernierePage`, construit depuis `org.springframework.data.domain.Page`. Repris tel quel par les listes des Sprints 3, 4 et 6. |
| 1.2 | Convention de test ajoutée : Maven Surefire ne prend par défaut que `*Test.java`. Le pom parent backend inclut désormais aussi `*IT.java`, pour que les tests d'intégration MockMvc nommés `...ControllerIT` s'exécutent via `mvn test` sans dépendre de Failsafe. |
| 1.3 | Vérification d'habilitation inter-services : **endpoint dédié** `GET /identite/habilitation?codeUnite={code}`, plutôt que de laisser chaque service décider à partir de `GET /identite/moi`. Motif : `code_unite` n'est pas dans le jeton Keycloak (section 10), donc un consommateur ne peut pas trancher seul le cas d'un rôle à portée locale — la dépendance existe quel que soit le choix ; l'endpoint fixe seulement *où s'exécute la règle*, dans le service qui en est propriétaire, plutôt que recopiée six fois. Endpoint **interne**, hors contrat passerelle (section 11). Voir `docs/appel-habilitation.md`. |
| 1.3 | Propagation du jeton : le service appelant **relaie tel quel l'en-tête `Authorization` de l'utilisateur final**, il ne s'authentifie pas avec un compte de service. Motif vérifié sur le realm `afb-rations-dev` : un seul client, public, `serviceAccountsEnabled: false` — aucune identité machine-à-machine n'existe, et le mapper d'audience place déjà `aud: rations-api` dans le jeton utilisateur. Aucune modification Keycloak nécessaire. |
| 1.3 | Indisponibilité du service Identité : **refus conservateur (fail-closed)**. Timeout, `5xx`, connexion refusée → le consommateur refuse l'action. **Aucun cache** d'un verdict positif : une habilitation peut avoir changé entre deux appels, et autoriser sur une valeur potentiellement obsolète contredirait RG-12 et le principe bancaire du refus par défaut. Compromis assumé : une panne du service Identité bloque les validations ; la réponse est de fiabiliser ce service, pas d'assouplir la règle. |
| 1.3 | Partage du producteur d'audit : **module Maven commun `rations-audit-commun`**, plutôt que duplication dans les six services. Motif : les cinq couches qui garantissent que l'audit ne fait jamais échouer le métier sont délicates ; six copies, c'est six occasions qu'une disparaisse en silence. Le couplage de build est jugé acceptable — le réacteur est déjà couplé par le pom parent, et l'indépendance qui compte (runtime, base, déploiement) n'est pas touchée. **Périmètre vérifié au build** (enforcer + `PerimetreDuModuleTest`), pas confié à un README : la dérive devient impossible par accident, quoique restant possible délibérément. `service-audit` n'en dépend pas (tolerant reader). |
| 1.3 | Ordre de publication : l'envoi sur le topic a lieu **après le commit** de la transaction (`@TransactionalEventListener(AFTER_COMMIT)`, `fallbackExecution = true`), et le listener est **`@Async` avec toute exception avalée localement**. Sans `@Async`, une exception d'audit remonterait à travers le commit et deviendrait une erreur HTTP alors que la modification est déjà en base. Le pool dédié porte un handler de rejet non levant, pour que la saturation ne rouvre pas la même porte. Risque résiduel assumé : perte d'événements (trace manquante), plus difficile à détecter qu'une trace fausse — voir `docs/publication-audit.md` section 5 et `docs/points-en-attente.md`. |
| 1.3 | Publication des refus (CT-04) : centralisée dans `GestionnaireErreursApi`, seul point où tous les refus convergent. Deux cas tracés (habilitation absente, rôle insuffisant), `idUtilisateur` nul quand l'auteur n'a pas de profil local. Non tracés : les 401 sans jeton (absence d'authentification, refusée en amont par le filtre) et les 4xx métier (erreurs d'usage, pas tentatives). Le verdict négatif de `/identite/habilitation` n'est pas tracé par le service Identité mais **par le consommateur** (sections 9.2 et 15). |
| 2.2 | **Modifier une grille ACTIVE crée une nouvelle ligne**, jamais une mise à jour en place. La remplaçante naît `EN_ATTENTE_DRH` ; l'ancienne est fermée (`date_fin` à la veille) au moment de la validation DRH, dans la même transaction (Sprint 2.3). Motif : la mise à jour en place perdrait l'historique, rendrait injustifiables les lignes déjà payées, exigerait la transition `ACTIVE → EN_ATTENTE_DRH` interdite au Sprint 2.1, et laisserait le couple soit avec un montant non validé, soit sans aucune grille active. Voir `docs/decisions/2026-08-27-versionnement-et-unicite-des-grilles.md`. |
| 2.2 | Contrôle d'unicité RG-14 : le refus porte sur la **cohérence de période**, non sur l'existence d'une grille active. Deux conflits en 409 : une proposition déjà `EN_ATTENTE_DRH` sur le couple (`GRILLE_EN_ATTENTE_EXISTANTE`, code ajouté au contrat) ; une date de début non strictement postérieure à celle de la grille en vigueur (`GRILLE_ACTIVE_EXISTANTE`, code du contrat). Une proposition postérieure est **acceptée** — c'est le remplacement normal. L'anti-datage est refusé parce que la fermeture « à la veille » produirait une période à l'envers et rendrait injustifiables des montants déjà payés. |
| 2.2 | `POST /grilles` : **création et soumission en un seul appel**, dans la même transaction. Aucun `BROUILLON` n'est jamais persisté. Motif : le contrat d'API ne prévoit qu'un endpoint, et l'option en deux temps obligerait à rejouer le contrôle d'unicité à la soumission. Conséquence assumée : la transition `BROUILLON → BROUILLON` du Sprint 2.1 n'est plus atteignable, mais elle est conservée. |
| 2.2 | Auteur d'une grille : **appel unique à `GET /identite/moi` à l'écriture, libellé recopié et figé dans la ligne** (`libelle_createur`, `libelle_validateur`, migration additive `V4`). Motif : `id_createur` est `NOT NULL` et le jeton Keycloak ne porte pas l'identifiant local — l'appel est imposé par le schéma, pas choisi. Les lectures restent 100 % locales : une panne d'identité empêche de proposer un tarif, jamais d'en appliquer un. **Motif réutilisable aux Sprints 2.3 à 6.** Voir `docs/decisions/2026-08-27-libelle-acteur-grille.md`. |
| 2.2 | Panne du service Identité pendant une écriture : refus conservateur (doctrine 1.3) rendu en **`503 SERVICE_IDENTITE_INDISPONIBLE`**, non en 403. Motif : l'ARH possède le droit qu'il exerce ; un 403 l'enverrait réclamer une habilitation qu'il a déjà pendant que la panne resterait invisible. Le 403 reste opposé quand le service Identité **répond** qu'aucun profil n'est ouvert. Les deux cas sont tracés (`ACCES_REFUSE`, motifs `IDENTITE_INDISPONIBLE` et `HABILITATION_ABSENTE`) depuis `GestionnaireErreursApi`. |
| 2.2 | `PageResponse<T>` **recopié à l'identique** dans service-grilles depuis service-identite, comme `NatureEnum` / `SessionEnum` au Sprint 2.1. Duplication assumée : `rations-audit-commun` est la seule mutualisation du backend et son périmètre est vérifié au build (CLAUDE.md §3 et §15). |
| 2.2 | Correctif hors perimetre assume dans `rations-audit-commun` : `DeltaAudit` serialisait les dates en tableau de composants (`[2026,9,1]`) au lieu de l'ISO 8601 exige par la section 11. Releve a la verification manuelle du 2.2, corrige a la source (`WRITE_DATES_AS_TIMESTAMPS` desactive) plutot que chez chaque appelant : le defaut valait pour les six services et aurait grossi a chaque nouvelle date tracee. Test de non-regression dans `DeltaAuditTest`. |
| 2.3 | Bornage des periodes : l'ancienne grille est fermee **a la veille de la `date_debut` de la remplacante**, non a la date de la decision. Motif : validation et prise d'effet sont deux instants differents ; fermer a la decision laisserait sans grille active les jours qui les separent, et une prestation tombee dans ce trou n'aurait aucun montant applicable (Sprint 2.4, rattrapage 6bis). La veille est la seule borne produisant un partitionnement exact du temps — a toute date, une grille et une seule. Consequence assumee : une remplacante a effet futur fait porter des aujourd'hui une `date_fin` future a l'ancienne, a presenter comme une *fermeture programmee*. Voir `docs/decisions/2026-08-27-bornage-et-atomicite-de-la-bascule.md`. |
| 2.3 | Atomicite de la bascule : fermeture et activation dans **une seule transaction**, avec **vidage explicite** (`saveAndFlush`) entre les deux. Motif : l'index partiel `ux_grille_active_par_couple` est evalue par PostgreSQL instruction par instruction, pas au commit — si l'activation partait avant la fermeture, il existerait l'espace d'un ordre SQL deux lignes actives sans `date_fin`, et la base refuserait une bascule legitime. L'ordre de vidage d'Hibernate n'etant pas un contrat public, il est fixe plutot que subi. L'appel au service Identite reste **hors** transaction, l'audit **apres** commit (doctrine 1.3). |
| 2.3 | Le controle de statut precede toute ecriture (`TransitionGrille.exigerValidationPossible`) : verifier apres avoir ferme l'ancienne aurait fonctionne par le rollback, mais aurait fait dependre la correction du resultat d'un mecanisme technique plutot que de l'ordre des etapes. |
| 2.3 | **Un rejet ne touche jamais la grille en vigueur** : le service ne la consulte meme pas. Motif : un rejet dit « ce tarif ne s'appliquera pas », pas « il n'y a plus de tarif ». Le motif est exige non vide a deux etages — `@NotBlank` sur le DTO (400 `REQUETE_INVALIDE`) et `TransitionGrille.rejeter` (422 `MOTIF_OBLIGATOIRE`). |
| 2.3 | Trois codes d'erreur ajoutes au contrat : `GRILLE_INTROUVABLE` (404), `TRANSITION_INTERDITE` (422, et non 409 : rien n'est duplique, c'est une regle de gestion qui refuse), `MOTIF_OBLIGATOIRE` (422, code deja retenu par le contrat pour le retour d'un processus sans motif — meme regle, meme code). |
| 2.3 | Hygiene de build relevee a la verification manuelle : `mvn spring-boot:run -pl <service>` resout `rations-audit-commun` depuis `~/.m2`, **pas depuis le reacteur**. Un module commun modifie mais non reinstalle reste invisible a l'execution, alors meme que `mvn test` passe (les tests, eux, passent par le reacteur). Constate au 2.3 : un jar perime rejouait le defaut de serialisation des dates corrige au 2.2. **Toute modification de `rations-audit-commun` doit etre suivie d'un `mvn -pl rations-audit-commun install`** avant de demarrer un service a la main. |
| 2.4 | Résolution du montant (RG-03) : `GET /grilles/active` renvoie **200 avec `disponible: false` et `montantFcfa: null`** quand aucune grille ne couvre la date demandée, jamais un `404` ni un montant à `0`. Même parti que `GET /identite/habilitation` (Sprint 1.3, `autorise: false`) : un endpoint interne qui répond à une question métier ne code pas la réponse négative comme une erreur de transport. Le paramètre `date` est **obligatoire, sans valeur par défaut** : un repli sur la date du jour produirait un montant faux pour toute saisie rétroactive, sans déclencher d'erreur. Voir `docs/decisions/2026-08-27-resolution-du-montant-applicable.md`. |
| 2.4 | Deux grilles ACTIVE ne doivent jamais couvrir la même date pour un même couple (invariant garanti à l'écriture par l'index partiel et la bascule du Sprint 2.3). Si ce cas survient malgré tout, le service **refuse et signale, il n'arbitre jamais** : `500 INCOHERENCE_GRILLE` au format d'erreur uniforme, tracé en log au préfixe repérable `INCOHERENCE GRILLE` (même convention que `AUDIT PERDU` dans `rations-audit-commun`). Choisir arbitrairement l'une des deux grilles servirait un montant potentiellement faux sans que personne ne le voie. |
| 2.4 | `GET /grilles/active` est **authentifié, sans rôle exigé** : le montant applicable est un barème sans information nominative, déjà lisible par `GET /grilles` pour l'ARH et la DRH. Le service appelant relaie tel quel l'en-tête `Authorization` de l'utilisateur final (doctrine 1.3), il ne s'authentifie pas avec un compte de service. |
| 2.4 | Indisponibilité du service Grilles côté service Saisie (Sprint 3.1) : **refus conservateur (fail-closed)**, comme pour le service Identité (doctrine 1.3). Timeout, `5xx`, connexion refusée → la ligne de prestation est refusée, jamais enregistrée sans montant en vue d'une valorisation ultérieure. Motif : `ligne_prestation.montant_applique` n'admet pas de valeur absente dans le dictionnaire actuel (section 4), et une ligne à montant incertain est exactement le risque que RG-03 cherche à écarter. Voir `docs/appel-resolution-montant.md`. |
| 2.4 | **Dette identifiée pour le Sprint 3.1** : `ligne_prestation` ne porte que `montant_applique` (section 4), pas l'identité de la grille qui l'a fourni. L'endpoint de résolution renvoie pourtant `idGrille`, `dateDebut`, `dateFin` pour justifier a posteriori un montant contesté — cette traçabilité n'a aujourd'hui nulle part où atterrir. **Action attendue au 3.1** : ajouter une colonne `id_grille` à `ligne_prestation`, migration additive sur le modèle de `V4` (Sprint 2.2), sans clé étrangère inter-base (même convention que `id_createur`/`id_validateur`). Voir `docs/decisions/2026-08-27-resolution-du-montant-applicable.md` §5. |
| 3.1 | **Identification d'un bénéficiaire déjà connu : le numéro de compte courant seul.** Ni le nom, ni le couple nom+prénom, ni aucune combinaison avec le code agence. Motif : il n'y a aucun enrôlement dans ce processus (CLAUDE.md §4) — le bénéficiaire naît au fil des saisies. Le nom est recopié à la main d'un jour sur l'autre et varie (accents, ordre, abréviations) ; le numéro de compte est recopié d'un document, stable, et c'est déjà la donnée qui commande la mise en paiement. **Compte connu mais nom enregistré différent : la ligne est rattachée au bénéficiaire existant, jamais écrasé**, et l'écart est journalisé au préfixe repérable `INCOHERENCE BENEFICIAIRE`. Écraser silencieusement changerait le nom de toutes les lignes déjà payées. Voir `docs/decisions/2026-08-28-resolution-beneficiaire-et-incoherence-nom.md`. |
| 3.1 | **Rattachement au processus mensuel : le processus préexiste à la saisie, la Saisie ne le crée jamais.** L'agent le déclenche explicitement par `POST /processus` côté service Workflow (contrat §5, US-03) ; `fiche_journaliere.id_processus` est une référence logique inter-base, sans clé étrangère possible. La Saisie **vérifie l'existence et le statut du processus auprès du service Workflow à chaque écriture, sans jamais mettre le verdict en cache** (doctrine 1.3) : un état soumis entre deux saisies doit refuser la suivante. `code_unite`, `mois_paiement` et `annee_paiement` sont **recopiés et figés** sur la fiche à son ouverture (migration V3), ce qui rend les lectures ultérieures — portée d'accès comprise — indépendantes du service Workflow. Voir `docs/rattachement-processus.md`. |
| 3.2 | **Appels sortants : 2 s de connexion, 3 s de lecture, aucun réessai.** Posés une fois pour toutes sur le `RestClient.Builder` partagé (`ConfigurationAppelsSortants`), valables dans tous les profils — c'est une doctrine d'appel, pas un réglage d'environnement. Mêmes valeurs qu'au Sprint 2.2 côté Grilles : une seule convention dans le module vaut mieux que deux valeurs proches qu'un lecteur prendrait pour une différence intentionnelle. **Aucun réessai** parce qu'une ligne saisie dépend déjà de trois appels synchrones (Grilles, Workflow, Identité) : réessayer chacun ferait passer le pire cas de 9 à 18 secondes, contre une cible de 3 secondes. Service Grilles injoignable → **refus conservateur**, la ligne n'est jamais enregistrée sans montant. Voir `docs/decisions/2026-08-31-delais-et-reessai-des-appels-sortants.md`. |
| 3.3 | **Caractère modifiable de l'état : revérifié à chaque écriture, jamais mis en cache**, sur les quatre endpoints d'écriture (`POST /saisie/fiches`, `POST`, `PUT`, `DELETE` sur `/saisie/lignes`). Refus rendu en **`422 ETAT_NON_MODIFIABLE`**, et non en `409` : rien n'est dupliqué, c'est une règle de gestion qui refuse — même raisonnement qu'au Sprint 2.3 pour `TRANSITION_INTERDITE`. Le message nomme la période, l'unité, le statut **et l'action attendue** (demander le retour au chef d'unité, RG-11) : un « état non modifiable » sec laisserait l'agent sans recours. Écart au contrat d'API à corriger dans le contrat, pas à laisser en silence. Voir `docs/decisions/2026-08-31-code-http-du-refus-sur-etat-non-modifiable.md`. |
| 3.4 | **RG-06 est partagée entre deux services : Saisie produit l'état consolidé, Workflow porte le montant total** sur `processus_mensuel` et applique l'aiguillage au seuil (RG-08). Aucun des deux ne fait le travail de l'autre : Saisie ne connaît pas le seuil, Workflow ne réadditionne pas les lignes. L'échange passe par l'endpoint **interne** `GET /saisie/processus/{id}/etat?codeUnite={code}` (section 11), hors contrat passerelle. Le total est **la somme des sous-totaux journaliers, eux-mêmes sommes des lignes rendues dans la réponse** : aucun `SUM` SQL parallèle, donc aucune divergence possible entre le détail affiché et le total. Calculé dans `ConsolidationService` et **nulle part ailleurs** ; entiers uniquement (`int` par ligne, `long` en somme), jamais un flottant. Agrégation sur les **montants figés** (RG-03), le service Grilles n'est jamais rappelé. Voir `docs/appel-consolidation.md`. |
| 3.4 | **`codeUnite` est transmis en paramètre obligatoire par le service Workflow**, plutôt que lu sur les fiches. Motif : un processus sans aucune fiche n'a pas de code unité à lire — le contrôle de portée disparaîtrait au moment précis où il n'y a rien à lire, et un chef d'unité serait traité différemment selon que son dossier contient ou non des lignes. Redemander l'unité à Workflow aurait produit l'aller-retour circulaire `Workflow → Saisie → Workflow`. Le paramètre n'est qu'une **déclaration à vérifier** : le `code_unite` figé sur les fiches reste l'autorité, et le désaccord est refusé en **`403 UNITE_NON_CONCORDANTE`**, tracé en audit — un paramètre fourni par l'appelant ne se croit pas sur parole. Aucun repli silencieux : paramètre absent → `400`. Voir `docs/decisions/2026-08-31-endpoint-interne-de-consolidation.md`. |
| 3.4 | **Processus sans aucune fiche : `200` avec zéro journée et un total de zéro, jamais `404`.** Même parti qu'au Sprint 2.4 pour `GET /grilles/active` : un endpoint interne qui répond à une question métier ne déguise pas une réponse négative légitime en erreur de transport. Un `404` serait de surcroît faux — Saisie ne sait pas si le processus existe (`processus_mensuel` vit dans une autre base), elle sait seulement qu'elle ne détient rien pour lui. **C'est à Workflow de refuser la soumission d'un état vide**, sur `nombreLignes == 0`. |
| 3.4 | Endpoint de consolidation ouvert aux **trois rôles du circuit** (`AGENT_UNITE`, `CHEF_UNITE_DA`, `DIRECTEUR_RESEAU_DR`), contrat §5, dans un **contrôleur séparé** de `SaisieController` qui reste réservé à `AGENT_UNITE`. Motif : le chef d'unité et le directeur réseau doivent lire l'état qu'ils valident ; les loger dans le contrôleur de saisie aurait imposé d'assouplir le rôle des cinq endpoints d'écriture. Le rôle n'est que le premier filtre — la portée d'accès reste vérifiée unité par unité auprès du service Identité. |
| 4.1 | **Les entités du workflow suivent le dictionnaire (§4) et les tables du Sprint 0.5, pas les prompts du guide.** Cinq colonnes annoncées par le guide 4.1 n'existent ni au dictionnaire ni en base : `date_declenchement`, `date_cloture`, `id_createur` (`processus_mensuel`), `date_action` (`etape_workflow`), `date_modification` (`parametre_systeme` — que §4 déclare explicitement « sans horodatage propre »). Aucune migration V3 : `ddl-auto: validate` aurait fait échouer le démarrage, et le « qui » / « quand » est déjà porté par l'audit et par `etape_workflow.id_acteur`. Tranché avec l'utilisateur avant tout codage. |
| 4.1 | **La création est la première des neuf transitions d'ET01 : elle passe par `TransitionProcessus.declencher`, et le constructeur de `ProcessusMensuel` est en visibilité paquet.** L'invariant « toute transition passe par la machine à états » devient vérifié par le compilateur. `estAutorisee` ne couvre que les transitions état → état et rend `false` sur une source nulle : un statut nul est presque toujours un défaut, et lui donner le sens « depuis rien, donc création » transformerait ce défaut en autorisation. Un test confronte les 36 couples possibles à ET01 et exige exactement **huit** arêtes. |
| 4.1 | **La machine à états n'arbitre pas le montant, et c'est vérifié structurellement.** Les deux issues depuis `EN_ATTENTE_DA` sont exposées séparément et ne prennent que le processus : elles n'ont rien à comparer, donc rien à arbitrer. Le choix relève du service d'aiguillage du 4.3, qui lira `SEUIL_AIGUILLAGE_DR` dans `parametre_systeme`. Un test interdit tout nom de méthode contenant « montant » ou « seuil ». |
| 4.1 | **Le type COMPLEMENTAIRE est refusé sans lire `RATTRAPAGE_ACTIF`** (`422 FONCTIONNALITE_NON_OUVERTE`), contrôle placé avant l'appel au service Identité. Motif : lire le drapeau signifierait « si ce paramètre passe à vrai, ceci fonctionne » — faux au 4.1, où le code de l'état complémentaire n'existe pas. Un basculement produirait des états complémentaires sans aucun de leurs contrôles (RG-15, délai, état d'origine), soit exactement ce que le dispositif est censé empêcher. Le drapeau sera consulté au Sprint 6bis, quand il aura quelque chose à ouvrir. |
| 4.1 | Deux codes ajoutés au contrat : **`PROCESSUS_EXISTANT` (409)** — quelque chose est bien dupliqué, distinction du Sprint 2.3 ; le refus vient du service, dont le message nomme le dossier déjà ouvert, l'index partiel `ux_processus_normal_par_periode` restant le filet en cas de course — et **`FONCTIONNALITE_NON_OUVERTE` (422)**. |
| 4.1 | **`GET /processus/{id}/etat` porte deux montants distincts : `montantTotalPorte` (valeur enregistrée sur `processus_mensuel`) et `montantTotalFcfa` (total calculé à l'instant par Saisie).** Ils diffèrent légitimement tant que l'état n'est pas soumis ; les fondre ferait perdre l'information de savoir si le chiffre affiché engage le circuit ou photographie une saisie en cours. Workflow **recopie** le total, ne le réadditionne jamais. Un `montantTotalFcfa` **nul est un refus, jamais un zéro** : les deux commanderaient le même aiguillage, l'un à juste titre, l'autre par accident. |
| 4.1 | Transactions : `declencher` est transactionnelle (elle écrit), avec l'appel réseau **en tête** ; `consulter` et `consulterEtat` ne le sont **pas** — elles enchaînent des appels réseau, et une transaction immobiliserait une connexion pendant tout ce temps (doctrines 2.3 et 3.4). `ProcessusMensuel` ne porte aucune association paresseuse. |
| 4.1 | **Bouchon du Sprint 3.3 supprimé** (actions B-01 à B-04 soldées), le service Workflow répondant désormais. `ProcessusResponse` tient les cinq champs lus par `ProcessusReponse` côté Saisie, verrouillés par un test : les renommer ferait refuser **toute écriture de ligne** en `503`, sans aucune erreur de compilation. Écart au périmètre « service-workflow uniquement » du guide, tranché avec l'utilisateur. |
| 4.2 | **Ce qu'est un « état complet » : quatre contrôles arbitrés** — état vide, ligne hors période, ligne sans montant, bénéficiaire sans compte courant. Le détail, les contrôles écartés et leurs motifs vivent dans `docs/controles-completude.md`, **à porter dans ce fichier à la clôture du Sprint 4** (point K-04). |
| 4.2 | **Le contrôle de période porte sur les LIGNES, jamais sur les JOURNÉES.** Il n'existe aucun `DELETE /saisie/fiches/{id}` au contrat : bloquer sur une journée vide égarée enfermerait définitivement l'agent, qui ne peut supprimer que des lignes. Contrôle **délégué ici par la Saisie** (`OuvertureFicheRequest`, Sprint 3.3) : si Workflow ne le prend pas, il n'est implémenté nulle part. Enjeu réel : une ligne de mars glissée dans l'état de septembre échappe au contrôle d'unicité RG-15 de mars, donc devient payable deux fois. |
| 4.2 | **`nombre_signatures` compte les écritures, pas les étapes** (migration V3 additive). Incrémenté **uniquement après confirmation d'écriture disque** : écriture dans un temporaire, `flush`, `FileChannel.force(true)`, vérification de la taille sur disque, renommage atomique. **L'écriture a lieu hors transaction et avant elle** : si elle échoue, la transaction ne s'ouvre pas et aucune étape n'est créée. Asymétrie assumée : un fichier orphelin est préférable à un compteur affirmant une signature absente du document (même préférence que la doctrine d'audit 1.3). |
| 4.2 | **Nature de la signature : mention horodatée + empreinte SHA-256**, pas de cryptographie. Sur le document : **login**, rôle figé, date et heure. En base : `SHA-256:<hexa>` dans `etape_workflow.signature_numerique`, jusque-là inutilisé. Le login plutôt que le nom d'usage, parce qu'il est la clé de rapprochement avec le journal d'audit et qu'il est stable. **Ce n'est pas une signature au sens juridique** et l'empreinte vit dans la même base que le reste : elle détecte une altération du fichier, pas une falsification coordonnée. Seule la **dernière** empreinte reste vérifiable, le fichier changeant à chaque enrichissement. Intégration au service de signature de la banque : point ouvert **côté DSI** dans `docs/points-en-attente.md`. |
| 4.2 | **Champ `manques` ajouté au format d'erreur**, service Workflow uniquement, `@JsonInclude(NON_EMPTY)`. CT-13 exige des manques *listés* ; une concaténation en prose ne se rend pas en liste et empêcherait le frontend de renvoyer l'agent vers la journée fautive. **Un manque par contrôle, jamais un par ligne fautive** : au plus quatre entrées, cinq exemples nommés, le reste annoncé en nombre. À porter au contrat d'API §1.4 à la clôture du Sprint 4 (point K-03). |
| 4.2 | **La page des visas est une page dédiée, toujours la dernière, et un cadre non signé reste rigoureusement vide.** L'estampage d'un PDF *ajoute* du contenu sans jamais en retirer : une zone placée après le détail se trouverait à une ordonnée variable, et une mention « en attente » gravée à la création resterait lisible **sous** la signature venue s'inscrire par-dessus. `GabaritDocument` est la source unique de la géométrie, partagée par la création et par l'estampage des Sprints 4.3 et 4.4. |
| 4.2 | **Convention de nommage validée** : `{annee}/{mois}/etat-rations-{codeUnite}-{annee}{mois}-p{idProcessus}.pdf`, **chemin relatif** à `app.pieces-jointes.repertoire`. L'identifiant du processus n'est pas facultatif : plusieurs COMPLEMENTAIRE sont possibles sur un même couple unité / période (§4), et deux états produiraient sinon le même nom de fichier — la contrainte `id_processus UNIQUE` ne l'empêcherait pas, les identifiants différant. Racine absolue en base proscrite : elle rendrait la table fausse au changement de point de montage. |
| 4.2 | **Police du document : Times-Roman, écart assumé à la charte §8.2** qui prescrit Bookman Old Style. Motif juridique et non technique : Bookman est une fonte Monotype licenciée avec Windows/Office, l'embarquer dans une image Docker serait une redistribution ; elle n'est de surcroît pas installée sur le poste. Times-Roman est native au format PDF, donc sans octet embarqué ni licence à valider. Tout le reste de la charte est tenu, sauf les cellules de tableau à 10 pt au lieu de 12 (six colonnes sur 482 pt de largeur utile). Point ouvert K-05. |
| 4.2 | **`GET /identite/moi` à la soumission : appel imposé par le schéma, pas choisi.** `etape_workflow.id_acteur` est `NOT NULL` et `/identite/habilitation` ne rend qu'un `login` — motif du Sprint 2.2 pour `id_createur`. Porte la soumission à **trois appels sortants** (15 s au pire cas, délais du Sprint 3.2) : accepté, la soumission étant un geste mensuel et non par ligne. Effet heureux : `SOUMISSION_PROCESSUS` est le premier événement d'audit de ce service à porter un `idUtilisateur`. |
| 4.2 | **Deux refus de montant, jamais une valeur approchée** : `montantTotalFcfa` nul → `503` (zéro et « absent » commanderaient le même aiguillage, l'un à juste titre, l'autre par accident) ; total supérieur à `Integer.MAX_VALUE` → `503` plutôt qu'une conversion silencieuse, qui rendrait le montant **négatif** et commanderait ensuite l'aiguillage. |
| 4.2 | **Le bloc transactionnel vit dans une classe à part** (`EnregistrementSoumission`). Raison technique d'abord : `@Transactional` sur une méthode appelée depuis la même classe n'a **aucun effet** avec les mandataires Spring, et rien ne le signalerait. Raison de lisibilité ensuite : la frontière entre appels réseau et écritures en base devient visible dans la structure. Une garde y relit le statut, trois appels réseau et une écriture disque s'étant écoulés depuis le premier contrôle. |
| 4.2 | **Hygiène de build, à ranger à côté de celle du Sprint 2.3 sur `~/.m2`** : un `mvn test` sans `clean` peut rapporter des erreurs « Unresolved compilation problem » — format du compilateur **Eclipse**, pas de `javac` — venant de `.class` produits par l'IDE et réutilisés par Maven. Devant une erreur de compilation inattendue, **relancer avec `clean` avant de chercher la cause dans le code**. |
