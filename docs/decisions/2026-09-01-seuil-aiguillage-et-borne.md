# Seuil d'aiguillage : lecture, borne exacte et refus (RG-08)

**Sprint 4.3 — 1er septembre 2026 — service Workflow**

Statut : **appliqué**. Décisions tranchées avec l'utilisateur avant tout codage.

---

## 1. Le problème

RG-08 commande le niveau d'approbation requis pour engager la banque : après la
validation du Chef d'Unité, un état d'au plus le seuil est clôturé directement,
au-delà il monte au Directeur Réseau. C'est un mécanisme de **contrôle interne**,
pas une commodité fonctionnelle.

Deux exigences en découlent, et une erreur sur l'une comme sur l'autre passe
inaperçue en test superficiel :

1. **le seuil se lit dans `parametre_systeme`, jamais en dur** — sans quoi la
   banque ne peut plus changer son niveau d'approbation sans redéploiement, et la
   ligne en base devient mensongère tout en laissant croire qu'elle gouverne ;
2. **la comparaison doit être exacte à la borne** — une comparaison stricte au lieu
   d'une comparaison large envoie au Directeur Réseau un état qui aurait dû être
   clos, et l'inverse clôt sans l'approbation requise un état qui la demandait.

---

## 2. Le comportement à la borne, confirmé avant codage

| Montant total enregistré | Décision |
| --- | --- |
| seuil − 1 | `CLOTURE` (clôture directe) |
| **seuil exactement** | **`CLOTURE`** (clôture directe) |
| **seuil + 1** | **`EN_ATTENTE_DR`** (montée au Directeur Réseau) |

---

## 3. Comment la comparaison est écrite pour ne pas pouvoir s'inverser

Trois dispositifs, dans cet ordre.

**Une seule comparaison, dans tout le module.** `AiguillageService.aiguiller` porte
la seule ligne qui compare un montant à un seuil. Ni `ValidationService`, ni
`EnregistrementValidation`, ni le contrôleur, ni la machine à états n'en refont une :
ils reçoivent une décision déjà prise et l'appliquent. Une comparaison qui n'existe
qu'à un endroit ne peut pas diverger d'elle-même, et une revue de RG-08 se fait en
lisant un seul fichier.

**Le sens est porté par les noms, pas par l'opérateur.** La comparaison s'écrit
`montantTotal > seuil` et rend `ENVOI_DIRECTEUR_RESEAU` — mot pour mot la phrase de
RG-08. La décision est une énumération à deux valeurs nommées d'après leur
*conséquence* (`SOUS_SEUIL_CLOTURE_DIRECTE`, `ENVOI_DIRECTEUR_RESEAU`), et non un
booléen `depasseLeSeuil` qu'on lit correctement une fois sur deux. Un `>=` posé là
par distraction devient **visiblement** faux à la relecture, pas seulement faux à
l'exécution.

**La preuve par balayage, et pas par trois points.** Au-delà des cas nommés,
`AiguillageServiceTest.uneSeuleBasculeALaBorne` parcourt une plage de montants
encadrant le seuil et exige que la décision **bascule exactement une fois**, au
passage de `seuil` à `seuil + 1`. Une inversion, un décalage d'une unité ou une
comparaison retournée déplacent ce point de bascule et font tomber le test — *même
si les cas nommés avaient été « ajustés » en même temps pour les faire repasser*.
C'est ce qui distingue un test qui constate d'un test qui verrouille.

**Le `switch` d'application est exhaustif.** `EnregistrementValidation` traduit la
décision en transition sur une énumération sans branche par défaut : ajouter un jour
une troisième issue ferait échouer la compilation, plutôt que de laisser une décision
sans effet.

---

## 4. Les trois décisions sur le paramètre

### 4.1 Paramètre absent ou désactivé → refus, jamais une valeur de repli

`500 SEUIL_INDISPONIBLE`, journalisé au préfixe repérable `SEUIL INDISPONIBLE`
(même convention que `INCOHERENCE GRILLE` au Sprint 2.4 et `AUDIT PERDU` dans
`rations-audit-commun`).

**Motif.** Une valeur par défaut dans le code réintroduit exactement ce que RG-08
interdit. L'option « tout envoyer au Directeur Réseau » avait l'attrait d'être
conservatrice au sens du contrôle interne — plus d'approbation, pas moins — mais
elle appliquerait une règle inventée : un état de 5 000 FCFA partirait au DR alors
que RG-08 dit le contraire, et personne ne verrait que le paramètre a disparu.
Devant une configuration qu'il ne sait pas interpréter, le service **signale, il
n'arbitre jamais**.

**Compromis assumé.** Une erreur de configuration bloque tout le circuit de
validation. C'est le prix d'une erreur visible immédiatement plutôt que
d'aiguillages faux pendant des semaines. Ce point de défaillance unique est inscrit
comme **surveillance prioritaire en production** dans `docs/points-en-attente.md`, à
la demande de l'utilisateur.

