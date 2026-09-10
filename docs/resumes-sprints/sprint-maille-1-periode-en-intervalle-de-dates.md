# Résumé Sprint Maille 1 — La période devient un intervalle de dates

**Date :** 10 septembre 2026
**Position :** hors séquence numérotée, entre le sous-sprint 6bis.1 et 6bis.2
**Objet :** remplacer le couple `(mois_paiement, annee_paiement)` par
`(date_debut, date_fin)` dans toute la représentation interne du module
**Livrable :** deux migrations, 65 fichiers de code, 744 tests au vert

---

## En une phrase

Le module ne raisonne plus en mois mais en **intervalle de dates**, ce qui lui
permet de porter le cycle hebdomadaire arbitré au point M-04 — et l'index
d'unicité qui garantissait « un seul état normal par période » est devenu une
contrainte **strictement plus forte**, qui interdit aussi le chevauchement partiel.

---

## 1. Les deux migrations

| Migration | Contenu | Vérifié |
| --- | --- | --- |
| Workflow **V6** | `date_debut` / `date_fin`, conversion des états, `CHECK` d'ordre, contrainte d'exclusion, suppression des anciennes colonnes | 15 états convertis, aucune perte |
| Saisie **V5** | Mêmes bornes sur la recopie figée de `fiche_journaliere`, index de regroupement RG-15 refait | 18 fiches converties |

**Éprouvées en transaction annulée avant d'être livrées.** Une migration Flyway
appliquée est immuable : une erreur dedans se corrige par une migration de plus,
jamais par une retouche (leçon du Sprint 6.3 sur `V1000`). La conversion a été
jouée sur la vraie base, ses résultats lus, puis annulée.

La conversion est **mécanique et sans perte** — un mois *est* un intervalle.
`MAKE_DATE(annee, mois, 1) + INTERVAL '1 month - 1 day'` traite février et les
années bissextiles sans table de correspondance : février 2027 est correctement
borné au 28.

**Les anciennes colonnes sont supprimées, pas conservées « au cas où ».** Deux
représentations de la même période sur la même ligne, c'est deux vérités dont
l'une sera lue par erreur.

## 2. Ce que l'index d'unicité est devenu

C'est le point de conception du sprint.

Avec `(mois, annee)`, deux périodes étaient **égales ou disjointes**, jamais
partiellement superposées : un index unique suffisait. Avec un intervalle, ce
n'est plus vrai. Un index unique sur `(code_unite, date_debut, date_fin)`
laisserait coexister :

```
etat A : du 07/09 au 13/09
etat B : du 10/09 au 16/09
```

Les journées du 10 au 13 appartiendraient à **deux états NORMAL de la même
unité**. RG-15 interroge « les autres états de la même période » : la question
n'aurait plus de réponse définie, et ces journées deviendraient payables deux fois.

D'où une **contrainte d'exclusion PostgreSQL**, arbitrée avec l'utilisateur :

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE processus_mensuel
    ADD CONSTRAINT ex_processus_normal_sans_chevauchement
    EXCLUDE USING gist (
        code_unite WITH =,
        DATERANGE(date_debut, date_fin, '[]') WITH &&
    ) WHERE (type_processus = 'NORMAL');
