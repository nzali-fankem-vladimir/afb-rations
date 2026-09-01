# Séparation des tâches (RG-12) : la lecture retenue et sa portée temporelle

**Sprint 4.4** — service Workflow — décision prise avec l'utilisateur avant tout codage.

---

## 1. La question

RG-12 dit qu'« un même utilisateur ne cumule pas la saisie et la validation
multi-niveau d'un même dossier, et ne choisit pas son N+1 ». Trois lectures étaient
possibles :

1. celui qui a soumis ne peut pas valider, à aucun niveau ;
2. celui qui a validé à un niveau ne peut pas valider au niveau suivant ;
3. les deux à la fois.

## 2. Décision : lecture 3, les deux cumuls sont interdits

**Une même personne n'agit qu'une fois sur la version du dossier qui est dans le
circuit.**

Motifs :

- C'est mot pour mot ce que dit le contrat d'API §5 : `403 SEPARATION_TACHES si le
  validateur a déjà agi sur le dossier`.
- Les lectures 1 et 2 laissent chacune une porte ouverte. Avec la 1, le chef d'unité
  qui a visé au premier niveau pourrait viser au second s'il changeait de rôle ; avec
  la 2, l'agent qui a saisi et soumis pourrait valider son propre dossier. Dans les
  deux cas, une seule personne engagerait la banque de bout en bout — exactement ce
  que le second niveau d'approbation existe pour empêcher.
- La lecture 3 s'écrit en une question : *cet acteur a-t-il déjà une étape sur le
  cycle courant ?* Une seule requête, une seule règle à relire.

## 3. La correction décisive : le contrôle porte sur le CYCLE COURANT

C'est le point que la formulation initiale de la question masquait, et il vaut mieux
que la règle elle-même.

### Le blocage qu'une lecture naïve produirait

Vérifier « cet acteur a-t-il une étape sur ce processus, n'importe laquelle » crée un
**verrou permanent** :

- La portée d'un chef d'unité est limitée à sa propre unité (décision Sprint 1.1).
  Beaucoup d'unités n'ont qu'**un seul** DA.
- Le DA de l'agence de Bafoussam retourne l'état de juillet pour une erreur de saisie
  (RG-10, RG-11). Ce retour laisse une ligne `etape_workflow` à son nom.
- L'agent corrige et resoumet. Le seul DA habilité sur cette unité est celui qui vient
  de retourner : la règle le refuserait **à vie**.
- Aucune échappatoire : la tolérance « si personne d'autre n'existe » a été écartée
  comme techniquement infaisable (voir §5), et le dossier ne pourrait plus jamais être
  validé par la voie normale.

Le contrôle censé protéger l'intégrité du circuit le condamnerait — un risque plus
grave que celui que RG-12 cherche à prévenir.

### La règle retenue

**Un cycle de validation commence à une `SOUMISSION_AGENT` et s'achève par une clôture
ou par un retour.** RG-12 ne regarde que les étapes du cycle courant, c'est-à-dire
celles dont `ordre_etape` est supérieur ou égal à celui de la **dernière**
`SOUMISSION_AGENT`.

```
ordre 1  SOUMISSION_AGENT  VALIDEE     -- cycle 1
ordre 2  VALIDATION_DA     RETOURNEE   -- cycle 1, clos par le retour
ordre 3  SOUMISSION_AGENT  VALIDEE     -- cycle 2, courant
ordre 4  VALIDATION_DA     VALIDEE     -- cycle 2
```

Ce que cela donne, cas par cas :

| Situation | Verdict |
| --- | --- |
| L'agent qui a soumis la version courante veut valider | **Refusé** |
| Le DA qui a validé la version courante veut viser au niveau DR | **Refusé** |
| Le DA qui a **retourné** au cycle précédent revalide la version corrigée | **Autorisé** |
| Le DR qui a retourné au cycle précédent revalide après resoumission | **Autorisé** |

L'esprit de RG-12 est intact : sur la version du dossier actuellement dans le circuit,
une personne n'agit qu'une fois. Ce qu'elle a fait sur une version annulée par un
retour ne pèse plus contre elle.

### Où la règle vit

- `domaine/CycleValidation` — le découpage, fonction pure sur la liste d'étapes,
  testable sans Spring.
