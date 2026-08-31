# Résumé Sprint 3.2 — Valorisation des lignes et contrôle des doublons

**Service :** service-saisie · **Date :** 31 août 2026 · **Config :** Opus / Élevé du début à la fin (exigé par le guide §1)

**Statut :** livré. `mvn -pl service-saisie test` → **BUILD SUCCESS, 26 tests, 0 échec**
(9 du Sprint 3.1 + 17 nouveaux). Backend complet recompilé sans régression.
Cartographie relancée : 2650 nœuds, 4888 liens, **aucun lien
`service-saisie` → `service-grilles`**.
Démarrage réel du service vérifié (`Started ServiceSaisieApplication`, Tomcat 8082) :
les tests instancient le client à la main, seul le démarrage prouve que le bean
`RestClient.Builder` est effectivement injecté — le câblage Spring n'était couvert
par aucun test.

Les deux règles qui protègent le module du paiement erroné et du double paiement.
**RG-03** : le montant vient de la grille, jamais de l'utilisateur — premier appel
inter-services sortant du projet. **RG-04** : un bénéficiaire ne figure pas deux
fois sur la même journée pour la même nature et la même session. Aucun endpoint :
ils viennent au Sprint 3.3.

---

## Décisions prises en cours de route

| # | Décision | Trace | Impact sprints suivants |
|---|---|---|---|
| 1 | **Type de retour scellé à trois cas** (`MontantResolu` / `AucuneGrilleApplicable` / `ServiceGrillesIndisponible`) plutôt qu'un enregistrement plat à drapeau ou deux cas + exception. Motif : **seul `MontantResolu` porte un montant, et c'est un `int` primitif** — le repli `montant != null ? montant : 0` qui ruinerait RG-03 n'est pas écrivable. Le `switch` est exhaustif sans `default` : un quatrième cas ferait échouer la compilation des appelants. | `ResultatResolutionMontant.java` | Modèle réutilisable pour `ClientWorkflow` (Sprint 4). |
| 2 | **`500 INCOHERENCE_GRILLE` rangé dans le refus technique, mais identifiable** : motif distinct, log `ERROR` au préfixe `INCOHERENCE GRILLE`, jamais réessayé. Correction apportée à la forme initiale, approuvée en séance. | `ResolutionMontantHttpClient` ; `docs/appel-resolution-montant.md` §3 | Le préfixe rejoint `INCOHERENCE BENEFICIAIRE` et `AUDIT PERDU` dans les conventions de log du module. |
| 3 | **Délais : 2 s connexion / 3 s lecture. AUCUN réessai** — tranché avec l'utilisateur. Motifs : le Sprint 3.1 empile déjà 3 dépendances synchrones par ligne (9 s → 18 s au pire avec réessai, contre une cible de 3 s) ; l'agent est devant son écran et sa resaisie *est* le réessai ; un réessai masquerait une dégradation de Grilles. | `docs/decisions/2026-08-31-delais-et-reessai-des-appels-sortants.md` | **Doctrine pour tous les clients sortants du service.** `ClientWorkflow` (Sprint 4) la reprend sans réglage propre. À reconsidérer **au Sprint 9 seulement**, et pour le seul cas « connexion refusée pendant une bascule de pod ». |
| 4 | **Piège Spring Boot 4 :** `spring.http.client.connect-timeout` / `read-timeout` **ne fonctionnent pas** dans ce service — `spring-boot-restclient` et `spring-boot-http-client` sont des modules séparés qu'aucun starter n'entraîne. Il n'existe **aucun** bean `RestClient.Builder` par défaut. D'où un bean explicite, en **portée prototype**. | même décision, §7 ; `ConfigurationAppelsSortants.java` | Sans le bean, l'injection échouerait au démarrage et les propriétés YAML seraient du texte mort — **sans erreur**, avec des appels non bornés. Tout futur client sortant passe par ce bean. En singleton, `ClientWorkflow` écraserait la `baseUrl` de `ResolutionMontantHttpClient`. |
| 5 | **Le montant n'a aucune entrée** : `CommandeCreationLigne` ne comporte pas de champ montant — tranché avec l'utilisateur contre l'option « champ documenté comme ignoré », qui créait la porte qu'elle prétendait fermer. Verrouillé par un test qui inspecte les composants du record. | `docs/decisions/2026-08-31-refus-de-ligne-et-codes-erreur-saisie.md` §3 | Sprint 3.3 : le DTO d'entrée suit la même règle ; un `montantApplique` envoyé par un client est ignoré à la désérialisation. |
| 6 | **Quatre refus, quatre codes.** `404 FICHE_INTROUVABLE` et `503 SERVICE_GRILLES_INDISPONIBLE` **ajoutés au contrat** ; `409 DOUBLON_LIGNE` et `422 GRILLE_INDISPONIBLE` en étaient déjà. Le `503` plutôt que `422` reprend le parti du Sprint 2.2 : un `422` enverrait l'agent réclamer un tarif pendant qu'un serveur est à terre. | même document §1–2 | Sprint 3.3 : `GestionnaireErreursApi` du service Saisie à écrire, avec ces quatre correspondances. |
| 7 | **RG-15 anticipée sans une ligne de code.** `ControleDoublonService` expose **un verdict, pas une requête** : les appelants ignorent comment la réponse est obtenue, donc l'extension au Sprint 6bis se fait à l'intérieur, sans toucher aucun appelant. Si `CreationLigneService` interrogeait lui-même le repository, RG-15 devrait être ajoutée dans chaque appelant — et l'oubli d'un seul laisserait passer un double paiement. | `ControleDoublonService.java` | Sprint 6bis : `estDoublonSurLaPeriode(...)` s'appuiera sur `code_unite` / `mois_paiement` / `annee_paiement` recopiés sur `fiche_journaliere` (décision Sprint 3.1). |

