# Résolution du montant applicable — convention d'appel pour le service Saisie

Module Paiement des Rations et du Transport de la Garde Armée — décision Sprint 2.4

Cette note décrit comment le service Saisie interroge le service Grilles pour
connaître le montant applicable à une ligne de prestation (RG-03). Elle ne
s'implémente pas encore : le service Saisie n'a pas de code métier avant le
Sprint 3.1. Elle fixe le contrat qu'il suivra.

---

## 1. L'endpoint

```
GET /grilles/active?nature=<RATION|TRANSPORT>&session=<JOUR|SOIR>&date=<AAAA-MM-JJ>
```

- Fait partie des **cinq endpoints du contrat d'API** du service Grilles
  (CLAUDE.md section 11), à la différence de `GET /identite/habilitation` qui
  est hors contrat passerelle. Il est décrit comme « usage interne » (service
  Saisie), mais son décompte compte dans les cinq.
- Les trois paramètres sont **obligatoires** — en particulier `date`, qui
  **n'a pas de valeur par défaut**. Le service ne suppose jamais « aujourd'hui »
  : la date à transmettre est celle de la **prestation** (le jour de la fiche
  journalière en cours de saisie), jamais la date de l'appel. Une saisie
  rétroactive appelle donc avec une date passée, et la résolution renvoie le
  tarif qui était en vigueur ce jour-là — pas le tarif courant.
- Absent ou mal formé → `400 REQUETE_INVALIDE`.

### Réponse `200`

```json
{
  "disponible": true,
  "nature": "RATION",
  "session": "JOUR",
  "date": "2026-07-10",
  "montantFcfa": 1500,
  "idGrille": 12,
  "dateDebut": "2026-07-01",
  "dateFin": "2026-07-31"
}
```

| Champ | Sens |
|---|---|
| `disponible` | `true` si une grille couvre la date demandée |
| `nature`, `session`, `date` | la question posée, rappelée telle quelle |
| `montantFcfa` | montant en FCFA, **`null`** — jamais `0` — quand `disponible` vaut `false` |
| `idGrille` | grille d'où vient le montant, `null` si indisponible |
| `dateDebut`, `dateFin` | période de validité de la grille retenue, `dateFin` nulle si elle est encore courante |

**L'indisponibilité est une réponse `200`, pas une erreur** (`disponible: false`,
`montantFcfa: null`) : même parti que `autorise: false` sur
`GET /identite/habilitation` (Sprint 1.3). Un endpoint interne qui répond à une
question métier ne code pas la réponse négative comme une erreur de transport.
Ne testez donc **jamais** le code HTTP pour détecter l'absence de tarif :
testez le champ `disponible`.

---

## 2. Comportement attendu côté appelant

### Le service Grilles répond, sans tarif applicable

```
disponible = false
```

C'est US-05 et CT-10 : la ligne est **refusée**, avec le code du contrat d'API
`422 GRILLE_INDISPONIBLE`, et un message explicite pour l'agent — quelque
chose comme « Aucune grille n'est en vigueur pour {nature} / {session} au
{date} ». L'agent ne peut pas continuer sans qu'une grille ait été proposée et
validée par le circuit ARH → DRH.

**Jamais de repli sur `0` ni sur un montant arbitraire.** Un montant à `0`
serait enregistré comme un tarif et pourrait partir en comptabilité sans que
personne ne s'en aperçoive.

### Le service Grilles répond avec un tarif

```
disponible = true
```

Le montant reçu est celui figé dans `ligne_prestation.montant_applique`. Il
n'est jamais recalculé ni ajusté côté Saisie.

### Le service Grilles est injoignable — refus conservateur (fail-closed)

**Décision Sprint 2.4 : timeout, `5xx`, connexion refusée, réponse illisible →
la ligne est refusée.** Aucune ligne n'est enregistrée sans montant connu, en
attente d'une valorisation ultérieure.

