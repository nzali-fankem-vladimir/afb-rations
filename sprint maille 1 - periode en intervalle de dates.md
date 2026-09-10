# SPRINT MAILLE 1

## La période devient un intervalle de dates — représentation interne

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Remplacer le couple `(mois_paiement, annee_paiement)` par `(date_debut, date_fin)` dans les bases Workflow et Saisie, et dans tout ce qui les lit |
| **Livrable** | Deux migrations, le contrôle de période réécrit, les contrats de fil transposés, un garde-fou côté Transmission |
| **Durée** | Trois journées |
| **Prérequis** | Sprint 6bis.1 validé et commité ; sprint d'ajustement métier clos (M-04 tranché sur la forme) |
| **Sprint suivant** | Maille 2 (charge comptable), puis 6bis.2 — les deux se débloquent à la fin de celui-ci |

## Pourquoi ce sprint existe, et pourquoi il s'arrête là

Le métier a tranché : le cycle de paiement est **hebdomadaire** (lecture B, M-04). La forme de la période a été arbitrée le 9 septembre 2026 : **un intervalle de dates**, jamais un numéro de semaine ISO. Voir `docs/decisions/2026-09-09-rythme-de-paiement-et-maille-de-la-periode.md`.

Le balayage du backend a compté **47 fichiers** portant `moisPaiement`, `anneePaiement` ou leurs équivalents SQL, répartis sur quatre services. C'est trois à quatre fois la taille d'un sous-sprint du projet. Le découpage retenu suit **ce qui dépend de la DFT et ce qui n'en dépend pas**, puis, à l'intérieur, ce qui débloque 6bis.2 :

| | Périmètre | Fichiers |
|---|---|---|
| **Maille 1** *(ce sprint)* | Workflow, Saisie, Reporting, garde-fou Transmission — **la représentation interne** | ~47 |
| Maille 2 | Charge comptable — `versionCharge`, `compteCharge`, période sur le fil — **le contrat externe** | 4 |

**Le Reporting ne pouvait pas être détaché**, et c'est une correction apportée à un premier découpage qui le prévoyait en sprint séparé. `WorkflowLectureHttpClient` envoie `mois` et `annee` en paramètres de requête à `GET /processus/recherche`, que le Workflow accepte en `@RequestParam(required = false)`. Si le Workflow cesse de les connaître, **Spring les ignore sans rien dire** : le Reporting recevrait des résultats non filtrés — toute la période au lieu du mois demandé — au lieu d'une erreur. Un résultat plausible et faux est exactement ce que ce projet refuse de produire. Les deux côtés changent donc ensemble.

**6bis.2 se débloque à la fin de ce sprint.** RG-15 a besoin de la période **interne** — celle recopiée sur `fiche_journaliere` et celle de `processus_mensuel` — jamais du format publié à la comptabilité.

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Ensemble du sous-sprint | Opus | Élevé |

Deux migrations sur des données existantes, un index d'unicité à traduire, et un contrat de fil inter-services dont la rupture est silencieuse. Aucune de ces trois choses ne pardonne l'approximation.

## 2. Outil de cartographie

```
py -3.14 -m graphify update .
```

## 3. Contexte

`processus_mensuel` porte aujourd'hui deux entiers, `mois_paiement` et `annee_paiement`, et rien d'autre ne dit à quelle période un état se rapporte. `fiche_journaliere` en porte une **recopie figée** (migration V3 du service Saisie), qui rend RG-15 calculable localement sans interroger le Workflow.

L'appartenance d'une journée à sa période est aujourd'hui un test d'**égalité mois + année** (`CompletudeService.dansLaPeriode`). Elle devient un test de bornes. C'est la seule règle de gestion du module qui change réellement de nature — le reste est de la transposition.

**Trois points de vigilance connus avant de commencer**, chacun développé au §10 :

