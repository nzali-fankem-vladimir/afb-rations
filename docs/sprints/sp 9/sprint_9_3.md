# SPRINT 9.3

## Performance, sécurité et robustesse

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Vérifier les exigences non fonctionnelles et le comportement en situation dégradée |
| **Livrable** | Mesures de performance, contrôle de sécurité, tests de robustesse |
| **Durée** | Une journée |
| **Prérequis** | Sprint 9.2 validé et commité |
| **Sprint suivant** | 9.4, documentation et clôture du projet |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Performance (étapes 2-3) | Sonnet | Moyen |
| Sécurité et robustesse (étapes 4-6) | Opus | Élevé |

**Changement manuel à l'étape 4.**

## 2. Outil de cartographie

Utilisation obligatoire à l'étape 4, pour le contrôle de sécurité.

```
py -3.14 -m graphify update .
```

## 3. Contexte

La recette a vérifié le fonctionnel. Ce sous-sprint traite ce que le cahier des charges exige par ailleurs et qui ne se voit pas au déroulé des cas.

Trois exigences chiffrées y figurent : un temps de réponse de trois secondes en moyenne et cinq au maximum en charge normale, un traitement complet dans les vingt-quatre heures, et une disponibilité couvrant les heures d'ouverture de la banque.

S'y ajoutent les exigences de contrôle interne, dont plusieurs ont été implémentées au fil des sprints mais jamais vérifiées globalement : séparation des tâches, traçabilité, protection contre les accès non autorisés.

Enfin, un module réparti en six services expose une surface de défaillance qu'un monolithe n'a pas. Un service injoignable ne doit pas produire un comportement incompréhensible.

## 4. Objectifs

- Mesure des temps de réponse sur les opérations courantes
- Identification et traitement des points lents
- Contrôle de sécurité sur l'ensemble du module
- Vérification du comportement en situation dégradée
- Consignation des résultats

## 5. Exigences concernées

| Exigence | Cible |
|---|---|
| Temps de réponse des opérations courantes | 3 secondes en moyenne, 5 au maximum |
| Traitement complet d'un cycle | 24 heures à compter des validations |
| Séparation des tâches | Aucun cumul possible |
| Traçabilité | Toute opération sensible tracée |
| Protection des données | Aucun accès non autorisé |

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis CLAUDE.md et le document de specifications, section
des besoins non fonctionnels. Confirme en 3 lignes les exigences
chiffrees.

CONTEXTE DE CETTE SESSION : Sprint 9.3, exigences non fonctionnelles.
La recette fonctionnelle est passee au Sprint 9.2.
SERVICE CONCERNE : tous, en verification.

METHODE DE TRAVAIL :
- Tu proposes, je mesure, nous analysons ensemble.
- Une optimisation ne se fait pas sans mesure prealable.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : liste les operations a mesurer en priorite. Je vois
la saisie d'une ligne, qui appelle le service Grilles, la
consultation de l'etat consolide, qui traverse deux services, la
validation, qui lit un parametre et publie, et la recherche
multicritere, qui croise deux sources. Complete cette liste si tu
identifies autre chose, et propose une methode de mesure.
```

### Étape 2. Mesure des temps de réponse

```
Mets en place la mesure sur les operations listees.

Mesure dans des conditions realistes : environnement conteneurise,
jeu de donnees representatif d'un mois complet pour une unite, soit
plusieurs dizaines de lignes reparties sur une vingtaine de journees.

Un jeu de trois lignes ne revele rien : les problemes de performance
apparaissent avec le volume.

Reporte les mesures dans un tableau, avec la moyenne et le maximum
observes pour chaque operation.
```

### Étape 3. Traitement des points lents

```
Analysons les mesures ensemble.

Pour chaque operation depassant la cible, identifie la cause avant de
proposer une correction : appel reseau superflu, requete non
optimisee, absence d'index, agregation en memoire de donnees qui
pourraient etre filtrees en amont.

Deux points sont suspects par construction :
- la saisie d'une ligne dans un etat complementaire, qui ajoute
  l'appel de RG-15
- la recherche multicritere, qui croise deux sources sans jointure

Ne propose aucune optimisation sans avoir mesure ce qu'elle
ameliore.
```

### Étape 4. Contrôle de sécurité

**Étape en Opus, effort élevé.**

```
Deroule le controle de securite sur l'ensemble du module :

1. Aucun secret en clair dans le code, la configuration, les images
   ou les manifests.
2. Aucun mot de passe applicatif, aucune route de login.
3. Tous les endpoints proteges, sauf les sondes de sante.
4. Acces aux donnees par requetes parametrees, aucune concatenation
   de chaine utilisateur.
5. Validation des donnees entrantes sur tous les DTO.
6. Aucun endpoint interne expose par la passerelle.
7. Separation des taches effective, verifiee par un essai reel.
8. Refus d'acces traces.
9. Journal d'audit immuable.

