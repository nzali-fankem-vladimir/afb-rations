# Contrôles de complétude avant soumission

**Service :** Workflow (port 8084)
**Sprint :** 4.2 — US-07, CT-13, RG-07
**Statut :** arbitré avec l'utilisateur le 1er septembre 2026, avant tout codage

> **À reprendre dans CLAUDE.md à la clôture du Sprint 4.** Cette note fixe ce
> qu'est un « état complet » dans ce module. Sans elle, un sous-sprint ultérieur
> réinterpréterait la complétude autrement, et deux définitions coexisteraient
> sans que rien ne le signale.

---

## 1. Pourquoi cette note existe

Le cahier des charges et US-07 disent que « le système vérifie la complétude des
informations et bloque une soumission incomplète ». **Aucun document du projet ne
dit lesquelles.** CT-13 exige en outre que le refus **liste les manques**, pas
qu'il échoue.

La liste ci-dessous est donc un arbitrage, pas une lecture. Elle a été prise avec
l'utilisateur au début du Sprint 4.2, sur présentation des candidats et de leur
justification métier.

---

## 2. Ce que la soumission engage

C'est ce qui donne sa mesure au contrôle. Passé la soumission :

| | Effet |
|---|---|
| Service Saisie | refuse **toute écriture** sur l'état (`EtatModifiableService`) |
| `processus_mensuel` | `montant_total` est gravé, statut `EN_ATTENTE_DA` |
| Sprint 4.3 | ce montant **commande l'aiguillage RG-08** (seuil 100 000 XAF) |
| Sprint 5, à la clôture | les lignes deviennent la charge de `rations.etat.valide`, **c'est-à-dire le paiement** |

Le contrôle de complétude est **le dernier point où un état peut encore être
corrigé**.

Il ne peut cependant pas tout voir. Deux familles de défaut, très inégales :

| | Nature | Détectable ? |
|---|---|---|
| **Un manque** | l'agent a oublié une journée, un bénéficiaire | **Non.** Seule l'absence *structurelle* l'est (aucune ligne du tout). |
| **Un intrus** | quelque chose est là qui ne devrait pas y être | **Oui**, et c'est là que le contrôle a une vraie prise. |

---

## 3. Les quatre contrôles retenus

Vocabulaire fermé par `CodeManqueEnum` (couche `domaine`), une valeur par
contrôle. Le test 21 de `CompletudeServiceTest` confronte l'énumération à cette
liste : **ajouter un cinquième contrôle fait échouer le build** tant que cette
note n'est pas mise à jour.

### 3.1 `ETAT_VIDE` — au moins une ligne de prestation

**Refus si** `nombreLignes` vaut zéro ou est absent de la réponse, **ou** si le
détail ne porte réellement aucune ligne.

