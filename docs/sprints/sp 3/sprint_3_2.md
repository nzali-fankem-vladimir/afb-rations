# SPRINT 3.2

## Valorisation des lignes et contrôle des doublons

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Appliquer RG-03 en consommant le service Grilles, et RG-04 en refusant les doublons journaliers |
| **Livrable** | Client de résolution du montant, service de contrôle des doublons, tests exhaustifs |
| **Durée** | Une journée |
| **Prérequis** | Sprint 3.1 validé et commité |
| **Sprint suivant** | 3.3, endpoints de saisie |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Client de résolution du montant (étapes 2-3) | Opus | Élevé |
| Contrôle des doublons (étapes 4-5) | Opus | Élevé |

**Changement manuel requis** si la session précédente était revenue en Sonnet. Ce sous-sprint porte les deux règles qui protègent le module du paiement erroné et du double paiement : le maintenir en Opus effort élevé du début à la fin.

## 2. Outil de cartographie

Utilisation obligatoire à l'étape 6. C'est le premier sous-sprint où un service en appelle un autre : la cartographie doit confirmer que l'appel se fait par l'API et non par un accès direct.

```
py -3.14 -m graphify update .
```

## 3. Contexte

Les entités existent. Ce sous-sprint pose les deux règles qui font la fiabilité de la saisie.

**RG-03, le montant automatique.** Le service Saisie ne calcule rien : il demande au service Grilles quel montant s'applique, pour une nature, une session et une date de prestation. C'est le premier appel inter-services du projet. La convention a été rédigée au Sprint 2.4 ; ce sous-sprint l'implémente côté appelant.

**RG-04, l'unicité journalière.** Un même bénéficiaire ne peut pas être saisi deux fois pour la même journée, la même nature et la même session. C'est le premier rempart contre le double paiement. Le contrôle porte sur la combinaison complète : le même agent peut légitimement recevoir une ration le jour et un transport le soir.

Aucun endpoint n'est exposé ici : ils viennent au sous-sprint 3.3.

## 4. Objectifs

- Client d'appel au service Grilles, isolé derrière une interface
- Gestion explicite de l'indisponibilité de grille et de l'indisponibilité du service
- Service de contrôle des doublons appliquant RG-04
- Service de création de ligne combinant les deux contrôles
- Tests couvrant les cas nominaux, les refus et les défaillances d'appel

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| RG-03 | Montant repris de la grille active, jamais saisi |
| RG-04 | Unicité journalière du bénéficiaire, par nature et session |
| US-04 | Le système empêche les doublons sur une journée |
| US-05 | Le système refuse une ligne sans grille active |
| CT-06 | Ligne valide : montant repris de la grille, ligne enregistrée |
| CT-09 | Doublon signalé et refusé |
| CT-10 | Grille manquante signalée et ligne refusée |

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, section 6, regles
RG-03 et RG-04. Lis aussi docs/appel-resolution-montant.md, redige au
Sprint 2.4. Confirme en 3 lignes ce que ces deux regles imposent.

CONTEXTE DE CETTE SESSION : Sprint 3.2. Le domaine de la saisie
existe depuis le Sprint 3.1. On implemente maintenant la valorisation
des lignes par appel au service Grilles, et le controle des doublons.
Aucun endpoint dans ce sous-sprint.
SERVICE CONCERNE : service-saisie uniquement. Le service Grilles est
appele par son api, jamais par sa base.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Chaque methode publique vient avec ses tests.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : propose l'interface du client de resolution du
montant, cote service-saisie. Uniquement la forme : nom de
l'interface, signature de la methode, type de retour. Le type de
retour doit distinguer trois cas : montant obtenu, aucune grille ne
couvre la date, service injoignable. Montre-moi cette forme avant
toute implementation.
```

### Étape 2. Client de résolution

```
Une fois la forme validee, implemente le client :

1. L'interface definie a l'etape 1, dans la couche domaine ou
   application selon ce que tu juges juste, en le justifiant.
2. Son implementation dans la couche infrastructure, qui appelle
   GET /grilles/active du service Grilles selon la convention du
   Sprint 2.4.
3. La propagation du jeton, conformement a la decision prise au
   Sprint 1.3.

L'implementation doit traduire une reponse d'indisponibilite de
grille et une erreur de communication en deux cas distincts du type
de retour, jamais en la meme erreur : l'un est un cas metier normal,
l'autre une defaillance technique.

Montre l'interface puis l'implementation.
```

### Étape 3. Comportement en cas de défaillance

```
Applique la decision prise au Sprint 2.4 sur le comportement quand
le service Grilles est injoignable.

Ajoute un delai d'attente sur l'appel : un service Grilles lent ne
doit pas bloquer indefiniment la saisie. Propose une valeur et
justifie-la.

Question a trancher si CLAUDE.md n'en dit rien : faut-il reessayer
l'appel avant d'abandonner ? Un reessai ameliore la robustesse mais
allonge le temps de reponse, dont le document maitre fixe la cible a
3 secondes en moyenne. Presente les options.

Montre le code.
```

### Étape 4. Contrôle des doublons

```
Cree le service de controle des doublons, portant RG-04 :

Entree : identifiant de la fiche journaliere, identifiant du
beneficiaire, nature, session.
Sortie : doublon ou non.

Le controle porte sur la combinaison complete. Un meme beneficiaire
peut recevoir une ration le jour et un transport le soir sur la meme
journee : ce n'est pas un doublon.

Attention a la portee du controle : RG-04 s'applique a la fiche du
jour. RG-15, qui etendra le controle a tous les etats de la periode,
est le Sprint 6bis : ne l'anticipe pas ici, mais ecris le service de
maniere a ce que cette extension ne demande pas de le reecrire
entierement. Montre-moi comment tu t'y prends.

