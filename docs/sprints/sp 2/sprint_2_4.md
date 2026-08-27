# SPRINT 2.4

## Résolution du montant et clôture du Sprint 2

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Exposer la résolution du montant applicable, consommée par le service Saisie |
| **Livrable** | Endpoint interne de résolution, convention d'appel documentée, clôture du sprint |
| **Durée** | Une journée |
| **Prérequis** | Sprint 2.3 validé et commité |
| **Sprint suivant** | 3.1, domaine de la saisie |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Résolution du montant (étapes 2-3) | Opus | Élevé |
| Convention d'appel et clôture (étapes 4-6) | Sonnet | Moyen |

**Changement manuel à l'étape 4.** Les trois premières étapes portent RG-03 et justifient Opus. À partir de la convention d'appel, revenir en Sonnet effort moyen.

## 2. Outil de cartographie

Utilisation recommandée à l'étape 5, pour confirmer que le service Grilles reste autonome et que la convention d'appel ne crée aucun couplage.

```
py -3.14 -m graphify update .
```

## 3. Contexte

Dernier sous-sprint du service Grilles. Les trois précédents ont construit le cycle de vie complet d'une grille. Celui-ci rend le service utile au reste du module.

RG-03 dit que le montant d'une ligne est repris de la grille active, jamais saisi. Concrètement, au Sprint 3, le service Saisie devra demander au service Grilles : quel montant s'applique à une ration de jour le 10 juillet ? La réponse dépend de la grille en vigueur **à cette date**, pas de la grille en vigueur aujourd'hui. Cette nuance compte dès qu'une saisie porte sur une journée passée, et davantage encore au Sprint 6bis avec les états complémentaires.

Ce sous-sprint clôt aussi le Sprint 2 : vérification de bout en bout et mise à jour de CLAUDE.md.

## 4. Objectifs

- `GET /grilles/active` : résolution du montant pour un couple nature et session à une date donnée
- Réponse explicite quand aucune grille ne couvre la date demandée
- Convention d'appel documentée pour le service Saisie
- Documentation Swagger complète du service Grilles
- Vérification de bout en bout et clôture du Sprint 2

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| RG-03 | Montant repris de la grille active, jamais saisi manuellement |
| US-05 | Refus d'une ligne sans grille active |
| CT-10 | Ligne refusée quand aucune grille ne couvre la combinaison |
| CT-29 | Après validation d'une grille, le montant appliqué est celui de la nouvelle |

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, section 6, regle
RG-03. Confirme en 3 lignes ce qu'elle impose.

CONTEXTE DE CETTE SESSION : Sprint 2.4, dernier sous-sprint du
service Grilles. Le cycle de vie complet existe depuis les
sous-sprints 2.1 a 2.3. On expose maintenant la resolution du
montant, que le service Saisie consommera au Sprint 3.
SERVICE CONCERNE : service-grilles uniquement.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Chaque methode publique vient avec ses tests.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : avant tout code, precise-moi le comportement attendu
de la resolution dans quatre situations : une grille couvre la date
demandee ; aucune grille ne la couvre ; la date demandee est
anterieure a toute grille connue ; deux grilles successives se
touchent exactement a la date demandee. Pour chacune, dis-moi ce que
l'api doit repondre et pourquoi. Attends ma validation.
```

### Étape 2. Service de résolution

```
Une fois le comportement valide, implemente le service de resolution :

Entree : nature, session, date de la prestation.
Sortie : le montant applicable et la grille qui l'a fourni.

La grille retenue est celle dont le statut est ACTIVE, dont la
date_debut est inferieure ou egale a la date demandee, et dont la
date_fin est nulle ou posterieure a cette date.

Quand aucune grille ne couvre la date, le service ne retourne pas
zero et ne leve pas une erreur technique : il retourne une reponse
explicite d'indisponibilite, que le service Saisie traduira en refus
de ligne.

Montre le service puis ses tests.
```

### Étape 3. Endpoint de résolution

```
Cree l'endpoint GET /grilles/active, conforme au contrat d'api,
section 4. Parametres : nature, session, date.

Deux questions a trancher avant d'ecrire :
1. La date est-elle obligatoire, ou vaut-elle la date du jour par
   defaut ? Une valeur par defaut simplifie l'appel courant mais
   masque une erreur si l'appelant oublie de la transmettre pour une
   saisie retroactive.
2. Comment cet endpoint est-il protege ? Il est decrit comme a usage
   interne, mais il est appele au nom d'un utilisateur qui saisit.
   Applique la decision prise au Sprint 1.3 sur la propagation du
   jeton.

Presente les options, attends ma decision.
Montre le controleur.
```

### Étape 4. Convention d'appel

```
Redige dans docs/ la note de convention destinee au service Saisie :

- Chemin, parametres, forme de la reponse.
- Comportement attendu cote appelant quand la grille est
  indisponible : refus de la ligne avec le code du contrat d'api,
  message explicite pour l'agent.
- Comportement attendu quand le service Grilles est injoignable.
  Question a me poser si CLAUDE.md ne tranche pas : refuse-t-on la
  saisie, ou accepte-t-on la ligne sans montant pour la valoriser
  plus tard ? Les deux ont des consequences lourdes, presente-les.

