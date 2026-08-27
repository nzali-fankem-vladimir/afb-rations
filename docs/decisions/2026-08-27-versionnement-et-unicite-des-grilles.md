# Versionnement des grilles tarifaires et périmètre du contrôle d'unicité

Module Paiement des Rations et du Transport de la Garde Armée — décisions Sprint 2.2

Trois décisions liées, prises en cours de sous-sprint 2.2, sur des points que ni
le cahier des charges, ni le contrat d'API, ni US-13 ne tranchaient
explicitement. Elles conditionnent le Sprint 2.3 (décision DRH) et le Sprint 2.4
(résolution du montant, RG-03).

---

## 1. Modifier une grille ACTIVE crée une nouvelle ligne

**Décision.** Quand l'Analyste RH veut changer un tarif en vigueur, le système
**crée une nouvelle ligne** au statut `EN_ATTENTE_DRH`. La grille en vigueur
n'est pas touchée. Elle sera fermée — `date_fin` posée à la veille de la
`date_debut` de la remplaçante — au moment où la DRH valide (Sprint 2.3), dans la
même transaction.

**Écarté :** la mise à jour en place de la ligne existante.

### Pourquoi

| Critère | Nouvelle ligne | Mise à jour en place |
|---|---|---|
| Historique | Chaque montant garde sa période de validité. Un contrôleur peut toujours répondre « ce montant venait de la grille n°12, valide du X au Y ». | Perdu. Le montant précédent n'existe plus nulle part. |
| Lignes de prestation déjà payées | Restent explicables : la grille d'origine existe toujours, avec ses bornes. | Deviennent inexplicables : la grille référencée porte désormais un autre montant, sans trace de l'écart. |
| Index partiel `ux_grille_active_par_couple` | Compatible sans effort : la nouvelle ligne naît `EN_ATTENTE_DRH`, donc hors de l'index. | Exigerait la transition `ACTIVE → EN_ATTENTE_DRH`, **explicitement interdite** par `TransitionGrille` (Sprint 2.1, livré et testé). |
| Contrat d'API | Cohérent : il n'existe aucun `PUT /grilles`. « Modifier » = reposter un `POST /grilles`. | Supposerait un endpoint de mise à jour absent du contrat. |
| Continuité de service | La grille en vigueur continue de s'appliquer jusqu'à la validation. | Pendant l'attente DRH : soit un montant non validé s'applique (viole CT-25), soit le couple n'a plus aucune grille active et les saisies du jour échouent (RG-03). |

Le dernier point est décisif : la mise à jour en place n'a **aucune** issue
acceptable pendant le délai de décision de la DRH.

### Conséquences

