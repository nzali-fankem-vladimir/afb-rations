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

## Intégration au service de signature électronique de la banque

Sprint 4.2, décision du 1er septembre 2026 (`SignatureService`,
`docs/controles-completude.md`). **À arbitrer avec la DSI**, pas avec le métier :
la question n'est pas de savoir si le métier veut une signature, mais de quelle
infrastructure de confiance la banque dispose et à quelles conditions ce module
peut s'y raccorder.

**Ce que le module fait aujourd'hui.** RG-09 est tenue par une **mention signée
horodatée** imprimée sur la pièce jointe (login, rôle figé au moment de l'acte,
date et heure), doublée d'une **empreinte SHA-256** du fichier enregistrée dans
`etape_workflow.signature_numerique`.

**Ce que cela prouve.** Que le document archivé n'a pas été altéré depuis la
dernière signature : on recalcule l'empreinte du fichier et on la compare.

**Ce que cela ne prouve pas, et qu'il ne faut pas laisser croire.**

- Ce n'est **pas une signature électronique au sens juridique**. Aucune clé,
  aucun certificat, aucune autorité de certification, aucun horodatage qualifié.
- L'empreinte vit **dans la même base** que le reste du module : elle ne protège
  pas de quelqu'un qui peut y écrire. Elle détecte une altération du fichier, pas
  une falsification coordonnée.
- Seule la **dernière** empreinte reste vérifiable contre le fichier. Le document
  étant enrichi à chaque validation, les empreintes intermédiaires documentent ce
  qu'était le document à leur étape sans pouvoir être recontrôlées. C'est
  l'empreinte finale, après clôture, qui scelle le justificatif archivé.

**La question posée à la DSI.** La banque dispose-t-elle d'un service de
signature électronique — autorité de certification interne, HSM, horodatage
qualifié — auquel ce module devrait se raccorder ? Et si oui, sous quelle forme :

| Variante | Ce qu'elle signifie | Ce qu'elle suppose |
| --- | --- | --- |
| **Cachet serveur** | Une clé unique du module scelle le document. Prouve que *le module* a produit et scellé la pièce, **pas** qu'une personne l'a signée. | Un certificat de service et sa garde. |
| **Signature personnelle** | Chaque agent, chef d'unité et directeur réseau signe avec sa propre clé. Seule variante qui honore vraiment « une signature par validation ». | Un certificat par acteur, une conservation des clés (HSM ou carte), une gestion de la révocation. |

**Le point de tension à signaler.** Les spécifications parlent d'une signature
numérique « **automatique** ». Ce mot exclut la variante *signature personnelle*,
qui suppose par nature un geste de la personne. Si le contrôle interne exige une
valeur probante opposable, c'est donc la spécification elle-même qu'il faut
rouvrir, pas seulement l'implémentation.

**Coût du report : faible.** Le passage à une signature PAdES est un
enrichissement du document, comme l'est déjà l'apposition des mentions ; la
géométrie de la page des visas, la convention de nommage et la discipline
d'écriture confirmée ne sont pas à reprendre. C'est la chaîne de confiance qui
manque, pas le code.
