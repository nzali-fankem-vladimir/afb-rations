# Bornage des périodes et atomicité de la bascule des grilles

**Date :** 2026-08-27
**Sprint :** 2.3 — Décision de la Directrice RH
**Règles concernées :** RG-14, RG-03 (indirectement), CT-27, CT-29
**Portée :** service-grilles ; **conséquences directes sur les Sprints 2.4, 3 et 6bis**

---

## 1. La question posée

Valider une grille suppose de fermer celle qu'elle remplace. Deux questions
distinctes se posaient, et l'étape 2 du guide demandait explicitement d'arbitrer
la première.

1. **Quelle `date_fin` poser sur l'ancienne grille ?** La veille de la
   `date_debut` de la remplaçante, ou la date à laquelle la DRH a tranché ?
2. **Comment garantir que les deux lignes bougent ensemble ?**

---

## 2. Décision 1 — La veille de la `date_debut` de la remplaçante

**Retenu :** `ancienne.date_fin = nouvelle.date_debut - 1 jour`.

### Pourquoi

Les deux options se défendent en lecture. Elles ne se valent pas dès qu'on
interroge le passé.

La validation et la prise d'effet sont deux instants différents : la DRH tranche
le 27 août une grille qui prend effet le 1er septembre. Fermer à la date de
décision laisserait les 28, 29, 30 et 31 août **sans aucune grille active** sur
le couple. Une prestation datée de ces jours-là — saisie en retard, ou
régularisée par un état complémentaire au Sprint 6bis — ne trouverait aucun
montant applicable. Le module ne saurait pas quoi facturer, et le blocage
surviendrait loin de sa cause.

Symétriquement, fermer *après* la prise d'effet ferait se chevaucher deux
périodes, et `rechercherGrilleActive` renverrait deux lignes là où le code en
attend une.

La veille est la seule borne qui produise un **partitionnement exact** du temps :
à toute date, une grille et une seule répond pour un couple donné.

### Conséquence assumée

Quand la remplaçante prend effet dans le futur, l'ancienne porte **dès
aujourd'hui** une `date_fin` future. Elle reste en vigueur jusque-là, ce qui est
correct, mais l'interface doit le présenter comme une *fermeture programmée* et
non comme une grille déjà close. Le champ `ancienneFermee` de la réponse de
validation porte cette information.

### Ce qui en dépend

- **Sprint 2.4** — la résolution du montant à une date donnée repose entièrement
  sur ce partitionnement. `rechercherGrilleActive` renvoie au plus une ligne
  *parce que* les périodes ne se chevauchent pas.
- **Sprint 6bis** — une régularisation sur une période passée résout son montant
  à la date de la prestation. Un trou rendrait la régularisation impossible, un
  chevauchement la rendrait fausse.

---

## 3. Décision 2 — Atomicité, et ordre d'écriture imposé

**Retenu :** fermeture et activation dans **une seule transaction**, avec un
**vidage explicite** (`saveAndFlush`) entre les deux.

### La transaction

Une fermeture réussie suivie d'une activation échouée laisserait le couple sans
grille active : toute saisie du Sprint 3 s'y briserait. La transaction ramène le
pire cas à « la validation a échoué, réessayez », qui est récupérable.

L'appel réseau au service Identité est placé **avant** l'ouverture de la
transaction — sinon une transaction resterait ouverte pendant les trois secondes
de délai de lecture, en tenant une connexion à la base. La publication d'audit
est **après le commit**, par construction de `rations-audit-commun` (décision
Sprint 1.3).

### Pourquoi le vidage explicite

L'index partiel `ux_grille_active_par_couple` porte sur
`statut_validation = 'ACTIVE' AND date_fin IS NULL`, et PostgreSQL l'évalue
**instruction par instruction**, pas au commit. Si Hibernate écrivait
l'activation de la cible avant la fermeture de l'ancienne, il existerait l'espace
d'un ordre SQL deux lignes actives sans date de fin, et la base refuserait une
bascule pourtant légitime.

L'ordre de vidage du contexte de persistance n'est pas un contrat public
d'Hibernate. Il est donc **fixé explicitement** plutôt que subi : `saveAndFlush`
sur l'ancienne, puis `save` sur la cible. Le test `1bis` de
`DecisionGrilleServiceTest` verrouille cet ordre, et le test `13` de
`GrilleTarifaireRepositoryTest` vérifie contre la vraie base que
l'enchaînement passe.

### Vérification du statut avant toute écriture

Le contrôle « la grille est-elle bien `EN_ATTENTE_DRH` ? » est fait **avant** de
toucher à l'ancienne grille, via `TransitionGrille.exigerValidationPossible`.
Vérifier après aurait fonctionné — le rollback aurait rattrapé — mais la
correction du résultat aurait alors dépendu d'un mécanisme technique plutôt que
de l'ordre des étapes.

---

## 4. Décision 3 — Le rejet ne touche jamais la grille en vigueur

Un rejet dit « ce tarif ne s'appliquera pas », pas « il n'y a plus de tarif ». Le
service de rejet **ne consulte même pas** la grille courante : il n'a rien à lui
faire. Confondre rejet et fermeture priverait le couple de grille active sans
raison, et bloquerait les saisies alors que rien n'a changé (test 10).

Le motif est exigé **non vide**, pas seulement non nul, à deux étages : `@NotBlank`
sur le DTO (400, lisible par le frontend) et `TransitionGrille.rejeter` (422
`MOTIF_OBLIGATOIRE`, garantie que la règle tienne quel que soit l'appelant).

---

## 5. Codes d'erreur ajoutés au contrat

| Code | HTTP | Cause |
| --- | --- | --- |
| `GRILLE_INTROUVABLE` | 404 | Aucune grille ne porte cet identifiant |
| `TRANSITION_INTERDITE` | 422 | Statut incompatible avec la décision demandée |
| `MOTIF_OBLIGATOIRE` | 422 | Rejet sans motif exploitable |

`MOTIF_OBLIGATOIRE` est le code déjà retenu par le contrat d'API pour le retour
d'un processus sans motif (section 5) : même règle, même code, pour que le
frontend n'ait pas deux traitements à écrire.

`TRANSITION_INTERDITE` est en 422 et non 409 : la ressource existe et rien ne la
duplique — c'est une règle de gestion qui refuse l'opération.

---

## 6. Réversibilité

La décision de bornage est **peu réversible une fois des grilles fermées** : les
`date_fin` déjà posées porteraient l'ancienne convention. Un changement ultérieur
exigerait une reprise de données sur `grille_tarifaire`. C'est pourquoi elle a
été tranchée avant écriture de la première ligne de Java, comme le guide le
demandait.

L'atomicité et l'ordre d'écriture, eux, sont internes au service et modifiables
sans impact sur les données.
