# Délais d'attente et réessai des appels sortants du service Saisie

**Sprint 3.2 — 31 août 2026 — décision prise avec l'utilisateur**

Portée : tous les appels HTTP sortants du service Saisie. Le premier est
`GET /grilles/active` (RG-03) ; `ClientWorkflow` et `ClientIdentite` suivront
dans ce même service.

---

## 1. La question

Le service Saisie devient appelant. Deux réglages qu'aucun document du projet
n'avait tranchés jusqu'ici :

1. Combien de temps attendre une réponse avant de renoncer ?
2. Faut-il réessayer avant d'abandonner ?

Le refus conservateur lui-même n'était pas en jeu : il est acquis depuis le
Sprint 1.3 (service Identité) et étendu au service Grilles au Sprint 2.4. Une
ligne dont le montant n'est pas connu n'est jamais enregistrée.

---

## 2. Décision

**Délais : 2 s de connexion, 3 s de lecture. Aucun réessai.**

Posés dans `infrastructure/config/ConfigurationAppelsSortants`, sur le
`RestClient.Builder` partagé par les clients sortants du service — donc valables
pour tous, sans qu'aucun ait à s'en souvenir.

Ces bornes valent dans **tous les profils**. Ce sont une doctrine d'appel, pas un
réglage d'environnement ; seule l'adresse du service appelé (`app.grilles.url`)
varie.

---

## 3. Pourquoi ces valeurs

Ce sont exactement celles de `ClientIdentite` côté service Grilles (Sprint 2.2).
Une seule convention d'appel sortant dans le module vaut mieux que deux valeurs
proches qu'il faudrait ensuite justifier séparément — et qu'un lecteur prendrait
à tort pour une différence intentionnelle.

3 s de lecture correspond à la cible de temps de réponse du document maître.
Sans borne explicite, le comportement par défaut de la JVM laisserait l'agent
devant un écran figé une minute avant d'apprendre que rien n'a été enregistré.

---

## 4. Pourquoi aucun réessai

Trois raisons, dans l'ordre de poids.

**Le budget de temps est déjà consommé par l'empilement.** Le Sprint 3.1 a acté
trois dépendances synchrones par ligne saisie (Grilles pour le montant, Workflow
pour le statut du processus, Identité pour l'habilitation —
`docs/rattachement-processus.md` §4). Un réessai sur chacune ferait passer le
pire cas de 9 à 18 secondes. La cible du document maître est de 3 secondes en
moyenne.

**L'agent est le réessai.** Contrairement à un traitement par lot, la saisie est
interactive : quelqu'un est devant l'écran et resaisit. Le réessai humain existe
déjà, gratuitement, et il est mieux informé qu'une boucle — il sait s'il a le
temps d'attendre.

**Un réessai masquerait la dégradation.** Un service Grilles qui répond une fois
sur deux se verrait dans les journaux si chaque échec refuse une ligne ; il
deviendrait invisible si le second appel rattrape le premier. Sur une donnée
réglementaire, on préfère voir la panne.

### Options écartées

| Option | Pourquoi non |
|---|---|
| Un réessai sur toute panne de transport (délai dépassé ou connexion refusée) | Double l'attente sur exactement le chemin le plus lent : l'agent apprend le refus au bout de ~6 s au lieu de ~3 s, et jusqu'à 12 s si les autres clients adoptent la même règle. |
| Un réessai sur connexion refusée seulement (coût quasi nul, l'échec est instantané) | Techniquement le meilleur compromis en temps, mais le bénéfice est mince : une connexion refusée signifie presque toujours que le service est arrêté, et le second appel le sera aussi. Le cas qu'elle couvrirait vraiment — une bascule de pod pendant un déploiement — n'existera qu'au Sprint 9. **À reconsidérer à ce moment-là**, pas avant. |

---

## 5. Ce que la décision ne couvre pas

- **`500 INCOHERENCE_GRILLE`** n'est jamais réessayé, ni maintenant ni si un
  réessai était réintroduit un jour : c'est un incident de données côté Grilles
  (deux grilles actives se chevauchent, Sprint 2.4), pas une panne de transport.
  Le réessayer produirait la même erreur en masquant son caractère anormal. Il
  est journalisé au préfixe repérable `INCOHERENCE GRILLE`
  (`docs/appel-resolution-montant.md` §3).
- **Le déclenchement du refus** reste inchangé : refus conservateur, la ligne
  n'est pas enregistrée. Voir `docs/appel-resolution-montant.md` §2.
- **Le message rendu à l'agent** reste distinct du refus métier
  (`GRILLE_INDISPONIBLE`). Un service en panne et une absence de tarif appellent
  deux actions différentes.

---

## 6. Conséquences pour les sprints suivants

| Sprint | Conséquence |
|---|---|
| 3.2 (celui-ci) | `ResolutionMontantHttpClient` reçoit le `RestClient.Builder` injecté ; il ne construit pas le sien. |
| 3.3 | Le contrôleur relaie l'en-tête `Authorization` ; le refus technique se traduit en réponse distincte du `422 GRILLE_INDISPONIBLE`. |
| 4 | `ClientWorkflow` reprend le même constructeur et la même doctrine, sans réglage propre. |
| 9 | Au passage en Kubernetes, reconsidérer le réessai sur connexion refusée pendant les bascules de pods — et lui seul. |

---

## 7. Note technique — Spring Boot 4

Les propriétés `spring.http.client.connect-timeout` / `read-timeout` **ne
fonctionnent pas dans ce service**, et il ne faut pas essayer de les y mettre :
Spring Boot 4 a scindé ses modules, et l'auto-configuration de
`RestClient.Builder` comme ces propriétés vivent dans `spring-boot-restclient` /
`spring-boot-http-client`, qu'aucun starter du service n'entraîne. Il n'existe
donc **aucun** bean `RestClient.Builder` par défaut, et ces propriétés seraient
lues comme du texte mort — sans erreur au démarrage, avec des appels non bornés
à l'exécution. D'où le bean explicite.

Ce bean est de **portée prototype** : un `RestClient.Builder` est mutable et
chaque client y pose sa propre `baseUrl`. En singleton, le jour où
`ClientWorkflow` rejoindra `ResolutionMontantHttpClient`, le second écraserait
l'adresse du premier — panne silencieuse et déroutante.
