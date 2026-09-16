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

Source, en lecture seule : `D:\stage afriland\formation spécialisée DSI\projet de gestion des absences\implementation\afb-dottel-mm\frontend`

Fichiers utiles : `src/pages/workflow/ProcessusDetailPage.jsx`, `RetournerProcessusModal.jsx`,
`src/components/ui/VoirMotifModal.jsx`, `ConfirmDialog.jsx`. L'état des lieux complet est dans
le guide **7F.1**, section « Réutilisation du projet DOTTEL ».

Le principe d'un écran de validation avec examen puis décision se transpose. Deux différences de fond : DOTTEL enchaîne trois validations RH successives sans condition, ce module en enchaîne deux avec un **aiguillage conditionnel au montant**. Et DOTTEL n'a pas de séparation des tâches, ce module en a une, qui peut refuser une validation à un utilisateur pourtant habilité.

## Ce qui a changé, et ce qui manque côté backend

*Vérifié le 16 septembre 2026 contre les DTO et contrôleurs réels, et contre une validation
jouée de bout en bout le même jour.* Ce guide a été écrit avant les Sprints 5.1, 6bis.1 et
6bis.2 ; il sous-estimait ce que la réponse de validation contient, et supposait trois
fonctions que le backend n'offre pas.

### La réponse de validation porte plus que ce que ce guide annonçait

`POST /processus/{id}/validation` rend `ValidationResponse` :
`idProcessus`, `statut`, `montantTotal`, `aiguillage`, `seuilApplique`, `pieceJointe`, `etape`,
**`transmission`**.

**`aiguillage` a TROIS valeurs, plus `null`** — et non deux :

| Valeur | Situation | `seuilApplique` | Ce que l'écran doit dire |
| --- | --- | --- | --- |
| `SOUS_SEUIL_CLOTURE_DIRECTE` | DA, montant ≤ seuil | **renseigné** | clôturé, envoyé à la comptabilité |
| `ENVOI_DIRECTEUR_RESEAU` | DA, montant > seuil | **renseigné** | transféré au DR **parce que le montant dépasse le seuil** |
| `COMPLEMENTAIRE_ENVOI_DIRECTEUR_RESEAU` | DA, **état complémentaire** | **`null`** | transféré au DR **parce que c'est une régularisation** — quel que soit le montant |
| `null` | **validation par le DR** | **`null`** | clôturé, envoyé à la comptabilité — **aucune comparaison n'a eu lieu** |

**Le piège** : écrire « transféré au DR parce que le montant dépasse le seuil » pour toute
issue `EN_ATTENTE_DR`. Sur un complémentaire de 4 500 FCFA, le message serait **faux** — et un
chef d'unité lirait qu'un seuil de 100 000 XAF a été franchi par 4 500 FCFA. Vérifié en réel :
un complémentaire rend `COMPLEMENTAIRE_ENVOI_DIRECTEUR_RESEAU` et `seuilApplique: null`.

**N'afficher le seuil que s'il est renseigné.** Jamais « seuil : 0 », jamais « seuil : null ».
Un seuil nul signifie que **le seuil n'a pas été lu**, pas qu'il vaut zéro — ce qui, pour le
contrôle interne, est une information différente.

**`transmission` : le seul endroit où un humain apprend qu'un paiement n'est pas parti.**
Bloc `{ transmis, motif, tentatives }`, **nul quand la validation ne clôture pas**. Quand elle
clôture, `transmis` peut valoir **`false`** — broker injoignable, charge incomplète. La clôture
est acquise quand même (CLAUDE.md §9.1), et **aucune reprise automatique n'existe** : le
valideur est la seule personne qui peut l'apprendre. Un « dossier validé » affiché sur un
`transmis: false` ferait disparaître l'information à l'endroit exact où elle devait apparaître.

### Trois fonctions que ce guide supposait, et que le backend n'offre pas

**1. La liste « en attente de mon niveau » n'a pas de filtre par statut.**
`GET /reporting/demandes` accepte `dateDebut`, `dateFin`, `codeUnite`, `nature`, `session`,
`beneficiaire`, `page`, `size` — **pas `statut`**. Filtrer côté interface serait **faux** : la
pagination est faite par le serveur, et la page 1 pourrait ne contenir aucun dossier en attente
pendant que la page 3 en contient. **Le manque est petit** : l'endpoint interne que le Reporting
appelle, `GET /processus/recherche`, accepte **déjà** `statut`. Il suffit de le relayer.

**2. Les colonnes « date de soumission » et « agent ayant soumis » n'existent pas.**
`DemandeResponse` porte `idProcessus`, `dateDebut`, `dateFin`, `codeUnite`, `typeProcessus`,
`montantTotal`, `statut`, `transmisComptabilite`, `statutIntegration`, `dateCreation`. Ces deux
informations vivent dans l'historique (`GET /reporting/processus/{id}/historique`), dossier par
dossier.

**3. Aucun endpoint ne sert le PDF signé.** `pieceJointe.cheminFichier` est un **chemin relatif
côté serveur** (`2026/09/etat-rations-00002-20260901-p6005.pdf`), pas une URL. Le seul PDF
téléchargeable du module est le rapport d'activité du Reporting.

**Ces trois points sont à trancher en ouverture de session** (étape 1). Un sous-sprint frontend
qui les découvrirait en cours de route inventerait des contournements — un filtre côté client
faux, ou une URL construite à la main sur un chemin serveur.

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

Tu as acces en LECTURE SEULE au frontend de reference DOTTEL :
D:\stage afriland\formation spécialisée DSI\projet de gestion des absences\implementation\afb-dottel-mm\frontend
N'ECRIS JAMAIS dans ce depot.

