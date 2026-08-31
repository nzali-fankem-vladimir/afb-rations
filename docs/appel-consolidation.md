# Convention d'appel — consolidation mensuelle

**Appelant :** service Workflow (port 8084)
**Appelé :** service Saisie (port 8082)
**Sprint :** 3.4 — RG-06, US-06, CT-11

Cette note est le contrat que le service Workflow doit respecter au Sprint 4. Elle
tient le même rôle que `appel-habilitation.md` pour `GET /identite/habilitation`
et `appel-resolution-montant.md` pour `GET /grilles/active`.

---

## 1. Ce que RG-06 partage entre les deux services

RG-06 dit que les fiches journalières sont consolidées automatiquement en un état
mensuel par unité. La règle ne peut pas vivre dans un seul service :

| | Détient | Fait |
|---|---|---|
| **Saisie** | `fiche_journaliere`, `ligne_prestation` | Produit l'état consolidé : journées, lignes détaillées, sous-totaux, **total du mois** |
| **Workflow** | `processus_mensuel`, `etape_workflow` | Consomme cet état, **porte le total** sur `processus_mensuel.montant_total`, applique l'aiguillage au seuil (RG-08) |

Aucun des deux ne fait le travail de l'autre. En particulier, **Saisie ne calcule
pas l'aiguillage et ne connaît pas le seuil** ; Workflow ne réadditionne pas les
lignes.

---

## 2. L'appel

```
GET http://service-saisie:8082/saisie/processus/{id}/etat?codeUnite={code}
Authorization: Bearer <jeton de l'utilisateur final>
```

**Endpoint interne.** Il n'est pas routé par la passerelle, ne figure pas au
contrat d'API public et ne change pas le compte des 26 endpoints du module
(CLAUDE.md §11). C'est l'endpoint public `GET /processus/{id}/etat`, servi par
Workflow, qui s'appuie dessus. Voir
`docs/decisions/2026-08-31-endpoint-interne-de-consolidation.md`.

### 2.1 Le jeton

Le service Workflow **relaie tel quel** l'en-tête `Authorization` de
l'utilisateur final. Il ne s'authentifie pas avec un compte de service : le realm
`afb-rations-dev` n'a qu'un client public, sans compte de service (doctrine
Sprint 1.3). Le rôle et la portée d'accès vérifiés sont donc ceux de la personne
réellement à l'origine de la demande.

### 2.2 Le paramètre `codeUnite` — obligatoire

**Workflow doit transmettre le code unité du processus.** Il le détient : c'est
`processus_mensuel.code_unite`. Le paramètre est obligatoire, sans valeur par
défaut ; absent, la réponse est `400 REQUETE_INVALIDE`.

Deux raisons, dans cet ordre :

1. **La portée d'accès reste vérifiable sur un état vide.** Un processus dont
   aucune journée n'a été saisie n'a aucune fiche, donc aucun code unité à lire.
   Sans ce paramètre, le contrôle disparaîtrait au moment précis où il n'y a rien
   à lire — et un chef d'unité se verrait traiter différemment selon que son
   dossier contient ou non des lignes.
2. **Aucun aller-retour circulaire.** Si Saisie redemandait l'unité à Workflow, on
   obtiendrait `Workflow → Saisie → Workflow` sur le chemin le plus emprunté du
   Sprint 4 : latence doublée et dépendance mutuelle à l'exécution entre deux
   services.

La valeur déclarée est **recoupée** contre le `code_unite` figé sur les fiches,
qui fait autorité. Désaccord ⇒ `403 UNITE_NON_CONCORDANTE`, tracé en audit.

### 2.3 Rôles acceptés

`AGENT_UNITE`, `CHEF_UNITE_DA`, `DIRECTEUR_RESEAU_DR` — les rôles du circuit. Le
chef d'unité et le directeur réseau doivent pouvoir lire l'état qu'ils sont en
train de valider. Tout autre rôle : `403 ACCES_REFUSE`.

Le rôle n'est que le premier filtre : la portée d'accès est vérifiée en plus,
unité par unité, auprès du service Identité.

---

## 3. La réponse

`200 OK`, `application/json`.

```json
{
  "idProcessus": 740,
  "codeUnite": "00002",
  "moisPaiement": 8,
  "anneePaiement": 2026,
  "nombreJournees": 2,
  "nombreLignes": 3,
  "nombreBeneficiaires": 1,
  "montantTotalFcfa": 9000,
  "journees": [
    {
      "idFicheJournaliere": 11,
      "dateJour": "2026-08-10",
      "statut": "EN_SAISIE",
      "nombreLignes": 2,
      "sousTotalFcfa": 6500,
      "lignes": [
        {
          "id": 101,
          "idFicheJournaliere": 11,
          "idBeneficiaire": 55,
          "beneficiaire": {
            "id": 55,
            "nom": "MBALLA",
            "prenom": "Paul",
            "numCompteCourant": "03702009991111",
            "codeAgence": "00002"
          },
          "nature": "RATION",
          "session": "JOUR",
          "montantApplique": 4000,
          "idGrille": 12,
          "dateCreation": "2026-08-10T09:00:00"
        }
      ]
    }
  ]
}
```

### 3.1 Ce que Workflow doit reprendre

| Champ | Usage au Sprint 4 |
|---|---|
| `montantTotalFcfa` | **À reporter tel quel** dans `processus_mensuel.montant_total`, puis comparer au seuil de `parametre_systeme` (RG-08) |
| `journees[].lignes[]` | Charge du topic `rations.etat.valide` à la clôture (contrat §7.1) : nom, prénom, compte courant, **code agence**, nature, session, montant |
| `nombreJournees`, `nombreLignes` | Contrôle de vraisemblance, refus d'un état vide à la soumission |