- L'index `ux_processus_normal_par_periode` **ne se traduit pas directement**. Deux intervalles peuvent se chevaucher partiellement, ce que deux couples (mois, année) ne pouvaient pas faire.
- Les cinq premiers champs de `ProcessusResponse` sont un **contrat de fil** lu par le service Saisie à chaque écriture de ligne (action B-04, Sprint 4.1). Les changer d'un seul côté fait refuser toute saisie en `503`, sans aucune erreur de compilation.
- Le service Transmission lit `moisPaiement` et `anneePaiement` sur l'en-tête du processus. Il n'est pas dans le périmètre de ce sprint, mais il ne doit pas casser.

## 4. Objectifs

- Migrer `processus_mensuel` et `fiche_journaliere` vers `(date_debut, date_fin)`, données existantes converties sans perte
- Traduire la contrainte d'unicité de RG-15 en une contrainte qui interdit aussi le **chevauchement**
- Réécrire `LIGNE_HORS_PERIODE` en test de bornes
- Transposer le contrat de fil Workflow ↔ Saisie **des deux côtés dans le même commit**
- Poser un garde-fou côté Transmission pour qu'il refuse de publier plutôt que de mentir
- Corriger T-02 au passage : `@Pattern` sur le numéro de compte, 11 chiffres

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| RG-06 | Consolidation par unité — le libellé « mensuelle » devient faux, la règle ne change pas |
| RG-15 | Unicité inter-états « sur la période » — c'est elle que 6bis.2 écrira, derrière ce sprint |
| M-04 | Rythme de paiement — forme tranchée, contrat Kafka encore ouvert |
| T-02 | Numéro de compte courant sans contrôle de format |
| B-04 | Contrat de fil Workflow ↔ Saisie, cinq champs verrouillés par un test |

## 6. Étapes d'implémentation

### Étape 1. La contrainte d'unicité — ARBITRÉE LE 10/09/2026 : FORME 1

```
L'index actuel interdit deux etats NORMAL sur le meme couple unite / periode :

  CREATE UNIQUE INDEX ux_processus_normal_par_periode
      ON processus_mensuel (code_unite, mois_paiement, annee_paiement)
      WHERE type_processus = 'NORMAL';

Avec deux entiers, l'unicite suffisait : deux periodes sont egales ou
disjointes, jamais partiellement superposees. Avec un intervalle, ce
n'est plus vrai. Un index unique sur (code_unite, date_debut, date_fin)
laisserait passer :

  etat A : du 07/09 au 13/09
  etat B : du 10/09 au 16/09

Les deux sont NORMAL, sur la meme unite, et les journees du 10 au 13
appartiendraient a DEUX etats normaux. RG-15 deviendrait indecidable, et
une journee serait payable deux fois.

Deux formes possibles :

FORME 1 -- contrainte d'exclusion PostgreSQL (recommandee)

  CREATE EXTENSION IF NOT EXISTS btree_gist;

  ALTER TABLE processus_mensuel
    ADD CONSTRAINT ex_processus_normal_sans_chevauchement
    EXCLUDE USING gist (
      code_unite WITH =,
      daterange(date_debut, date_fin, '[]') WITH &&
    ) WHERE (type_processus = 'NORMAL');

  Strictement plus forte que l'index actuel : elle interdit l'egalite ET
  le chevauchement. Garantie par la base, donc increvable.
  COUT : elle exige l'extension btree_gist. En developpement, aucun
  probleme (on se connecte en superutilisateur). En production, c'est un
  geste de DBA a demander -- a inscrire au registre comme point DSI.

FORME 2 -- index unique simple sur (code_unite, date_debut)

  Aucune extension. Mais elle n'interdit que deux etats commencant le
  meme jour : le chevauchement partiel reste possible, et il faudrait le
  refuser dans le code, donc par discipline plutot que par construction.

Je recommande la FORME 1, et j'assume son cout : une contrainte que la
base fait respecter vaut mieux qu'un controle applicatif sur une regle
qui commande un double paiement. Mais l'extension est une dependance de
deploiement reelle, et ce n'est pas a moi de l'engager.

ARBITRAGE RENDU LE 10 SEPTEMBRE 2026 : la FORME 1 est retenue. La
contrainte d'exclusion est ecrite dans la migration V6, et le point D-12
est ouvert au registre pour l'extension btree_gist en production. Ne
repose pas la question, applique.
```

### Étape 2. Le nommage des pièces jointes — ARBITRÉ LE 10/09/2026

