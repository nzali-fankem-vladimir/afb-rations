# Points en attente

Questions ouvertes, à trancher avec la DSI ou le métier. Ne pas figer de
comportement définitif ailleurs dans le code tant qu'un point reste ici.

## D-07 — Nommage définitif des topics Kafka en environnement partagé

Les noms de développement (`rations.etat.valide`, `rations.etat.accuse`,
`rations.audit.evenement`, Sprint 0.5) ne sont pas arrêtés pour
l'environnement partagé. Ne pas les figer ailleurs que dans
`infra/docker/kafka-topics.sh` (CLAUDE.md section 9).

## Garantie d'exhaustivité du journal d'audit — outbox transactionnel

Sprint 1.3, décision du 27 août 2026 (`docs/publication-audit.md` section 5).

La publication d'audit a lieu **après le commit** de la transaction métier, de
façon asynchrone. Cela écarte les traces **fausses** — un rollback ne laisse
jamais derrière lui la trace d'une opération annulée — mais conserve un risque de
trace **manquante** : broker durablement indisponible, file d'attente saturée,
ou arrêt de la JVM entre le commit et l'envoi.

**Ce risque est le plus difficile à détecter des deux.** Une trace fausse se
repère par recoupement avec l'état réel (`utilisateurs.role` contredit
`audit_log`) ; une trace manquante ne se repère pas du tout, rien ne signalant
une absence. Le compromis retenu est donc un compromis de coût pour ce sprint,
pas un état correct.

La réponse de fond est un **outbox transactionnel** : écrire l'événement dans
une table locale, dans la même transaction que la modification, puis le relayer
vers Kafka par un processus séparé. Ni trace fausse, ni trace manquante. Coût :
une table et un relais supervisé par service métier.

**À arbitrer avec le contrôle interne / l'audit interne** : le niveau de perte
d'événements acceptable est-il nul ? Si oui, l'outbox devient obligatoire pour
les six services, et le module `rations-audit-commun` est le seul endroit à
reprendre.

## Révocation immédiate d'un jeton Keycloak après changement de rôle

Sprint 1.2, décision du 26 août 2026
(`docs/decisions/2026-08-26-attribution-role-administrateur.md`) : une
modification de rôle applicatif s'applique dès la requête suivante, le
module étant stateless et relisant le profil local à chaque appel. Mais le
jeton Keycloak déjà émis à l'utilisateur reste valide jusqu'à son expiration
naturelle : ce module n'a aucun moyen de le révoquer immédiatement.

Si le métier exige qu'un changement de rôle coupe l'accès sur-le-champ (cas
d'un incident de sécurité, par exemple), la réponse est à chercher côté
configuration du realm Keycloak (durée de vie courte des jetons,
introspection systématique) — pas dans ce service, qui ne stocke ni
n'émet de jeton.
