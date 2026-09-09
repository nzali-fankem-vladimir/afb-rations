# Rythme de paiement hebdomadaire et maille de la période

**Date :** 9 septembre 2026
**Sprint :** ajustement métier « rythme de paiement », hors séquence numérotée,
intercalé entre les sous-sprints 6bis.1 et 6bis.2
**Point de registre :** M-04 (`docs/points-en-attente.md`,
`docs/dispositifs_provisoires.md` section 3)
**Statut :** tranché sur les trois questions posées ; mise en œuvre à planifier

---

## 1. Le fait qui a déclenché ce sprint

Le métier a signalé que le paiement des frais de ration et de transport de la
garde armée se fait **de façon hebdomadaire**, et non mensuelle.

Tout le module était bâti sur l'hypothèse inverse, jamais discutée nulle part :
aucun document du projet — cahier des charges, user stories, contrat d'API,
guides de sprint, résumés, décisions — ne contenait une seule occurrence de
« hebdomadaire » ou de « semaine » au sens d'un rythme de paiement. Le mensuel
était un postulat implicite hérité de la description du processus papier
(« consolidation mensuelle à la main »), jamais remis en cause depuis le
Sprint 0.

L'information est arrivée juste avant le sous-sprint 6bis.2, qui doit
implémenter RG-15 — la **première règle du module à figer en code la définition
de la période**. Une semaine plus tard, elle aurait été écrite, testée,
commitée, puis défaite.

## 2. Ce qui a été demandé au métier, et ce qu'il a répondu

Trois lectures se cachaient derrière le même mot. Elles ont été posées sans
qu'aucune ne soit présentée comme acquise, avec pour chacune la description de
ce qu'elle changerait concrètement pour un agent de saisie, un Chef d'Unité et
un Directeur Réseau une fois l'application livrée.

| Lecture | Contenu | Impact annoncé |
| --- | --- | --- |
| **(A)** | Seul le décaissement est hebdomadaire ; le dossier reste mensuel | Quasi nul — le décaissement est hors périmètre (CLAUDE.md section 8) |
| **(B)** | Tout le cycle devient hebdomadaire | Majeur : maille de la période dans deux bases, contrat Kafka, valeur du seuil |
| **(C)** | Validation hebdomadaire, transmission comptable regroupée mensuellement | Le plus lourd : ajoute un niveau d'agrégation inexistant aujourd'hui |

**Réponse du métier, 9 septembre 2026 : lecture (B).** Tout le cycle devient
hebdomadaire — saisie, consolidation, soumission, validation, clôture et
transmission comptable.

La valeur provisoire retenue à l'ouverture de M-04 était **(A)**, non par
paresse mais parce que c'était la seule lecture ne demandant aucune
modification, donc la seule ne fabriquant aucune dette si elle se révélait
fausse. Elle est écartée par la réponse ; elle reste consignée parce qu'elle
explique pourquoi aucune ligne de code n'a été écrite avant.

## 3. Décision 1 — La valeur du seuil RG-08 est maintenue à 100 000 XAF

**Décidée le 9 septembre 2026**, en réponse à la question portée à la DRH et à
la DFT.

### Ce qui avait été soulevé

Les 100 000 XAF étaient calibrés sur le cumul d'un **mois**. Appliqués tels
quels à une semaine, ils laissent le Chef d'Unité clôturer seul des dossiers
qui, ramenés au mois, dépassent 400 000 XAF. Le point avait été porté comme un
changement du **niveau d'approbation requis pour engager la banque**, et non
comme un réglage technique.

### La conséquence, mesurée et non supposée

Mesure faite le 9 septembre 2026 sur les 15 états présents en base — **données
de test, non représentatives des volumes de production**, mais suffisantes pour
illustrer le mécanisme :

