# Résumé Sprint 4.2 — Soumission et pièce jointe signée

> ⚠️ **Lire à la lumière du sprint Maille 1 (10 septembre 2026).** Ce document décrit
> l'état du module **à sa date**, quand la période de paiement était un mois porté par
> le couple `(mois_paiement, annee_paiement)`. Le métier a depuis établi que le cycle
> est **hebdomadaire** (point M-04), et la période est devenue un intervalle de dates
> `(date_debut, date_fin)`. Ce qui est écrit ici reste vrai de son époque et n'est
> **pas** réécrit : un enregistrement daté qu'on corrige après coup cesse d'être un
> enregistrement. Voir
> `docs/decisions/2026-09-09-rythme-de-paiement-et-maille-de-la-periode.md` et
> `docs/resumes-sprints/sprint-maille-1-periode-en-intervalle-de-dates.md`.

**Service :** service-workflow uniquement
**Date :** 1er septembre 2026
**Config :** **Opus / effort élevé sur l'intégralité du sprint**, à la demande de
l'utilisateur — écart au guide §1, qui prescrivait un passage en Sonnet / moyen
pour les étapes 4 et 5 (génération PDF et signature). Aucun changement de modèle
en cours de route.

**Statut :** livré, **tests au vert**, vérification visuelle par l'utilisateur
restant à faire.
`mvn -pl service-workflow test` → **BUILD SUCCESS, 147 tests, 0 échec** (53 au
Sprint 4.1). Backend complet `mvn clean test` → **BUILD SUCCESS, 390 tests, 0
échec**, aucune régression.

---

## Le cœur du sous-sprint

L'agent peut désormais soumettre son état mensuel. Trois choses se produisent :
le système **vérifie la complétude** et refuse en listant les manques, il
**génère la pièce jointe** — un PDF unique par processus — et il y **appose la
signature de l'agent**, première des trois possibles.

Ni validation, ni aiguillage au seuil : ce sont les sous-sprints 4.3 et 4.4.

---

## ⚠️ Les trois points où l'utilisateur a corrigé ou resserré une décision

Ils méritent d'être en tête : ce sont eux qui ont donné leur forme définitive au
sous-sprint.

### 1. Le contrôle de période porte sur les *lignes*, pas sur les *journées*

Ma formulation initiale bloquait sur l'existence d'une **journée** hors période.
**C'était un piège.** Il n'existe **aucun `DELETE /saisie/fiches/{id}`** au
contrat d'API — on supprime une ligne, jamais une fiche. Une fiche vide égarée de
mars aurait donc empêché l'agent de soumettre son mois, **définitivement, sans
recours dans le module**.

Le contrôle porte sur les lignes : l'agent garde `DELETE /saisie/lignes/{id}` pour
se corriger, et une journée vide hors période ne bloque rien. Verrouillé par le
test 9 de `CompletudeServiceTest`.

### 2. Le compteur de signatures ne vaut que s'il constate une écriture

Mon argument initial pour la migration V3 était incomplet. L'utilisateur l'a
rendu tranchant :

> « `etape_workflow` dit "la validation DA a été enregistrée en base".
> `nombre_signatures` ne devient utile que s'il compte les signatures réellement
> écrites dans le PDF, écriture disque qui peut échouer indépendamment de la
> transaction. Si l'incrémentation se fait dans la même transaction que
> `etape_workflow`, la migration V3 ne sert à rien : elle recrée la même faute que
> le total de l'état, deux chemins pour une même vérité. »

D'où l'ordre retenu, et l'engagement pris explicitement avant d'écrire une ligne :
**l'écriture du fichier a lieu hors transaction et avant elle**, avec `fsync`
réel, vérification de la taille sur disque et renommage atomique. Le compteur
n'est incrémenté qu'ensuite.

Cet ordre fait mieux que rendre la divergence détectable : il la rend
**impossible**. Si l'écriture échoue, la transaction ne s'ouvre pas, et
`etape_workflow` n'est pas créée non plus.

### 3. Le login plutôt que le nom d'usage sur la mention de signature

Choix de l'utilisateur. Le login est la clé de rapprochement avec le journal
d'audit — qui n'enregistre lui aussi qu'un login (décision Sprint 3.3) — et il est
stable, là où un nom d'usage se corrige, s'accentue ou se réordonne.

---

## Décisions prises en cours de route

Toutes dans `docs/decisions/2026-09-01-soumission-piece-jointe-et-signature.md`.

