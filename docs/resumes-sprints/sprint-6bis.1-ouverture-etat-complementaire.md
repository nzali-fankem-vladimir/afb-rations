# Résumé Sprint 6bis.1 — Ouverture d'un état complémentaire

**Date :** 5 septembre 2026
**Objet du guide :** permettre l'ouverture d'un processus complémentaire sur une
période déjà close, derrière un drapeau de fonctionnalité
**Ce qui a réellement été fait :** l'intégralité du guide, plus la réparation de
trois classes de tests que le sprint cassait au passage, plus deux tests de bout
en bout non demandés mais exigés en cours de route

---

## En une phrase

Un agent peut désormais ouvrir un état complémentaire sur une période close sans
jamais rouvrir l'état d'origine — et cette fonctionnalité **reste fermée**,
`RATTRAPAGE_ACTIF` valant `false`, jusqu'à ce que RG-15 existe. Le besoin métier a
été confirmé pendant le sprint (**M-01 résolu**), mais cela n'ouvre pas le drapeau :
sans le contrôle d'unicité inter-états, un complémentaire pourrait reproduire une
ligne déjà payée.

---

## Écarts constatés par rapport au guide, avant tout codage

**La migration demandée existait déjà.** Le guide prescrivait une migration V4
insérant `RATTRAPAGE_ACTIF` et `DELAI_REGULARISATION_JOURS`. Ces deux paramètres
sont posés depuis `V2__parametres_systeme.sql` (Sprint 4.1), dans la forme exacte de
`docs/dispositifs_provisoires.md` §1.2. `FonctionnaliteNonOuverteException` existait
également. **Aucune migration n'a donc été créée** — et V4 comme V5 sont de toute
façon déjà pris par le statut d'intégration (5.1) et la réservation de transmission
(5.3) : la prochaine migration du service Workflow sera V6.

**Aucun `OuvertureComplementaireRequest` n'a été créé**, contrairement au tableau des
fichiers du guide. `POST /processus` a un seul corps de requête, défini par le
contrat d'API §5, dont l'exemple d'état complémentaire porte exactement les mêmes
champs. Un second DTO sur le même endpoint n'aurait pu que diverger du premier.
`DeclenchementProcessusRequest` portait déjà `typeProcessus`, `idProcessusOrigine`
et `motifOuverture` depuis le Sprint 4.1.

---

## Décisions prises en cours de route, avec l'utilisateur

### Comportement par défaut du drapeau — et un avertissement distinct

Un paramètre absent est traité comme **fermé**, sans lever d'exception : « fermé »
est une réponse normale, pas une panne. À la demande de l'utilisateur, un `WARN`
**distinct** signale l'absence de la ligne, par opposition à une fermeture
délibérée. Sans lui, une suppression accidentelle éteindrait la régularisation pour
toujours : le jour de la confirmation métier, l'`UPDATE` prévu n'ouvrirait rien du
tout, et personne ne saurait pourquoi. Trois situations anormales, trois messages
distincts — ligne disparue, ligne désactivée, valeur illisible.

### Le 403 d'unité non concordante est tracé en audit

Question posée par l'utilisateur, et elle était juste : si un désaccord d'unité
mérite un `403`, c'est qu'on le traite comme une tentative d'accès inter-unité — et
la doctrine du Sprint 6.3 (CT-04) exige alors qu'il soit tracé.
`UniteNonConcordanteException` est donc branchée dans `GestionnaireErreursApi`, seul
point de convergence des refus d'accès du service, et passe par le même
`publierRefus` que les quatre autres refus, avec son propre motif. Elle ne pouvait
pas rejoindre les trente trous d'audit du Sprint 6.3 : le mécanisme centralisé
existait, il a été réutilisé.

### Le délai de régularisation reçoit la lecture stricte du seuil

Point non soulevé dans ma première analyse, relevé par l'utilisateur.
`DELAI_REGULARISATION_JOURS` est stocké en texte, comme `SEUIL_AIGUILLAGE_DR`, et
tout aussi susceptible d'être malformé. Il reçoit donc le même traitement
*fail-closed* qu'au Sprint 4.3 : absent, désactivé, non numérique ou négatif →
refus, jamais une valeur devinée. Deux codes distincts, parce que ce sont deux
situations différentes :

