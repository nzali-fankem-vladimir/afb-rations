# Agrégation multi-services du Reporting : croiser deux bases sans jointure

> ⚠️ **Lire à la lumière du sprint Maille 1 (10 septembre 2026).** Ce document décrit
> l'état du module **à sa date**, quand la période de paiement était un mois porté par
> le couple `(mois_paiement, annee_paiement)`. Le métier a depuis établi que le cycle
> est **hebdomadaire** (point M-04), et la période est devenue un intervalle de dates
> `(date_debut, date_fin)`. Ce qui est écrit ici reste vrai de son époque et n'est
> **pas** réécrit : un enregistrement daté qu'on corrige après coup cesse d'être un
> enregistrement. Voir
> `docs/decisions/2026-09-09-rythme-de-paiement-et-maille-de-la-periode.md` et
> `docs/resumes-sprints/sprint-maille-1-periode-en-intervalle-de-dates.md`.

**Date :** 3 septembre 2026
**Sprint :** 6.1, suivi des demandes et recherche multicritère
**Statut :** tranchée avec l'utilisateur avant tout codage
**Règles :** US-15, CT-30, CT-31, RG-06

---

## 1. Le problème

Le service Reporting est le seul du module qui **n'a pas de base**. Toutes les données
qu'il présente vivent ailleurs. La recherche multicritère du cahier des charges (CT-30)
porte sur cinq critères, et ces cinq critères sont répartis sur deux bases :

| Critère | Où il vit | Table |
| --- | --- | --- |
| Période (mois, année) | service Workflow | `processus_mensuel` |
| Unité | service Workflow | `processus_mensuel` |
| Nature (RATION / TRANSPORT) | service Saisie | `ligne_prestation` |
| Session (JOUR / SOIR) | service Saisie | `ligne_prestation` |
| Bénéficiaire | service Saisie | `beneficiaires` + `ligne_prestation` |

**Aucune jointure SQL n'est possible** : ce sont deux bases distinctes, et l'accès direct
à la base d'un autre service est interdit (AR04, CLAUDE.md §3). Le croisement doit donc
se faire par les API, et tenir la cible de **trois secondes** du document maître.

### Deux constats d'état des lieux, faits avant l'arbitrage

1. **Aucun des deux services appelés n'expose de liste.** Workflow ne rend que le détail
   d'*un* processus, Saisie que l'état consolidé d'*un* processus. Ce sous-sprint ajoute
   donc **deux endpoints internes de lecture**, hors contrat passerelle — même statut que
   `GET /saisie/processus/{id}/etat` (Sprint 3.4) et `GET /identite/habilitation`
   (Sprint 1.3).
2. **`fiche_journaliere` porte `code_unite`, `mois_paiement` et `annee_paiement` figés**
   (Sprint 3.1, migration V3). La Saisie sait donc filtrer par période et par unité toute
   seule. C'est ce qui rend le croisement possible **sans faire voyager de listes
   d'identifiants** entre les deux services.

---

## 2. Les stratégies examinées

| # | Stratégie | Appels HTTP | Sort |
| --- | --- | --- | --- |
| **A** | Un appel par service au maximum, croisement dans le Reporting | **1 ou 2, fixes** | **retenue** |
| B | Pagination déléguée au Workflow, filtre Saisie appliqué après | 1 ou 2, fixes | écartée |
| C | Le Reporting fait voyager les identifiants retenus vers le second service | 1 ou 2, fixes | écartée |
| D | Un appel Saisie par état candidat | **N**, proportionnel au résultat | écartée |

**B est écartée parce qu'elle rend la pagination fausse.** Le Workflow paginerait sur un
ensemble plus large que l'ensemble final : une page demandée à 10 en rendrait 3, et le
`totalElements` annoncé serait un nombre que rien ne vérifie. Une pagination dont le total
ment n'est pas une pagination.

**C est écartée pour un gain nul.** Elle déléguerait la pagination exacte à la base du
second service, au prix d'une URL portant plusieurs centaines d'identifiants, d'un
`IN (...)` de même taille, et d'un couplage plus fort entre les deux endpoints internes.
Aux volumes réels du module (§4), elle ne gagne rien de mesurable. Elle reste **la bonne
correction le jour où la borne du §4 mord** : elle est consignée à ce titre dans
`docs/points-en-attente.md`.

