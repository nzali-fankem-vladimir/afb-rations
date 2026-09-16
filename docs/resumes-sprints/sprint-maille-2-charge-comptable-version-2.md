# Résumé Sprint Maille 2 — La charge comptable passe en version 2

**Date :** 16 septembre 2026
**Position :** hors séquence numérotée, après la Maille 1
**Objet :** porter sur le fil la forme acceptée par la DFT — bornes de période,
libellé, compte de charge — et retirer le garde-fou temporaire de la Maille 1
**Livrable :** migration V7, charge v2 publiée et **lue sur le broker**, 745 tests

---

## En une phrase

Le module publie désormais une charge qui n'a plus besoin de mois — ce qui lève le
refus de transmettre une période à cheval sur deux mois, et achève la migration
commencée à la Maille 1.

---

## 1. Ce que la charge porte maintenant

Message **réellement lu sur `rations.etat.valide`**, partition 0, offset 7 :

```json
{
  "idProcessus": 1009,
  "versionCharge": 2,
  "periode": {
    "dateDebut": "2027-01-01",
    "dateFin": "2027-01-31",
    "libelle": "DU 01/01/2027 AU 31/01/2027"
  },
  "codeUnite": "00002",
  "compteCharge": "64380090200",
  "typeProcessus": "NORMAL",
  "montantTotal": 7000,
  "lignes": [ … ]
}
```

**Ce n'est pas une capture de test** : c'est le contenu du topic, relu avec
`kafka-console-consumer` après une transmission réelle. La doctrine du Sprint 6.3
vaut ici comme pour l'audit — la preuve est une lecture, pas l'absence d'erreur.

## 2. `versionCharge` vaut 2, et il n'y a aucune fenêtre de transition

Le fait qui tranche : **le module n'est pas déployé**. Conteneurisation et
déploiement sont les sous-sprints 8.2 et 8.3 ; les états déjà transmis l'ont été sur
le broker local. **La comptabilité n'a jamais reçu un seul message de version 1.**

Il n'y avait donc rien à faire cohabiter, et la première charge qu'elle recevra
portera directement la version 2.

**Pourquoi garder le champ** : il coûte un entier et achète le changement suivant.
Le module vient de découvrir que sa cadence n'était pas celle qu'il croyait ; un
champ de version est ce qui permet à la prochaine surprise de ne pas être une
rupture.

**Pourquoi 2 et non 1** : le contrat d'API documente la forme `{ mois, annee }`.
C'est la version 1, produite en production ou non. La renuméroter ferait mentir
l'historique du contrat pour économiser un chiffre.

## 3. Le libellé, et pourquoi sa forme n'est pas un détail

`periode.libelle` porte `DU 01/01/2027 AU 31/01/2027` — le **fragment** de période
de l'écriture. La comptabilité y préfixe sa nomenclature (`RATION`,
`TAXI GARDE ARMEE`) : seul le module connaît sa cadence, seule elle connaît ses
écritures.

**Ce libellé se lit sur le relevé de compte du bénéficiaire.** C'est ce qui a emporté
la forme : en hebdomadaire, une période mensuelle aurait produit **quatre lignes
rigoureusement identiques par mois**, rendant toute réclamation inarbitrable.
`DU 07/09/2026 AU 13/09/2026` se lit ; `SEMAINE 37 DE 2026` ne se lit pas.

## 4. Le compte de charge

**Migration V7** pose `COMPTE_CHARGE_RATIONS = 64380090200` dans
`parametre_systeme`, à côté du seuil d'aiguillage — la table conçue pour les valeurs
qui gouvernent de l'argent et doivent changer sans redéploiement.

**Il voyage sur l'en-tête que Transmission lit déjà** (`GET /processus/{id}`) :
ce service n'a pas de base et ne peut pas lire `parametre_systeme` (AR04). Un
endpoint dédié aurait ajouté cinq secondes au seul chemin du module où le budget est
calculé au cordeau (Sprint 5.3). **Zéro appel réseau ajouté.**

Écart assumé et borné : un compte de charge n'est pas une propriété d'un processus.
Le module transporte un paramètre, il ne code toujours ni le sens débit/crédit ni la
structure de l'écriture.

### Tolérant en lecture, strict à la publication

C'est la décision de conception du sprint, et elle sépare deux endroits :

| Chemin | Comportement si le compte manque |
| --- | --- |
| `GET /processus/{id}` — consultation par l'agent | Rend `null`, sert le dossier normalement. Faire échouer la consultation parce qu'un paramètre **comptable** manque punirait quelqu'un qui n'y peut rien |
| Publication de la charge | **Refuse** — `COMPTE_CHARGE_ABSENT` → `500 CHARGE_INCOMPLETE`. Publier sur un compte deviné, c'est imputer de l'argent ailleurs, sans erreur visible |