| # | Décision | Impact sprints suivants |
|---|---|---|
| 1 | **Quatre contrôles de complétude** arbitrés : état vide, ligne hors période, ligne sans montant, bénéficiaire sans compte. Détail et contrôles écartés dans `docs/controles-completude.md`. | Note à porter dans CLAUDE.md à la clôture du Sprint 4 (K-04). |
| 2 | **Le contrôle de période, délégué ici par la Saisie** (`OuvertureFicheRequest`, Sprint 3.3), est le seul trou réel que ce sprint referme. Enjeu : une ligne de mars dans l'état de septembre échappe au contrôle d'unicité RG-15 de mars, donc devient payable deux fois. | RG-15 (Sprint 6bis) peut s'appuyer sur une période désormais fiable. |
| 3 | **`nombre_signatures` (migration V3) compte les écritures, pas les étapes.** Incrémenté après confirmation d'écriture uniquement. Asymétrie assumée : fichier orphelin plutôt que compteur menteur. | 4.3 et 4.4 : appeler `enregistrerSignatureSupplementaire()` **après** que le stockage a confirmé. |
| 4 | **Signature = mention horodatée + empreinte SHA-256**, pas de cryptographie. Ce que cela prouve et ne prouve pas est écrit noir sur blanc. | Intégration au service de signature de la banque : point ouvert **côté DSI**. |
| 5 | **Champ `manques` ajouté au format d'erreur**, service Workflow uniquement, omis du JSON partout ailleurs. | À porter au contrat d'API §1.4 à la clôture du Sprint 4 (K-03). Le frontend 7F peut rendre une liste. |
| 6 | **Page des visas dédiée, cadres vides réellement vides.** L'estampage ajoute du contenu sans jamais en retirer. `GabaritDocument` est la source unique de la géométrie. | 4.3 et 4.4 estampent dans un cadre déjà réservé, aux coordonnées connues. |
| 7 | **Convention de nommage** `{annee}/{mois}/etat-rations-{unite}-{annee}{mois}-p{id}.pdf`, chemin **relatif**. L'identifiant du processus évite la collision entre un NORMAL et un COMPLEMENTAIRE de même période. | Sprint 6bis : les états complémentaires ne s'écraseront pas. |
| 8 | **Times-Roman au lieu de Bookman Old Style** — question de licence Monotype, pas de technique. Reste de la charte tenu. | Point ouvert K-05 pour la DSI. |
| 9 | **`GET /identite/moi` imposé par le schéma** (`id_acteur NOT NULL`). Porte la soumission à trois appels sortants, 15 s au pire cas : accepté, geste mensuel. | 4.3 et 4.4 auront le même besoin, même patron. |
| 10 | **Deux refus de montant** : total nul → `503` (jamais un zéro) ; total débordant l'`INTEGER` → `503` (jamais une troncature, qui rendrait le montant négatif). | 4.3 : l'aiguillage RG-08 s'appuie sur un montant dont la justesse est garantie. |
| 11 | **Le bloc transactionnel dans une classe à part** — `@Transactional` en auto-invocation n'a aucun effet, et rien ne le signalerait. | Patron à reprendre partout où un appel réseau précède une écriture. |

---

## Ce qui a été fait — critères de validation du guide (§11)

| Critère | Statut | Détail |
|---|---|---|
| Contrôles de complétude arbitrés et documentés | ✅ | Quatre contrôles, arbitrés avec l'utilisateur avant tout codage ; `docs/controles-completude.md` porte aussi **six contrôles écartés** et le motif de chacun |
| Manques listés en cas de refus | ✅ | Champ `manques` structuré, `422 ETAT_INCOMPLET` ; tests 9 de `SoumissionServiceTest` et 22 de `ProcessusControllerIT` |
| Nature de la signature tranchée | ✅ | Mention horodatée + empreinte SHA-256 ; ce qu'elle ne prouve pas est écrit dans le code, la décision et `points-en-attente.md` |
| Convention de nommage du fichier validée | ✅ | Validée avec l'utilisateur ; tests 14, 15 et 16 de `DocumentServiceTest` |
| PDF généré conforme à la charte | ⚠️ | Généré et relu (2 pages, 13 352 octets). Charte tenue **sauf la police** (écart assumé, décision 7) et les cellules de tableau à 10 pt. **Conformité visuelle à confirmer par l'utilisateur** |
| Pièce jointe unique par processus | ✅ | Contrôle applicatif + contrainte `id_processus UNIQUE` + refus d'écraser le fichier ; tests 18, 19, 20 de `SoumissionServiceTest` |
| Montant total cohérent avec l'état consolidé | ✅ | **Recopié, jamais réadditionné** ; test 5 le vérifie avec un total volontairement en désaccord avec la somme des lignes |
| Étape de soumission créée | ✅ | `SOUMISSION_AGENT`, `VALIDEE`, ordre 1, acteur et signature ; test 1 |
| Dix tests passants | ✅ | **94 tests écrits** pour ce sous-sprint, contre dix demandés |
| Aucun endpoint de validation créé | ✅ | Test 17 de l'IT : `/validation` et `/retour` répondent toujours 404 |

