# SPRINT D'AJUSTEMENT MÉTIER

## Rythme de paiement — mensuel ou hebdomadaire

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Qualifier le signalement métier « le paiement est hebdomadaire », en mesurer l'impact et geler ce qui ne doit pas être écrit avant la réponse |
| **Livrable** | Point en attente M-04 ouvert, inventaire d'impact figé, sous-sprint 6bis.2 gelé, forme technique proposée sous condition |
| **Durée** | Une demi-journée d'analyse, puis attente de la réponse métier |
| **Prérequis** | Sprint 6bis.1 terminé et commité |
| **Sprint suivant** | 6bis.2, unicité inter-états — **gelé jusqu'à la réponse au point M-04** |

## Nature de ce sprint

**Ce sprint ne produit aucun code.** Ni migration, ni entité, ni endpoint, ni test. C'est ce qui le distingue de tous les autres guides du projet, et ce n'est pas un aveu de faiblesse : c'est le seul travail utile tant que la question posée n'a pas de réponse.

Il fait trois choses, et rien d'autre. Il **qualifie** ce que « hebdomadaire » recouvre réellement, parce que trois lectures très différentes se cachent derrière le même mot. Il **inventorie** l'impact pendant que l'information est fraîche, pour qu'aucun point ne se perde. Il **gèle** le seul code qui serait écrit faux si on continuait.

Le projet s'applique déjà cette règle à lui-même. `docs/points-en-attente.md` ouvre par : « Ne pas figer de comportement définitif ailleurs dans le code tant qu'un point reste ici. » Et l'arbitrage du point A-01, au sprint de rattrapage du service Audit, a retenu que « décider de la forme de l'écriture avant d'avoir conçu la lecture ne fait pas l'économie d'une migration, il la garantit ». C'est exactement la situation.

Sprint **hors séquence numérotée**, intercalé entre 6bis.1 et 6bis.2 — même statut que le sprint de rattrapage du service Audit (CLAUDE.md section 17).

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Ensemble du sous-sprint | Opus | Élevé |

Maintenir Opus effort élevé. Ce sprint n'écrit pas de code, mais il décide de ce qui sera écrit dans les six suivants, et il touche à trois sujets où une erreur d'appréciation coûte cher : la maille de la période, le seuil d'approbation de la banque, et un contrat publié à une équipe externe.

## 2. Outil de cartographie

```
py -3.14 -m graphify update .
```

## 3. Contexte

Le métier vient d'indiquer que le paiement des frais de ration et de transport de la garde armée se fait **de façon hebdomadaire**, et non mensuelle.

Tout le module est bâti sur l'hypothèse inverse, et cette hypothèse n'a jamais été discutée nulle part : aucun document du projet — cahier des charges, user stories, contrat d'API, guides de sprint, résumés, décisions — ne contient une seule occurrence de « hebdomadaire » ou de « semaine » au sens d'un rythme de paiement. Le mensuel est un postulat implicite, hérité de la description du processus papier (« consolidation mensuelle à la main »), et jamais remis en cause depuis le Sprint 0.

**L'information arrive au pire moment possible, et c'est précisément ce qui la rend utile.** Le sous-sprint 6bis.1 vient de s'achever : il ouvre un état complémentaire sur une période close. Le sous-sprint 6bis.2 doit implémenter RG-15, l'unicité inter-états, dont l'énoncé est « aucune ligne ne peut reproduire une combinaison déjà présente dans un autre état **de la même unité et de la même période** ». C'est la première règle du module qui **fige en code la définition de la période**. Une semaine plus tard, elle aurait été écrite, testée, commitée — et il aurait fallu la défaire.

Reçue aujourd'hui, l'information coûte un gel. Reçue au Sprint 9, elle aurait coûté une reprise de trois bases de données, d'un contrat inter-applicatif et de dix-huit fichiers de tests.

## 4. Objectifs

