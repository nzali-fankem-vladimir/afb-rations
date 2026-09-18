# Refonte de la barre latérale et du journal d'audit, deux règles d'écriture pour la suite

*Sprint 7F.6, retours de vérification visuelle du 17 septembre 2026.*

## 1. Barre latérale groupée et réductible

**Décision.** La sidebar (`frontend/src/components/layout/Sidebar.tsx`) passe d'une liste à
plat de huit liens à trois groupes (« Mon travail », « Référentiel », « Administration »,
`navigation.ts` §`GroupeNavigation`), avec :
- un compteur de dossiers en attente sur le lien Validation, lu via
  `useCompteurValidation` (un appel `GET /reporting/demandes?statut=EN_ATTENTE_DA|DR&size=1`,
  ne retenant que `totalElements`) ;
- un bouton qui réduit ou étend la barre, préférence gardée dans `localStorage`
  (`rations.sidebar.reduite`), lu et écrit avec garde `try/catch` (navigation privée, stockage
  bloqué : repli silencieux sur l'état déplié) ;
- réduite, seule la forme ronde du logo (`Logo variant="embleme"`) reste, centrée, sans le
  texte : le repère visuel Afriland ne disparaît jamais, même à l'étroit. Chaque lien garde son
  icône, centrée, avec son libellé porté par `title` (et un `sr-only` pour le lecteur d'écran).

**Pourquoi.** Retour utilisateur sur la maquette de refonte (artefact publié au Sprint 7F.6) :
la liste à plat ne distinguait pas ce qui se fait tous les jours de ce qui s'administre
rarement, et rien ne signalait au chef d'unité qu'un dossier attendait sans ouvrir l'écran.

**Conséquence pour la suite.** Tout nouvel écran ajouté à la navigation (Sprint 7F.7 : Suivi,
Rapports, Régularisation) doit être rattaché à l'un des trois groupes dans `navigation.ts`, pas
ajouté en dehors. La sidebar elle-même n'a pas à être retouchée par sous-sprint : elle lit la
liste, elle ne la connaît pas.

## 2. Journal d'audit lisible sans JSON ni identifiant

**Décision.** `AuditPage.tsx` ne montre plus aucun JSON brut ni identifiant numérique
(`idUtilisateur`, `idEntite`). `detail_json` suit toujours la même forme, écrite une fois pour
tout le backend par `DeltaAudit.java` (`{"champ":{"avant":...,"apres":...}}` ou
`{"contexte":valeur}`) : cette régularité rend possible un analyseur générique
(`pages/audit/libellesAudit.ts`, `analyserDetailJson`) plutôt qu'un rendu écrit action par
action. Trois dictionnaires traduisent l'action, l'entité et le service en français, avec un
repli qui prettifie toute clé non répertoriée plutôt que de rien afficher. La colonne « Par »
montre un login s'il figure dans le détail (ex. `MODIFICATION_PARAMETRE` porte un contexte
`auteur`), sinon « Compte identifié » (un `idUtilisateur` existe mais sans nom disponible ici)
ou « non renseigné » (aucun auteur enregistré, le cas de 21 des 30 points de publication,
CLAUDE.md §9.2).

**Pourquoi.** Retour utilisateur : « pas de JSON ou d'ID, une présentation lisible pour un
non-informaticien ». Un contrôleur interne n'est pas un développeur.

**Limite assumée et consignée.** Sans identifiant affiché, certaines lignes (une ligne de
prestation, un dossier) ne sont plus individuellement repérables par numéro depuis cet écran
si le détail ne porte aucune information nommante (nature, session, bénéficiaire...). Les
identifiants restent en base pour l'investigation technique ; ce n'est plus le rôle de cet
écran de les afficher.

## 3. Deux règles d'écriture, valables pour toute interface et toute maquette du projet

1. **Aucun emoji comme icône.** Un glyphe géométrique sobre (bloc Unicode « Geometric
   Shapes », rendu monochrome partout) ou une bibliothèque d'icônes réelle (`lucide-react`
   côté frontend). Jamais un pictogramme coloré.
2. **Aucun tiret cadratin (« — »)** dans un texte affiché. Remplacé par une virgule, des
   parenthèses, un point milieu (« · ») entre deux termes courts, un deux-points, ou une
   phrase reformulée.

**Pourquoi.** L'utilisateur juge ces deux signes caractéristiques d'une redaction générée par
IA et ne veut plus les voir, dans l'application comme dans les maquettes. 34 occurrences du
tiret cadratin ont été corrigées dans le frontend réel à cette occasion (voir `git log` du
17 septembre 2026), au-delà des seules pages visées par le retour.

**Portée.** S'applique à toute interface (écrans réels, maquettes/artefacts) et à tout texte
destiné à l'utilisateur final du module. Ne s'applique pas rétroactivement aux commentaires
Javadoc backend ni aux journaux de décisions déjà rédigés (CLAUDE.md compris), qui sont de la
documentation interne, pas une interface.

**Conséquence pour la suite.** La règle est répercutée dans `docs/sprints/sp 7/sprint_7F_7.md`
(étape 1bis, sections 6 et 7) : toute maquette et tout écran produits à partir de ce sprint la
respectent sans qu'il faille la redemander.
