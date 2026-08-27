# SPRINT 1.2

## Administration des utilisateurs

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Exposer les endpoints d'administration : consultation de la liste et attribution du rôle et du code unité |
| **Livrable** | Deux endpoints protégés par le rôle ADMIN, avec leurs DTO, leurs tests et leur traçabilité |
| **Durée** | Une journée |
| **Prérequis** | Sprint 1.1 validé et commité |
| **Sprint suivant** | 1.3, habilitations inter-services et journal d'audit |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Liste paginée et filtres (étapes 2-3) | Sonnet | Moyen |
| Attribution du rôle et tests (étapes 4-6) | Sonnet | Moyen |

Aucun changement manuel de modèle pendant ce sous-sprint.

## 2. Outil de cartographie

Facultatif ici : le périmètre reste circonscrit à un seul service dont l'état est connu depuis le sous-sprint précédent.

## 3. Contexte

Le domaine Utilisateur est consolidé et la portée d'accès existe. Ce sous-sprint l'expose : deux endpoints réservés à l'administrateur, décrits dans le contrat d'API, section 2.

Le premier liste les utilisateurs avec pagination et filtres. Le second attribue un rôle applicatif et un code unité. C'est cet endpoint qui matérialise une décision structurante du projet : **l'identité vient de l'annuaire, l'habilitation métier vient du module.** Keycloak ne connaît pas la distinction entre un agent de Douala et un agent de Bafoussam ; le module, si.

Ce sous-sprint produit aussi le premier écran de liste paginé du backend. Le format de pagination retenu ici servira de référence pour les listes des Sprints 3, 4 et 6.

## 4. Objectifs

- `GET /identite/utilisateurs` : liste paginée, filtrable par rôle, code unité et statut actif
- `PUT /identite/utilisateurs/{id}/role` : attribution du rôle et du code unité
- DTO d'entrée et de sortie, avec validation
- Protection par le rôle ADMIN, refus en 403 pour les autres rôles
- Traçabilité de toute modification de profil
- Tests unitaires et tests d'intégration des deux endpoints

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| US-19 | Paramétrage des profils et habilitations par l'administrateur |
| US-02 | Accès limité aux fonctions autorisées par le profil |
| CT-39 | Attribution d'un rôle applicatif et d'un code unité |
| CT-04 | Refus d'accès hors périmètre, tracé |

Codes HTTP applicables, document maître section 7.1 : 200, 400, 401, 403, 404, 409.

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, section 11 sur les
contrats d'api. Confirme en 3 lignes les trois endpoints du service
Identite.

CONTEXTE DE CETTE SESSION : Sprint 1.2, administration des
utilisateurs. Le domaine et la portee d'acces existent depuis le
Sprint 1.1. On expose maintenant les deux endpoints reserves a
l'administrateur.
SERVICE CONCERNE : service-identite uniquement.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Aucune entite JPA n'est exposee en api : DTO en entree comme en
  sortie.
- Chaque methode publique vient avec ses tests.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : propose la forme des DTO de ce sous-sprint : le DTO
de sortie pour la liste des utilisateurs, et le DTO d'entree pour
l'attribution du role et du code unite. Montre-moi leur structure et
les contraintes de validation avant d'ecrire le moindre controleur.
```

### Étape 2. DTO et validation

```
Une fois la forme validee, ecris les DTO :

1. Sortie liste : id, login, nom, prenom, email, role, codeUnite,
   actif, dateDernierAcces. Pas de sub_keycloak, qui est un detail
   d'implementation du fournisseur d'identite.
2. Entree attribution : role et codeUnite.

Contraintes de validation sur l'entree :
- role obligatoire, valeur appartenant a RoleEnum
- codeUnite : exactement cinq chiffres quand il est renseigne
- codeUnite obligatoire pour AGENT_UNITE et CHEF_UNITE_DA, facultatif
  pour les roles a portee nationale

Sur cette derniere contrainte, dis-moi si tu la mets dans la
validation du DTO ou dans le service, et pourquoi.

Montre les fichiers.
```

### Étape 3. Liste paginée

```
Cree le service et l'endpoint GET /identite/utilisateurs :

- Pagination : parametres de page et de taille, avec des valeurs par
  defaut raisonnables et une taille maximale bornee.
- Filtres optionnels et combinables : role, codeUnite, actif.
- Tri par defaut : nom puis prenom.
- Reponse : la page d'utilisateurs plus les metadonnees de
  pagination.

Le format de reponse paginee servira de reference pour toutes les
listes du projet. Montre-moi la forme retenue avant de la
generaliser.

Reserve l'endpoint au role ADMIN.
Montre le service puis le controleur.
```

### Étape 4. Attribution du rôle

```
Cree le service et l'endpoint PUT /identite/utilisateurs/{id}/role :

1. Verifie que l'utilisateur cible existe, sinon 404.
2. Applique les contraintes de coherence entre role et code unite.
3. Met a jour le role et le code unite.
4. Retourne le profil mis a jour.

Trois points a trancher avec moi avant d'ecrire, ne decide pas seul :
- un administrateur peut-il modifier son propre role ?
- que se passe-t-il si l'on retire le role ADMIN au dernier
  administrateur du systeme ?
- une modification de role doit-elle invalider les sessions en cours
  de l'utilisateur concerne ?

Presente les options et leurs consequences, attends ma decision.
```

### Étape 5. Traçabilité

```
Toute modification de profil doit etre tracee, conformement au
document maitre section 7.3 : la journalisation intervient apres la
modification d'etat et avant la notification.

Applique la decision prise au Sprint 0.2 sur l'emplacement de
audit_log. Enregistre l'auteur, l'action, l'entite ciblee, son
identifiant et un delta avant/apres du role et du code unite.