| Situation | Code | Motif |
|---|---|---|
| Configuration en défaut | `500 DELAI_REGULARISATION_INDISPONIBLE` | L'agent n'a rien à corriger — même parti que `SEUIL_INDISPONIBLE` |
| Origine réellement trop ancienne | `422 DELAI_REGULARISATION_DEPASSE` | Règle de gestion qui refuse |

Zéro est accepté (seule une période close le jour même reste régularisable), le
négatif refusé (il fermerait tout en silence). Même distinction qu'au Sprint 4.3.

### La casse du drapeau

Décision prise en écrivant les tests : `'TRUE'` ouvre comme `'true'`. La valeur est
écrite à la main par un administrateur ; refuser sur une majuscule laisserait la
fonctionnalité fermée avec un simple avertissement au journal — un échec presque
silencieux. `'oui'`, `'1'` et `'vrai'` restent refusés : là où l'intention est
claire on la suit, là où elle demande une interprétation on refuse.

### Le sort du sprint, à l'annonce du rythme hebdomadaire

Le métier a signalé en cours de sprint que le paiement serait **hebdomadaire** et
non mensuel. Décision prise avec l'utilisateur : **terminer et commiter 6bis.1,
geler 6bis.2**. Motif : `OuvertureComplementaireService` **recopie** la période
depuis l'origine au lieu de l'interpréter, et le seul endroit qui la lit est
`exigerPeriodeConcordante` — une comparaison demande-contre-origine qui survivrait à
un changement de représentation en deux lignes. 6bis.2 (RG-15, « même unité et même
période ») serait au contraire le premier code écrit faux. Voir
`sprint ajustement metier rythme de paiement.md` à la racine.

---

## Les contrôles d'ouverture, arbitrés puis implémentés

L'ordre est celui du guide, avec deux insertions justifiées par les doctrines
existantes du projet.

| # | Contrôle | Refus | Origine de la décision |
|---|---|---|---|
| 1 | Drapeau `RATTRAPAGE_ACTIF` | `422 FONCTIONNALITE_NON_OUVERTE` | Guide, en tête absolue |
| 2 | Identifiant d'origine fourni | `422 ORIGINE_REQUISE` | Ajouté — étape 6 du guide l'exige, contrainte conditionnelle au type |
| 3 | Motif non vide | `422 MOTIF_OBLIGATOIRE` | Placé avant les E/S, doctrine `RetourService` (4.4) |
| 4 | Origine existante | `404 PROCESSUS_INTROUVABLE` | Réutilise l'exception existante |
| 5 | Portée sur l'unité **de l'origine** | `403 UTILISATEUR_NON_HABILITE` / `503` | Charger avant de vérifier, doctrine 4.1 |
| 6 | Origine clôturée | `422 ETAT_NON_CLOTURE` | Réutilise le code du Sprint 5.3 — même fait, même code |
| 7 | Dans le délai | `422 DELAI_REGULARISATION_DEPASSE` | Nouveau code |
| 8 | Unité déclarée concordante | `403 UNITE_NON_CONCORDANTE` **+ audit** | Même nom et même code que côté Saisie (3.4) |
| 9 | Période déclarée concordante | `422 PERIODE_NON_CONCORDANTE` | Nouveau code — la période n'ouvre aucun droit |

**Pourquoi 403 pour l'unité et 422 pour la période.** L'unité est ce sur quoi la
portée d'un agent est définie (Sprint 1.1) : un désaccord peut signaler un
débordement de périmètre, et se trace. La période n'ouvre aucun droit — se tromper
de mois est une maladresse de saisie. La traiter en refus d'accès enverrait l'agent
réclamer une habilitation dont l'absence n'est pas en cause, et remplirait le
journal d'audit de fautes de frappe.

---

## Ce qui a été construit

### La création passe par la machine à états

`TransitionProcessus.ouvrirComplementaire(origine, motif)` est la **seconde et
dernière porte de création** d'un `ProcessusMensuel`, dont le second constructeur
reste en visibilité paquet. L'invariant « toute création passe par la machine à
états » (Sprint 4.1) reste donc vérifié par le compilateur.

La machine ne juge que ce qu'elle est fondée à juger : **l'origine est close**, et
**un motif existe**. Le reste — drapeau, délai, portée, concordances — relève du
service : ce sont des règles d'ouverture, pas des règles de cycle de vie. Les y
faire entrer rendrait la machine dépendante d'un paramètre de configuration, ce que
la décision du Sprint 4.1 interdit pour le seuil.

