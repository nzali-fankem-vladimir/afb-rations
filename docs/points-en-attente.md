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

---

## `SEUIL_AIGUILLAGE_DR` — point de défaillance unique du circuit de validation

**Ouvert au Sprint 4.3. À surveiller en priorité en production.**

**La décision prise.** Si le paramètre `SEUIL_AIGUILLAGE_DR` est absent, désactivé
ou porte une valeur illisible, le service Workflow **refuse la validation**
(`500 SEUIL_INDISPONIBLE`) au lieu d'appliquer une valeur de repli. Une valeur par
défaut dans le code réintroduirait exactement ce que RG-08 interdit, et un
aiguillage sur un seuil inventé serait invisible : le circuit continuerait de
tourner en appliquant un niveau d'approbation que personne n'a décidé.

**Le compromis assumé, et sa conséquence.** Une seule ligne de
`parametre_systeme` conditionne **tout** le circuit de validation du module. Une
suppression accidentelle, une désactivation, une valeur mal saisie — `100 000` avec
une espace, une valeur avec décimale — bloque toutes les validations de toutes les
unités, immédiatement et en même temps. Ce n'est pas un défaut de conception : c'est
le prix explicitement accepté pour qu'une erreur de configuration soit visible tout
de suite plutôt que de produire des aiguillages faux pendant des semaines. Mais
c'est un point de fragilité qu'il faut connaître avant de le découvrir.

**Ce qui est demandé à l'exploitation.**

| Mesure | Pourquoi |
| --- | --- |
| **Supervision du log au préfixe `SEUIL INDISPONIBLE`** | C'est le signal unique et immédiat. Chaque échec de lecture le journalise en `error` avec la valeur trouvée. Une alerte sur ce préfixe transforme une panne de circuit en incident détecté en quelques secondes. |
| **Contrôle de la ligne au déploiement et après toute migration** | La ligne vient de la migration V2. Un rejeu de base, une restauration partielle ou une intervention manuelle peuvent la faire disparaître sans que rien ne le signale tant que personne ne valide. |
| **Restreindre l'écriture sur `parametre_systeme`** | La table n'a aujourd'hui aucun endpoint d'administration : elle se modifie en SQL direct. Tant qu'il en est ainsi, l'accès en écriture à cette table est un accès au niveau d'approbation requis par la banque, et devrait être tracé au même titre. |
| **Journaliser toute modification du seuil** | Le module trace le seuil **appliqué** à chaque validation (audit `VALIDATION_PROCESSUS`, champ `seuilApplique`), donc l'effet du changement. Il ne trace pas le changement lui-même : `parametre_systeme` n'a ni horodatage ni auteur (CLAUDE.md §4). Un contrôle interne qui voudrait savoir *qui* a abaissé le seuil et *quand* ne le trouvera nulle part dans ce module. |

**Piste, si le métier le demande un jour.** Un endpoint d'administration du seuil,
réservé à `ADMIN` ou à la DRH, publiant un événement d'audit — ce qui fermerait la
dernière ligne du tableau. Hors périmètre du module tel que spécifié : il n'existe
aucun endpoint `/parametres` au contrat d'API.

---

## Séparation des tâches dans une unité à un seul valideur (Sprint 4.4)

**Le point.** RG-12 interdit à une même personne d'agir deux fois sur la version d'un
dossier qui est dans le circuit. La portée d'un chef d'unité est limitée à sa propre
unité (décision Sprint 1.1), et rien n'oblige une unité à compter plus d'un DA.

**Ce qui est déjà réglé.** Le contrôle porte sur le **cycle courant** et non sur toute
la vie du processus : un retour clôt un cycle, et le DA qui a retourné un état peut
valider la version corrigée. Sans ce découpage, le premier retour de chaque unité à un
seul DA aurait produit un blocage définitif. Voir
`docs/decisions/2026-09-01-separation-des-taches-et-cycle-de-validation.md`.

**Ce qui reste ouvert.** Le cas où la **même personne** soumet puis doit valider — un
agent promu chef d'unité qui reprend ses propres dossiers, ou une unité où le chef fait
lui-même la saisie. Le module refuse (`403 SEPARATION_TACHES`) et le déblocage est
organisationnel : un suppléant habilité sur l'unité, ou une réattribution par
l'administrateur. Le module ne peut pas faire mieux : il ne connaît pas l'annuaire des
valideurs d'une unité, `GET /identite/habilitation` ne répondant qu'à « cette personne
a-t-elle droit sur cette unité ? », jamais « qui d'autre ? ».

**Ce qui est demandé au métier.**

| Question | Pourquoi elle se pose |
| --- | --- |
| Chaque unité dispose-t-elle d'au moins un valideur distinct de l'agent qui saisit ? | Sans cela, le circuit est bloqué dès la première soumission de cette unité, et le refus paraîtra arbitraire à l'utilisateur. |
| Qui valide quand le chef d'unité est absent ? | La question existait avant le module ; la digitalisation la rend simplement visible et bloquante là où le papier laissait passer. |
| Un « suppléant » doit-il exister comme habilitation, ou l'administrateur réattribue-t-il au cas par cas ? | La première option demanderait une évolution du service Identité ; la seconde tient avec l'existant. |

**Surveillance.** Les refus `SEPARATION_TACHES` sont tracés en audit avec leur propre
motif, distinct de `HABILITATION_ABSENTE` et de `ROLE_INSUFFISANT`. Un comptage par
unité dira si le cas est théorique ou quotidien — et c'est cette mesure, pas une
hypothèse, qui devra décider d'une éventuelle évolution.