```
La convention actuelle (Sprint 4.2) :

  {annee}/{mois}/etat-rations-{codeUnite}-{annee}{mois}-p{id}.pdf
  ex. 2026/09/etat-rations-00002-202609-p109.pdf

Le AAAAMM avait ete choisi pour qu'un tri alphabetique soit un tri
chronologique. La propriete doit survivre.

Proposition :

  {annee}/{mois de date_debut}/etat-rations-{unite}-{AAAAMMJJ de date_debut}-p{id}.pdf
  ex. 2026/09/etat-rations-00002-20260907-p109.pdf

Les sous-dossiers restent {annee}/{mois} -- ils servent a eviter un
repertoire plat, pas a dire la periode. Le nom de fichier passe au jour
de debut, ce qui conserve le tri chronologique et distingue quatre
semaines d'un meme mois.

DOUZE PIECES JOINTES EXISTENT DEJA sous l'ancienne forme, dont sept
rattachees a des etats deja transmis. ELLES NE SONT PAS RENOMMEES : un
document signe ne se reecrit pas, et piece_jointe.chemin_fichier porte
le chemin reel. La convention ne vaut que pour les documents a venir.

ARBITRAGE RENDU LE 10 SEPTEMBRE 2026 : la proposition est retenue
telle quelle. Applique.
```

### Étape 3. Migration du service Workflow — V6

```
La prochaine migration du service Workflow est V6 : V1 a V5 sont prises
(creation, parametres, nombre_signatures, statut d'integration,
reservation de transmission). NE MODIFIE JAMAIS une migration deja
appliquee, meme pour un commentaire -- l'empreinte change et le service
ne demarre plus (regle apprise au Sprint 6.3 sur V1000).

Ecris V6__processus_periode_en_intervalle.sql :

1. Ajouter date_debut DATE et date_fin DATE, nullables dans un premier
   temps.
2. Remplir les 15 lignes existantes depuis le couple (mois, annee) :
   date_debut = premier jour du mois, date_fin = dernier jour du mois.
   La conversion est mecanique et sans perte -- un mois EST un
   intervalle.
3. Passer les deux colonnes NOT NULL.
4. Ajouter une contrainte CHECK (date_fin >= date_debut). Une periode a
   l'envers n'a aucun sens et doit etre impossible, pas seulement
   improbable.
5. Supprimer l'ancien index ux_processus_normal_par_periode, poser la
   contrainte arbitree a l'etape 1.
6. Supprimer mois_paiement et annee_paiement.
7. Commenter les deux nouvelles colonnes (COMMENT ON COLUMN), en disant
   que les bornes sont INCLUSIVES.

Sur le point 6 : supprimer plutot que garder les anciennes colonnes en
double. Les garder ferait cohabiter deux verites sur la meme ligne, et
la premiere requete ecrite par distraction lirait la mauvaise. Le
service Transmission est traite a l'etape 8, il ne les lit pas
directement en base.

Montre le SQL avant de l'appliquer.
```

### Étape 4. Migration du service Saisie — V5

```
La prochaine migration du service Saisie est V5 (V1 a V4 prises).

fiche_journaliere porte une RECOPIE FIGEE de la periode du processus
(migration V3), qui rend RG-15 calculable sans interroger le Workflow.
Cette recopie doit suivre la meme forme.

Ecris V5__fiche_journaliere_periode_en_intervalle.sql :

1. Ajouter date_debut DATE et date_fin DATE, nullables -- elles le
   restent, comme mois_paiement et annee_paiement l'etaient : ce sont
   des copies de confort, id_processus reste la reference (decision V3).
2. Remplir les 18 lignes existantes depuis (mois_paiement,
   annee_paiement), meme conversion qu'a l'etape 3.
3. Remplacer idx_fiche_journaliere_unite_periode par un index sur
   (code_unite, date_debut, date_fin).
4. Supprimer mois_paiement et annee_paiement.
5. Reprendre le COMMENT ON COLUMN de la V3 en l'adaptant.

ATTENTION a la justification ecrite dans la V3 : elle affirme que le
triplet est immuable parce que ux_processus_normal_par_periode impose un
seul processus NORMAL par (code_unite, mois, annee). Cet argument doit
etre reecrit en fonction de la contrainte arbitree a l'etape 1, sinon le
commentaire justifiera l'immuabilite par un index qui n'existe plus.

Montre le SQL avant de l'appliquer.
```

