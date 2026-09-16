# Résumé Sprint 6bis.2 — Unicité inter-états, et clôture du Sprint 6bis

**Date :** 16 septembre 2026
**Position :** dernier sous-sprint de la régularisation, après les Mailles 1 et 2
**Objet :** implémenter RG-15, le seul rempart contre le double paiement en régularisation
**Livrable :** migration V6 (Saisie), contrôle local, `409 DOUBLON_INTER_ETATS`, garde de build

---

## En une phrase

RG-15 est en place — et le contrôle qui devait traverser deux services et coûter un appel
réseau **par ligne saisie** est devenu une requête locale indexée, parce que la contrainte
d'exclusion de la Maille 1 avait rendu la question inter-services sans objet.

---

## 1. L'écart au guide, et pourquoi il est fondé

Le guide 6bis.2 décrivait une « difficulté d'architecture » : le contrôle porte sur trois
tables réparties dans deux bases, aucune jointure SQL n'est possible, donc il faut un
endpoint côté Workflow et **un appel inter-services à chaque ligne saisie**. Le guide
s'en inquiétait lui-même face à la cible de 3 secondes.

**Cette difficulté n'existait plus.** Le raisonnement tient en trois lignes :

- deux états `NORMAL` d'une même unité ne peuvent plus se chevaucher — la base les refuse
  (`ex_processus_normal_sans_chevauchement`, Maille 1) ;
- un état `COMPLEMENTAIRE` recopie **exactement** les bornes de son origine (Sprint 6bis.1) ;
- donc tous les états d'une unité qui couvrent une journée donnée partagent la même période.

« Les états de l'unité qui portent cette journée » et « les états de l'unité sur cette
période » désignent le **même ensemble** — à ceci près que le premier se demande sans
connaître la période. Or `code_unite` et `date_jour` sont déjà sur `fiche_journaliere`,
recopiés et figés depuis le Sprint 3.1.

**Trois fichiers annoncés par le guide n'ont pas été écrits** : l'endpoint Workflow,
`ProcessusPeriodeClient`, `ProcessusPeriodeHttpClient`.

### Le Sprint 3.2 l'avait prévu, mot pour mot

La javadoc de `ControleDoublonService`, écrite trois sprints plus tôt, annonçait que RG-15
« s'appuiera sur les colonnes `code_unite`, `date_debut` et `date_fin` recopiées sur
`fiche_journaliere` — sans cette recopie, RG-15 exigerait un appel au service Workflow pour
chaque ligne saisie ». La prévision s'est vérifiée sur le fond comme sur le moyen.

Ce qui manquait pour s'en passer, c'était la garantie que « même journée » implique « même
période ». La Maille 1 l'a apportée sans le chercher.

## 2. La journée plutôt que la période — et c'est le plus sûr des deux

Le contrôle interroge `(code_unite, date_jour)`, pas les bornes. Ce n'est pas un raccourci
équivalent : c'est **strictement plus sûr**, et la base de développement en portait la preuve.

Une fiche peut porter une `date_jour` située **hors des bornes de son propre état**. C'est le
défaut que le contrôle de complétude refuse à la *soumission* (`LIGNE_HORS_PERIODE`, Sprint
4.2) — donc il existe tant que l'état n'est pas soumis. L'inventaire de `rations_saisie` en a
montré un exemplaire : une fiche datée **2020-01-01** logée dans l'état d'août 2026.

Interrogé par période, RG-15 ne verrait pas une telle ligne et la laisserait ressaisir
ailleurs. Interrogé par journée, il la voit.

**La dégradation est du bon côté.** Si la contrainte d'exclusion était un jour relâchée, le
contrôle deviendrait *plus large* que RG-15 — il refuserait une combinaison présente dans un
état d'une période seulement chevauchante. Il ne peut jamais devenir plus étroit, donc jamais
laisser passer un double paiement.

## 3. L'arbitrage que le guide laissait ouvert

*« Faut-il inclure les processus au statut `RETOURNE` et `EN_COURS_SAISIE` ? »*

**Ils sont inclus.** Une ligne saisie dans un état encore ouvert n'a pas été payée, mais elle
le sera si cet état aboutit ; l'exclure autoriserait deux saisies concurrentes de la même
prestation, dans deux états qui se clôtureraient tous deux.

Ce qui tranche est **l'asymétrie des erreurs** : un refus à tort se corrige en supprimant une
ligne — un état non clôturé reste modifiable (Sprint 3.3) —, un double paiement non.

