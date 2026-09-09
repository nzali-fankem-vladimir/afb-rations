# Sprint d'ajustement métier — Rythme de paiement

**Date :** 9 septembre 2026
**Position :** hors séquence numérotée, intercalé entre les sous-sprints 6bis.1
et 6bis.2 — même statut que le sprint de rattrapage du service Audit
**Objet :** qualifier le signalement métier « le paiement est hebdomadaire », en
mesurer l'impact, et geler ce qui serait écrit faux
**Livrable :** point M-04 ouvert puis tranché, inventaire d'impact figé,
sous-sprint 6bis.2 gelé, forme de la période arbitrée

---

## 1. Ce que ce sprint a produit

**Aucune ligne de code.** C'est sa propriété définissante, et elle est vérifiée :
`git status` ne montre **aucune** modification sous `backend/`, ni sous
`frontend/`. Quatre fichiers de documentation, dont deux créés.

| Fichier | Nature |
| --- | --- |
| `docs/dispositifs_provisoires.md` | Ligne M-04 ajoutée au registre (section 3), sept colonnes servies, puis mise à jour après arbitrage |
| `docs/points-en-attente.md` | Section narrative M-04 — 338 lignes : trois lectures, gel motivé, inventaire d'impact en trois familles, trois questions portées, forme technique, passe documentaire préparée |
| `docs/decisions/2026-09-09-rythme-de-paiement-et-maille-de-la-periode.md` | **Créé** — porte les trois arbitrages rendus dans la journée |
| `docs/resumes-sprints/sprint-ajustement-metier-rythme-de-paiement.md` | **Créé** — le présent résumé |

## 2. La question posée, et la réponse du métier

Le métier a signalé que le paiement se fait **de façon hebdomadaire**. Trois
lectures se cachaient derrière ce seul mot ; elles ont été posées sans qu'aucune
ne soit présentée comme acquise, chacune décrite par ce qu'elle changerait
concrètement pour un agent de saisie, un Chef d'Unité et un Directeur Réseau une
fois l'application livrée.

| Lecture | Contenu | Réponse |
| --- | --- | --- |
| (A) | Seul le décaissement est hebdomadaire, le dossier reste mensuel | Écartée |
| **(B)** | **Tout le cycle devient hebdomadaire** | **Retenue par le métier** |
| (C) | Validation hebdomadaire, transmission comptable regroupée mensuellement | Écartée |

La valeur provisoire retenue à l'ouverture du point était **(A)** — non par
paresse, mais parce que c'était la seule lecture ne demandant aucune modification,
donc la seule ne fabriquant aucune dette si elle se révélait fausse. Elle est
consignée dans M-04 parce qu'elle explique pourquoi rien n'a été écrit avant la
réponse.

## 3. Les trois arbitrages rendus

Trois questions ne se résolvaient pas en code. Elles ont été portées le
9 septembre 2026 et tranchées le même jour.

**① Le seuil RG-08 est maintenu à 100 000 XAF.** Le point avait été soulevé comme
un changement du niveau d'approbation requis pour engager la banque, pas comme un
réglage technique. **Conséquence mesurée en base**, pas supposée : sur les 15
états présents (données de test), **un seul** dépassait le seuil — l'état 1317,
105 000 XAF, monté au Directeur Réseau. Ramené à la semaine, il vaut ~24 249 XAF
et se clôturerait chez le Chef d'Unité. **Aucun des quinze** n'atteindrait le
Directeur Réseau. Le seuil maintenu divise par ~4,33 la fréquence à laquelle un
dossier lui parvient. La décision est celle du métier, elle est appliquée telle
quelle, et elle reste **réversible sans redéploiement** — la valeur est relue en
base à chaque validation et jamais mise en cache (Sprint 4.3).