### Étape 5. Le domaine et les DTO du Workflow

```
Reprends, dans cet ordre :

1. ProcessusMensuel -- les deux champs, leurs getters, et les DEUX
   constructeurs. Celui de declenchement, et celui de l'etat
   complementaire, qui RECOPIE la periode depuis l'origine (Sprint
   6bis.1) : il continue de recopier, il copie simplement autre chose.

2. TransitionProcessus.declencher(dateDebut, dateFin, codeUnite) et
   ouvrirComplementaire -- la machine a etats ne juge toujours pas la
   periode, elle la transporte.

3. DeclenchementProcessusRequest -- @Min(1) @Max(12) sur le mois
   disparait. A la place : deux LocalDate obligatoires, et un controle
   que dateFin n'est pas anterieure a dateDebut. Le format ISO 8601 est
   deja la convention du projet (section 11).

   NE POSE AUCUNE BORNE DE DUREE MAXIMALE sans me demander. Une semaine
   fait sept jours, mais rien dans les decisions ne dit qu'une periode
   ne peut pas en durer quatorze -- et le point M-04 a justement retenu
   l'intervalle pour ne pas figer une cadence.

4. ProcessusResponse et les autres reponses portant la periode
   (EtatProcessusResponse, EnTeteProcessusResponse,
   HistoriqueProcessusResponse, SoumissionResponse, EtatConsolide).

5. ProcessusMensuelRepository -- la recherche par periode ;
   ProcessusSpecifications -- les filtres ; RechercheProcessusService --
   l'ordre de tri, qui trie aujourd'hui sur annee puis mois.

Montre chaque fichier apres modification.
```

### Étape 6. La règle de période et les libellés

```
1. CompletudeService.dansLaPeriode devient un test de bornes :
   la date de la journee est-elle comprise entre date_debut et
   date_fin, bornes incluses ? C'est plus simple ET plus robuste que
   l'egalite mois + annee -- c'est l'un des quatre motifs qui ont fait
   retenir l'intervalle.

   Le message de LIGNE_HORS_PERIODE doit nommer les deux bornes. Un
   agent qui lit "hors de la periode 09/2026" pouvait deviner ; devant
   quatre etats dans le mois, il ne devinera plus.

2. Les QUATRE methodes libellePeriode DU PERIMETRE, qui formatent
   aujourd'hui "%02d/%d" et produisent "L'etat 09/2026 de l'unite
   00002". En hebdomadaire, quatre etats porteraient le meme intitule.
   Forme attendue : "du 07/09/2026 au 13/09/2026".

   Elles sont dans SoumissionService, ValidationService,
   OuvertureComplementaireService et RetourService (service Workflow).

   Deux AUTRES portent le meme nom dans ConstructionChargeService
   (service Transmission) : NE PAS Y TOUCHER, c'est l'etape 2.

   CompletudeService a une methode equivalente nommee periode(), pas
   libellePeriode : une recherche sur le seul nom la manquerait.

3. DocumentService -- le titre "ETAT MENSUEL DE PAIEMENT", la ligne
   "TOTAL DU MOIS", et periodeEnToutesLettres avec son tableau MOIS[].
   Ces trois libelles sont IMPRIMES DANS LE PDF, donc lus par un
   valideur. Propose les nouveaux libelles avant de les ecrire.

4. NommageDocument.cheminRelatif -- selon l'arbitrage de l'etape 2.
```

### Étape 7. Le contrat de fil avec la Saisie — LES DEUX CÔTÉS ENSEMBLE

