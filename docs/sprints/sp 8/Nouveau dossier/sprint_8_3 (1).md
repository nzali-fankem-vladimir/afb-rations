# SPRINT 8.3

## Déploiement Kubernetes et clôture du Sprint 8

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Produire les manifests de déploiement et clôturer la mise en plateforme |
| **Livrable** | Manifests versionnés référençant un fichier de valeurs centralisé, script de garde-fou, procédure de déploiement, clôture du sprint |
| **Durée** | Une à deux journées |
| **Prérequis** | Sprint 8.2 validé et commité |
| **Sprint suivant** | 9.1, tests d'intégration |

## Point mis à jour : centralisation des valeurs en attente

La version précédente de ce guide demandait de semer des valeurs de substitution explicites directement dans chaque manifest. Cette approche est remplacée par le dispositif décrit dans `docs/dispositifs-provisoires.md`, section 2 : un fichier unique `infra/k8s/valeurs-environnement.yml`, référencé par les neuf manifests, et un script `scripts/verifier-valeurs.sh` qui bloque tout déploiement en production tant qu'une valeur y reste marquée `A_CONFIRMER_DSI_`.

L'objectif ne change pas : ne jamais écrire une valeur qui aurait l'air définitive. Le moyen change : au lieu de répéter neuf fois la même mention, un seul fichier centralise tout, ce qui rend le remplacement à la réponse DSI immédiat et vérifiable.

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Fichier central et manifests (étapes 2-5) | Opus | Moyen |
| Script de garde-fou, procédure et clôture (étapes 6-8) | Sonnet | Moyen |

**Changement manuel à l'étape 6.**

## 2. Outil de cartographie

Utilisation obligatoire à l'étape 8, pour la clôture du Sprint 8.

```
py -3.14 -m graphify update .
```

## 3. Contexte

Dernier sous-sprint de la mise en plateforme. Les images existent ; il reste à décrire leur déploiement.

Une précaution s'impose d'emblée. Plusieurs éléments nécessaires à un déploiement réel restent en attente de la DSI, et le registre `docs/points-en-attente.md` les liste sous les références D-01 à D-09 : le namespace dédié, l'adresse du registre Harbor, les conventions de nommage, la méthode de gestion des secrets, l'URL du realm de production, les adresses Kafka, le nommage des topics en environnement partagé, les URL publiques, et le pipeline de livraison.

Ce sous-sprint produit donc des manifests **complets et versionnés**, qui référencent un fichier central où vivent, seules, les valeurs en attente. L'objectif est que le déploiement soit prêt le jour où les réponses arrivent, sans avoir à reprendre neuf fichiers.

## 4. Objectifs

- Fichier `infra/k8s/valeurs-environnement.yml` centralisant toutes les valeurs variables
- Manifests de déploiement pour les neuf composants, référençant ce fichier
- Configuration et secrets séparés du code
- Sondes de disponibilité et de vivacité correctement distinguées
- Exposition externe de la passerelle et du frontend
- Script `scripts/verifier-valeurs.sh` bloquant un déploiement en production tant qu'une valeur reste marquée
- Procédure de déploiement documentée
- Clôture du Sprint 8

## 5. Règles concernées

Aucune règle métier. Document maître sections 7.6 et 10.

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis CLAUDE.md, le document maitre section 10, et
docs/dispositifs-provisoires.md section 2 sur le dispositif de
centralisation des valeurs DSI. Confirme en 3 lignes le principe du
fichier central et du prefixe A_CONFIRMER_DSI_.

CONTEXTE DE CETTE SESSION : Sprint 8.3, deploiement Kubernetes. Les
images existent depuis le Sprint 8.2.

Toute valeur non confirmee par la DSI vit dans
infra/k8s/valeurs-environnement.yml, prefixee A_CONFIRMER_DSI_.
AUCUNE valeur de ce type ne doit etre ecrite en dur dans un manifest
individuel : les manifests referencent le fichier central.

SERVICE CONCERNE : infrastructure.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Aucun secret en clair dans un manifest versionne.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : cree infra/k8s/valeurs-environnement.yml, conforme
au modele de docs/dispositifs-provisoires.md section 2.3 : namespace,
registre, prefixe de nommage, url du realm, brokers et topics kafka,
urls d'exposition, methode de gestion des secrets. Montre le fichier
avant de creer le moindre manifest de deploiement.
```

### Étape 2. Configuration et secrets référençant le fichier central

```
Cree les manifests de configuration :

- Une configuration commune : urls internes, noms de topics, origines
  autorisees, profil actif. Les valeurs encore en attente sont lues
  depuis infra/k8s/valeurs-environnement.yml, pas reecrites.
- Une configuration par service quand elle diverge.
- Les secrets, dans des objets distincts : identifiants de base,
  identifiants Kafka le cas echeant.