**② La période devient un intervalle de dates** (`date_debut`, `date_fin`), et
non un numéro de semaine ISO. Quatre motifs, vérifiés dans le code avant d'être
avancés : le contrôle `LIGNE_HORS_PERIODE` se simplifie en comparaison de bornes ;
le chevauchement de mois disparaît ; **la forme survit à tout changement ultérieur
de cadence sans nouvelle migration** — motif décisif, le métier venant de changer
d'avis une fois ; et elle évite les pièges de la semaine ISO (semaine 53, année
ISO décalée, bornes `@Max(12)` refusant une « semaine 13 »).

**③ Le chevauchement de mois est sans objet**, par conséquence directe de ②. La
semaine du 29 septembre au 5 octobre est `[2026-09-29, 2026-10-05]` : il n'y a
plus de mois d'appartenance à déterminer parce qu'il n'y a plus de champ à
remplir. **La notion de mois disparaît de l'identité de la période** — pas du
module : un rapport pourra toujours porter sur une plage couvrant un mois.

## 4. Ce qui reste ouvert

**Un seul point maintient M-04 ouvert : la position de la DFT sur le contrat
Kafka.** La charge publiée sur `rations.etat.valide` porte
`"periode": { "mois", "annee" }` et est consommée par le module de
comptabilisation, **qui n'est pas maintenu par cette équipe**. La forme retenue
côté module est l'intervalle de dates ; la porter sur le fil suppose que l'équipe
consommatrice l'accepte. À poser dans le même échange que **M-03** et **D-11**,
mêmes interlocuteurs. Contexte utile : **7 états ont déjà été transmis** sous la
forme mensuelle et ne sont pas rejouables.

M-04 est passé au registre à l'état **« Partiellement résolu »**.

## 5. Le gel, et sa portée exacte

**Le sous-sprint 6bis.2 est gelé** — il implémente RG-15 sur « la même période »,
la première règle du module à figer en code la définition de la période.

Le motif du gel a été **révisé deux fois dans la journée**. Le guide prévoyait que
6bis.2 « reprenne dès que le métier a répondu, quelle que soit sa réponse » : cette
phrase supposait la lecture (A). La réponse étant (B), le gel a d'abord été
maintenu dans l'attente de la forme. Celle-ci étant désormais arbitrée, **le gel
subsiste pour une raison qui n'est plus une inconnue mais un ordre de travaux** :
la forme est décidée, la migration qui la porte n'est pas écrite. Tant que
`processus_mensuel` et `fiche_journaliere` portent `(mois_paiement,
annee_paiement)`, écrire RG-15 reviendrait à écrire faux en connaissance de cause.

**6bis.2 reprend derrière les migrations de la maille**, pas derrière une réponse
à obtenir. Le découpage de ces travaux — sprint dédié ou travaux rattachés à
6bis.2 — reste à arrêter.

**Rien d'autre n'est gelé.** 7F.1 (socle), 7F.2 (layout), 7F.3 (authentification),
8.1 et 8.2 ne touchent pas à la période et peuvent avancer — vérifié :
`frontend/src` ne contient **aucune** occurrence de `mois`, `periode` ou `annee`.
7F.4, 7F.5 et 7F.7 en dépendent, mais viennent après 7F.1–7F.3 : l'arbitrage aura
eu lieu d'ici là. Geler tout le projet aurait été aussi faux que d'ignorer la
question.

## 6. L'inventaire d'impact, en trois familles

Le guide demandait de distinguer une **rupture** (le code cesse d'être correct)
d'une **vérité de documentation à reformuler** (le code reste correct, sa
justification vieillit). Les mélanger ferait passer une relecture de javadoc pour
une migration.

### Ce qui casse — 12 points de rupture