Reprends le principe de son ecran de validation, mais deux
differences comptent : ce module a un aiguillage conditionnel au
montant, et une separation des taches qui peut refuser une validation
a un utilisateur pourtant habilite.

SERVICE CONCERNE : frontend, contre le service Workflow.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : AVANT TOUT CODE, lis la section "Ce qui a change,
et ce qui manque cote backend" de ce guide, puis presente-moi les
TROIS MANQUES qu'elle decrit, avec pour chacun les options et ta
recommandation. Attends ma reponse.

Pistes a examiner, sans les tenir pour acquises :
1. Filtre par statut : relayer "statut" dans GET /reporting/demandes
   vers GET /processus/recherche, qui l'accepte deja. Petit ajout
   backend. Ecarte le filtrage cote client, faux avec une pagination
   serveur.
2. Date et agent de soumission : les retirer de la LISTE et les
   afficher sur l'ECRAN D'EXAMEN, depuis l'historique. Aucun ajout
   backend, et pas un appel par ligne de liste.
3. PDF signe : un endpoint de telechargement a creer cote Workflow,
   OU l'acces au PDF reporte. S'il est cree : il doit verifier la
   portee d'acces unite par unite, ET PUBLIER UN EVENEMENT D'AUDIT --
   CLAUDE.md §9.2 trace "ce qui fait sortir un fichier du systeme",
   precedent de l'export du Reporting au Sprint 6.3. Un PDF signe qui
   sort sans trace serait le seul fichier du module dans ce cas.

Toute modification backend decidee ici sort du perimetre frontend :
elle se fait avec ses tests et sa verification reelle, et se signale
dans le resume du sprint.

ENSUITE SEULEMENT : cree le module d'appel a l'api de validation :
fonctions typees pour la validation et le retour. Derive les types des
DTO Java (ValidationResponse.java), pas du seul contrat d'api : le
contrat est ignore par git et ne connait pas
COMPLEMENTAIRE_ENVOI_DIRECTEUR_RESEAU. Type "aiguillage" comme une
union des TROIS valeurs, NULLABLE, "seuilApplique" comme nullable, et
"transmission" comme un bloc nullable. Montre le fichier.
```

### Étape 2. Liste des dossiers en attente

```
Cree l'ecran listant les dossiers a traiter par l'utilisateur
courant :

- Pour un Chef d'Unite : les etats en attente de son niveau, sur son
  unite.
- Pour un Directeur Reseau : les etats en attente de son niveau, sur
  son perimetre.

Colonnes : periode (ses deux bornes), unite, type (normal ou
complementaire), montant total -- et, selon l'arbitrage de l'etape 1,
date et agent de soumission.

Signale visuellement un etat COMPLEMENTAIRE : il arrive toujours chez
le Directeur Reseau, quel que soit son montant, et un DR qui ne le
saurait pas s'etonnerait de recevoir un dossier de 4 500 FCFA.

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
- Un acces au document pdf genere -- SELON L'ARBITRAGE DE L'ETAPE 1.
  Ne construis jamais une url a partir de pieceJointe.cheminFichier :
  c'est un chemin sur le serveur, pas une adresse.
- Deux actions : valider, retourner.

Montre le fichier.
```

### Étape 4. Validation et aiguillage

```
Branche l'action de validation.

La reponse du serveur indique le resultat d'aiguillage et le seuil
applique. Affiche-le explicitement au validateur, selon les QUATRE
cas du tableau de la section "Ce qui a change" :
- SOUS_SEUIL_CLOTURE_DIRECTE : cloture, envoye a la comptabilite ;
- ENVOI_DIRECTEUR_RESEAU : transfere au DR, le montant depasse le seuil ;
- COMPLEMENTAIRE_ENVOI_DIRECTEUR_RESEAU : transfere au DR PARCE QUE
  C'EST UNE REGULARISATION -- ne parle PAS de seuil ;
- null (validation par le DR) : cloture, envoye a la comptabilite.

N'affiche le seuil QUE s'il est renseigne. Un seuil nul veut dire
qu'il n'a pas ete lu, pas qu'il vaut zero.

Un validateur doit comprendre ce que sa decision a declenche. Un
simple message de succes ne suffit pas.

TRAITE LE BLOC "transmission" quand il n'est pas nul :
- transmis = true : envoye a la comptabilite ;
- transmis = false : LE DOSSIER EST CLOTURE, MAIS IL N'EST PAS PARTI
  EN PAIEMENT. Affiche-le comme un avertissement franc, avec le motif,
  et dis qui prevenir. Aucune reprise automatique n'existe : si ce
  message est discret, personne d'autre ne l'apprendra.
Ne le presente pas comme un echec de la validation : la validation a
reussi. C'est sa consequence qui a echoue.

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
| Seuil appliqué affiché **seulement s'il est renseigné** | Vérifié |
| Trois valeurs d'aiguillage plus `null` traitées distinctement | Vérifié |
| Complémentaire : aucun message évoquant le seuil | Vérifié |
| `transmission.transmis = false` affiché comme un avertissement franc | Vérifié |
| Trois manques backend présentés et tranchés en ouverture | Fait |
| Aucune URL construite sur `cheminFichier` | Vérifié |
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
- **Un complémentaire monte au Directeur Réseau sans que le seuil soit lu** (Sprint 6bis.1). Tout message qui l'expliquerait par le montant serait faux, et faux sur la règle même qui commande le niveau d'approbation de la banque.
- **`transmis: false` est l'information la plus importante que cet écran puisse afficher.** La validation a réussi, le paiement n'est pas parti, et aucun mécanisme ne le rattrapera seul. La présenter en succès vert avec une note discrète, c'est la perdre.
- **Ne pas filtrer côté client une liste paginée par le serveur.** Le résultat semblerait juste sur un jeu d'essai de dix dossiers, et serait faux en production.
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
