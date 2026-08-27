# SPRINT 6BIS.1

## Ouverture d'un état complémentaire

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Permettre l'ouverture d'un processus complémentaire sur une période déjà close |
| **Livrable** | Ouverture d'un état complémentaire, rattachement à l'état d'origine, circuit inchangé |
| **Durée** | Une à deux journées |
| **Prérequis** | Sprint 6.3 validé et commité |
| **Sprint suivant** | 6bis.2, unicité inter-états et clôture |

## Condition de démarrage (mise à jour)

**Ce sprint peut désormais démarrer sans confirmation métier.** La règle précédente, qui bloquait le démarrage tant que le besoin n'était pas confirmé, est remplacée par un dispositif de drapeau de fonctionnalité, décrit dans `docs/dispositifs-provisoires.md`.

Le code se développe et se livre normalement, mais **la fonctionnalité reste fermée** tant que le paramètre `RATTRAPAGE_ACTIF` vaut `false` en base. Elle s'ouvre par une simple mise à jour de paramètre le jour où le métier confirme, sans reprise de code ni redéploiement.

Ceci ne dispense pas de poser les deux questions au métier et à la comptabilité, listées au registre des points en attente sous les références M-01, M-02 et M-03. Tant qu'elles ne sont pas résolues, le drapeau reste fermé et ce sprint produit du code qui ne s'exécute jamais en conditions réelles.

| Point | Réf | Interlocuteur |
|---|---|---|
| Confirmation du besoin d'état complémentaire | M-01 | Métier |
| Délai de régularisation d'une période close | M-02 | Métier |
| Position sur une seconde transmission comptable | M-03 | DFT |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Ensemble du sous-sprint | Opus | Élevé |

Maintenir Opus effort élevé. Ce sprint touche à la contrainte d'unicité du processus normal et au périmètre de la transmission comptable, deux mécanismes déjà en place qu'il ne faut pas fragiliser.

## 2. Outil de cartographie

```
py -3.14 -m graphify update .
```

## 3. Contexte

Un bénéficiaire signale à son agent qu'il n'a pas été payé sur un mois déjà clôturé. C'est le cas que traite ce sprint.

La solution retenue pendant la phase documentaire est claire : **on ne rouvre jamais l'état d'origine.** Il reste figé avec ses signatures, ce qui préserve l'intégrité du contrôle interne. À la place, un état complémentaire distinct est ouvert sur la même période, rattaché à l'état d'origine, et il suit exactement le même circuit de validation.

Deux points ont été explicitement écartés au cours de la conception, et il ne faut pas les réintroduire. Il n'y a **pas d'entité Réclamation** : le signalement du bénéficiaire est un événement externe au système, l'agent l'enregistre en ouvrant l'état complémentaire avec un motif libre. Et il n'y a **pas de liste de bénéficiaires attendus** : ce processus n'ayant pas d'enrôlement, le système ne peut pas savoir qui aurait dû être payé.

La plomberie existe déjà : `processus_mensuel` porte `type_processus`, `id_processus_origine` et `motif_ouverture` depuis le Sprint 0.5, et l'index partiel du Sprint 0.5 autorise plusieurs états complémentaires sur une période où un seul état normal est permis.

**Nouveauté de cette version du guide :** l'ouverture passe désormais par un contrôle de drapeau avant tout autre traitement. C'est la première chose que le service vérifie.

## 4. Objectifs

- Paramètres `RATTRAPAGE_ACTIF` et `DELAI_REGULARISATION_JOURS` créés en base
- Endpoint exposant les fonctionnalités actives au frontend
- Ouverture d'un état complémentaire, rattaché à un état d'origine clôturé, refusée tant que le drapeau est fermé
- Contrôles d'ouverture : drapeau actif, origine existante, clôturée, dans le délai, même unité et même période
- Saisie possible sur l'état complémentaire, via le service Saisie inchangé
- Circuit de validation identique à celui d'un état normal
- Tests couvrant le drapeau fermé, le drapeau ouvert, et l'intégrité de l'état d'origine

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| US-17 | Ouverture d'un état complémentaire sans rouvrir l'origine |
| RG-15 | Unicité inter-états, dont l'implémentation est le sous-sprint 6bis.2 |
| CT-34 | État complémentaire portant le type et référençant l'origine, qui reste figée |
| CT-37 | Circuit de validation identique à celui d'un état normal |

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, section 7 sur
l'etat complementaire, section 15 sur les erreurs interdites, et
docs/dispositifs-provisoires.md, section 1, sur le drapeau de
fonctionnalite. Confirme en 3 lignes ce qu'est un etat complementaire,
ce qu'il ne doit jamais faire, et comment le drapeau RATTRAPAGE_ACTIF
en encadre l'ouverture.