- Distinguer les trois lectures possibles de « paiement hebdomadaire » et obtenir du métier laquelle est la bonne
- Ouvrir le point en attente **M-04** au registre, avec sa valeur provisoire et son incidence
- Geler le sous-sprint 6bis.2 jusqu'à la réponse, et dire pourquoi lui et pas un autre
- Figer l'inventaire d'impact, service par service, pendant qu'il est frais
- Porter les trois conséquences **non techniques** devant leurs interlocuteurs : le seuil RG-08 devant la DRH, le contrat Kafka devant la DFT, le chevauchement de mois devant le métier
- Proposer une forme technique **sous condition**, à arbitrer seulement si la réponse le justifie
- Recenser les documents dont les affirmations deviendraient fausses, pour que la correction soit une liste et non une chasse

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| RG-06 | Consolidation mensuelle automatique par unité — le mot « mensuelle » est dans le libellé même |
| RG-08 | Seuil d'aiguillage : sa **valeur** est calibrée sur un cumul mensuel |
| RG-15 | Unicité inter-états « sur la période » — la règle que 6bis.2 doit écrire |
| US-06 | « Que le système consolide automatiquement les fiches en un état mensuel » |
| US-07 | « Consulter puis soumettre l'état mensuel consolidé » |
| US-16 | « Les rapports sont produits par période et par agence » |
| US-18 | Unicité inter-états avant d'accepter une ligne de régularisation |
| M-04 | **Point ouvert par ce sprint** — rythme de paiement, mensuel ou hebdomadaire |
| W-02 | Point voisin, déjà ouvert : rien n'interdit d'ouvrir un état sur une période future |

## 6. Étapes d'implémentation

### Étape 1. Qualifier la demande métier

```
Le metier a signale que le paiement des rations et du transport de la
garde armee se fait de facon HEBDOMADAIRE, et non mensuelle.

Avant toute analyse d'impact, il faut savoir de quoi on parle. Trois
lectures se cachent derriere le meme mot, et elles n'ont pas du tout
les memes consequences :

(A) LE DECAISSEMENT est hebdomadaire, mais le cycle documentaire
    reste mensuel. Les agents recoivent leur argent chaque semaine,
    mais l'etat consolide, valide par le circuit et transmis a la
    comptabilite reste l'etat du mois.
    Impact sur le module : QUASI NUL. Le decaissement est hors
    perimetre (CLAUDE.md section 8 : le module ne produit aucune
    ecriture comptable et ne touche pas au CBS). Le module continue
    de faire ce qu'il fait.

(B) LE CYCLE ENTIER est hebdomadaire. Saisie, consolidation,
    soumission, validation, cloture et transmission comptable ont
    lieu chaque semaine.
    Impact : MAJEUR. La maille de la periode change dans trois bases,
    dans le contrat publie a la comptabilite, et dans la valeur du
    seuil d'approbation de la banque.

(C) HYBRIDE : validation hebdomadaire, mais transmission comptable
    regroupee mensuellement.
    Impact : le plus lourd des trois. Il ajoute un niveau
    d'agregation qui n'existe nulle part aujourd'hui, entre l'etat
    valide et l'evenement publie.

Ne devine pas. Ne choisis pas la lecture qui t'arrange. Pose la
question au metier dans ces termes, avec un exemple concret pour
chacune, et ATTENDS LA REPONSE.

Tant que la reponse n'est pas connue, la valeur provisoire retenue
est (A) : c'est la seule qui ne demande aucune modification, donc la
seule qui ne fabrique aucune dette si elle se revele fausse.
```

### Étape 2. Ouvrir le point en attente M-04

```
Ouvre le point M-04 au registre des points en attente. Il vit a DEUX
endroits, et les deux doivent etre servis :

1. docs/dispositifs_provisoires.md section 3, dans le tableau, avec
   les colonnes existantes : Ref, Objet, Interlocuteur, Demande le,
   Valeur provisoire, Incidence si non resolu, Etat.

   Ref            : M-04
   Objet          : Rythme de paiement, mensuel ou hebdomadaire
   Interlocuteur  : Metier
   Demande le     : la date du jour
   Valeur prov.   : cycle mensuel conserve (lecture A)
   Incidence      : maille de la periode figee a tort dans RG-15,
                    puis dans tout le frontend
   Etat           : En attente

2. docs/points-en-attente.md, en section narrative, sur le modele de
   la section A-01. Elle expose les trois lectures, dit pourquoi la
   valeur provisoire est (A), et nomme ce qui est gele en attendant.

M-04 est la premiere reference metier libre : M-01, M-02 et M-03 sont
prises. Presente-le a cote de W-02, qui est deja ouvert vers le metier
et porte sur la meme table (les periodes d'un etat) : les deux
questions peuvent partir dans le meme echange.

Montre les deux ajouts.
```

### Étape 3. Geler le sous-sprint 6bis.2

