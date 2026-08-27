# Identifiant et libellé de l'acteur d'une grille tarifaire

Module Paiement des Rations et du Transport de la Garde Armée — décision Sprint 2.2

Comment `service-grilles` obtient l'identifiant local et le nom lisible de l'ARH
qui crée une grille, sans lire la base du service Identité. Conditionne le
Sprint 2.3 (`id_validateur`, même problème côté DRH) et tout service futur
devant inscrire un auteur dans une table.

---

## 1. Le problème n'est pas le libellé, c'est l'identifiant

La table `grille_tarifaire` porte `id_createur BIGINT NOT NULL` : l'identifiant
local de l'ARH dans la table `utilisateurs`, qui vit dans la base
`rations_identite`.

Le jeton Keycloak porte le `sub` (un UUID), le `preferred_username`, le rôle de
realm. **Il ne porte pas cet identifiant numérique, et ne peut pas le porter** :
CLAUDE.md §10 pose que l'identifiant local est attribué par l'administrateur au
pré-provisionnement — il n'existe pas dans l'annuaire.

> `service-grilles` doit donc interroger `service-identite` au moment de la
> création, **quelle que soit la réponse retenue sur le libellé**. La dépendance
> est imposée par le schéma, pas par un choix de conception.

La vraie question tranchée ici est : que fait-on de cette information une fois
obtenue ?

---

## 2. Décision

**Un seul appel `GET /identite/moi` à la création, puis recopie du libellé dans
la ligne.**

1. Au `POST /grilles`, `ClientIdentite` relaie l'en-tête `Authorization` de l'ARH
   vers `GET /identite/moi` et obtient `{ id, login, nom, prenom }`.
2. `id_createur` reçoit l'identifiant ; `libelle_createur` reçoit `« NKOLO
   Claire »` (migration additive `V4`).
3. `GET /grilles` ne traverse **jamais** le réseau : les libellés sont déjà dans
   les lignes.

Symétriquement au Sprint 2.3 : `libelle_validateur` est recopié au moment de la
décision DRH.

### Options écartées

| Option | Pourquoi écartée |
|---|---|
| Retraduire `id → nom` à chaque lecture | **Impossible en l'état** : aucun endpoint ne permet à l'ARH de traduire un identifiant en nom (`/identite/utilisateurs` est réservé au rôle ADMIN). Exigerait un nouvel endpoint dans `service-identite`, hors périmètre. Et même débloquée : un appel réseau par ligne affichée, sur le chemin de consultation — une panne d'identité empêcherait la simple lecture. |
| Projection locale des utilisateurs par Kafka | Ajouterait une **11ᵉ table** (CLAUDE.md §4 en fixe 10) et un **4ᵉ topic** (§9 en fixe 3). Disproportionné pour afficher un nom. |
| N'exposer que l'identifiant technique | « Créé par 4 » est exact et inexploitable par un Analyste RH. |

---

## 3. Le libellé est figé, et c'est voulu

`libelle_createur` est une **copie prise au moment de l'acte**, pas un cache à
rafraîchir. Si Claire NKOLO change de nom ou quitte la banque, la grille de
septembre continue de nommer son auteur d'alors.

C'est le comportement attendu d'une pièce de contrôle interne : la question
posée est « qui a créé cette grille, tel qu'il était connu ce jour-là », pas
« qui est cette personne aujourd'hui ». L'identifiant technique, lui, reste la
donnée de référence et permet toujours de retrouver le profil courant.

Les colonnes sont **nullables** à dessein : le libellé est un confort de lecture,
pas une donnée de référence. Une contrainte `NOT NULL` ferait dépendre l'écriture
en base de la disponibilité d'un service distant, ce qui n'a pas sa place dans un
schéma. Quand le libellé manque, `GrilleResponse` affiche `utilisateur #4` — une
trace moins lisible reste une trace, un champ vide laisserait croire que la
grille n'a pas d'auteur.

---

## 4. Ce que cela fait à l'indépendance du service

`service-grilles` acquiert une **dépendance d'exécution** vers `service-identite`,
limitée au chemin d'écriture.

Ce n'est **pas** un couplage de code ni de base : aucune classe partagée, aucun
accès à `rations_identite`, aucune clé étrangère inter-base. C'est un appel REST
sur un contrat interne — exactement la doctrine déjà posée au Sprint 1.3
(`docs/appel-habilitation.md`), et l'une des rares dépendances synchrones admises
en microservices.

Portée du couplage :

| Chemin | Dépend de service-identite ? |
|---|---|
| `POST /grilles` | Oui. Une panne empêche de créer une grille. |
| `GET /grilles` | Non. La consultation fonctionne service Identité arrêté. |
| Résolution du montant (Sprint 2.4) | Non. La chaîne de paiement n'est pas touchée. |

Une panne d'identité empêche donc de **proposer** un tarif, jamais d'en
**appliquer** un.

---

## 5. Refus conservateur, et le code HTTP rendu

Conformément à la doctrine du Sprint 1.3 : timeout, connexion refusée, `5xx`,
corps illisible → **l'action est refusée**. Aucun cache d'une réponse antérieure,
aucun auteur par défaut. Une grille dont on ne sait pas qui l'a créée n'a pas sa
place dans une pièce de contrôle interne.

Les délais sont bornés court — 2 s de connexion, 3 s de lecture — parce que le
défaut laisserait un ARH devant un écran figé pendant une minute avant
d'apprendre que rien n'a été enregistré.

**Le code rendu est `503 SERVICE_IDENTITE_INDISPONIBLE`, pas `403`.** Décision
prise en cours de sprint : l'ARH possède le droit qu'il exerce. Lui répondre
« accès refusé » l'enverrait réclamer à l'administrateur une habilitation qu'il a
déjà, pendant que la panne réelle resterait invisible. Le refus est identique, le
diagnostic rendu est juste.

Distinction maintenue avec le `403` : celui-ci reste opposé quand
`service-identite` **répond** que ce compte n'a aucun profil ouvert dans le
module (`AuteurNonHabiliteException`). Là, l'ARH doit bien s'adresser à
l'administrateur.

**Les deux cas sont tracés** en audit (`ACCES_REFUSE`, motifs
`IDENTITE_INDISPONIBLE` et `HABILITATION_ABSENTE`), depuis
`GestionnaireErreursApi` — seul point où tous les refus convergent, comme dans
`service-identite` (décision Sprint 1.3). Le cas de la panne est le plus
important à tracer : sans lui, une indisponibilité prolongée ne laisserait
aucune marque qu'un travail a été empêché.

---

## 6. À reprendre aux sprints suivants

- **Sprint 2.3** : `id_validateur` et `libelle_validateur` suivent le même
  chemin, au moment de la décision DRH. Le mutateur
  `enregistrerLibelleValidateur` existe déjà sur l'entité.
- **Sprints 3 à 6** : tout service devant inscrire l'auteur d'une action dans sa
  base rencontrera ce problème à l'identique. Le motif retenu — *appel à
  l'écriture, copie figée, lecture locale* — est réutilisable tel quel.
- **Passerelle (Sprint 9)** : si la passerelle vient un jour enrichir les
  requêtes d'un en-tête `X-Utilisateur-Id`, l'appel disparaîtrait de chaque
  service. Piste à évaluer alors, pas avant : elle suppose que les services ne
  soient joignables que par la passerelle.
