# Résumé Sprint 3.1 — Domaine de la saisie : bénéficiaire, fiche et ligne

**Service :** service-saisie · **Date :** 28 août 2026 · **Config :** Sonnet / Moyen (étapes 2–4 et 6), Opus / Élevé (étape 5, arbitrage d'architecture)

**Statut :** livré. `mvn -pl service-saisie test` → **BUILD SUCCESS, 9 tests, 0 échec**. Backend complet recompilé sans régression. Migration `V2` appliquée à `rations_saisie`.

Premier sous-sprint du service Saisie, cœur métier du module. Objectif : le code
qui exploite les trois tables existantes depuis le Sprint 0.5 (`beneficiaires`,
`fiche_journaliere`, `ligne_prestation`) — entités, repositories, résolution du
bénéficiaire au fil de la saisie — **sans aucun endpoint**. Plus un arbitrage
d'architecture : le rattachement de la fiche à un processus mensuel qui vit dans
une autre base.

---

## Décisions prises en cours de route

| # | Décision | Trace | Impact sprints suivants |
|---|---|---|---|
| 1 | **Champ `actif` NON ajouté à `Beneficiaire`.** Le texte de l'étape 1 du guide le listait comme « conforme au dictionnaire », mais il n'est ni dans CLAUDE.md §4 ni dans la migration `V1`, et aucun besoin métier ne le réclame (pas d'enrôlement, pas d'écran de gestion). L'entité s'en tient au dictionnaire : `id, nom, prenom, numCompteCourant, codeAgence, dateCreation`. | `docs/decisions/2026-08-28-incoherence-guide-31-champ-actif-beneficiaire.md` | Aucun. Si le besoin de désactiver un bénéficiaire apparaît, migration additive dédiée à ce moment-là. |
| 2 | **Colonne `id_grille` ajoutée à `ligne_prestation` par migration additive `V2`** (`BIGINT`, nullable, sans FK inter-base). Dette identifiée au Sprint 2.4 : l'endpoint `GET /grilles/active` renvoie l'identité de la grille pour justifier a posteriori un montant contesté, mais cette traçabilité n'avait nulle part où atterrir. | `V2__ligne_prestation_ajout_id_grille.sql` ; dette consignée dans `docs/decisions/2026-08-27-resolution-du-montant-applicable.md` §5 et CLAUDE.md §17 | Sprint 3.2 : la valorisation renseignera `id_grille` en même temps que `montant_applique`. |
| 3 | **Identifiants plats intra-base** (`idFicheJournaliere`, `idBeneficiaire`, `idGrille`, `idProcessus` en `Long`), pas d'associations JPA — **bien que les FK réelles existent en base** pour les deux références intra-`rations_saisie`. Motif : `ligne_prestation` est la table de contrôle à fort volume (RG-04, RG-15), jamais navigée ; une association `@ManyToOne` même `LAZY` ouvrirait un chargement transitif non voulu. **Ce n'est PAS une règle « toujours plat comme `GrilleTarifaire` »** : le critère est le profil d'accès, pas le mimétisme. | `docs/decisions/2026-08-28-identifiants-plats-dans-service-saisie.md` | Une autre table de ce service, avec un profil « agrégat lu et affiché avec ses enfants », pourra légitimement utiliser une vraie association. |
| 4 | **Identité du bénéficiaire = numéro de compte courant SEUL.** Le nom/prénom se tape à la main à chaque saisie ; l'inclure dans la clé ferait de toute faute de frappe un doublon qui casserait RG-04. Repository : `findByNomAndPrenomAndNumCompteCourant` (prévu par le texte de l'étape 3 *avant* l'arbitrage) → `findByNumCompteCourant`. | `docs/decisions/2026-08-28-resolution-beneficiaire-et-incoherence-nom.md` §Décision 1 | Sprint 3.2 : reconsidérer un index `UNIQUE (num_compte_courant)` pour rendre l'invariant structurel (non fait ici : hors périmètre, service vierge). |
| 5 | **Compte connu, nom enregistré différent → on conserve l'existant intact, on ne bloque pas, on trace deux fois** (log `WARN` préfixe `INCOHERENCE BENEFICIAIRE` + événement d'audit `INCOHERENCE_BENEFICIAIRE`). **Comparaison normalisée avant de déclencher la trace** : `trim`, espaces multiples, casse, accents (`Normalizer.NFD`) — sinon l'audit serait noyé de bruit. | même doc, §Décision 2 | **Point ouvert Sprint 4** ci-dessous. |
| 6 | **Rattachement au processus mensuel : l'agent déclenche, la Saisie vérifie** (option 1). Saisie ne crée jamais de processus ; elle en vérifie un via `GET /processus/{id}` (Workflow) avant toute écriture, en **refus conservateur** si Workflow est muet. Options « Saisie crée » (condamne l'état COMPLEMENTAIRE) et « projection Kafka » (4ᵉ topic, cohérence différée sur une donnée financière) écartées. | `docs/rattachement-processus.md` §3 | **À reporter dans CLAUDE.md §17 à la clôture du Sprint 3.** Sprint 3.2 : écrire `ClientWorkflow`. |
| 7 | **Vérification du statut du processus à CHAQUE écriture de ligne**, jamais mise en cache — `POST /saisie/lignes` reçoit `idFicheJournaliere` directement, un contrôle limité à l'ouverture laisserait écrire dans un état déjà soumis. Coût assumé : **3 dépendances synchrones empilées par ligne** (Grilles + Workflow + Identité) + RG-04/RG-15 en base. | même doc, §4 | Sprint 3.2 : Saisie devient consommateur de `/identite/habilitation` → **doit publier `ACCES_REFUSE`** sur verdict négatif *et* indisponibilité (guide §0.2). |
| 8 | **Recopie figée de `code_unite`, `mois_paiement`, `annee_paiement` sur `fiche_journaliere`** (migration `V3` additive, au Sprint 3.2). Sans ça, RG-15 (« même unité, même période ») est infaisable localement : Saisie ne connaît que des `id_processus` opaques. Ces trois valeurs sont immuables pour un processus (index `ux_processus_normal_par_periode`), la copie ne périme jamais. Le `statut`, mutable, n'est jamais copié. | même doc, §5 | Sprint 3.2 : migration `V3` + remplissage à l'ouverture de la fiche. Sprint 8 : RG-15 devient une requête locale. |
| 9 | **Le service Workflow n'existe pas encore** (ordre d'implémentation CLAUDE.md §14 : Saisie au Sprint 3, Workflow au Sprint 4). `ClientWorkflow` sera écrit au 3.2 et **testé contre un bouchon `MockRestServiceServer`** ; l'intégration réelle n'est vérifiable qu'au **Sprint 4**. Contrainte de conception : `ClientWorkflow` doit recevoir un `RestClient.Builder` **injecté** (pas construit en dur comme `ClientIdentite`), sinon le bouchon ne s'y attache pas. | même doc, §6 | **Aucun document du Sprint 3 ne doit affirmer que l'intégration Saisie → Workflow fonctionne.** Elle est *codée selon un contrat écrit*, pas *vérifiée*. |

