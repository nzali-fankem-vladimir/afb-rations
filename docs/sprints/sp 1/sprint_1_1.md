# SPRINT 1.1

## Domaine Utilisateur et portée d'accès

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Consolider le domaine Utilisateur posé au Sprint 0.4 et introduire la notion de portée d'accès par rôle |
| **Livrable** | Entité complète, repository enrichi, service de portée, tests JUnit |
| **Durée** | Une journée |
| **Prérequis** | Sprint 0.7 validé et commité |
| **Sprint suivant** | 1.2, administration des utilisateurs |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Consolidation de l'entité et du repository (étapes 2-3) | Sonnet | Moyen |
| Service de portée et tests (étapes 4-5) | Sonnet | Moyen |

Aucun changement manuel de modèle pendant ce sous-sprint.

## 2. Outil de cartographie

Utile pour confirmer l'état réel de service-identite après le Sprint 0. Lancer la commande avant l'étape 1, exploiter le rapport dans un second message.

```
py -3.14 -m graphify update .
```

## 3. Contexte

Premier sous-sprint fonctionnel du projet. Le Sprint 0.4 a créé l'essentiel du domaine Utilisateur pour faire fonctionner l'authentification : l'entité, le repository, la résolution depuis le jeton et l'endpoint de profil. Ce sous-sprint ne les refait pas, il les consolide et les complète.

Le manque principal à combler est la **portée d'accès**. Le document de conception, section 10, précise que chaque profil ne voit pas les mêmes dossiers : un agent et un chef d'unité ne voient que leur unité, un directeur réseau voit son réseau, l'analyste RH, la directrice RH et l'administrateur voient toutes les unités. Cette notion n'existe nulle part dans le code : elle est pourtant consommée par les Sprints 3, 4 et 6 pour filtrer les données.

Deuxième manque : la couverture de tests. Le Sprint 0.4 a produit des tests de fumée sur le convertisseur de rôles et l'endpoint de profil. Le Sprint 1 exige des tests unitaires en bonne et due forme sur chaque méthode publique.

## 4. Objectifs

- Entité `Utilisateur` complète et conforme au dictionnaire de données, champ par champ
- Repository enrichi des méthodes de recherche dont les sous-sprints suivants auront besoin
- Notion de portée d'accès introduite et testée
- Mise à jour de la date de dernier accès à chaque résolution du profil courant
- Couverture de tests JUnit sur toutes les méthodes publiques du domaine

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| US-01 | Connexion avec l'identifiant `prenom_nom`, chargement du rôle et du code unité |
| US-02 | Accès limité aux fonctions autorisées par le profil |
| RG-12 | Séparation des tâches, préparée ici par la portée, appliquée au Sprint 4 |
| CT-01 à CT-05 | Scénario SC-01 du document de scénarios |

La portée d'accès n'est pas une règle numérotée : elle vient du document de conception, section 10.

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, sections 4 et 10.
Confirme en 3 lignes ce que tu y as trouve sur la table utilisateurs
et sur l'authentification.

CONTEXTE DE CETTE SESSION : Sprint 1.1, domaine Utilisateur. Le
Sprint 0.4 a deja cree l'entite Utilisateur, son repository, la
resolution depuis le jeton et GET /identite/moi. On ne les refait
pas : on les consolide, on ajoute la notion de portee d'acces, et on
complete la couverture de tests.
SERVICE CONCERNE : service-identite uniquement.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Chaque methode publique vient avec ses tests JUnit : cas nominal
  et deux cas d'erreur au minimum.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question
  plutot que de supposer.

PREMIERE ACTION : a partir de la cartographie, dresse l'inventaire de
ce qui existe deja dans service-identite apres le Sprint 0 : classes,
methodes, tests. Compare-le au dictionnaire de donnees pour la table
utilisateurs et liste-moi ce qui manque ou ce qui differe. Ne corrige
rien encore.
```

### Étape 2. Consolidation de l'entité

```
A partir de l'inventaire, complete l'entite Utilisateur pour qu'elle
corresponde exactement au dictionnaire de donnees : id, login,
sub_keycloak, matricule, nom, prenom, email, role, code_unite,
actif, date_creation, date_dernier_acces.

Verifie en particulier :
- role est typé par RoleEnum, pas par une chaine libre
- code_unite est un VARCHAR(5), nullable pour les roles a portee
  nationale
- aucun champ de mot de passe n'existe

Si l'entite est deja conforme, dis-le et n'y touche pas. Montre le
fichier seulement s'il change.
```

### Étape 3. Repository

```
Complete UtilisateurRepository avec les methodes dont les
sous-sprints 1.2 et 1.3 auront besoin :

1. findBySubKeycloak(String subKeycloak) : Optional<Utilisateur>
   (existe deja depuis le Sprint 0.4)
2. findByLogin(String login) : Optional<Utilisateur>
3. findByRole(RoleEnum role) : List<Utilisateur>
4. findByCodeUnite(String codeUnite) : List<Utilisateur>
5. une methode paginee de recherche avec filtres optionnels sur le
   role, le code unite et le statut actif

Pour la cinquieme, montre-moi ta proposition avant de l'ecrire :
requete derivee, Specification ou requete JPQL. Explique ton choix.
```

### Étape 4. Service de portée d'accès

C'est le cœur de ce sous-sprint.

```
Cree un service qui determine la portee d'acces d'un utilisateur,
selon le document de conception section 10 :

