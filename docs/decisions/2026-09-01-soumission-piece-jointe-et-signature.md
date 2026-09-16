# Soumission, pièce jointe unique et nature de la signature

> ⚠️ **Lire à la lumière du sprint Maille 1 (10 septembre 2026).** Ce document décrit
> l'état du module **à sa date**, quand la période de paiement était un mois porté par
> le couple `(mois_paiement, annee_paiement)`. Le métier a depuis établi que le cycle
> est **hebdomadaire** (point M-04), et la période est devenue un intervalle de dates
> `(date_debut, date_fin)`. Ce qui est écrit ici reste vrai de son époque et n'est
> **pas** réécrit : un enregistrement daté qu'on corrige après coup cesse d'être un
> enregistrement. Voir
> `docs/decisions/2026-09-09-rythme-de-paiement-et-maille-de-la-periode.md` et
> `docs/resumes-sprints/sprint-maille-1-periode-en-intervalle-de-dates.md`.

**Date :** 2026-09-01 — Sprint 4.2
**Statut :** adoptée
**Portée :** service Workflow

---

## 1. Décision 1 — ce qu'est un « état complet » : quatre contrôles

**Tranchée avec l'utilisateur, avant toute ligne de code.**

Le cahier des charges dit que « le système vérifie la complétude des informations »
sans dire lesquelles. La liste retenue, son détail et les contrôles écartés vivent
dans **`docs/controles-completude.md`** — note que l'étape 3 du guide demande, et
qui doit rejoindre CLAUDE.md à la clôture du Sprint 4.

En résumé : `ETAT_VIDE`, `LIGNE_HORS_PERIODE`, `LIGNE_SANS_MONTANT`,
`BENEFICIAIRE_SANS_COMPTE`.

### 1.1 La correction qui compte : le contrôle porte sur les *lignes*, pas les *journées*

Ma formulation initiale bloquait sur l'existence d'une **journée** hors période.
**C'était un piège**, relevé et confirmé par l'utilisateur.

Il n'existe **aucun `DELETE /saisie/fiches/{id}`** au contrat d'API : les cinq
endpoints de Saisie sont `POST /fiches`, `GET /fiches/{id}/lignes`, et
`POST` / `PUT` / `DELETE` sur `/lignes`. **On supprime une ligne, jamais une
fiche.** Bloquer sur une journée vide égarée aurait donc enfermé l'agent
définitivement : plus aucune soumission possible pour ce mois, sans recours dans
le module.

En portant le contrôle sur les lignes, l'agent conserve `DELETE /saisie/lignes/{id}`
pour se corriger, et une journée vide hors période ne bloque rien — elle ne porte
aucun montant. Verrouillé par le test 9 de `CompletudeServiceTest`.

### 1.2 Pourquoi le contrôle de période est le vrai apport du sous-sprint

`OuvertureFicheRequest` (Sprint 3.3) dit explicitement que « savoir si la journée
tombe dans le mois du processus, c'est une question qui appartient au processus,
donc au service Workflow ». **La Saisie s'en est dessaisie par écrit. Si Workflow
ne le prend pas, il n'est implémenté nulle part.**

L'enjeu dépasse l'erreur de période : RG-15 (Sprint 6bis) vérifie l'unicité
inter-états **à unité et période égales**. Une ligne de mars glissée dans l'état de
septembre échappe donc au contrôle d'unicité de mars, et la même journée devient
payable deux fois. C'est un vecteur de double paiement que rien d'autre ne referme.

---

## 2. Décision 2 — `nombre_signatures` compte les écritures, pas les étapes

**Tranchée avec l'utilisateur, qui a corrigé mon argument.**

La colonne n'existe pas au dictionnaire. Migration **V3** additive.

### 2.1 Pourquoi l'ajouter, alors que le Sprint 4.1 avait refusé cinq colonnes

