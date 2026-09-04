# Point A-01 tranché : pas de champ `login_acteur`, deux défauts distincts consignés

**Sprint de rattrapage — construction du service Audit, 4 septembre 2026** ·
Décision tranchée avec l'utilisateur, à l'étape 3 (arbitrage et écriture de
l'entité `AuditLog`), conformément à l'engagement du Sprint 6.3 de ne pas
reporter cette question au-delà de l'étape qui conçoit l'entité et la lecture
en même temps.

**Statut : arrêtée. Option B retenue.** Aucun champ `login_acteur` n'est ajouté
au schéma de `audit_log` à ce sprint.

---

## 1. Ce qui a motivé l'arbitrage

Le Sprint 6.3 avait identifié, sans les vérifier en détail, 21 points de
publication sur 30 laissant `id_utilisateur` nul, et proposé deux options :
ajouter un champ `login_acteur` recherchable, ou documenter la limite. La
décision devait être prise à cette étape, avec l'écriture (les migrations V1/V2
déjà commitées) et la lecture (les endpoints à concevoir) sous les yeux en même
temps.

## 2. Vérification en réel avant arbitrage

Avant de trancher, les 193 messages alors présents sur `rations.audit.evenement`
ont été relus intégralement (`kafka-console-consumer.sh --from-beginning`,
193 messages capturés le 4 septembre 2026, offset ensuite réinitialisé à 0 pour
ne pas perturber la reprise réelle de l'étape 7). Ce n'est pas une inspection de
code mais une lecture du contenu réel du topic.

**Constat déterministe : la nullité d'`id_utilisateur` dépend exclusivement du
type d'action (0 % ou 100 %, jamais mixte pour une même action).** Quinze
actions sur les 24 observées ont systématiquement `id_utilisateur` nul.

**Une affirmation provisoire formulée pendant l'échange s'est révélée
inexacte, et est corrigée ici plutôt que silencieusement abandonnée** :
il avait été avancé une incohérence entre les clés `login` et `loginCible`.
Vérification faite, `loginCible` n'apparaît que sur `ATTRIBUTION_ROLE`, action
dont `id_utilisateur` **n'est pas nul** — c'est l'identifiant de l'administrateur
auteur du geste, tandis que `loginCible` désigne la personne dont le rôle
change. Cette action est donc hors du périmètre d'A-01. La vraie incohérence
oppose `login` à `auteur` (section 4 ci-dessous).

## 3. Pourquoi Option B, et pourquoi pas une version atténuée d'Option A

Un champ `login_acteur` alimenté par extraction de `detail_json` a été écarté
parce que la vérification en réel a montré qu'il n'aurait résolu ni le cas le
plus fréquent, ni le cas le plus simple :

- **Le cas le plus fréquent (45 événements sur 193, `CREATION_LIGNE_PRESTATION`,
  service Saisie) n'a aucun identifiant d'acteur dans le message, sous quelque
  forme que ce soit.** Un champ dérivé du JSON resterait nul exactement là où
  il est le plus utile — pour la même raison qui a fait juger `id_utilisateur`
  trompeur dans son état actuel (une colonne qui a l'air fiable et ne l'est
  pas partout).
- **Le cas où un login existe n'utilise pas une clé stable** (`login` contre
  `auteur` selon le service), ce qui aurait obligé le consommateur — tolerant
  reader, sans connaissance des types métier de chaque producteur
  (CLAUDE.md section 15) — à connaître la forme interne du `detail_json` de
  chaque action pour en extraire un login. C'est exactement le couplage que le
  statut de tolerant reader est censé éviter.

Ajouter le champ maintenant aurait donc demandé, pour être honnête, de d'abord
corriger les producteurs — ce que ce sprint ne fait pas (il ne touche que la
moitié aval de la chaîne, CLAUDE.md et le guide sont explicites sur ce
périmètre).

## 4. Les deux défauts consignés, distinctement

Consignés dans `docs/points-en-attente.md`, point A-01, section « Décision
tranchée au sprint de rattrapage du service Audit ». Ce ne sont **pas** la
même limite et ne doivent pas être fusionnées dans un futur correctif :

1. **Service Saisie : absence totale de capture d'acteur à la source**, sur
   `CREATION_LIGNE_PRESTATION`, `OUVERTURE_FICHE_JOURNALIERE`,
   `SUPPRESSION_LIGNE_PRESTATION`, `MODIFICATION_LIGNE_PRESTATION` et
   `CREATION_BENEFICIAIRE`. Confirmé dans le code
   (`CreationLigneService.tracer` : `idUtilisateur` est un `null` littéral).
   C'est un défaut de producteur, à corriger dans un futur sprint qui touche
   au service Saisie.
2. **Incohérence de nommage de la clé du login**, quand un login est bien
   capturé malgré `id_utilisateur` nul : `login` (`ACCES_REFUSE`,
   `GENERATION_RAPPORT`, `EXPORT_RAPPORT`) contre `auteur`
   (`DECLENCHEMENT_PROCESSUS`, service Workflow). À uniformiser sur `login`,
   déjà majoritaire, dans le même futur sprint producteur.

Le tableau complet des 15 actions à `id_utilisateur` toujours nul — service
émetteur, présence ou non d'un acteur capturé, nom de la clé — est dans
`docs/points-en-attente.md` pour éviter qu'un futur sprint producteur n'ait à
refaire cette lecture du topic.

## 5. Ce que ce sprint fait à la place

L'entité `AuditLog` (étape 3) reprend exactement les colonnes déjà fixées par
les migrations V1 et V2 : aucune colonne `login_acteur`, aucune migration V3.
Le point A-01 reste ouvert dans `docs/points-en-attente.md`, désormais avec le
détail exact nécessaire à sa résolution, au lieu d'une limite générique.
