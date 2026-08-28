# Résolution du montant applicable : date de prestation, indisponibilité et traçabilité

**Date :** 2026-08-27
**Sprint :** 2.4 — Résolution du montant et clôture du Sprint 2
**Règles concernées :** RG-03, US-05, CT-10, CT-29
**Portée :** service-grilles ; **conséquences directes sur les Sprints 3.1, 3.2 et 6bis**

---

## 1. La question posée

RG-03 dit que le montant d'une ligne est repris de la grille active, jamais
saisi. Reste à définir *ce que le service Grilles répond* quand le service
Saisie le lui demande. Quatre situations devaient être arbitrées avant tout
code : une grille couvre la date demandée ; aucune ne la couvre ; la date est
antérieure à toute grille connue ; deux grilles successives se touchent
exactement à cette date.

---

## 2. Décision 1 — La résolution se fait à la date de la prestation

**Retenu :** la grille retenue est celle en vigueur **à la date de la
prestation**, jamais celle en vigueur à la date de l'appel.

### Pourquoi

Une saisie du 10 juillet effectuée le 27 août doit être tarifée au montant de
juillet. Résoudre à la date du jour produirait un montant faux sans qu'aucune
erreur ne se déclenche : la ligne serait enregistrée, validée et transmise à la
comptabilité avec un tarif qui n'était pas celui en vigueur ce jour-là.

Le piège est qu'il ne se voit pas en test si toutes les saisies portent sur la
journée courante — les deux dates coïncident alors, et le défaut reste
invisible. **Le test sur date passée n'est donc pas optionnel** : c'est le seul
qui distingue les deux implémentations.

L'enjeu grandit au Sprint 6bis : un état complémentaire régularise par
construction une période close, donc toujours une date passée.

### Critère retenu

Grille au statut `ACTIVE`, `date_debut <= date`, et `date_fin` nulle ou
`>= date`. Les deux bornes sont **inclusives**. Une grille `EN_ATTENTE_DRH` ou
`REJETEE` n'est jamais retenue (CT-25) : une proposition non tranchée est sans
effet sur les saisies.

---

## 3. Décision 2 — L'indisponibilité est une réponse `200`, pas une erreur

**Retenu :** `200 OK` avec `disponible: false` et `montantFcfa: null`.

### Pourquoi

Ce choix n'est pas isolé : il **reprend le précédent déjà posé sur
`GET /identite/habilitation`** (Sprint 1.3), qui répond `200` avec
`autorise: false` plutôt qu'un `403`. Même raisonnement dans les deux cas : un
endpoint interne qui répond à une question métier ne doit pas coder la réponse
négative comme une erreur de transport. « Aucune grille ne couvre cette date »
est une réponse, pas un échec.

La conséquence pratique est côté appelant. Le service Saisie doit distinguer
**trois** situations, qui appellent trois comportements différents :

| Ce que Grilles répond | Ce que Saisie en fait | Message à l'agent |
|---|---|---|
| `200`, `disponible: true` | La ligne est tarifée au montant reçu | — |
| `200`, `disponible: false` | Refus métier `422 GRILLE_INDISPONIBLE` (code du contrat) | « Aucune grille n'est en vigueur pour cette date » |
| Pas de réponse exploitable | Refus technique | « Le service des grilles est indisponible, réessayez » |

Coder l'indisponibilité en `404` ferait fusionner les lignes 2 et 3 : un `404`
de transport (mauvaise URL, service arrêté derrière la passerelle) est
indiscernable d'un `404` métier. L'agent recevrait le mauvais message, et le
diagnostic serait perdu.

### `null`, jamais `0`

Le montant absent est `null`. **Retourner `0` serait une faute grave** : une
ligne serait enregistrée à montant nul sans que personne ne s'en aperçoive, et
partirait ainsi en comptabilité. Avec `null`, un appelant qui ignorerait le
drapeau `disponible` échoue bruyamment plutôt que silencieusement.

---

## 4. Décision 3 — Les bornes exactes, et l'incohérence de données

### Aux bornes : une grille et une seule

La bascule du Sprint 2.3 ferme l'ancienne grille **à la veille** de la
`date_debut` de la remplaçante. Le temps est donc partitionné exactement :