## Tests et vérifications du guide (§9)

| Vérification | Résultat |
|---|---|
| `mvn -pl service-workflow test` | **BUILD SUCCESS, 147 tests** |
| Dix tests du sous-sprint | **94**, tous passants |
| Soumission nominale | `EN_ATTENTE_DA`, montant enregistré, pièce jointe signée, étape créée |
| État incomplet | Refus `422` avec la **liste** des manques, chacun nommant le geste attendu |
| Montant total | Égal à celui de l'état consolidé, recopié sans recalcul |
| Pièce jointe | Une seule par processus, vérifié contre la vraie base |
| Compteur de signatures | Vaut 1 après soumission, et seulement après écriture confirmée |
| Seconde soumission | Refusée, aucune seconde pièce jointe, **aucun second fichier écrit** |
| Fichier PDF | Généré, valide (`%PDF-1.7`), 2 pages, relu par extraction de texte |
| Étape SOUMISSION_AGENT | Créée avec sa date, son acteur et sa signature |

---

## Les 94 tests du sous-sprint

**`CompletudeServiceTest` — 21 tests** (sans base, sans réseau)

| # | Objet |
|---|---|
| 1 | Un état complet ne produit aucun manque |
| 2-6 | État vide : aucune journée, compteur absent, journées sans ligne, **compteur en désaccord avec le détail**, court-circuit des trois autres contrôles |
| 7-11 | Période : autre mois, **même mois autre année**, **le piège de la journée vide** (ne bloque pas), journée sans date, journée fautive nommée une seule fois |
| 12-14 | Montant absent, à zéro, négatif |
| 15-17 | Compte courant absent, fait d'espaces, bénéficiaire entièrement absent |
| 18 | Trois manques cumulés, dans un ordre stable |
| 19 | **200 lignes fautives → un seul manque**, avec « et 195 autre(s) » |
| 20 | Réponse tronquée : refus propre, jamais une `NullPointerException` |
| 21 | Le vocabulaire compte **exactement** les quatre contrôles arbitrés |

**`DocumentServiceTest` — 16 tests** (relecture du PDF produit)

| # | Objet |
|---|---|
| 1 | PDF valide, et **un exemplaire déposé dans `target/` pour relecture visuelle** |
| 2 | Au moins deux pages : le détail, puis la page des visas |
| 3-5 | En-tête, détail par journée, sous-totaux et total |
| 6 | **Le total imprimé est celui annoncé, jamais un recalcul** (99 000 contre 8 000 de lignes) |
| 7 | Une journée sans ligne est imprimée, pas escamotée |
| 8 | Un seul cadre porte une mention ; **c'est ce test qui a trouvé le défaut d'horodatage** |
| 9-10 | Trois cadres même sans signature ; un cadre vide ne porte **aucun** texte |
| 11 | Liste de signatures nulle tolérée |
| 12-13 | Cadres disjoints, dans la page ; ordre conforme au circuit |
| 14-16 | Nommage : format, **absence de collision**, tri chronologique |

**`SignatureServiceTest` — 13 tests** (stockage réel sur répertoire temporaire)

| # | Objet |
|---|---|
| 1-3 | Première signature : document écrit, mention imprimée, **empreinte égale à celle du fichier** |
| 4 | Seconde création refusée, document signé intact |
| 5 | **LE POINT CENTRAL : enrichir conserve la signature précédente** |
| 6 | Les trois signatures du circuit coexistent |
| 7 | Le détail de l'état survit intact à l'enrichissement |
| 8 | Aucune page ajoutée : on estampe, on ne recompose pas |
| 9-10 | L'empreinte suit le fichier courant ; **une empreinte antérieure n'est plus opposable** |
| 11-13 | Document absent, au-delà de trois signatures, compteur et date de modification |

**`StockageDocumentsFichierTest` — 13 tests**

| # | Objet |
|---|---|
| 1-4 | Écriture nominale, sous-dossiers, **aucun temporaire résiduel**, taille constatée sur disque |
| 5-6 | **Refus d'écraser un document existant**, sans résidu |
| 7-8 | Remplacement complet, création si absent |
| 9-11 | Document vide refusé, chemin hors racine refusé, chemin vide refusé |
| 12-13 | Relecture, existence |

**`SoumissionServiceTest` — 22 tests** (vraie base `rations_workflow` + stockage réel)

