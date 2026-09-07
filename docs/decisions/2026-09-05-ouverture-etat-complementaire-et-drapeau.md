# Ouverture d'un état complémentaire : drapeau, contrôles et ce qui reste ouvert

**Sprint 6bis.1 — 5 septembre 2026**

Ce document consigne les décisions du sous-sprint 6bis.1 qui **engagent les sprints
suivants** : 6bis.2 (RG-15), 7F.4 et 7F.7 (frontend), et le futur sprint d'ajustement
du rythme de paiement.

---

## 1. Le besoin est confirmé, le drapeau reste fermé

Le métier a confirmé le besoin d'état complémentaire pendant ce sprint : **M-01 est
résolu**. Cela n'ouvre pas `RATTRAPAGE_ACTIF` pour autant, et la raison n'a rien à
voir avec M-01.

**RG-15 n'existe pas.** Sans le contrôle d'unicité inter-états, un état
complémentaire peut reproduire une combinaison (bénéficiaire, journée, nature,
session) déjà présente dans l'état d'origine — donc payer deux fois le même
bénéficiaire pour la même journée. C'est précisément ce que le point de vigilance du
guide 6bis.1 annonçait : « tant que RG-15 n'existe pas, ouvrir le drapeau en test
réel resterait risqué ».

La chaîne de dépendance à retenir, parce qu'elle se perd facilement :

```
besoin confirmé (M-01 résolu)
    └─> ouverture impossible tant que 6bis.2 n'est pas livré
            └─> 6bis.2 gelé tant que M-04 (rythme de paiement) n'a pas de réponse
```

**Conséquence pour le déploiement :** `RATTRAPAGE_ACTIF` doit rester à `false` en
production, quelle que soit l'insistance métier, jusqu'à livraison de RG-15. Le
drapeau peut être ouvert **en base de test isolée** pour éprouver le parcours.

---

## 2. Les neuf contrôles d'ouverture et leurs codes

Arbitrés avec l'utilisateur avant tout codage. Le frontend (7F.7) devra savoir les
distinguer, et 6bis.2 s'insérera dans cette séquence.

| # | Contrôle | Refus |
|---|---|---|
| 1 | Drapeau `RATTRAPAGE_ACTIF` | `422 FONCTIONNALITE_NON_OUVERTE` |
| 2 | Identifiant d'origine fourni | `422 ORIGINE_REQUISE` |
| 3 | Motif non vide | `422 MOTIF_OBLIGATOIRE` |
| 4 | Origine existante | `404 PROCESSUS_INTROUVABLE` |
| 5 | Portée sur l'unité **de l'origine** | `403 UTILISATEUR_NON_HABILITE` / `503` |
| 6 | Origine clôturée | `422 ETAT_NON_CLOTURE` |
| 7 | Dans le délai de régularisation | `422 DELAI_REGULARISATION_DEPASSE` |
| 8 | Unité déclarée concordante | `403 UNITE_NON_CONCORDANTE` |
| 9 | Période déclarée concordante | `422 PERIODE_NON_CONCORDANTE` |

**RG-15 s'insérera en position 10**, après tous ces contrôles : elle porte sur les
*lignes*, qui n'existent pas encore au moment de l'ouverture. Elle s'appliquera donc
à la saisie de chaque ligne du complémentaire, pas à son ouverture.

### Trois choix de code qui ne vont pas de soi

**Le drapeau se lit en premier, avant même l'existence de l'origine.** Un `404`
affiché avant le refus de fonctionnalité laisserait croire que la régularisation est
ouverte et que seul le numéro d'origine est faux : l'agent chercherait le bon, puis
se heurterait au mur qu'on aurait pu lui montrer d'emblée.

**`403` pour l'unité, `422` pour la période.** L'unité est ce sur quoi la portée
d'un agent est définie (Sprint 1.1) : un désaccord peut signaler un débordement de
périmètre, et se trace en audit. La période n'ouvre aucun droit — se tromper de mois
est une maladresse de saisie. La traiter en refus d'accès enverrait l'agent réclamer
une habilitation dont l'absence n'est pas en cause, et remplirait le journal d'audit
de fautes de frappe.

**`422 ORIGINE_REQUISE` et non `400`.** La contrainte est **conditionnelle au type**
demandé : un `@NotNull` sur le DTO refuserait tous les déclenchements ordinaires du
module.

---

## 3. Le refus d'unité non concordante est tracé en audit

`UniteNonConcordanteException` passe par `GestionnaireErreursApi`, seul point de
convergence des refus d'accès du service, et publie `ACCES_REFUSE` avec son propre
motif — comme les quatre autres refus en 403 déjà présents.