```
Le sous-sprint 6bis.2 implemente RG-15 : « aucune ligne ne peut
reproduire une combinaison (beneficiaire, journee, nature, session)
deja presente dans un autre etat de la MEME UNITE et de la MEME
PERIODE ».

C'est la premiere regle du module qui fige en code la definition de
la periode. Ecrite aujourd'hui, elle le serait sur le couple
(mois_paiement, annee_paiement) recopie sur fiche_journaliere par la
migration V3 du service Saisie, et sur l'index
idx_fiche_journaliere_unite_periode qui va avec.

Inscris le gel de 6bis.2 dans docs/points-en-attente.md, sous M-04, et
dis-le en une phrase : ce sous-sprint reprend des que le metier a
repondu, quelle que soit sa reponse.

En revanche, NE GELE RIEN D'AUTRE. Les sprints 7F.1, 7F.2, 7F.3, 8.1
et 8.2 ne touchent pas a la periode et peuvent avancer. Geler tout le
projet pour une question ouverte serait aussi faux que de l'ignorer.
```

### Étape 4. Figer l'inventaire d'impact

```
Ecris l'inventaire d'impact dans la section narrative de M-04, service
par service. Il est deja etabli, ne le refais pas : reprends-le tel
quel du present guide, section 8, tableau des points de rupture.

Deux precautions de redaction :

- Dis explicitement CE QUI N'EST PAS IMPACTE, et pas seulement ce qui
  l'est. Un inventaire qui ne liste que les degats fait croire que
  tout est a refaire, et cette croyance coute plus cher que le travail
  reel.

- Distingue ce qui est une RUPTURE (le code cesse d'etre correct) de
  ce qui est une VERITE DE DOCUMENTATION A REFORMULER (le code reste
  correct, sa justification ecrite vieillit). Les melanger ferait
  passer une relecture de javadoc pour une migration.
```

### Étape 5. Porter les trois questions non techniques

```
Trois consequences ne se resolvent pas en code et ne t'appartiennent
pas. Elles doivent partir chez leurs interlocuteurs, chacune dans les
termes de son metier :

1. LE SEUIL RG-08, devant la DRH et la DFT.
   Les 100 000 XAF sont calibres sur le cumul d'un MOIS. Applique tel
   quel a une semaine, ce seuil laisse cloturer directement par le
   Chef d'Unite des dossiers qui, ramenes au mois, valent plus de
   400 000 XAF. Le Directeur Reseau sortirait de fait du circuit.
   Ce n'est pas un reglage technique, c'est un changement du niveau
   d'approbation requis pour engager la banque.
   Bonne nouvelle a dire au metier : la valeur vit dans
   parametre_systeme et se change sans redeploiement (RG-08). C'est
   la DECISION qui manque, pas le moyen de l'appliquer.

2. LE CONTRAT KAFKA, devant la DFT.
   La charge publiee sur rations.etat.valide porte
   "periode": { "mois", "annee" } (contrat d'API section 7.1). Elle
   est consommee par le module de comptabilisation, qui n'est pas
   maintenu par cette equipe. Toute autre forme de periode est une
   modification de contrat inter-applicatif.
   A poser dans le meme echange que M-03 (seconde transmission sur
   une periode deja traitee) et D-11 (cle de partition des accuses) :
   ce sont les trois questions ouvertes avec le meme interlocuteur.

3. LE CHEVAUCHEMENT DE MOIS, devant le metier.
   Une semaine tombe a cheval sur deux mois : du 29 septembre au
   5 octobre. Ce cas n'existe pas aujourd'hui, et le module n'a aucun
   moyen de le representer — fiche_journaliere recopie et FIGE le
   mois et l'annee du processus (migration V3, service Saisie).
   Question a poser en clair : une semaine a cheval appartient-elle
   au mois de son premier jour, a celui de son dernier, ou la notion
   de mois disparait-elle du module ?

Ces trois questions partent AVANT toute decision technique. La forme
qu'on donnera a la periode depend de la troisieme.
```

### Étape 6. Proposer la forme technique, sous condition