Cette décision prolonge la doctrine déjà posée pour le service Identité
(CLAUDE.md section 9.2, décision Sprint 1.3, `docs/appel-habilitation.md`
section 3) plutôt que d'en introduire une nouvelle : un service dont dépend
une donnée réglementaire qui ne répond pas se traite comme s'il avait répondu
négativement.

**Pourquoi pas l'option écartée — accepter la ligne sans montant, à valoriser
plus tard.** Elle a été envisagée et refusée pour trois raisons :

1. `ligne_prestation.montant_applique` (CLAUDE.md section 4) n'admet pas de
   valeur absente dans le dictionnaire actuel. L'accepter aurait exigé soit
   d'autoriser `NULL`, soit d'inventer un état intermédiaire (« ligne en
   attente de tarif ») — une extension de schéma et de workflow non prévue par
   ce sous-sprint, pour un besoin non confirmé par le métier.
2. Une ligne à montant absent est exactement le risque que la section 10 du
   guide d'implémentation qualifie de faute grave côté résolution : rien ne
   garantit qu'elle ne soit pas transmise à la comptabilité avant d'avoir été
   revalorisée.
3. Le coût du refus conservateur est borné et déjà accepté ailleurs dans le
   module (Identité) : une panne temporaire bloque la saisie du jour, elle ne
   corrompt aucune donnée. La réponse à ce coût est de fiabiliser le service
   Grilles, pas d'assouplir la règle.

### Distinguer les trois cas dans le code appelant

| Ce que Grilles répond | Ce que Saisie fait | Code / message |
|---|---|---|
| `200`, `disponible: true` | Ligne tarifée au montant reçu | — |
| `200`, `disponible: false` | Ligne refusée, refus métier | `422 GRILLE_INDISPONIBLE`, message nommant la date et le couple |
| Pas de réponse exploitable (timeout, `5xx`, connexion refusée) | Ligne refusée, refus technique | Message distinct : « service des grilles indisponible, réessayez » |

Les deux derniers cas ne doivent **jamais** produire le même message à
l'agent : le premier lui dit qu'il n'y a pas de tarif à cette date (rien à
faire côté Saisie, il faut qu'une grille soit proposée) ; le second lui dit
que le système est en panne (rien à faire côté agent non plus, mais l'action
attendue est différente — réessayer plus tard, pas contacter l'ARH).

---

## 3. Ce que le service Saisie ne doit pas faire

- Ne jamais utiliser `LocalDate.now()` comme valeur de `date` par défaut,
  même pour « simplifier » l'appel courant : ce défaut ne se voit pas en test
  tant que toutes les saisies portent sur la journée courante, et produit un
  montant faux dès qu'une saisie porte sur un jour passé.
- Ne jamais mettre en cache une réponse positive : une grille peut être
  remplacée entre deux appels (validation DRH), et le montant appliqué doit
  toujours refléter l'état courant des grilles à la date interrogée.
- Ne jamais traiter un `500 INCOHERENCE_GRILLE` comme une indisponibilité
  ordinaire à retenter silencieusement : c'est un incident de données côté
  Grilles (deux grilles actives se chevauchent), pas une panne réseau. Le
  signaler comme tel.

---

## 4. Résumé pour le service Saisie

1. Résoudre le montant **avant** de figer une ligne, jamais après : RG-03
   interdit un montant saisi à la main.
2. Appeler `GET /grilles/active` avec `nature`, `session`, et `date` = date de
   la **prestation** (jour de la fiche), jamais la date du jour.
3. `200` avec `disponible = true` → figer `montantFcfa` dans la ligne.
4. `200` avec `disponible = false` → refuser la ligne, `422 GRILLE_INDISPONIBLE`.
5. Toute autre réponse (timeout, `5xx`, connexion refusée) → refuser la ligne,
   refus technique distinct du refus métier.
6. Ne jamais enregistrer de ligne à montant `null` ou `0`.
7. Ne jamais mémoriser une réponse pour un appel ultérieur.
