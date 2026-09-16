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

Source, en lecture seule : `D:\stage afriland\formation spécialisée DSI\projet de gestion des absences\implementation\afb-dottel-mm\frontend`

Fichiers utiles : `src/pages/workflow/DeclencherProcessusPage.jsx`, `ProcessusListPage.jsx`,
`ProcessusDetailPage.jsx`, et les composants `components/ui/` (formulaire, tableau, alerte).
L'état des lieux complet est dans le guide **7F.1**, section « Réutilisation du projet DOTTEL ».

Le parcours métier diffère entièrement. DOTTEL repose sur un enrôlement, une vérification d'éligibilité et un déclenchement mensuel unique par l'Analyste RH. Ici, l'agent saisit **jour par jour**, sans enrôlement ni liste préalable de bénéficiaires.

Ce qui se reprend : les patrons de formulaire, la présentation des tableaux, le traitement des erreurs d'API. Ce qui ne se reprend pas : l'écran de vérification de matricule, l'écran de confirmation d'enrôlement et l'import Excel de bénéficiaires (`pages/enrolement/`), qui n'existent pas dans ce module — ni `AjusterLignesModal.jsx` et `RecapitulatifResynchronisation.jsx`, qui répondent à un parcours DOTTEL sans équivalent ici.

**Attention au déclenchement de DOTTEL** : il repose sur un **mois**. Ici, la période est un **intervalle de dates** (voir la section suivante). Ne pas transposer un sélecteur mois/année.

## Ce qui a changé depuis la rédaction de ce guide

Ce guide a été écrit quand le module raisonnait en mois. **Le cycle de paiement est hebdomadaire** (point M-04), et la Maille 1 a remplacé le couple mois/année par un intervalle de dates. Le tableau complet des changements du backend est dans le guide **7F.1**.

| Pour ce sous-sprint | Conséquence |
| --- | --- |
| Période d'un état | **`dateDebut` et `dateFin`**, ISO 8601, **bornes incluses** — `dateFin` est le dernier jour, pas le premier de la suivante |
| Déclenchement `POST /processus` | demande deux dates, plus `codeUnite` ; **aucune durée imposée** |
| Unicité | deux états NORMAL d'une unité ne peuvent **pas se chevaucher, même partiellement** → `409 PROCESSUS_EXISTANT` |
| Liste des dossiers de l'agent | `GET /reporting/demandes` (ouvert à `AGENT_UNITE`) : `dateDebut`, `dateFin`, `codeUnite`, `nature`, `session`, `beneficiaire`, `page`, `size` |
| Doublons | **deux codes** : `DOUBLON_LIGNE` (RG-04) et `DOUBLON_INTER_ETATS` (RG-15) |

**Pourquoi aucune durée imposée.** L'intervalle de dates a été retenu précisément pour ne pas figer une cadence : le métier en a changé une fois. Un sélecteur qui forcerait « sept jours » réintroduirait dans l'interface le postulat que la décision vient de retirer du backend (CLAUDE.md §15).

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

- Écran de déclenchement et de sélection du processus de la période
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

Tu as acces en LECTURE SEULE au frontend de reference DOTTEL :
D:\stage afriland\formation spécialisée DSI\projet de gestion des absences\implementation\afb-dottel-mm\frontend
N'ECRIS JAMAIS dans ce depot.

ATTENTION : le parcours metier est different. Ici il n'y a NI
enrolement, NI verification d'eligibilite, NI import de
beneficiaires. L'agent saisit jour par jour, librement. Ne transpose
aucun de ces ecrans.

ATTENTION AUSSI a la periode : DOTTEL declenche sur un MOIS. Ce module
declenche sur un INTERVALLE de dates (dateDebut, dateFin, bornes
incluses), depuis la Maille 1. Lis la section "Ce qui a change depuis
la redaction de ce guide" avant tout fichier.

SERVICE CONCERNE : frontend, contre les services Workflow et Saisie.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- TypeScript strict, aucun any.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : cree le module d'appel a l'api de saisie et de
processus : fonctions typees correspondant aux endpoints, types de
requete et de reponse derives du contrat d'api. Aucun composant
encore. Montre le fichier.

LA SOURCE DE VERITE DES TYPES EST LE DTO JAVA DU BACKEND, pas le
contrat d'api. Deux raisons, verifiees le 16 septembre 2026 :
- le contrat (docs/initialisation projet/) est IGNORE PAR GIT : depuis
  un clone du depot, il est tout simplement absent ;
- il est a jour sur la periode (dateDebut, dateFin), mais il lui manque
  DOUBLON_INTER_ETATS, COMPLEMENTAIRE_ENVOI_DIRECTEUR_RESEAU,
  GET /parametres/fonctionnalites et motifRetour.