```
Cette etape ne s'execute QUE si le metier confirme la lecture (B) ou
la lecture (C). Si c'est (A), elle ne s'execute pas du tout : ferme
M-04 et reprends 6bis.2 tel qu'il etait prevu.

Si (B) ou (C), la question est : par quoi remplacer le couple
(mois_paiement, annee_paiement) ?

Deux formes possibles, et je recommande la seconde :

  FORME 1 — numero de semaine ISO + annee ISO
  FORME 2 — intervalle de dates (date_debut, date_fin)

Quatre motifs en faveur de l'intervalle de dates :

1. Le controle LIGNE_HORS_PERIODE devient un test « la date de la
   journee est-elle entre les deux bornes », plus simple ET plus
   robuste que l'egalite mois + annee d'aujourd'hui
   (CompletudeService.dansLaPeriode).
2. Le chevauchement de mois disparait en tant que probleme, au lieu
   d'etre traite cas par cas dans six endroits differents.
3. La forme survit a tout changement ulterieur de cadence — quinzaine,
   decade, mois de nouveau — sans nouvelle migration. Le metier vient
   de changer d'avis une fois ; rien ne dit qu'il ne le fera pas deux.
4. Elle evite les pieges classiques de la semaine ISO : l'existence
   d'une semaine 53, et une annee ISO qui differe de l'annee
   calendaire aux premiers jours de janvier. Elle evite aussi que
   @Max(12) sur DeclenchementProcessusRequest et le controle
   mois > 12 de ConstructionChargeService refusent une semaine 13.

C'est une PROPOSITION A ARBITRER, pas une decision acquise. Presente
les deux formes, recommande la seconde, et attends l'arbitrage. Si tu
la retiens, elle devra faire l'objet d'un document dans
docs/decisions/ le jour ou elle sera tranchee, jamais avant.
```

### Étape 7. Dire ce qui ne change pas

```
Recense explicitement ce qui est INDIFFERENT au rythme de paiement.
Cette liste vaut autant que l'autre : elle borne le chantier.

- Tout le grain JOURNALIER. La fiche est ouverte par jour
  (uk_fiche_journaliere_processus_jour porte sur
  (id_processus, date_jour)), RG-04 controle l'unicite dans la
  journee, RG-05 ouvre une fiche vierge chaque jour. Un agent
  saisira exactement comme aujourd'hui.
- Le service Grilles en entier. Le montant est resolu A LA DATE DE LA
  JOURNEE (Sprint 2.4), jamais a la periode. Les grilles ont leurs
  propres dates de validite, sans rapport avec le rythme de paiement.
- Le service Identite, la passerelle, et rations-audit-commun : zero
  occurrence de mois ou de periode.
- L'accuse comptable. AccuseComptableEvent rapproche l'etat par
  idProcessus SEUL (Sprint 5.2) : il ne porte aucune periode, donc il
  est insensible au changement de maille.
- Tout le circuit de validation. RG-07, RG-09 a RG-13 sont formulees
  sur l'etat, jamais sur sa duree. Le service Workflow ne lit le type
  ni la periode nulle part dans la validation.

Verifie chacune de ces affirmations avant de l'ecrire. Ne recopie pas
cette liste sur ma parole.
```

### Étape 8. Recenser les documents à corriger

```
Une fois la reponse metier connue, et SEULEMENT si elle est (B) ou
(C), une passe documentaire sera necessaire. Prepare des maintenant sa
liste, pour qu'elle soit une liste et non une chasse.

Trois familles, par ordre de gravite :

1. CE QUI DEVIENT FAUX. Affirmations qui cesseraient d'etre vraies :
   - docs/resumes-sprints/sprint-3.4-consolidation-mensuelle.md, a
     commencer par son NOM DE FICHIER et son titre
   - sprint-6.1 : « ~50 unites x un etat par mois = ~600/an ; 5 000
     represente ~8 ans ». En hebdomadaire : ~2 600/an, soit moins de
     deux ans avant que la borne app.reporting.limite-resultats ne
     morde et ne rende 422 RECHERCHE_TROP_LARGE en production.
   - sprint-4.1 : « Second declenchement meme unite et periode »
   - RG-06 elle-meme, dont le libelle porte le mot « mensuelle »

2. CE QUI RESTE VRAI MAIS DONT LA JUSTIFICATION VIEILLIT. Quatre
   decisions sont justifiees par « c'est un geste mensuel » :
   soumission a 15 s au pire cas (4.2), SELECT du seuil juge invisible
   (4.3), acks=all (5.1), arbitrage de latence contre la doctrine du
   3.2 (5.1).
   VERIFIE AVANT DE CONCLURE, et ne dramatise pas : 50 unites x 52
   semaines fait environ 2 600 soumissions par an, ce qui reste
   trivial. Ces arguments TIENNENT. Seule leur formulation est a
   reprendre.

3. CE QUI EST UN ARTEFACT PHYSIQUE DEJA PRODUIT. La convention de
   nommage des pieces jointes,
   {annee}/{mois}/etat-rations-{unite}-{annee}{mois}-p{id}.pdf, et le
   libelle « TOTAL DU MOIS » imprime dans les documents signes. Des
   fichiers existent deja sur le stockage sous cette forme : la
   question n'est pas seulement de changer la convention, mais de
   savoir ce qu'on fait des documents deja archives.

Montre la liste. Ne corrige rien tant que M-04 est ouvert.
```

