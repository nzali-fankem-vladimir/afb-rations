# Passerelle et registre : noms logiques, routage, endpoints internes

**Sprint :** 8.1, 20 septembre 2026
**Statut :** en service, vérifié en réel contre les sept services et un vrai jeton Keycloak.

---

## 1. Registre et noms logiques

### 1.1 Noms logiques arrêtés

Le nom logique d'un service est son `spring.application.name`. Il n'a pas été
modifié par ce sprint, et **il ne doit jamais l'être** : le service émetteur du
journal d'audit en est estampillé (`EvenementAudit.avecServiceEmetteur`,
CLAUDE.md §9.2), donc un renommage changerait les valeurs écrites dans un
journal immuable.

| Nom logique | Port | Préfixes routés (après `/api`) |
| --- | --- | --- |
| `service-identite` | 8081 | `/identite` |
| `service-saisie` | 8082 | `/saisie` |
| `service-grilles` | 8083 | `/grilles` |
| `service-workflow` | 8084 | `/processus`, `/parametres` |
| `service-reporting` | 8085 | `/reporting` |
| `service-transmission` | 8086 | `/transmission` |
| `service-audit` | 8087 | `/audit` |

La passerelle (`gateway`, 8080) et le registre (`registry`, 8761) ne sont pas
routés : la première est l'entrée, le second n'est pas exposé.

### 1.2 Décision : le registre est facultatif en développement local

Question posée à l'étape 2 : le registre est-il indispensable en local, ou une
adresse fixe suffit-elle ? Poste de développement mesuré à 15,9 Go de mémoire
dont **1,0 Go libre** avec Postgres, Kafka et Keycloak lancés.

Option retenue, dite hybride :

- **Routage** : chaque route de la passerelle porte une adresse lue dans une
  variable d'environnement, avec un défaut fixe local (ex. `WORKFLOW_URI`,
  défaut `http://localhost:8084`). En recette et en production la même variable
  vaut `lb://service-workflow`. Une seule table de routage, deux modes.
- **Enregistrement** : les sept services et la passerelle embarquent le client
  Eureka, activé par l'interrupteur `EUREKA_ENABLED`, **éteint par défaut**
  (`false`). L'adresse du registre est `EUREKA_URL`
  (défaut `http://localhost:8761/eureka`).
- Un poste léger travaille sans registre : une JVM de moins sur neuf. Pour
  vérifier la découverte, on allume l'interrupteur le temps du test.

Options écartées :

- **Registre toujours actif** : une JVM de plus (300 à 400 Mo), 10 à 30 s
  avant qu'un service soit visible, donc des 503 de la passerelle juste après
  un démarrage, sur un poste qui n'a qu'un Go libre.
- **Adresses fixes seules** : plus léger, mais le critère « sept services
  visibles dans le registre » ne serait jamais démontré, et la production
  exigerait un changement de mode de routage.

**Les deux modes sont vérifiés**, pour que l'interrupteur ne cache pas un mode
cassé : mêmes réponses (401 sur les routes du contrat, 404 sur les internes) en
adresses fixes et en `lb://` à travers le registre.

### 1.3 Ce que le registre n'est pas

Le registre ne porte aucune sécurité : son tableau de bord est ouvert. Il ne
doit donc être joignable que sur le réseau interne (à traiter avec la DSI au
Sprint 8.3, manifests Kubernetes).

---

## 2. Table de routage

**Principe : liste blanche.** Seuls les chemins déclarés dans
`gateway/src/main/resources/application.yml` sont exposés ; tout le reste rend
404 à la passerelle. Les endpoints internes ne sont donc pas exposés **parce
qu'ils n'y figurent pas**, et non parce qu'une règle les exclut. Une liste noire
aurait laissé passer tout endpoint interne ajouté plus tard.

**Chaque route porte son verbe.** Deux chemins portent à la fois un endpoint du
contrat et un endpoint interne (voir §3).

**Les identifiants sont contraints aux chiffres** (`{id:[0-9]+}`). Sans cette
contrainte, `GET /processus/recherche` (interne) serait capté par la règle
`GET /processus/{id}` et exposé.

