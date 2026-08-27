# SPRINT 7F.4

## Écrans de saisie de l'agent d'unité

*Module Paiement des Rations et du Transport de la Garde Armée — Frontend*

| | |
|---|---|
| **Objet** | Parcours de l'agent : déclenchement, saisie journalière, consultation et soumission de l'état |
| **Livrable** | Écrans de saisie et de soumission, gestion des refus métier |
| **Durée** | Une à deux journées |
| **Prérequis** | Sprint 7F.3 validé et commité |
| **Sprint suivant** | 7F.5, écrans de validation |

## Réutilisation du projet DOTTEL

Source : `[CHEMIN_PROJET_DOTTEL]`

Le parcours métier diffère entièrement. DOTTEL repose sur un enrôlement, une vérification d'éligibilité et un déclenchement mensuel unique par l'Analyste RH. Ici, l'agent saisit **jour par jour**, sans enrôlement ni liste préalable de bénéficiaires.

Ce qui se reprend : les patrons de formulaire, la présentation des tableaux, le traitement des erreurs d'API. Ce qui ne se reprend pas : l'écran de vérification de matricule, l'écran de confirmation d'enrôlement et l'import Excel de bénéficiaires, qui n'existent pas dans ce module.

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Écran de saisie journalière (étapes 2-4) | Sonnet | Moyen |
| Consultation et soumission (étapes 5-6) | Sonnet | Moyen |

## 2. Outil de cartographie

```
py -3.14 -m graphify update .
```

## 3. Contexte

Premier domaine fonctionnel du frontend. L'écran de saisie journalière est le plus utilisé du module : un agent y passe plusieurs minutes chaque jour.

Trois comportements du backend structurent son ergonomie. Le montant est **calculé et retourné par le serveur** : le champ est en lecture seule et se remplit après enregistrement de la ligne. Le doublon et la grille indisponible produisent des **refus métier normaux**, pas des pannes : ils doivent s'afficher comme des messages compréhensibles, pas comme des erreurs techniques. Et l'ouverture d'une fiche est **idempotente** : rouvrir un jour déjà saisi affiche les lignes existantes, il ne faut donc pas vider l'écran à chaque ouverture.

## 4. Objectifs

- Écran de déclenchement et de sélection du processus mensuel
- Écran de saisie journalière avec calendrier et tableau de lignes
- Modification et suppression d'une ligne avant soumission
- Écran de consultation de l'état consolidé, avec soumission
- Traitement lisible des refus métier

## 5. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis CLAUDE.md, section 11 pour le contrat d'api du
service Saisie, et section 6 pour les regles RG-03 a RG-06. Confirme
en 3 lignes les cinq endpoints de saisie.

CONTEXTE DE CETTE SESSION : Sprint 7F.4, ecrans de saisie. Premier
domaine fonctionnel du frontend.

Tu as acces en lecture au projet DOTTEL :
[CHEMIN_PROJET_DOTTEL]

ATTENTION : le parcours metier est different. Ici il n'y a NI
enrolement, NI verification d'eligibilite, NI import de
beneficiaires. L'agent saisit jour par jour, librement. Ne transpose
aucun de ces ecrans.

SERVICE CONCERNE : frontend, contre les services Workflow et Saisie.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- TypeScript strict, aucun any.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : cree le module d'appel a l'api de saisie et de
processus : fonctions typees correspondant aux endpoints, types de
requete et de reponse derives du contrat d'api. Aucun composant
encore. Montre le fichier.
```

### Étape 2. Écran de processus

```
Cree l'ecran listant les processus de l'agent et permettant d'en
declencher un nouveau.

Il affiche, pour chaque processus : periode, unite, montant total,
statut. Le declenchement demande la periode et le code unite, le code
unite etant preselectionne d'apres le profil de l'utilisateur
courant.

Traite le refus de second processus normal sur une periode deja
couverte : c'est un cas normal, le message doit l'expliquer.

Montre le fichier.
```

### Étape 3. Écran de saisie journalière

```
Cree l'ecran central du module :

- Un calendrier de selection du jour.
- A la selection, appel d'ouverture de fiche. Si des lignes existent
  deja pour ce jour, elles s'affichent : l'ouverture est idempotente,
  ne vide jamais l'ecran.