- AGENT_UNITE et CHEF_UNITE_DA : leur unite uniquement
- DIRECTEUR_RESEAU_DR : son reseau
- ARH, DRH, ADMIN : toutes les unites

Le service expose au minimum :
1. une methode indiquant si un utilisateur peut acceder aux donnees
   d'un code unite donne
2. une methode retournant la liste des codes unite accessibles, ou
   une indication de portee nationale

Point a trancher avec moi avant d'ecrire : la notion de reseau du
Directeur Reseau n'est definie nulle part dans les specifications.
Trois options : un champ supplementaire sur l'utilisateur, une table
de rattachement unite vers reseau, ou une portee nationale par defaut
en attendant que le metier tranche. Presente-moi les trois avec leurs
consequences et attends ma decision.
```

### Étape 5. Résolution du profil courant

```
Complete le service de resolution du profil courant issu du Sprint
0.4 :

1. Met a jour date_dernier_acces a chaque resolution reussie.
2. Refuse un utilisateur dont actif vaut false, avec l'erreur
   correspondant au contrat d'api (403, code explicite).
3. Expose la portee d'acces dans le profil retourne.

Montre le service, puis ses tests.
```

### Étape 6. Tests JUnit

```
Ecris les tests unitaires du domaine, avec Mockito :

Sur le service de portee :
1. agent d'unite accedant a sa propre unite : autorise
2. agent d'unite accedant a une autre unite : refuse
3. chef d'unite accedant a une autre unite : refuse
4. analyste RH accedant a n'importe quelle unite : autorise
5. administrateur accedant a n'importe quelle unite : autorise

Sur la resolution du profil courant :
6. jeton valide, utilisateur actif : profil retourne, date de
   dernier acces mise a jour
7. jeton valide, utilisateur inactif : refus
8. jeton valide, aucun profil local : comportement conforme a la
   decision prise au Sprint 0.4

Donnees de test camerounaises et codes guichets reels (00001, 00002).
Montre le fichier de test.
```

## 7. Fichiers à créer ou modifier

| Chemin | Nature |
|---|---|
| `service-identite/.../domaine/Utilisateur.java` | Modification si écart au dictionnaire |
| `service-identite/.../infrastructure/UtilisateurRepository.java` | Ajout des méthodes de recherche |
| `service-identite/.../domaine/PorteeAcces.java` | Création, type ou objet de portée |
| `service-identite/.../application/PorteeAccesService.java` | Création |
| `service-identite/.../application/UtilisateurCourantService.java` | Modification |
| `service-identite/src/test/.../PorteeAccesServiceTest.java` | Création |
| `service-identite/src/test/.../UtilisateurCourantServiceTest.java` | Création ou complément |

## 8. Commandes terminal

```bash
cd afb-rations/backend

mvn -pl service-identite test
mvn -pl service-identite spring-boot:run
```

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn -pl service-identite test` | BUILD SUCCESS, aucune régression |
| Huit tests du sous-sprint | Tous passants |
| Entité conforme au dictionnaire | Comparaison champ par champ |
| Aucun champ de mot de passe | Vérifié |
| `GET /identite/moi` toujours fonctionnel | 200 avec jeton, 401 sans |
| Date de dernier accès mise à jour | Vérifiée en base après un appel |
| Utilisateur inactif | Refus avec le code d'erreur du contrat |

## 10. Points de vigilance

- **Ne pas recréer ce qui existe.** Le Sprint 0.4 a produit une partie du domaine. Repartir de zéro ferait perdre la configuration de sécurité qui fonctionne.
- La notion de réseau du Directeur Réseau n'est définie nulle part dans les spécifications. Ne pas l'inventer : poser la question, consigner la réponse dans CLAUDE.md.
- `code_unite` est nullable pour les rôles à portée nationale. Un `NOT NULL` sur ce champ empêcherait de créer un compte ARH ou DRH.
- La portée d'accès sera consommée par les Sprints 3, 4 et 6. Une signature de méthode instable obligerait à reprendre ces services : la valider avant de généraliser.
- Les tests utilisent des noms camerounais et des codes guichets réels. Voir document maître, section 7.5.
- Ne créer aucun endpoint dans ce sous-sprint : les endpoints d'administration sont le sous-sprint 1.2.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Entité conforme au dictionnaire, champ par champ | Vérifié |
| Repository enrichi des cinq méthodes | Fait |
| Service de portée d'accès créé | Fait |
| Question sur le réseau du DR posée et tranchée | Fait |
| Date de dernier accès mise à jour à chaque résolution | Vérifié |
| Utilisateur inactif refusé | Vérifié |
| Huit tests JUnit passants | Vérifié |
| `mvn test` sans régression | Vérifié |
| Aucun endpoint créé dans ce sous-sprint | Vérifié |

## 12. Commit

```bash
git add .
git commit -m "sprint-1.1: domaine utilisateur et portee d'acces

- Entite et repository consolides sur le dictionnaire de donnees
- Service de portee d'acces par role
- Mise a jour de la date de dernier acces
- Tests unitaires du domaine

Refs: US-01, US-02, document de conception section 10"
```

---

**Fin du Sprint 1.1** — en attente de validation avant le Sprint 1.2