| Verbe | Chemin (préfixe `/api`) | Service | Au contrat |
| --- | --- | --- | --- |
| GET | `/identite/moi` | Identité | oui |
| GET | `/identite/utilisateurs` | Identité | oui |
| PUT | `/identite/utilisateurs/{id}/role` | Identité | oui |
| POST | `/saisie/fiches` | Saisie | oui |
| GET | `/saisie/fiches/{id}/lignes` | Saisie | oui |
| POST | `/saisie/lignes` | Saisie | oui |
| PUT, DELETE | `/saisie/lignes/{id}` | Saisie | oui |
| GET, POST | `/grilles` | Grilles | oui |
| GET | `/grilles/active` | Grilles | oui |
| POST | `/grilles/{id}/validation` | Grilles | oui |
| POST | `/grilles/{id}/rejet` | Grilles | oui |
| POST | `/grilles/{id}/retrait` | Grilles | **non** |
| POST | `/processus` | Workflow | oui |
| GET | `/processus/{id}` | Workflow | oui |
| GET | `/processus/{id}/etat` | Workflow | oui |
| GET | `/processus/{id}/document` | Workflow | **non** |
| POST | `/processus/{id}/soumission` | Workflow | oui |
| POST | `/processus/{id}/validation` | Workflow | oui |
| POST | `/processus/{id}/retour` | Workflow | oui |
| GET | `/parametres/fonctionnalites` | Workflow | **non** |
| GET, PUT | `/parametres/{code}` | Workflow | **non** |
| GET | `/reporting/demandes` | Reporting | oui |
| GET | `/reporting/rapports` | Reporting | oui |
| GET | `/reporting/rapports/export` | Reporting | oui |
| GET | `/reporting/processus/{id}/historique` | Reporting | oui |
| GET | `/transmission/processus/{id}` | Transmission | oui |
| GET | `/audit/entrees` | Audit | oui |
| GET | `/audit/processus/{id}` | Audit | oui |
| GET | `/audit/actions` | Audit | **non** |

### 2.1 Les six endpoints hors contrat, et pourquoi ils sont routés

Le contrat d'API compte 26 endpoints. Six autres, nés après lui, sont appelés
par le frontend. **Les classer « internes » parce qu'ils sont hors contrat
casserait chacun un écran, sans aucune erreur visible** : le service
répondrait normalement à qui l'appelle, mais plus personne ne l'appellerait.

| Endpoint | Écran concerné |
| --- | --- |
| `GET /parametres/fonctionnalites` | affichage et accès de la régularisation (Sprint 7F.7) |
| `GET /parametres/{code}` | administration des paramètres (ADMIN, 7F.6) |
| `PUT /parametres/{code}` | administration des paramètres (ADMIN, 7F.6) |
| `GET /processus/{id}/document` | téléchargement du document signé |
| `POST /grilles/{id}/retrait` | retrait d'une proposition par son auteur |
| `GET /audit/actions` | liste des types d'action du journal d'audit |

Le guide du sprint ne signalait que le premier. Les cinq autres ont été relevés
dans les contrôleurs et dans les modules d'appel du frontend, pas déduits du
contrat.

---

## 3. Les dix endpoints internes, non exposés

Appelés de service à service, directement (`IDENTITE_URL`, `WORKFLOW_URL`...),
jamais à travers la passerelle. Chacun rend **404** sur le port 8080, vérifié.

| Verbe | Chemin | Service | Appelé par |
| --- | --- | --- | --- |
| GET | `/identite/habilitation` | Identité | les six services métier |
| GET | `/identite/utilisateurs/libelles` | Identité | Reporting |
| GET | `/saisie/processus/{id}/etat` | Saisie | Workflow, Transmission |
| GET | `/saisie/processus/recherche` | Saisie | Reporting |
| GET | `/processus/recherche` | Workflow | Reporting |
| GET | `/processus/{id}/historique` | Workflow | Reporting |
| PUT | `/processus/{id}/integration` | Workflow | Transmission (secret `X-Cle-Interne`) |
| GET | `/processus/{id}/integration` | Workflow | Transmission |
| PUT | `/processus/{id}/transmission` | Workflow | Transmission (verrou de RG-13) |
| POST | `/transmission/processus/{id}` | Transmission | Workflow, à la clôture |

### 3.1 Le piège des verbes

Deux chemins portent des endpoints de natures opposées. Une règle écrite sur le
chemin sans le verbe exposerait le mauvais. Même leçon qu'au Sprint 5.3 sur le
`securityMatcher`.

