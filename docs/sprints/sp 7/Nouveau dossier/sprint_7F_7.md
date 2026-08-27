# SPRINT 7F.7

## Suivi, reporting, régularisation et clôture du Sprint 7F

*Module Paiement des Rations et du Transport de la Garde Armée — Frontend*

| | |
|---|---|
| **Objet** | Écrans de suivi et de rapports, parcours de régularisation, clôture du frontend |
| **Livrable** | Suivi multicritère, exports, ouverture d'état complémentaire pilotée par drapeau, clôture du sprint |
| **Durée** | Une à deux journées |
| **Prérequis** | Sprint 7F.6 validé et commité |
| **Sprint suivant** | 8.1, passerelle et déploiement |

## Réutilisation du projet DOTTEL

Source : `[CHEMIN_PROJET_DOTTEL]`

Les écrans de suivi et de tableau de bord de DOTTEL fournissent les patrons de présentation. Le parcours de régularisation n'a en revanche aucun équivalent : il est propre à ce module.

## Point mis à jour : les écrans de régularisation se construisent toujours

La version précédente de ce guide subordonnait la construction des écrans de régularisation à la réalisation préalable du Sprint 6bis. Ce n'est plus le cas : le Sprint 6bis produit désormais son code derrière un drapeau de fonctionnalité, décrit dans `docs/dispositifs-provisoires.md`, et non plus derrière une condition de démarrage.

**Les étapes 5 et 6 de ce sous-sprint se réalisent donc systématiquement**, que le Sprint 6bis ait ou non été mené à ce stade, à une seule condition : que les endpoints du service Workflow qu'elles consomment existent. Si le Sprint 6bis n'a pas encore été réalisé au moment d'exécuter ce sous-sprint, ces deux étapes sont à reporter jusqu'à ce qu'il le soit, sans que cela change le reste du sous-sprint.

Le frontend interroge lui-même l'état du drapeau au chargement, et masque ou affiche l'entrée de menu en conséquence : c'est ce mécanisme qui remplace l'ancienne condition de démarrage.

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Suivi et exports (étapes 2-4) | Sonnet | Moyen |
| Fonctionnalités actives et régularisation (étapes 5-6) | Opus | Élevé |
| Clôture (étapes 7-8) | Sonnet | Moyen |

**Deux changements manuels.** Passer en Opus pour la lecture du drapeau et la régularisation, qui touchent à la règle empêchant le double paiement, puis revenir en Sonnet.

## 2. Outil de cartographie

Utilisation obligatoire à l'étape 7, pour la clôture du Sprint 7F.

```
py -3.14 -m graphify update .
```

## 3. Contexte

Dernier sous-sprint du frontend. Il complète le parcours de l'Analyste RH avec le suivi et les rapports, puis ajoute le parcours de régularisation, désormais construit systématiquement mais affiché conditionnellement.

Un point d'ergonomie sur la régularisation : le refus pour unicité inter-états est le message le plus délicat du module. L'agent tente de rattraper un bénéficiaire et le système refuse, en indiquant que la prestation figure déjà dans un autre état de la période. Sans explication précise, l'agent ne comprendra pas pourquoi son rattrapage est bloqué.

Un second point d'ergonomie, propre à cette version du sprint : si la fonctionnalité n'est pas encore ouverte par le métier, l'agent ne doit même pas voir l'entrée de menu qui y mène. Un menu visible mais menant à un refus systématique serait plus déroutant qu'un menu absent.

## 4. Objectifs

- Écran de suivi multicritère des demandes
- Écran d'historique d'un dossier
- Écran de rapports avec exports PDF et Excel
- Lecture des fonctionnalités actives au chargement de l'application
- Écran d'ouverture d'un état complémentaire, affiché seulement si le drapeau est ouvert
- Vérification de bout en bout du frontend et clôture du Sprint 7F

## 5. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis CLAUDE.md, section 11 pour le contrat d'api du
service Reporting, section 7 pour l'etat complementaire, et
docs/dispositifs-provisoires.md section 1.4 sur le comportement
frontend attendu. Confirme en 3 lignes les quatre endpoints de
reporting et la maniere dont le frontend doit traiter le drapeau
RATTRAPAGE_ACTIF.

CONTEXTE DE CETTE SESSION : Sprint 7F.7, dernier sous-sprint du
frontend. Suivi, rapports et regularisation. Les ecrans de
regularisation se construisent systematiquement, mais leur AFFICHAGE
depend du drapeau RATTRAPAGE_ACTIF lu depuis
GET /parametres/fonctionnalites.

Tu as acces en lecture au projet DOTTEL :
[CHEMIN_PROJET_DOTTEL]

Ses ecrans de suivi fournissent les patrons de presentation. Le
parcours de regularisation n'a aucun equivalent chez lui.