**Code HTTP : `500` et non `422`.** Le chef d'unité n'a commis aucune erreur et n'a
rien à corriger dans son dossier ; c'est la configuration du module qui est en
défaut. Le présenter comme un refus métier l'enverrait chercher une faute
inexistante.

### 4.2 Valeur illisible → le même refus, avec le même format

Un seul chemin d'échec, donc un seul comportement à connaître.

La lecture est **stricte** : `Long.parseLong` sur la valeur débarrassée de ses
espaces de bord. Cette seule capture couvre le texte, la chaîne vide, le séparateur
de milliers, la décimale, la notation exponentielle **et le dépassement de
capacité** ; le signe est vérifié juste après. **Aucune exception brute ne peut
remonter au client** : `SeuilIndisponibleException` est l'unique sortie en échec.

Trois refus méritent leur motif :

- `"100 000"` — se lit comme cent mille, mais rien ne garantit que c'est ce qui a
  été voulu ; deviner reviendrait à fabriquer un seuil ;
- une valeur négative — elle ferait monter **absolument tout** au Directeur Réseau
  sans jamais produire d'erreur, et ce détournement du circuit passerait inaperçu ;
- un dépassement de capacité — il retombe sur le même refus, jamais sur une erreur
  technique nue.

**Un seuil de zéro est en revanche accepté** : c'est une règle intelligible — tout
état non nul requiert l'approbation du Directeur Réseau.

**Écarté : la vérification au démarrage du service.** Séduisante, mais elle protège
mal — le paramètre peut être modifié en base pendant que le service tourne, ce que
CT-18 fait précisément, et un contrôle au démarrage ne le verrait jamais.

### 4.3 Relu à chaque validation, jamais mis en cache

CT-18 exige qu'une modification du seuil prenne effet immédiatement. C'est aussi la
doctrine du Sprint 1.3 pour l'habilitation : **aucun verdict de contrôle interne ne
se sert d'une valeur potentiellement périmée**.

Le coût est un `SELECT` sur une table de trois lignes indexée par code, dans un geste
mensuel qui enchaîne déjà deux appels réseau de plusieurs secondes. Il est invisible.

---

## 5. L'ordre des opérations : le seuil est lu avant l'estampage

```
HORS TRANSACTION
  1. charger le processus                        -> 404
  2. habilitation sur l'unité DU PROCESSUS       -> 403 / 503
  3. statut : EN_ATTENTE_DA uniquement           -> 422
  4. la pièce jointe existe                      -> 500
  5. profil du valideur (GET /identite/moi)      -> 403 / 503
  6. [ point d'accroche RG-12, sous-sprint 4.4 ]
  7. AIGUILLAGE : lecture du seuil, décision     -> 500
  8. ESTAMPER, ÉCRIRE, CONFIRMER                 -> 500

TRANSACTION (EnregistrementValidation)
  9. relire, revalider le statut et le montant (concurrence)
 10. étape VALIDATION_DA, VALIDEE, signée
 11. pièce jointe : nombre_signatures passe à deux
 12. transition CLOTURE ou EN_ATTENTE_DR
 13. audit (publié après le commit)
```

**Pourquoi l'aiguillage précède l'écriture.** Un seuil illisible doit arrêter la
validation **avant** qu'un visa ne soit gravé dans le PDF. Dans l'ordre inverse, un
paramètre absent laisserait un document estampé d'une signature que la base ne
connaîtrait jamais : la pièce archivée affirmerait une validation qui n'a pas eu
lieu. Même raisonnement qu'au Sprint 4.2 pour la complétude — tous les refus
possibles sont épuisés avant la première écriture. Le test
`ValidationServiceTest.seuilIllisible` vérifie que la taille du fichier ne bouge pas.

**Le bloc transactionnel vit dans une classe à part**, `EnregistrementValidation`,
pour la raison technique du Sprint 4.2 : `@Transactional` sur une méthode appelée
depuis la même classe n'a aucun effet avec les mandataires Spring, et rien ne le
signalerait.

---

## 6. Le montant comparé est celui qui est enregistré

`processus_mensuel.montant_total`, reporté à la soumission depuis l'état consolidé.
**Jamais un montant redemandé au service Saisie à l'instant de la validation** : une
grille tarifaire modifiée entretemps ferait dépendre l'aiguillage d'un événement
étranger au dossier, et le chef d'unité validerait un montant différent de celui
qu'il a lu et signé.

Une garde de deux lignes dans la transaction (`exigerMontantInchange`) rend
l'invariant explicite : la décision appliquée est celle qui correspond au montant
encore enregistré. Elle ne devrait jamais se déclencher — le montant n'est écrit
qu'à la soumission — et c'est précisément pourquoi elle est peu coûteuse. Sans elle,
l'invariant reposerait sur un raisonnement juste aujourd'hui et fragile à la
prochaine évolution du circuit.

---

## 7. La clôture ne transmet rien

`transmis_comptabilite` n'est jamais touché : il reste à `false`. La publication sur
`rations.etat.valide` est le Sprint 5, et c'est elle qui posera le drapeau. Le poser
dès la clôture contournerait le verrou de transmission unique de RG-13 : l'état
paraîtrait transmis avant de l'être, et la transmission réelle serait ensuite refusée
comme un doublon.