| Chemin | Verbe exposé | Verbe interne |
| --- | --- | --- |
| `/transmission/processus/{id}` | **GET** (statut d'intégration) | **POST** (déclenchement de transmission) |
| `/processus/{id}/integration` | aucun | **PUT** et **GET** |

Le `POST /transmission/processus/{id}` est le plus sensible des deux : exposé,
il permettrait d'envoyer en paiement un état choisi depuis l'extérieur, en
contournant le circuit de validation. C'est l'un des chemins de double
transmission que CT-22 vérifie.

---

## 4. CORS : un seul endroit

La configuration vit **uniquement** sur la passerelle
(`spring.cloud.gateway.server.webflux.globalcors`), lue dans
`CORS_ALLOWED_ORIGINS` (défaut `http://localhost:5173`). Les sept services l'ont
perdue au même sprint : leur `SecurityConfig` porte désormais
`.cors(cors -> cors.disable())`, **désactivation explicite et non suppression
silencieuse**, avec son motif à côté. Deux configurations produiraient des
en-têtes en double, que le navigateur rejette avec un message peu explicite.

Deux réglages ne sont pas cosmétiques :

- **`add-to-simple-url-handler-mapping: true`** : les routes portant leur verbe,
  un préflight `OPTIONS` ne correspond à aucune route et serait refusé en 404
  avant d'être vu. Ce réglage fait répondre la passerelle elle-même au
  préflight, sans le router.
- **`exposed-headers: [Content-Disposition]`** : sans lui, le navigateur
  interdit au JavaScript de **lire** cet en-tête, même quand la requête aboutit.
  Le nom du fichier téléchargé retomberait sur un repli générique.

**Défaut corrigé au passage.** Seul le service Workflow exposait
`Content-Disposition` (ajouté au rattrapage post-7F.6 pour le document signé).
Le service Reporting ne l'exposait pas, alors que `exporterRapport` lit le même
en-tête : le nom des exports PDF et Excel retombait **toujours** sur le repli
générique `rapport-rations.pdf`. Jamais constaté, le téléchargement réel
n'ayant pas été cliqué au Sprint 7F.7. La centralisation le corrige pour tous
les services d'un seul geste, ce qui est l'argument même de la centralisation.

---

## 5. Jeton : la passerelle transmet, elle ne valide pas

Les sept services restent des Resource Server OAuth2 : ils valident le jeton
eux-mêmes. La passerelle ne le valide pas à leur place, ne le remplace pas, ne
le lit pas.

**Décision (étape 6, tranchée par l'utilisateur) : une requête sans jeton est
routée, et c'est le service qui répond 401.** La passerelle ne fait aucun
contrôle d'authentification.

Motifs :

- Un seul endroit décide qui entre. L'adresse du realm, l'audience et les
  règles de validation ne vivent qu'à un endroit, donc ne peuvent pas diverger.
- Un accès direct à un service reste protégé. Si la passerelle devenait le seul
  point de contrôle, contourner la passerelle contournerait toute la sécurité.
- Une validation à la passerelle ferait tomber **tout** le module d'un coup le
  jour où elle ne joindrait plus Keycloak.
- Elle enverrait par ailleurs l'utilisateur dans une impasse connue : le
  frontend ne reconnecte **pas** sur un 401 quand un jeton était joint
  (`apiClient.ts`, Sprint 7F.3, pour éviter une boucle sans fin). Un désaccord
  de configuration sur la seule passerelle produirait donc un écran d'erreur
  sans message utile, et le défaut serait logé là où personne ne regarde.

Prix assumé : une requête sans jeton traverse le réseau interne pour rien. Le
cas est rare, le frontend renouvelant le jeton 30 secondes avant son expiration.

Écartées : la vérification de simple présence de l'en-tête (ce n'est pas une
sécurité, n'importe quelle valeur passerait, et elle bloquerait une future route
publique sans explication), et la validation complète à la passerelle.

**Vérifié en réel** : un jeton de 1237 caractères obtenu de Keycloak rend, à
travers la passerelle, une réponse **identique octet pour octet** à celle du
service appelé en direct. Une altération quelconque de l'en-tête ferait échouer
la vérification de signature. Les rôles traversent intacts (un agent est refusé
en `403 ACCES_REFUSE` sur un endpoint ADMIN, l'ARH passe), et le compte de
contrôle `thomas_ndzana`, sans profil local, rend bien
`403 UTILISATEUR_NON_HABILITE` (invariant du Sprint 0.4).

---

## 6. Ce que le frontend connaît désormais

Une seule variable, `VITE_API_BASE_URL`, valant `http://localhost:8080/api` en
développement. Les six variables par service du Sprint 7F.4 ont disparu, et
`creerClientApi` n'est plus exportée : un appel à une adresse de service ne
compile plus, au lieu de rester un oubli silencieux. Les huit modules d'API
partagent le même client Axios, donc les mêmes intercepteurs.

La variable est **exigée** : absente, Axios prendrait l'origine de la page pour
base et chaque appel partirait vers le serveur de développement du frontend, qui
rendrait une page HTML en 404 — une erreur réseau sans rapport avec la cause.

---

## 7. Démarrage local

```
# registre (facultatif : seulement si EUREKA_ENABLED=true)
mvn -pl registry spring-boot:run

# passerelle
mvn -pl gateway spring-boot:run
```

Le frontend n'appelant plus que la passerelle, **la passerelle doit désormais
tourner** pour que l'application fonctionne. C'est un changement par rapport au
Sprint 7F, où elle était inutile
(`docs/GUIDE_LANCEMENT_ET_VERIFICATIONS.md`, mis à jour).
