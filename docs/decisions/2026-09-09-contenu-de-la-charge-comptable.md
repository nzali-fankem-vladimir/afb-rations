# Contenu de l'événement publié à la comptabilité

**Date :** 9 septembre 2026
**Sprint :** ajustement métier « rythme de paiement », prolongement de l'analyse
du point M-04
**Point de registre :** M-04 (position du module arrêtée ; l'accord de la DFT
reste à obtenir)
**Document lié :** `docs/decisions/2026-09-09-rythme-de-paiement-et-maille-de-la-periode.md`
**Statut :** position du module arrêtée sur les six questions ; **rien n'est
implémenté**

---

## 1. Ce qui a déclenché cette analyse

Le point M-04 laissait ouverte la forme de la période dans la charge publiée sur
`rations.etat.valide`. En préparant l'échange avec la DFT, l'extrait suivant du
cahier des charges a été porté à l'analyse — il décrit l'écriture attendue **pour
chaque bénéficiaire** :

```
DEBIT  : CODE UNITE - 64380090200 - cle - MONTANT - Libelle (RATION / TAXI GARDE ARMEE DU MM/AAAA)
CREDIT : AGENCE COMPTE COURANT - N° COMPTE - CLE - MONTANT - Libelle (RATION / TAXI GARDE ARMEE DU MM/AAAA)
```

**Ce que cet extrait révèle, et qui n'avait pas été vu : le mois n'est pas
seulement un champ technique, il est dans le LIBELLÉ de l'écriture.**
`… GARDE ARMEE DU MM/AAAA` est une chaîne que **le bénéficiaire lira sur son
relevé de compte**.

En cadence hebdomadaire, avec la forme mensuelle actuelle, l'agent verrait
**quatre lignes rigoureusement identiques** — « RATION GARDE ARMEE DU 09/2026 »,
quatre fois — sans pouvoir dire laquelle correspond à quelle semaine. Toute
réclamation deviendrait inarbitrable.

**C'est l'argument le plus concret en faveur de l'intervalle de dates** retenu la
veille au titre de M-04 : `DU 07/09/2026 AU 13/09/2026` se lit sans effort sur un
relevé. `SEMAINE 37 DE 2026` ne se lit pas — personne ne relit son compte en
semaines ISO.

**Note documentaire :** le cahier des charges n'est pas versionné dans le dépôt.
`docs/initialisation projet/` contient le contrat d'API, les user stories et les
guides, mais pas lui. Puisqu'il commande désormais des décisions d'architecture,
son extrait pertinent devrait y entrer.

## 2. Écart entre le cahier des charges et ce que le module détenait

Établi par lecture du code et de la base le 9 septembre 2026.

| Élément du cahier | État avant cette décision |
| --- | --- |
| `CODE UNITE` (débit) | Présent et publié |
| `64380090200` (compte de charge) | **Absent du projet entier** — zéro occurrence |
| `CLE` (débit et crédit) | **Notion inexistante** — aucune colonne, aucun champ |
| `AGENCE COMPTE COURANT` | Présent (`codeAgence`) |
| `N° COMPTE` | Présent (`numCompteCourant`), mais **sans contrôle de format** |
| `MONTANT` | Présent, figé à la saisie (RG-03) |
| Libellé `… DU MM/AAAA` | **Aucun libellé n'était publié** |

## 3. Les six décisions

### 3.1 La clé est calculée par le CBS — le module ne la publie pas

Aucun champ `cle` dans la charge, aucune colonne sur `beneficiaires`, aucun champ
à l'écran de saisie.

**Motif.** Une clé est un **chiffre de contrôle** : sa raison d'être est de
détecter une faute de frappe dans le numéro de compte. Si un agent saisit *et* le
compte *et* la clé, le contrôle ne vaut plus rien — une erreur cohérente passe
sans être vue. Le référentiel des comptes du CBS fait foi ; la recopie manuelle
d'un chiffre de contrôle est un contresens.

**Conséquence de planning, à ne pas perdre.** Si la DFT répond l'inverse, la
décision doit tomber **avant le sous-sprint 7F.4** (écrans de saisie) : ce serait
une colonne sur `beneficiaires`, une migration, un champ à l'écran et un
contrôle. Décidé après, l'écran serait à reprendre.