```
C'est le point le plus dangereux du sprint, et il ne produit aucune
erreur de compilation.

Les cinq premiers champs de ProcessusResponse (idProcessus, statut,
codeUnite, moisPaiement, anneePaiement) sont lus par ProcessusReponse
dans service-saisie, a CHAQUE ecriture de ligne de prestation. C'est
l'action B-04 du Sprint 4.1, et un test les verrouille. Si tu changes
un cote sans l'autre, VerificationProcessusHttpClient refuse toute
saisie en 503 avec le message "reponse 200 sans unite ni periode
exploitables" -- une panne complete de la saisie, sans qu'aucun
compilateur ne dise rien.

Les deux cotes changent DANS LE MEME COMMIT :

  service-workflow  : ProcessusResponse
  service-saisie    : ProcessusReponse, ResultatVerificationProcessus,
                      VerificationProcessusHttpClient

Puis, cote Saisie uniquement :

  FicheJournaliere            -- les deux champs recopies
  FicheJournaliereService     -- la recopie a l'ouverture de fiche
  RechercheLignesRepository   -- le JPQL construit dynamiquement
  RechercheLignesController   -- les @RequestParam mois et annee
  ConsolidationService        -- la periode de l'etat, lue sur la
                                premiere fiche
  EtatModifiableService       -- le message "%02d/%d"
  EtatConsolideResponse, FicheResponse, EtatConsolide

Le test qui verrouille les cinq champs doit etre MIS A JOUR, pas
supprime : il protege toujours quelque chose, il protege simplement
autre chose.
```

### Étape 8. Le garde-fou du service Transmission

```
Le service Transmission n'est PAS dans le perimetre de ce sprint -- la
charge comptable est l'etape 2, et elle attend la DFT. Mais il lit
enTete.moisPaiement() et enTete.anneePaiement() dans
ConstructionChargeService, et il publie "periode": { mois, annee } sur
rations.etat.valide.

Il ne doit ni casser, ni mentir.

Solution : EnTeteProcessus continue de porter moisPaiement et
anneePaiement, DERIVES de date_debut cote Workflow. Un etat dont la
periode tient dans un seul mois se derive sans perte, et la charge v1
part comme avant.

MAIS un intervalle a cheval sur deux mois n'a AUCUN mois vrai. Dans ce
cas, la derivation NE DEVINE PAS : elle refuse. L'etat n'est pas publie,
et le refus remonte en 500 CHARGE_INCOMPLETE -- code existant, dont le
motif d'origine s'applique tel quel : l'etat est cloture donc fige,
l'appelant n'a rien a corriger.

C'est volontairement visible plutot que silencieux. Le jour ou une
semaine chevauchera deux mois, la transmission s'arretera et dira
pourquoi, au lieu d'imputer un paiement sur un mois invente. C'est aussi
ce qui rend la dependance a la DFT CONCRETE : tant que l'etape 2 n'est
pas faite, ces etats-la ne partent pas.

Journalise le refus au prefixe repérable PERIODE A CHEVAL, et inscris la
limite dans le point M-04.
```

### Étape 9. Le service Reporting

```
Le Reporting ne stocke rien : il interroge le Workflow et la Saisie, et
agrege en memoire. Il change donc pour deux raisons distinctes -- ce
qu'il ENVOIE, et ce qu'il RECOIT.

CE QU'IL ENVOIE -- le point dangereux

  WorkflowLectureHttpClient et SaisieLectureHttpClient posent
  queryParam("mois", ...) et queryParam("annee", ...). Les deux
  endpoints appeles les declarent en @RequestParam(required = false).

  Si tu changes le cote serveur sans le cote client, SPRING IGNORE
  SILENCIEUSEMENT les parametres inconnus : la recherche renvoie TOUT
  au lieu du filtre demande. Pas d'exception, pas de 400, juste un
  resultat trop large qu'un lecteur prendra pour la verite.

  Change les deux cotes dans le meme commit, et ecris un test qui
  verifie qu'un filtre de periode REDUIT le nombre de resultats. Un
  test qui verifie seulement que l'appel aboutit ne verrait rien.

CE QU'IL RECOIT

  ReponseWorkflow mappe moisPaiement et anneePaiement.

L'API PUBLIQUE DU REPORTING

  ReportingController accepte periode=AAAA-MM et le convertit en
  YearMonth. Ce format ne peut plus exprimer une semaine.
  Propose sa forme de remplacement AVANT de coder -- c'est un
  parametre du contrat d'API, pas un detail interne. Piste : deux
  parametres dateDebut et dateFin en ISO 8601, qui ont l'avantage de
  permettre un rapport sur n'importe quelle plage, y compris un mois
  entier ou un trimestre.

LES EXPORTS

  ExportPdfService et ExportExcelService portent chacun un tableau
  MOIS[] et une methode periodeEnToutesLettres. Le nommage des
  fichiers exportes suit rapport-rations-<agence>-<AAAAMM>, dont la
  propriete voulue etait qu'un tri alphabetique soit un tri
  chronologique -- elle doit survivre.

LA BORNE DE VOLUME

  app.reporting.limite-resultats vaut 5000, calibre sur ~600 etats par
  an, soit ~8 ans de marge. A ~2 600 etats par an, la marge tombe sous
  deux ans. Ne change pas la valeur toi-meme : signale-la, et propose
  un chiffre avec son calcul.
```

