# Endpoint interne de consolidation, et code unité transmis en paramètre

**Date :** 2026-08-31 — Sprint 3.4
**Statut :** adoptée
**Portée :** service Saisie, et service Workflow au Sprint 4

---

## 1. Le problème

RG-06 impose une consolidation mensuelle par unité. Elle se partage entre deux
services : Saisie détient les données, Workflow porte le montant total et
l'aiguillage. Il faut donc un appel de Workflow vers Saisie, et trois questions se
posent ensemble :

1. Cet endpoint fait-il partie du contrat d'API public, ou est-il interne ?
2. Comment vérifier la **portée d'accès** — quel agent a le droit de lire quel
   état — sans que Saisie ait à redemander l'unité du processus à Workflow ?
3. Que répondre pour un processus dont **aucune journée n'a encore été saisie** ?

---

## 2. Décision 1 — l'endpoint est interne

`GET /saisie/processus/{id}/etat` **n'appartient pas au contrat exposé par la
passerelle.**

| | Endpoint public du contrat | Cet endpoint |
|---|---|---|
| Chemin | `GET /processus/{id}/etat` | `GET /saisie/processus/{id}/etat?codeUnite=…` |
| Service | **Workflow**, 8084 | **Saisie**, 8082 |
| Routé par la passerelle | oui | **non** |
| Compté dans les 26 endpoints (CLAUDE.md §11) | oui | **non** |
| Appelé par | le frontend | **le service Workflow** |

C'est exactement le statut de `GET /identite/habilitation?codeUnite=…` (décision
Sprint 1.3) : un endpoint interne qui répond à une question posée par un autre
service, et qui ne modifie pas le compte du contrat public. Le service Saisie
expose donc **cinq endpoints publics et un seul interne**.

Workflow reprendra cette réponse, y ajoutera ce qu'il détient seul — statut, type
de processus, `montant_total` — et servira l'endpoint public du contrat.

**Motif.** Le contrat décrit l'état consolidé comme une ressource du processus,
qui est un objet du domaine de Workflow. L'exposer une seconde fois côté Saisie
donnerait au frontend deux chemins vers la même information, avec deux formes
différentes et deux jeux de règles d'accès à tenir cohérents.

---

## 3. Décision 2 — le code unité est transmis en paramètre, obligatoire

`codeUnite` est un paramètre de requête **obligatoire, sans valeur par défaut**,
fourni par le service Workflow qui détient `processus_mensuel.code_unite`.

### 3.1 Ce que l'option écartée aurait produit

L'option naturelle était de lire l'unité sur les fiches, où elle est recopiée et
figée depuis la migration V3 — c'est déjà ce que fait la consultation des lignes
d'une fiche. Elle échoue sur un cas précis : **un processus sans aucune fiche n'a
aucune unité à lire.**

Le contrôle de portée aurait alors disparu au moment exact où il n'y a rien à
lire. Ce n'est pas un simple manque : c'est un contrôle qui s'évapore
silencieusement selon l'état des données, donc un contrôle sur lequel on ne peut
pas raisonner. Concrètement, un chef d'unité aurait été traité différemment selon
que son dossier contenait ou non des lignes — un comportement qui n'aurait
probablement été découvert qu'au premier essai réel du Sprint 4.

### 3.2 Ce qui a aussi été écarté

Redemander l'unité au service Workflow. Cela aurait produit
`Workflow → Saisie → Workflow` sur le chemin le plus emprunté du Sprint 4 :
latence doublée et **dépendance mutuelle à l'exécution** entre deux services que
l'architecture veut indépendants.

### 3.3 Le trou que le paramètre ouvre, et comment il est fermé

Un paramètre fourni par l'appelant ne peut pas être cru sur parole. Sans
précaution, un agent habilité sur `00002` appellerait
`/saisie/processus/999/etat?codeUnite=00002` où le processus 999 relève en réalité
de `00007` : il franchirait le contrôle d'habilitation et recevrait les lignes
d'une unité qui ne le regarde pas.

D'où la règle en trois temps :

```
1. Habilitation    l'appelant a-t-il droit sur l'unité déclarée ?   ← toujours,
                                                                     même état vide
2. Lecture         les fiches du processus
3. Recoupement     le code_unite figé sur les fiches correspond-il
                   à l'unité déclarée ?
```

**Le `code_unite` figé sur les fiches reste l'autorité ; le paramètre n'est qu'une
déclaration à vérifier.** S'il n'y a aucune fiche, il n'y a rien à recouper — mais
rien à divulguer non plus, la réponse est vide.

Un repli silencieux (« si le paramètre est absent, lire sur les fiches »)
rouvrirait le trou du §3.1 sans jamais déclencher d'erreur. Le paramètre est donc
obligatoire, pour la même raison que `date` sur `GET /grilles/active` au
Sprint 2.4.

### 3.4 Désaccord : `403`, tracé

`403 UNITE_NON_CONCORDANTE`, publié en audit avec le motif du même nom.

Rien ne permet de distinguer, au moment du refus, un défaut du service appelant
d'une tentative de débordement de périmètre. La doctrine du refus conservateur
(Sprint 1.3) tranche : refuser, et tracer. Un défaut réel de Workflow apparaîtra
bruyamment dans le journal d'audit, avec un message nommant les deux unités.

Un `422` aurait été plus fidèle au cas du bug, mais la tentative se serait alors
noyée parmi les erreurs d'usage, sans être tracée comme un refus d'accès (CT-04).

### 3.5 Limite connue

