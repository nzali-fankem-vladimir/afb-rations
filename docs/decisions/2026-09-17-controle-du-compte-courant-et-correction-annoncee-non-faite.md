# Décision — Contrôle du compte courant, et une correction annoncée mais jamais faite

**Date :** 17 septembre 2026
**Contexte :** Sprint 7F.6, proposition n°3 (retour de vérification visuelle)

## Le fait

L'utilisateur a demandé que l'écran de saisie contrôle le format du numéro de
compte courant et du code agence. En préparant la proposition, trois sources ont
été comparées au code :

| Source | Affirmation |
| --- | --- |
| Métier, 9 septembre 2026 (point T-02) | Un compte courant fait **11 chiffres** |
| Dictionnaire de données | « 11 chiffres, contrôlé par un `@Pattern` » |
| Résumé du sprint Maille 1, section 9 | « T-02 corrigé, `@Pattern("^[0-9]{11}$")` posé » |
| **Code réel** | `@Size(max = 20)` seul, **aucun contrôle de format** |
| `git log` sur `IdentiteBeneficiaireRequest.java` | **Aucune modification** depuis le Sprint 3.3 |

La correction avait été décrite, jamais appliquée. Le registre
`docs/dispositifs_provisoires.md` indiquait pourtant toujours « En attente ».

## Décision (option A, validée par l'utilisateur)

1. **Serveur** : `@Pattern(regexp = "^[0-9]{11}$")` sur le numéro de compte courant
   (`service-saisie`). Un contrôle posé uniquement à l'écran se contourne par un
   appel direct à l'API ; c'est le serveur qui protège le paiement.
2. **Écran** : contrôle au clic sur « Ajouter », erreurs affichées sous chaque champ.
   Règles regroupées dans `frontend/src/utils/validationSaisie.ts`, réutilisables.
3. **Données existantes** : non corrigées, arbitrage du 9 septembre inchangé (la
   production démarrera vide ; tronquer des numéros inconnus fabriquerait des comptes
   plausibles mais faux).
4. **Documentation** : rectificatif daté dans le résumé Maille 1, texte d'origine
   conservé ; T-02 passé à « Corrigé ».

## Impact sur les sprints suivants

- **Jeux d'essai** : tout compte saisi doit faire 11 chiffres. Les comptes de
  développement à 14 chiffres ne peuvent plus être ressaisis. **La recette du
  Sprint 9.2 devra régénérer son jeu d'essai** avec des comptes à 11 chiffres ;
  sinon, ressaisir le bon numéro d'une personne existante créera un second
  bénéficiaire (le compte est le seul critère d'identification, Sprint 3.1).
- **Contrat d'API §7.1** : son exemple à 14 chiffres reste faux. C'est le document lu
  par l'équipe de comptabilisation. À corriger à la prochaine révision du contrat.
- **Règle de méthode pour la recette (Sprint 9)** : une correction annoncée par un
  résumé de sprint se vérifie **dans le code et dans l'historique git**, pas dans le
  résumé. Ce cas le montre : le résumé, le dictionnaire et le registre semblaient
  cohérents entre eux, et le code ne l'était pas. C'est la même famille que le
  constat du Sprint 6.3 sur l'audit (« un dispositif ne se valide pas par le
  silence »).