| | Seuil appliqué au mois (aujourd'hui) | Seuil appliqué à la semaine (demain) |
| --- | --- | --- |
| États dépassant 100 000 XAF | **1** sur 15 (état 1317, 105 000 XAF) | **0** sur 15 |

L'unique état étant monté au Directeur Réseau vaut ~24 249 XAF ramené à la
semaine : il se clôturerait désormais chez le Chef d'Unité. **En pratique, le
seuil maintenu tel quel divise par environ 4,33 la fréquence à laquelle un
dossier atteint le Directeur Réseau.**

### Ce que la décision implique

Le seuil reste à 100 000 XAF. Le Directeur Réseau ne sort pas formellement du
circuit — RG-07 et l'aiguillage de `AiguillageService` sont inchangés —, mais il
y intervient de fait bien plus rarement. **La décision est celle du métier, elle
est consignée telle quelle.**

**Point de réversibilité, à rappeler le jour où la question reviendra :** la
valeur vit dans `parametre_systeme`, elle est relue à **chaque** validation et
n'est **jamais mise en cache** (Sprint 4.3, CT-18). La changer ne demande ni
redéploiement, ni redémarrage, ni migration. Si l'usage révèle que le Directeur
Réseau doit revoir des dossiers, une seule ligne de base suffit à le rétablir.

**Ce qui reste interdit et ne change pas :** aucun repli, aucune valeur par
défaut, aucune seconde comparaison montant/seuil ailleurs que dans
`AiguillageService.aiguiller` (CLAUDE.md section 15).

## 4. Décision 2 — La période devient un intervalle de dates

**Deux formes étaient proposées, la seconde recommandée. La seconde est
retenue.**

| | Forme | Contenu |
| --- | --- | --- |
| Forme 1 | Semaine ISO | `semaine` + `annee_iso` |
| **Forme 2** | **Intervalle de dates** | **`date_debut` + `date_fin`** — retenue |

### Les quatre motifs, vérifiés dans le code avant d'être avancés

1. **Le contrôle de période se simplifie.** `LIGNE_HORS_PERIODE` devient « la
   date de la journée est-elle entre les deux bornes », au lieu de l'égalité
   `dateJour.getMonthValue() == mois && dateJour.getYear() == annee` de
   `CompletudeService.dansLaPeriode`. Plus simple **et** plus robuste.
2. **Le chevauchement de mois disparaît en tant que problème**, au lieu d'être
   arbitré cas par cas en six endroits. Voir la décision 3 ci-dessous.
3. **La forme survit à tout changement ultérieur de cadence** — quinzaine,
   décade, retour au mois — **sans nouvelle migration**. Le métier vient de
   changer d'avis une fois ; rien ne dit qu'il ne le fera pas deux. C'est le
   motif décisif : il ne suppose pas que la cadence actuelle soit la dernière.
4. **Elle évite les pièges de la semaine ISO** : l'existence d'une **semaine
   53**, et une année ISO qui diffère de l'année calendaire aux premiers jours
   de janvier. Elle évite aussi que la borne `@Max(12)` de
   `DeclenchementProcessusRequest` et le contrôle équivalent de
   `ConstructionChargeService` refusent une « semaine 13 ».

### Ce que cela ne préjuge pas

L'intervalle de dates est une **forme de représentation**, pas une cadence.
Rien dans le schéma n'imposera que `date_fin - date_debut` vaille sept jours :
c'est le métier qui pose la cadence, et le module la représente. Cette
propriété est exactement ce qui donne au motif 3 sa valeur.

## 5. Décision 3 — La question du chevauchement de mois est sans objet

La troisième question portée au métier était : *une semaine à cheval sur deux
mois — du 29 septembre au 5 octobre — appartient-elle au mois de son premier
jour, à celui de son dernier, ou la notion de mois disparaît-elle du module ?*

**Réponse retenue : la troisième branche, par voie de conséquence de la
décision 2.** Un intervalle de dates n'a pas de mois d'appartenance à
déterminer : la semaine du 29 septembre au 5 octobre est simplement
`[2026-09-29, 2026-10-05]`. Il n'y a plus de question à trancher parce qu'il
n'y a plus de champ à remplir.

**La notion de mois disparaît de l'identité de la période.** Elle ne disparaît
pas du module : un rapport pourra toujours être demandé sur une plage de dates
couvrant un mois, et un utilisateur continuera de raisonner en mois. Ce qui
disparaît, c'est le **mois comme attribut porté par un état et par une fiche**.

Mesure de contrôle faite le 9 septembre 2026 : **zéro** journée en base tombe
aujourd'hui dans une semaine ISO à cheval sur deux mois. Le problème était
prospectif, pas constaté — il est éteint avant d'être apparu.

## 6. Ce qui reste ouvert, et pourquoi M-04 ne se ferme pas encore

**Le contrat Kafka publié à la comptabilité.** La charge publiée sur
`rations.etat.valide` porte `"periode": { "mois", "annee" }` (contrat d'API
section 7.1). Elle est consommée par le **module de comptabilisation, qui n'est
pas maintenu par cette équipe**. La forme retenue côté module est l'intervalle
de dates ; la porter sur le fil suppose que l'équipe consommatrice l'accepte et
adapte son côté. Ce n'est pas une décision que ce module peut prendre seul, quel
que soit l'arbitrage interne : **modifier unilatéralement la charge casserait un
flux de paiement chez quelqu'un d'autre, sans erreur visible de ce côté-ci**
(CLAUDE.md section 15).

À porter dans le même échange que **M-03** (seconde transmission sur une période
déjà traitée) et **D-11** (clé de partition des accusés) : ce sont les trois
questions ouvertes avec le même interlocuteur. Élément de contexte utile à
l'échange : **7 états ont déjà été transmis** sous la forme mensuelle actuelle,
et ne sont pas rejouables.

M-04 reste donc ouvert au registre sur ce seul point.

**Position du module arrêtée le même jour**, après lecture de l'extrait du cahier
des charges décrivant l'écriture attendue :
`docs/decisions/2026-09-09-contenu-de-la-charge-comptable.md`. Six décisions y
sont prises — forme de la période, partage du libellé, granularité, transition
par champ de version, compte de charge configurable, clé calculée par le CBS.
**Ce sont des positions à porter devant la DFT, pas un accord obtenu.**

Cet examen a de surcroît confirmé la décision 2 par un argument qui manquait :
le mois figure dans le **libellé de l'écriture**, donc sur le relevé de compte du
bénéficiaire. En hebdomadaire, la forme mensuelle produirait quatre lignes
identiques par mois. Il a aussi mis au jour un défaut sans rapport avec le
rythme — le numéro de compte courant n'est contrôlé ni en longueur ni en format,
et 44 des 45 bénéficiaires en base sont faux (point **T-02**).

## 7. Ce que la mise en œuvre suppose

Cette décision **n'écrit aucun code** et n'en a écrit aucun. Elle fixe la cible.
Le chantier qu'elle ouvre, établi par balayage du backend le 9 septembre 2026 et
détaillé dans la section M-04 de `docs/points-en-attente.md` :

- **Deux migrations** : `processus_mensuel` (workflow, avec l'index unique
  `ux_processus_normal_par_periode`) et `fiche_journaliere` (saisie, avec
  l'index `idx_fiche_journaliere_unite_periode` et la recopie figée de la
  migration V3).
- **Un contrat d'entrée** : `DeclenchementProcessusRequest`.
- **Une règle de gestion** : `CompletudeService.dansLaPeriode`.
- **Deux conventions d'artefact** : le nommage des pièces jointes
  (`NommageDocument`) et le libellé imprimé `TOTAL DU MOIS` (`DocumentService`).
- **Le service Reporting** : filtres, réponses, nommage d'export, et la borne
  `app.reporting.limite-resultats` — calibrée sur ~600 états/an, soit ~8 ans de
  marge, qui deviennent **moins de deux ans** à ~2 600 états/an.
- **Le service Transmission** : construction de la charge et contrôles de
  concordance, sous réserve de l'accord de la DFT (section 6).

**Ordre imposé.** Ces travaux précèdent le sous-sprint 6bis.2 : RG-15 porte sur
« la même période », elle ne peut pas s'écrire avant que la période ait sa forme
définitive. C'est la raison du gel, et elle survit à la réponse du métier.

## 8. Ce qui n'est pas impacté

Vérifié dans le code le 9 septembre 2026, point par point, et non recopié. Cette
liste borne le chantier : un inventaire qui ne liste que les dégâts fait croire
que tout est à refaire.

- **Tout le grain journalier.** `uk_fiche_journaliere_processus_jour` porte sur
  `(id_processus, date_jour)` ; l'index RG-04 porte sur
  `(id_fiche_journaliere, id_beneficiaire, nature, session)`. Aucune période n'y
  figure. **Un agent saisira exactement comme aujourd'hui.**
- **Le service Grilles en entier.** Le montant est résolu **à la date de la
  journée** (`resoudre(nature, session, LocalDate date)`, Sprint 2.4), jamais à
  la période. Les périodes de validité des grilles ont leurs propres bornes,
  sans rapport avec le rythme de paiement — et sont d'ailleurs *déjà* des
  intervalles de dates, ce qui rend la forme retenue cohérente avec l'existant.
- **Le service Identité, la passerelle, `rations-audit-commun` et le service
  Audit** : zéro occurrence de `mois`, `periode` ou `semaine` dans
  `src/main/java`.
- **L'accusé comptable.** `AccuseComptableEvent` rapproche l'état par
  `idProcessus` **seul** : aucune période, donc insensible au changement de
  maille.
- **Le circuit de validation, dans ses décisions.** RG-07 et RG-09 à RG-13 sont
  formulées sur l'état, jamais sur sa durée.
- **Le frontend existant.** `frontend/src` ne contient aucune occurrence de
  `mois`, `periode` ou `annee` : le socle du Sprint 0.3 est indemne.

**Quatre justifications vieillissent sans qu'aucune ne tombe** — soumission à
15 s au pire cas (4.2), `SELECT` du seuil jugé invisible (4.3), `acks=all`
(5.1), arbitrage de latence contre la doctrine du 3.2 (5.1). Toutes reposent sur
la formule « c'est un geste mensuel ». À ~2 600 soumissions par an, soit ~10 par
jour ouvré, les quatre arguments **tiennent** : seule leur formulation est à
reprendre. Ne pas les confondre avec une rupture.

## 9. Ce que ce sprint prouve, et qu'il faut garder

Le projet s'applique depuis le Sprint 1.3 une règle simple, inscrite en tête de
`docs/points-en-attente.md` : *« Ne pas figer de comportement définitif ailleurs
dans le code tant qu'un point reste ici. »* L'arbitrage du point A-01, au sprint
de rattrapage du service Audit, l'avait reformulée ainsi : *« décider de la forme
de l'écriture avant d'avoir conçu la lecture ne fait pas l'économie d'une
migration, il la garantit. »*

C'est exactement ce qui s'est joué ici. Reçue aujourd'hui, l'information a coûté
un gel de quelques jours. Reçue au Sprint 9, elle aurait coûté la reprise de deux
bases, d'un contrat inter-applicatif et de dix-huit fichiers de tests.

**La leçon à retenir pour les sprints suivants n'est pas « le métier change
d'avis ».** C'est que le mensuel n'avait jamais été **écrit** comme une décision :
il était un postulat hérité, invisible parce que jamais formulé. La forme retenue
— l'intervalle de dates — est choisie en partie pour cette raison : elle ne
reformule pas un nouveau postulat de cadence, elle refuse d'en poser un.

---

**Références :** M-04, M-03, D-11, W-02, RG-06, RG-08, RG-15, US-06, US-07,
US-16, US-18, `docs/points-en-attente.md`,
`docs/dispositifs_provisoires.md` section 3.
