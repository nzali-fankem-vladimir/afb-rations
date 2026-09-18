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

Source, en lecture seule : `D:\stage afriland\formation spécialisée DSI\projet de gestion des absences\implementation\afb-dottel-mm\frontend`

Fichiers utiles : `src/pages/reporting/DashboardPage.jsx`, `HistoriquePage.jsx`, `AuditPage.jsx`,
`src/components/ui/DataTable.jsx`, `MessageListeVide.jsx`. L'état des lieux complet est dans le
guide **7F.1**, section « Réutilisation du projet DOTTEL ».

Les écrans de suivi et de tableau de bord de DOTTEL fournissent les patrons de présentation.

> *Correction d'une version antérieure de ce guide*, qui affirmait que « le parcours de
> régularisation n'a aucun équivalent » chez DOTTEL. **DOTTEL a un mode rattrapage** (son Sprint
> MM.11, `pages/workflow/DeclencherProcessusPage.jsx`). Vérifié le 16 septembre 2026.

**Il ne se transpose pas, et la raison est de fond.** Les deux modèles sont incompatibles :

| | DOTTEL | Ce module |
| --- | --- | --- |
| Forme | un **drapeau `rattrapage`** posé sur un processus ordinaire du même mois | un **état `COMPLEMENTAIRE` distinct**, qui référence l'état d'origine clôturé |
| Qui est rattrapé | **calculé** : « seuls les bénéficiaires non payés seront… » | **saisi librement** par l'agent, jour par jour |
| Ce qui le rend possible | une **liste d'enrôlés** à comparer aux payés | **rien** — il n'existe aucune liste de bénéficiaires attendus |
| Ce qui empêche le double paiement | la comparaison à la liste | **RG-15**, contrôlée à chaque ligne saisie |

Le calcul « bénéficiaires non payés » de DOTTEL **suppose l'enrôlement**, interdit dans ce module
(CLAUDE.md §15). Le transposer réintroduirait l'enrôlement par la porte de la régularisation.
**Ne reprendre de ce mode que la présentation** — le bandeau signalant qu'on travaille sur une
période déjà payée.

## Point mis à jour : le Sprint 6bis est terminé, et la régularisation est ouverte

*Mis à jour le 16 septembre 2026.* La version précédente de cette section envisageait que le
Sprint 6bis ne soit pas encore réalisé. **Il l'est** : ouverture contrôlée d'un état
complémentaire (6bis.1) et unicité inter-états RG-15 (6bis.2), vérifiées de bout en bout. Et
**`RATTRAPAGE_ACTIF` vaut `true` depuis la migration V8 du Workflow**, le métier ayant validé.

**Les étapes 5 et 6 se réalisent donc sans condition.** Tous les endpoints qu'elles consomment
existent.

**Le masquage conditionnel reste nécessaire**, pour une autre raison qu'à l'origine : le drapeau
n'est plus l'attente d'une décision, c'est la **commande d'urgence**. Il se referme par un simple
`UPDATE`, sans redéploiement, et l'interface doit alors cesser d'offrir la régularisation.