**La période et l'unité sont recopiées de l'origine, jamais reçues.** Ce que
l'appelant déclare sert uniquement à être vérifié. C'est ce qui rend ce code
largement indifférent à la maille de la période.

### L'état d'origine n'est jamais touché

Le constructeur ne lit l'origine que pour en recopier cinq valeurs. Il ne lui
applique aucun mutateur, ne touche ni à son statut, ni à ses signatures, ni à son
drapeau de transmission. Deux tests le vérifient champ par champ, dont un de bout en
bout sur un dossier réellement clôturé par le circuit.

### La date de clôture se lit sur la dernière étape validée

`processus_mensuel` ne porte aucune colonne `date_cloture` (décision Sprint 4.1) :
l'instant de la clôture vit sur `etape_workflow.date_creation` de la validation qui
a clos le dossier. Le service prend donc la **dernière** étape `VALIDEE` par rang
décroissant, en réutilisant
`findFirstByIdProcessusAndStatutEtapeOrderByOrdreEtapeDesc`, déjà présente depuis le
Sprint 4.4.

Le raisonnement ne tient que parce que `CLOTURE` est terminal : aucune étape ne peut
suivre celle qui a clos le dossier. **Sur exigence de l'utilisateur**, deux tests
couvrent le dossier à plusieurs cycles — celui qui a déjà piégé le projet une fois,
au Sprint 4.4 avec RG-12.

Un état `CLOTURE` sans aucune étape `VALIDEE` est une incohérence de données : le
délai devient inévaluable, et l'ouverture est refusée plutôt qu'accordée sur une
ancienneté supposée.

### Le refus du Sprint 4.1 est levé, pas supprimé

`ProcessusService.exigerTypeOuvert` devient `exigerTypeNormal`. Ce n'est plus un
refus métier mais un **invariant de programmation** : une demande complémentaire
parvenue jusque-là signale un aiguillage cassé, et lève `IllegalArgumentException`.

Le garde-fou reste indispensable — `TransitionProcessus.declencher` crée *toujours*
un `NORMAL`, et sans ce contrôle une demande complémentaire produirait
silencieusement un second état mensuel ordinaire sur une période close, le pire des
comportements possibles.

L'aiguillage se fait dans `ProcessusController`, sur un `switch` exhaustif du type
demandé. Le comportement observable est **inchangé** drapeau fermé : `422
FONCTIONNALITE_NON_OUVERTE`, comme depuis le Sprint 4.1 — mais il s'ouvre désormais
par une mise à jour de paramètre, sans reprise de code.

---

## Trois classes de tests que le sprint cassait

Constat factuel établi avant toute correction : **294 tests, 1 échec, 50 erreurs**.

| Classe | Erreurs | Cause |
|---|---|---|
| `ProcessusControllerIT` | 40 | `@MockitoBean OuvertureComplementaireService` manquant — le contexte `@WebMvcTest` ne s'assemblait plus |
| `IntegrationComptableIT` | 10 | idem |
| `ProcessusServiceTest` | 1 échec | `typeComplementaireRefuse` attendait `FonctionnaliteNonOuverteException` |

Un quatrième cas est apparu après réparation : `ProcessusControllerIT.typeComplementaireRefuse`
levait un `NullPointerException`, le contrôleur aiguillant désormais vers un service
mocké rendant `null`. Il a été **converti** : il vérifie maintenant que l'aiguillage
a bien lieu (`verify(processusService, never())`) et que le refus ressort sous son
code dédié.

Ces classes ont été réparées **avant** d'écrire une seule ligne de test nouveau.

---

## Les tests

**341 tests, 0 échec, 0 erreur — BUILD SUCCESS.** 294 → 341, soit **47 tests
ajoutés**. Le guide en demandait 18 ; les 29 autres couvrent les bornes et les
scénarios exigés en cours de route.

| Fichier | Tests | Objet |
|---|---|---|
| `FonctionnaliteServiceTest` | 14 | Drapeau (7) et délai (7), contre la vraie base |
| `OuvertureComplementaireServiceTest` | 24 | Drapeau prioritaire (5), ouverture (5), refus (12), multi-cycles (2) |
| `ParametreControllerIT` | 4 | Endpoint des fonctionnalités, tous rôles, refus sans jeton |
| `ProcessusControllerIT` | +1 | Ouverture par un rôle autre qu'`AGENT_UNITE` : 403 |
| `AiguillageServiceTest` | +3 | Second niveau obligatoire, seuil nul, seuil non lu |
| `CircuitCompletIT` | +1 | Régularisation de bout en bout après un dossier à deux cycles |