### 3.2 Le grain fin est conservé — une entrée par (bénéficiaire, nature, session)

La charge continue de porter une entrée par ligne de prestation, et **non** une
entrée agrégée par bénéficiaire. La comptabilité agrège si elle le souhaite.

**Motif.** Une information détaillée s'agrège toujours ; une information agrégée
ne se reconstitue jamais. Le grain fin est aussi celui où le montant est figé
(RG-03).

**Le cas est réel, pas théorique.** Mesuré en base le 9 septembre 2026 : un même
bénéficiaire porte jusqu'à **3 lignes, 2 natures et 2 sessions** sur un même état
(processus 740, bénéficiaire 221, total 4 500 FCFA).

**Point à confirmer avec la DFT.** Le libellé du cahier — `RATION / TAXI` — se lit
comme « l'un ou l'autre », ce qui suggérait une écriture par (bénéficiaire,
nature), sessions confondues. Si la comptabilité attend une écriture et en reçoit
deux, **le bénéficiaire voit deux lignes sur son relevé** : l'écart est
immédiatement visible et doit être convenu, pas supposé.

### 3.3 Le module publie le libellé de la PÉRIODE, la comptabilité le préfixe

Le module publie `periode.libelle` — par exemple `"DU 07/09/2026 AU 13/09/2026"`.
La comptabilité compose `RATION GARDE ARMEE ` ou `TAXI GARDE ARMEE ` devant.

**Motif.** **Seul le module connaît sa cadence.** Si la comptabilité formate la
période elle-même, elle code notre cadence chez elle et se casse le jour où nous
passons à la quinzaine — c'est-à-dire précisément le risque que l'intervalle de
dates avait été choisi pour écarter (M-04, motif 3).

**Écart assumé, et sa borne.** CLAUDE.md section 8 interdit au module de produire
quoi que ce soit de comptable. Composer un fragment de libellé est une entorse
mesurée : le module nomme **son propre objet métier**, il ne nomme pas l'écriture.
Le vocabulaire comptable — `RATION GARDE ARMEE`, `TAXI GARDE ARMEE` — reste chez
la comptabilité, y compris la correspondance `TRANSPORT` → `TAXI`.

### 3.4 La transition passe par un champ de version, pas par une bascule

Ajout d'un champ `versionCharge` à la racine de la charge. La comptabilité lit
les deux formes pendant une fenêtre convenue, puis l'ancienne est retirée.

**Motif.** Doctrine additive constante du projet : `manques` au Sprint 4.2,
`motifRetour` au 4.4, `transmission` au 5.1, `situation`/`message` au 5.3 —
toujours des champs ajoutés, jamais un contrat rompu d'un coup.

**Point d'honnêteté à porter à la DFT.** `mois` et `annee` **ne peuvent pas
cohabiter** avec la nouvelle forme : dès qu'une semaine chevauche deux mois,
aucune valeur n'est vraie. Remplir un champ avec une valeur fausse est pire que
de le retirer. C'est le champ de version qui porte la transition, pas la
coexistence des deux périodes.

### 3.5 Le compte de charge figure dans la charge, et il est configurable

`64380090200` est publié à la **racine** de la charge, sous `compteCharge` — pas
par ligne, puisqu'il est constant pour tout l'état et que le répéter suggérerait
qu'il peut varier.

**Décision explicite de l'utilisateur, contre la recommandation initiale.** Il
avait été proposé de l'exclure au titre de CLAUDE.md sections 8 et 15 (« jamais
de schéma débit/crédit codifié »). La décision retenue est de le publier, à la
condition expresse qu'il soit modifiable **sans redéploiement**. L'écart à la
doctrine est donc assumé et borné : le module transporte une **valeur de
paramétrage**, il ne code toujours ni le sens débit/crédit, ni la structure de
l'écriture.

**Mécanisme retenu : `parametre_systeme`, valeur portée par l'en-tête déjà lu.**