---

## Ce qui a été fait — critères de validation du guide (§11)

| Critère | Statut | Détail |
|---|---|---|
| Client de résolution isolé derrière une interface | ✅ | Port `ResolutionMontantClient` dans `application` ; adaptateur `ResolutionMontantHttpClient` dans `infrastructure/grilles`. Justification de la couche : le domaine porte les règles vérifiables sans rien demander à personne ; « quel montant le 10 juillet ? » est une question posée à un autre service, et `ServiceGrillesIndisponible` ferait entrer une notion de panne réseau dans le domaine |
| Trois cas de retour distincts | ✅ | Type scellé ; 6 tests dans `ResolutionMontantClientTest` couvrent les trois cas + `INCOHERENCE_GRILLE` + réponse contradictoire |
| Délai d'attente posé et justifié | ✅ | 2 s / 3 s, `ConfigurationAppelsSortants`, alignés sur `ClientIdentite` (Sprint 2.2) |
| Question du réessai tranchée | ✅ | Aucun réessai, décidé avec l'utilisateur, consigné en décision |
| RG-04 portant sur la combinaison complète | ✅ | 5 tests **contre PostgreSQL réel** : fiche × bénéficiaire × nature × session |
| Extension future à RG-15 anticipée sans être implémentée | ✅ | Verdict et non requête (décision 7). Aucune ligne de code RG-15 écrite |
| Montant utilisateur systématiquement ignoré | ✅ | Aucun champ montant dans la commande ; test structurel + test comportemental |
| Messages de refus explicites et distincts | ✅ | 4 exceptions, 4 messages, 4 codes. Le motif technique reste au journal, jamais dans le message à l'agent |
| Dix tests passants | ✅ | **17 nouveaux tests**, dont les 10 exigés. `mvn -pl service-saisie test` → BUILD SUCCESS, 26 / 0 échec |
| Aucun accès direct à la base d'un autre service | ✅ | Aucune datasource vers `rations_grilles` (seules des mentions en commentaire), aucun import `...rations.grilles.*`, `mvn dependency:list` → `rations-audit-commun` seul. Cartographie : **aucun lien `service-saisie` → `service-grilles`**, d'aucune sorte — l'appel n'existe qu'en HTTP, à l'exécution |

---

## Les 17 tests

**`ResolutionMontantClientTest` — 6 tests, bouchon `MockRestServiceServer`, sans réseau réel**

| Test | Ce qu'il prouve |
|---|---|
| `200 disponible=true` | Montant + grille repris tels quels ; URL portant `date=2026-07-10` ; en-tête `Authorization` relayé tel quel |
| `200 disponible=false` | Absence de tarif lue dans le **champ**, jamais dans un code HTTP |
| connexion refusée | Refus technique, distinct de l'absence de tarif |
| `500` | Un `5xx` ne dit pas qu'il n'y a pas de tarif |
| `500 INCOHERENCE_GRILLE` | Motif identifiable ; **un seul appel émis**, aucun réessai |
| `disponible=true` sans montant | Refus technique, **jamais un repli sur `0`** |

**`ControleDoublonServiceTest` — 5 tests (6 à 10 du guide), contre PostgreSQL réel**

Un test à base de Mockito prouverait seulement que le service délègue au
repository — c'est-à-dire rien sur la règle. Ce qu'il faut prouver, c'est que le
SQL distingue réellement quatre éléments.

| # | Cas | Verdict |
|---|---|---|
| 6 | même bénéficiaire, même jour, même nature, même session | **doublon** |
| 7 | nature différente | accepté |
| 8 | session différente | accepté |
| 9 | journée différente | accepté |
| 10 | bénéficiaire différent | accepté |

Les tests 7 à 10 sont les plus importants : une règle d'unicité **trop large** est
plus difficile à détecter qu'une règle absente — elle ne produit pas de double
paiement, elle empêche un agent de la garde d'être payé pour une prestation
réellement effectuée, et personne ne s'en plaint dans les journaux.

**`CreationLigneServiceTest` — 6 tests (1 à 5 du guide + ordre des contrôles), Mockito**