## 7. Fichiers à créer ou modifier

| Chemin | Nature |
|---|---|
| `docs/dispositifs_provisoires.md` | Modification, section 3 : ligne M-04 au registre |
| `docs/points-en-attente.md` | Modification : section narrative M-04, inventaire d'impact, gel de 6bis.2 |
| `docs/decisions/AAAA-MM-JJ-rythme-de-paiement-et-maille-de-la-periode.md` | Création **différée** — le jour où le métier répond, jamais avant |

**Aucun fichier de `backend/` n'est touché par ce sprint.** C'est sa propriété définissante, et le critère de validation le vérifie.

## 8. Commandes terminal

Mesure de l'impact réel sur les bases, à exécuter avant de rédiger l'inventaire. Elles disent combien de données existantes seraient concernées par une reprise — ce qui n'est pas la même question que « combien de lignes de code ».

```bash
docker ps --filter "name=rations" --format "{{.Names}}\t{{.Status}}"
```

Volume d'états portant une période mensuelle :

```sql
\c rations_workflow
SELECT type_processus, statut, COUNT(*) AS nombre,
       MIN(annee_paiement || '-' || LPAD(mois_paiement::text, 2, '0')) AS plus_ancien,
       MAX(annee_paiement || '-' || LPAD(mois_paiement::text, 2, '0')) AS plus_recent
FROM processus_mensuel
GROUP BY type_processus, statut
ORDER BY type_processus, statut;
```

Fiches portant la recopie figée du mois et de l'année (migration V3) :

```sql
\c rations_saisie
SELECT COUNT(*) AS fiches_total,
       COUNT(mois_paiement) AS fiches_avec_periode_recopiee,
       COUNT(DISTINCT (code_unite, annee_paiement, mois_paiement)) AS couples_unite_periode
FROM fiche_journaliere;
```

Journées qui tomberaient à cheval sur deux semaines calendaires, si la maille devenait hebdomadaire — mesure du problème réel, pas du problème supposé :

```sql
\c rations_saisie
SELECT EXTRACT(ISOYEAR FROM date_jour) AS annee_iso,
       EXTRACT(WEEK FROM date_jour)    AS semaine_iso,
       COUNT(DISTINCT EXTRACT(MONTH FROM date_jour)) AS mois_touches,
       COUNT(*) AS journees
FROM fiche_journaliere
GROUP BY 1, 2
HAVING COUNT(DISTINCT EXTRACT(MONTH FROM date_jour)) > 1
ORDER BY 1, 2;
```

États déjà transmis à la comptabilité sous la forme mensuelle du contrat, donc non rejouables :

```sql
\c rations_workflow
SELECT COUNT(*) AS etats_transmis
FROM processus_mensuel
WHERE transmis_comptabilite = TRUE;
```

Pièces jointes déjà archivées sous la convention `{annee}/{mois}` :

```sql
\c rations_workflow
SELECT COUNT(*) AS documents, MIN(chemin_fichier) AS exemple
FROM piece_jointe;
```

Aucune commande `mvn` n'est nécessaire : ce sprint ne compile rien.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| Les trois lectures (A), (B), (C) sont posées au métier | Fait, dans ces termes |
| Aucune lecture n'est présentée comme acquise | Vérifié |
| M-04 présent dans `docs/dispositifs_provisoires.md` section 3 | Vérifié, sept colonnes servies |
| M-04 présent en section narrative dans `docs/points-en-attente.md` | Vérifié |
| Gel de 6bis.2 écrit, avec son motif | Vérifié |
| Aucun autre sprint gelé sans motif | Vérifié |
| Inventaire d'impact distinguant rupture et vérité documentaire | Vérifié |
| Liste de ce qui n'est **pas** impacté, chaque point vérifié en code | Vérifié |
| Les trois questions non techniques sont adressées à leur interlocuteur | Fait |
| Forme technique présentée comme proposition, jamais comme décision | Vérifié |
| `git status` ne montre aucune modification sous `backend/` | Vérifié |
| `mvn -pl service-workflow test` reste au vert | BUILD SUCCESS, inchangé |

