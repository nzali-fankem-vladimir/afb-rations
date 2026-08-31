# Vérification du statut : câblée dès maintenant, avec un bouchon sous garde-fou

**Sprint 3.3 — 31 août 2026** · Décision tranchée avec l'utilisateur, étape 3.

**Statut :** appliquée. **Comporte une action obligatoire au Sprint 4 : supprimer
le bouchon.**

---

## 1. Le problème

La convention du Sprint 3.1 (`docs/rattachement-processus.md` §4) impose de
vérifier le statut du processus mensuel auprès du service Workflow **à chaque
écriture de ligne**, en refus conservateur : toute réponse autre qu'un `200`
exploitable fait refuser l'écriture.

**Le service Workflow n'existe pas.** L'ordre d'implémentation (CLAUDE.md §14) le
place au Sprint 4, après la Saisie. `service-workflow/src` ne contient à ce jour
que `SecurityConfig`, `RoleJwtConverter` et ses migrations Flyway.

Appliquer la convention telle quelle revient donc à appeler un port fermé, et
donc — refus conservateur oblige — à **refuser toute écriture**.

---

## 2. Ce que chaque voie coûtait

| Voie | Effet |
|---|---|
| **A — câbler sans bouchon** | Toute écriture refusée en `503` jusqu'au Sprint 4. **Pas seulement les écritures sur un état verrouillé : la saisie normale aussi**, puisque Workflow ne répond à personne. Le sous-sprint devient invérifiable à la main, et le service inutilisable. |
| **B — ne pas câbler** | La saisie fonctionne, mais le trou du Sprint 3.2 reste ouvert : rien n'empêche d'écrire dans une fiche dont le processus est déjà soumis, voire clôturé. Le critère de validation « écriture sur un état non modifiable : refusée » ne serait pas tenu — il serait *déclaré* tenu par un test contre un mock, sans qu'aucun code de production ne l'applique. |
| **C — câbler, plus un bouchon activable** | La vérification est réellement câblée sur les quatre écritures ; un dispositif explicite et borné rend le service utilisable en développement. |

**Voie C retenue.** Le raisonnement décisif est celui du coût asymétrique : A
retarde une vérification manuelle, B laisse un défaut de contrôle interne. Un
défaut de contrôle est plus coûteux qu'une gêne d'exploitation, et surtout plus
difficile à rattraper — personne ne se plaint d'un contrôle qui manque.

---

## 3. Ce qui est câblé, pour de bon

- Port `VerificationProcessusClient` (couche `application`), à trois issues
  scellées — même forme que `ResolutionMontantClient` au Sprint 3.2.
- Adaptateur `VerificationProcessusHttpClient` : `RestClient.Builder` **injecté**
  (sans quoi `MockRestServiceServer` ne pourrait pas s'y attacher), délais
  2 s / 3 s, aucun réessai, relais tel quel de l'en-tête `Authorization`.
- `EtatModifiableService` : porte unique, appelée par **les cinq endpoints** —
  écriture et lecture.
- `404` → `ProcessusIntrouvable` ; tout autre non-`200` → refus technique.
- Un **statut inconnu** de `StatutProcessusEnum` produit un refus, jamais une
  autorisation : tolérant à la lecture, fermé à la décision.

**Rien de tout cela n'est vérifié en intégration.** L'URL, le mapping JSON, la
traduction des codes : autant de choses qui « marchent en test » et se
découvrent au Sprint 4. Aucun document de ce sprint ne doit prétendre le
contraire (`docs/rattachement-processus.md` §6.3).

---

## 4. Le bouchon, et ses trois garde-fous

`BouchonVerificationProcessus` répond « ce processus existe et il est
modifiable » sans appeler personne.

1. **Jamais actif par défaut.** Il faut nommer explicitement le profil
   `bouchon-workflow`. Le profil `dev` seul ne suffit pas.
2. **Refus de démarrer hors développement.** Activé sans le profil `dev`, il fait
   **échouer le démarrage du service**. C'est le garde-fou ajouté à la demande de
   l'utilisateur : un avertissement se noie dans les journaux, un service qui ne
   monte pas se remarque à la seconde. C'est la différence entre un dispositif
   qu'on retire et un dispositif qui reste.
3. **Bruyant.** Bannière `WARN` au démarrage, `WARN` à chaque appel. Personne ne
   peut croire que le contrôle a lieu.

S'y ajoute un test qui vérifie que, sous le seul profil `dev`, le bean injecté
est bien le client HTTP réel — le bouchon ne peut pas devenir actif par
inadvertance.

```bash
# Vérification manuelle du Sprint 3.3
mvn -pl service-saisie spring-boot:run \
    -Dspring-boot.run.profiles=dev,bouchon-workflow
```

Réglages sous `app.workflow.bouchon` : `code-unite` (défaut `00002`),
`mois-paiement`, `annee-paiement`, `statut`. Le code unité doit correspondre à
celui de l'agent de test, **sans quoi la vérification d'habilitation refuse
l'écriture** — et c'est bien ce qu'elle doit faire : le bouchon remplace Workflow,
pas Identité.

Régler `statut` sur `SOUMIS` permet de vérifier à la main le `422
ETAT_NON_MODIFIABLE` sans écrire une ligne de code.

---

## 5. Action obligatoire au Sprint 4

| Réf | Action | État |
|---|---|---|
| **B-01** | Supprimer `BouchonVerificationProcessus.java` et l'annotation `@Profile("!bouchon-workflow")` de `VerificationProcessusHttpClient` | **Ouvert** |
| **B-02** | Retirer le bloc `app.workflow.bouchon` de `application-dev.yml` et le mode d'emploi qui l'accompagne | **Ouvert** |
| **B-03** | Vérifier en intégration réelle ce que le bouchon ne prouve pas : URL, mapping JSON de `GET /processus/{id}`, traduction des statuts, comportement au timeout | **Ouvert** |
| **B-04** | Conformer la réponse de `GET /processus/{id}` aux cinq champs lus par `ProcessusReponse` : `idProcessus`, `statut`, `codeUnite`, `moisPaiement`, `anneePaiement` | **Ouvert** |

La suppression est conçue pour tenir en deux gestes : rien d'autre que le port ne
référence le bouchon.

**B-04 est le risque réel de cette décision.** Le contrat de
`GET /processus/{id}` a été *déduit* du contrat d'API §5 — qui donne les noms de
champs de `POST /processus` et de l'exemple de validation, mais **ne décrit pas le
corps de réponse de cet endroit précis**. Si le Sprint 4 nomme ses champs
autrement, le client se taira : `ProcessusReponse` est un *tolerant reader*, les
champs manquants seront nuls, et le service refusera toute écriture en `503`
« réponse 200 sans unité ni période exploitables ». Le message de journal nomme
les champs manquants, précisément pour que ce diagnostic prenne une minute et non
une demi-journée.