Si l'infrastructure d'audit n'existe pas encore, cree le strict
minimum ici et signale-le : elle sera generalisee au Sprint 1.3.

Montre le code d'appel dans le service d'attribution.
```

### Étape 6. Tests

```
Ecris les tests de ce sous-sprint.

Tests unitaires du service, avec Mockito :
1. attribution nominale d'un role avec code unite
2. attribution d'un role a portee nationale sans code unite
3. utilisateur cible inexistant : erreur attendue
4. code unite invalide, quatre chiffres : erreur attendue
5. role AGENT_UNITE sans code unite : erreur attendue
6. comportement conforme aux trois decisions prises a l'etape 4

Tests d'integration des endpoints :
7. GET /identite/utilisateurs sans jeton : 401
8. GET /identite/utilisateurs avec un jeton AGENT_UNITE : 403
9. GET /identite/utilisateurs avec un jeton ADMIN : 200, page
   correcte
10. filtres combines role et codeUnite : resultats coherents
11. PUT sur un identifiant inexistant : 404
12. PUT nominal avec un jeton ADMIN : 200, profil mis a jour et
    trace ecrite

Donnees camerounaises, codes guichets reels.
Montre les deux fichiers de test.
```

## 7. Fichiers à créer ou modifier

| Chemin | Nature |
|---|---|
| `service-identite/.../api/dto/UtilisateurResponse.java` | Création |
| `service-identite/.../api/dto/AttributionRoleRequest.java` | Création |
| `service-identite/.../api/dto/PageResponse.java` | Création, format de pagination de référence |
| `service-identite/.../application/UtilisateurAdminService.java` | Création |
| `service-identite/.../api/UtilisateurAdminController.java` | Création |
| `service-identite/.../infrastructure/UtilisateurRepository.java` | Complément si nécessaire |
| `service-identite/src/test/.../UtilisateurAdminServiceTest.java` | Création |
| `service-identite/src/test/.../UtilisateurAdminControllerIT.java` | Création |

## 8. Commandes terminal

```bash
cd afb-rations/backend

mvn -pl service-identite test
mvn -pl service-identite spring-boot:run
```

Vérifications manuelles, avec un jeton ADMIN obtenu depuis Keycloak :

```bash
curl -H "Authorization: Bearer <jeton_admin>" \
  "http://localhost:8081/identite/utilisateurs?page=0&size=10"

curl -H "Authorization: Bearer <jeton_admin>" \
  "http://localhost:8081/identite/utilisateurs?role=AGENT_UNITE&codeUnite=00002"

curl -X PUT -H "Authorization: Bearer <jeton_admin>" \
  -H "Content-Type: application/json" \
  -d '{"role":"CHEF_UNITE_DA","codeUnite":"00002"}' \
  http://localhost:8081/identite/utilisateurs/1/role
```

Vérification du refus, avec un jeton non administrateur :

```bash
curl -i -H "Authorization: Bearer <jeton_agent>" \
  http://localhost:8081/identite/utilisateurs
```

Attendu : 403.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn -pl service-identite test` | BUILD SUCCESS, aucune régression |
| Douze tests du sous-sprint | Tous passants |
| Liste sans jeton | 401 |
| Liste avec un rôle non ADMIN | 403 |
| Liste avec un jeton ADMIN | 200, pagination correcte |
| Filtres combinés | Résultats cohérents |
| Attribution sur identifiant inexistant | 404 |
| Attribution nominale | 200, profil modifié en base |
| Code unité à quatre chiffres | 400 |
| Trace écrite après modification | Ligne présente dans le journal |
| Aucune entité JPA exposée en API | Vérifié |

## 10. Points de vigilance

- **Le format de pagination retenu ici est repris partout ensuite.** Le valider avant de généraliser évite de reprendre trois services plus tard.
- Ne pas exposer `sub_keycloak` dans les réponses d'API. C'est un identifiant technique du fournisseur d'identité, sans intérêt pour le client et inutilement révélateur.
- La cohérence entre rôle et code unité est une règle de fond, pas une simple validation de format. Un agent sans code unité ne pourrait accéder à aucune donnée.
- Les trois questions de l'étape 4 touchent au contrôle interne, en particulier celle du dernier administrateur. Ne pas trancher seul, consigner la décision dans CLAUDE.md.
- La taille de page doit être bornée. Sans borne, un appel avec une taille excessive dégrade le service.
- Ne pas créer d'endpoint de création d'utilisateur. Les comptes viennent de l'annuaire ; le module ne fait qu'attribuer des habilitations à des comptes existants.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| DTO d'entrée et de sortie créés, avec validation | Fait |
| Format de pagination arrêté et validé | Fait |
| `GET /identite/utilisateurs` conforme au contrat d'API | Vérifié |
| `PUT /identite/utilisateurs/{id}/role` conforme au contrat | Vérifié |
| Endpoints réservés au rôle ADMIN | Vérifié |
| Trois décisions de l'étape 4 tranchées et consignées | Fait |
| Modification de profil tracée | Vérifié |
| Douze tests passants | Vérifié |
| Aucune entité JPA exposée | Vérifié |
| Aucun endpoint de création d'utilisateur | Vérifié |

## 12. Commit

```bash
git add .
git commit -m "sprint-1.2: administration des utilisateurs

- Liste paginee et filtrable, reservee au role admin
- Attribution du role applicatif et du code unite
- Format de pagination de reference pour le projet
- Tracabilite des modifications de profil

Refs: US-19, CT-39, contrat d'api section 2"
```

---

**Fin du Sprint 1.2** — en attente de validation avant le Sprint 1.3