### 3.2 Ce que la réponse ne porte pas, volontairement

Ni le statut du processus, ni son type (`NORMAL` / `COMPLEMENTAIRE`), ni le
`montant_total` déjà enregistré. Ces données appartiennent à Workflow ; les
recopier ici recréerait dans Saisie une projection de son domaine
(`rattachement-processus.md` §3).

### 3.3 Garanties sur les montants

- **Entiers en FCFA.** `montantTotalFcfa` et `sousTotalFcfa` sont des entiers
  (`long`), `montantApplique` un `int`. Aucun flottant, aucun `BigDecimal`, aucune
  décimale dans le JSON.
- **Total = somme des sous-totaux = somme des lignes affichées.** Il n'existe pas
  de second chemin de calcul, donc pas de divergence possible entre le détail et
  le total.
- **Montants figés, jamais recalculés.** L'agrégation porte sur
  `ligne_prestation.montant_applique`, figé à la saisie (RG-03). Le service
  Grilles n'est pas appelé. Une ligne saisie en juillet garde le montant de la
  grille de juillet, même si le tarif a changé depuis.
- **Tri déterministe.** Journées par date croissante, lignes par identifiant
  croissant (ordre de saisie).

---

## 4. Processus sans aucune fiche

**`200 OK`**, jamais `404`.

```json
{
  "idProcessus": 740,
  "codeUnite": "00002",
  "moisPaiement": null,
  "anneePaiement": null,
  "nombreJournees": 0,
  "nombreLignes": 0,
  "nombreBeneficiaires": 0,
  "montantTotalFcfa": 0,
  "journees": []
}
```

Un processus qui vient d'être ouvert et où l'agent n'a encore rien saisi est un
état **normal**, pas une erreur. Même parti qu'au Sprint 2.4 pour
`GET /grilles/active` (`200` avec `disponible: false`) : un endpoint interne qui
répond à une question métier ne déguise pas une réponse négative légitime en
erreur de transport.

Répondre `404` serait de surcroît faux : Saisie ne sait pas si le processus
existe — `processus_mensuel` vit dans une autre base. Elle sait seulement qu'elle
ne détient rien pour cet identifiant. Un identifiant inexistant et un processus
réellement vide produisent donc la **même réponse**, ce qui ne divulgue rien.

`moisPaiement` et `anneePaiement` sont nuls : ils sont lus sur les fiches.
`codeUnite` reste renseigné — c'est l'écho de la question posée.

**Conséquence pour Workflow :** c'est à lui de refuser la soumission d'un état
vide, à partir de `nombreLignes == 0`. C'est sa règle, pas celle de Saisie.

---

## 5. Erreurs

| Situation | Statut | `code` |
|---|---|---|
| Aucun jeton, ou jeton invalide | `401` | — |
| Rôle hors du circuit | `403` | `ACCES_REFUSE` |
| Hors de la portée d'accès sur l'unité déclarée | `403` | `UTILISATEUR_NON_HABILITE` |
| Unité déclarée ≠ unité figée sur les fiches | `403` | `UNITE_NON_CONCORDANTE` |
| `codeUnite` absent | `400` | `REQUETE_INVALIDE` |
| Service Identité injoignable | `503` | `SERVICE_IDENTITE_INDISPONIBLE` |

Format uniforme `{ timestamp, status, code, message, path }` (CLAUDE.md §11).

Les trois refus `403` et le `503` sont **publiés en audit** (`ACCES_REFUSE`,
motifs `ROLE_INSUFFISANT`, `HABILITATION_ABSENTE`, `UNITE_NON_CONCORDANTE`,
`IDENTITE_INDISPONIBLE`) depuis `GestionnaireErreursApi`, conformément à CT-04 et
à la doctrine du Sprint 1.3.

---

## 6. Si le service Saisie est injoignable

**Refus conservateur (fail-closed)**, comme pour Identité (Sprint 1.3) et pour
Grilles (Sprint 2.4). Timeout, `5xx`, connexion refusée ⇒ **Workflow refuse
l'opération en cours**.

Concrètement, au Sprint 4 :

| Opération Workflow | Comportement attendu si Saisie est muet |
|---|---|
| Soumission d'un état par l'agent | **Refusée.** Sans consolidation, il n'y a pas de montant total, donc pas de `montant_total` à enregistrer. |
| Validation DA ou DR | **Refusée.** L'aiguillage RG-08 se décide sur le montant ; valider sans lui reviendrait à choisir un niveau de validation au hasard. |
| Consultation `GET /processus/{id}/etat` | **`503`**, avec un message nommant le service en panne. |

**Ne jamais** enregistrer un `montant_total` partiel, à zéro ou repris d'une
lecture antérieure : un montant faux autour du seuil de 100 000 XAF envoie le
dossier au mauvais niveau de validation, et rien ne le signale.

**Aucun cache.** Le total change à chaque ligne saisie. Servir une valeur
mémorisée reviendrait à valider un état qui n'est plus celui qu'on a lu.

Le code d'erreur à retenir côté Workflow, par symétrie avec l'existant :
`503 SERVICE_SAISIE_INDISPONIBLE`.

---

## 7. Quand appeler

| Moment | Pourquoi |
|---|---|
| **Soumission** par l'agent | Calculer et figer `montant_total` sur le processus |
| **Validation DA**, puis **DR** | Afficher l'état à valider ; recouper le montant avant l'aiguillage |
| **Clôture** | Constituer la charge du topic `rations.etat.valide` |

Une fois l'état soumis, `EtatModifiableService` refuse toute écriture côté Saisie :
le montant lu à la validation porte donc sur un état qui **ne peut plus changer**.
C'est ce qui rend l'aiguillage RG-08 stable entre le moment où le valideur voit le
montant et celui où il décide.