- Un formulaire d'ajout de ligne : nature en liste deroulante, nom,
  prenom, numero de compte courant, code agence, session en liste
  deroulante.
- Le champ montant est en lecture seule et se renseigne apres
  enregistrement, avec la valeur retournee par le serveur.
- Un tableau des lignes du jour, avec sous-total.

Montre-moi la disposition envisagee avant d'ecrire le composant :
c'est l'ecran le plus utilise du module, sa forme merite d'etre
validee.
```

### Étape 4. Traitement des refus métier

```
Traite les deux refus les plus frequents de cet ecran :

- Doublon : le beneficiaire est deja saisi pour cette journee, cette
  nature et cette session. Le message doit citer ces quatre elements,
  pas se contenter d'un refus generique.
- Grille indisponible : aucun tarif n'est en vigueur pour cette
  combinaison. Le message doit orienter l'agent vers l'Analyste RH.

Ces deux cas ne sont pas des pannes : ne les presente pas comme des
erreurs techniques. Enrichis la table de correspondance des codes
d'erreur du Sprint 7F.1.

Montre les modifications.
```

### Étape 5. Modification et suppression

```
Ajoute la modification et la suppression d'une ligne, possibles
uniquement tant que l'etat n'est pas soumis.

Une modification de nature ou de session declenche une nouvelle
resolution du montant cote serveur : le montant affiche doit etre
rafraichi avec la valeur retournee, jamais recalcule cote interface.

La suppression demande une confirmation.

Quand l'etat n'est plus modifiable, les actions doivent etre
desactivees plutot que d'echouer a l'appel : l'agent doit comprendre
avant de cliquer.

Montre les modifications.
```

### Étape 6. Consultation et soumission

```
Cree l'ecran de consultation de l'etat mensuel consolide :

- Detail par journee, avec sous-totaux, et total du mois.
- Bouton de soumission.

Traite le refus pour etat incomplet : le backend retourne la liste
des manques, l'interface doit les afficher un par un, pas resumer par
un message generique.

Apres soumission reussie, l'etat n'est plus modifiable : l'ecran doit
le refleter immediatement.

Montre le fichier.
```

## 6. Critères de validation

| Élément | Statut attendu |
|---|---|
| Aucun écran d'enrôlement, d'éligibilité ou d'import | Vérifié |
| Ouverture de fiche non destructrice | Vérifié |
| Champ montant en lecture seule, renseigné par le serveur | Vérifié |
| Doublon affiché avec ses quatre éléments | Vérifié |
| Grille indisponible orientant vers l'ARH | Vérifié |
| Modification rafraîchissant le montant depuis le serveur | Vérifié |
| Actions désactivées sur un état non modifiable | Vérifié |
| Manques listés un par un en cas d'état incomplet | Vérifié |
| Parcours complet testé sur plusieurs journées | Vérifié |

## 7. Points de vigilance

- **Ne jamais recalculer un montant côté interface.** Même pour un affichage provisoire : la valeur affichée doit toujours venir du serveur, sinon l'agent pourrait voir un montant différent de celui enregistré.
- Rouvrir un jour déjà saisi ne vide pas l'écran. C'est le pendant côté interface de RG-05, tel qu'il a été précisé au Sprint 3.3.
- Un doublon et une grille manquante sont des situations métier normales. Les présenter comme des erreurs techniques déstabiliserait l'agent sans l'aider.
- Désactiver une action impossible vaut mieux que la laisser échouer. Un bouton actif qui produit une erreur est une mauvaise expérience.
- Le code agence est saisi avec le bénéficiaire, le code unité vient du processus. Ne pas demander deux fois la même chose ni les confondre dans le formulaire.

## 8. Commit

```bash
git add .
git commit -m "sprint-7F.4: ecrans de saisie de l'agent

- Ecran de processus et saisie journaliere avec calendrier
- Montant en lecture seule, toujours issu du serveur
- Refus metier affiches comme des cas normaux et circonstancies
- Consultation de l'etat consolide et soumission

Refs: US-03 a US-07, RG-03 a RG-06"
```

---

**Fin du Sprint 7F.4** — en attente de validation avant le Sprint 7F.5