Le motif du refus au 4.1 était que ces colonnes **dupliquaient** une information
déjà portée ailleurs (le journal d'audit sait qui a déclenché et quand). Ici c'est
l'inverse : ce compteur porte une information que **rien d'autre ne porte**.

### 2.2 La condition posée par l'utilisateur, et qui fait toute la valeur de la colonne

> « L'argument avancé n'est valable que si le compteur mesure autre chose que
> `etape_workflow`. Concrètement : `etape_workflow` dit "la validation DA a été
> enregistrée en base". `nombre_signatures` ne devient utile que s'il compte les
> signatures réellement écrites dans le PDF par iText, écriture disque qui peut
> échouer indépendamment de la transaction. Si l'incrémentation se fait dans la
> même transaction que `etape_workflow`, sans vérifier que l'écriture disque a
> réussi, la migration V3 ne sert à rien. »

**C'est exact, et c'est la règle retenue.** L'ordre est :

```
  HORS TRANSACTION, AVANT ELLE
    generer le PDF en memoire
    ecrire dans un fichier temporaire
    flush() puis FileChannel.force(true)      <- fsync reel
    verifier : taille sur disque == taille attendue
    Files.move(temp -> definitif, ATOMIC_MOVE)
    echec ici -> ON S'ARRETE. La transaction ne s'ouvre pas.

  TRANSACTION
    montant, transitions, etape_workflow, piece_jointe (nombre_signatures = 1)
```

**Ce que cet ordre change.** Le scénario décrit par l'utilisateur — base commitée à
2 signatures, PDF réellement à 1 — devient **impossible** plutôt que simplement
détectable : si l'écriture échoue, `etape_workflow` n'est pas créée non plus.

**L'asymétrie inverse est assumée.** Fichier écrit puis transaction en échec laisse
un **fichier orphelin** que rien ne référence. C'est la préférence voulue, la même
qu'en matière d'audit (Sprint 1.3) : mieux vaut une trace de trop qui ne trompe
personne qu'une affirmation fausse en base.

**Ce que la colonne garde comme valeur.** Elle enregistre ce que le fichier
**contenait au moment de son écriture confirmée**. Si le fichier est plus tard
remplacé, tronqué ou corrompu, elle reste la référence contre laquelle le
constater. `etape_workflow` ne pourrait pas jouer ce rôle : il ne parle que du
circuit.

`nom_fichier` **n'est pas ajouté** : `chemin_fichier` existe et le nom en est le
dernier segment.

### 2.3 Limite connue, non masquée

Java n'expose aucun moyen de forcer sur disque l'**entrée de répertoire** après le
renommage. Le *contenu* du fichier est bien synchronisé ; la durabilité du
renommage lui-même, en cas de coupure d'alimentation dans la seconde qui suit,
dépend du système de fichiers. Le risque résiduel est un fichier absent alors que
la base le référence — une panne franche, pas une donnée fausse.

---

## 3. Décision 3 — nature de la signature : mention horodatée + empreinte SHA-256

**Tranchée avec l'utilisateur.** Les spécifications disent « signature numérique
**automatique** » sans préciser, et ce mot est le seul indice qu'elles donnent.

### 3.1 Ce qui est retenu

| Où | Quoi |
|---|---|
| Sur le document | Une mention dans le cadre de visa : **login**, rôle figé au moment de l'acte, date et heure |
| En base, `etape_workflow.signature_numerique` | `SHA-256:<64 hexa>` — empreinte du **fichier** juste après cette signature |

Le champ `signature_numerique` (`VARCHAR(255)`) existait depuis la migration V1 et
n'avait **aucun usage**. Il en a un désormais.

**L'identité portée est le login, pas le nom d'usage** (choix de l'utilisateur).
Le login est la clé de rapprochement avec le journal d'audit, qui n'enregistre lui
aussi qu'un login (décision Sprint 3.3), et il est stable là où un nom d'usage se
corrige, s'accentue ou se réordonne.

### 3.2 Ce que l'empreinte prouve, et ce qu'elle ne prouve pas

**Elle prouve** que le document archivé n'a pas été altéré depuis la dernière
signature : on recalcule l'empreinte du fichier, on compare.

**Elle ne prouve pas :**

- ce n'est **pas** une signature électronique au sens juridique — aucune clé,
  aucun certificat, aucune autorité ;
- l'empreinte vit **dans la même base** que le reste : elle ne protège pas de
  quelqu'un qui peut y écrire. Elle détecte une altération du fichier, pas une
  falsification coordonnée.

### 3.3 Conséquence à connaître : seule la dernière empreinte est vérifiable

Le fichier change à chaque enrichissement. Les empreintes intermédiaires
documentent ce qu'était le document à leur étape **sans pouvoir être recontrôlées**
— l'état intermédiaire n'existe plus. Après clôture, c'est l'empreinte finale qui
scelle le justificatif archivé.

Le test 10 de `SignatureServiceTest` verrouille ce point, pour que personne ne
construise un contrôle sur les empreintes intermédiaires en les croyant opposables.

### 3.4 Pourquoi PAdES a été écarté ici

La variante *clé unique du module* produirait un **cachet serveur** : il prouverait
que le module a scellé le document, pas qu'une personne l'a signé — alors que RG-09
parle d'une signature par validation. La variante *clé par acteur* est la seule qui
honore vraiment RG-09, mais elle suppose une PKI (certificat par agent, HSM ou
carte, révocation) dont rien n'existe ni n'est mentionné, **et le mot
« automatique » des spécifications l'exclut** : une signature personnelle suppose
un geste de la personne.

Point ouvert consigné **côté DSI** dans `docs/points-en-attente.md`, à la demande
de l'utilisateur. Le coût du report est faible : la géométrie de la page des visas,
la convention de nommage et la discipline d'écriture confirmée ne seraient pas à
reprendre. C'est la chaîne de confiance qui manque, pas le code.

---

## 4. Décision 4 — le champ `manques` ajouté au format d'erreur

**Tranchée avec l'utilisateur.**

CT-13 exige que le refus **liste** les manques, et l'étape 2 du guide précise
« pour que l'interface les affiche à l'agent ». Le format d'erreur uniforme n'a que
cinq champs.

`ErreurApiDto` gagne un sixième champ **facultatif**, annoté
`@JsonInclude(NON_EMPTY)` : les cinq champs du contrat §1.4 restent présents, au
même nom et au même type, et `manques` est **absent du JSON** partout ailleurs.
L'ajout est purement additif — aucun consommateur existant ne casse (test 23 de
`ProcessusControllerIT`).

**Pourquoi pas une concaténation dans `message`.** Elle se lit à l'œil mais ne se
rend pas en liste : le frontend (Sprint 7F) ne pourrait ni distinguer les manques,
ni renvoyer l'agent vers la journée fautive, sans découper de la prose.

**Ajouté au seul service Workflow** : les trois autres n'ont rien à y mettre. À
porter au contrat d'API et à CLAUDE.md §11 à la clôture du Sprint 4 (point K-03).

### 4.1 Un manque par contrôle, jamais un par ligne fautive

Chaque contrôle produit **au plus un élément**, qui agrège et nomme au plus cinq
exemples, le reste annoncé en nombre. La liste rendue en compte donc au plus
quatre. Le nombre total reste annoncé en tête de chaque message : la troncature ne
cache jamais l'ampleur (test 19 de `CompletudeServiceTest`, sur 200 lignes
fautives).