**Motif.** Un état à 0 FCFA traverserait sinon tout le circuit de validation
jusqu'à la comptabilité. Ce contrôle n'était pas un arbitrage : il était **déjà
dû**, inscrit à trois endroits — `docs/appel-consolidation.md` §4 (« c'est à
Workflow de refuser la soumission d'un état vide, à partir de
`nombreLignes == 0`. C'est sa règle, pas celle de Saisie »),
`docs/decisions/2026-08-31-domaine-du-workflow-et-machine-a-etats.md` §10, et le
point **C-03** du résumé du Sprint 3.4.

**Le second cas mérite d'être explicité.** Le compteur annoncé par Saisie peut
être non nul alors que le détail est vide. Ce désaccord ne devrait jamais
arriver ; s'il arrive, on refuse plutôt que de soumettre un état dont on ne voit
pas le contenu, et **le message dit franchement l'incohérence** au lieu de faire
croire à un oubli de l'agent.

**Ce contrôle court-circuite les trois autres :** ils n'auraient aucune ligne à
examiner, et « aucune ligne » se suffit à soi-même. Le refus porte alors un manque
et un seul.

### 3.2 `LIGNE_HORS_PERIODE` — aucune ligne étrangère au mois du processus

**Refus si** une ligne est rattachée à une journée dont le mois ou l'année ne
sont pas ceux du processus.

**Motif — c'est le seul trou réel que ce sprint referme.** Rien ne vérifiait
qu'une journée saisie tombe dans le mois du processus.
`POST /saisie/fiches {idProcessus: <processus de 09/2026>, dateJour: "2026-03-05"}`
passait. Ce n'est pas un oubli : le DTO `OuvertureFicheRequest` (Sprint 3.3) le
dit explicitement —

> « Quant à savoir si la journée tombe dans le mois du processus, c'est une
> question qui appartient au processus, donc au service Workflow : une annotation
> de validation ici en ferait une règle du service Saisie, dupliquée et
> divergente le jour où elle changerait. »

**La Saisie s'est délibérément dessaisie de ce contrôle au profit de Workflow. Si
Workflow ne le prend pas, il n'est implémenté nulle part.**

**L'enjeu dépasse l'erreur de période.** RG-15 (Sprint 6bis) vérifie l'unicité
inter-états **à unité et période égales**. Une ligne de mars glissée dans l'état
de septembre **échappe au contrôle d'unicité de mars** : la même journée devient
payable deux fois, une fois dans l'état de mars, une fois dans celui de
septembre. C'est un vecteur de double paiement, et il n'est refermé nulle part
ailleurs.

**⚠️ Le contrôle porte sur les LIGNES, jamais sur les JOURNÉES.** C'est la
correction la plus importante apportée à la formulation initiale, et elle doit
survivre aux sprints suivants.

Il n'existe **aucun `DELETE /saisie/fiches/{id}`** au contrat d'API : les cinq
endpoints de Saisie sont `POST /fiches`, `GET /fiches/{id}/lignes`, et
`POST` / `PUT` / `DELETE` sur `/lignes`. **On supprime une ligne, jamais une
fiche.** Bloquer sur l'existence d'une journée hors période enfermerait donc
définitivement l'agent : une fiche vide égarée de mars l'empêcherait de soumettre
son mois, à jamais, sans aucun recours dans le module.

En portant le contrôle sur les lignes, une journée hors période mais **vide** ne
bloque pas — elle ne porte aucun montant — et l'agent conserve
`DELETE /saisie/lignes/{id}` pour se corriger. Le message le lui dit.

**Une journée sans date est traitée comme hors période**, avec un message
distinct. Elle ne peut pas être *prouvée* dans le mois, et le refus par défaut
vaut aussi pour les valeurs absentes (même raisonnement que `estAutorisee` sur un
statut nul, Sprint 4.1). Le message renvoie vers l'administrateur, pas vers
l'agent : ce n'est pas son erreur.

### 3.3 `LIGNE_SANS_MONTANT` — aucune ligne sans montant applicable

**Refus si** `montantApplique` est nul, absent, ou inférieur ou égal à zéro.

**Motif.** RG-03 : le montant est repris de la grille active, jamais saisi. Une
ligne sans montant ne peut pas être payée. Zéro est traité comme absent : c'est
presque toujours le signe d'une résolution qui n'a pas abouti plutôt qu'un tarif
réel, et le contrat de création d'une grille exige d'ailleurs un montant
strictement positif (Sprint 2.2).

### 3.4 `BENEFICIAIRE_SANS_COMPTE` — aucun bénéficiaire sans compte courant

**Refus si** le bénéficiaire est absent de la ligne, ou si son
`numCompteCourant` est nul ou fait d'espaces.

**Motif.** C'est la donnée qui commande **qui est payé** : elle porte la ligne de
crédit dans la charge du topic `rations.etat.valide` (contrat §7.1). Sans elle,
la ligne est impayable.

### 3.5 Pourquoi 3.3 et 3.4 ne sont pas du code mort

Ces deux contrôles **ne peuvent pas échouer aujourd'hui** par la voie
applicative :

| Garantie | Où elle vit |
|---|---|
| `ligne_prestation.montant_applique INTEGER NOT NULL` | migration V1 de **rations_saisie** |
| refus conservateur si Grilles est muet | `service-saisie`, Sprint 2.4 |
| `beneficiaires.num_compte_courant VARCHAR(20) NOT NULL` | migration V1 de **rations_saisie** |
| `@NotBlank` sur `numCompteCourant` | `IdentiteBeneficiaireRequest`, Sprint 3.1 |

**Toutes vivent dans le schéma d'un autre service, avec lequel Workflow n'a aucun
lien de compilation.** C'est exactement le motif qui les justifie : si la Saisie
relâchait l'une de ces contraintes, ou si une reprise de données par script SQL
les contournait, Workflow se mettrait **silencieusement** à soumettre des états
contenant des bénéficiaires impayables, et ces lignes partiraient telles quelles
vers la comptabilité.

`EtatConsolide` est un *tolerant reader* aux champs boîtés ; la décision du
Sprint 4.1 §8 dit qu'« un champ manquant se lit `null` et fait refuser ». Ces deux
contrôles ne sont pas des filets décoratifs : ils sont **cette discipline appliquée
jusqu'au bout**.

---

## 4. Les contrôles écartés, et pourquoi

### 4.1 « Aucune journée ouverte sans ligne » — écarté

**Ce serait un piège.** Faute de `DELETE /saisie/fiches/{id}` (§3.2), un agent qui
ouvre un jour par erreur, ou qui ouvre le jour d'une garde finalement annulée, ne
pourrait **plus jamais** refermer cette journée, donc plus jamais soumettre son
mois.

Le besoin métier est réel — un jour ouvert et resté vide *peut* être un oubli — mais
le système ne sait pas distinguer l'oubli de l'absence légitime de garde, et le
coût d'une erreur est ici un blocage définitif.

**À rouvrir uniquement si** le service Saisie gagne un endpoint de suppression de
fiche. Ce serait alors un ajout au contrat d'API, hors du périmètre du Sprint 4.

### 4.2 « Toutes les fiches au statut `ENREGISTREE` » — écarté

**Ce contrôle bloquerait 100 % des soumissions.** Vérifié dans le code :
`FicheJournaliere` naît `EN_SAISIE` (constructeur, ligne 122) et **aucun endpoint
du Sprint 3 ne la fait jamais passer à `ENREGISTREE`** — la recherche de tout
mutateur de statut sur cette entité ne rend rien.

`StatutFicheEnum` déclare pourtant les deux valeurs, et son propre javadoc
reconnaît que « ce cycle n'est pas encore piloté par une machine à états ici ».

**Dette identifiée, à trancher au Sprint 4 ou 6 :** soit le service Saisie gagne
la transition `EN_SAISIE → ENREGISTREE` et ce contrôle devient possible, soit le
statut de fiche est un vestige à retirer du dictionnaire. **Laisser les deux
valeurs sans transition est le pire des trois états**, parce que le prochain
lecteur croira que le statut veut dire quelque chose.

### 4.3 « Le total égale la somme des sous-totaux » — écarté

La décision du Sprint 3.4 garantit déjà cette égalité **par construction** : le
total *est* la somme des sous-totaux, eux-mêmes sommes des lignes rendues, et il
n'existe aucun `SUM` SQL parallèle. Le revérifier ici créerait précisément le
**second chemin de calcul** que cette décision interdit — et deux chemins finissent
par diverger.

`CompletudeService` lit `montantApplique` ligne par ligne pour vérifier sa
*présence*, **jamais pour en faire une somme**.

### 4.4 « Aucune journée dans le futur » — écarté

Le contrôle de période (§3.2) borne déjà les journées au mois du processus. Par
ailleurs, la saisie rétroactive est explicitement supportée (Sprint 2.4 : le
montant est résolu à la date de la *prestation*). Une règle sur la date du jour
appartiendrait à l'ouverture de la fiche, côté Saisie, pas à la soumission.

### 4.5 « Cohérence de l'unité » et « cohérence de la période déclarée » — écartés

`EtatConsolide.codeUnite` est l'**écho** du paramètre que Workflow vient d'envoyer
(`docs/appel-consolidation.md` §2.2) : le vérifier reviendrait à vérifier sa propre
question. Le désaccord réel — unité déclarée ≠ unité figée sur les fiches — est déjà
refusé par Saisie en `403 UNITE_NON_CONCORDANTE`.

`moisPaiement` / `anneePaiement` au niveau de l'état sont lus sur les fiches et
peuvent être nuls (§4 de la même note, et point ouvert **C-04** : les fiches
antérieures à la migration V3 en sont dépourvues). Le contrôle de période de §3.2
n'en dépend pas : il lit `dateJour`, qui a toujours été `DATE NOT NULL`.

### 4.6 « Service Saisie joignable » — hors du contrôle de complétude

Ce n'est pas un manque de l'agent, c'est une panne. Traité en amont par le refus
conservateur `503 SERVICE_SAISIE_INDISPONIBLE` (doctrine Sprint 1.3, réaffirmée
par `docs/appel-consolidation.md` §6). Le mêler à la liste des manques ferait
croire à l'agent qu'il a quelque chose à corriger.

---

## 5. Forme du refus

`422 ETAT_INCOMPLET`, format d'erreur uniforme **augmenté d'un champ `manques`**.

```json
{
  "timestamp": "2026-09-01T10:24:00",
  "status": 422,
  "code": "ETAT_INCOMPLET",
  "message": "L'etat de 09/2026 pour l'unite 00002 presente 2 manques.",
  "path": "/processus/109/soumission",
  "manques": [
    { "code": "LIGNE_HORS_PERIODE",
      "message": "1 ligne(s) sont rattachees a des journees hors de la periode 09/2026 : 05/03/2026. Supprimez ces lignes ..." },
    { "code": "BENEFICIAIRE_SANS_COMPTE",
      "message": "1 ligne(s) concernent un beneficiaire sans numero de compte courant : EYENGA Louise (ligne n° 412 du 03/09/2026) ..." }
  ]
}
```

**Décision, arbitrée avec l'utilisateur.** Les cinq champs du contrat §1.4
(`timestamp`, `status`, `code`, `message`, `path`) restent présents, au même nom et
au même type ; `manques` est **omis du JSON** partout ailleurs
(`@JsonInclude(NON_NULL)`). L'ajout est purement additif : aucun consommateur
existant ne casse.

**Pourquoi pas une concaténation dans `message`.** L'étape 2 du guide et CT-13
exigent des manques *listés*, « pour que l'interface les affiche à l'agent ». Une
prise en prose se lit à l'œil mais ne se rend pas en liste : le frontend
(Sprint 7F) ne pourrait ni distinguer les manques, ni renvoyer l'agent vers la
journée fautive, sans découper du texte à la main.

**Ajouté au seul service Workflow.** Les trois autres services n'ont rien à y
mettre ; le champ y serait toujours absent du JSON. Périmètre du sous-sprint
respecté.

### 5.1 Un manque par contrôle, jamais un par ligne fautive

Chaque contrôle produit **au plus un élément**, qui agrège ce qu'il a trouvé et en
nomme au plus cinq exemples, le reste étant annoncé en nombre
(`... et 195 autre(s)`). Un état de deux cents lignes toutes défectueuses
produirait sinon deux cents entrées illisibles. **La liste rendue en compte au plus
quatre**, et le nombre total reste annoncé en tête de chaque message : la
troncature ne cache jamais l'ampleur du problème (test 19).

### 5.2 Chaque message nomme le geste attendu

Un manque qui ne dit pas quoi faire oblige l'agent à chercher, ce que l'étape 2 du
guide et CT-13 refusent explicitement. C'est la doctrine déjà posée au Sprint 3.3
pour `ETAT_NON_MODIFIABLE` : le message nomme la période, l'unité, **et l'action
attendue**.

---

## 6. Où le contrôle s'exécute

`CompletudeService` (couche `application`) ne consulte **ni la base, ni le service
Saisie, ni le service Identité**. Toutes ses données sont dans ses deux
paramètres : le processus tel que Workflow le connaît, et l'état consolidé déjà
obtenu par l'appelant.

Deux conséquences :

- il est testable sans aucune infrastructure — d'où les 21 tests de
  `CompletudeServiceTest`, qui éprouvent des cas (réponse tronquée, journée sans
  date, deux cents lignes fautives) pénibles à fabriquer contre un service réel ;
- **il constate, il ne refuse pas.** Il ne lève jamais d'exception et ne rend
  jamais `null`. Le refus appartient au service de soumission, seul à savoir ce
  qu'il faut faire d'un manque.

**Position dans l'ordre des opérations** (document maître §7.3) : après
l'habilitation et le chargement, **avant toute écriture** — et notamment avant la
génération du document PDF. On n'écrit pas un fichier pour un état qu'on va
refuser.

---

## 7. Points ouverts issus de cette note

| Réf | Point | Pour qui |
|---|---|---|
| **K-01** | `StatutFicheEnum.ENREGISTREE` n'est jamais atteint : donner la transition au service Saisie, ou retirer le statut du dictionnaire. Ne pas laisser les deux valeurs sans transition. | Sprint 4 ou 6 |
| **K-02** | Absence de `DELETE /saisie/fiches/{id}` : tant qu'elle dure, le contrôle de journée vide reste inapplicable (§4.1). Ajout au contrat d'API à arbitrer. | Métier / Sprint 7F |
| ~~**K-03**~~ | ~~Le champ `manques` doit être porté au contrat d'API §1.4 et à CLAUDE.md §11 à la clôture du Sprint 4.~~ **SOLDÉ au Sprint 4.4** : porté au contrat d'API §1.4, avec un exemple de charge et le tableau des trois codes de refus en 403 ; déjà présent en CLAUDE.md §11. | — |
| ~~**K-04**~~ | ~~Cette note doit rejoindre CLAUDE.md à la clôture du Sprint 4.~~ **SOLDÉ au Sprint 4.4** : les quatre contrôles sont portés dans CLAUDE.md §6, sous forme de tableau, après RG-15. Cette note reste la référence pour les contrôles **écartés** et leurs motifs. | — |