Référence : `docs/dispositifs_provisoires.md` (avec un **tiret bas** — la version précédente de ce
guide écrivait `dispositifs-provisoires.md`, un chemin qui n'existe pas), section **1.4**,
« Comportement côté frontend ».

## Ce qui a changé depuis la rédaction de ce guide

| Pour ce sous-sprint | Conséquence |
| --- | --- |
| Filtres du suivi et des rapports | **`dateDebut`, `dateFin`**, plus de `periode=AAAA-MM` |
| Rapports : l'**unité**, pas l'agence | le paramètre est **`codeUnite`**. La version précédente disait « l'agence » — c'est la confusion que CLAUDE.md §15 interdit (voir l'étape 4) |
| Nom des exports | `rapport-rations-<unité>-<AAAAMMJJ>.<pdf\|xlsx>`, premier jour de la période |
| `GET /parametres/fonctionnalites` | rend `{ rattrapageActif: boolean }`, et **vaut `true`** |
| Ouverture d'un complémentaire | `POST /processus` avec `typeProcessus: COMPLEMENTAIRE`, `idProcessusOrigine`, `motifOuverture`, **et les bornes de l'origine** |
| Circuit d'un complémentaire | **toujours au Directeur Réseau**, quel que soit le montant (6bis.1) |
| Doublon inter-états | `409 DOUBLON_INTER_ETATS`, message **nommant l'état en conflit** — déjà traité à l'écran de saisie depuis le 7F.4 révisé |
| Suivi par le DRH | **impossible** : le Reporting ne l'autorise pas (tableau révisé du 7F.2) |

**Les huit refus possibles à l'ouverture d'un complémentaire**, lus dans
`GestionnaireErreursApi` du Workflow :

| Code | Statut | Cause |
| --- | --- | --- |
| `FONCTIONNALITE_NON_OUVERTE` | 422 | drapeau refermé entre-temps |
| `ORIGINE_REQUISE` | 422 | aucun état d'origine désigné |
| `ETAT_NON_CLOTURE` | 422 | l'origine n'est pas clôturée — on ne régularise qu'un état clos |
| `MOTIF_OBLIGATOIRE` | 422 | motif absent ou fait d'espaces |
| `PERIODE_NON_CONCORDANTE` | 422 | bornes envoyées différentes de celles de l'origine |
| `DELAI_REGULARISATION_DEPASSE` | 422 | origine trop ancienne (`DELAI_REGULARISATION_JOURS`, provisoirement 90) |
| `UNITE_NON_CONCORDANTE` | 403 | unité envoyée différente de celle de l'origine |
| `DELAI_REGULARISATION_INDISPONIBLE` | 500 | paramètre de délai illisible — refus, jamais une valeur de repli |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Maquette des écrans (étape 1bis) | Sonnet | Moyen |
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

Un second point d'ergonomie : si la fonctionnalité est **refermée** (c'est désormais un geste d'urgence, le métier l'ayant ouverte), l'agent ne doit même pas voir l'entrée de menu qui y mène. Un menu visible mais menant à un refus systématique serait plus déroutant qu'un menu absent.

**Méthode retenue depuis le Sprint 7F.6 : maquette avant code.** Une proposition d'interface se discute et se corrige en quelques minutes sur un artefact ; la même discussion sur du code déjà écrit coûte une réécriture. L'étape 1bis construit cette maquette pour les écrans de ce sous-sprint, avant que l'étape 2 ne touche au code réel. Deux règles d'écriture valables pour toute maquette et tout écran produits à partir de ce sprint : aucun émoji comme icône (préférer un glyphe géométrique sobre ou aucune icône), et aucun tiret cadratin (« — ») dans le texte affiché, remplacé par une virgule, des parenthèses, un point milieu (« · ») entre deux termes courts, ou une phrase reformulée — ces deux points, remontés par l'utilisateur lors de la vérification du Sprint 7F.6, s'appliquent à toute interface et à toute maquette produites pour ce projet.

## 4. Objectifs

- Maquette (artefact) des écrans de ce sous-sprint, validée avec l'utilisateur avant l'implémentation
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
docs/dispositifs_provisoires.md (TIRET BAS) section 1.4 sur le
comportement frontend attendu. Confirme en 3 lignes les quatre endpoints de
reporting et la maniere dont le frontend doit traiter le drapeau
RATTRAPAGE_ACTIF.

CONTEXTE DE CETTE SESSION : Sprint 7F.7, dernier sous-sprint du
frontend. Suivi, rapports et regularisation. Le Sprint 6bis est
TERMINE et RATTRAPAGE_ACTIF vaut true (migration V8) : la
regularisation est ouverte. Son AFFICHAGE depend toujours du drapeau
lu depuis GET /parametres/fonctionnalites, qui sert desormais de
commande d'urgence.

Tu as acces en LECTURE SEULE au frontend de reference DOTTEL :
D:\stage afriland\formation spécialisée DSI\projet de gestion des absences\implementation\afb-dottel-mm\frontend
N'ECRIS JAMAIS dans ce depot.

Ses ecrans de suivi fournissent les patrons de presentation.
ATTENTION : DOTTEL a un "mode rattrapage", mais son modele est
INCOMPATIBLE avec ce module -- il calcule les beneficiaires non payes
a partir d'une liste d'enroles, et l'enrolement est interdit ici. Ne
transpose PAS ce calcul. Lis la section "Reutilisation du projet
DOTTEL" de ce guide.

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

### Étape 1bis. Maquette des écrans de ce sous-sprint

```
Avant d'ecrire le moindre ecran reel, produis une maquette (artefact
HTML) des ecrans de ce sous-sprint, pour validation avant
implementation -- meme methode qu'au Sprint 7F.6.

UN SEUL artefact, avec un menu de gauche qui bascule entre panneaux
(comme au 7F.6), couvrant :
- l'ecran de suivi multicritere (filtres, tableau pagine, statut
  d'integration comptable avec ses cinq situations) ;
- l'ecran d'historique d'un dossier (chronologie, y compris les
  passages repetes au meme niveau apres un retour) ;
- l'ecran de rapports et exports (selection unite + periode,
  synthese, deux boutons d'export) ;
- l'ecran d'ouverture d'un etat complementaire (selection de
  l'origine, motif obligatoire, recapitulatif des bornes non
  modifiables, avertissement "periode deja payee") ;
- l'etat MASQUE : ce que voit un agent quand RATTRAPAGE_ACTIF est
  ferme (aucune entree de menu, pour verifier que son absence ne
  laisse pas un trou visuel) ;
- le refus d'unicite inter-etats (409 DOUBLON_INTER_ETATS), avec son
  message le plus delicat du module -- nomme le beneficiaire, la
  journee, la nature, la session ET l'etat en conflit ;
- les modales necessaires (confirmation d'ouverture d'un
  complementaire, avec recapitulatif ; les huit refus possibles,
  chacun avec un message distinct) ;
- les cinq etats (chargement, vide, vide apres filtre, erreur,
  contenu) sur au moins l'ecran de suivi.

Reprends la charte du projet a l'identique (frontend/src/index.css :
rouge #e30613, gris neutres, Source Sans 3) et la barre laterale deja
validee au 7F.6 (groupee, reductible, logo Afriland conserve). Donnees
fictives uniquement.

DEUX REGLES D'ECRITURE, remontees par l'utilisateur a la verification
du 7F.6, valables pour cette maquette ET pour l'implementation qui
suit :
1. Aucun emoji comme icone. Un glyphe geometrique sobre (deja choisis
   au 7F.6 : les icones de la sidebar) ou pas d'icone du tout.
2. Aucun tiret cadratin (« — ») dans un texte affiche. Remplace-le
   par une virgule, des parentheses, un point milieu (« · ») entre
   deux termes courts ("Ration · jour"), ou reformule la phrase.

Presente-moi le lien, section par section, avant de passer a l'etape
2.
```

### Étape 2. Suivi multicritère

```
Cree l'ecran de suivi des demandes :

- Filtres combinables : periode (dateDebut et dateFin, via le
  selecteur de periode du 7F.1), unite, session, nature, beneficiaire.
  Ce sont exactement les parametres de GET /reporting/demandes.
- Tableau pagine, avec le composant du Sprint 7F.1.
- Colonnes : periode (ses deux bornes), unite, type (normal ou
  complementaire), montant total, statut d'avancement, statut
  d'integration comptable.

Le statut d'integration comptable merite une attention. Il se lit sur
DEUX champs de la reponse, transmisComptabilite et statutIntegration,
et un statutIntegration NUL a deux sens sans rapport :
- transmisComptabilite = false : jamais transmis ;
- transmisComptabilite = true : publication NON CONFIRMEE -- on ignore
  si l'etat est parti. C'est le signal a rendre visible.
Pour le detail, GET /transmission/processus/{id} rend une "situation"
parmi cinq (NON_TRANSMIS, PUBLICATION_NON_CONFIRMEE, EN_ATTENTE_ACCUSE,
INTEGRE, REJETE) et un message en clair : utilise-les plutot que de
reconstruire la logique cote interface.

VOCABULAIRE (decision du Sprint 6.2) : ecris "envoye a la
comptabilite", JAMAIS "transmis" ni "paye". Un etat peut etre envoye
ET rejete par la comptabilite : rien n'a ete paye. Un lecteur presse
qui lit "84 000 FCFA transmis" comprend "verses aux agents".

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

- Selection de la periode (dateDebut, dateFin) et de l'UNITE.

  ATTENTION : la version precedente de ce guide disait "l'agence". Le
  parametre reel est codeUnite. Code agence et code unite ont le meme
  format mais des roles distincts (CLAUDE.md §4 et §15) : l'agence
  domicilie le compte du beneficiaire, l'unite SUPPORTE LA CHARGE. Un
  rapport par agence regrouperait des depenses de plusieurs unites, et
  son libelle mentirait. Ecris "unite" partout sur cet ecran.
- Affichage du rapport consolide.
- Deux boutons d'export, pdf et excel, declenchant le telechargement
  du fichier retourne par le serveur.

Traite le cas d'une periode sans donnees : le rapport est vide, ce
n'est pas une erreur. Le serveur rend 200 avec vide = true et une
synthese a zero.

Les fichiers exportes se nomment rapport-rations-<unite>-<AAAAMMJJ>,
AAAAMMJJ etant le premier jour de la periode : ne le reconstruis pas
cote interface, lis l'en-tete Content-Disposition.

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

LA REQUETE : POST /processus avec typeProcessus = COMPLEMENTAIRE,
idProcessusOrigine, motifOuverture, ET codeUnite, dateDebut, dateFin.
Ces trois derniers doivent etre ceux de l'etat d'origine, a
l'identique : le backend les compare et refuse sinon
(PERIODE_NON_CONCORDANTE, UNITE_NON_CONCORDANTE). PRE-REMPLIS-LES
depuis l'origine selectionnee et NE LES REND PAS MODIFIABLES : les
laisser saisir, c'est offrir a l'agent de provoquer un refus.

LA SELECTION DE L'ORIGINE se heurte au meme manque qu'au 7F.5 :
GET /reporting/demandes n'a pas de filtre par statut. Reprends
l'arbitrage rendu au 7F.5 sur ce point. Ne filtre pas cote client une
liste paginee par le serveur.

Traite les HUIT refus du tableau de la section "Ce qui a change". Deux
meritent un soin particulier :
- DELAI_REGULARISATION_DEPASSE : la periode est trop ancienne pour
  etre regularisee dans le module. Dis-le, et dis que ce n'est pas une
  panne.
- ETAT_NON_CLOTURE : on ne regularise qu'un etat clos. Un etat encore
  ouvert se corrige directement.

APRES OUVERTURE, previens l'agent qu'un etat complementaire est TOUJOURS
valide par le Directeur Reseau, quel que soit son montant.

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
Sprint 7F.4, SANS MODIFICATION : le refus pour unicite inter-etats
(409 DOUBLON_INTER_ETATS) y est deja traite depuis la revision du
guide 7F.4. VERIFIE-le plutot que de le refaire.

Le message est le plus delicat du module. Il indique que la prestation
figure deja dans un autre etat de la periode, en citant le
beneficiaire, la journee, la nature, la session -- ET L'ETAT EN
CONFLIT, que le backend nomme. Affiche-le tel quel.

AJOUTE un bandeau permanent sur l'ecran de saisie d'un complementaire :
on travaille sur une periode DEJA PAYEE. C'est la seule chose a
reprendre du mode rattrapage de DOTTEL.

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
test, DRAPEAU OUVERT (sa valeur livree depuis la migration V8) :
declenchement, saisie, soumission, validation, aiguillage, cloture,
suivi, rapport.

Rejoue ensuite le parcours de regularisation : ouverture d'etat
complementaire, saisie, refus d'unicite sur une combinaison deja
payee (message nommant l'etat en conflit), saisie acceptee sur une
combinaison nouvelle, puis validation -- qui doit monter au Directeur
Reseau meme pour un petit montant.

Verifie enfin le MASQUAGE, drapeau FERME : UPDATE parametre_systeme
SET valeur = 'false' WHERE code = 'RATTRAPAGE_ACTIF'. Le menu doit
disparaitre et la route etre bloquee par l'url.

PUIS REOUVRE-LE IMMEDIATEMENT : SET valeur = 'true'. Le metier l'a
ouvert ; le laisser ferme apres un test couperait la regularisation
sans qu'aucune alerte ne le signale. Confirme-moi la reouverture par
une requete.
```

### Étape 8. Clôture du Sprint 7F

```
Mets a jour CLAUDE.md avec les decisions prises pendant le Sprint 7F :
1. La CONFIRMATION du lieu de conservation du jeton (7F.3) -- en
   memoire, detenu par keycloak-js, decision de fait du Sprint 0.4.
2. La CONFIRMATION de la politique de rafraichissement (7F.3) --
   updateToken avant chaque appel, decision de fait du Sprint 0.4.
3. La disposition retenue pour l'ecran de saisie journaliere (7F.4).
4. La maniere de signaler qu'une grille est sans effet (7F.6).
5. La table de correspondance des codes d'erreur et ses messages,
   incluant desormais FONCTIONNALITE_NON_OUVERTE, DOUBLON_INTER_ETATS
   et les huit refus d'ouverture d'un complementaire.
6. Le mecanisme de masquage conditionnel fonde sur les
   fonctionnalites actives (7F.7).

Propose les ajouts section par section.
```

## 6. Critères de validation

| Élément | Statut attendu |
|---|---|
| Maquette des écrans de ce sous-sprint validée avant l'implémentation | Fait |
| Aucun émoji comme icône, aucun tiret cadratin dans un texte affiché | Vérifié |
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
| Parcours complet validé drapeau ouvert, masquage vérifié drapeau fermé | Vérifié |
| **Drapeau rouvert après le test, confirmé par requête** | Vérifié |
| Rapports filtrés par **unité**, jamais par agence | Vérifié |
| « Envoyé à la comptabilité », jamais « transmis » ni « payé » | Vérifié |
| Publication non confirmée (`transmis` + statut nul) signalée | Vérifié |
| Bornes de l'origine pré-remplies et non modifiables à l'ouverture | Vérifié |
| Huit refus d'ouverture d'un complémentaire traités | Vérifié |
| Calcul « bénéficiaires non payés » de DOTTEL non transposé | Vérifié |
| CLAUDE.md complété des décisions du Sprint 7F | Fait |

## 7. Points de vigilance

- **Masquer un lien ne suffit jamais.** Le principe posé au Sprint 7F.3 pour les rôles s'applique identiquement au drapeau de fonctionnalité : la route doit être protégée indépendamment de l'affichage du menu.
- **Le refus pour unicité inter-états est le message le plus délicat du module.** L'agent agit de bonne foi pour rattraper un oubli et se heurte à un blocage. Sans message précis citant les quatre éléments, il conclura à un dysfonctionnement.
- Le statut d'intégration comptable est une information de suivi sous-estimée. Un état clôturé depuis longtemps sans accusé signale un problème en aval, et seul le suivi peut le révéler.
- Ne recalculer aucun total côté interface. L'écran doit afficher ce que le serveur retourne, sinon les chiffres finiraient par diverger de ceux des exports.
- L'historique doit montrer les passages répétés au même niveau. C'est la trace des allers-retours, et elle a une valeur de contrôle interne.
- Les résidus du projet source sont le risque de fin de sprint : une route, un rôle ou un terme de DOTTEL laissé dans le code passe inaperçu et sème la confusion plus tard.
- **Le mode rattrapage de DOTTEL est le piège le plus subtil du Sprint 7F.** Il ressemble au besoin, il est soigné, et il repose sur une liste d'enrôlés : le transposer réintroduirait l'enrôlement sous un autre nom.
- **Rouvrir le drapeau après avoir testé le masquage.** Un drapeau oublié fermé coupe la régularisation en silence : aucune erreur, seulement un menu absent.
- **Agence et unité ne se confondent jamais**, y compris dans un libellé d'écran. Un rapport « par agence » regrouperait les charges de plusieurs unités.
- **Maquette avant code, pas maquette puis code sans relecture.** L'étape 1bis n'est utile que si les écrans réels de l'étape 2 y correspondent réellement à la fin du sous-sprint ; vérifier l'écart au moment de la clôture (étape 7).
- **Aucun émoji, aucun tiret cadratin.** Deux marqueurs d'écriture assistée relevés par l'utilisateur à la vérification du Sprint 7F.6, sur la maquette autant que sur le code réel.

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

Refs: US-15 a US-18, RG-15, docs/dispositifs_provisoires.md"
```

---

**Fin du Sprint 7F.7 et du Sprint 7F.** Le frontend est complet, y compris le parcours de régularisation, dont l'affichage reste subordonné au drapeau `RATTRAPAGE_ACTIF`. En attente de validation avant le Sprint 8, passerelle et déploiement.