---

## Ce qui a été fait — critères de validation du guide (§11)

| Critère | Statut | Détail |
|---|---|---|
| Trois entités conformes au dictionnaire | ✅ | `Beneficiaire` (6 champs), `FicheJournaliere` (5), `LignePrestation` (8 avec `id_grille`) ; `nature`/`session`/`statut` typés par énumérations ; Hibernate `ddl-auto: validate` passe au démarrage des tests |
| Aucune entité de processus créée dans ce service | ✅ | `idProcessus` est un `Long`. Aucune classe `ProcessusMensuel`, aucune association JPA vers Workflow |
| Trois repositories avec leurs six recherches | ✅ | Bénéficiaire : `findByNumCompteCourant`, `findByNomContainingIgnoreCase` (paginée) · Fiche : `findByIdProcessusAndDateJour` (RG-05), `findByIdProcessus` (conso. 3.4) · Ligne : `findByIdFicheJournaliere`, `existsByIdFicheJournaliereAndIdBeneficiaireAndNatureAndSession` (RG-04) |
| Critère d'identification du bénéficiaire tranché | ✅ | Compte courant seul (décision 4) |
| Comportement compte connu / nom différent tranché | ✅ | Conserver + tracer, comparaison normalisée (décision 5) |
| Convention de rattachement au processus arrêtée et documentée | ✅ | `docs/rattachement-processus.md` (décisions 6–9) |
| Huit tests passants | ✅ | 9 tests, couvrant les 8 scénarios du guide. **`mvn -pl service-saisie test` → BUILD SUCCESS, 9 / 0 échec** |
| Aucun référentiel ni import de bénéficiaires | ✅ | `ResolutionBeneficiaireService` est le seul point d'entrée ; création à la première saisie, jamais en lot |
| Aucune dépendance vers un autre service | ✅ | `mvn dependency:tree` : service-saisie → `rations-audit-commun` uniquement (mutualisation sanctionnée CLAUDE.md §3). Cartographie graphify relancée : 2525 nœuds, aucune arête service→service |
| Aucun endpoint créé | ✅ | Aucun `@RestController` |
| `code_agence` présent, `code_unite` absent du schéma | ✅ | `\d beneficiaires` : `code_agence VARCHAR(5) NOT NULL`, aucun `code_unite`, aucun `actif` |

---

## Les 9 tests (8 scénarios du guide)

