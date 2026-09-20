# Passerelle : liste blanche de routage, registre facultatif, CORS centralisé, jeton non validé

**Date :** 20 septembre 2026
**Sprint :** 8.1, passerelle API et registre de services
**Statut :** appliqué, vérifié en réel. À respecter par les Sprints 8.2 (conteneurisation), 8.3 (Kubernetes) et 9.

---

## 1. Le routage est une liste blanche, pas une liste noire

Seuls les chemins déclarés dans `gateway/application.yml` sont exposés ; tout
le reste rend 404 à la passerelle. Les dix endpoints internes du module ne sont
donc pas exposés **parce qu'ils n'y figurent pas**.

L'alternative — router `/identite/**` puis exclure `/identite/habilitation` —
aurait laissé passer tout endpoint interne ajouté plus tard. Le défaut aurait
été silencieux : l'endpoint marche, personne ne remarque qu'il est désormais
joignable de l'extérieur.

**Conséquence pour les sprints suivants** : tout nouvel endpoint destiné au
frontend doit être ajouté à cette table, faute de quoi l'écran qui l'appelle
rendra 404 sans que le service soit en cause. C'est le prix, assumé, de la
liste blanche.

## 2. Chaque route porte son verbe, et les identifiants sont contraints

Deux chemins portent à la fois un endpoint du contrat et un endpoint interne :

- `/transmission/processus/{id}` : **GET** au contrat, **POST** interne. Exposé,
  le POST permettrait d'envoyer en paiement un état choisi depuis l'extérieur,
  en contournant le circuit de validation (chemin de double transmission que
  CT-22 vérifie).
- `/processus/{id}/integration` : **PUT** et **GET**, tous deux internes.

Même leçon qu'au Sprint 5.3 sur le `securityMatcher` : une règle écrite sur le
chemin sans le verbe se trompe de cible.

Les identifiants sont par ailleurs contraints aux chiffres, `{id:[0-9]+}` :
sans cela, `GET /processus/recherche` (interne) serait capté par la règle
`GET /processus/{id}`. Vérifié en réel, pas supposé : `/processus/recherche`
rend 404, `/processus/1` rend 401.

## 3. Six endpoints hors contrat sont routés, et ce n'est pas une entorse

Le contrat compte 26 endpoints. Six autres, nés après lui, sont appelés par le
frontend : `GET /parametres/fonctionnalites`, `GET` et `PUT /parametres/{code}`,
`GET /processus/{id}/document`, `POST /grilles/{id}/retrait`,
`GET /audit/actions`.

Le guide du sprint n'en signalait qu'un, avec l'avertissement juste : le classer
interne « masquerait la régularisation à tous les agents, sans aucune erreur
visible ». Les cinq autres ont été relevés dans les contrôleurs et dans les
modules d'appel du frontend.

**Règle qui en découle** : la question n'est pas « cet endpoint est-il au
contrat ? » mais « le navigateur l'appelle-t-il ? ». Les deux listes ne
coïncident plus depuis le Sprint 7F.

## 4. Le registre est facultatif en local, et les deux modes sont éprouvés

`EUREKA_ENABLED`, éteint par défaut. Les adresses de routage sont des variables
d'environnement : défaut local en adresse fixe, `lb://service-xxx` en
environnement partagé. Une seule table de routage, deux modes.

Motif : poste mesuré à 1,0 Go de mémoire libre avec l'infrastructure lancée. Un
registre obligatoire ajoutait une JVM et une fenêtre de 10 à 30 secondes pendant
laquelle la passerelle rend 503 alors que le service tourne.

**Les deux modes ont été vérifiés**, et c'est le point : un interrupteur dont on
ne teste qu'une position finit par cacher une position cassée.

## 5. CORS : désactivé explicitement dans les services, pas supprimé

Les sept `SecurityConfig` portent `.cors(cors -> cors.disable())` avec le motif
en commentaire, plutôt qu'un silence. Un lecteur qui ne trouve aucune mention du
CORS se demande s'il a été oublié ; un lecteur qui trouve une désactivation
commentée sait que c'est délibéré.

