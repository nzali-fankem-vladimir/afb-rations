# Refus d'une ligne de prestation : quatre situations, quatre codes

**Sprint 3.2 — 31 août 2026**

Le service Saisie sait désormais refuser une ligne. Ce document fixe ce que
chaque refus signifie, le code que le Sprint 3.3 devra lui donner, et pourquoi
les confondre serait coûteux.

---

## 1. Les quatre refus

| Situation | Exception (domaine) | Code / statut attendu au Sprint 3.3 | Ce que l'agent doit faire |
|---|---|---|---|
| La fiche journalière n'existe pas | `FicheIntrouvableException` | `404 FICHE_INTROUVABLE` | corriger la référence |
| RG-04 : bénéficiaire déjà servi ce jour-là, même nature, même session | `DoublonLigneException` | `409 DOUBLON_LIGNE` *(code du contrat d'API §3)* | corriger sa saisie |
| RG-03 : aucune grille ne couvre la date | `GrilleIndisponibleException` | `422 GRILLE_INDISPONIBLE` *(code du contrat d'API §3)* | attendre qu'une grille soit proposée par l'ARH et validée par la DRH |
| Le service Grilles n'a rien répondu d'exploitable | `ServiceGrillesIndisponibleException` | `503 SERVICE_GRILLES_INDISPONIBLE` | réessayer plus tard |

Deux codes existaient déjà au contrat (`DOUBLON_LIGNE`, `GRILLE_INDISPONIBLE`).
Deux sont **ajoutés** : `FICHE_INTROUVABLE`, sur le modèle de `GRILLE_INTROUVABLE`
(Sprint 2.3), et `SERVICE_GRILLES_INDISPONIBLE`, sur celui de
`SERVICE_IDENTITE_INDISPONIBLE` (Sprint 2.2).

---

## 2. Pourquoi le service en panne rend `503` et non `422`

C'est le même arbitrage qu'au Sprint 2.2 pour le service Identité, transposé.

Un `422` dit à l'agent : *votre saisie viole une règle de gestion*. Il l'enverrait
donc réclamer une grille tarifaire à l'analyste RH — démarche inutile, puisque la
grille existe peut-être parfaitement — pendant que la panne réelle resterait
invisible à tout le monde. Le `503` dit ce qui est vrai : le système ne peut pas
répondre pour l'instant, l'action attendue est d'attendre.

**Le corollaire vaut d'être écrit : le message rendu à l'agent ne contient jamais
le motif technique.** L'adresse du service appelé, la trace d'infrastructure, le
code de statut reçu — tout cela va au journal, où l'exploitant le lira. L'agent y
apprendrait seulement à s'inquiéter.

---

## 3. Le montant n'a pas d'entrée

`CommandeCreationLigne` **ne comporte aucun champ montant**, et c'est délibéré
(RG-03, point de vigilance n°1 du guide 3.2).

L'option écartée était un champ documenté « proposé, jamais utilisé », avec un
test prouvant qu'il n'atteint pas la ligne. Elle rendait le test plus direct,
mais créait exactement la porte qu'elle prétendait fermer : le champ existerait
dans le code, et un développeur pourrait le câbler en croyant réparer un oubli.

La façon la plus sûre d'ignorer une valeur est de n'avoir aucun endroit où la
mettre. C'est aussi le contrat d'API tel qu'il est écrit : `POST /saisie/lignes`
ne prévoit pas de montant en entrée.

**Verrouillé par un test** (`CreationLigneServiceTest`, test 4) qui inspecte les
composants du record : ajouter un champ dont le nom contient « montant » fait
échouer la construction avant qu'aucune valeur n'ait pu atteindre une ligne.

**Conséquence pour le Sprint 3.3 :** le DTO d'entrée de l'endpoint suit la même
règle. Si un client envoie tout de même un `montantApplique` dans son JSON, le
champ est ignoré à la désérialisation — jamais lu, jamais mappé.

---

## 4. Point ouvert pour le Sprint 3.3 — l'index unique manquant

`ControleDoublonService` applique RG-04 **par une lecture avant écriture**. Il
laisse donc une fenêtre : deux requêtes concurrentes portant la même combinaison
peuvent la franchir toutes les deux et produire deux lignes identiques — donc
deux paiements.

Le cas est peu probable (un agent saisit ligne à ligne) mais pas impossible :
double clic, deux onglets ouverts, un client qui rejoue une requête.

**Action attendue au Sprint 3.3** — migration additive `V4` :

```sql
CREATE UNIQUE INDEX ux_ligne_par_fiche_beneficiaire_nature_session
    ON ligne_prestation (id_fiche_journaliere, id_beneficiaire, nature, session);
```

et traduction de `DataIntegrityViolationException` en `409 DOUBLON_LIGNE` dans
`GestionnaireErreursApi`, comme le service Grilles le fait déjà pour
`ux_grille_active_par_couple` (Sprint 2.2).

Le contrôle applicatif ne disparaît pas pour autant : c'est lui qui produit le
message nommant le bénéficiaire, la journée, la nature et la session. L'index
est le filet, pas le message.

---

## 5. Ce qui n'est délibérément pas contrôlé à ce stade

Le Sprint 3.1 a acté que le **statut du processus mensuel** serait vérifié auprès
du service Workflow à chaque écriture de ligne, en refus conservateur
(`docs/rattachement-processus.md` §4). Ce contrôle **n'existe pas encore** : le
service Workflow n'est écrit qu'au Sprint 4.

Conséquence à assumer et à ne pas oublier : **rien n'empêche aujourd'hui d'écrire
une ligne dans une fiche dont le processus est déjà soumis, voire clôturé.** La
limite est documentée en tête de `CreationLigneService`. Aucun document du
Sprint 3 ne doit laisser croire que ce garde-fou est en place.
