# Resolution d'un compte sans profil local

**Date :** 25 aout 2026
**Sprint :** 0.4, securite et authentification Keycloak
**Statut :** tranchee

## Question

Keycloak authentifie l'agent et fournit son identite. Le role applicatif et le
code unite, eux, sont geres localement (CLAUDE.md section 10) : ils vivent dans
la table `utilisateurs` et non dans l'annuaire.

Que doit faire le module quand un jeton valide se presente avec un
`sub_keycloak` qu'aucune ligne locale ne connait ?

## Options examinees

| Option | Qui decide de l'acces | Cout d'administration |
|---|---|---|
| Pre-provisionnement puis liaison automatique | l'administrateur, a l'avance | saisir login, role, code unite |
| Refus strict | l'administrateur, a l'avance | idem, plus la recopie manuelle de l'identifiant technique Keycloak |
| Creation automatique a la premiere connexion | personne | nul au depart, tout a rattraper ensuite |

## Decision

**Pre-provisionnement puis liaison automatique.**

L'administrateur ouvre le profil local a partir du login annuaire
(`prenom_nom`), en y portant le role et le code unite. La colonne
`sub_keycloak` reste vide. A la premiere connexion, `UtilisateurCourantService`
retrouve le profil par `preferred_username` et y inscrit le `sub`. Les
connexions suivantes passent directement par le `sub`.

Un jeton valide sans profil local correspondant est refuse en **403**, au format
d'erreur uniforme, code `UTILISATEUR_NON_HABILITE`.

## Motifs

- L'habilitation au module reste un acte d'administration explicite. Un compte
  annuaire valide n'obtient rien du seul fait d'exister : c'est le risque de
  controle interne que le sprint 0.4 signale nommement.
- Le code unite est toujours renseigne. Une creation automatique le laisserait
  vide, ce qui bloquerait la saisie et le workflow des le sprint 3.
- L'administrateur ne manipule aucun identifiant technique. Le refus strict
  aurait impose la recopie d'un UUID par agent, penible et source d'erreur.

## Consequences

- La table `utilisateurs` porte `sub_keycloak` en colonne **nullable et unique**.
- Un endpoint d'administration des profils devient necessaire
  (`/identite/utilisateurs`, deja au contrat d'API). Hors perimetre du sprint 0.4.
- Deux comptes Keycloak revendiquant le meme login annuaire : le second est
  refuse plutot que de se voir reattribuer l'habilitation du premier.
- Un profil desactive est refuse en 403 sans etre supprime : la trace demeure.
- En developpement, les profils sont pre-provisionnes par la migration `V1000`
  du service Identite. Le compte `pierre_belinga`, present a l'annuaire et
  volontairement absent de cette migration, sert de cas de controle du refus.