---

## 5. Décision 5 — la page des visas est une page dédiée, et les cadres vides le sont vraiment

**Contrainte technique découverte en écrivant l'étape 5.**

L'estampage d'un PDF **ajoute** du contenu : il n'en retire jamais. Deux
conséquences de conception :

1. **Les trois cadres vivent sur une page dédiée, toujours la dernière.** Une zone
   placée à la suite du détail se trouverait à une ordonnée différente selon le
   nombre de journées saisies ; l'estampage du Sprint 4.3 devrait alors *deviner*
   où écrire. Sur une page dédiée, les coordonnées sont les mêmes pour tous les
   états du module.
2. **Un cadre non atteint par le circuit reste rigoureusement vide.** Une première
   version imprimait « En attente » ; cette mention serait restée lisible **sous**
   la signature venue s'inscrire par-dessus, et les deux se seraient lues ensemble.
   C'est le paragraphe d'introduction de la page qui dit ce que signifie un cadre
   vide. Verrouillé par le test 10 de `DocumentServiceTest`.

`GabaritDocument` est la **source unique** de la géométrie, partagée par la
création et par l'estampage. Si les deux divergeaient d'un point, la signature du
Chef d'Unité s'inscrirait à côté de son cadre — ou par-dessus celle de l'agent.

### 5.1 Un défaut réel trouvé par un test qui relit le PDF

À 90 pt de hauteur de cadre, **la ligne d'horodatage disparaissait silencieusement
du document** : un canevas iText posé sur un rectangle fixe abandonne sans bruit ce
qui n'y tient pas. RG-09 exige pourtant une signature *horodatée*.

Le défaut n'a été vu que parce que le test **relit le PDF produit** au lieu de
vérifier les appels à iText. Un test d'interaction serait passé au vert sur un
document amputé. Hauteur portée à 110 pt, et le test 8 garde cette valeur.

