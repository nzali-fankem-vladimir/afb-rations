# Realm Keycloak de developpement

Le module ne gere aucun mot de passe et n'expose aucune route de login : il valide
des jetons emis par Keycloak, qui verifie les identifiants aupres de l'annuaire
(CLAUDE.md section 10). Ce dossier ne contient donc que la configuration du
fournisseur d'identite.

## Fichiers

| Fichier | Role |
|---|---|
| `realm-afb-rations-dev.json` | **Source de reference.** Definition complete et importable du realm : client public, six roles, comptes de test. C'est ce fichier qu'on modifie. |
| `init-realm.ps1` | Cree ou recree le realm dans un conteneur Keycloak a partir du fichier ci-dessus. |
| `realm-export.json` | Instantane de ce que Keycloak detient reellement, obtenu par export partiel de l'API d'administration. Sert de controle, pas de source. |

## Recreer le realm

Keycloak tourne en `start-dev` : sa configuration disparait a la suppression du
conteneur. Le realm se recree alors en une commande.

```powershell
# Le mot de passe de la console d'administration n'est pas versionne.
$env:KEYCLOAK_ADMIN_PASSWORD = "<mot de passe admin du conteneur>"

.\init-realm.ps1                                     # conteneur par defaut
.\init-realm.ps1 -Force                              # recreer un realm existant
.\init-realm.ps1 -Container keycloak-rations         # autre conteneur
```

Le realm est heberge dans le conteneur Keycloak partage du poste (`dottel-keycloak`,
port 8180). Les realms sont etanches : `afb-rations-dev` et `dottel-dev` ne
partagent ni comptes, ni roles, ni cles de signature. Un jeton emis par l'un est
rejete par les services de l'autre, l'emetteur ne correspondant pas.

## Comptes de test

Mot de passe commun : `Rations2026`. Comptes de developpement uniquement.

| Compte | Role | Profil local |
|---|---|---|
| `jean_mbarga` | AGENT_UNITE | unite 00002 |
| `paul_essama` | CHEF_UNITE_DA | unite 00002 |
| `sylvie_atangana` | DIRECTEUR_RESEAU_DR | unite 00001 |
| `claire_nkolo` | ARH | unite 00001 |
| `agnes_tchinda` | DRH | unite 00001 |
| `martin_fouda` | ADMIN | unite 00001 |
| `pierre_belinga` | AGENT_UNITE | **aucun** — compte de controle du refus en 403 |

Le role et le code unite affiches ici sont ceux du profil **local** du module
(table `utilisateurs`, migration `V1000` du service Identite), pas ceux de
l'annuaire. Keycloak porte l'identite, le module porte l'habilitation.

## Points de vigilance

- L'export contient les cles de signature du realm de developpement et les
  empreintes des mots de passe de test. Il n'a de valeur qu'en developpement :
  le realm de production est celui de la banque, jamais celui-ci.
- `kc.sh export` echoue sur un conteneur en cours d'execution, la base H2 de
  developpement etant deja ouverte par le serveur. D'ou l'export partiel par
  l'API d'administration, qui ne comprend pas les comptes : ceux-ci restent
  portes par `realm-afb-rations-dev.json`.
- L'URL du realm de production reste un point en attente DSI.