Pour chaque point, dis-moi s'il est verifie ou en ecart, en citant le
fichier concerne. Ne corrige rien avant que nous ayons la liste
complete.
```

### Étape 5. Comportement en situation dégradée

```
Verifions le comportement du module quand une brique manque.

Pour chaque situation, arrete un service et observe :
1. Service Grilles arrete : que se passe-t-il a la saisie d'une
   ligne ?
2. Service Saisie arrete : que se passe-t-il a la consultation de
   l'etat consolide ?
3. Service Identite arrete : que se passe-t-il a la connexion et aux
   appels suivants ?
4. Kafka arrete : que se passe-t-il a la cloture d'un etat ?
5. Service Transmission arrete : que se passe-t-il a la cloture ?

Attendu dans tous les cas : un message comprehensible pour
l'utilisateur, aucune donnee incoherente laissee en base, et une
reprise possible une fois le service revenu.

Le cas 4 est le plus sensible : un etat cloture sans transmission
possible est le scenario identifie au Sprint 5.1. Verifie que la
decision prise alors s'applique effectivement.

Consigne les observations.
```

### Étape 6. Corrections

```
Corrige les ecarts de securite et les comportements degrades
inacceptables, par ordre de gravite :

1. Ecarts de securite touchant les donnees ou l'authentification.
2. Situations degradees laissant des donnees incoherentes.
3. Points lents depassant la cible.
4. Messages incomprehensibles en situation degradee.

Un commit par correction.
```

## 7. Fichiers à créer ou modifier

| Chemin | Nature |
|---|---|
| `docs/mesures-performance.md` | Tableau des mesures |
| `docs/controle-securite.md` | Résultat des neuf points |
| `docs/comportement-degrade.md` | Observations et décisions |
| Fichiers en écart | Corrections |

## 8. Commandes terminal

Recherche de secrets :

```bash
grep -rni "password\|secret\|apikey" backend/*/src/main --include=*.java --include=*.yml \
  | grep -v "secretKeyRef\|REMPLACER"
```

Recherche de concaténation dans les requêtes :

```bash
grep -rn "createQuery\|nativeQuery" backend/*/src/main/java --include=*.java
```

Vérification des endpoints publics :

```bash
grep -rn "permitAll" backend/*/src/main/java --include=*.java
```

Attendu : uniquement les sondes de santé.

Simulation d'une panne :

```bash
docker compose stop rations-grilles
```

Puis tenter une saisie de ligne à l'écran et observer.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| Temps de réponse des opérations courantes | Moyenne sous 3 secondes |
| Temps maximum observé | Sous 5 secondes |
| Saisie sur état complémentaire | Cible tenue malgré RG-15 |
| Recherche multicritère | Cible tenue |
| Neuf points de sécurité | Tous vérifiés |
| Séparation des tâches | Refus effectif en essai réel |
| Cinq situations dégradées | Message clair, aucune donnée incohérente |
| Reprise après retour du service | Possible dans les cinq cas |
| Clôture sans Kafka | Décision du Sprint 5.1 effectivement appliquée |

## 10. Points de vigilance

- **Mesurer avant d'optimiser.** Une optimisation décidée sur une intuition complexifie le code sans gain démontré, et peut dégrader ce qui fonctionnait.
- Mesurer sur un volume réaliste. Un mois complet représente plusieurs dizaines de lignes ; un jeu de trois lignes ne révèle aucun problème de performance.
- La clôture sans Kafka disponible est le scénario le plus dangereux du module : un état figé, jamais transmis, donc jamais payé, et rien ne le signale. La décision prise au Sprint 5.1 doit être vérifiée en conditions réelles, pas seulement en test unitaire.
- Un message incompréhensible en situation dégradée est une anomalie. L'utilisateur doit savoir qu'il s'agit d'un incident temporaire, pas d'un refus métier.
- Aucune donnée incohérente ne doit subsister après une panne. Une fiche rattachée à un processus inexistant, ou un état à moitié validé, se paierait bien plus tard.
- Les points de sécurité se vérifient, ils ne se supposent pas. Plusieurs ont été posés il y a plusieurs sprints et ont pu être contournés depuis sans que personne s'en aperçoive.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Opérations mesurées sur volume réaliste | Fait |
| Cibles de temps de réponse tenues | Vérifié |
| Optimisations justifiées par une mesure | Fait |
| Neuf points de sécurité vérifiés | Fait |
| Écarts de sécurité corrigés | Fait |
| Cinq situations dégradées éprouvées | Fait |
| Aucune donnée incohérente après panne | Vérifié |
| Reprise possible dans tous les cas | Vérifié |
| Résultats consignés dans `docs/` | Fait |

## 12. Commit

```bash
git add .
git commit -m "sprint-9.3: performance, securite et robustesse

- Mesures sur volume realiste et traitement des points lents
- Controle de securite en neuf points, ecarts corriges
- Comportement en situation degradee eprouve sur cinq scenarios
- Aucune donnee incoherente laissee apres panne

Refs: besoins non fonctionnels, exigences de controle interne"
```

---

**Fin du Sprint 9.3** — en attente de validation avant le Sprint 9.4