---

## 6. Décision 6 — convention de nommage et stockage

**Validée avec l'utilisateur.**

```
  {racine}/2026/09/etat-rations-00002-202609-p109.pdf
  en base : 2026/09/etat-rations-00002-202609-p109.pdf   (chemin RELATIF)
```

| Morceau | Raison |
|---|---|
| `00002` | code **unité** (ligne de débit), jamais le code agence |
| `202609` | année **puis** mois : le tri alphabétique du répertoire devient chronologique |
| `p109` | identifiant du processus — **sans lui, deux états entreraient en collision** |

**Le dernier point n'est pas facultatif.** CLAUDE.md §4 n'impose l'unicité que sur
le processus NORMAL : plusieurs COMPLEMENTAIRE sont possibles sur un même couple
unité / période (Sprint 6bis). Deux états produiraient alors le **même** nom de
fichier, et le renommage écraserait le PDF signé du premier. La contrainte
`id_processus UNIQUE` de `piece_jointe` ne protégerait pas de cela : elle porte sur
des identifiants qui diffèrent — deux lignes distinctes pointeraient vers un seul
fichier. Test 15 de `DocumentServiceTest`.

**Chemin relatif, jamais absolu.** La racine est une donnée de configuration
(`app.pieces-jointes.repertoire`) : une racine absolue en base rendrait toute la
table fausse le jour où le volume change de point de montage entre le poste de
développement et Kubernetes.

---

## 7. Décision 7 — la police du document : écart assumé à la charte

**Tranchée avec l'utilisateur.**

La charte §8.2 prescrit **Bookman Old Style**. Le document utilise
**Times-Roman**, l'une des quatorze polices natives du format PDF.

**Motif.** Bookman Old Style est une fonte Monotype licenciée avec Windows et
Office ; l'embarquer dans un dépôt puis dans une image Docker déployée sur
Kubernetes serait une *redistribution*, ce qui est une question juridique et non
technique. Elle n'est de surcroît **pas installée** sur le poste de développement
(vérifié : absente de `C:\Windows\Fonts`). Times-Roman n'est pas embarquée du tout
— elle est garantie par tout lecteur PDF — donc sans octet ajouté, sans licence à
valider, sans dépendance réseau au build.

**Tout le reste de la charte est tenu** : A4, marges de 2 cm, noir `1A1A1A`, rouge
`E30613` réservé au sous-titre, logo centré en tête de la **première page
uniquement**, aucun en-tête sur les pages, pied de page réduit au numéro en gris
`666666` 9 pt, tableaux à filets fins gris sans aucun aplat.

**Une seconde lecture assumée :** le corps 12 pt est tenu pour la prose, mais les
**cellules de tableau descendent à 10 pt**. À 12 pt, un tableau de six colonnes sur
482 pt de largeur utile ferait déborder un nom de bénéficiaire sur trois lignes.

Le retour à Bookman, si la DSI valide une fonte libre de la famille, est un
changement d'une ligne (point ouvert **K-05**).

---

## 8. Décision 8 — l'appel à `GET /identite/moi` est imposé, pas choisi

`etape_workflow.id_acteur` est `BIGINT NOT NULL`, et `GET /identite/habilitation`
— déjà appelé pour la portée d'accès — ne rend qu'un `login`, jamais l'identifiant
local. **Sans cet appel, aucune étape de workflow ne peut être écrite.** C'est
exactement le motif du Sprint 2.2 pour `id_createur` sur les grilles.

**Conséquence assumée : la soumission enchaîne trois appels sortants** —
habilitation, profil, consolidation. Avec les délais du Sprint 3.2 (2 s / 3 s,
aucun réessai), le pire cas atteint **quinze secondes**. C'est accepté : la
soumission est un geste **mensuel**, pas un geste par ligne. Le budget de trois
secondes du Sprint 3.2 visait la saisie, où chaque ligne paie le prix.

**Effet secondaire heureux :** `SOUMISSION_PROCESSUS` est le **premier événement
d'audit de ce service à porter un `idUtilisateur`**. Le déclenchement du Sprint 4.1
le laissait nul, faute d'identifiant disponible.

---

## 9. Décision 9 — où la transaction commence, et pourquoi elle est dans une classe à part

