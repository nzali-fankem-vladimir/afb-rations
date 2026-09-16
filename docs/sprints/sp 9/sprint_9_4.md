# SPRINT 9.4

## Documentation et clôture du projet

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Compléter la documentation, vérifier la conformité globale et clôturer le projet |
| **Livrable** | README, documentation d'API, guide utilisateur, dossier de livraison |
| **Durée** | Une journée |
| **Prérequis** | Sprint 9.3 validé et commité |
| **Sprint suivant** | Aucun. Fin du projet de développement. |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Documentation (étapes 2-5) | Sonnet | Moyen |
| Conformité globale et clôture (étapes 6-7) | Opus | Élevé |

**Changement manuel à l'étape 6.** Le contrôle de conformité finale confronte le code livré à l'ensemble des spécifications : Opus effort élevé.

## 2. Outil de cartographie

Utilisation obligatoire à l'étape 6.

```
py -3.14 -m graphify update .
```

## 3. Contexte

Dernier sous-sprint du projet. Le module fonctionne, il est testé, mesuré et déployable. Il reste à le rendre reprenable par quelqu'un d'autre.

C'est un enjeu réel sur ce projet. Le module a été construit sur plusieurs semaines, avec des dizaines de décisions prises en cours de route. Beaucoup ont été consignées dans CLAUDE.md au fil des clôtures de sprint, mais le fichier s'adresse à un assistant de développement, pas à un exploitant ni à un utilisateur.

Ce sous-sprint produit ce qui manque, et vérifie une dernière fois que le module livré correspond aux spécifications validées.

## 4. Objectifs

- README complet à la racine du dépôt
- Documentation d'API accessible et exacte
- Guide utilisateur par rôle
- Dossier de livraison rassemblant les éléments d'exploitation
- Contrôle de conformité globale
- Clôture du projet et liste des points restants

## 5. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis CLAUDE.md integralement. Confirme en 5 lignes ce que
fait le module, comment il est structure, et ce qui reste en attente.

CONTEXTE DE CETTE SESSION : Sprint 9.4, documentation et cloture. Le
module est fonctionnel, teste et deployable. On le rend reprenable.
SERVICE CONCERNE : tous, en documentation.

METHODE DE TRAVAIL :
- Un document a la fois. Tu montres, j'approuve, tu continues.
- La documentation decrit ce qui EXISTE, pas ce qui etait prevu.
- Si tu constates un ecart entre une specification et le code livre,
  tu me le signales plutot que de documenter l'un ou l'autre.

PREMIERE ACTION : propose le plan du README. Il s'adresse a un
developpeur qui reprend le projet sans l'avoir connu. Montre-moi le
plan avant de rediger.
```

### Étape 2. README

```
Redige le README a la racine :

- Objet du module, en quelques lignes comprehensibles par un
  non-specialiste.
- Perimetre, et surtout ce qui est hors perimetre : la production des
  ecritures comptables et l'impact CBS.
- Architecture : les SEPT services -- service Audit compris --, la
  passerelle, le registre, et la bibliotheque rations-audit-commun
  (qui n'est pas un service), leurs responsabilites.
- Stack technique et versions.
- Demarrage en developpement : prerequis, commandes, ordre.
- Structure du depot.
- Ou trouver quoi : specifications, guides de sprint, decisions,
  procedures.

Renvoie aux documents existants plutot que de recopier leur contenu.
```

### Étape 3. Documentation d'API

```
Verifie et complete la documentation Springdoc de chaque service :

- Les vingt-quatre endpoints du contrat d'api documentes.
- Pour chacun : role requis, parametres, codes de retour, exemple de
  reponse.
- Les endpoints internes marques comme tels.

Compare la documentation generee au contrat d'api produit en phase
documentaire. Tout ecart signale soit un endpoint non conforme, soit
une documentation incomplete : dis-moi lequel avant de corriger.

Precise dans le README ou consulter cette documentation.
```

### Étape 4. Guide utilisateur

```
Redige un guide utilisateur, organise par role :

- Agent d'unite : declencher une periode (deux dates, cycle
  hebdomadaire), saisir, soumettre, corriger apres retour, ouvrir un
  etat complementaire -- la regularisation est ouverte.
- Chef d'Unite : examiner, valider, retourner.
- Directeur Reseau : idem, avec le seuil -- et TOUS les etats
  complementaires, qui lui parviennent quel que soit leur montant.
- Analyste RH : grilles, suivi, rapports, journal d'audit.
- Directrice RH : validation des grilles, journal d'audit.
- Administrateur : habilitations, journal d'audit.