**Les tests qui comptent le plus**, dans l'ordre :

1. **Drapeau fermé, demande parfaite** — origine existante, close, dans les délais,
   bonne unité, motif fourni. Tout est juste, le refus tombe quand même.
2. **Le refus du drapeau précède tout** — y compris sur une origine inexistante, et
   y compris sur une requête fautive. Prouvé par l'absence d'interaction avec le
   service Identité.
3. **L'origine strictement inchangée** — statut, montant, drapeau de transmission,
   statut d'intégration, date de création, et ses signatures.
4. **La borne du délai** — 90 jours passe, 91 refuse. Un test sur une valeur
   confortablement inférieure passerait même avec une comparaison inversée d'une
   unité.
5. **Le dossier à deux cycles** — validé DA, monté au DR, retourné, corrigé,
   resoumis, clôturé. Le délai court depuis la **dernière** validation ; un calcul
   fondé sur la première refuserait des régularisations légitimes. Doublé d'un test
   miroir où tous les cycles sont anciens, sans lequel le premier passerait même
   avec un service prenant n'importe quelle étape récente.

---

## L'aiguillage d'un complémentaire : second niveau obligatoire

**Un état complémentaire monte toujours au Directeur Réseau, quel que soit son
montant.** Règle **provisoire**, retenue le 6 septembre en attendant l'arbitrage du
métier.

Trois lectures étaient possibles, et deux laissaient un trou de contrôle interne.
Comparer le montant du complémentaire au seuil, comme pour un état normal,
permettait de fractionner une régularisation en plusieurs états restant chacun sous
la barre — le rythme hebdomadaire l'aggraverait, les états devenant environ quatre
fois plus petits. Laisser le Chef d'Unité clore seul autorisait un complémentaire de
n'importe quel montant sans second regard.

**Le principe qui tranche : quand on décide sans le métier, on décide dans le sens
qui exige *plus* d'approbation, jamais moins.** La règle retenue ne peut que trop
demander, et c'est le seul sens dans lequel se tromper sur un paiement ne coûte rien
d'irréversible.

Implémentation : une branche en tête d'`AiguillageService.aiguiller`, **avant** la
lecture du seuil. Troisième valeur `COMPLEMENTAIRE_ENVOI_DIRECTEUR_RESEAU`,
`seuilApplique` devenu `Long` et rendu **nul** — un `0` aurait été indiscernable d'un
seuil réellement configuré à zéro, que le Sprint 4.3 accepte. **Le seuil n'est pas lu
du tout**, et un test le prouve en le rendant illisible : l'état normal échoue,
le complémentaire aboutit.

Si le métier arbitre autrement, la bascule tient dans ce seul fichier. Voir
`docs/decisions/2026-09-05-ouverture-etat-complementaire-et-drapeau.md` § 8.

---

## Vérification manuelle contre l'environnement réel

Menée le 7 septembre 2026 : conteneurs `rations-postgres` et `rations-kafka`,
`dottel-keycloak`, puis les services Workflow (8084), Identité (8081) et Audit (8087)
démarrés à la main. Jeton obtenu pour `jean_mbarga` (AGENT_UNITE, unité 00002).

**Deux preuves d'ordre qu'on ne peut pas truquer.** Le service Identité était
volontairement **arrêté** pendant le premier lot : un contrôle d'habilitation placé
avant le drapeau aurait rendu `503`, et une origine inexistante aurait rendu `404`.
Les deux demandes ont rendu `422 FONCTIONNALITE_NON_OUVERTE`. Le drapeau passe donc
bien en tête, et ce n'est pas déduit d'un test à double.