### Étape 10. T-02 — le format du numéro de compte

```
Dans IdentiteBeneficiaireRequest (service Saisie), le champ
numCompteCourant porte aujourd'hui :

  @NotBlank(message = "le numero de compte courant est obligatoire")
  @Size(max = 20, message = "... ne peut pas depasser 20 caracteres")

Le @Size devient inutile une fois la longueur exacte imposee. Remplace-le
par un @Pattern(regexp = "^[0-9]{11}$") dont le message nomme la regle :
onze chiffres, sans espace ni separateur. Garde le @NotBlank -- il
produit un message different et plus clair sur un champ absent.

Aucune migration : la colonne reste VARCHAR(20).

NE CORRIGE PAS les 44 beneficiaires existants -- arbitrage du
9 septembre : ce sont des donnees de developpement, la base de
production demarrera vide, et corriger a l'aveugle des numeros dont on
ignore la valeur vraie fabriquerait des beneficiaires plausibles mais
faux. Voir docs/points-en-attente.md, point T-02.

Signale en revanche que le dictionnaire de donnees declare VARCHAR(30)
la ou la base et le code portent 20, et que l'exemple du contrat d'API
section 7.1 porte un numero a 14 chiffres. Ces deux corrections
documentaires sont a faire, mais PAS ici : elles partent avec la passe
documentaire de M-04.
```

### Étape 11. Tests

```
Reprends les tests existants, ne les reecris pas depuis zero -- ils
portent des intentions qu'il ne faut pas perdre.

Les jeux d'essai construisent aujourd'hui une periode par mois, avec un
compteur prochainMois() qui contourne l'index d'unicite. CINQ fichiers
le portent, comptes et non estimes : CircuitCompletIT,
OuvertureComplementaireServiceTest, RetourServiceTest,
ValidationServiceTest, VerrouTransmissionServiceIT. SoumissionServiceTest
utilise une variante ecrite a la main, ((rang - 1) % 12) + 1 : elle ne
sort pas d'une recherche sur "prochainMois". Ils doivent tous produire
des INTERVALLES DISJOINTS. Attention : avec la contrainte
d'exclusion, deux intervalles qui se chevauchent seront refuses PAR LA
BASE -- un jeu d'essai negligent fera echouer des tests sans rapport.

Tests a AJOUTER :

 1. Deux etats NORMAL sur des intervalles disjoints : acceptes.
 2. Deux etats NORMAL sur le MEME intervalle : refuse.
 3. Deux etats NORMAL sur des intervalles qui SE CHEVAUCHENT
    PARTIELLEMENT : refuse. C'est le test qui justifie tout l'etape 1 ;
    il echouerait avec un simple index unique.
 4. Deux etats COMPLEMENTAIRE sur le meme intervalle : acceptes -- la
    contrainte ne porte que sur NORMAL.
 5. dateFin anterieure a dateDebut : refuse.
 6. Une ligne datee la veille de date_debut : LIGNE_HORS_PERIODE.
 7. Une ligne datee le jour de date_debut : acceptee (borne incluse).
 8. Une ligne datee le jour de date_fin : acceptee (borne incluse).
 9. Une ligne datee le lendemain de date_fin : LIGNE_HORS_PERIODE.
10. Un complementaire recopie l'intervalle de son origine, a
    l'identique.
11. La derivation de Transmission sur un intervalle tenant dans un
    mois : reussit.
12. La derivation sur un intervalle a cheval sur deux mois : refuse,
    et rien n'est publie.
13. Numero de compte a 10, 11 et 12 chiffres : seul 11 est accepte.
14. Le test de verrouillage des cinq champs de ProcessusResponse, mis
    a jour.

Les tests 7 et 8 comptent autant que le 6 et le 9 : une borne mal
posee ne se voit que sur la borne elle-meme.
```