**D est interdite par le guide** (§10) et par le bon sens : une recherche rendant 200
états déclencherait 200 appels HTTP.

---

## 3. Décision — stratégie A, et ce qu'elle fait exactement

```
1. GET <workflow>/processus/recherche -> les EN-TETES des etats VISIBLES PAR L'APPELANT
   (mois, annee, codeUnite, limite)      periode, unite, montant total, statut,
                                         statut d'integration, drapeau de transmission

2. GET <saisie>/processus/recherche   -> les id_processus contenant AU MOINS UNE ligne
   (mois, annee, nature, session,        correspondante
    beneficiaire)                        -- appel EFFECTUE UNIQUEMENT si l'un des trois
                                         criteres de ligne est demande

3. ici                                -> intersection, dans l'ordre rendu par le Workflow,
                                         puis pagination
```

**Un ou deux appels HTTP**, jamais davantage.

### La portée d'accès n'est jamais un paramètre

Aucun des deux appels ne transporte de code unité de portée. **Chaque service résout
lui-même la portée depuis le jeton relayé**, en lisant le champ `porteeAcces` que le
service Identité publie sur `GET /identite/moi`. Le Reporting n'a donc pas à la connaître,
et surtout : un appel direct forgé sur le port 8084 ou 8082 **ne peut pas s'attribuer des
unités**, puisqu'il n'existe aucun champ où les déclarer.

C'est la doctrine du Sprint 3.4 — « un paramètre fourni par l'appelant ne se croit pas sur
parole » — poussée un cran plus loin : le paramètre n'existe pas.

Ce n'est pas une réinterprétation locale de la portée, que le service Workflow écarte à
juste titre dans `ProfilReponse` : c'est la **lecture du champ que son propriétaire
publie**. Seul l'angle de la question change — « peut-il agir sur l'unité X », à laquelle
`GET /identite/habilitation` répond, contre « quelles unités peut-il voir », à laquelle
elle ne sait pas répondre sans un appel par code guichet du référentiel.

Le nombre d'appels est **fixe**, quel que soit le nombre de résultats. Cas nominal mesuré
en dizaines de millisecondes par appel, soit très loin de la cible de trois secondes.

### Comportement quand un filtre porte sur les deux sources à la fois

**Intersection stricte.** Un état ressort de la recherche s'il satisfait *à la fois* les
critères d'en-tête (période, unité) **et** contient au moins une ligne satisfaisant les
critères de ligne (nature, session, bénéficiaire). C'est la lecture naturelle d'une
recherche multicritère, et la seule qui ne surprenne pas : demander « août 2026 +
RATION » ne doit pas rendre les états d'août sans ration.

Le grain du résultat reste **l'état mensuel**, pas la ligne : `GET /reporting/demandes`
rend des demandes, pas des prestations. Un critère de ligne agit donc comme un filtre
d'existence sur l'état, jamais comme un découpage de son contenu.

### Contrepartie assumée

La pagination se fait **en mémoire dans le Reporting**, donc les deux services rendent
toute la portée avant découpage. C'est ce qui impose la borne du §4.

---

## 4. La borne de volume : 5 000 en-têtes, configurable

### L'arithmétique qui la fixe

- Un état NORMAL par unité et par mois (RG-06), plus les COMPLEMENTAIRE éventuels.
- Référentiel des codes guichets : de l'ordre de **50 unités** (CLAUDE.md §13).
- Soit **~600 états par an** pour l'ensemble du pays.
- Une recherche nationale sans aucun filtre atteint donc 600 après un an, **3 000 après
  cinq ans**, 4 800 après huit.

**Borne retenue : 5 000 en-têtes**, soit environ 1 Mo de JSON — un ordre de grandeur en
dessous de ce qui menacerait la cible de trois secondes, et de l'ordre de huit années de
données nationales complètes.

### Elle est une propriété de configuration, pas une constante compilée

`app.reporting.limite-resultats`, défaut `5000`. Le jour où elle mord, elle se relève par
variable d'environnement, sans redéploiement de code, le temps de faire la vraie
correction — descendre la pagination dans les bases, c'est-à-dire la stratégie C.

Ce n'est **pas** une valeur métier au sens de `parametre_systeme` : ce n'est pas une règle
de gestion mais un garde-fou technique, et le service Reporting n'a de toute façon aucune
base où la loger.