SERVICE CONCERNE : frontend, contre les services Reporting et
Workflow.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : cree le module d'appel a l'api de reporting :
fonctions typees pour la recherche, l'historique, le rapport et
l'export. L'export retourne un fichier binaire : verifie comment le
client axios le gere avant de typer la fonction. Montre le fichier.
```

### Étape 2. Suivi multicritère

```
Cree l'ecran de suivi des demandes :

- Filtres combinables : periode, unite, session, nature,
  beneficiaire.
- Tableau pagine, avec le composant du Sprint 7F.1.
- Colonnes : periode, unite, montant total, statut d'avancement,
  statut d'integration comptable.

Le statut d'integration comptable merite une attention : il indique
si l'etat transmis a ete pris en charge, integre ou rejete par la
comptabilite. Un etat cloture depuis longtemps sans accuse est un
signal, et l'interface doit le rendre visible.

Une recherche sans resultat n'est pas une erreur : affiche un etat
vide explicite.

Montre le fichier.
```

### Étape 3. Historique d'un dossier

```
Cree l'ecran d'historique :

Chronologie des etapes : niveau, acteur, decision, motif de retour
eventuel, date et heure.

Un dossier retourne puis resoumis passe plusieurs fois par le meme
niveau : la chronologie doit montrer tous les passages, pas seulement
le dernier. C'est ce qui donne sa valeur au controle interne.

Montre le fichier.
```

### Étape 4. Rapports et exports

```
Cree l'ecran de rapports, reserve a l'Analyste RH :

- Selection de la periode et de l'agence.
- Affichage du rapport consolide.
- Deux boutons d'export, pdf et excel, declenchant le telechargement
  du fichier retourne par le serveur.

Traite le cas d'une periode sans donnees : le rapport est vide, ce
n'est pas une erreur.

Le total affiche a l'ecran doit etre identique a celui des fichiers
exportes. Ne recalcule rien cote interface : affiche ce que le
serveur retourne.

Montre le fichier.
```

### Étape 5. Fonctionnalités actives et masquage conditionnel

**Étape en Opus, effort élevé.**

```
Cree l'appel a GET /parametres/fonctionnalites, fait une seule fois
au chargement de l'application, apres la resolution du profil
utilisateur du Sprint 7F.3.

Expose le resultat dans un contexte, au meme niveau que le contexte
d'authentification, accessible a toute l'application.

Branche le masquage sur ce contexte :
1. L'entree de menu de la sidebar menant a l'ouverture d'un etat
   complementaire (Sprint 7F.2) n'apparait que si rattrapageActif
   vaut vrai.
2. La route correspondante est elle-meme protegee : un acces direct
   par l'url doit rediriger si la fonctionnalite est fermee, pas
   seulement si l'utilisateur n'a pas le role requis.

Rappel du principe du Sprint 7F.3 : masquer un lien ne suffit jamais,
il faut aussi proteger la route. C'est le meme principe applique ici
au drapeau plutot qu'au role.

Montre le contexte puis les modifications de la sidebar et des
routes.
```

### Étape 6. Ouverture d'un état complémentaire et saisie de régularisation

**Reste en Opus, effort élevé.**

```
Cree l'ecran d'ouverture d'un etat complementaire, reserve a l'agent
d'unite et visible seulement si le drapeau est ouvert :

- Selection d'un etat cloture de son unite.
- Saisie d'un motif d'ouverture, obligatoire.
- Confirmation.

L'ecran doit indiquer clairement que l'etat d'origine ne sera pas
modifie : il reste cloture avec ses signatures, et un nouvel etat
distinct est ouvert sur la meme periode.

Il n'y a NI formulaire de reclamation, NI liste de beneficiaires a
rattraper : le signalement du beneficiaire est externe au systeme, et
aucune liste de beneficiaires attendus n'existe dans ce processus.

Si l'agent parvient malgre tout a soumettre une demande alors que le
drapeau vient d'etre ferme entre-temps, le backend refusera avec
FONCTIONNALITE_NON_OUVERTE : ajoute ce code a la table de
correspondance des erreurs du Sprint 7F.1, avec un message
correspondant.

La saisie sur un etat complementaire utilise ensuite les ecrans du
Sprint 7F.4, sans modification, a un ajout pres : le traitement du
refus pour unicite inter-etats. Le message est le plus delicat du
module. Il doit indiquer que la prestation figure deja dans un autre
etat de la periode, en citant le beneficiaire, la journee, la nature
et la session.

Un agent qui tente un rattrapage legitime et se voit refuser doit
comprendre immediatement pourquoi, sans quoi il croira a un
dysfonctionnement.