La methode de gestion des secrets elle-meme (D-04 au registre) n'est
pas encore arretee par la DSI : produis les manifests de secrets avec
des cles vides ou des valeurs de substitution issues du fichier
central, jamais de valeur inventee.

Montre les fichiers.
```

### Étape 3. Déploiements

```
Cree un manifest de deploiement par composant : les six services, la
passerelle, le registre, le frontend.

Pour chacun :
- image issue du registre Harbor, dont l'adresse vient du fichier
  central, avec une etiquette de version, pas une etiquette flottante
- variables d'environnement lues depuis la configuration et les
  secrets
- ressources demandees et limites, proposees et justifiees
- nombre de replicas, propose et justifie service par service

Deux points a trancher avec moi :
1. Combien de replicas pour le service Transmission ? Il consomme un
   topic Kafka : plusieurs instances traitant le meme accuse
   supposent que l'idempotence du Sprint 5.2 tienne.
2. Confirme-moi le choix d'une etiquette de version plutot qu'une
   etiquette flottante.

Montre les fichiers un par un.
```

### Étape 4. Sondes

```
Configure les sondes de chaque deploiement.

Distingue bien les deux :
- la sonde de vivacite indique si le conteneur doit etre redemarre
- la sonde de disponibilite indique s'il peut recevoir du trafic

Les confondre produit un comportement piegeux : un service lent a
demarrer serait redemarre en boucle, sans jamais aboutir.

Le service Workflow depend du service Saisie, le service Saisie du
service Grilles : leur sonde de disponibilite doit-elle refleter ces
dependances ? Presente les consequences, j'arbitre.

Montre les modifications.
```

### Étape 5. Exposition

```
Cree les manifests d'exposition :

- Un service interne par composant, pour la communication entre pods.
- Une exposition externe pour la passerelle et le frontend
  uniquement, aux urls issues du fichier central.

Les six services applicatifs et le registre ne sont PAS exposes a
l'exterieur : ils ne sont joignables que depuis l'interieur du
cluster. Une exposition accidentelle permettrait de contourner la
passerelle et donc les controles centralises.

Montre les fichiers.
```

### Étape 6. Script de garde-fou

**Passage en Sonnet, effort moyen.**

```
Cree scripts/verifier-valeurs.sh, conforme au modele de
docs/dispositifs-provisoires.md section 2.4 :

- Compte les occurrences du prefixe A_CONFIRMER_DSI_ dans
  infra/k8s/valeurs-environnement.yml.
- En developpement et en recette, affiche un avertissement mais
  laisse passer.
- En production, refuse et retourne un code d'erreur si au moins une
  occurrence subsiste.

Rends-le executable et montre-moi un essai sur l'etat actuel du
fichier, qui doit encore contenir des valeurs en attente a ce stade
du projet.

Montre le script.
```

### Étape 7. Procédure de déploiement

```
Redige dans docs/ la procedure de deploiement :

- Prerequis : acces au cluster, au registre, secrets renseignes,
  fichier de valeurs a jour.
- Premiere etape systematique : executer
  scripts/verifier-valeurs.sh avec l'environnement cible.
- Ordre d'application des manifests.
- Verifications apres deploiement : pods sains, sondes vertes,
  parcours fonctionnel.
- Procedure de retour arriere.

Renvoie vers docs/points-en-attente.md pour le detail des points D-01
a D-09 encore ouverts au moment de la redaction.
```

### Étape 8. Clôture du Sprint 8

```
Lance la cartographie et verifie :
- qu'aucun secret en clair ne figure dans un fichier versionne
- qu'aucune valeur A_CONFIRMER_DSI_ n'apparait ailleurs que dans
  infra/k8s/valeurs-environnement.yml
- qu'aucune adresse de service n'est en dur dans le frontend
- que seuls la passerelle et le frontend sont exposes
- que les endpoints internes ne sont pas routes par la passerelle

Puis mets a jour CLAUDE.md avec les decisions du Sprint 8 :
1. La table de routage et la liste des endpoints internes (8.1).
2. L'usage du registre en developpement local (8.1).
3. Le comportement de la passerelle sur une requete sans jeton (8.1).
4. Le mecanisme d'injection de l'url dans le frontend (8.2).
5. Le nombre de replicas par service et l'etiquetage des images
   (8.3).
6. Le traitement des dependances entre services dans les sondes
   (8.3).
7. Le mecanisme de centralisation des valeurs DSI et le script de
   garde-fou (8.3).