- `application/SeparationTachesService` — la porte unique du contrôle, appelée par la
  validation aux deux niveaux.

Le rang d'étape est **calculé** (`dernier + 1`) et non fixe, y compris pour la
soumission : deux étapes de même rang rendraient le découpage en cycles incapable de
dire laquelle est la dernière soumission.

## 4. Où le contrôle s'insère, et où il ne s'insère pas

**Sur la validation, aux deux niveaux** — après l'obtention du profil (l'identifiant
local est nécessaire), avant l'aiguillage et avant toute écriture. Ordre du document
maître §7.3 : habilitation, règles, puis modification.

**Pas sur le retour.** RG-12 interdit de *valider* un dossier qu'on a soutenu, pas de
le refuser. Le refus n'engage pas la banque, il l'en empêche : le risque que RG-12
prévient n'existe pas. Un chef d'unité qui aurait soumis puis retourné son propre état
ne ferait que le renvoyer en saisie, ce qui ne fait avancer aucun paiement.

## 5. Le cumul de rôles : refus strict

**Décision : refus strict, message explicite, déblocage organisationnel.**

Précision de fait : `utilisateurs.role` est **une seule colonne** (CLAUDE.md §4). Dans
ce module, un compte porte **un rôle à la fois** — un cumul simultané n'existe pas.
Le cas réel est le changement de rôle dans le temps : la personne a soumis comme agent,
puis a été promue chef d'unité. RG-12 la refuse alors sur ce dossier, ce qui est le
comportement de contrôle interne attendu.

Options écartées :

- **Tolérance si personne d'autre n'existe** — infaisable : le service Workflow ne
  connaît pas l'annuaire des valideurs d'une unité, et `GET /identite/habilitation` ne
  répond qu'à « cette personne a-t-elle droit sur cette unité ? », jamais « qui
  d'autre ? ».
- **Escalade automatique au niveau supérieur** — inventerait un circuit absent d'ET01
  et contraire à RG-07 (« aucun saut de niveau »), et masquerait un problème
  d'organisation au lieu de le signaler.

Limite connue et assumée : dans une unité sans suppléant, un dossier soumis puis
repris par la même personne reste bloqué jusqu'à intervention organisationnelle. Le
découpage en cycles (§3) élimine le cas de loin le plus fréquent — le retour suivi
d'une resoumission —, qui aurait sinon frappé **toutes** les unités à un seul DA.

## 6. Le refus est un troisième code en 403

`403 SEPARATION_TACHES`, code prévu au contrat d'API §5, distinct de `ACCES_REFUSE` et
de `UTILISATEUR_NON_HABILITE`. Les trois demandent trois gestes différents :

| Code | Ce qu'il dit | Ce que la personne doit faire |
| --- | --- | --- |
| `ACCES_REFUSE` | votre rôle ne permet pas cette action | changer d'écran, ou vérifier son rôle |
| `UTILISATEUR_NON_HABILITE` | vous n'avez pas de droit sur cette unité | demander une habilitation |
| `SEPARATION_TACHES` | vous avez déjà agi sur ce dossier | **rien** — le dossier doit changer de mains |

Les confondre laisserait un chef d'unité réclamer indéfiniment une habilitation qu'il
possède déjà. Le refus est tracé en audit avec son propre motif : un contrôle interne
doit pouvoir compter les tentatives de cumul séparément des accès hors périmètre.

## 7. Conséquences pour les sprints suivants

- **Sprint 6 (Reporting)** — l'historique d'un processus (`GET
  /reporting/processus/{id}/historique`) doit rendre les étapes **par cycle**, sans
  quoi un dossier ayant fait deux tours paraîtra porter deux validations DA
  contradictoires. `CycleValidation` est réutilisable tel quel.
- **Sprint 6bis (état complémentaire)** — un état COMPLEMENTAIRE est un processus
  distinct, donc un parcours neuf : RG-12 s'y applique sans adaptation.
- `EtapeWorkflowRepository.findByIdProcessusAndIdActeur`, annoncée au Sprint 4.1 comme
  le support de RG-12, **n'est pas utilisée** : le découpage en cycles a besoin de voir
  tout le parcours, y compris les étapes des autres acteurs, pour situer la dernière
  soumission. Elle est conservée — elle répond à une autre question, utile au
  reporting.