`SoumissionService` **n'est pas transactionnel** : il enchaîne trois appels réseau
et une écriture disque. Le seul bloc transactionnel vit dans
`EnregistrementSoumission`, et ne contient que des écritures en base.

Deux raisons de la classe séparée, dans cet ordre :

1. **Technique.** Spring pose ses transactions par mandataire :
   `@Transactional` sur une méthode appelée depuis la même classe n'a **aucun
   effet**. La transaction serait absente, et rien ne le signalerait.
2. **Lisible.** La frontière devient visible dans la structure du code, sans avoir
   à lire une annotation.

**Une garde de concurrence y relit le statut.** Entre le contrôle de
`SoumissionService` et le commit, trois appels réseau et une écriture disque se
sont écoulés : une seconde requête a eu tout le temps de soumettre le même état.

---

## 10. Décision 10 — les deux refus de montant

| Cas | Décision | Motif |
|---|---|---|
| `montantTotalFcfa` **nul** | Refus `503`, jamais un zéro | Les deux commanderaient le même aiguillage (« sous le seuil »), l'un à juste titre, l'autre par accident (décision Sprint 4.1 §6) |
| `montantTotalFcfa` **> `Integer.MAX_VALUE`** | Refus `503`, jamais une troncature | `montant_total` est un `INTEGER` en base, le service Saisie rend un `long`. Une conversion silencieuse rendrait le total **négatif**, et ce négatif commanderait ensuite l'aiguillage |

Le second cas est improbable ; sa conséquence ne l'est pas. Tests 6 et 7 de
`SoumissionServiceTest`.

---

## 11. Codes d'erreur ajoutés au contrat

| Code | Statut | Cas |
|---|---|---|
| `ETAT_INCOMPLET` | `422` | Complétude en échec. **Seul code à renseigner le champ `manques`** |
| `PIECE_JOINTE_EXISTANTE` | `409` | Un document existe déjà pour ce processus |
| `DOCUMENT_NON_PRODUIT` | `500` | Composition ou écriture du PDF en échec |

`422` pour le premier : rien n'est dupliqué, c'est une règle de gestion qui refuse
(distinction du Sprint 2.3). `409` pour le deuxième : quelque chose est bien
dupliqué. `500` pour le troisième : ce n'est ni une maladresse de l'agent ni une
règle de gestion, c'est une défaillance du serveur — le présenter en `422`
enverrait l'agent corriger une saisie qui n'a rien de faux.

---

## 12. Point d'hygiène de build relevé à la vérification

`mvn test` sur le réacteur complet a rapporté **deux erreurs dans
`service-saisie`** au format « Unresolved compilation problem » — celui du
compilateur **Eclipse**, pas de `javac`. L'extension Java de l'IDE avait produit
des `.class` porteurs d'une erreur de généricité, que Maven a réutilisés tels quels
(« Nothing to compile — all classes are up to date »).

`mvn -pl service-saisie clean test` passe : **73 tests, 0 échec**. Aucun défaut
réel, aucune régression.

**Leçon, à ranger à côté de celle du Sprint 2.3 sur `~/.m2` :** un `mvn test` sans
`clean` peut rapporter des erreurs qui n'existent que dans les artefacts de l'IDE.
Devant une erreur de compilation inattendue, **relancer avec `clean` avant de
chercher la cause dans le code**.

---

## 13. Ce qui est attendu des sous-sprints suivants

- **4.3 (validation et aiguillage)** : lire `SEUIL_AIGUILLAGE_DR` dans
  `parametre_systeme` et choisir entre les deux transitions déjà exposées depuis
  `EN_ATTENTE_DA`. Appeler `SignatureService.enrichirEtSigner` — **jamais**
  `creerEtSigner` — pour la signature du Chef d'Unité, puis
  `PieceJointe.enregistrerSignatureSupplementaire()` **après** que le stockage a
  confirmé l'écriture.
- **4.4 (retour)** : `TransitionProcessus.reprendreParAgent` ramène un état
  `RETOURNE` en `EN_COURS_SAISIE`. C'est ce geste qui rend un état retourné à
  nouveau soumissible — la soumission n'accepte que `EN_COURS_SAISIE`, et son
  message le dit déjà à l'agent.
- **Clôture du Sprint 4** : porter `docs/controles-completude.md` dans CLAUDE.md,
  et le champ `manques` au contrat d'API §1.4 (points K-03 et K-04).