**Vérifié en réel**, en vidant la ligne en base : la transmission a été refusée avec
le message nommant le paramètre, et **l'état est resté `transmis_comptabilite = false`**
— donc rejouable. Rétabli par un simple `UPDATE`, il est reparti **sans redémarrage**,
ce qui était toute la raison du choix de `parametre_systeme`.

## 5. Un code d'anomalie que j'avais mal choisi

Le refus de compte réutilisait `PERIODE_INVALIDE`, par commodité. La vérification
réelle l'a montré : le message disait « aucun compte de charge » sous un code qui
parle de période.

**Corrigé en `COMPTE_CHARGE_ABSENT`**, code distinct, parce que les deux appellent
des gestes différents : une période inexploitable signale un dossier mal formé, un
compte absent signale une ligne manquante en base — geste d'administration, pas
correction de dossier. Les confondre enverrait chercher une faute là où il n'y en a
pas.

## 6. Le garde-fou de la Maille 1 a disparu

La Maille 1 faisait rendre un mois **nul** dès qu'une période chevauchait deux mois,
ce que `ConstructionChargeService` refusait en `PERIODE_INVALIDE`. C'était un
dispositif temporaire, posé pour rendre la dépendance à ce sprint **visible plutôt
que silencieuse**.

Il est sans objet : la charge ne porte plus de mois. `moisDerive`, `anneeDerivee` et
`tientDansUnSeulMois` sont supprimés — laissés en place, ils seraient restés un piège
pour le prochain lecteur.

**Conséquence directe : une semaine du 29 septembre au 5 octobre se transmet
désormais normalement.**

## 7. Une divergence fermée au passage

`EtatValideProducerTest` construisait son propre `new ObjectMapper()` nu, là où la
production utilise un bean configuré avec `JavaTimeModule` et l'ISO 8601. Le test
passait donc sur une forme de message que la production ne produit pas — **exactement
la divergence que le Sprint 5.1 avait déjà payée**.

Il utilise désormais `new ConfigurationProducteurEtatValide().convertisseurChargeComptable()`,
c'est-à-dire **le** convertisseur de production, pas une copie. La divergence ne peut
plus revenir.

## 8. Les tests

| Suite | Tests | État |
| --- | --- | --- |
| `rations-audit-commun` | 18 | Vert |
| service-identite | 61 | Vert |
| service-saisie | 81 | Vert |
| service-grilles | 99 | Vert |
| service-workflow | 344 | Vert |
| service-reporting | 33 | Vert |
| **service-transmission** | **98** | Vert |
| gateway | 11 | Vert |
| **Total** | **745** | **BUILD SUCCESS** |

Deux tests ajoutés : aucune charge ne part sans compte de charge, et des bornes de
période à l'envers sont refusées. Le test « période hors de 1 à 12 » de la version 1
a été **repointé** sur une borne absente — le cas équivalent, le mois n'existant plus.

## 9. Vérification réelle

| Vérification | Résultat |
| --- | --- |
| Migration V7 appliquée | `flyway_schema_history` : version 7, succès |
| Paramètre en base | `COMPTE_CHARGE_RATIONS = 64380090200`, actif |
| `compteCharge` sur l'en-tête | Présent dans `GET /processus/1009` |
| Charge publiée | **Lue sur le broker**, offset 7, forme v2 complète |
| Compte vidé → refus | `500 CHARGE_INCOMPLETE`, `COMPTE_CHARGE_ABSENT` |
| État refusé | Reste non transmis, donc rejouable |
| Compte rétabli → reprise | Transmis, offset 8, **sans redémarrage** |

## 10. Ce qui reste ouvert

**Deux questions de l'échange DFT sont restées sans réponse explicite** : le partage
du libellé (section 3) et la granularité fine (une entrée par bénéficiaire, nature,
session). Les positions du module sont appliquées **à défaut d'objection**, pas comme
accordées — la distinction compte, et elles sont **à reposer avant la première
transmission réelle**, c'est-à-dire avant le sous-sprint 8.3.

**D-12** — l'extension PostgreSQL `btree_gist`, requise par la contrainte
d'exclusion de la Maille 1, reste à obtenir de la DSI avant le déploiement.

---

**Prochaine étape :** le sous-sprint **6bis.2** (RG-15), dont le guide est à ajuster
au vocabulaire de période avant exécution. Il ouvre `RATTRAPAGE_ACTIF` et satisfait
la condition posée par la DFT sur M-03.