```

Elle interdit l'égalité **et** le chevauchement, et c'est la base qui la fait
respecter — pas une discipline de code. Vérifié en réel : deux semaines
consécutives `[05-11]` et `[12-18]` passent, `[08-14]` est refusé.

**Bornes inclusives des deux côtés** (`'[]'`) : `date_fin` est le dernier jour de
la période, pas le premier de la suivante. C'est la convention que la base,
l'entité et le contrôle de complétude partagent — en choisir une autre quelque
part aurait fait cohabiter deux lectures du même champ.

**Coût assumé :** l'extension `btree_gist` est un geste de DBA en production.
Inscrite au registre comme point **D-12**.

## 3. La question soulevée par l'utilisateur, et le défaut qu'elle a révélé

*« Refuser systématiquement deux périodes qui se chevauchent ne crée-t-il pas un
trou opérationnel pour l'agent ? »*

La question était fondée. Trois cas existent, dont **un seul** est un vrai trou :

| Cas | Situation | Verdict |
| --- | --- | --- |
| A | Erreur de saisie | Refus juste — c'est le cas fréquent |
| **B** | **Bascule mensuel → hebdomadaire** : l'état de septembre bloque la semaine du 28/09 au 04/10 | **Le vrai trou**, une fois par unité |
| C | Régularisation | Sans objet : la contrainte ne porte que sur `NORMAL` |

**La contrainte est maintenue** — la relâcher pour un cas qui survient une fois par
unité dans toute la vie du module échangerait un inconvénient ponctuel contre un
risque permanent.

**Le cas B se ferme sans une ligne de code**, par une règle d'exploitation : la
bascule se fait sur une **frontière de mois**. Le dernier état mensuel s'arrête le
30 septembre, la première période hebdomadaire commence le 1er octobre.

**Mais le message de refus était mauvais, et c'est le vrai défaut.** Il disait
« Rejoignez ce dossier » — bon conseil sur un état ouvert, **faux** sur un état
clos, qui ne se rejoint pas. Il dépend désormais du statut de l'état en conflit et
nomme, dans le cas clos, les deux issues réelles : l'état complémentaire, ou le
décalage de la borne de début — avec la date écrite dans le message.

## 4. Le Reporting ne pouvait pas être détaché

Un premier découpage prévoyait le Reporting en sprint séparé. **C'était faux et
dangereux**, et la mesure l'a montré : `WorkflowLectureHttpClient` envoie `mois` et
`annee` en paramètres de requête, que le Workflow déclare
`@RequestParam(required = false)`. Si le serveur cesse de les connaître, **Spring
les ignore sans rien dire** : le Reporting recevrait *toute la période* au lieu du
filtre demandé.

Pas d'exception, pas de `400` — un résultat plausible et faux, la pire des trois
propriétés. Les deux côtés ont donc changé ensemble.

**L'API publique du Reporting change aussi.** `periode=AAAA-MM` ne peut pas
exprimer une semaine ; deux paramètres `dateDebut` et `dateFin` en ISO 8601 le
remplacent, et rendent en prime possible un rapport sur n'importe quelle plage —
une semaine, un mois entier, un trimestre.

## 5. Le garde-fou prévu était inutile

Le guide prévoyait d'écrire, côté Transmission, un refus de publier pour les
périodes à cheval sur deux mois. **Il existait déjà** :
`ConstructionChargeService` contrôle `periode.mois() == null` et lève l'anomalie
`PERIODE_INVALIDE`, rendue en `500 CHARGE_INCOMPLETE`.

Il a donc suffi que le Workflow rende un mois **nul** quand la période chevauche
deux mois — **zéro ligne écrite côté Transmission**. L'état n'est pas publié, le
message dit pourquoi, et cela rend la dépendance à la Maille 2 concrète plutôt que
théorique : tant qu'elle n'est pas faite, ces états-là ne partent pas, et cela se
voit.

## 6. Une régression que la compilation ne voyait pas

Le service Transmission compare la période de l'en-tête (Workflow) à celle du
détail (Saisie). La Saisie ne renvoyant plus `moisPaiement`, la comparaison aurait
échoué et **toute transmission aurait été refusée** en `SOURCES_DISCORDANTES`.

La comparaison porte désormais sur les **bornes** — et c'est plus juste : comparer
le mois dérivé laissait passer pour concordantes deux semaines différentes d'un
même mois.

## 7. Deux erreurs de méthode que j'ai commises et corrigées

**① Une passe mécanique a produit un non-sens qui compilait.** Le renommage aveugle
avait donné `int periodeDebut` dans `Rapport` — un numéro de mois sous un nom de
date. Le compilateur était content, et c'était faux. `Rapport`, `RapportService` et
le contrôleur du Reporting ont été repris à la main.

De même dans les tests : `LocalDateTime.of(ANNEE, processus.getMoisPaiement(), 12,
7, 45)` était un usage **légitime** du numéro de mois pour composer une date dans
la période, que la substitution avait cassé.

**② Un `mvn -q` silencieux n'est pas une réussite.** Une exécution filtrée a
rapporté « OK » alors que la compilation des tests échouait : le `-q` masquait
l'erreur et mon `grep` ne trouvait rien. Le silence d'une commande filtrée ne
prouve rien — c'est la même leçon que celle du Sprint 6.3 sur l'audit.

## 8. Les tests

| Suite | Tests | État |
| --- | --- | --- |
| `rations-audit-commun` | 18 | Vert |
| service-identite | 61 | Vert |
| service-saisie | 81 | Vert |
| service-grilles | 99 | Vert |
| **service-workflow** | **344** | Vert |
| service-reporting | 33 | Vert |
| service-transmission | 97 | Vert |
| gateway | 11 | Vert |
| **Total** | **744** | **BUILD SUCCESS** |

**Trois tests ajoutés**, qui verrouillent ce que le sprint a de neuf :

1. **Chevauchement partiel refusé** — le test qui justifie toute la contrainte
   d'exclusion ; il échouerait avec un simple index unique.
2. **Deux périodes consécutives acceptées** — verrouille la convention de bornes
   incluses. Une borne de fin exclusive aurait fait de la seconde un chevauchement.
3. **Message orienté sur un conflit avec un état clos** — vérifie que l'agent
   n'est jamais invité à rejoindre un dossier qu'il ne peut pas rejoindre.

**Les jeux d'essai ont dû être repris**, et pas seulement adaptés : le compteur
`prochainMois()` produisait des périodes qui, converties naïvement, se seraient
chevauchées — et la contrainte d'exclusion les aurait refusées, faisant échouer des
tests sans rapport avec ce qu'ils éprouvent. Un fabricant de période a été introduit,
qui n'évalue le compteur **qu'une fois** : composé deux fois pour former les deux
bornes, il aurait produit une période à cheval sur deux mois différents.

## 9. T-02 corrigé

`@Pattern(regexp = "^[0-9]{11}$")` sur `IdentiteBeneficiaireRequest`, en
remplacement du `@Size(max = 20)` devenu inutile. Le `@NotBlank` est conservé : il
produit un message différent et plus clair sur un champ absent.

**Les 44 bénéficiaires faux ne sont pas corrigés**, conformément à l'arbitrage du
9 septembre : ce sont des données de développement, la base de production démarrera
vide, et corriger à l'aveugle des numéros dont on ignore la valeur vraie
fabriquerait des comptes plausibles mais faux.

## 10. Ce que ce sprint n'a pas fait

- **La charge comptable** — `versionCharge`, `compteCharge`, la période sur le fil.
  C'est la Maille 2, et la DFT a désormais répondu.
- **RG-15** — c'est 6bis.2, qui se débloque ici.
- **La passe documentaire** — préparée depuis le sprint d'ajustement, à exécuter.
- **Le renommage de `processus_mensuel`** — écarté le 9 septembre : la chaîne
  littérale est une **valeur** dans 14 fichiers et dans 193 événements d'audit déjà
  persistés.

## 11. Fichiers

| Périmètre | Fichiers |
| --- | --- |
| Migrations | 2 créations |
| service-workflow | 21 |
| service-saisie | 16 |
| service-reporting | 19 |
| service-transmission | 4 |
| Tests | 29 |

---

**Prochaine étape :** la passe documentaire, puis la Maille 2.