Montre le service.
```

### Étape 5. Création de ligne

```
Cree le service de creation d'une ligne de prestation, qui combine
tout :

1. Resout le beneficiaire, service du Sprint 3.1.
2. Verifie le doublon, RG-04. Si doublon, refuse avec le code du
   contrat d'api et un message citant le beneficiaire, la journee, la
   nature et la session.
3. Resout le montant, RG-03. Si aucune grille, refuse avec le code
   prevu et un message explicite pour l'agent.
4. Cree la ligne avec le montant resolu.
5. Trace l'operation dans le journal d'audit.

L'ordre compte : verifier le doublon avant d'appeler le service
Grilles evite un appel reseau inutile sur une ligne qui sera de
toute facon refusee.

Le montant n'est jamais accepte depuis l'entree utilisateur, meme
s'il y figure. Montre le service.
```

### Étape 6. Tests

```
Ecris les tests de ce sous-sprint, avec Mockito pour le client de
resolution.

Valorisation :
1. ligne valide : montant resolu depuis la grille, ligne creee
2. aucune grille ne couvre la date : ligne refusee, message explicite
3. service Grilles injoignable : comportement conforme a la decision
   du Sprint 2.4
4. le montant transmis dans l'entree utilisateur est ignore : c'est
   celui de la grille qui est enregistre
5. saisie sur une journee passee : la grille de l'epoque est
   sollicitee, pas la courante

Doublons :
6. meme beneficiaire, meme jour, meme nature, meme session : refuse
7. meme beneficiaire, meme jour, nature differente : accepte
8. meme beneficiaire, meme jour, session differente : accepte
9. meme beneficiaire, jour different, meme combinaison : accepte
10. beneficiaire different, meme combinaison : accepte

Les tests 7 a 10 sont essentiels : ils prouvent que RG-04 ne bloque
pas des saisies legitimes.

Puis lance la cartographie et verifie qu'aucun acces direct a la
base du service Grilles n'existe.

Montre les fichiers de test.
```

## 7. Fichiers à créer

| Chemin | Nature |
|---|---|
| `service-saisie/.../application/ResolutionMontantClient.java` | Interface |
| `service-saisie/.../infrastructure/ResolutionMontantHttpClient.java` | Implémentation |
| `service-saisie/.../application/ControleDoublonService.java` | RG-04 |
| `service-saisie/.../application/CreationLigneService.java` | Orchestration |
| `service-saisie/src/test/.../ResolutionMontantClientTest.java` | Tests du client |
| `service-saisie/src/test/.../ControleDoublonServiceTest.java` | Tests RG-04 |
| `service-saisie/src/test/.../CreationLigneServiceTest.java` | Tests combinés |

## 8. Commandes terminal

Le service Grilles doit tourner pour les tests d'intégration.

```bash
cd afb-rations/backend

mvn -pl service-grilles spring-boot:run
mvn -pl service-saisie test
```

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn -pl service-saisie test` | BUILD SUCCESS |
| Dix tests du sous-sprint | Tous passants |
| Montant repris de la grille | Vérifié |
| Montant utilisateur ignoré | Vérifié |
| Saisie sur date passée | Grille de l'époque sollicitée |
| Grille indisponible | Ligne refusée, message explicite |
| Service Grilles injoignable | Comportement conforme à la décision |
| Doublon exact | Refusé |
| Nature ou session différente | Accepté |
| Journée différente | Accepté |
| Aucun accès à la base du service Grilles | Vérifié par cartographie |

## 10. Points de vigilance

- **Le montant ne vient jamais du client.** S'il figure dans l'entrée, il est ignoré. Un montant accepté depuis l'extérieur ouvrirait la porte à un paiement arbitraire.
- Indisponibilité de grille et indisponibilité de service sont deux cas différents. Le premier est un cas métier normal, à expliquer à l'agent. Le second est une panne, à traiter comme telle. Les confondre donnerait un message trompeur.
- RG-04 porte sur la combinaison complète. Un contrôle sur le seul bénéficiaire empêcherait un agent de recevoir une ration et un transport le même jour, ce qui est légitime.
- Le contrôle de doublon précède l'appel réseau. L'ordre inverse fonctionne mais consomme un appel pour rien.
- Ne pas anticiper RG-15. L'extension à tous les états de la période est le Sprint 6bis. Le service doit être écrit pour l'accueillir, pas pour l'implémenter maintenant.
- L'appel au service Grilles passe par son API. Aucune datasource pointant vers `rations_grilles` ne doit apparaître dans le service Saisie.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Client de résolution isolé derrière une interface | Fait |
| Trois cas de retour distincts | Vérifié |
| Délai d'attente posé et justifié | Fait |
| Question du réessai tranchée | Fait |
| RG-04 portant sur la combinaison complète | Vérifié |
| Extension future à RG-15 anticipée sans être implémentée | Fait |
| Montant utilisateur systématiquement ignoré | Vérifié |
| Messages de refus explicites et distincts | Vérifié |
| Dix tests passants | Vérifié |
| Aucun accès direct à la base d'un autre service | Vérifié |

## 12. Commit

```bash
git add .
git commit -m "sprint-3.2: valorisation des lignes et controle des doublons

- Client de resolution du montant vers le service grilles
- Distinction entre grille indisponible et service injoignable
- Controle d'unicite journaliere sur la combinaison complete
- Tests des cas nominaux, des refus et des defaillances

Refs: RG-03, RG-04, US-04, US-05"
```

---

**Fin du Sprint 3.2** — en attente de validation avant le Sprint 3.3