**Le raisonnement, pour qu'il ne soit pas défait plus tard.** Si un désaccord
d'unité mérite un `403`, c'est qu'on le traite comme une tentative d'accès
inter-unité. Or la doctrine du Sprint 6.3 (CT-04) est explicite : tout refus d'accès
se trace, sans quoi il n'existe nulle part. Un `403` non tracé aurait rejoint les
trente points de publication manquants inventoriés au Sprint 6.3.

**Règle générale à appliquer aux sprints suivants :** tout nouveau refus en `403`
passe par `publierRefus`. Il n'y a pas d'exception raisonnable.

---

## 4. Le délai de régularisation reçoit la lecture stricte du seuil

`DELAI_REGULARISATION_JOURS` est stocké en texte, comme `SEUIL_AIGUILLAGE_DR`, et
tout aussi susceptible d'être malformé. Il reçoit donc le traitement *fail-closed*
du Sprint 4.3, avec **deux codes distincts** :

- `500 DELAI_REGULARISATION_INDISPONIBLE` — paramètre absent, désactivé, illisible
  ou négatif, **ou date de clôture de l'origine introuvable**. L'agent n'a rien à
  corriger : c'est la configuration qui est en défaut.
- `422 DELAI_REGULARISATION_DEPASSE` — la configuration est saine, l'origine est
  réellement trop ancienne.

Zéro est accepté — seule une période close le jour même reste régularisable, ce qui
est une configuration intelligible. Le négatif est refusé : il fermerait toute
régularisation sans qu'aucune erreur ne le signale, exactement comme un seuil
négatif ferait monter tout au Directeur Réseau.

---

## 5. Le drapeau ferme sur trois situations, et le dit distinctement

`FonctionnaliteService.rattrapageActif()` **ne lève jamais** : « fermé » est une
réponse normale, pas une panne. Toute situation autre qu'un `true` franc rend
`false`.

Trois situations anormales sont journalisées en `WARN` avec des messages
**distincts**, au préfixe repérable `DRAPEAU FONCTIONNALITE INTROUVABLE` :

| Situation | Pourquoi la distinguer |
|---|---|
| Ligne absente de la table | Une suppression accidentelle éteindrait la fonctionnalité pour toujours : le jour de la confirmation métier, l'`UPDATE` prévu n'ouvrirait rien du tout |
| Ligne présente mais `actif = false` | Il faudra réactiver la ligne **en plus** d'y porter `true` |
| Valeur illisible | Une valeur que personne n'a voulue |

La fermeture délibérée (`false`) ne journalise rien : c'est l'état nominal du module
aujourd'hui, et le signaler noierait les trois autres.

**La casse est tolérée**, la sémantique ne l'est pas. `TRUE` ouvre comme `true` : la
valeur est écrite à la main par un administrateur, et refuser sur une majuscule
laisserait la fonctionnalité fermée avec un simple avertissement au journal — un
échec presque silencieux. `oui`, `1` et `vrai` restent refusés : là où l'intention
est claire on la suit, là où elle demande une interprétation on refuse.

---

## 6. La date de clôture se lit sur la DERNIÈRE étape validée

`processus_mensuel` ne porte aucune colonne `date_cloture` (décision Sprint 4.1) :
l'instant de la clôture vit sur `etape_workflow.date_creation` de la validation qui
a clos le dossier.

**Un dossier peut porter plusieurs étapes `VALIDEE`** — validé par le chef d'unité,
monté au directeur réseau, retourné, corrigé, resoumis, revalidé, clos. Le service
prend donc la **dernière** par rang décroissant, le rang étant calculé `dernier + 1`
depuis le Sprint 4.4.

Prendre la première ferait courir le délai depuis un visa **annulé par un retour**,
et refuserait des régularisations parfaitement légitimes. Le raisonnement ne tient
que parce que `CLOTURE` est terminal : aucune étape ne peut suivre celle qui a clos
le dossier.

Deux tests couvrent ce cas, dont un de bout en bout jouant réellement les deux
cycles. C'est le piège que le Sprint 4.4 a déjà tendu une fois avec RG-12 et le
découpage en cycles.

---

## 7. La période est recopiée de l'origine, jamais reçue

Le constructeur `ProcessusMensuel(origine, motif)` recopie mois, année et code unité
depuis l'état d'origine. Ce que l'appelant déclare sert **uniquement à être
vérifié** (contrôles 8 et 9) — doctrine du Sprint 3.4 : un paramètre fourni par
l'appelant ne se croit pas sur parole.