CONTEXTE DE CETTE SESSION : Sprint 6bis.1, ouverture d'un etat
complementaire. La confirmation metier n'est pas encore obtenue : le
code se developpe mais reste ferme derriere le drapeau
RATTRAPAGE_ACTIF, qui vaut false par defaut.

Deux rappels de conception, issus d'arbitrages deja rendus :
- Il n'existe PAS d'entite Reclamation. Le signalement du
  beneficiaire est externe au systeme. Ne la cree pas.
- Il n'existe PAS de liste de beneficiaires attendus. Ce processus
  n'a pas d'enrolement, le systeme ne peut pas savoir qui aurait du
  etre paye. Ne la construis pas.
SERVICE CONCERNE : service-workflow uniquement.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Chaque methode publique vient avec ses tests.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : cree la migration V4 de service-workflow inserant
les deux parametres RATTRAPAGE_ACTIF (valeur false) et
DELAI_REGULARISATION_JOURS (valeur 90, libelle mentionnant
explicitement VALEUR PROVISOIRE), conformement au dispositif decrit
dans docs/dispositifs-provisoires.md section 1.2. Montre le fichier.
```

### Étape 2. Contrôle du drapeau

```
Cree le service de lecture du drapeau de fonctionnalite, reutilisable
par le controleur d'ouverture et par l'endpoint de consultation des
fonctionnalites actives.

Il lit RATTRAPAGE_ACTIF dans parametre_systeme et retourne un
booleen.

Question a trancher, comme pour le seuil du Sprint 4.3 : que faire si
le parametre est absent ? Une absence ne doit jamais etre interpretee
comme actif : le comportement par defaut, en l'absence du parametre,
doit etre ferme. Confirme-moi cette lecture avant de coder.

Montre le service.
```

### Étape 3. Endpoint des fonctionnalités actives

```
Cree l'endpoint GET /parametres/fonctionnalites, conforme au
dispositif :

Retourne rattrapageActif, lu via le service de l'etape 2. Accessible
a tout utilisateur authentifie, sans restriction de role : le
frontend en a besoin des la connexion pour savoir quoi afficher.

Montre le controleur.
```

### Étape 4. Liste des contrôles d'ouverture

```
Avant tout code d'ouverture, enumere avec moi les controles qu'une
ouverture d'etat complementaire doit passer, dans l'ordre :

1. Drapeau RATTRAPAGE_ACTIF actif.
2. Origine existante.
3. Origine cloturee.
4. Origine dans le delai de regularisation, lu via
   DELAI_REGULARISATION_JOURS.
5. Meme unite.
6. Meme periode.

Complete cette liste si tu identifies autre chose, et dis-moi pour
chaque controle quel code d'erreur du contrat d'api s'applique. Le
controle 1 utilise le code FONCTIONNALITE_NON_OUVERTE, distinct d'un
refus d'habilitation ou d'un refus metier ordinaire. Attends ma
validation.
```

### Étape 5. Service d'ouverture

```
Une fois la liste des controles validee, implemente le service
d'ouverture :

1. Verifie le drapeau RATTRAPAGE_ACTIF. S'il est ferme, refuse
   immediatement avec FONCTIONNALITE_NON_OUVERTE, avant toute autre
   verification -- inutile de contreoler l'habilitation ou l'origine
   si la fonctionnalite meme n'est pas ouverte.
2. Verifie l'habilitation : role AGENT_UNITE, portee d'acces sur
   l'unite de l'etat d'origine.
3. Applique les controles 2 a 6 de l'etape 4.
4. Exige un motif d'ouverture non vide. Comme le motif de retour du
   Sprint 4.4, une chaine d'espaces n'est pas un motif.
5. Cree le processus : type COMPLEMENTAIRE, id_processus_origine
   renseigne, meme code unite, meme mois et meme annee que l'origine,
   statut EN_COURS_SAISIE.
6. Ne touche a AUCUN champ de l'etat d'origine.
7. Trace l'ouverture avec le motif.

Le point 6 est essentiel : l'etat d'origine reste figé, avec ses
signatures, sa date de cloture et son indicateur de transmission.

Montre le service.
```

### Étape 6. Levée du refus du Sprint 4.1

```
Le Sprint 4.1 refusait le type COMPLEMENTAIRE avec un message
indiquant que la fonctionnalite n'etait pas ouverte. Ce refus est
desormais porte par le drapeau plutot que par un refus systematique :
remplace-le par l'appel au service d'ouverture de l'etape 5, qui
integre deja le controle du drapeau en premiere position.

Verifie que l'endpoint POST /processus continue de refuser :
- un second processus NORMAL sur une periode deja couverte
- un etat complementaire sans identifiant d'origine