**Et cette inclusion n'est pas un filtre à écrire : c'est l'absence de filtre.** C'est
précisément ce qui rend le contrôle réalisable sans interroger le Workflow, seul détenteur du
statut d'un processus. L'arbitrage métier et la simplification technique se rejoignent, ce
qui est rare et mérite d'être noté.

**Vérifié en réel**, et c'est la plus jolie preuve du sprint : le conflit détecté au second
complémentaire portait sur l'état **6005, qui était `EN_COURS_SAISIE`**. Un contrôle limité
aux états clos ne l'aurait pas vu.

## 4. Appliqué à tous les états, sans condition de type

La fiche ne porte pas `type_processus`, et le demander coûterait exactement l'appel réseau
qu'on vient d'économiser. Le comportement décrit par le guide — « sur un NORMAL, RG-04
seule » — est néanmoins tenu, **par démonstration plutôt que par un `if`** :

- un complémentaire exige une origine `CLOTURE` (Sprint 6bis.1) ;
- un second NORMAL couvrant la même journée est refusé par la base.

Sur un état NORMAL en cours de saisie, il n'existe donc **aucun autre état à trouver**. La
requête rend une liste vide : un no-op démontrable, pas une concession.

## 5. Un code distinct, parce que le geste est distinct

| Code | Ce qu'il dit | Ce que l'agent fait |
| --- | --- | --- |
| `409 DOUBLON_LIGNE` (RG-04) | « vous l'avez déjà saisie **ici** » | il regarde sa fiche, la ligne y est |
| `409 DOUBLON_INTER_ETATS` (RG-15) | « elle a déjà été servie **ailleurs** » | il regarde l'état nommé, le plus souvent clos et payé |

Sous le code de RG-04, l'agent chercherait dans sa propre fiche une ligne qui ne s'y trouve
pas, et conclurait à un défaut du module. Même raisonnement que les trois codes en `403` du
Sprint 4.4.

**Le message nomme l'état en conflit**, en plus des quatre éléments de la combinaison : c'est
la seule information qui permette à l'agent de vérifier l'affirmation du module, et au
contrôle interne de refaire le rapprochement.

## 6. Ce que le contrôle n'a pas, et qu'on assume

**RG-15 n'a aucun filet en base, à la différence de RG-04.** L'index unique de la migration
V4 peut garder RG-04 parce que sa combinaison vit dans une seule table ; RG-15 porte sur une
jointure `fiche × ligne`, qu'aucun index ne contraint.

Il subsiste donc une fenêtre entre la lecture et l'écriture : deux agents de la même unité
saisissant la même prestation au même instant, dans deux états différents. Cas nettement plus
rare que le double-clic que RG-04 rencontrait, et sans remède à coût raisonnable — le fermer
exigerait de verrouiller toutes les fiches de l'unité pour la journée.

## 7. Une garde de build plutôt qu'un commentaire

`Rg15SansAppelReseauTest` vérifie que `ControleDoublonService` ne dépend que du dépôt des
lignes, ne nomme aucun client sortant et ne lit aucun statut de processus.

Motif : la validité du raccourci repose sur un **raisonnement** qu'un futur lecteur peut
ignorer. Rien ne l'empêcherait alors d'ajouter « juste une vérification » auprès du Workflow —
ce qui rendrait la saisie d'une ligne dépendante d'un quatrième service synchrone. Même
discipline que `PerimetreDuModuleTest` (1.3) et `CleInterneJamaisJournaliseeTest` (5.2).

**Elle ne ferme rien d'utile** : si RG-15 devait vraiment consulter le Workflow un jour, elle
échouerait — et c'est exactement le moment de relire le raisonnement avant de la supprimer.

## 8. RG-13 face à la régularisation

Un complémentaire clôturé déclenche une transmission sur une période **déjà transmise**. RG-13
ne la bloque pas, et c'est structurel : le verrou vit sur la **ligne du processus**, jamais sur
le couple (unité, période). Deux processus distincts, deux lignes, deux verrous indépendants.

Un verrou porté par la période aurait rendu toute régularisation intransmissible —
c'est-à-dire impayée.

Prouvé deux fois : par un test contre la vraie base, et en réel (section 10).

## 9. Le chemin d'accès

**Migration V6 de la Saisie** : `idx_fiche_journaliere_unite_journee (code_unite, date_jour)`.

L'index de période de la V5 est **conservé** — il sert la recherche de lignes du Reporting,
qui filtre bien sur un intervalle.

