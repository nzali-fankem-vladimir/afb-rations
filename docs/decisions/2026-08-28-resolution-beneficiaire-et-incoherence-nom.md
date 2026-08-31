# Résolution du bénéficiaire : critère d'identité et écart nom↔compte

**Date :** 28 août 2026
**Sprint :** 3.1, domaine de la saisie — étape 4
**Statut :** tranchée (avec l'utilisateur). **Un point reste ouvert pour le Sprint 4.**

## Contexte

Il n'y a **aucun enrôlement** dans ce processus : pas de référentiel de
bénéficiaires en amont. Le bénéficiaire est créé au moment de la première
saisie qui le concerne. `ResolutionBeneficiaireService.resoudre(nom, prenom,
numCompteCourant, codeAgence)` est le seul point d'entrée : il retrouve un
bénéficiaire existant ou en crée un.

Deux questions que le guide interdit de trancher seul.

## Décision 1 — Identité = numéro de compte courant **seul**

On reconnaît un bénéficiaire déjà saisi par son **seul numéro de compte
courant**, pas par la combinaison `nom + prenom + compte`.

### Motif

Le nom et le prénom se tapent **à la main à chaque saisie**, sans liste
déroulante ni auto-complétion (pas d'enrôlement). Les inclure dans la clé
d'identité ferait de toute faute de frappe un **doublon de bénéficiaire** :
« NGUEMA Jean » et « NGUEMA Jaen » sur le même compte deviendraient deux
personnes. RG-04 (unicité journalière `bénéficiaire × jour × nature × session`)
et RG-15 (unicité inter-états) reposent sur l'identifiant du bénéficiaire :
un doublon les contourne en silence.

Le numéro de compte courant, lui, est recopié d'un document — stable, et déjà
la donnée qui compte pour la mise en paiement.

### Conséquence technique

`BeneficiaireRepository.findByNomAndPrenomAndNumCompteCourant(...)`, prévu par
le texte de l'étape 3 du guide **avant** que la question soit tranchée, est
remplacé par `findByNumCompteCourant(...)` → `Optional<Beneficiaire>`. Le
décompte « six recherches » du guide est inchangé.

### Piste de durcissement (non faite ici)

Aucune contrainte d'unicité sur `beneficiaires.num_compte_courant` en base
(migration V1, Sprint 0.5). L'invariant « un compte = un bénéficiaire » n'est
donc garanti que par le code (passage obligé par `resoudre`). Un index unique
`UNIQUE (num_compte_courant)` le rendrait structurel. Non ajouté au Sprint 3.1 :
hors périmètre (aucune migration prévue au guide au-delà de `id_grille`), et
`service-saisie` est vierge — aucun risque de données existantes en conflit.
À reconsidérer au Sprint 3.2, quand la saisie écrira réellement des lignes.

## Décision 2 — Compte connu, nom enregistré différent → conserver + tracer

Quand `findByNumCompteCourant` retourne un bénéficiaire dont le nom ou le prénom
**diffère** de ce qui vient d'être saisi (option **B** des quatre présentées) :

1. la ligne est rattachée au **bénéficiaire existant**, qui n'est **jamais
   modifié** (le compte fait foi) ;
2. la saisie **n'est pas bloquée** ;
3. une **double trace** est émise :
   - un log `WARN` au préfixe repérable **`INCOHERENCE BENEFICIAIRE`** (même
     convention que `INCOHERENCE GRILLE` et `AUDIT PERDU`) ;
   - un événement d'audit **`INCOHERENCE_BENEFICIAIRE`** sur
     `rations.audit.evenement` (entité `beneficiaires`, id du bénéficiaire
     existant), avec le delta `nom` / `prenom` enregistré → saisi.

### Comparaison normalisée avant de déclencher la trace

**Condition posée par l'utilisateur.** L'écart est mesuré après normalisation :
`trim` + espaces multiples réduits + casse neutralisée + accents retirés
(`Normalizer.Form.NFD`). Sans cela, `"  NGUEMA"` vs `"Nguéma"` produirait une
fausse incohérence à chaque reprise de saisie, et l'audit
`INCOHERENCE_BENEFICIAIRE` serait noyé de bruit — donc inexploitable.

### Options écartées

| Option | Pourquoi écartée |
|---|---|
| A — conserver l'existant, silencieux | Une erreur de compte (mauvais numéro → mauvaise personne payée) resterait invisible ; une correction de nom légitime serait perdue sans trace. |
| C — refuser la ligne | Bloque aussi les fautes de frappe bénignes, sans aucun moyen de « confirmer quand même » dans le module ce sprint. |
| D — mettre à jour le nom enregistré | Un doigt sur le mauvais numéro de compte réécrirait le nom d'une vraie personne, sans trace de l'ancien. Le plus risqué. |

## Point ouvert — Sprint 4

**La trace est un filet a posteriori, pas un contrôle préventif.** Rien, au
Sprint 3.1, n'empêche une ligne portant une incohérence nom↔compte d'être
enregistrée, consolidée, validée puis transmise à la comptabilité. L'événement
`INCOHERENCE_BENEFICIAIRE` n'a d'utilité que si **quelqu'un le regarde avant que
l'argent parte**.

Besoin à traiter au Sprint 4 (workflow et validation) : le Chef d'Unité (DA) et
le Directeur Réseau (DR) qui valident un état doivent **voir les lignes marquées
`INCOHERENCE_BENEFICIAIRE`** — a minima un indicateur sur l'écran de validation,
idéalement le détail de l'écart — **avant de clôturer**. Sinon l'audit ne sert
jamais au bon moment.

À rapprocher de la décision
`docs/decisions/2026-08-28-identifiants-plats-dans-service-saisie.md` (profil de
`ligne_prestation` comme table de contrôle) et à reporter dans CLAUDE.md §17 à
la clôture du Sprint 3.