Deux migrations (`processus_mensuel` avec `ux_processus_normal_par_periode`,
`fiche_journaliere` avec `idx_fiche_journaliere_unite_periode` et la recopie
figée V3) ; un contrat d'entrée (`DeclenchementProcessusRequest`, bornes
`@Max(12)`) ; une règle de gestion (`CompletudeService.dansLaPeriode`) ; deux
conventions d'artefact (nommage des pièces jointes, libellé `TOTAL DU MOIS`) ; le
contrat Kafka ; les contrôles de `ConstructionChargeService` ; le service
Reporting (filtres, réponses, nommage d'export) ; et **la borne
`app.reporting.limite-resultats`** — calibrée sur ~600 états/an soit ~8 ans de
marge, qui deviennent **moins de deux ans** à ~2 600 états/an.

### Ce qui reste vrai mais dont la justification vieillit — 4 points

Quatre décisions sont justifiées par « c'est un geste mensuel » : soumission à
15 s au pire cas (4.2), `SELECT` du seuil jugé invisible (4.3), `acks=all` (5.1),
arbitrage de latence contre la doctrine du 3.2 (5.1). **Vérification faite : les
quatre arguments tiennent.** ~2 600 soumissions par an font ~10 par jour ouvré —
trivial. Seule leur formulation est à reprendre.

### Ce qui n'est pas impacté — vérifié point par point dans le code

Le **grain journalier** en entier (`uk_fiche_journaliere_processus_jour` sur
`(id_processus, date_jour)`, index RG-04 sur la fiche) — un agent saisira
exactement comme aujourd'hui ; le **service Grilles** en entier (montant résolu à
la **date de la journée**, ses périodes de validité sont d'ailleurs *déjà* des
intervalles de dates) ; **Identité, passerelle, `rations-audit-commun` et service
Audit** — zéro occurrence, compté ; **l'accusé comptable**
(`AccuseComptableEvent` rapproche par `idProcessus` seul) ; **les décisions du
circuit de validation** ; **le frontend existant**.

## 7. Trois écarts relevés dans le guide lui-même

Le guide exigeait de vérifier ses propres affirmations plutôt que de les recopier.
Trois ne tenaient pas.

1. **Le tableau des points de rupture n'existait pas.** L'étape 4 renvoyait à « la
   section 8 du présent guide » ; cette section est *« Commandes terminal »*.
   L'inventaire a donc été établi de première main par balayage du backend.
2. **Deux affirmations de la liste « non impacté » étaient fausses telles
   qu'écrites.** Le guide affirmait que « le service Workflow ne lit le type ni la
   période nulle part dans la validation ». Or `AiguillageService` lit bien le
   type — un état `COMPLEMENTAIRE` monte au Directeur Réseau sans lecture du seuil
   (décision du Sprint 6bis.1, postérieure à la rédaction de la liste) ; et
   `ValidationService` / `RetourService` lisent bien mois et année pour composer
   un libellé « L'état 09/2026 de l'unité 00002 ». La première lecture est
   indifférente au rythme ; **la seconde a été reclassée en rupture mineure**, car
   en hebdomadaire quatre états porteraient le même intitulé.
3. **W-02 n'est pas au registre.** Le guide demandait de présenter M-04 « à côté
   de W-02, déjà ouvert vers le métier » ; W-02 ne vit que dans les tableaux de
   questions ouvertes des résumés 4.1 et 4.2, il n'a jamais été porté dans
   `docs/dispositifs_provisoires.md` section 3. Signalé dans M-04, **non ajouté
   d'office au registre**.

## 8. Mesures d'impact en base

Exécutées avant rédaction de l'inventaire, pour dire combien de **données**
seraient concernées — question distincte de « combien de lignes de code ».

| Mesure | Résultat |
| --- | --- |
| États portant une période mensuelle | **15** — 11 `CLOTURE`, 1 `EN_COURS_SAISIE`, 1 `RETOURNE`, 2 `COMPLEMENTAIRE`, de 2026-09 à 2027-09 |
| Fiches portant la période recopiée figée (V3) | **18 / 18**, sur 14 couples unité + période |
| Journées à cheval sur deux mois dans une même semaine ISO | **0** — le problème était prospectif, pas constaté ; il est éteint avant d'être apparu |
| États déjà transmis sous la forme mensuelle du contrat | **7** — non rejouables |
| Pièces jointes archivées en `{annee}/{mois}` | **12**, ex. `2026/09/etat-rations-00002-202609-p109.pdf` |
| États dépassant le seuil, en mensuel puis en hebdomadaire | **1 → 0** |