| # | Objet |
|---|---|
| 1-4 | Soumission nominale, écritures réellement persistées, **fichier réellement sur disque**, empreinte de l'étape égale à celle du fichier |
| 5-7 | Montant recopié ; **total absent refusé** ; **débordement refusé plutôt que tronqué** |
| 8-9 | État vide, état incomplet : manques listés, **rien d'écrit, aucun fichier** |
| 10-13 | Déjà soumis, clôturé, retourné, inexistant |
| 14-17 | Hors portée, Identité muet, profil absent, Saisie muet |
| 18-20 | Une seule pièce jointe ; seconde soumission refusée **avant génération** ; pièce jointe orpheline bloquante |
| 21-22 | Soumission tracée avec `idUtilisateur` et empreinte ; **un refus ne laisse aucune trace de succès** |

**`ProcessusControllerIT` — 9 tests ajoutés** (26 au total)

| # | Objet |
|---|---|
| 17 | **Révisé** : `/validation` et `/retour` toujours 404, `/soumission` en sort |
| 18-19 | Soumission nominale ; jeton relayé tel quel |
| 20-21 | `CHEF_UNITE_DA` → 403 publié en audit ; sans jeton → 401 **sans audit** |
| 22-23 | `422 ETAT_INCOMPLET` avec la liste ; **champ `manques` absent des autres erreurs** |
| 24-26 | Déjà soumis 422, pièce jointe existante 409, document non produit **500** |

---

## Deux défauts trouvés par les tests, et corrigés

### 1. Un horodatage de signature perdu en silence — défaut réel de production

À 90 pt de hauteur, le cadre de visa **abandonnait sans bruit la ligne
d'horodatage** : un canevas iText posé sur un rectangle fixe tronque ce qui n'y
tient pas, sans lever d'erreur. RG-09 exige pourtant une signature *horodatée*.

Le défaut n'a été vu que parce que le test **relit le PDF produit** au lieu de
vérifier les appels à iText. Un test d'interaction serait passé au vert sur un
document amputé. Hauteur portée à 110 pt ; le test 8 garde cette valeur, et le
javadoc de `VISA_HAUTEUR` explique pourquoi il ne faut pas la réduire.

### 2. Des jeux d'essai incohérents — défaut de test, révélateur d'un contrôle qui fonctionne

`SoumissionServiceTest` fabriquait des journées de janvier pour des processus
d'autres mois. **Le contrôle de période les a toutes refusées, à juste titre** :
13 tests en échec, tous pour la bonne raison. Les fixtures datent désormais les
journées d'après la période du processus.

---

## Hygiène de build — un point à retenir

`mvn test` sur le réacteur complet a rapporté deux erreurs dans `service-saisie`
au format « Unresolved compilation problem » — celui du compilateur **Eclipse**,
pas de `javac`. L'extension Java de l'IDE avait produit des `.class` porteurs
d'une erreur de généricité, que Maven a réutilisés (« Nothing to compile »).

`mvn -pl service-saisie clean test` → **73 tests, 0 échec**. Aucun défaut réel.

**À ranger à côté de la leçon du Sprint 2.3 sur `~/.m2` :** devant une erreur de
compilation inattendue, **relancer avec `clean` avant de chercher la cause dans le
code**.

---

## Fichiers

**Créés — service-workflow (30 fichiers de production, 3 de test)**

| Chemin | Objet |
|---|---|
| `domaine/CodeManqueEnum.java` | Vocabulaire fermé des quatre manques |
| `domaine/PieceJointe.java` | Entité ; `nombre_signatures` et son invariant |
| `domaine/exception/{DocumentNonProduit,EtatIncomplet,PieceJointeExistante}Exception.java` | Trois refus |
| `application/CompletudeService.java` | Les quatre contrôles |
| `application/{ManqueCompletude,ResultatCompletude}.java` | Le verdict, jamais un booléen |
| `application/DocumentService.java` | Génération PDF par iText 8 |
| `application/GabaritDocument.java` | **Source unique de la charte et de la géométrie des visas** |
| `application/RedacteurVisa.java` | Seul endroit où une mention est dessinée |
| `application/{MentionSignature,ActeurSignataire,ResultatSignature}.java` | Types de la signature |
| `application/SignatureService.java` | RG-09 : créer puis **enrichir**, jamais régénérer |
| `application/{StockageDocuments,DocumentEcrit}.java` | Port d'écriture confirmée et sa preuve |
| `application/NommageDocument.java` | Convention de nommage |
| `application/{ProfilClient,ResultatProfil}.java` | Port `GET /identite/moi` |
| `application/SoumissionService.java` | Orchestration, hors transaction |
| `application/{EnregistrementSoumission,ResultatSoumission}.java` | **Le seul bloc transactionnel** |
| `infrastructure/PieceJointeRepository.java` | Accès, `existsByIdProcessus` |
| `infrastructure/identite/{ProfilHttpClient,ProfilReponse}.java` | Adaptateur `/identite/moi` |
| `infrastructure/stockage/StockageDocumentsFichier.java` | **fsync, vérification de taille, renommage atomique** |
| `api/dto/SoumissionResponse.java` | Les trois effets du geste |
| `resources/db/migration/V3__piece_jointe_ajout_nombre_signatures.sql` | Migration additive |
| `resources/assets/logo afriland.png` | Logo, fourni par l'utilisateur |
| `test/…/{CompletudeService,DocumentService,SignatureService,SoumissionService}Test.java` | 72 tests |
| `test/…/infrastructure/stockage/StockageDocumentsFichierTest.java` | 13 tests |