N'ecris aucun client dans service-saisie : il n'a pas encore de code.
```

### Étape 5. Documentation et vérification

```
Complete la documentation Springdoc du service Grilles : les cinq
endpoints du contrat d'api, avec leur role requis, leurs codes de
retour et un exemple de reponse.

Puis lance la cartographie et verifie :
- que service-grilles ne depend d'aucun autre service
- que les cinq endpoints du contrat existent, ni plus ni moins
- qu'aucun endpoint ne permet de modifier une grille ACTIVE
  directement

Liste les ecarts sans les corriger.
```

### Étape 6. Clôture du Sprint 2

```
Mets a jour CLAUDE.md avec les decisions prises pendant le Sprint 2 :
1. Le traitement d'une modification de grille active, ligne nouvelle
   ou modification en place (Sprint 2.2).
2. Le mode d'obtention du libelle createur sans dependance croisee
   (Sprint 2.2).
3. La date_fin posee sur l'ancienne grille lors d'une bascule
   (Sprint 2.3).
4. Le comportement de la resolution aux bornes de dates et en cas
   d'indisponibilite (Sprint 2.4).
5. La protection de l'endpoint interne et la propagation du jeton.

Propose les ajouts section par section, sans reecrire le fichier.
```

## 7. Fichiers à créer ou modifier

| Chemin | Nature |
|---|---|
| `service-grilles/.../api/dto/MontantApplicableResponse.java` | DTO de sortie |
| `service-grilles/.../application/ResolutionMontantService.java` | Résolution RG-03 |
| `service-grilles/.../api/GrilleController.java` | Ajout de l'endpoint |
| `service-grilles/src/test/.../ResolutionMontantServiceTest.java` | Tests |
| `docs/appel-resolution-montant.md` | Convention pour le service Saisie |
| `CLAUDE.md` | Décisions du Sprint 2 |

## 8. Commandes terminal

```bash
cd afb-rations/backend

mvn -pl service-grilles test
mvn -pl service-grilles spring-boot:run
```

Vérifications manuelles :

```bash
curl -H "Authorization: Bearer <jeton>" \
  "http://localhost:8083/grilles/active?nature=RATION&session=JOUR&date=2026-08-18"

curl -H "Authorization: Bearer <jeton>" \
  "http://localhost:8083/grilles/active?nature=RATION&session=JOUR&date=2020-01-01"
```

Attendu : un montant sur le premier appel, une réponse d'indisponibilité sur le second.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn -pl service-grilles test` | BUILD SUCCESS, aucune régression |
| Date couverte par une grille | Montant correct retourné |
| Date antérieure à toute grille | Indisponibilité explicite |
| Date après fermeture, sans remplaçante | Indisponibilité explicite |
| Date à la borne exacte entre deux grilles | Une seule grille retenue, comportement conforme à l'étape 1 |
| Après validation d'une nouvelle grille | Le montant retourné change à partir de sa date de début |
| Résolution sur une date passée | Retourne la grille de l'époque, pas la courante |
| Cinq endpoints du contrat | Ni plus ni moins |
| Aucune dépendance vers un autre service | Vérifié |

## 10. Points de vigilance

- **La résolution se fait à la date de la prestation, pas à la date du jour.** C'est le point le plus facile à manquer, et il ne se voit pas en test si toutes les saisies portent sur la journée courante. Le test sur date passée n'est pas optionnel.
- Retourner zéro quand aucune grille ne couvre la date serait une faute grave : une ligne serait enregistrée à montant nul sans que personne ne s'en aperçoive. L'indisponibilité doit être explicite et distincte d'un montant.
- Le comportement en cas d'indisponibilité du service Grilles engage la robustesse de toute la saisie. Accepter une ligne sans montant créerait des lignes à valoriser plus tard, avec le risque qu'elles partent en comptabilité sans montant.
- Les bornes de dates doivent être testées exactement. Un `<` au lieu d'un `<=` décale la bascule d'une journée, et cette journée-là produira un montant faux.
- Aucun endpoint ne doit permettre de modifier directement une grille ACTIVE. Toute évolution passe par une nouvelle proposition validée par la DRH.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Comportement aux quatre situations défini avant codage | Fait |
| Résolution à la date de la prestation, testée sur date passée | Vérifié |
| Indisponibilité explicite, distincte d'un montant nul | Vérifié |
| Décisions sur la date par défaut et la protection tranchées | Fait |
| Note de convention rédigée pour le service Saisie | Fait |
| Comportement en cas de service injoignable tranché | Fait |
| Cinq endpoints du contrat, documentés | Vérifié |
| Aucun endpoint modifiant une grille active | Vérifié |
| CLAUDE.md complété des cinq décisions du Sprint 2 | Fait |

## 12. Commit

```bash
git add .
git commit -m "sprint-2.4: resolution du montant applicable

- Resolution a la date de la prestation, pas a la date du jour
- Indisponibilite explicite quand aucune grille ne couvre la date
- Convention d'appel documentee pour le service saisie
- Cloture du sprint 2

Refs: RG-03, US-05, CT-10, CT-29"
```

---

**Fin du Sprint 2.4 et du Sprint 2.** Le service Grilles est complet. En attente de validation avant le Sprint 3.