Montre les modifications du controleur.
```

### Étape 7. Saisie sur l'état complémentaire

```
Verifie que la saisie fonctionne sur un etat complementaire sans
modification du service Saisie.

Le service Saisie verifie qu'un etat est modifiable, mecanisme du
Sprint 3.3. Un etat complementaire au statut EN_COURS_SAISIE doit
donc etre modifiable comme un etat normal.

Si une adaptation s'avere necessaire, montre-la moi et explique
pourquoi elle ne pouvait pas etre evitee : l'objectif est que le
service Saisie ignore la distinction entre les deux types.

Le controle d'unicite inter-etats, RG-15, est le sous-sprint suivant.
Ne l'implemente pas ici.
```

### Étape 8. Circuit de validation

```
Verifie que le circuit de validation s'applique a l'identique :
soumission, validation du Chef d'Unite, aiguillage au seuil sur le
montant de l'etat complementaire, validation du Directeur Reseau
au-dela, cloture.

Point d'attention : le montant compare au seuil est celui de l'etat
complementaire seul, pas le cumul avec l'etat d'origine. Un
complement de 5 000 XAF sur un mois ou l'etat initial atteignait
120 000 XAF se cloture donc directement.

Confirme-moi que c'est bien la lecture attendue avant de la figer :
c'est une consequence non evidente de RG-08 appliquee aux etats
complementaires, et le metier pourrait attendre l'inverse.
```

### Étape 9. Tests

```
Ecris les tests de ce sous-sprint.

Drapeau :
1. drapeau ferme (valeur par defaut) : ouverture refusee avec
   FONCTIONNALITE_NON_OUVERTE, quel que soit le reste de la demande
2. drapeau ferme : le refus intervient avant toute autre verification,
   meme sur une origine inexistante
3. drapeau ouvert : le controle passe a la suite des verifications
4. parametre absent de la base : comportement ferme par defaut,
   conforme a la decision de l'etape 2

Les tests 1 et 2 sont les plus importants du sous-sprint : ils
prouvent que la fonctionnalite reste bien fermee tant que le metier
n'a pas tranche.

Ouverture, drapeau ouvert pour les besoins du test :
5. ouverture nominale sur un etat d'origine cloture : type
   COMPLEMENTAIRE, origine referencee, motif enregistre
6. l'etat d'origine est inchange apres l'ouverture : statut, date de
   cloture, signatures, indicateur de transmission identiques
7. origine inexistante : 404
8. origine non clotured : refuse
9. origine hors delai de regularisation : refuse
10. origine appartenant a une autre unite : refuse
11. motif vide ou compose d'espaces : refuse
12. ouverture par un role autre qu'AGENT_UNITE : 403
13. plusieurs etats complementaires sur la meme periode : autorises
14. un second etat NORMAL sur la meme periode : toujours refuse

Le test 6 reste le plus important apres les tests 1 et 2 : il prouve
que l'etat d'origine reste intact.

Circuit :
15. saisie sur un etat complementaire : fonctionne sans modification
    du service Saisie
16. soumission et validation d'un etat complementaire : circuit
    identique
17. aiguillage sur le montant du complementaire seul, conformement a
    la lecture confirmee a l'etape 8

Endpoint de fonctionnalites :
18. GET /parametres/fonctionnalites retourne rattrapageActif conforme
    a l'etat du parametre en base

Montre les fichiers de test.
```

## 7. Fichiers à créer ou modifier

| Chemin | Nature |
|---|---|
| `service-workflow/.../db/migration/V4__parametres_rattrapage.sql` | Migration, drapeau et délai |
| `service-workflow/.../application/FonctionnaliteService.java` | Création |
| `service-workflow/.../api/ParametreController.java` | Création, endpoint des fonctionnalités |
| `service-workflow/.../application/OuvertureComplementaireService.java` | Création |
| `service-workflow/.../api/dto/OuvertureComplementaireRequest.java` | DTO d'entrée |
| `service-workflow/.../application/ProcessusService.java` | Levée du refus |
| `service-workflow/.../api/ProcessusController.java` | Modification |
| `service-workflow/src/test/...` | Tests |

## 8. Commandes terminal

```bash
cd afb-rations/backend

mvn -pl service-workflow test
mvn -pl service-workflow spring-boot:run
```

Vérification du drapeau fermé :

```bash
curl -H "Authorization: Bearer <jeton_agent>" \
  http://localhost:8084/parametres/fonctionnalites