- à `date = date_debut` de la nouvelle → **la nouvelle** (l'ancienne porte
  `date_fin = date - 1`) ;
- à `date = date_fin` de l'ancienne → **l'ancienne** (la nouvelle commence le
  lendemain).

La bascule prend effet le jour même de la `date_debut`, pas le lendemain. Un
`<` au lieu d'un `<=` décalerait la bascule d'une journée, et cette journée-là
produirait un montant faux — d'où des tests portant sur les bornes exactes, et
non sur des dates « au milieu » d'une période.

### Si deux grilles se chevauchaient malgré tout

L'invariant est tenu à l'écriture (index partiel `ux_grille_active_par_couple`
et bascule atomique du 2.3). Une reprise de données ou une intervention directe
en base pourraient néanmoins le violer.

**Retenu :** ce cas est **détecté et refusé explicitement**, jamais arbitré.

Choisir arbitrairement l'une des deux grilles servirait un montant
potentiellement faux sans que personne ne le voie — exactement le risque que
tout le reste de cette note cherche à écarter. Mieux vaut un refus visible
qu'un montant plausible et faux.

Mais « laisser remonter l'exception » ne suffit pas : une exception non
interceptée produirait une réponse générique Spring Boot, **hors du format
d'erreur uniforme du projet** (`timestamp, status, code, message, path`). On
casserait le contrat d'erreur pour gagner une visibilité qu'on n'obtiendrait
pas vraiment. Le cas est donc :

- intercepté dans `GestionnaireErreursApi`, au format uniforme, avec le code
  dédié **`INCOHERENCE_GRILLE`** (`500`) ;
- **tracé en log au préfixe repérable `INCOHERENCE GRILLE`**, sur le modèle du
  préfixe `AUDIT PERDU` déjà utilisé par `rations-audit-commun` pour ses cas
  graves. Un incident de données doit être cherchable dans les logs par une
  chaîne stable.

---

## 5. Dette identifiée pour le Sprint 3 — `id_grille` sur `ligne_prestation`

**À traiter au Sprint 3.1. Relevé ici pour ne pas être redécouvert tard.**

L'endpoint de résolution renvoie, avec le montant, **l'identité de la grille qui
l'a fourni** (`idGrille`, `dateDebut`, `dateFin`) : c'est ce qui permet de
justifier a posteriori le montant figé dans une ligne.

Or `ligne_prestation` (CLAUDE.md §4) ne porte aujourd'hui que
`montant_applique`. **Cette traçabilité n'a donc nulle part où atterrir.** Le
montant est figé à la saisie, mais rien ne dit *de quelle grille* il provient ;
retrouver l'origine d'un montant contesté supposerait de rejouer la résolution
à la date de la ligne, ce qui suppose que les grilles n'aient jamais été
corrigées entre-temps — hypothèse qu'on ne peut pas garantir.

**Action attendue au Sprint 3.1 :** ajouter une colonne `id_grille` à
`ligne_prestation`, par **migration additive** du service Saisie, sur le modèle
de la migration `V4` du Sprint 2.2 (`libelle_createur` / `libelle_validateur`),
elle aussi au-delà du dictionnaire d'origine.

C'est un identifiant simple, **sans clé étrangère** : `grille_tarifaire` vit
dans la base `rations_grilles`, `ligne_prestation` dans `rations_saisie`. Même
convention que `id_createur` et `id_validateur`, qui référencent `utilisateurs`
d'une autre base.

Sans cette colonne, l'endpoint de résolution reste correct et utilisable — le
montant est juste —, mais la partie « et voici d'où il vient » de sa réponse est
simplement jetée par l'appelant.

---

## 6. Réversibilité

| Décision | Coût d'un revirement |
|---|---|
| Résolution à la date de prestation | Élevé — c'est RG-03 même ; ne pas revenir dessus |
| Indisponibilité en `200` | Faible tant que le service Saisie est seul appelant (Sprint 3) ; croissant ensuite |
| Refus explicite sur incohérence | Faible, cas qui ne doit pas se produire |
| `id_grille` sur `ligne_prestation` | Faible **si traité au 3.1** ; élevé après, les lignes déjà saisies n'ayant plus d'origine récupérable |