### Le refus est explicite, et ne ressemble à rien d'autre

Trois réponses **strictement distinctes**, jamais confondues :

| Situation | Réponse |
| --- | --- |
| Rien ne correspond | `200`, `content: []`, `totalElements: 0`. Réponse **normale**. |
| Trop d'états pour être montrés | **`422 RECHERCHE_TROP_LARGE`**, message nommant le nombre trouvé, la borne, **et l'action attendue**. |
| Un service ne répond pas | code distinct — voir §5. |

`422` et non `500` : ce n'est pas une panne, et l'appelant *peut* corriger en affinant sa
recherche. C'est une règle de gestion qui refuse, distinction déjà posée au Sprint 2.3
(`TRANSITION_INTERDITE`) et au Sprint 3.3 (`ETAT_NON_MODIFIABLE`).

**Le motif de cette exigence.** Si un dépassement de borne se présentait comme une absence
de résultat, une analyste RH cherchant sur une année entière verrait un écran vide et
conclurait qu'il n'y a rien à trouver — alors qu'il existe des milliers de dossiers
qu'elle ne voit pas. C'est le principe déjà posé partout ailleurs dans le module :
« aucune donnée » et « je ne peux pas te la montrer » ne se confondent jamais
(`INCOHERENCE_GRILLE` au 2.4, `SEUIL_INDISPONIBLE` au 4.3, les cinq situations nommées de
la consultation d'intégration au 5.3).

Le message est donc de la forme :

> « 6 214 états correspondent à votre recherche, au-delà de la limite de 5 000 résultats.
> Ajoutez un filtre de période ou d'unité pour restreindre la recherche. »

---

## 5. Décision — service injoignable : échec net, jamais un résultat partiel

Trois situations, dont une seule appelait un arbitrage.

| Situation | Comportement | Pourquoi il n'y avait pas le choix |
| --- | --- | --- |
| **Workflow injoignable** | `503 SERVICE_WORKFLOW_INDISPONIBLE` | Aucun résultat n'est concevable sans lui : les en-têtes viennent de sa base et de nulle part ailleurs. |
| **Saisie injoignable, sans critère de ligne** | sans effet | Elle n'est pas appelée du tout. Un appel de moins dans le cas courant, et une panne sans conséquence sur une recherche qui ne la concerne pas. |
| **Identité injoignable sur l'historique** | non bloquant | L'historique s'affiche avec les identifiants d'acteurs au lieu des logins. Ce n'est pas une décision d'accès — le cloisonnement a déjà été tranché en amont par le Workflow —, c'est un libellé. Faire échouer un dossier entier pour un nom manquant priverait le contrôle interne de ce qu'il consulte, pour une raison cosmétique. |

### Le cas arbitré : Saisie injoignable **et** critère de ligne demandé

On détient les en-têtes, on ne peut pas appliquer le filtre nature / session /
bénéficiaire. Deux options se présentaient.

**Retenue : échec net, `503 SERVICE_SAISIE_INDISPONIBLE`.** Le message nomme les critères
qui n'ont pas pu être appliqués et donne les deux issues : réessayer, ou relancer la
recherche sans eux.

**Écartée : résultat partiel signalé par un drapeau.** Le résultat partiel serait ici
**plus large** que ce qui a été demandé — tous les états de la période, y compris ceux qui
ne contiennent aucune ration. Affichée sous une étiquette « RATION », cette liste est
aussi trompeuse qu'une liste trop étroite, et bien plus difficile à repérer : rien dans les
lignes affichées ne signale qu'elles ne répondent pas au critère. Le drapeau ne protège que
le lecteur qui pense à le lire.

C'est la doctrine constante du module : **refuser et signaler, jamais arbitrer** —
`INCOHERENCE_GRILLE` au Sprint 2.4, `SEUIL_INDISPONIBLE` au 4.3, le refus des accusés
contradictoires au 5.2.

## 6. Ce qui reste ouvert

La borne du §4 est un garde-fou, pas une architecture. La correction de fond est de
descendre la pagination dans les bases (stratégie C), ce qui suppose de faire voyager les
identifiants ou d'accepter un couplage plus fort entre les endpoints internes. Consigné
dans `docs/points-en-attente.md` avec une **date de revue**, pour que la valeur ne soit pas
redécouverte en production le jour où les recherches commencent à être refusées.