Les fiches antérieures à la migration V3 portent un `code_unite` nul. Une valeur
absente ne peut pas contredire la déclaration : elle est écartée du recoupement.
Un processus dont **toutes** les fiches seraient dans ce cas ne serait donc pas
recoupé — le fait est journalisé en `WARN`, nommant le processus. Toute fiche
ouverte depuis le Sprint 3.3 porte la valeur ; la limite est transitoire et
concerne les seules lignes créées lors des essais manuels des Sprints 3.1 et 3.2.

---

## 4. Décision 3 — les rôles du circuit, pas le seul agent

`AGENT_UNITE`, `CHEF_UNITE_DA`, `DIRECTEUR_RESEAU_DR`.

C'est ce que dit le contrat d'API §5 pour `/processus/{id}/etat` (« rôles du
circuit »). Le chef d'unité et le directeur réseau doivent lire l'état qu'ils sont
en train de valider ; le leur interdire bloquerait le workflow du Sprint 4.

**Conséquence :** un contrôleur séparé, `ConsolidationController`. Ajouter la
méthode à `SaisieController`, réservé à `AGENT_UNITE`, aurait imposé d'assouplir
le rôle des cinq endpoints d'écriture — un prix bien supérieur à celui d'une
classe de plus.

---

## 5. Décision 4 — un processus sans fiche est un `200`, pas un `404`

Réponse `200` avec `journees: []`, `montantTotalFcfa: 0`, `moisPaiement` et
`anneePaiement` nuls, `codeUnite` renseigné par écho de la déclaration.

**Motif.** Un processus qui vient d'être ouvert et où l'agent n'a encore rien
saisi est un état normal. Même parti qu'au Sprint 2.4 pour `GET /grilles/active`
(`200` avec `disponible: false`) : un endpoint interne qui répond à une question
métier ne déguise pas une réponse négative légitime en erreur de transport.

Répondre `404` serait de surcroît **faux** : Saisie ne sait pas si le processus
existe — `processus_mensuel` vit dans une autre base. Elle sait seulement qu'elle
ne détient rien pour cet identifiant. Confondre les deux forcerait Workflow à
interpréter une erreur pour un cas nominal.

Effet secondaire favorable : un identifiant inexistant et un processus réellement
vide produisent la même réponse, qui ne divulgue rien.

**C'est à Workflow de refuser la soumission d'un état vide**, à partir de
`nombreLignes == 0`. Sa règle, pas celle de Saisie.

---

## 6. Décision 5 — où le montant est calculé

**Dans `ConsolidationService`, et nulle part ailleurs.** Le total du mois est la
somme des sous-totaux journaliers, eux-mêmes sommes des lignes effectivement
rendues dans la réponse. Les DTO de sortie recopient sans rien recalculer.

Un `SUM(...)` SQL posé à côté aurait produit un second chemin de calcul, avec sa
propre clause `WHERE`. Le jour où l'un des deux dérive, l'état afficherait un
détail et un total qui ne s'additionnent pas, et rien ne le signalerait. Ici, la
divergence est **structurellement impossible** : retirer une ligne du détail la
retire du total dans le même mouvement.

Deux conséquences techniques :

- **Lecture par `IN`, jamais par jointure.** Une jointure mal posée peut
  multiplier les lignes, donc compter deux fois un montant. Avec un `IN` sur
  `id_fiche_journaliere`, chaque ligne apparaît au plus une fois.
- **Entiers uniquement.** `int` par ligne, `long` pour les sommes. Aucun flottant,
  aucun `BigDecimal`. Un seul flottant introduit dans la chaîne suffirait à
  produire un écart d'arrondi sur le montant qui commande l'aiguillage au seuil de
  100 000 XAF (RG-08) — une erreur d'un franc envoie un dossier au mauvais niveau
  de validation.

---

## 7. Décision 6 — la méthode n'est pas transactionnelle

Elle commence par un appel réseau au service Identité. L'englober dans une
transaction immobiliserait une connexion de la réserve pendant tout cet appel — le
défaut que la doctrine du Sprint 2.3 écarte déjà à la bascule des grilles.

**Conséquence assumée :** les fiches et leurs lignes sont lues en deux temps. Une
journée ouverte entre les deux lectures serait absente de la réponse — mais
absente *cohéremment* : `nombreJournees` et `montantTotalFcfa` la reflètent l'un
comme l'autre, exactement comme si la requête était arrivée un instant plus tôt.
Aucune ligne ne peut en revanche être comptée deux fois.

Et cette fenêtre n'existe **que tant que l'état est en saisie** : dès la
soumission, `EtatModifiableService` refuse toute écriture. Le montant sur lequel se
décide l'aiguillage est donc lu sur un état qui ne peut plus changer.

---

## 8. Ce qui est attendu du Sprint 4

- Transmettre `codeUnite` à chaque appel — voir `docs/appel-consolidation.md` §2.2.
- Reporter `montantTotalFcfa` tel quel dans `processus_mensuel.montant_total`,
  sans le réadditionner.
- **Refus conservateur** si Saisie est injoignable : ne jamais enregistrer un
  `montant_total` partiel, à zéro, ou repris d'une lecture antérieure. Code
  attendu : `503 SERVICE_SAISIE_INDISPONIBLE`.
- Refuser la soumission d'un état vide, sur `nombreLignes == 0`.
- Remplacer le bouchon `BouchonVerificationProcessus` (Sprint 3.3) par le véritable
  appel — voir `docs/dispositifs_provisoires.md`.