**Mesure honnête du plan d'exécution :** sur les volumes actuels (18 fiches), PostgreSQL
choisit un *Seq Scan*, et c'est le bon choix — une table d'une page ne s'indexe pas. Forcé par
`enable_seqscan = off`, le planificateur prend bien
`Index Scan using idx_fiche_journaliere_unite_journee`. L'index est donc utilisable et sera
utilisé au volume de production ; le dire autrement aurait été une affirmation non vérifiée.

## 10. Vérification réelle

Six services réellement démarrés, jetons obtenus par grant `password` sur le realm
`afb-rations-dev`. État d'origine **109** (unité 00002, période du 1er au 30 septembre 2026,
`CLOTURE`, transmis) ayant payé à MBALLA une RATION de JOUR le **3 septembre**.

| Geste | Résultat |
| --- | --- |
| Ouverture du complémentaire **6005** sur 109 | `201`, bornes recopiées à l'identique |
| **Ressaisie exacte** (MBALLA / RATION / JOUR / 03-09) | **`409 DOUBLON_INTER_ETATS`**, message nommant **l'état 109** |
| Même jour, nature différente (TRANSPORT / JOUR) | `201` — régularisation légitime |
| Même jour, session différente (RATION / SOIR) | `201` |
| Journée différente (10-09, RATION / JOUR) | `201` |
| Le même doublon **dans la fiche courante** | `409 **DOUBLON_LIGNE**` — les deux codes se distinguent bien |
| Second complémentaire **6006**, ressaisie du TRANSPORT du 03-09 | `409 DOUBLON_INTER_ETATS` nommant **6005, `EN_COURS_SAISIE`** |
| Soumission, validation DA | `EN_ATTENTE_DR`, `aiguillage: COMPLEMENTAIRE_ENVOI_DIRECTEUR_RESEAU`, `seuilApplique: null` |
| Validation DR | `CLOTURE`, 3 signatures, `transmission: { transmis: true }` |
| **Charge lue sur le broker** | v2, `typeProcessus: COMPLEMENTAIRE`, 4 500 FCFA, **3 lignes** |
| Seconde demande de transmission sur 6005 | `200 DEJA_TRANSMIS`, aucune republication |
| Journal d'audit (`rations_audit`) | **216 événements**, dont les 7 du scénario |

**Le fait qui compte, dans la charge publiée :** les trois lignes sont exactement les trois
régularisations légitimes. La RATION de JOUR du 3 septembre, déjà payée par l'état 109, **n'y
figure pas**. Le double paiement a été empêché à la saisie, donc il ne peut pas atteindre le
message comptable.

**Deux états transmis sur la même période**, chacun unique pour lui-même :

```
  109 | NORMAL         | CLOTURE | 2026-09-01 | 2026-09-30 |  1500 | transmis
 6005 | COMPLEMENTAIRE | CLOTURE | 2026-09-01 | 2026-09-30 |  4500 | transmis
```

**Audit vérifié par requête, jamais par l'absence d'erreur** (doctrine du Sprint 6.3) : la
trace complète de 6005 est en base — `OUVERTURE_COMPLEMENTAIRE`, `SOUMISSION_PROCESSUS`, deux
`VALIDATION_PROCESSUS`, `TRANSMISSION_COMPTABLE`, `TRANSMISSION_ETAT_VALIDE`. La seconde
demande de transmission n'a produit **aucune** trace supplémentaire, conformément à la règle
qui veut qu'un rejeu ne fasse pas croire à un second traitement comptable.

Le refus RG-15, lui, n'est pas tracé — et c'est conforme : les `4xx` métier sont des erreurs
d'usage, pas des tentatives d'accès (doctrine 1.3).

## 11. Les tests

| Suite | Tests | État |
| --- | --- | --- |
| `rations-audit-commun` | 18 | Vert |
| service-identite | 61 | Vert |
| **service-saisie** | **96** (+15) | Vert |
| service-grilles | 99 | Vert |
| **service-workflow** | **345** (+1) | Vert |
| service-reporting | 33 | Vert |
| service-transmission | 98 | Vert |
| service-audit | 11 | Vert |
| **Total** | **761** | **BUILD SUCCESS** |

Les dix tests de `ControleDoublonServiceRg15Test` tournent **contre la vraie base**, pour la
même raison qu'au Sprint 3.2 : ce qui est en jeu n'est pas que le service délègue au dépôt,
mais que la requête distingue réellement six éléments — et cela ne se vérifie que sur une base
qui exécute le SQL.