Une ligne de plus dans la table qui porte déjà `SEUIL_AIGUILLAGE_DR` — c'est-à-dire
la valeur qui commande le niveau d'approbation de la banque, conçue exactement
pour changer sans redéploiement :

```
COMPTE_CHARGE_RATIONS | Compte de charge d'imputation ... | 64380090200 | t
```

Trois voies avaient été examinées :

| | Mécanisme | Coût d'un changement | Coût technique |
| --- | --- | --- | --- |
| A | Variable d'environnement / ConfigMap | Édition **et redémarrage du pod** | Nul |
| **B** | **`parametre_systeme`, valeur portée sur l'en-tête que Transmission lit déjà** | **Un `UPDATE`, effet immédiat** | **Aucun appel réseau ajouté** |
| C | `parametre_systeme` via un endpoint interne dédié | Un `UPDATE`, effet immédiat | +1 appel : budget 27 → 32 s, délai de lecture 35 → 40 s, borne défensive 55 → 60 s (Sprint 5.3) |

**B est retenue.** A ne tient pas la contrainte — un redémarrage de pod reste une
opération de déploiement. C est plus propre sémantiquement mais paie un
aller-retour réseau dans le seul chemin du module dont le budget de temps a été
calculé au cordeau au Sprint 5.3.

**Difficulté technique que B contourne.** `parametre_systeme` vit dans
`rations_workflow` ; le service Transmission, qui construit la charge, **n'a
aucune base** et ne peut pas lire celle d'un autre service (AR04). Il lit en
revanche déjà `EnTeteProcessus` auprès du Workflow, dans la même requête : un
champ de plus y voyage gratuitement.

**Défaut assumé de B, écrit pour qu'il ne surprenne personne.** Un compte de
charge n'est pas une propriété d'un processus, et le loger dans son en-tête
mélange deux choses. Compromis retenu contre cinq secondes de budget dans le
chemin le plus tendu du module ; un nom de champ explicite et un commentaire le
rendent lisible.

**Un seul compte de charge, pas un par nature.** L'hypothèse de deux comptes
distincts (rations et transport) a été posée et écartée par le métier. `RATION`
et `TRANSPORT` s'imputent sur le même compte, `compteCharge` reste donc à la
racine.

### 3.6 Le compte de charge est relu à chaque transmission, jamais mis en cache

**Motif.** Doctrine du seuil d'aiguillage (Sprint 4.3, CT-18), transposée : une
valeur de paramétrage qui gouverne de l'argent se relit à chaque usage. Si le
plan comptable change, les états transmis après le changement suivent le nouveau
compte — comportement comptable normal. Le figer à la clôture ferait qu'un état
clôturé en janvier et transmis en mars s'imputerait sur un compte périmé.

En pratique l'écart est de quelques secondes, la transmission suivant la clôture.
La règle est écrite plutôt que subie.

**Règle non négociable qui l'accompagne : paramètre absent, désactivé ou vide →
REFUS DE PUBLIER, jamais un repli.** C'est mot pour mot la doctrine de RG-08
(`SEUIL_INDISPONIBLE`, Sprint 4.3), et elle pèse plus lourd ici : publier un
message de paiement avec un compte de charge absent ou par défaut, c'est
**imputer de l'argent sur le mauvais compte, sans erreur visible**.

**Aucun code d'erreur nouveau n'est nécessaire.** `500 CHARGE_INCOMPLETE` existe
depuis le Sprint 5.1 et son motif d'origine s'applique tel quel : l'état est
clôturé, donc figé, l'appelant n'a rien à corriger. Ce serait le **quinzième**
contrôle de complétude de la charge.

## 4. La charge résultante

```json
{
  "idProcessus": 740,
  "versionCharge": 2,
  "periode": {
    "dateDebut": "2026-09-07",
    "dateFin": "2026-09-13",
    "libelle": "DU 07/09/2026 AU 13/09/2026"
  },
  "codeUnite": "00002",
  "compteCharge": "64380090200",
  "typeProcessus": "NORMAL",
  "montantTotal": 84000,
  "lignes": [
    {
      "nom": "Mbarga", "prenom": "Jean",
      "numCompteCourant": "03702099911",
      "codeAgence": "00002",
      "nature": "RATION", "session": "JOUR",
      "montant": 2500
    }
  ]
}
```