```

Attendu : `{"rattrapageActif": false}`.

```bash
curl -X POST -H "Authorization: Bearer <jeton_agent>" \
  -H "Content-Type: application/json" \
  -d '{"moisPaiement":7,"anneePaiement":2026,"codeUnite":"00002","typeProcessus":"COMPLEMENTAIRE","idProcessusOrigine":12,"motifOuverture":"MBARGA Jean omis les 10 et 15 juillet"}' \
  http://localhost:8084/processus
```

Attendu : refus `FONCTIONNALITE_NON_OUVERTE`.

Ouverture du drapeau pour tester le reste du parcours :

```sql
\c rations_workflow
UPDATE parametre_systeme SET valeur = 'true' WHERE code = 'RATTRAPAGE_ACTIF';
```

Rejouer l'appel précédent : attendu, cette fois, un traitement normal selon les autres contrôles.

Contrôle de l'intégrité de l'origine :

```sql
SELECT id, type_processus, id_processus_origine, statut, date_cloture, transmis_comptabilite
FROM processus_mensuel
WHERE code_unite = '00002' AND mois_paiement = 7 AND annee_paiement = 2026;
```

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn -pl service-workflow test` | BUILD SUCCESS, aucune régression |
| Dix-huit tests du sous-sprint | Tous passants |
| Drapeau fermé par défaut | Ouverture systématiquement refusée |
| Refus du drapeau prioritaire sur les autres contrôles | Vérifié |
| Endpoint de fonctionnalités actives | Reflète l'état réel du paramètre |
| Drapeau ouvert, état d'origine après ouverture | Strictement inchangé |
| Origine non clôturée ou hors délai | Ouverture refusée |
| Motif vide | Refusé |
| Plusieurs complémentaires sur une période | Autorisés |
| Second état normal | Toujours refusé |
| Saisie sur complémentaire | Fonctionne sans modification du service Saisie |
| Circuit de validation | Identique à un état normal |
| Aucune entité Réclamation | Vérifié |
| Aucune liste de bénéficiaires attendus | Vérifié |

## 10. Points de vigilance

- **Le drapeau se vérifie en premier, avant tout autre contrôle.** Un refus d'habilitation ou d'origine inexistante affiché avant le refus de fonctionnalité laisserait croire que la fonctionnalité est ouverte et que seul un détail bloque la demande.
- Un paramètre absent de la base doit être traité comme fermé, jamais comme ouvert. L'absence ne doit jamais être interprétée en faveur de l'exécution d'une fonctionnalité sensible.
- Le code `FONCTIONNALITE_NON_OUVERTE` est distinct du code de refus d'habilitation. Confondre les deux enverrait l'agent réclamer un droit qu'une autre personne ne peut pas lui accorder.
- L'état d'origine ne doit jamais être modifié, drapeau ouvert ou fermé. C'est tout l'intérêt de la solution retenue.
- Ne pas recréer l'entité `Reclamation` ni construire de liste de bénéficiaires attendus : ces éléments ont été explicitement écartés du modèle.
- L'index partiel du Sprint 0.5 autorise plusieurs états complémentaires mais un seul état normal par période.
- Le contrôle d'unicité inter-états est le sous-sprint suivant. Le drapeau protège contre l'exposition prématurée de la fonctionnalité, mais tant que RG-15 n'existe pas, ouvrir le drapeau en test réel resterait risqué : ne l'ouvrir qu'en base de test isolée.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Migration des deux paramètres créée | Fait |
| Comportement par défaut fermé confirmé | Fait |
| Endpoint de fonctionnalités actives fonctionnel | Vérifié |
| Drapeau vérifié en priorité sur tout autre contrôle | Vérifié |
| Liste des contrôles d'ouverture arbitrée | Fait |
| État d'origine strictement inchangé | Vérifié |
| Motif obligatoire et non vide | Vérifié |
| Asymétrie normal et complémentaire respectée | Vérifié |
| Saisie fonctionnant sans modification du service Saisie | Vérifié |
| Circuit de validation identique | Vérifié |
| Lecture de l'aiguillage confirmée | Fait |
| Aucune entité Réclamation ni liste de bénéficiaires | Vérifié |
| Dix-huit tests passants | Vérifié |

## 12. Commit

```bash
git add .
git commit -m "sprint-6bis.1: ouverture d'un etat complementaire derriere un drapeau

- Parametres rattrapage_actif et delai_regularisation_jours
- Endpoint des fonctionnalites actives pour le frontend
- Ouverture fermee par defaut, refus prioritaire et code dedie
- Etat d'origine jamais rouvert, circuit de validation identique

Refs: US-17, CT-34, CT-37, docs/dispositifs-provisoires.md"
```

---

**Fin du Sprint 6bis.1** — en attente de validation avant le Sprint 6bis.2. La fonctionnalité reste fermée en production tant que le registre des points en attente ne signale pas M-01 et M-02 comme résolus.
