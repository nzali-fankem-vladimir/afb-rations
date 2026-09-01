# Second niveau, retour motivé et reprise : trois décisions du Sprint 4.4

**Sprint 4.4** — service Workflow — décisions prises avec l'utilisateur avant codage.

---

## 1. Le niveau de validation est désigné par le STATUT, pas par le rôle

Un seul endpoint sert les deux visas (`POST /processus/{id}/validation`, contrat d'API
§5). Il fallait décider ce qui commande.

**Décision : le statut du dossier désigne le niveau, le rôle de l'appelant est ensuite
vérifié contre lui.**

```
EN_ATTENTE_DA  ->  niveau CHEF_UNITE        exige le rôle CHEF_UNITE_DA
EN_ATTENTE_DR  ->  niveau DIRECTEUR_RESEAU  exige le rôle DIRECTEUR_RESEAU_DR
tout autre statut -> aucun niveau attendu   -> 422 TRANSITION_INTERDITE
```

Motifs :

- **Le statut est détenu par le service** et ne peut pas être changé de l'extérieur.
  Le rôle vient du profil local, qu'un administrateur peut modifier entre deux gestes
  (Sprint 1.2).
- **RG-07 est une propriété du dossier.** « Aucun saut de niveau » décrit le parcours
  de l'état, pas la qualité de qui le regarde. Laisser le rôle décider reviendrait à
  laisser l'appelant désigner le niveau d'approbation qui l'engage.
- **Les messages de refus deviennent justes.** Le dossier peut dire « je n'attends
  aucune validation » (422) ou « j'attends le visa du directeur réseau, pas le vôtre »
  (403), chacun nommant l'état réel.

La correspondance statut → niveau est une **bijection partielle** : un statut attend au
plus un niveau, les quatre autres n'en attendent aucun. Aucune ambiguïté à lever,
aucune priorité à inventer.

Rendu structurel par l'énumération `domaine/NiveauValidation`, qui porte pour chaque
niveau son statut requis, son `NomEtapeEnum`, son rôle et son intitulé. Le niveau est
**passé en paramètre** au bloc transactionnel ; le rededuire là créerait un second
endroit où le niveau se décide.

**Codes de refus.** Un rôle du circuit présenté au mauvais niveau rend `403
ACCES_REFUSE` (code déjà au contrat) via `RoleNonAttenduException`, dont la seule
raison d'être est le **message** : « votre rôle ne permet pas cette action » serait
faux pour un valideur du circuit.

**Pas d'aiguillage au second niveau.** `NiveauValidation.declencheAiguillage()` ne vaut
que pour `CHEF_UNITE`. Après le visa du directeur réseau il n'y a plus d'échelon : le
service d'aiguillage n'est pas appelé, et **le seuil n'est même pas lu**. Un test le
prouve en rendant le paramètre illisible : la validation aboutit quand même. En
conséquence, `ValidationResponse.aiguillage` et `seuilApplique` sont **nuls** au second
niveau — les deux champs restent présents, au même nom et au même type, mais ne
portent aucune valeur. Inventer une troisième valeur d'aiguillage aurait fait croire à
une comparaison montant / seuil qui n'a pas eu lieu. L'audit du second niveau
n'inscrit ni seuil ni décision, pour la même raison.

---

## 2. RG-11 : le retour ramène toujours à l'agent, et c'est structurel

C'est le piège annoncé du sous-sprint. Un retour du directeur réseau **ne revient pas
au chef d'unité** : il redescend directement à la saisie.

Ce n'est pas une discipline de code : les deux transitions de retour d'ET01
(`retournerParChefUnite`, `retournerParDirecteurReseau`) mènent au **même et unique**
statut `RETOURNE`. Il n'existe aucune cible intermédiaire à choisir, donc aucune
occasion de se tromper.

Justification métier : le chef d'unité a déjà visé une version que le directeur réseau
vient de refuser, et il n'a pas la main sur les lignes de saisie. C'est l'agent, et lui
seul, qui peut corriger.

**RG-10, le motif, est exigé à trois étages :**

1. `@NotBlank` sur `RetourRequest` → `400 REQUETE_INVALIDE` (la chaîne d'espaces
   comprise : le contrôle porte sur le contenu utile, `isEmpty` aurait laissé passer
   `"   "`) ;
2. `RetourService.exigerMotif` → `422 MOTIF_OBLIGATOIRE`, avant tout appel réseau ;
3. `TransitionProcessus.retourner…` et `EtapeWorkflow.retournerAvecMotif` — une étape
   `RETOURNEE` ne peut pas naître sans motif.

Le premier protège le chemin HTTP, les autres protègent la règle : un retour sans
motif laisserait l'agent devant un refus sans rien à corriger, et RG-10 serait
enfreinte en silence.

**Le retour n'appose aucune signature** (RG-09 ne vaut que pour les validations : le
valideur refuse d'engager la banque, il ne l'engage pas) et **ne touche pas au
document** — ni son chemin, ni son compteur.