**Argument à mettre en avant devant la DFT : les lignes bénéficiaires ne changent
pas d'un caractère.** Tout le diff tient dans l'objet `periode` — deux champs
remplacés par trois — plus `versionCharge` et `compteCharge` à la racine. Leur
travail d'adaptation est petit et localisé.

## 5. Ce qui reste hors de la charge, et pourquoi

- **Le sens débit / crédit.** Le module dit « cet agent a droit à X, la charge
  pèse sur l'unité Y ». Les deux lignes s'en déduisent ; les écrire ici, ce
  serait produire l'écriture (CLAUDE.md sections 8 et 15).
- **La clé.** Section 3.1.
- **Le libellé comptable complet.** Section 3.3 — le module fournit le fragment
  de période, pas le vocabulaire comptable.
- **Toute notion d'écriture, de journal ou d'imputation CBS.** Inchangé.

## 6. Format du numéro de compte — défaut identifié, non corrigé ici

**Le numéro de compte courant fait 11 chiffres.** Établi par le métier le
9 septembre 2026.

**Le module ne le contrôle pas.** Seul contrôle en vigueur, sur
`IdentiteBeneficiaireRequest` :

```java
@NotBlank(message = "le numéro de compte courant est obligatoire")
@Size(max = 20, message = "le numéro de compte courant ne peut pas dépasser 20 caractères")
String numCompteCourant,
```

Aucune longueur exacte, aucun format. **Constat mesuré :** 44 des 45 bénéficiaires
en base portent un numéro à 14 chiffres, donc faux ; un seul est correct
(`03702099911`). **L'exemple du contrat d'API section 7.1 lui-même
(`00002000123456`) est faux.**

**Pourquoi ce champ mérite un contrôle plus que tout autre.** Il cumule deux rôles
critiques :

1. Il est le **seul critère d'identification d'un bénéficiaire** (Sprint 3.1 : ni
   le nom, ni le couple nom + prénom). Une faute de frappe ne corrige rien — elle
   **crée silencieusement un second bénéficiaire**.
2. Il est la **ligne de crédit du paiement** : le compte qui reçoit l'argent.

Une faute de frappe fabrique donc un agent fantôme *et* envoie son argent
ailleurs, sans qu'aucun contrôle ne s'y oppose. Les 44 lignes fausses en base le
prouvent : personne n'a rien vu.

Le défaut préexistait. Ce qui l'aggrave : le passage en cadence hebdomadaire
**quadruple les occasions de saisie**, donc de frappe fautive.

**Correction recommandée, non appliquée :** `@Pattern(regexp = "^[0-9]{11}$")`
sur le DTO d'entrée, message nommant la règle. **Aucune migration** — la colonne
reste `VARCHAR(20)`, seul le contrôle d'entrée change. À poser **avant 7F.4**,
pour que l'écran et le backend refusent la même chose. Reste à décider du sort
des 44 lignes de test fausses : correction ou jeu de données neuf.

Consigné comme point **T-02** au registre.

## 7. Aucun lien entre le numéro de compte et le code agence

Une piste de contrôle de cohérence gratuit avait été envisagée — le préfixe du
compte égalerait le code agence — puis **écartée par le métier**.

`code_agence` et `code_unite` suivent le référentiel des codes guichets :
`00001`, `00002`, `00003`, … `00050`, séquentiels (CLAUDE.md section 13). Ils
n'ont **aucun rapport** avec la composition du numéro de compte. Aucun contrôle
croisé n'est donc dérivable, et le contrôle de format de la section 6 reste le
seul filet possible.

## 8. Ce qui reste à obtenir de la DFT

Cette décision arrête **la position du module**, pas un accord. La charge est
consommée par le module de comptabilisation, qui n'est pas maintenu par cette
équipe. À porter dans le même échange que **M-03** et **D-11** :

1. La forme de la période (`dateDebut`, `dateFin`, `libelle`).
2. Le partage du libellé — nous le fragment de période, eux le préfixe.
3. La granularité — grain fin conservé (section 3.2).
4. La fenêtre de transition et la valeur de `versionCharge`.
5. Confirmation que la clé est bien calculée de leur côté.