(Verifier au moment de rediger : les parametres systeme n'avaient
aucun ecran au 16 septembre 2026, faute d'endpoint d'ecriture -- ils
se modifiaient par UPDATE en base. Le journal d'audit est ouvert a
ARH, DRH et ADMIN, pas a l'administrateur seul.)

Explique aussi les refus les plus frequents et ce qu'ils signifient :
doublon, grille indisponible, separation des taches, unicite
inter-etats. Ce sont eux qui generent le plus de questions.

Un utilisateur doit pouvoir se debrouiller sans formation prealable.
```

### Étape 5. Dossier de livraison

```
Rassemble dans docs/livraison les elements necessaires a
l'exploitation :

- Procedure de deploiement (Sprint 8.3).
- Procedure de publication des images (Sprint 8.2).
- Liste des variables d'environnement, avec leur role.
- Mesures de performance (Sprint 9.3).
- Resultat du controle de securite (Sprint 9.3).
- Cahier de recette renseigne (Sprint 9.2).
- Liste des points en attente DSI et metier.

Cree un index de ce dossier, listant chaque document et son objet.
```

### Étape 6. Contrôle de conformité globale

**Étape en Opus, effort élevé.**

```
Dernier controle du projet. Confronte le code livre a l'ensemble des
specifications validees.

1. Les quinze regles de gestion : chacune est-elle implementee, et
   ou ?
2. Les dix-neuf user stories : chacune est-elle couverte ?
3. Les vingt-quatre endpoints du contrat d'api : existent-ils tous,
   et aucun de plus ?
4. Le dictionnaire de donnees : le schema livre y correspond-il,
   champ par champ, en tenant compte des migrations posterieures ?
5. Les dix erreurs interdites de CLAUDE.md section 15 : aucune n'est
   presente ?

Presente le resultat sous forme de tableau. Pour tout ecart, indique
s'il s'agit d'un manque, d'un ajout non prevu, ou d'une divergence
assumee et documentee.

Ne corrige rien avant que nous ayons la liste complete.
```

### Étape 7. Clôture du projet

```
Finalise :

1. Mets a jour CLAUDE.md : etat final du module, decisions du Sprint
   9, retrait des points desormais tranches.
2. Mets a jour le planning : tous les sprints termines.
3. Redige la note de cloture : ce qui a ete livre, ce qui reste en
   attente, ce qui a ete ecarte du perimetre et pourquoi.

La note de cloture doit permettre a la DSI et au metier de savoir
exactement ou en est le module, sans avoir a lire l'ensemble de la
documentation.
```

## 6. Fichiers à créer

| Chemin | Nature |
|---|---|
| `README.md` | Racine du dépôt |
| `docs/guide-utilisateur.md` | Par rôle |
| `docs/livraison/index.md` | Index du dossier |
| `docs/livraison/variables-environnement.md` | Liste et rôle |
| `docs/conformite-finale.md` | Tableau de contrôle |
| `docs/note-cloture.md` | Note de clôture |
| `CLAUDE.md` | État final |
| Planning | Tous sprints terminés |

## 7. Commandes terminal

Vérification finale complète :

```bash
cd afb-rations/backend
mvn clean test
mvn verify -P integration-tests

cd ../frontend
npm run build

cd ../infra/docker
docker compose build
docker compose up -d
docker compose ps
```

Vérification de la documentation d'API :

```bash
curl http://localhost:8080/api/identite/swagger-ui.html
```

## 8. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn clean test` | BUILD SUCCESS |
| Tests d'intégration | Tous passants |
| Build frontend | Réussi |
| Composition Docker | Tous conteneurs sains |
| Documentation d'API | Vingt-quatre endpoints documentés |
| Quinze règles de gestion | Toutes implémentées et localisées |
| Dix-neuf user stories | Toutes couvertes |
| Schéma conforme au dictionnaire | Vérifié |
| Dix erreurs interdites | Aucune présente |
| README permettant un démarrage | Testé en suivant les instructions |

## 9. Points de vigilance

- **Documenter ce qui existe, pas ce qui était prévu.** Une documentation décrivant un comportement différent du code livré est pire que pas de documentation : elle induit en erreur.
- Le README doit permettre un démarrage effectif. Le tester en suivant ses propres instructions sur un environnement propre est le seul moyen de savoir s'il est complet.
- Le guide utilisateur doit expliquer les refus. Ce sont eux qui génèrent les appels au support, pas les parcours nominaux.
- Le contrôle de conformité peut révéler des écarts assumés, décidés en cours de route et documentés. Ce ne sont pas des anomalies, mais ils doivent apparaître comme des divergences explicites.
- La note de clôture s'adresse à la DSI et au métier, pas à un développeur. Elle doit être lisible sans connaissance technique du module.
- Les points en attente ne disparaissent pas parce que le projet se termine. Les lister clairement évite qu'ils soient découverts en production.

## 10. Critères de validation

| Critère | Statut attendu |
|---|---|
| README complet, démarrage testé | Vérifié |
| Documentation d'API exacte et complète | Vérifié |
| Guide utilisateur par rôle, refus expliqués | Fait |
| Dossier de livraison rassemblé et indexé | Fait |
| Contrôle de conformité globale réalisé | Fait |
| Écarts qualifiés : manque, ajout, divergence assumée | Fait |
| Aucune des dix erreurs interdites | Vérifié |
| CLAUDE.md à l'état final | Fait |
| Planning à jour, tous sprints terminés | Fait |
| Note de clôture rédigée | Fait |

## 11. Commit

```bash
git add .
git commit -m "sprint-9.4: documentation et cloture du projet

- Readme, documentation d'api et guide utilisateur par role
- Dossier de livraison rassemble et indexe
- Controle de conformite globale sur regles, stories, endpoints et schema
- Note de cloture et etat final du module

Refs: cloture du projet"
```

---

**Fin du Sprint 9.4, du Sprint 9 et du projet de développement.**

Le module de paiement des rations et du transport de la garde armée est livré. Les points restant en attente auprès de la DSI et du métier figurent dans la note de clôture.