## 7. Fichiers à créer ou modifier

| Chemin | Nature |
|---|---|
| `service-workflow/.../db/migration/V6__processus_periode_en_intervalle.sql` | Création |
| `service-saisie/.../db/migration/V5__fiche_journaliere_periode_en_intervalle.sql` | Création |
| `service-workflow/.../domaine/ProcessusMensuel.java` | Modification |
| `service-workflow/.../domaine/TransitionProcessus.java` | Modification |
| `service-workflow/.../application/CompletudeService.java` | Modification — la règle |
| `service-workflow/.../application/NommageDocument.java` | Modification |
| `service-workflow/.../application/DocumentService.java` | Modification — libellés imprimés |
| `service-workflow/.../api/dto/ProcessusResponse.java` | Modification — **contrat de fil** |
| `service-saisie/.../infrastructure/workflow/ProcessusReponse.java` | Modification — **contrat de fil** |
| `service-saisie/.../domaine/FicheJournaliere.java` | Modification |
| `service-transmission/.../application/EnTeteProcessus.java` | Modification — dérivation |
| `service-reporting/.../infrastructure/workflow/WorkflowLectureHttpClient.java` | Modification — **paramètres ignorés en silence** |
| `service-reporting/.../api/ReportingController.java` | Modification — le format `AAAA-MM` disparaît |
| *(~47 fichiers au total, cf. §6)* | |

**Aucun fichier de `ConstructionChargeService`** au-delà du garde-fou de dérivation : la charge comptable est le sprint Maille 2.

## 8. Commandes terminal

```bash
cd afb-rations/backend
mvn -pl service-workflow clean test
mvn -pl service-saisie clean test
mvn -pl service-transmission clean test
```

État des données avant migration, pour vérifier la conversion après :

```sql
\c rations_workflow
SELECT id, code_unite, mois_paiement, annee_paiement, statut FROM processus_mensuel ORDER BY id;
```

Attendu après migration : autant de lignes, `date_debut` au premier jour du mois d'origine et `date_fin` au dernier, aucune ligne perdue.

Vérification de la contrainte d'exclusion, si la forme 1 est retenue :

```sql
\c rations_workflow
INSERT INTO processus_mensuel (date_debut, date_fin, code_unite, type_processus, statut, montant_total, transmis_comptabilite)
VALUES ('2099-01-05', '2099-01-11', '00002', 'NORMAL', 'EN_COURS_SAISIE', 0, false);
-- doit reussir

INSERT INTO processus_mensuel (date_debut, date_fin, code_unite, type_processus, statut, montant_total, transmis_comptabilite)
VALUES ('2099-01-08', '2099-01-14', '00002', 'NORMAL', 'EN_COURS_SAISIE', 0, false);
-- doit ECHOUER : chevauchement des 8, 9, 10 et 11 janvier
```

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| Les 15 états convertis sans perte | 15 lignes, bornes cohérentes avec le mois d'origine |
| Les 18 fiches converties sans perte | 18 lignes |
| Chevauchement partiel de deux NORMAL | Refusé **par la base** |
| Deux COMPLEMENTAIRE sur le même intervalle | Acceptés |
| Bornes de `LIGNE_HORS_PERIODE` | Les deux jours limites acceptés, les deux voisins refusés |
| Saisie d'une ligne après migration | Fonctionne — le contrat de fil tient |
| Transmission d'un état tenant dans un mois | Publie comme avant |
| Transmission d'un état à cheval | Refuse, `CHARGE_INCOMPLETE`, rien n'est publié |
| Suites des trois services | BUILD SUCCESS |