Lis backend/service-workflow/.../api/dto/ProcessusResponse.java,
DeclenchementProcessusRequest.java et backend/service-saisie/.../api/dto/.
Si le contrat est present, signale-moi tout ecart avec les DTO.
```

### Étape 2. Écran de processus

```
Cree l'ecran listant les processus de l'agent et permettant d'en
declencher un nouveau.

Il affiche, pour chaque processus : periode, unite, montant total,
statut. La liste vient de GET /reporting/demandes, ouvert a
AGENT_UNITE.

La periode s'affiche avec ses DEUX bornes ("du 07/09/2026 au
13/09/2026"), jamais sous la forme d'un mois : une semaine a cheval
sur deux mois n'a pas de mois.

Le declenchement demande DEUX DATES (debut et fin, bornes incluses) et
le code unite, preselectionne d'apres le profil de l'utilisateur
courant. Utilise le selecteur de periode du Sprint 7F.1. N'IMPOSE
AUCUNE DUREE : le cycle est hebdomadaire aujourd'hui, mais
l'intervalle a ete choisi pour ne pas figer une cadence.

Traite le refus 409 PROCESSUS_EXISTANT : deux etats NORMAL d'une unite
ne peuvent pas se CHEVAUCHER, meme partiellement. C'est un cas normal.
AFFICHE LE MESSAGE DU BACKEND, ne le remplace pas par un texte fixe :
il depend du statut de l'etat en conflit. Sur un etat encore ouvert,
il dit de le rejoindre ; sur un etat CLOTURE, il nomme l'etat
complementaire et la date a partir de laquelle ouvrir. Un message
fige dirait "rejoignez ce dossier" sur un dossier clos, qui ne se
rejoint pas.

Montre le fichier.
```

### Étape 3. Écran de saisie journalière

```
Cree l'ecran central du module :

- Un calendrier de selection du jour, BORNE a la periode de l'etat
  (dateDebut a dateFin incluses).

  Pourquoi le borner : une ligne datee hors de la periode de son etat
  n'est refusee qu'a la SOUMISSION (LIGNE_HORS_PERIODE). Entre-temps
  elle existe, et elle ne fait l'objet du controle d'unicite RG-15 que
  pour sa propre journee. La base de developpement en portait un
  exemplaire reel (une fiche du 01/01/2020 dans l'etat d'aout 2026).
  Borner le calendrier empeche l'erreur a la source au lieu de la
  signaler des jours plus tard.
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
Traite les refus les plus frequents de cet ecran :

- Doublon sur la journee (409 DOUBLON_LIGNE, RG-04) : le beneficiaire
  est deja saisi pour cette journee, cette nature et cette session,
  SUR CETTE FICHE. Le message doit citer ces quatre elements, pas se
  contenter d'un refus generique.
- Doublon inter-etats (409 DOUBLON_INTER_ETATS, RG-15) : la prestation
  a deja ete servie DANS UN AUTRE ETAT de la meme unite et de la meme
  periode. Rare sur un etat normal, frequent en regularisation (7F.7),
  qui reutilise cet ecran. Le message du backend NOMME L'ETAT EN
  CONFLIT : affiche-le tel quel. Ne le fusionne pas avec DOUBLON_LIGNE
  -- l'agent chercherait sur sa fiche une ligne qui ne s'y trouve pas.
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
Cree l'ecran de consultation de l'etat consolide de la periode :

- Detail par journee, avec sous-totaux, et total de la periode.
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
| `DOUBLON_LIGNE` et `DOUBLON_INTER_ETATS` distingués, état en conflit affiché | Vérifié |
| Déclenchement sur deux dates, aucune durée imposée | Vérifié |
| Refus de chevauchement affichant le message du backend | Vérifié |
| Calendrier de saisie borné à la période de l'état | Vérifié |
| Période affichée par ses deux bornes, jamais comme un mois | Vérifié |
| Types dérivés des DTO Java, non du seul contrat d'API | Fait |
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
- **Les DTO Java font foi, pas le contrat d'API.** Le contrat est ignoré par git — absent de tout clone du dépôt — et, là où il est présent, il manque de quatre ajouts récents (`DOUBLON_INTER_ETATS`, `COMPLEMENTAIRE_ENVOI_DIRECTEUR_RESEAU`, `GET /parametres/fonctionnalites`, `motifRetour`). Un type faux ne se voit pas : **TypeScript ne vérifie pas une réponse HTTP à la compilation**, il fait confiance à la déclaration.
- **`dateFin` est incluse.** Une semaine du lundi 7 au dimanche 13 se déclenche avec `dateFin = 13`, pas 14. Une borne exclusive glissée dans l'interface ferait chevaucher chaque semaine avec la suivante, et le backend refuserait **toutes** les semaines après la première.
- **Ne pas afficher le compte de charge depuis la réponse de `POST /processus`** : il y vaut `null`, alors que `GET /processus/{id}` rend la valeur.

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