Montre les fichiers et les modifications.
```

### Étape 7. Vérification de bout en bout

**Retour en Sonnet, effort moyen.**

```
Lance la cartographie et verifie :
- qu'aucun fichier .jsx ni aucun any ne subsiste
- qu'aucun formulaire de mot de passe n'existe
- qu'aucun montant n'est calcule cote interface
- qu'aucun ecran d'enrolement, d'eligibilite ou d'import de
  beneficiaires n'a ete cree
- qu'aucun residu de route, de role ou de vocabulaire dottel ne
  subsiste
- que l'entree de menu de regularisation est bien absente quand le
  drapeau est ferme, et presente quand il est ouvert

Liste les ecarts sans les corriger.

Puis fais le parcours complet a l'ecran, avec les six utilisateurs de
test, drapeau ferme d'abord : declenchement, saisie, soumission,
validation, aiguillage, cloture, suivi, rapport. Verifie que le menu
de regularisation est absent.

Ouvre ensuite le drapeau en base de test et rejoue le parcours de
regularisation : ouverture d'etat complementaire, saisie, refus
d'unicite sur une combinaison deja payee, saisie acceptee sur une
combinaison nouvelle.
```

### Étape 8. Clôture du Sprint 7F

```
Mets a jour CLAUDE.md avec les decisions prises pendant le Sprint 7F :
1. Le lieu de conservation du jeton cote navigateur (7F.3).
2. La politique de rafraichissement du jeton (7F.3).
3. La disposition retenue pour l'ecran de saisie journaliere (7F.4).
4. La maniere de signaler qu'une grille est sans effet (7F.6).
5. La table de correspondance des codes d'erreur et ses messages,
   incluant desormais FONCTIONNALITE_NON_OUVERTE.
6. Le mecanisme de masquage conditionnel fonde sur les
   fonctionnalites actives (7F.7).

Propose les ajouts section par section.
```

## 6. Critères de validation

| Élément | Statut attendu |
|---|---|
| Suivi multicritère avec statut d'intégration visible | Vérifié |
| Recherche sans résultat traitée comme un état vide | Vérifié |
| Historique montrant tous les passages | Vérifié |
| Totaux identiques entre écran et exports | Vérifié |
| Exports PDF et Excel téléchargeables | Vérifié |
| Fonctionnalités actives lues au chargement | Vérifié |
| Menu de régularisation masqué si drapeau fermé | Vérifié |
| Route de régularisation protégée, pas seulement masquée | Vérifié |
| État d'origine annoncé comme non modifié | Vérifié |
| Aucun formulaire de réclamation ni liste de bénéficiaires | Vérifié |
| Refus d'unicité inter-états explicite et circonstancié | Vérifié |
| Code `FONCTIONNALITE_NON_OUVERTE` traité avec un message clair | Vérifié |
| Aucun `.jsx`, aucun `any`, aucun mot de passe | Vérifié |
| Aucun résidu DOTTEL | Vérifié |
| Parcours complet validé, drapeau fermé puis ouvert | Vérifié |
| CLAUDE.md complété des décisions du Sprint 7F | Fait |

## 7. Points de vigilance

- **Masquer un lien ne suffit jamais.** Le principe posé au Sprint 7F.3 pour les rôles s'applique identiquement au drapeau de fonctionnalité : la route doit être protégée indépendamment de l'affichage du menu.
- **Le refus pour unicité inter-états est le message le plus délicat du module.** L'agent agit de bonne foi pour rattraper un oubli et se heurte à un blocage. Sans message précis citant les quatre éléments, il conclura à un dysfonctionnement.
- Le statut d'intégration comptable est une information de suivi sous-estimée. Un état clôturé depuis longtemps sans accusé signale un problème en aval, et seul le suivi peut le révéler.
- Ne recalculer aucun total côté interface. L'écran doit afficher ce que le serveur retourne, sinon les chiffres finiraient par diverger de ceux des exports.
- L'historique doit montrer les passages répétés au même niveau. C'est la trace des allers-retours, et elle a une valeur de contrôle interne.
- Les résidus du projet source sont le risque de fin de sprint : une route, un rôle ou un terme de DOTTEL laissé dans le code passe inaperçu et sème la confusion plus tard.

## 8. Commit

```bash
git add .
git commit -m "sprint-7F.7: suivi, reporting et regularisation pilotee par drapeau

- Suivi multicritere avec statut d'integration comptable
- Historique complet des passages et rapports exportables
- Fonctionnalites actives lues au chargement, menu masque si fermees
- Route de regularisation protegee independamment du masquage
- Refus d'unicite inter-etats explicite
- Cloture du sprint 7F

Refs: US-15 a US-18, RG-15, docs/dispositifs-provisoires.md"
```

---

**Fin du Sprint 7F.7 et du Sprint 7F.** Le frontend est complet, y compris le parcours de régularisation, dont l'affichage reste subordonné au drapeau `RATTRAPAGE_ACTIF`. En attente de validation avant le Sprint 8, passerelle et déploiement.