**Modifiés**

| Chemin | Modification |
|---|---|
| `service-workflow/pom.xml` | iText 8 : artefacts `kernel` et `layout` |
| `api/ErreurApiDto.java` | Champ `manques`, `@JsonInclude(NON_EMPTY)` |
| `api/GestionnaireErreursApi.java` | Trois gestionnaires, plus un journal |
| `api/ProcessusController.java` | `POST /processus/{id}/soumission` |
| `domaine/ProcessusMensuel.java` | `reporterMontantTotal(int)` — report, jamais calcul |
| `domaine/EtapeWorkflow.java` | `validerAvecSignature(String)` |
| `resources/application-dev.yml` | `app.pieces-jointes.repertoire` |
| `test/…/ProcessusControllerIT.java` | Test 17 révisé, 9 tests ajoutés |
| `CLAUDE.md` | §4 (`piece_jointe`), §11 (endpoints, codes, champ `manques`), §17 (12 décisions) |
| `docs/points-en-attente.md` | Point DSI sur le service de signature de la banque |

**Créés — documentation**

| Chemin | Objet |
|---|---|
| `docs/controles-completude.md` | Note de l'étape 3 : retenus, écartés, motifs |
| `docs/decisions/2026-09-01-soumission-piece-jointe-et-signature.md` | Les treize décisions |

---

## Points ouverts

| # | Point | Pour qui |
|---|---|---|
| **K-01** | `StatutFicheEnum.ENREGISTREE` n'est **jamais atteint** : aucun endpoint du Sprint 3 ne fait passer une fiche de `EN_SAISIE` à `ENREGISTREE`. Donner la transition, ou retirer le statut. Laisser les deux valeurs sans transition est le pire des trois états. | Sprint 4 ou 6 |
| **K-02** | Absence de `DELETE /saisie/fiches/{id}`. Tant qu'elle dure, un contrôle sur les journées vides reste inapplicable. | Métier / Sprint 7F |
| **K-03** | Porter le champ `manques` au contrat d'API §1.4 et à CLAUDE.md §11. | Clôture Sprint 4 |
| **K-04** | Porter `docs/controles-completude.md` dans CLAUDE.md. | Clôture Sprint 4 |
| **K-05** | Police du document : Times-Roman au lieu de Bookman Old Style. Valider une fonte libre de la famille, ou entériner l'écart. | DSI |
| **Signature** | Intégration au service de signature électronique de la banque (`points-en-attente.md`). | DSI |
| **C-04** | *(hérité du 4.1, non traité)* Fiches antérieures à la migration V3 côté Saisie, `code_unite` nul, non recoupables. | Nettoyage de données |
| **W-01** | *(hérité)* `HttpStatus.UNPROCESSABLE_ENTITY` déprécié depuis Spring 7, sur trois services. | Dette technique |
| **W-02** | *(hérité)* Rien n'interdit d'ouvrir un état sur une période future. | Métier |
| **Stockage** | `app.pieces-jointes.repertoire` doit être un volume **persistant et sauvegardé** en production : il porte les états signés. À arrêter avec la DSI, au même titre que les Secrets Kubernetes. | DSI |

---

## Suite

**Sprint 4.3 — validation et aiguillage au seuil.** La machine à états, la pièce
jointe et le mécanisme d'enrichissement sont en place ; 4.3 lit
`SEUIL_AIGUILLAGE_DR` dans `parametre_systeme` et choisit entre les deux
transitions déjà exposées depuis `EN_ATTENTE_DA`, sans rien ajouter à la machine.
La signature du Chef d'Unité passe par `enrichirEtSigner`, **jamais** par
`creerEtSigner`.