**Pas de colonne `date_cloture`.** Le guide 4.3 étape 5 demande de la renseigner ;
elle n'existe ni au dictionnaire (CLAUDE.md §4) ni dans la migration V1, et le
Sprint 4.1 a explicitement tranché de ne pas l'ajouter (CLAUDE.md §17). Hibernate
tourne en `ddl-auto: validate` : l'ajouter à l'entité sans migration ferait échouer
le démarrage. L'instant de la clôture est porté par `etape_workflow.date_creation` de
l'étape `VALIDATION_DA` — la ligne qui l'a provoquée — et par le journal d'audit.
Décision maintenue, sans nouvelle migration.

---

## 8. Le contrat d'API

`ValidationResponse` rend les **cinq champs de l'exemple du contrat section 5**, aux
mêmes noms et aux mêmes types : `idProcessus`, `statut`, `montantTotal`,
`aiguillage`, `seuilApplique`. Deux blocs de témoignage s'y ajoutent —
`pieceJointe` et `etape` —, sur le modèle **additif** du champ `manques` au
Sprint 4.2 : les champs du contrat restent présents et inchangés, l'ajout ne retire
rien.

**Pourquoi la réponse rend le seuil.** Le contrat l'exige, et pour une raison de
fond : le chef d'unité doit voir **sur quelle règle** son dossier a été aiguillé.
Rendre le seul statut lui laisserait constater qu'un état monte au Directeur Réseau
sans pouvoir savoir si c'est le seuil qui a changé ou son montant — et il n'aurait,
pour trancher, qu'à interroger un administrateur.

**Un code ajouté au contrat : `SEUIL_INDISPONIBLE` (500).**

**Rôle : `CHEF_UNITE_DA` seul, à ce sous-sprint.** Le contrat destine l'endpoint aux
deux valideurs, « DA ou DR selon le niveau ». Le second niveau est le sous-sprint
4.4 ; ouvrir le rôle du Directeur Réseau avant de servir la transition
`EN_ATTENTE_DR → CLOTURE` le placerait devant un refus de statut incompréhensible.

---

## 9. L'audit porte le seuil, pas seulement la décision

L'événement `VALIDATION_PROCESSUS` enregistre le statut avant/après, **le montant
total et le seuil appliqué**. Un contrôle interne qui relit cette trace dans six mois
doit pouvoir refaire la comparaison lui-même, même si le paramètre a changé depuis :
sans le seuil du jour, la trace dirait qu'un état de 84 000 FCFA a été clôturé, sans
permettre de juger si c'était la bonne décision.

C'est aussi le premier événement de ce service, après la soumission, à porter un
`idUtilisateur` : `GET /identite/moi` est appelé de toute façon, `etape_workflow.id_acteur`
étant `NOT NULL`.

---

## 10. Ce qui n'est pas de ce sous-sprint

- **RG-12, séparation des tâches** — sous-sprint 4.4. Le point d'accroche est marqué
  à sa place exacte dans `ValidationService` : le profil du valideur, donc son
  identifiant local, y est déjà connu, rien n'a encore été écrit, et
  `EtapeWorkflowRepository.findByIdProcessusAndIdActeur` existe depuis le Sprint 4.1.
  Il n'y a rien à réorganiser pour l'y insérer.
- **Le retour à l'agent** (RG-10, RG-11) et **la validation du Directeur Réseau** —
  sous-sprint 4.4.
- **La transmission comptable** — Sprint 5.

---

## 11. Conséquences pour les sprints suivants

| Sprint | Conséquence |
| --- | --- |
| **4.4** | Le second niveau réutilise `AiguillageService` ? **Non** : depuis `EN_ATTENTE_DR`, la seule issue de validation est la clôture — il n'y a plus de seuil à comparer. L'endpoint devra en revanche router selon le rôle du valideur, et le point d'accroche RG-12 devra être servi pour les **deux** niveaux. `ordreEtapeSuivant` calcule déjà le rang à partir du dernier pas connu, ce qui reste juste après un retour puis une resoumission. |
| **5** | La transmission comptable posera `transmis_comptabilite`. Elle est déclenchée par le service Workflow à la clôture : les deux points de clôture (`SOUS_SEUIL_CLOTURE_DIRECTE` ici, validation DR au 4.4) devront tous deux la déclencher, et **une seule fois** (RG-13). |
| **6** | Le reporting affichera le seuil appliqué à chaque dossier ; il est dans l'audit, pas sur `processus_mensuel`. Si le métier veut le voir sur l'état, il faudra une colonne — décision à prendre à ce moment, pas ici. |
| **9.2** | Le cahier de recette rejoue CT-14, CT-15 et CT-18 à la main. CT-18 demande de modifier `SEUIL_AIGUILLAGE_DR` en base entre deux validations : le module n'exposant aucun endpoint d'administration du paramètre, cela se fait en SQL direct. |