**Défaut latent trouvé et corrigé par la centralisation.** Seul le service
Workflow exposait `Content-Disposition`. Le Reporting ne l'exposait pas alors
que `exporterRapport` lit le même en-tête : le nom des exports PDF et Excel
retombait **toujours** sur le repli générique. Jamais constaté, le
téléchargement réel n'ayant pas été cliqué au Sprint 7F.7. C'est l'argument même
de la centralisation : un réglage oublié dans un service sur sept ne se voit
pas, un réglage unique se voit.

## 6. La passerelle ne valide pas le jeton (décision de l'utilisateur)

Une requête sans jeton est routée, et c'est le service qui répond 401.

Au-delà des motifs d'architecture (un seul endroit décide qui entre, un accès
direct reste protégé, une panne de validation à la passerelle ferait tomber tout
le module d'un coup), un motif mesuré a pesé : **le frontend ne reconnecte pas
sur un 401 quand un jeton était joint** (`apiClient.ts`, Sprint 7F.3, pour
éviter une boucle sans fin). Une passerelle qui valide et se trompe de
configuration enverrait donc l'utilisateur dans une impasse sans message, avec
le défaut logé là où personne ne regarde.

Écartée aussi la vérification de simple présence de l'en-tête : n'importe quelle
valeur passerait, ce n'est pas une sécurité, et elle bloquerait une future route
publique sans explication.

## 7. Le frontend ne peut plus, structurellement, appeler un service

`creerClientApi` n'est plus exportée par `apiClient.ts`. Les huit modules d'API
importent le client unique. Un appel à une adresse de service ne compile plus.

C'est la même discipline que `PerimetreDuModuleTest` (1.3),
`CleInterneJamaisJournaliseeTest` (5.2) ou `Rg15SansAppelReseauTest` (6bis.2) :
l'invariant est tenu par le compilateur, pas par la vigilance du relecteur.

`VITE_API_BASE_URL` est par ailleurs **exigée** : absente, Axios prendrait
l'origine de la page pour base et les appels partiraient vers le serveur Vite,
qui rendrait du HTML en 404. L'écran afficherait une erreur réseau sans rapport
avec la cause.

## 8. Leçon d'outillage : retirer une clé YAML n'est pas retirer une ligne

En retirant `app.cors.allowed-origins` des sept services, la première tentative
a supprimé la clé racine `app:` avec elle. Tout ce qui vivait dessous — les URL
des services appelés, la clé interne `X-Cle-Interne`, le topic d'audit, le
répertoire des pièces jointes — s'est retrouvé rattaché à `spring:`. **Aucun
service n'aurait démarré, et la compilation ne voit rien** : c'est du YAML.

Le défaut a été trouvé en **analysant** les fichiers avant et après et en
comparant les chemins de propriétés un à un, pas en relisant le diff, où il
passait pour une suppression de quatre lignes.

**Règle** : après toute modification en lot d'un fichier de configuration,
comparer les propriétés analysées, jamais seulement le diff. Même famille que le
bean `ObjectMapper` absent au 5.1, `@EnableKafka` manquant au 5.2 et le
`securityMatcher` sans verbe au 5.3 : un câblage qui ne se voit qu'en
assemblant, ou ici qu'en démarrant.

## 9. Correctif hors périmètre : deux tests dépendaient de l'état ambiant de la base

`GrilleTarifaireRepositoryTest` tourne contre la vraie base `rations_grilles`.
Deux de ses tests affirmaient des choses sur son contenu du moment :

- test 10 : la grille RATION / JOUR applicable aujourd'hui a une `date_fin` nulle ;
- test 12 bis : la même grille couvre aujourd'hui et dans un mois.

Les deux sont tombés. Constat mesuré, et non déduit : la base porte la grille 1
(1500 FCFA, 01/08 au **30/09**) et la grille 141 (1200 FCFA, à partir du
**01/10**), cette dernière créée le **17 septembre 2026 à 14h01**, pendant les
vérifications manuelles des écrans de grilles. Une bascule a donc été proposée
et validée, et la fermeture programmée du Sprint 2.3 a fait exactement son
travail.

**Le code était juste, les tests étaient faux.** C'est l'erreur que CLAUDE.md
§15 interdit nommément : un test ne dépend pas de la valeur ambiante d'une
donnée que l'exploitation fait légitimement évoluer. La règle y vise
`parametre_systeme` ; le contenu de la table des grilles relève de la même
famille.

Correction retenue :

- test 10 vérifie désormais que la grille rendue **couvre** la date demandée
  (bornes incluses), ce qui est son objet, au lieu d'affirmer qu'aucune
  remplaçante n'a jamais été programmée ;