### Réponses obtenues le 10 septembre 2026

| | Question | Réponse |
| --- | --- | --- |
| 1 | Forme de la période | **Oui** — `dateDebut` + `dateFin` + `libelle` acceptés |
| 2 | Partage du libellé | **Sans réponse explicite** — position du module appliquée à défaut |
| 3 | Granularité | **Sans réponse explicite** — position du module appliquée à défaut |
| 4 | Fenêtre et `versionCharge` | **Délégué au module** |
| 5 | Clé calculée par le CBS | **Oui** — le module ne la publie pas |

**Les points 2 et 3 n'ont pas reçu de réponse.** Ils sont consignés comme
appliqués *à défaut d'objection*, pas comme accordés. La distinction compte : si
la comptabilité découvre à l'intégration qu'elle attendait un montant agrégé par
bénéficiaire, c'est le grain fin qui sera en cause, et il faudra pouvoir dire
qu'il n'avait jamais été validé. **À reposer avant la première transmission
réelle**, c'est-à-dire avant le sous-sprint 8.3.

### Décision 7 — `versionCharge` vaut 2, et il n'y a aucune fenêtre de transition

**Le module n'est pas déployé.** La conteneurisation et le déploiement Kubernetes
sont les sous-sprints 8.2 et 8.3, non réalisés. Les **7 états déjà transmis**
l'ont été sur le broker de développement local : le module de comptabilisation
n'a **jamais reçu un seul message réel** de ce module.

Il n'y a donc **rien à faire cohabiter**. La version 1 de la charge n'a pas
d'existence en production, et la première charge jamais reçue par la comptabilité
portera directement la forme à intervalle de dates.

**Pourquoi conserver le champ malgré tout.** Il coûte un entier et il achète le
changement suivant. Le module vient de découvrir en cours de route que sa cadence
n'était pas celle qu'il croyait ; un champ de version est précisément ce qui
permet à la prochaine surprise de ne pas être une rupture.

**Pourquoi 2 et non 1.** Le contrat d'API section 7.1 documente la forme
`{ mois, annee }` : c'est la version 1, produite en production ou non. La
renuméroter ferait mentir l'historique du contrat pour économiser un chiffre.

### Décision 8 — La clé de partition des accusés est `idProcessus` (D-11)

La DFT laisse le module recommander. Recommandation retenue : **que le producteur
d'accusés renvoie la clé qu'il a reçue sur `rations.etat.valide`**, c'est-à-dire
`idProcessus`.

Quatre motifs :

1. **Symétrie des deux sens.** Une seule convention à expliquer, à superviser et
   à retrouver six mois plus tard.
2. **L'ordre est garanti là où il compte.** Deux accusés portant sur le *même*
   état arrivent dans l'ordre où ils ont été émis. C'est le seul ordre dont le
   module a besoin.
3. **Aucun ordre n'est promis entre états différents**, et rien dans le code n'en
   dépend — l'exiger aurait imposé une partition unique, donc un goulot.
4. **Aucune sérialisation à réaccorder** : renvoyer la clé reçue évite tout
   désaccord de type ou de format entre les deux équipes.

**Formulé comme une défense en profondeur, pas comme une dépendance**, et c'est
ce qui rend la demande facile à accepter. `TransitionIntegration` refuse déjà
toute régression de statut : un rejeu de topic dans le désordre est inoffensif
aujourd'hui, sans aucune garantie d'ordre. La clé ne répare rien — elle évite que
le filet soit le seul mécanisme en jeu.

Élément de contexte utile à l'échange : **7 états ont déjà été transmis** sous la
forme mensuelle actuelle, et ne sont pas rejouables.

**Rien de tout cela n'est implémenté.** Aucune ligne de code n'a été écrite pour
cette décision.

---

**Références :** M-04, M-03, D-11, T-02, RG-03, RG-08, CLAUDE.md sections 8, 11,
13 et 15, contrat d'API section 7.1,
`docs/decisions/2026-09-09-rythme-de-paiement-et-maille-de-la-periode.md`.
