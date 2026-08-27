# Attribution du rôle et du code unité : trois points de contrôle interne

**Date :** 26 août 2026
**Sprint :** 1.2, administration des utilisateurs
**Statut :** tranchée

## Question

`PUT /identite/utilisateurs/{id}/role` touche à l'habilitation métier. Le
guide du sous-sprint impose de trancher trois points avec le métier avant
d'écrire le code, plutôt que de les décider seul.

## Décision 1 — Auto-modification par un administrateur

**Interdite, sans exception, sur cet endpoint.** Un jeton ADMIN qui cible son
propre identifiant reçoit 409.

**Limite connue, documentée plutôt que masquée :** si cet administrateur est
le seul actif du système, l'interdiction le bloque aussi lui-même. Le
sous-sprint ne prévoit pas de dérogation pour ce cas : le rétablissement
passe alors par une intervention directe en base (DSI), hors périmètre
applicatif. Ce n'est pas un trou involontaire mais un compromis assumé :
ouvrir une exception « si je suis seul » romprait la garantie plus large que
l'endpoint n'est jamais un moyen de s'auto-élever ou de s'auto-rétrograder,
pour gagner un cas limite qui se prévient en amont (ne jamais laisser
descendre le nombre d'ADMIN actifs à un seul).

## Décision 2 — Retrait du dernier administrateur actif

**Bloqué (409)** si la modification ferait passer à zéro le nombre
d'utilisateurs `role = ADMIN` et `actif = true` (le champ `actif` existe
déjà sur `utilisateurs` depuis la décision du Sprint 0.7).

**Précision sur le comptage :** le critère est `actif = true`, **sans**
exiger `sub_keycloak` renseigné. Un profil ADMIN pré-provisionné mais encore
jamais connecté reste un administrateur valide au sens du module (décision
Sprint 0.4 : pré-provisionnement puis liaison automatique) — il suffit que
la personne se connecte une première fois via Keycloak pour que la liaison
s'établisse. L'exiger reviendrait à ne compter que les administrateurs
« déjà vus », ce que rien dans le projet ne demande, et pénaliserait un
remplacement d'administrateur préparé à l'avance.

## Décision 3 — Invalidation des sessions en cours

**Hors périmètre de ce service, pas de mécanisme ajouté.** Le module est
stateless (CLAUDE.md section 10) : le rôle applicatif est relu en base à
chaque requête via le profil local, jamais mis en cache. Un rôle modifié
s'applique donc dès l'appel suivant, sans action supplémentaire.

**Angle mort documenté, pas nié :** ceci ne révoque pas le jeton Keycloak
lui-même. Un utilisateur rétrogradé garde un jeton valide jusqu'à son
expiration naturelle ; il perd seulement l'accès aux endpoints qui vérifient
le rôle applicatif à chaque appel. Une révocation immédiate au niveau du
jeton relèverait de la configuration du realm Keycloak (durée de vie
courte, introspection), pas de ce service — consigné dans
`docs/points-en-attente.md`.

## Conséquences

- Nouvelle exception `AutoModificationInterditeException` (409) et
  `DernierAdministrateurException` (409) dans
  `UtilisateurAdminService.attribuerRole`.
- Le comptage du dernier ADMIN interroge `UtilisateurRepository` par
  `role` et `actif`, pas par présence de `sub_keycloak`.
- Aucun changement de `SecurityConfig` ni de configuration Keycloak dans ce
  sous-sprint.