- test 12 bis s'énonce **négativement**, comme CT-25 lui-même : à la date de
  prise d'effet, la grille applicable n'est **pas** la proposition et son
  montant n'est **pas** celui proposé. L'ancienne formulation exigeait
  l'ancienne grille elle-même, ce qui supposait qu'aucune autre bascule n'est
  programmée dans le mois — étranger à ce que le test vérifie ;
- la grille courante est comparée à un **relevé pris avant** la proposition, au
  lieu d'être supposée égale à la grille du jour. Les deux divergent dès la
  première bascule, distinction que le test 13 faisait déjà explicitement.

Ces tests n'avaient pas tourné depuis le 17 septembre, le Sprint 7F.7 étant
purement frontend. **Leçon** : une suite de tests qui n'est pas exécutée à
chaque sprint laisse vieillir ses hypothèses en silence.

Option écartée : remettre la base dans son état d'avant. Elle effacerait une
grille légitimement validée et ferait mentir le journal d'audit, qui porte déjà
l'événement `VALIDATION_GRILLE` correspondant.

## 10. Retour utilisateur pendant la vérification : le prénom d'un bénéficiaire est facultatif

Demande de l'utilisateur : certains bénéficiaires n'ont pas de prénom, le nom reste obligatoire.

**Décision (option A, choisie par l'utilisateur) : un prénom absent, vide ou blanc est
enregistré comme chaîne vide, jamais `NULL`.** La colonne `beneficiaires.prenom` reste
`NOT NULL` : aucune migration, et tout ce qui affiche ou transmet le prénom continue de
recevoir une chaîne. Option écartée : `NULL`, plus « propre » en théorie mais imposant une
migration et la revue de chaque lecteur (documents, comptabilité, audit).

**Point unique** : `Beneficiaire.normaliserPrenom`, appelé par le constructeur **et** par
`corrigerIdentite`. Création et correction passent toutes deux par là, donc aucun chemin ne
peut l'oublier. Vérifié par `BeneficiairePrenomFacultatifTest`.

**Deux DTO à lever, pas un.** `IdentiteBeneficiaireRequest` (création) perdait son
`@NotBlank`, mais `ModificationLigneRequest` portait un `@Pattern` qui refusait aussi la
chaîne vide : sans le retirer, on aurait pu créer un bénéficiaire sans prénom mais jamais
**retirer** un prénom existant. Sur la modification, `null` signifie « inchangé » et `""`
signifie « retirer » : deux demandes distinctes, verrouillées par un test.

**Compatibilité comptable vérifiée avant de décider** : `ConstructionChargeService` ne refuse
que le cas « nom **et** prénom vides » (`BENEFICIAIRE_SANS_NOM`), donc un prénom absent ne
bloque aucune transmission. Le nom, lui, reste obligatoire à trois étages (formulaire, DTO,
contrôle de charge).

**Pour les sprints suivants** : tout code qui affiche nom puis prénom produit un espace final
pour un bénéficiaire sans prénom. Invisible en HTML et dans un PDF, à retirer si un jour une
chaîne est comparée ou tronquée.

## 11. Incident d'environnement : compiler sous des JVM qui tournent

Pendant la vérification, `GET /identite/utilisateurs` a rendu un 500 (`NoClassDefFoundError:
UtilisateurSpecifications`) : les fichiers compilés de `service-identite` avaient disparu de
`target/classes`. Cause : mes relances de `mvn clean test` puis `mvn test` **pendant que les
services tournaient**, chaque JVM chargeant ses classes à la demande depuis ce répertoire.
Ce n'était ni la passerelle (l'appel direct rendait le même 500) ni le code.

**Règle** : arrêter les services avant tout `mvn clean` ou `mvn test` sur un module qu'ils
utilisent, recompiler, puis redémarrer. Même famille que la consigne du Sprint 2.3 sur
`~/.m2` : le build et l'exécution ne voient pas toujours les mêmes fichiers.

## 12. Point ouvert laissé au Sprint 8.2

Le registre n'a aucune sécurité : son tableau de bord est ouvert à qui l'atteint.
Il ne doit être joignable que sur le réseau interne. À traiter avec la DSI avec
le reste des manifests (point D-08, URL publiques, déjà en attente).