| Vérification | Résultat observé |
|---|---|
| `GET /parametres/fonctionnalites` avec jeton | `{"rattrapageActif":false}`, `200` |
| Le même sans jeton | `401` |
| Ouverture, drapeau fermé, origine plausible | `422 FONCTIONNALITE_NON_OUVERTE` |
| Ouverture, drapeau fermé, origine **inexistante** | `422 FONCTIONNALITE_NON_OUVERTE` — pas de `404` |
| `UPDATE` du drapeau à `true` | Endpoint rend `true` **sans redémarrage** |
| Sans `idProcessusOrigine` | `422 ORIGINE_REQUISE` |
| Motif composé d'espaces | `422 MOTIF_OBLIGATOIRE` |
| Origine inexistante, drapeau ouvert | `404 PROCESSUS_INTROUVABLE` |
| Origine réelle, Identité arrêté | `503` — les contrôles 2 à 4 ont bien été franchis |
| Ouverture nominale sur l'origine 1321 | `201`, processus **3782** créé |
| Second complémentaire, même période | `201`, processus **3783** — autorisé |
| Second état NORMAL, même période | `409 PROCESSUS_EXISTANT` |
| Unité déclarée `00007` sur une origine `00002` | `403 UNITE_NON_CONCORDANTE` |
| Période déclarée `03/2027` sur une origine `08/2027` | `422 PERIODE_NON_CONCORDANTE` |
| Origine non clôturée | `422 ETAT_NON_CLOTURE` |
| Fermeture du drapeau | Endpoint rend `false`, ouverture de nouveau refusée |

**L'état d'origine, comparé champ à champ avant et après.** Empreinte SQL prise avant
l'ouverture, reprise après, sur `processus_mensuel` **et** `etape_workflow` :
`diff` **vide**. Le processus 1321 conserve son statut `CLOTURE`, son montant, son
drapeau de transmission, son `statut_integration = INTEGRE` et ses deux signatures
`SHA-256`. C'est le critère le plus important du sprint, et il est vérifié par
comparaison, pas par lecture.

**L'audit, lu dans `rations_audit` et non supposé.** Doctrine du Sprint 6.3 : un
critère d'audit ne se coche qu'après une requête. Quatre traces réelles :

- `OUVERTURE_COMPLEMENTAIRE` sur 3782 et 3783, dont le delta porte l'auteur
  (`jean_mbarga`), son rôle, le motif d'ouverture, l'identifiant de l'origine, son
  statut **et sa date de clôture** — de quoi refaire le calcul du délai six mois plus
  tard, même si le paramètre a changé depuis ;
- `ACCES_REFUSE` de motif `UNITE_NON_CONCORDANTE`, ce qui tient l'exigence CT-04 sur
  le nouveau refus en `403` ;
- `ACCES_REFUSE` de motif `IDENTITE_INDISPONIBLE`, produit par le refus conservateur
  pendant que le service Identité était arrêté.

**Données laissées en base, délibérément.** Les processus 3782 et 3783 ne sont pas
supprimés : le journal d'audit enregistre leur création, et les effacer ferait mentir
un journal que le module déclare immuable — même raisonnement qu'au Sprint 6.3 pour
le profil de `pierre_belinga`. Le drapeau, lui, a été **refermé** et vérifié fermé.

---

## Critères de validation du guide

| Critère | État |
|---|---|
| Migration des deux paramètres créée | **Sans objet** — déjà posés par V2 (Sprint 4.1) |
| Comportement par défaut fermé confirmé | Fait, avec `WARN` distinct sur l'absence |
| Endpoint de fonctionnalités actives fonctionnel | Vérifié, 4 tests |
| Drapeau vérifié en priorité sur tout autre contrôle | Vérifié, 3 tests |
| Liste des contrôles d'ouverture arbitrée | Fait, 9 contrôles validés avec l'utilisateur |
| État d'origine strictement inchangé | Vérifié, 2 tests dont un de bout en bout |
| Motif obligatoire et non vide | Vérifié, à deux étages |
| Asymétrie normal et complémentaire respectée | Vérifié, 2 tests |
| Saisie fonctionnant sans modification du service Saisie | Vérifié — **zéro ligne modifiée côté Saisie** |
| Circuit de validation identique | Vérifié, de bout en bout |
| Lecture de l'aiguillage confirmée | **Fait** — second niveau obligatoire pour tout complémentaire, règle provisoire |
| Aucune entité Réclamation ni liste de bénéficiaires | Vérifié |
| Dix-huit tests passants | Dépassé — 47 tests ajoutés, 341 au total |

---

## État final laissé en place, délibérément

