# Décision — Endpoint d'écriture des paramètres système (Sprint 7F.6)

**Date :** 17 septembre 2026
**Contexte :** Sprint 7F.6, étape 6 (paramètres système)

## Le problème

Le guide 7F.6 signalait qu'aucun endpoint d'écriture n'existe pour `parametre_systeme` :
le seuil d'aiguillage (`SEUIL_AIGUILLAGE_DR`), le délai de régularisation
(`DELAI_REGULARISATION_JOURS`) et le compte de charge (`COMPTE_CHARGE_RATIONS`) ne se
modifient aujourd'hui que par `UPDATE` SQL direct. Le guide proposait trois options :
reporter l'écran, créer l'endpoint (hors périmètre frontend), ou un écran de
consultation seule.

`docs/points-en-attente.md` documentait déjà ce manque comme **volontaire** ("piste, si
le métier le demande un jour"), pas comme un oubli.

## Ce qui a changé l'arbitrage

Après le Sprint 7F, il ne reste que les Sprints 8 (passerelle, déploiement) et 9
(tests d'intégration, documentation) — **aucun sprint backend fonctionnel n'est plus
prévu**. Si l'endpoint n'était pas ajouté maintenant, l'absence resterait permanente,
et non plus "en attente d'un signal métier".

## Décision

**Ajout backend scopé**, tranché avec l'utilisateur en ouverture de l'étape 6 : un
endpoint d'écriture minimal, plutôt qu'un report ou une consultation seule.

- `PUT /parametres/{code}` (service Workflow), réservé **ADMIN**.
- `GET /parametres/{code}` ajouté en complément, réservé **ADMIN** : sans lui,
  l'écran de modification écrirait à l'aveugle, sans jamais afficher la valeur
  courante. Aucune trace d'audit sur cette lecture (CLAUDE.md section 9.2, les
  lectures de travail n'en publient pas).
- **Trois codes seulement** modifiables (`ParametreAdminService.CODES_MODIFIABLES`) :
  `SEUIL_AIGUILLAGE_DR`, `DELAI_REGULARISATION_JOURS`, `COMPTE_CHARGE_RATIONS`.
- **`RATTRAPAGE_ACTIF` explicitement exclu** de l'écriture (mais lisible par
  `GET /parametres/{code}`) : ce drapeau reste gouverné par sa propre doctrine
  (CLAUDE.md section 7 — ouvert par migration V8, fermé par un `UPDATE` hors module
  en cas d'urgence). Le mélanger à un formulaire générique confondrait deux
  mécanismes de gouvernance distincts. Refus en `422 PARAMETRE_NON_MODIFIABLE`.
- **Lecture stricte reprise du seuil d'aiguillage** (Sprint 4.3) pour les deux
  paramètres entiers : `Long.parseLong`, aucun séparateur de milliers, aucune
  décimale, valeur négative refusée. Ce n'est **pas** une seconde comparaison
  montant/seuil (`AiguillageService` reste le seul endroit qui compare un montant au
  seuil) — c'est une validation de forme avant écriture, nécessairement distincte.
- **Compte de charge** : texte libre non vide, pas de contrainte numérique — c'est un
  numéro de compte du plan comptable, pas nécessairement numérique dans tous les cas.
- **Événement d'audit obligatoire** (`MODIFICATION_PARAMETRE`, entité cible
  `parametre_systeme`), portant l'ancienne et la nouvelle valeur. C'est exactement le
  manque que `docs/points-en-attente.md` signalait pour `SEUIL_AIGUILLAGE_DR` : savoir
  qui a changé une valeur qui commande le niveau d'approbation de la banque, et quand.
- **Format avant réseau** : la valeur est validée avant tout appel au service
  Identité (résolution de l'acteur pour l'audit) — même ordre que `RetourService`
  pour le motif obligatoire (RG-10).
- **Ordre des refus** : le code hors liste blanche (`PARAMETRE_NON_MODIFIABLE`, 422)
  est vérifié **avant** l'existence en base (`PARAMETRE_INTROUVABLE`, 404) — un code
  jamais autorisé n'a pas besoin d'aller chercher une ligne pour être refusé.

## Fichiers créés ou modifiés

**Backend** (`backend/service-workflow/`) :
- `domaine/ParametreSysteme.java` (mutateur `changerValeur` ajouté)
- `domaine/exception/ParametreIntrouvableException.java`,
  `ParametreNonModifiableException.java`, `ValeurParametreInvalideException.java` (créés)
- `api/dto/ModificationParametreRequest.java`, `ParametreResponse.java` (créés)
- `application/ParametreAdminService.java` (créé)
- `api/ParametreController.java` (endpoints `GET`/`PUT /parametres/{code}` ajoutés)
- `api/GestionnaireErreursApi.java` (trois gestionnaires ajoutés)
- `test/.../ParametreAdminServiceTest.java` (créé, 13 cas)
- `test/.../ParametreControllerIT.java` (mock `ParametreAdminService` ajouté)

Testé : `mvn test -pl service-workflow` — 358/358 (aucune régression sur les 345
tests existants, 13 nouveaux).

**Frontend** (`frontend/src/`) :
- `api/parametresApi.ts` (créé)
- `pages/admin/ParametresAdminPage.tsx`, `ModificationParametreModale.tsx` (créés)
- `components/layout/navigation.ts` (route `/admin/parametres` réintroduite, retirée
  au Sprint 7F.2 faute d'endpoint)
- `router/AppRouter.tsx` (route câblée)
- `utils/messagesErreur.ts` (trois codes ajoutés)

## Ce qui n'a pas changé

- `GET /parametres/fonctionnalites` reste inchangé : il ne sert qu'à masquer/afficher
  une entrée de menu (`rattrapageActif`), pas à lire une valeur de configuration.
- Le compte d'endpoints du contrat d'API section 11 n'est pas révisé rétroactivement :
  ces deux endpoints sont additifs, comme `GET /grilles/active` ou les endpoints
  internes déjà notés hors contrat passerelle.

## Impact sur les sprints suivants

Aucun sprint backend n'est prévu après le 7F : ce point ne rouvre donc pas de travail
futur. À noter pour le Sprint 9 (documentation) : ces deux endpoints devront figurer
dans la documentation Swagger/README finale, au même titre que les autres endpoints
additifs du module.