**Les tests 3, 4 et 5 pèsent autant que le test 2.** Le second prouve que le rempart existe ;
les trois autres prouvent qu'il ne bloque pas les régularisations légitimes, qui sont la raison
d'être du sprint. Une règle d'unicité trop large ne produit pas de double paiement — elle
empêche un agent d'être payé pour une prestation réellement effectuée, et personne ne s'en
plaint dans les journaux.

Un onzième test, hors liste du guide, verrouille le cas de la section 2 : une ligne hors des
bornes de son propre état reste vue.

## 12. Une incohérence relevée, non corrigée

`POST /processus` rend `compteCharge: null` là où `GET /processus/{id}` rend bien
`64380090200`. Le paramètre est présent et actif ; c'est la réponse de **création** qui ne le
renseigne pas.

**Sans conséquence sur le paiement** — le service Transmission lit l'en-tête par `GET`, et la
charge publiée porte bien le compte (section 10). Mais un frontend qui afficherait le dossier
depuis la réponse de création verrait un champ vide sans raison. **Laissé en l'état, signalé
ici** : la corriger relève du sous-sprint qui servira cet écran, pas de RG-15.

## 12bis. Un couplage que la vérification réelle a mis au jour

Ouvrir `RATTRAPAGE_ACTIF` pour jouer le scénario a **fait échouer quatre tests** —
`FonctionnaliteServiceTest.fermeParDefaut` et trois d'`OuvertureComplementaireServiceTest`.
Aucun rapport avec RG-15 : ces tests lisent la **vraie base** et affirment que le drapeau
vaut `false`.

Le message du test dit « RATTRAPAGE_ACTIF vaut false depuis la migration V2 ». C'est vrai de
la *migration*, pas de la *base* : le drapeau est précisément conçu pour changer par `UPDATE`
sans redéploiement. Ces tests confondent donc **la valeur posée par la migration** et **la
valeur courante de l'environnement**, et ils tomberont le jour où le métier ouvrira le
drapeau légitimement — sur un build qui n'aura rien cassé.

**Non corrigé ici** : le geste juste est de faire poser la valeur au test lui-même plutôt que
de la subir, et cela touche des tests écrits au sous-sprint précédent. Signalé plutôt que
retouché en marge de RG-15 ; le drapeau a été remis à `false` et la suite est repassée au vert.

**Rappel d'hygiène de build, au passage.** Lancer `mvn test` pendant qu'un `spring-boot:run`
tient `target/classes` du même module produit des erreurs « Unresolved compilation problem »
au format du compilateur **Eclipse** — le piège documenté au Sprint 4.2. Devant une erreur de
compilation inattendue : arrêter le service, relancer avec `clean`, et seulement ensuite
chercher la cause dans le code.

## 13. Ce que ce sprint clôt, et ce qu'il n'ouvre pas

Le **Sprint 6bis est terminé** : ouverture contrôlée d'un état complémentaire (6bis.1) et
unicité inter-états (6bis.2). Le backend est fonctionnellement complet.

`RATTRAPAGE_ACTIF` a été ouvert le temps de la vérification réelle, puis **remis à `false`**
— tant parce que c'est la valeur de sa migration que parce que quatre tests s'y adossent
(section 12bis). L'ouvrir est un geste d'exploitation (`UPDATE`, effet immédiat, sans
redéploiement), jamais une livraison. Il n'y a désormais plus de raison **technique** de le
laisser fermé — **la décision d'ouverture reste au métier**, et elle n'est pas prise ici.

**Données de développement laissées en base** : les états complémentaires **6004** et **6006**,
vides. Le premier vient d'un premier appel dont la sortie avait été avalée par un filtre de
mise en forme — la requête avait bien abouti. Signalé plutôt que nettoyé en silence.

**Reste ouvert, sans rapport avec ce sprint :**

- **D-12** — l'extension PostgreSQL `btree_gist` à obtenir de la DSI avant le déploiement 8.3.
- Les deux questions DFT sans réponse explicite (partage du libellé, grain fin), appliquées
  « à défaut d'objection » et à reposer avant la première transmission réelle.
- `DELAI_REGULARISATION_JOURS` : valeur provisoire de 90 jours, à confirmer par le métier.

---

**Prochaine étape :** le **Sprint 7F.1**, socle du frontend. Quatre guides restants portent
encore du vocabulaire mensuel (7F.4, 9.3, 9.1) et sont à ajuster juste avant leur exécution ;
les sous-sprints 7F.5 à 7F.7 consommeront l'API du Reporting, dont les paramètres sont passés
de `periode=AAAA-MM` à `dateDebut`/`dateFin` à la Maille 1.