**Conséquence pour le futur changement de maille de période.** Ce service est
largement indifférent à la représentation de la période : il la copie. Le seul
endroit qui la *lit* est `exigerPeriodeConcordante`, une comparaison
demande-contre-origine. Un passage du couple `(mois, année)` à un intervalle de
dates y coûterait une méthode, pas un service. C'est ce qui a permis de terminer
6bis.1 malgré l'annonce du rythme hebdomadaire.

---

## 8. Un état complémentaire monte toujours au Directeur Réseau

**Règle provisoire, retenue le 6 septembre 2026, avant l'arbitrage du métier.** Un
état complémentaire exige le second niveau d'approbation **quel que soit son
montant**. Le seuil n'est pas lu, aucune comparaison n'a lieu.

### Les trois lectures possibles, et pourquoi celle-ci

| Lecture | Trou de contrôle interne |
|---|---|
| Comparer le montant du complémentaire au seuil, comme un état normal | Un fractionnement en plusieurs complémentaires reste sous la barre à chaque fois. Le rythme hebdomadaire (M-04) l'aggraverait : les états devenant ~4× plus petits, presque tous passeraient sous le seuil |
| Laisser le Chef d'Unité clore seul | Un complémentaire de **n'importe quel montant** échappe au second regard |
| **Monter systématiquement au DR** *(retenue)* | Aucun. Elle ne peut que **trop** demander |

**Le principe qui tranche : quand on décide sans le métier, on décide dans le sens
qui exige *plus* d'approbation, jamais moins.** C'est le seul sens dans lequel une
erreur d'appréciation sur un paiement ne coûte rien d'irréversible — et c'est la
même prudence que le refus conservateur du Sprint 1.3 et le *fail-closed* du seuil au
Sprint 4.3.

### Comment c'est écrit

Une branche en tête de `AiguillageService.aiguiller`, **avant** la lecture du seuil :

- décision `COMPLEMENTAIRE_ENVOI_DIRECTEUR_RESEAU`, troisième valeur de
  `DecisionAiguillage` ;
- `seuilApplique` **nul** — `ResultatAiguillage.seuilApplique` passe de `long` à
  `Long`. Un `0` aurait été une valeur sentinelle, indiscernable d'un seuil
  réellement configuré à zéro, que le Sprint 4.3 accepte explicitement ;
- **le seuil n'est pas lu du tout.** Un test le prouve en le rendant illisible : un
  état normal échoue en `SEUIL_INDISPONIBLE`, le complémentaire aboutit. Lire un
  paramètre qui ne gouverne pas la décision ferait échouer une validation sur une
  panne étrangère au dossier — le défaut que le Sprint 4.4 a écarté en ne rappelant
  pas l'aiguillage au second niveau.

**Une troisième valeur d'énumération plutôt que `ENVOI_DIRECTEUR_RESEAU` réutilisé.**
La conséquence est la même, le **motif** ne l'est pas, et il doit se lire sans être
déduit d'un `seuilApplique` nul. C'est d'autant plus nécessaire que la règle est
provisoire : le jour où le métier tranchera, il faudra retrouver les dossiers qu'elle
a aiguillés. Le `switch` d'`EnregistrementValidation` porte donc deux cas menant à la
même transition, délibérément distincts.

### Si le métier arbitre autrement

La bascule tient dans `AiguillageService` : la comparaison de RG-08 n'existe qu'à cet
endroit (doctrine du Sprint 4.3). Revenir à « comparer le montant du complémentaire
au seuil » ou passer à « le Chef d'Unité clôt seul » se fait dans la même méthode, en
quelques lignes plus les tests. La forme « cumuler avec les autres états de la
période » serait la seule vraiment coûteuse : elle ferait dépendre le niveau
d'approbation d'un dossier de dossiers étrangers, que le Workflow devrait relire à
chaque validation et qui peuvent eux-mêmes changer entre-temps.

**Aucune de ces bascules n'est structurelle.** Le montant, le seuil et la décision
restent au même endroit.

---

## 9. Ce que ce sprint n'a délibérément pas fait

- **Aucune entité `Reclamation`.** Le signalement du bénéficiaire est extérieur au
  système ; l'agent l'enregistre dans le motif d'ouverture, qui est la **seule**
  trace de ce qui a déclenché la régularisation.
- **Aucune liste de bénéficiaires attendus.** Ce processus n'a pas d'enrôlement : le
  module ne peut pas savoir qui aurait dû être payé.
- **Aucune modification du service Saisie.** Sa liste des statuts modifiables
  (`EN_COURS_SAISIE`, `RETOURNE`) ne connaît pas la notion de type : un complémentaire
  est modifiable comme un état normal, par construction.
- **Aucune migration.** Les deux paramètres étaient posés depuis `V2` (Sprint 4.1).
  V4 et V5 étant déjà pris, la prochaine migration du service Workflow sera **V6**.