- **Sprint 2.3.** La validation DRH est une opération à deux lignes,
  transactionnelle : fermeture de l'ancienne (`TransitionGrille.fermer`) et
  activation de la remplaçante. Les deux ou aucune — un commit partiel laisserait
  soit deux grilles courantes (violation d'index), soit aucune (rupture RG-03).
- **Bornage.** La `date_fin` de l'ancienne se pose à la **veille** de la
  `date_debut` de la remplaçante, les bornes de `rechercherGrilleActive` étant
  inclusives des deux côtés (décision Sprint 2.1). Sans cela, un jour de
  chevauchement rendrait deux grilles applicables à la même date.
- **Sprint 2.4.** La résolution du montant lit toujours une grille `ACTIVE` à une
  date donnée. L'historique la rend capable de justifier un montant passé, pas
  seulement le montant du jour.

---

## 2. Le contrôle d'unicité porte sur la cohérence de période, pas sur l'existence d'une grille active

**Décision.** `POST /grilles` refuse (409) dans deux cas, et deux seulement :

1. **Une proposition attend déjà la DRH sur ce couple** (`EN_ATTENTE_DRH`) →
   code `GRILLE_EN_ATTENTE_EXISTANTE`.
2. **La date de début n'est pas strictement postérieure** à celle de la grille
   en vigueur → code `GRILLE_ACTIVE_EXISTANTE` (code du contrat d'API).

Une proposition démarrant après la grille en vigueur est **acceptée** : c'est le
mécanisme de remplacement de la décision n°1.

**Écarté :** refuser toute création dès qu'une grille `ACTIVE` existe sur le
couple.

### Pourquoi

Le guide du Sprint 2.2 énonçait littéralement « couple déjà actif : 409 » (test
n°10), mais aussi une vérification complémentaire supposant qu'une grille
`EN_ATTENTE_DRH` **coexiste** avec une grille `ACTIVE`. Les deux ne peuvent pas
être vrais ensemble. Le guide fournit lui-même l'arbitrage :

- son test n°6 exige « un comportement conforme aux décisions prises aux étapes
  1 et 4 » — les décisions reforment le comportement attendu ;
- son exemple `curl` (§8) propose `TRANSPORT / SOIR` au 1er septembre sur un
  couple **déjà actif** depuis le 1er du mois courant, et annonce « Attendu :
  201 ».

Refuser toute création sur un couple actif rendrait par ailleurs la décision n°1
inapplicable : plus aucun tarif ne pourrait jamais changer.

### Pourquoi le refus d'anti-datage

Une proposition démarrant **avant ou le même jour** que la grille en vigueur est
irréconciliable avec la mécanique de fermeture :

```
en vigueur  : RATION/JOUR  1500 FCFA  début 01/08  fin (aucune)
proposition : RATION/JOUR  1800 FCFA  début 01/07

à la validation, fermeture de l'ancienne à la veille du 01/07 :
en vigueur  : RATION/JOUR  1500 FCFA  début 01/08  fin 30/06   <-- période à l'envers
```

L'ancienne grille devient un intervalle vide. Les lignes de prestation d'août,
payées 1 500 FCFA, ne se rattachent plus à aucune grille valide à leur date : le
montant payé devient injustifiable. Ce n'est pas un défaut d'affichage, c'est une
pièce comptable qui perd sa justification.

### Pourquoi refuser deux propositions concurrentes

Si deux grilles attendent la DRH sur `RATION / JOUR` et qu'elle valide les deux,
la seconde validation trouve la première déjà active. Laquelle ferme laquelle ?
La question n'a pas de bonne réponse, et la base trancherait par une violation
d'index — c'est-à-dire par une erreur technique opposée à la DRH.

### Ordre des deux contrôles

La proposition concurrente est signalée **avant** le problème de date : c'est le
seul des deux cas où l'ARH n'a rien à corriger. Lui parler d'abord de sa date
l'enverrait modifier une saisie qui n'a rien d'erroné.

### L'index reste indispensable

Le contrôle applicatif passe avant dans tous les cas séquentiels. Reste la
course : deux écritures simultanées que deux vérifications simultanées ne peuvent
pas voir l'une de l'autre. L'index partiel les arrête, et
`GestionnaireErreursApi` traduit sa violation en 409 lisible plutôt qu'en 500
citant un nom d'index PostgreSQL.

---

## 3. Création et soumission en un seul appel

**Décision.** `POST /grilles` crée la grille et la soumet dans la même
transaction. Ce qui atteint la base est déjà `EN_ATTENTE_DRH` : **aucun brouillon
n'est jamais persisté**.

**Écarté :** `POST /grilles` créant en `BROUILLON`, plus un endpoint de
soumission.

### Pourquoi

- Le contrat d'API ne prévoit qu'un `POST /grilles`, décrit comme « Crée une
  grille **et la soumet** à la DRH ». L'option en deux temps ajouterait un 27ᵉ
  endpoint hors contrat.
- Elle obligerait à **rejouer** le contrôle d'unicité à la soumission : deux
  brouillons sur le même couple pourraient sinon être soumis l'un après l'autre,
  et l'ambiguïté de la décision n°2 reviendrait par la porte de service.

### Conséquences

- L'ARH ne peut pas préparer une grille et la garder pour lui. **Chaque appel est
  un engagement.**
- La transition `BROUILLON → BROUILLON` de la machine à états du Sprint 2.1
  (« ajustement avant soumission ») n'est plus atteignable par aucun chemin
  applicatif. Elle est **conservée** : elle décrit un instant réel du cycle de
  vie, le statut `BROUILLON` reste dans l'énumération et dans la contrainte
  `CHECK`, et la retirer coûterait plus qu'elle ne rapporte le jour où le métier
  demanderait le brouillon.
- Si le métier réclame un jour le brouillon, la reprise est locale : un endpoint
  de soumission, et le contrôle d'unicité rejoué à cet endroit.
