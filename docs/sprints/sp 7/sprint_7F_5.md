# SPRINT 7F.5

## Écrans de validation hiérarchique

*Module Paiement des Rations et du Transport de la Garde Armée — Frontend*

| | |
|---|---|
| **Objet** | Parcours du Chef d'Unité et du Directeur Réseau : examen, validation, retour motivé |
| **Livrable** | Écran de validation, affichage de l'aiguillage, retour avec motif, reprise côté agent |
| **Durée** | Une journée |
| **Prérequis** | Sprint 7F.4 validé et commité |
| **Sprint suivant** | 7F.6, grilles tarifaires et administration |

## Réutilisation du projet DOTTEL

Source : `[CHEMIN_PROJET_DOTTEL]`

Le principe d'un écran de validation avec examen puis décision se transpose. Deux différences de fond : DOTTEL enchaîne trois validations RH successives sans condition, ce module en enchaîne deux avec un **aiguillage conditionnel au montant**. Et DOTTEL n'a pas de séparation des tâches, ce module en a une, qui peut refuser une validation à un utilisateur pourtant habilité.

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Écran de validation et aiguillage (étapes 2-4) | Sonnet | Moyen |
| Retour motivé et reprise (étapes 5-6) | Sonnet | Moyen |

## 2. Outil de cartographie

```
py -3.14 -m graphify update .
```

## 3. Contexte

Les deux niveaux de validation partagent le même écran : le contrat d'API expose un endpoint unique, le niveau étant déterminé côté serveur. L'interface n'a donc pas à distinguer les deux rôles dans son fonctionnement, seulement dans les dossiers qu'elle présente.

Deux comportements du backend doivent apparaître clairement à l'écran. **L'aiguillage** : après validation du Chef d'Unité, le serveur indique si le dossier est clôturé ou transféré au Directeur Réseau, et le seuil appliqué. Le validateur doit voir ce qu'il a déclenché. **La séparation des tâches** : un refus à ce titre n'est pas un défaut d'habilitation, le message doit l'expliquer autrement.

## 4. Objectifs

- Liste des dossiers en attente, filtrée selon le rôle et la portée
- Écran d'examen avec l'état consolidé et les signatures déjà apposées
- Validation avec affichage du résultat d'aiguillage
- Retour motivé, avec motif obligatoire
- Affichage du motif côté agent et reprise du dossier

## 5. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis CLAUDE.md, section 7 pour le workflow et section 6
pour les regles RG-08, RG-10, RG-11 et RG-12. Confirme en 3 lignes
l'aiguillage au seuil et la regle de retour.

CONTEXTE DE CETTE SESSION : Sprint 7F.5, ecrans de validation. Les
ecrans de saisie existent depuis le Sprint 7F.4.

Tu as acces en lecture au projet DOTTEL :
[CHEMIN_PROJET_DOTTEL]

Reprends le principe de son ecran de validation, mais deux
differences comptent : ce module a un aiguillage conditionnel au
montant, et une separation des taches qui peut refuser une validation
a un utilisateur pourtant habilite.

SERVICE CONCERNE : frontend, contre le service Workflow.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : cree le module d'appel a l'api de validation :
fonctions typees pour la validation et le retour, types derives du
contrat d'api section 5. La reponse de validation contient le
resultat d'aiguillage et le seuil applique : type-les fidelement.
Montre le fichier.
```

### Étape 2. Liste des dossiers en attente

```
Cree l'ecran listant les dossiers a traiter par l'utilisateur
courant :

- Pour un Chef d'Unite : les etats en attente de son niveau, sur son
  unite.
- Pour un Directeur Reseau : les etats en attente de son niveau, sur
  son perimetre.

Colonnes : periode, unite, montant total, date de soumission,
agent ayant soumis.

Le montant total est une information decisive : c'est lui qui
determinera l'aiguillage. Mets-le en evidence sans le surcharger de
couleur, la charte reservant le rouge aux accents.

Montre le fichier.
```

### Étape 3. Écran d'examen

```
Cree l'ecran d'examen d'un dossier :