| # | Test | Fichier | Base ? |
|---|---|---|---|
| 1 | Bénéficiaire inconnu → créé et retourné | `ResolutionBeneficiaireServiceTest` | Mockito |
| 2 | Bénéficiaire connu, identité identique → existant, sans doublon ni trace | idem | Mockito |
| 3a | Compte connu, nom réellement différent → existant **intact** + audit `INCOHERENCE_BENEFICIAIRE` (prouve décisions 1 **et** 2) | idem | Mockito |
| 3b | Écart de casse / espaces / accents seul → **aucune trace** (condition de normalisation) | idem | Mockito |
| 4 | Recherche fiche par processus + date → retrouve la bonne | `FicheJournaliereRepositoryTest` | PostgreSQL réel |
| 5 | Recherche fiche pour une date sans fiche → rien | idem | PostgreSQL réel |
| 6 | Existence ligne vraie sur combinaison présente (RATION / JOUR) | `LignePrestationRepositoryTest` | PostgreSQL réel |
| 7 | Existence **fausse** quand la session diffère (RATION / SOIR) | idem | PostgreSQL réel |
| 8 | Existence **fausse** quand la nature diffère (TRANSPORT / JOUR) | idem | PostgreSQL réel |

Tests 7 et 8 = les plus importants : ils prouvent que RG-04 portera sur la
combinaison complète, pas sur le seul bénéficiaire. Données camerounaises, code
guichet réel `00002` (Douala Bonanjo).

---

## Héritage Sprint 1.3 appliqué

- **§0.1** — `rations-audit-commun` dans le `pom.xml` de service-saisie ;
  `spring.kafka.bootstrap-servers` + `spring.jpa.hibernate.ddl-auto: validate` +
  `open-in-view: false` dans `application-dev.yml`. Starter `spring-boot-starter-data-jpa-test`
  ajouté (tests de repository contre PostgreSQL). Le port `PublicateurAudit` est
  injecté par `ResolutionBeneficiaireService` (trace `INCOHERENCE_BENEFICIAIRE`).
- **§0.2** — sans objet **ce sous-sprint** : service-saisie ne consomme pas
  encore `/identite/habilitation` (aucun endpoint). **À câbler au Sprint 3.2**,
  avec publication obligatoire de `ACCES_REFUSE` (décision 7).

---

## Point ouvert — Sprint 4

**La trace `INCOHERENCE_BENEFICIAIRE` est un filet a posteriori, pas un contrôle
préventif.** Rien n'empêche une ligne incohérente d'être enregistrée, consolidée,
validée puis transmise. L'événement n'a d'utilité que si quelqu'un le regarde
avant que l'argent parte. Besoin à traiter au Sprint 4 : le Chef d'Unité (DA) et
le Directeur Réseau (DR) qui valident un état doivent **voir les lignes marquées
`INCOHERENCE_BENEFICIAIRE` avant de clôturer**.
(`docs/decisions/2026-08-28-resolution-beneficiaire-et-incoherence-nom.md`
§Point ouvert.)

---

## Fichiers

**Créés — code :** `domaine/Beneficiaire.java`, `domaine/FicheJournaliere.java`,
`domaine/LignePrestation.java`, `domaine/StatutFicheEnum.java`,
`domaine/NatureEnum.java`, `domaine/SessionEnum.java`,
`infrastructure/BeneficiaireRepository.java`,
`infrastructure/FicheJournaliereRepository.java`,
`infrastructure/LignePrestationRepository.java`,
`application/ResolutionBeneficiaireService.java`,
`resources/db/migration/V2__ligne_prestation_ajout_id_grille.sql`,
`src/test/.../application/ResolutionBeneficiaireServiceTest.java`,
`src/test/.../infrastructure/FicheJournaliereRepositoryTest.java`,
`src/test/.../infrastructure/LignePrestationRepositoryTest.java`.

**Créés — docs :** `docs/rattachement-processus.md`,
`docs/decisions/2026-08-28-incoherence-guide-31-champ-actif-beneficiaire.md`,
`docs/decisions/2026-08-28-identifiants-plats-dans-service-saisie.md`,
`docs/decisions/2026-08-28-resolution-beneficiaire-et-incoherence-nom.md`,
ce résumé.

**Modifiés :** `backend/service-saisie/pom.xml` (audit-commun + starter data-jpa-test),
`backend/service-saisie/src/main/resources/application-dev.yml` (kafka + jpa validate + open-in-view).

---

## Suite

Sprint 3.2 — valorisation et contrôle des doublons (RG-03, RG-04). Écriture de
`ClientWorkflow` (bouchon), `ClientGrilles` (résolution du montant, refus
conservateur), migration `V3` (recopie `code_unite`/`mois`/`annee`), publication
de `ACCES_REFUSE`.