## 10. Points de vigilance

- **L'unicité ne se traduit pas, elle se renforce.** Deux couples (mois, année) sont égaux ou disjoints ; deux intervalles peuvent se chevaucher à moitié. Un index unique posé par symétrie laisserait une journée appartenir à deux états NORMAL, et RG-15 — qui interroge « les autres états de la même période » — n'aurait plus de réponse définie.
- **Le contrat de fil ne casse pas à la compilation.** Les cinq premiers champs de `ProcessusResponse` sont lus par nom, en JSON, par le service Saisie. Les changer d'un seul côté produit une panne totale de la saisie sous forme de `503`, et rien avant l'exécution ne l'annonce. Les deux côtés dans le même commit, sans exception.
- **Ne pas garder les anciennes colonnes « au cas où ».** Deux représentations de la même période sur la même ligne, c'est deux vérités dont une sera lue par erreur. La conversion est mécanique et sans perte : il n'y a rien à conserver.
- **La dérivation côté Transmission refuse, elle ne devine pas.** Un intervalle à cheval sur deux mois n'a pas de mois. Publier le mois de la date de début serait plausible, faux, et invisible — la pire des trois propriétés.
- **Les douze pièces jointes existantes ne sont pas renommées.** Un document signé ne se réécrit pas, et `piece_jointe.chemin_fichier` porte le chemin réel. La nouvelle convention ne vaut que pour les documents à venir.
- **Ne jamais modifier une migration déjà appliquée**, même pour y corriger un commentaire : l'empreinte Flyway change et le service refuse de démarrer sur tout environnement où elle a tourné. Leçon du Sprint 6.3 sur `V1000`.
- **Aucune borne de durée maximale sans arbitrage.** L'intervalle a précisément été retenu pour ne pas figer une cadence ; y écrire « sept jours au plus » réintroduirait le postulat que M-04 vient de retirer.
- **`processus_mensuel` garde son nom.** Décision du 9 septembre : la chaîne `"processus_mensuel"` est une **valeur** dans 14 fichiers et dans 193 événements d'audit déjà persistés. Renommer la table couperait l'historique du journal, et le réécrire est ce que son immuabilité interdit.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Contrainte d'unicité arbitrée avec l'utilisateur | Fait |
| Convention de nommage arbitrée avec l'utilisateur | Fait |
| Deux migrations écrites, appliquées, données converties sans perte | Vérifié |
| Chevauchement partiel refusé, prouvé par un test | Vérifié |
| `LIGNE_HORS_PERIODE` réécrit, quatre tests de bornes | Vérifié |
| Contrat de fil transposé des deux côtés, test à jour | Vérifié |
| Saisie d'une ligne fonctionnelle après migration | Vérifié en réel |
| Garde-fou Transmission : refuse sur un intervalle à cheval | Vérifié |
| T-02 corrigé, 44 lignes intactes | Vérifié |
| Filtre de période du Reporting : un test prouve qu'il **réduit** le résultat | Vérifié |
| Suites des trois services au vert | BUILD SUCCESS |
| Résumé de sprint et décision consignés | Fait |

## 12. Commit

```bash
git add .
git commit -m "sprint-maille-1a: la periode devient un intervalle de dates

- Migrations V6 (workflow) et V5 (saisie), 15 etats et 18 fiches convertis
- Contrainte d'exclusion : deux etats NORMAL ne peuvent plus se chevaucher
- LIGNE_HORS_PERIODE reecrit en test de bornes, bornes incluses
- Contrat de fil Workflow/Saisie transpose des deux cotes
- Transmission : derivation qui refuse plutot que d'inventer un mois
- T-02 : @Pattern 11 chiffres, les 44 lignes de dev restent intactes

Refs: M-04, RG-06, RG-15, T-02, B-04, docs/decisions/2026-09-09-rythme-de-paiement-et-maille-de-la-periode.md"
```

---

**Fin du Sprint Maille 1a** — le sous-sprint **6bis.2 se débloque ici**. Le Reporting suit en 1b, la charge comptable en étape 2, derrière la réponse de la DFT.