## 10. Points de vigilance

- **La valeur provisoire est le mensuel, et ce n'est pas de la paresse.** C'est la seule lecture qui ne demande aucune modification, donc la seule qui ne fabrique aucune dette si elle se révèle fausse. Basculer le module en hebdomadaire sur une phrase entendue en réunion, puis découvrir que seul le décaissement l'était, coûterait une reprise complète pour rien.
- **Ne pas confondre le décaissement et le cycle documentaire.** Le module ne verse pas d'argent : il produit un état validé et le met à disposition de la comptabilité (CLAUDE.md section 8). Que les agents soient payés chaque semaine n'implique pas mécaniquement que l'état qui le justifie soit hebdomadaire — le processus papier d'origine faisait déjà les deux à des rythmes différents.
- **Le seuil RG-08 est le vrai sujet, pas la maille.** Changer `mois_paiement` en autre chose est un travail long mais mécanique. Décider si le Directeur Réseau doit continuer de voir des dossiers est une décision de gouvernance que personne dans l'équipe technique ne peut prendre. Elle doit partir en premier, parce qu'elle mettra le plus de temps à revenir.
- **Le contrat Kafka n'appartient pas à cette équipe.** `"periode": { "mois", "annee" }` est consommé par le module de comptabilisation. Le modifier unilatéralement casserait un flux de paiement chez quelqu'un d'autre, sans erreur visible de ce côté-ci.
- **Une semaine chevauche deux mois, et le module ne sait pas représenter cela.** `fiche_journaliere` recopie et fige le mois et l'année du processus. Tant que cette question n'est pas tranchée, aucune forme technique ne peut être choisie : c'est elle qui commande le choix, pas l'inverse.
- **Geler 6bis.2 ne veut pas dire geler le projet.** Les sprints 7F.1 à 7F.3, 8.1 et 8.2 ne touchent pas à la période. Les arrêter tous serait une réaction disproportionnée qui coûterait des semaines pour aucun risque évité.
- **L'inventaire doit dire ce qui ne bouge pas.** Un inventaire qui ne liste que les dégâts fait croire que tout est à refaire. Le grain journalier, les grilles, l'identité, l'audit et l'accusé comptable sont indifférents au rythme : c'est la moitié du module, et le dire change la perception du chantier.
- **Ne pas créer de document de décision maintenant.** La décision n'est pas prise ; un fichier dans `docs/decisions/` daté d'aujourd'hui affirmerait le contraire. Il se crée le jour de la réponse, avec la réponse dedans.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Trois lectures distinguées et posées au métier | Fait |
| Point M-04 ouvert aux deux emplacements du registre | Vérifié |
| Valeur provisoire (cycle mensuel) explicitement retenue et motivée | Fait |
| Sous-sprint 6bis.2 gelé, motif écrit | Vérifié |
| Aucun autre sprint gelé | Vérifié |
| Inventaire d'impact figé, rupture et documentation distinguées | Fait |
| Liste du non-impacté vérifiée point par point dans le code | Vérifié |
| Seuil RG-08 porté devant la DRH et la DFT | Fait |
| Contrat Kafka porté devant la DFT, avec M-03 et D-11 | Fait |
| Chevauchement de mois porté devant le métier | Fait |
| Forme technique proposée sous condition, non tranchée | Vérifié |
| Liste des documents à corriger préparée, aucun corrigé | Fait |
| Aucune modification sous `backend/` | Vérifié |
| Aucun document de décision créé prématurément | Vérifié |

## 12. Commit

```bash
git add docs/ "sprint ajustement metier rythme de paiement.md"
git commit -m "ajustement metier: rythme de paiement, point M-04 ouvert et 6bis.2 gele

- Trois lectures de l'hebdomadaire distinguees, valeur provisoire mensuelle
- Point M-04 ouvert au registre, incidence et gel consignes
- Inventaire d'impact fige : ruptures, verites documentaires, non-impacte
- Seuil RG-08, contrat Kafka et chevauchement de mois portes aux interlocuteurs
- Aucune modification de code : la forme de la periode reste a arbitrer

Refs: M-04, W-02, RG-06, RG-08, RG-15, docs/points-en-attente.md"
```

---

**Fin du Sprint d'ajustement métier** — le sous-sprint 6bis.2 reste gelé tant que le registre ne signale pas M-04 comme résolu. Si la réponse est la lecture (A), M-04 se ferme sans aucune modification du module et 6bis.2 reprend tel qu'il était prévu.