- L'etat consolide, avec le detail par journee et le total.
- Les signatures deja apposees, avec leur acteur et leur date.
- Un acces au document pdf genere.
- Deux actions : valider, retourner.

Montre le fichier.
```

### Étape 4. Validation et aiguillage

```
Branche l'action de validation.

La reponse du serveur indique le resultat d'aiguillage et le seuil
applique. Affiche-le explicitement au validateur : selon le cas, que
le dossier est cloture et transmis a la comptabilite, ou qu'il est
transfere au Directeur Reseau parce que le montant depasse le seuil.

Un validateur doit comprendre ce que sa decision a declenche. Un
simple message de succes ne suffit pas.

Traite aussi le refus pour separation des taches : il ne s'agit pas
d'un defaut d'habilitation mais d'une regle de controle interne. Le
message doit expliquer que l'utilisateur est deja intervenu sur ce
dossier. Enrichis la table de correspondance des codes d'erreur.

Montre les modifications.
```

### Étape 5. Retour motivé

```
Ajoute l'action de retour :

- Une saisie de motif, obligatoire. Le bouton de confirmation reste
  inactif tant que le motif est vide ou compose uniquement d'espaces.
- Apres retour, un message rappelant que le dossier revient a l'agent
  d'unite, y compris si le retour vient du Directeur Reseau.

Ce dernier point evite un malentendu : un Directeur Reseau pourrait
croire que son retour renvoie au Chef d'Unite.

Montre les modifications.
```

### Étape 6. Reprise côté agent

```
Complete les ecrans de saisie du Sprint 7F.4 pour traiter le retour :

- Un etat au statut retourne s'affiche distinctement dans la liste
  des processus de l'agent.
- Le motif du retour est visible, avec son auteur et sa date.
- L'agent peut modifier ses lignes et resoumettre.

Verifie que le parcours complet fonctionne : soumission, retour
motive, correction, resoumission, validation.

Montre les modifications.
```

## 6. Critères de validation

| Élément | Statut attendu |
|---|---|
| Liste filtrée selon le rôle et la portée | Vérifié |
| Montant total mis en évidence | Vérifié |
| Signatures antérieures affichées | Vérifié |
| Résultat d'aiguillage explicite après validation | Vérifié |
| Seuil appliqué affiché | Vérifié |
| Refus pour séparation des tâches expliqué distinctement | Vérifié |
| Motif obligatoire, bouton inactif si vide | Vérifié |
| Message rappelant le retour vers l'agent | Vérifié |
| Motif visible côté agent | Vérifié |
| Parcours complet retour, correction, resoumission | Vérifié |

## 7. Points de vigilance

- **Le validateur doit voir ce que sa décision a déclenché.** Un dossier clôturé et transmis à la comptabilité n'est pas la même chose qu'un dossier transféré au niveau supérieur : le message doit les distinguer.
- Le refus pour séparation des tâches est le message le plus déroutant du module. Un utilisateur habilité, sur son propre périmètre, se voit refuser une action : sans explication claire, il croira à un défaut de droits ou à une panne.
- Rappeler que le retour va à l'agent, jamais au niveau intermédiaire. C'est RG-11, et elle contredit l'intuition.
- Ne pas afficher le seuil comme une donnée figée dans l'interface. Il vient de la réponse du serveur, et il est modifiable par l'administrateur.
- Un motif d'espaces n'est pas un motif. Le contrôle côté interface doit anticiper le refus du serveur.

## 8. Commit

```bash
git add .
git commit -m "sprint-7F.5: ecrans de validation hierarchique

- Liste des dossiers en attente filtree par role et portee
- Validation affichant le resultat d'aiguillage et le seuil applique
- Retour motive rappelant le retour vers l'agent
- Reprise et resoumission cote agent

Refs: US-08 a US-11, RG-08, RG-10, RG-11, RG-12"
```

---

**Fin du Sprint 7F.5** — en attente de validation avant le Sprint 7F.6
