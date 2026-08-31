# Écriture sur un état non modifiable : `422`, et non `409`

**Sprint 3.3 — 31 août 2026** · Décision tranchée avec l'utilisateur, étape 3.

**Statut :** appliquée. **Écart assumé avec le contrat d'API §1.3, à corriger dans
le document de référence.**

---

## 1. La situation

Un agent tente d'ajouter, de modifier ou de supprimer une ligne de prestation
dans une fiche dont le processus mensuel n'est plus ouvert : il a été soumis au
Chef d'Unité, il attend une validation, ou il est clôturé.

Le service Saisie doit refuser. Reste à choisir le code HTTP.

---

## 2. Ce que dit le contrat d'API, et pourquoi on s'en écarte

Le contrat d'API §1.3 range sous `409` : « Conflit (doublon, contrainte
d'unicité, **transition non permise**) ». La lecture littérale donnerait donc
`409`.

**Le Sprint 2.3 a déjà écarté cette lecture**, pour exactement la même famille de
refus. `TRANSITION_INTERDITE` — valider une grille qui n'est pas
`EN_ATTENTE_DRH` — y a été rendu en `422`, avec ce motif consigné dans
CLAUDE.md §17 :

> `TRANSITION_INTERDITE` (422, **et non 409 : rien n'est dupliqué, c'est une
> règle de gestion qui refuse**)

Le raisonnement s'applique ici sans changement. Écrire dans un état soumis ne
duplique rien, ne viole aucune contrainte d'unicité, ne concurrence aucune autre
écriture. C'est une règle de gestion qui dit non.

**Retenir `409` ici ferait répondre différemment deux services du même module à
la même nature de refus.** Un client — le frontend au Sprint 7F, un intégrateur
plus tard — apprendrait qu'un refus de transition est un `409` chez Grilles et
un `422` chez Saisie, sans qu'aucune différence de fond ne le justifie.

---

## 3. La décision

| | |
|---|---|
| **Statut HTTP** | `422 Unprocessable Entity` |
| **Code métier** | `ETAT_NON_MODIFIABLE` |
| **Exception** | `EtatNonModifiableException` |
| **Endpoints concernés** | `POST /saisie/fiches`, `POST /saisie/lignes`, `PUT /saisie/lignes/{id}`, `DELETE /saisie/lignes/{id}` |

```json
{
  "timestamp": "2026-08-31T14:22:00",
  "status": 422,
  "code": "ETAT_NON_MODIFIABLE",
  "message": "L'etat de la periode 08/2026 pour l'unite 00002 est SOUMIS : il n'est plus modifiable. Demandez son retour au chef d'unite pour reprendre la saisie.",
  "path": "/saisie/lignes"
}
```

Le message nomme **la période, l'unité, le statut, et l'action attendue**. Un
« état non modifiable » sec laisserait l'agent sans recours : la voie de sortie
est le retour par le Chef d'Unité (RG-11), et c'est cela qu'il doit lire.

---

## 4. L'écart doit être corrigé dans le contrat, pas laissé en silence

**C'est le point central de cette décision.** Deux services rendent désormais
`422` là où le contrat d'API §1.3 annonce `409`. Tant que le document de
référence n'est pas corrigé, il contredit le code sans que personne ne le sache —
et le prochain service du circuit (Workflow, Sprint 4, qui refusera une
validation sur un statut incompatible) devra deviner lequel des deux suivre.

**Correction à porter au contrat d'API**, section 1.3 :

> `409` — Conflit : doublon, contrainte d'unicité.
> `422` — Règle de gestion non respectée, **y compris une transition ou une
> écriture refusée en raison du statut de la ressource**.

Cette correction est **la seule action ouverte** de cette décision. Elle ne peut
pas être portée depuis le code : le contrat d'API est un document de référence du
programme, hors du dépôt d'implémentation.

| Réf | Objet | Interlocuteur | État |
|---|---|---|---|
| **C-01** | Corriger le contrat d'API §1.3 : la transition refusée relève du `422`, non du `409` | DSI / rédacteur du contrat | **Ouvert** |

---

## 5. Codes de refus du service Saisie, vue d'ensemble

Six refus, six codes. Les quatre premiers viennent du Sprint 3.2
(`docs/decisions/2026-08-31-refus-de-ligne-et-codes-erreur-saisie.md`), les deux
derniers de ce sprint.

| Situation | Statut | Code | Ce que l'agent doit faire |
|---|---|---|---|
| Fiche inexistante | `404` | `FICHE_INTROUVABLE` | corriger la référence |
| Ligne inexistante | `404` | `LIGNE_INTROUVABLE` | corriger la référence |
| Processus inexistant | `404` | `PROCESSUS_INTROUVABLE` | ouvrir l'état du mois |
| RG-04 : doublon | `409` | `DOUBLON_LIGNE` | corriger sa saisie |
| RG-03 : aucune grille | `422` | `GRILLE_INDISPONIBLE` | attendre une grille validée |
| **État verrouillé** | **`422`** | **`ETAT_NON_MODIFIABLE`** | **demander le retour au chef d'unité** |
| Hors portée d'accès | `403` | `UTILISATEUR_NON_HABILITE` | s'adresser à l'administrateur |
| Grilles muet | `503` | `SERVICE_GRILLES_INDISPONIBLE` | réessayer plus tard |
| Identité muet | `503` | `SERVICE_IDENTITE_INDISPONIBLE` | réessayer plus tard |
| Workflow muet | `503` | `SERVICE_WORKFLOW_INDISPONIBLE` | réessayer plus tard |

Trois `503` distincts plutôt qu'un seul : ils ne se réparent pas au même endroit.
Un exploitant qui lit `SERVICE_WORKFLOW_INDISPONIBLE` sait quel service relever.

---

## 6. Ce que cette décision engage pour la suite

- **Sprint 4 (Workflow)** : le refus de valider un processus au mauvais statut
  doit rendre `422`, pas `409`. Trois services alignés valent mieux que deux.
- **Sprint 7F (frontend)** : le `422 ETAT_NON_MODIFIABLE` doit être présenté
  comme un état de l'écran — la saisie devient consultation — et non comme une
  erreur technique.