| # | Cas | Ce qu'il prouve |
|---|---|---|
| 1 | ligne valide | Montant **et grille** figés depuis la réponse ; audit `CREATION_LIGNE_PRESTATION` publié |
| 2 | aucune grille | `GrilleIndisponibleException`, message nommant nature / session / date et l'action attendue ; rien enregistré |
| 3 | Grilles injoignable | Exception **d'un autre type**, message « réessayez » sans motif technique ; rien enregistré ; **un seul appel** |
| 4 | montant utilisateur | Aucun composant « montant » dans la commande (réflexion) + montant enregistré = celui de la grille |
| 5 | saisie rétroactive | La date interrogée est celle de la **fiche** (10 juillet), explicitement `isNotEqualTo(LocalDate.now())` |
| — | doublon | Refus RG-04 **sans aucune interaction** avec le client Grilles ; message citant les quatre éléments |

---

## Lecture de la cartographie — ce qu'elle montre exactement

`service-saisie` n'a **aucun lien** vers `service-grilles` : ni import, ni appel,
ni référence. Le seul lien sortant du service va vers `rations-audit-commun`
(22 liens), mutualisation sanctionnée par CLAUDE.md §3.

Le graphe fait apparaître un lien `service-identite → service-grilles`. Il est
**sans rapport avec ce sous-sprint et sans réalité** : deux fichiers de *test*
(`UtilisateurAdminServiceTest` L67 et `DecisionGrilleServiceTest` L135)
contiennent chacun un symbole nommé `Validation`, que l'extracteur AST rapproche
par homonymie. Aucun import, aucun code de production. À ne pas confondre avec
une dépendance en cas de relecture ultérieure du graphe.

---

## Points ouverts créés par ce sous-sprint

**1. L'index unique manque encore — Sprint 3.3.** RG-04 est appliquée par une
lecture avant écriture : deux requêtes concurrentes (double clic, deux onglets)
peuvent franchir le contrôle toutes les deux et produire deux paiements. Le
verrou définitif est
`UNIQUE (id_fiche_journaliere, id_beneficiaire, nature, session)`, migration
additive `V4`, avec traduction de `DataIntegrityViolationException` en
`409 DOUBLON_LIGNE` — exactement ce que le service Grilles fait déjà pour
`ux_grille_active_par_couple`. Le contrôle applicatif reste : c'est lui qui
produit le message nommant les quatre éléments. L'index est le filet, pas le
message.

**2. Le statut du processus n'est pas vérifié — Sprints 3.3 et 4.** Le Sprint 3.1
avait acté une vérification auprès du service Workflow à chaque écriture de
ligne, en refus conservateur. Workflow n'existe qu'au Sprint 4. **Rien n'empêche
donc aujourd'hui d'écrire une ligne dans une fiche dont le processus est déjà
soumis, voire clôturé.** Documenté en tête de `CreationLigneService` ; aucun
document du Sprint 3 ne doit laisser croire que ce garde-fou est en place.

**3. `CLAUDE.md` §17 n'est pas encore mis à jour**, conformément à ce qu'avait
prévu le Sprint 3.1 : les décisions du Sprint 3 y seront reportées **à la clôture
du Sprint 3**, en une fois.

---

## Fichiers

**Créés — code (9) :**
`application/ResolutionMontantClient.java`,
`application/ResultatResolutionMontant.java`,
`application/ControleDoublonService.java`,
`application/CommandeCreationLigne.java`,
`application/CreationLigneService.java`,
`infrastructure/grilles/ResolutionMontantHttpClient.java`,
`infrastructure/grilles/MontantApplicableReponse.java`,
`infrastructure/grilles/ErreurRemontee.java`,
`infrastructure/config/ConfigurationAppelsSortants.java`.

**Créés — exceptions (4) :** `domaine/exception/DoublonLigneException.java`,
`GrilleIndisponibleException.java`, `ServiceGrillesIndisponibleException.java`,
`FicheIntrouvableException.java`.

**Créés — tests (3) :**
`application/ControleDoublonServiceTest.java`,
`application/CreationLigneServiceTest.java`,
`infrastructure/grilles/ResolutionMontantClientTest.java`.

**Créés — docs :**
`docs/decisions/2026-08-31-delais-et-reessai-des-appels-sortants.md`,
`docs/decisions/2026-08-31-refus-de-ligne-et-codes-erreur-saisie.md`,
ce résumé.

**Modifiés :** `backend/service-saisie/src/main/resources/application-dev.yml`
(`app.grilles.url`).

---

## Suite

Sprint 3.3 — endpoints de saisie. Y sont attendus : les cinq endpoints du
contrat, le `GestionnaireErreursApi` du service avec les quatre correspondances
d'erreur, le relais de l'en-tête `Authorization` jusqu'au client Grilles, la
migration `V4` de l'index unique, et le renseignement de `idUtilisateur` /
`adresseIp` dans les traces d'audit — nuls aujourd'hui faute de contexte HTTP.
