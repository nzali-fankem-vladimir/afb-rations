# `dispositifs_provisoires.md` prescrit une colonne qui n'existe pas

**Date :** 18 septembre 2026
**Sprint :** 7F.7, étape 7 (vérification de bout en bout)
**Portée :** documentation uniquement, aucun code touché

---

## Le fait

`docs/dispositifs_provisoires.md`, sections 1.2 et 1.5, donne des exemples SQL
d'`INSERT` et d'`UPDATE` sur `parametre_systeme` qui renseignent une colonne
`date_modification`. Cette colonne **n'existe pas** : `V1__creation_tables_workflow.sql`
déclare la table avec cinq colonnes seulement (`id`, `code`, `libelle`, `valeur`,
`actif`), et aucune migration ultérieure (V2 à V8) n'en ajoute.

Constaté en réel le 18 septembre 2026 : la commande `UPDATE parametre_systeme SET
valeur = 'false', date_modification = CURRENT_TIMESTAMP WHERE code = '...'`,
copiée telle quelle depuis le document, échoue avec
`column "date_modification" of relation "parametre_systeme" does not exist`.

## Pourquoi ce n'est pas une surprise

CLAUDE.md section 4 le dit explicitement, dans la description de la convention
transverse `date_creation` : *« sauf `parametre_systeme` (sans horodatage
propre) »*. Le document `dispositifs_provisoires.md` a donc toujours été en
contradiction avec le dictionnaire de données sur ce point précis — un écart de
documentation, pas une régression de code.

## Correction

Aucun code n'est modifié : la table n'a pas besoin d'horodatage pour ce que le
dispositif exige (un drapeau lu au chargement, rien de plus). Seule la
commande de contrôle à utiliser change :

```sql
UPDATE parametre_systeme SET valeur = 'false' WHERE code = 'RATTRAPAGE_ACTIF';
-- puis, apres verification :
UPDATE parametre_systeme SET valeur = 'true' WHERE code = 'RATTRAPAGE_ACTIF';
```

`docs/dispositifs_provisoires.md` reste à corriger sur ce point précis (retirer
`date_modification` de ses exemples SQL, sections 1.2 et 1.5) — non fait dans
cette note pour ne pas modifier un document déjà volumineux au milieu d'une
vérification ; à faire au prochain toilettage de ce fichier.

## Portée pour les sprints suivants

Tout exemple SQL copié depuis `dispositifs_provisoires.md` touchant
`parametre_systeme` doit être vérifié contre `V1__creation_tables_workflow.sql`
avant exécution, pas recopié tel quel.