Mets aussi a jour le planning : Sprint 8 termine, et
docs/points-en-attente.md : etat actuel de chaque point D.
```

## 7. Fichiers à créer

| Chemin | Nature |
|---|---|
| `infra/k8s/valeurs-environnement.yml` | Fichier central des valeurs variables |
| `infra/k8s/config/` | Configuration commune et par service, référençant le fichier central |
| `infra/k8s/secrets/` | Objets de secrets |
| `infra/k8s/deployments/` | Neuf manifests de déploiement |
| `infra/k8s/services/` | Services internes |
| `infra/k8s/ingress/` | Exposition externe, passerelle et frontend |
| `scripts/verifier-valeurs.sh` | Script de garde-fou |
| `docs/procedure-deploiement.md` | Procédure, renvoi au registre des points en attente |
| `CLAUDE.md` | Décisions du Sprint 8 |

## 8. Commandes terminal

Vérification de la syntaxe des manifests, sans cluster :

```bash
cd afb-rations/infra/k8s
kubectl apply --dry-run=client -f config/
kubectl apply --dry-run=client -f deployments/
kubectl apply --dry-run=client -f services/
```

Exécution du script de garde-fou :

```bash
chmod +x scripts/verifier-valeurs.sh
./scripts/verifier-valeurs.sh dev
./scripts/verifier-valeurs.sh recette
./scripts/verifier-valeurs.sh production
```

Attendu : succès en développement et en recette, échec en production tant que des valeurs restent marquées.

Recherche de secrets en clair et de valeurs dispersées :

```bash
grep -rni "password\|secret\|token" infra/k8s/ | grep -v "secretKeyRef\|valeurs-environnement.yml"
grep -rln "A_CONFIRMER_DSI_" infra/k8s/ | grep -v "valeurs-environnement.yml"
```

Attendu : aucun résultat sur les deux recherches, la seconde confirmant que le marqueur ne vit que dans le fichier central.

Vérification des expositions externes :

```bash
grep -rn "kind: Ingress" infra/k8s/
```

Attendu : passerelle et frontend uniquement.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| Validation syntaxique des manifests | Sans erreur |
| Aucun secret en clair | Vérifié |
| Marqueurs `A_CONFIRMER_DSI_` uniquement dans le fichier central | Vérifié |
| Script de garde-fou : dev et recette | Laisse passer |
| Script de garde-fou : production | Bloque tant que des marqueurs subsistent |
| Étiquettes de version, non flottantes | Vérifié |
| Sondes de vivacité et de disponibilité distinctes | Vérifié |
| Exposition externe limitée à deux composants | Vérifié |
| Ressources et limites définies sur chaque déploiement | Vérifié |
| Procédure de déploiement rédigée, renvoyant au registre | Fait |

## 10. Points de vigilance

- **Aucune valeur `A_CONFIRMER_DSI_` en dehors du fichier central.** Si elle réapparaît dans un manifest individuel, le remplacement à la réponse DSI redevient une chasse au trésor. La recherche de contrôle de l'étape 8 doit être exécutée systématiquement.
- Confondre les deux sondes produit un comportement difficile à diagnostiquer : un service lent à démarrer redémarre en boucle sans jamais aboutir.
- Une étiquette flottante rend un déploiement non reproductible.
- Seuls la passerelle et le frontend sont exposés. Un service applicatif joignable de l'extérieur permettrait de contourner la passerelle, donc les contrôles centralisés du Sprint 8.1.
- Le nombre de replicas du service Transmission est lié à l'idempotence du Sprint 5.2. Plusieurs instances consommant le même topic ne sont sûres que si le traitement l'est.
- Le script de garde-fou n'est utile que s'il est exécuté systématiquement avant tout déploiement, en particulier en production. L'intégrer en première étape de la procédure documentée, pas comme une vérification optionnelle.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Fichier central créé, valeurs en attente correctement préfixées | Fait |
| Manifests référençant le fichier central, aucune duplication | Vérifié |
| Configuration et secrets séparés | Fait |
| Aucun secret en clair versionné | Vérifié |
| Décisions sur replicas et étiquetage tranchées | Fait |
| Sondes correctement distinguées | Vérifié |
| Traitement des dépendances dans les sondes arbitré | Fait |
| Exposition limitée à la passerelle et au frontend | Vérifié |
| Script de garde-fou fonctionnel sur les trois environnements | Vérifié |
| Manifests valides syntaxiquement | Vérifié |
| Procédure documentée, renvoyant au registre des points en attente | Fait |
| CLAUDE.md et registre des points en attente mis à jour | Fait |

## 12. Commit

```bash
git add .
git commit -m "sprint-8.3: manifests kubernetes avec valeurs dsi centralisees

- Fichier unique des valeurs en attente, manifests qui le referencent
- Script de garde-fou bloquant la production tant qu'une valeur subsiste
- Sondes de vivacite et de disponibilite distinctes
- Exposition externe limitee a la passerelle et au frontend
- Procedure de deploiement renvoyant au registre des points en attente

Refs: document maitre sections 7.6 et 10, docs/dispositifs-provisoires.md"
```

---

**Fin du Sprint 8.3 et du Sprint 8.** Le module est prêt à être déployé en développement et en recette. Le déploiement en production reste bloqué par le script de garde-fou tant que les points D-01 à D-09 du registre ne sont pas résolus. En attente de validation avant le Sprint 9, recette.