**Le motif est visible par l'agent** (US-11) : `GET /processus/{id}` rend un champ
`motifRetour`, ajouté **en fin** de `ProcessusResponse` — les onze champs antérieurs
gardent nom, type et ordre, les cinq premiers étant un contrat inter-services avec la
Saisie (action B-04). Le motif n'est rendu que **tant que le statut vaut `RETOURNE`** :
une correction déjà faite, affichée sur un dossier reparti dans le circuit, se lirait
comme un reproche en cours. L'historique complet relève de `GET
/reporting/processus/{id}/historique` (Sprint 6).

Le motif est aussi enregistré **dans l'audit**, en plus de
`etape_workflow.motif_retour` : l'étape vit dans une base que le module peut écrire, le
journal d'audit dans une base où aucun service métier n'a de droit de modification.

---

## 3. La reprise est portée par la resoumission — aucun septième endpoint

**Décision : l'état reste visiblement `RETOURNE` jusqu'à la resoumission ; la
transition `RETOURNE → EN_COURS_SAISIE` est appliquée dans la transaction de
resoumission, juste avant `EN_COURS_SAISIE → SOUMIS`.**

Motifs :

- Le contrat d'API prévoit **six endpoints**, et le critère de validation du sprint
  exige « six endpoints, ni plus ni moins ». Un `POST /processus/{id}/reprise` en
  ferait un septième.
- L'agent voit dans sa liste que le dossier lui a été **retourné** — une reprise
  automatique dès le retour aurait affiché « en saisie » comme un mois ordinaire.
- Il peut corriger ses lignes immédiatement : le service Saisie tient déjà `RETOURNE`
  pour modifiable (`StatutProcessusEnum.estModifiable`, décision Sprint 3.3). Rien à
  changer côté Saisie.

`SoumissionService` accepte donc deux statuts, en liste **fermée et positive**
(`EN_COURS_SAISIE`, `RETOURNE`) comme côté Saisie : un statut ajouté un jour ne
deviendrait pas soumissible par omission.

### 3.1 Le document est régénéré, pas enrichi

Problème découvert en lisant le code du 4.2 et **absent du guide** : `SoumissionService`
refusait toute soumission si une pièce jointe existait (`409
PIECE_JOINTE_EXISTANTE`). Une resoumission après retour aurait donc échoué — le test 16
du guide était impossible à satisfaire en l'état.

**Décision : à la resoumission, le PDF est reconstruit depuis l'état corrigé et
remplace l'ancien ; `nombre_signatures` repart à 1.**

Motifs :

- Après correction, les lignes et les montants ont changé. Garder le document d'origine
  ferait valider au chef d'unité **un PDF qui ne correspond plus au dossier**.
- Y ajouter une seconde mention agent l'imprimerait par-dessus la première, dans un
  cadre déjà occupé (géométrie fixe de `GabaritDocument`, Sprint 4.2).
- Les visas apposés avant le retour disparaissent avec l'ancien fichier, ce qui est
  juste : ils portaient sur une version annulée.
- Archiver l'ancien à côté était impossible — `piece_jointe.id_processus` est UNIQUE et
  le modèle n'admet qu'un document par processus (CLAUDE.md §4) : le fichier serait
  devenu orphelin, référencé nulle part.

Traçabilité préservée : chaque version reste rattachable à son empreinte SHA-256 par le
journal d'audit, où aucun service métier n'a de droit d'écriture, et le retour lui-même
est tracé avec son motif.

Le remplacement passe par `StockageDocuments.remplacer` — même `fsync`, même renommage
atomique, et le compteur n'avance qu'**après** confirmation d'écriture disque (doctrine
Sprint 4.2).

`409 PIECE_JOINTE_EXISTANTE` est **conservé**, restreint au cas qui reste une
incohérence : une pièce jointe sur un état `EN_COURS_SAISIE`, c'est-à-dire un état déjà
soumis dont le statut aurait été changé autrement que par la machine à états.

### 3.2 Le rang d'étape est calculé, y compris pour la soumission

`EnregistrementSoumission` fixait `ORDRE_SOUMISSION = 1`. Une resoumission aurait
produit deux étapes de rang 1, et le découpage en cycles de RG-12
(`docs/decisions/2026-09-01-separation-des-taches-et-cycle-de-validation.md`) n'aurait
plus su laquelle est la dernière soumission. Le rang est désormais `dernier + 1`,
partout.

---

## 4. Conséquences pour les sprints suivants

- **Sprint 5 (transmission)** — `transmis_comptabilite` reste `false` sur les **deux**
  branches à la fin du Sprint 4, vérifié par deux tests. C'est le Sprint 5 qui le pose,
  à la publication effective (RG-13).
- **Sprint 6 (reporting)** — un dossier peut porter plusieurs cycles ; l'historique doit
  les distinguer, sans quoi deux validations DA contradictoires apparaîtront sur la
  même ligne de temps.
- **Frontend** — `aiguillage` et `seuilApplique` sont nuls sur une validation de second
  niveau ; `motifRetour` est nul hors état `RETOURNE`. Les deux doivent être traités
  comme des absences légitimes, pas comme des erreurs.
