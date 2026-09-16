# Structure du rapport d'activité (Sprint 6.2) — vocabulaire « envoyé » vs « payé »

> ⚠️ **Lire à la lumière du sprint Maille 1 (10 septembre 2026).** Ce document décrit
> l'état du module **à sa date**, quand la période de paiement était un mois porté par
> le couple `(mois_paiement, annee_paiement)`. Le métier a depuis établi que le cycle
> est **hebdomadaire** (point M-04), et la période est devenue un intervalle de dates
> `(date_debut, date_fin)`. Ce qui est écrit ici reste vrai de son époque et n'est
> **pas** réécrit : un enregistrement daté qu'on corrige après coup cesse d'être un
> enregistrement. Voir
> `docs/decisions/2026-09-09-rythme-de-paiement-et-maille-de-la-periode.md` et
> `docs/resumes-sprints/sprint-maille-1-periode-en-intervalle-de-dates.md`.

**Date :** 4 septembre 2026
**Sprint :** 6.2, rapports d'activité et exports
**Statut :** structure arbitrée avec l'utilisateur avant tout codage (guide 6.2, étape 1)
**Règles :** US-16, CT-32, CT-33

---

## 1. Ce que contient le rapport

`GET /reporting/rapports?periode=AAAA-MM[&codeUnite=NNNNN]` — rôle ARH, portée
d'accès résolue en amont par le service Workflow depuis le jeton relayé (jamais un
paramètre, doctrine Sprint 6.1).

Source unique : **un seul appel** à `GET /processus/recherche` (Workflow), sans
critère de ligne, donc la Saisie n'est jamais interrogée pour ce rapport. Le grain
reste **l'état mensuel** (`processus_mensuel`), jamais la ligne de prestation ni le
bénéficiaire.

| Bloc | Contenu |
| --- | --- |
| En-tête | Période en toutes lettres · agence (ou « Toutes les unités ») · date de génération · login de l'utilisateur qui produit le rapport (`preferred_username` du jeton) · nombre d'états |
| Détail (une ligne par état) | N° dossier · unité · type (NORMAL/COMPLEMENTAIRE) · statut d'avancement · montant total FCFA · envoyé à la comptabilité (Oui/Non) · situation d'intégration · date de création |
| Sous-totaux par agence | **Seulement si `codeUnite` absent** : nombre d'états + montant cumulé par unité |
| Synthèse générale | voir §2 |

**Exclu volontairement** (point de vigilance §10 du guide) : aucun graphique,
aucune moyenne, aucune évolution mois sur mois, aucun détail nominatif des visas.

### Garantie sur le détail nominatif (arbitrage Q1)

Le rapport ne porte **pas** le login + la date de chaque validation par état : cela
imposerait un appel d'historique par état, contraire à la règle « 1 à 2 appels » du
Sprint 6.1. L'ARH qui a besoin du détail nominatif d'un dossier précis passe par
`GET /reporting/processus/{id}/historique`, **déjà livré au Sprint 6.1**. Le rapport
national ne duplique pas cet endpoint.

---

## 2. Le bloc synthèse, et le piège du mot « transmis »

`transmis_comptabilite = true` signifie seulement que **le message est parti sur le
topic** `rations.etat.valide`. Il ne signifie pas que l'argent a été versé : un état
peut être `transmis_comptabilite = true` **et** porter `statut_integration = REJETE`
— la comptabilité a refusé le message après coup, rien n'a été payé.

Sans précaution de vocabulaire, un lecteur pressé lit « 84 000 FCFA transmis » et
comprend « 84 000 FCFA payés aux agents », alors qu'une partie a pu être rejetée.
Il faudrait creuser la répartition par situation d'intégration pour s'en apercevoir.

### Décision

1. **La ligne de synthèse s'appelle « Montant envoyé à la comptabilité »**, jamais
   « transmis » ni « payé ». Elle vaut la somme des `montantTotal` des états dont le
   drapeau de transmission est posé. C'est le chiffre vérifié par le test 7 de CT-32.
2. **Une ligne distincte « Montant rejeté par la comptabilité »** est ajoutée à la
   synthèse, calculée séparément : somme des `montantTotal` des états dont la
   situation d'intégration vaut `REJETE`. Elle n'est pas noyée dans la répartition
   détaillée — elle a sa propre ligne, au même niveau que les autres montants.

Bloc synthèse complet :

| Ligne | Calcul |
| --- | --- |
| Nombre d'états | compte |
| Montant total de la période | Σ `montantTotal` |
| Montant envoyé à la comptabilité | Σ `montantTotal` où `transmisComptabilite = true` |
| Montant non envoyé à la comptabilité | Σ `montantTotal` où `transmisComptabilite = false` |
| **Montant rejeté par la comptabilité** | Σ `montantTotal` où `situationIntegration = REJETE` |
| Répartition des états par statut d'avancement | compte par statut |
| Répartition des états par situation d'intégration | compte par situation |

---

## 3. Un seul calcul, trois usages (CT-32)

`RapportService.produire(...)` rend un objet domaine `Rapport` dont **tous les
totaux sont déjà calculés**. `ExportPdfService` et `ExportExcelService` reçoivent
cet objet et ne font que le rendre — aucune addition de montant chez eux. Le
contrôleur de consultation sérialise le même objet. Un chemin de calcul, trois
sorties identiques par construction. Le test 6 (écran = PDF = Excel) est le verrou.

---

## 4. Période sans données (CT-33)

Rapport produit normalement : 0 état, tous les totaux à 0, indicateur `vide = true`,
et une mention explicite « Aucune activité enregistrée pour cette période. » dans le
PDF comme dans l'Excel. Jamais une erreur, jamais un 404 — l'absence d'activité est
une information en soi.

---

## 5. Portée pour le frontend (Sprint 7F)

Le choix de vocabulaire « envoyé / rejeté / non envoyé » plutôt que « transmis /
payé » est **contractuel côté affichage** : l'écran de rapport du frontend doit
reprendre ces libellés tels quels, et ne jamais présenter le montant envoyé comme un
montant payé.