- **`RATTRAPAGE_ACTIF = false`.** M-01 est résolu (le métier a confirmé le besoin),
  mais le drapeau **ne doit pas être ouvert** : RG-15 n'existe pas, et sans elle un
  complémentaire peut reproduire une ligne déjà payée dans l'origine. Chaîne à
  retenir : besoin confirmé → ouverture impossible tant que 6bis.2 n'est pas fait →
  6bis.2 gelé en attendant M-04.
- **`DELAI_REGULARISATION_JOURS = 90`**, libellé portant toujours `VALEUR
  PROVISOIRE` : M-02 n'est pas résolu.
- **6bis.2 gelé.** Voir `sprint ajustement metier rythme de paiement.md`.

---

## Points ouverts

| Réf | Objet | Interlocuteur |
|---|---|---|
| M-02 | Délai de régularisation d'une période close (90 jours provisoires) | Métier |
| M-04 | Rythme de paiement, mensuel ou hebdomadaire | Métier |

**Résolu pendant ce sprint :** M-01, le besoin d'état complémentaire, confirmé par le
métier. Cela n'ouvre pas le drapeau : RG-15 reste le préalable.

**Tranché à titre provisoire, en attendant le métier :** l'aiguillage d'un
complémentaire, qui monte systématiquement au Directeur Réseau. Ce n'est pas un point
en attente au registre — le module a une règle et l'applique —, mais elle est
signalée comme provisoire dans le code, dans les tests et dans la décision, et sa
bascule est locale.

---

## Fichiers du sprint

**Créés — code**

| Chemin | Nature |
|---|---|
| `application/FonctionnaliteService.java` | Lecture du drapeau et du délai |
| `application/OuvertureComplementaireService.java` | Les neuf contrôles et la création |
| `api/ParametreController.java` | `GET /parametres/fonctionnalites` |
| `api/dto/FonctionnalitesActivesResponse.java` | Corps de la réponse |
| `domaine/exception/OrigineRequiseException.java` | `422 ORIGINE_REQUISE` |
| `domaine/exception/MotifOuvertureRequisException.java` | `422 MOTIF_OBLIGATOIRE` |
| `domaine/exception/DelaiRegularisationDepasseException.java` | `422 DELAI_REGULARISATION_DEPASSE` |
| `domaine/exception/DelaiRegularisationIndisponibleException.java` | `500 DELAI_REGULARISATION_INDISPONIBLE` |
| `domaine/exception/UniteNonConcordanteException.java` | `403 UNITE_NON_CONCORDANTE`, tracée |
| `domaine/exception/PeriodeNonConcordanteException.java` | `422 PERIODE_NON_CONCORDANTE` |

**Modifiés — code**

| Chemin | Nature |
|---|---|
| `domaine/ProcessusMensuel.java` | Constructeur COMPLEMENTAIRE, visibilité paquet |
| `domaine/TransitionProcessus.java` | `ouvrirComplementaire`, deux gardes |
| `application/ProcessusService.java` | Refus 4.1 → invariant de programmation |
| `application/AiguillageService.java` | Branche complémentaire, seuil non lu |
| `application/DecisionAiguillage.java` | Troisième valeur, `COMPLEMENTAIRE_ENVOI_DIRECTEUR_RESEAU` |
| `application/ResultatAiguillage.java` | `seuilApplique` : `long` → `Long` nullable |
| `application/EnregistrementValidation.java` | Troisième cas du `switch` exhaustif |
| `api/ProcessusController.java` | Aiguillage sur le type demandé |
| `api/GestionnaireErreursApi.java` | Six gestionnaires, dont un traçant en audit |
| `infrastructure/ParametreSystemeRepository.java` | `findByCode` — distinguer deux silences |

**Tests**

| Chemin | Nature |
|---|---|
| `application/FonctionnaliteServiceTest.java` | Création, 14 tests |
| `application/OuvertureComplementaireServiceTest.java` | Création, 24 tests |
| `api/ParametreControllerIT.java` | Création, 4 tests |
| `api/ProcessusControllerIT.java` | Mock ajouté, 1 test converti, 1 test créé |
| `api/IntegrationComptableIT.java` | Mock ajouté |
| `application/ProcessusServiceTest.java` | Test 5 révisé |
| `application/AiguillageServiceTest.java` | 3 tests du second niveau obligatoire |
| `application/CircuitCompletIT.java` | Câblage + 1 test de bout en bout |

**Aucune migration.** Les deux paramètres existaient depuis V2.