## 9. Passe documentaire préparée, aucune correction faite

Liste établie pour que la correction soit une **liste** et non une chasse. Elle
s'exécute avec les migrations, pas maintenant.

- **Devient faux :** `sprint-3.4-consolidation-mensuelle.md` (**à commencer par
  son nom de fichier**) ; la volumétrie de `sprint-6.1` (ligne 98) ; « Second
  déclenchement même unité et période » de `sprint-4.1` (lignes 100 et 137) ;
  **RG-06 elle-même**, dont le libellé porte le mot « mensuelle », ainsi que
  US-06, US-07 et US-16 ; le nom de la table `processus_mensuel` et de la classe
  `ProcessusMensuel`.
- **Reformulation seulement :** les quatre justifications « geste mensuel ».
- **Artefacts physiques déjà produits :** 12 pièces jointes sous
  `{annee}/{mois}`, dont 7 rattachées à des états transmis. Les laisser sous
  l'ancienne forme est probablement la seule réponse acceptable — un document
  signé ne se réécrit pas.

## 10. Critères de validation

| Critère | Statut |
| --- | --- |
| Trois lectures distinguées et posées au métier | ✅ Fait, dans ces termes, aucune présentée comme acquise |
| M-04 présent au registre (`dispositifs_provisoires.md` §3) | ✅ Sept colonnes servies |
| M-04 présent en section narrative (`points-en-attente.md`) | ✅ 338 lignes, sur le modèle de A-01 |
| Valeur provisoire (cycle mensuel) explicitement retenue et motivée | ✅ Consignée, puis écartée par la réponse (B) |
| Sous-sprint 6bis.2 gelé, motif écrit | ✅ Motif révisé deux fois, la révision est tracée |
| Aucun autre sprint gelé | ✅ 7F.1–7F.3, 8.1, 8.2 libres — `frontend/src` vérifié indemne |
| Inventaire d'impact figé, rupture et documentation distinguées | ✅ Trois familles séparées |
| Liste du non-impacté vérifiée point par point **dans le code** | ✅ Vérifiée, et **deux affirmations du guide corrigées** |
| Seuil RG-08 porté devant la DRH et la DFT | ✅ Porté, **et tranché** — maintenu |
| Contrat Kafka porté devant la DFT, avec M-03 et D-11 | ✅ Porté, **reste ouvert** |
| Chevauchement de mois porté devant le métier | ✅ Porté, **tranché sans objet** |
| Forme technique présentée comme proposition, non tranchée d'office | ✅ Recommandée puis **arbitrée par l'utilisateur**, jamais décidée seul |
| `git status` ne montre aucune modification sous `backend/` | ✅ Zéro |
| `mvn -pl service-workflow test` reste au vert | ✅ BUILD SUCCESS, exit 0, inchangé |
| Aucun document de décision créé prématurément | ✅ Créé **après** l'arbitrage, daté du jour de la décision |

## 11. Ce que ce sprint prouve

Le mensuel n'avait jamais été **écrit** comme une décision : c'était un postulat
hérité du processus papier, invisible parce que jamais formulé. Aucun document du
projet ne contenait une occurrence de « hebdomadaire » ou de « semaine ».

Reçue aujourd'hui, l'information a coûté un gel de quelques jours. Reçue au
Sprint 9, elle aurait coûté la reprise de deux bases, d'un contrat
inter-applicatif et de dix-huit fichiers de tests.

**La forme retenue est choisie en partie pour cela** : l'intervalle de dates ne
reformule pas un nouveau postulat de cadence — il refuse d'en poser un.
