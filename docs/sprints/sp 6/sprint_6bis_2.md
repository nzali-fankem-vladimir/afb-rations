# SPRINT 6BIS.2

## Unicité inter-états et clôture du Sprint 6bis

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Implémenter RG-15, le contrôle qui empêche un état complémentaire de repayer un bénéficiaire déjà servi |
| **Livrable** | Contrôle d'unicité inter-états, traversant deux services, clôture du sprint |
| **Durée** | Une à deux journées |
| **Prérequis** | Sprint 6bis.1 validé et commité |
| **Sprint suivant** | 7F.1, socle du frontend |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Ensemble du sous-sprint | Opus | Élevé |

Maintenir Opus effort élevé. RG-15 est le seul rempart contre le double paiement dans le cadre d'une régularisation.

## 2. Outil de cartographie

Utilisation obligatoire. Le contrôle traverse deux services : la cartographie doit confirmer qu'il ne le fait pas par un accès direct à la base.

```
py -3.14 -m graphify update .
```

## 3. Contexte

Dernier sous-sprint de la régularisation. Le sous-sprint précédent permet d'ouvrir un état complémentaire, mais rien n'empêche encore d'y ressaisir un bénéficiaire déjà payé dans l'état d'origine. C'est précisément ce que RG-15 interdit.

**La règle, telle qu'elle a été formulée pendant la phase documentaire :** aucune ligne ne peut reproduire une combinaison bénéficiaire, journée, nature et session déjà présente dans un autre état de la même unité et de la même période, normal ou complémentaire.

RG-04, posée au Sprint 3.2, contrôlait déjà le doublon, mais à l'échelle de la fiche du jour. RG-15 étend le périmètre à l'ensemble des états de la période. Le service Saisie avait été écrit pour accueillir cette extension sans être réécrit.

Une difficulté d'architecture s'y ajoute. Le contrôle porte sur trois tables réparties dans deux bases : `processus_mensuel` vit dans le service Workflow, `fiche_journaliere` et `ligne_prestation` dans le service Saisie. Aucune requête SQL ne peut les joindre.

## 4. Objectifs

- Contrôle d'unicité inter-états, traversant les deux services
- Extension du contrôle existant sans réécriture du service Saisie
- Message de refus explicite pour l'agent
- Vérification de bout en bout de la régularisation
- Clôture du Sprint 6bis et mise à jour de CLAUDE.md

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| RG-15 | Unicité inter-états sur la période |
| RG-04 | Unicité journalière, dont RG-15 est l'extension |
| US-18 | Vérification de l'unicité avant acceptation d'une ligne de régularisation |
| CT-35 | Bénéficiaire non payé sur une journée : ligne acceptée |
| CT-36 | Combinaison déjà présente dans l'état d'origine : ligne refusée |

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, section 6, regles
RG-04 et RG-15. Confirme en 3 lignes la difference de perimetre entre
les deux.

CONTEXTE DE CETTE SESSION : Sprint 6bis.2, unicite inter-etats.
L'ouverture d'un etat complementaire existe depuis le sous-sprint
precedent, mais rien n'empeche encore d'y ressaisir un beneficiaire
deja paye. RG-15 est ce rempart.

Difficulte : le controle porte sur processus_mensuel, qui vit dans la
base du service Workflow, et sur fiche_journaliere et
ligne_prestation, qui vivent dans celle du service Saisie. Aucune
jointure SQL n'est possible.
SERVICE CONCERNE : service-saisie principalement, avec appel au
service Workflow.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Aucun acces direct a la base d'un autre service.
- Chaque methode publique vient avec ses tests.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : avant tout code, decris-moi la sequence exacte du
controle RG-15 quand l'agent saisit une ligne dans un etat
complementaire. Precise ou chaque donnee est lue, quel service est
appele, et dans quel ordre. Montre-moi cette sequence en pseudo-code,
sans java. Attends ma validation.
```

### Étape 2. Obtention des états de la période

```
Une fois la sequence validee, implemente la premiere moitie : obtenir
la liste des processus de la meme unite et de la meme periode.

Le service Saisie interroge le service Workflow par son api. Si
l'endpoint necessaire n'existe pas, cree-le cote Workflow : il doit
retourner les identifiants des processus d'une unite pour une
periode, quel que soit leur type et leur statut.

Question a trancher : faut-il inclure les processus au statut
RETOURNE et EN_COURS_SAISIE dans le controle ? Une ligne saisie dans
un etat encore en cours n'a pas ete payee, mais elle le sera si cet
etat aboutit. L'exclure autoriserait deux saisies concurrentes de la
meme prestation. Presente les deux lectures, j'arbitre.

Montre l'interface, son implementation, et l'endpoint cote Workflow
s'il a ete cree.
```

### Étape 3. Contrôle d'unicité inter-états

```
Implemente la seconde moitie : verifier qu'aucune ligne existante,
dans aucun des processus obtenus a l'etape 2, ne porte la meme
combinaison beneficiaire, journee, nature et session.

Etends le service de controle des doublons du Sprint 3.2 plutot que
d'en creer un second. Ce service avait ete ecrit pour accueillir cette
extension : verifie que c'est bien le cas, et si ce n'est pas
possible, explique-moi pourquoi.

Le controle porte sur la combinaison complete, comme RG-04. Un meme
beneficiaire peut legitimement etre rattrape pour une journee et une
nature qui n'avaient pas ete saisies.

En cas de refus, le message doit citer le beneficiaire, la journee,
la nature et la session, et indiquer que la prestation figure deja
dans un autre etat de la periode. Un message generique laisserait
l'agent sans moyen de comprendre.

Montre le service.
```

### Étape 4. Branchement sur la saisie

```
Branche le controle sur la creation de ligne :

- Sur un etat NORMAL : RG-04 seule s'applique, comme avant. Le
  comportement ne doit pas changer.
- Sur un etat COMPLEMENTAIRE : RG-04 puis RG-15.

Verifie que la performance reste acceptable. RG-15 ajoute un appel
inter-services a chaque ligne saisie : mesure le temps de reponse et
compare-le a la cible de 3 secondes du document maitre.

Si la marge est faible, propose une optimisation, en me montrant ce
qu'elle change avant de l'appliquer.

Montre les modifications.
```

### Étape 5. Tests

```
Ecris les tests de ce sous-sprint.

Unicite inter-etats :
1. beneficiaire non paye sur la journee visee : ligne acceptee
2. combinaison exacte deja presente dans l'etat d'origine : refusee
3. meme beneficiaire, meme journee, nature differente : acceptee
4. meme beneficiaire, meme journee, session differente : acceptee
5. meme beneficiaire, journee differente : acceptee
6. combinaison presente dans un AUTRE etat complementaire de la meme
   periode : refusee
7. combinaison presente dans un etat d'une AUTRE unite : acceptee
8. combinaison presente dans un etat d'une AUTRE periode : acceptee
9. etats au statut RETOURNE ou EN_COURS_SAISIE : comportement
   conforme a la decision de l'etape 2

Les tests 3, 4 et 5 verifient que RG-15 ne bloque pas les
regularisations legitimes, qui sont la raison d'etre du sprint. Le
test 6 verifie que le controle couvre bien tous les etats, pas
seulement l'origine.

Non-regression :
10. saisie sur un etat NORMAL : comportement inchange, RG-04 seule
11. performance : temps de reponse sous la cible

Bout en bout :
12. etat d'origine cloture avec un beneficiaire paye le 10 juillet,
    ouverture d'un complementaire, ressaisie du 10 juillet refusee,
    saisie du 15 juillet acceptee, soumission, validation, cloture,
    transmission

Le test 12 rejoue le scenario exact du document de scenarios, CT-34
a CT-37.

Montre les fichiers de test.
```

### Étape 6. Seconde transmission comptable

```
Un etat complementaire cloture declenche une transmission sur une
periode deja transmise.

Applique la position de la comptabilite obtenue avant le demarrage du
sprint 6bis. Verifie que RG-13, l'unicite de transmission du Sprint
5.3, ne bloque pas cette seconde transmission : elle porte sur un
processus different, donc elle est legitime.

Ecris le test qui le prouve : deux transmissions distinctes sur la
meme periode, une par processus, chacune unique pour son propre
processus.

Montre le test.
```

### Étape 7. Clôture du Sprint 6bis

```
Lance la cartographie et verifie :
- que le controle RG-15 passe par l'api du service Workflow, jamais
  par sa base
- qu'aucun second service de controle de doublon n'a ete cree en
  parallele du premier
- qu'aucune entite Reclamation ni liste de beneficiaires attendus
  n'existe

Puis mets a jour CLAUDE.md avec les decisions du Sprint 6bis :
1. Les controles d'ouverture d'un etat complementaire (6bis.1).
2. La lecture de l'aiguillage sur le montant du complementaire seul
   (6bis.1).
3. L'inclusion ou non des etats non clotures dans le controle RG-15
   (6bis.2).
4. L'optimisation eventuelle du controle et son incidence (6bis.2).
5. La position de la comptabilite sur une seconde transmission.

Retire aussi de CLAUDE.md la mention de l'etat complementaire comme
point en attente : il est desormais confirme et implemente.
```

## 7. Fichiers à créer ou modifier

| Chemin | Nature |
|---|---|
| `service-workflow/.../api/ProcessusController.java` | Endpoint de liste par unité et période |
| `service-saisie/.../application/ProcessusPeriodeClient.java` | Interface |
| `service-saisie/.../infrastructure/ProcessusPeriodeHttpClient.java` | Implémentation |
| `service-saisie/.../application/ControleDoublonService.java` | Extension à RG-15 |
| `service-saisie/.../application/CreationLigneService.java` | Branchement |
| `service-saisie/src/test/...` | Tests |
| `CLAUDE.md` | Décisions du Sprint 6bis |

## 8. Commandes terminal

```bash
cd afb-rations/backend

mvn -pl service-saisie test
mvn -pl service-workflow spring-boot:run
mvn -pl service-saisie spring-boot:run
```

Scénario manuel, sur un état complémentaire ouvert au sous-sprint précédent :

```bash
curl -X POST -H "Authorization: Bearer <jeton_agent>" \
  -H "Content-Type: application/json" \
  -d '{"idFicheJournaliere":45,"beneficiaire":{"nom":"MBARGA","prenom":"Jean","numCompteCourant":"00002000123456","codeAgence":"00002"},"nature":"RATION","session":"JOUR"}' \
  http://localhost:8082/saisie/lignes
```

Attendu : refus si cette combinaison figure déjà dans l'état d'origine pour la même journée.

Contrôle croisé en base, sur les deux bases :

```sql
\c rations_workflow
SELECT id, type_processus, statut FROM processus_mensuel
WHERE code_unite = '00002' AND mois_paiement = 7 AND annee_paiement = 2026;
```

```sql
\c rations_saisie
SELECT f.id_processus, f.date_jour, l.id_beneficiaire, l.nature, l.session
FROM fiche_journaliere f JOIN ligne_prestation l ON l.id_fiche_journaliere = f.id
WHERE f.id_processus IN (12, 47)
ORDER BY f.date_jour;
```

Attendu : aucune combinaison bénéficiaire, journée, nature et session présente deux fois.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn -pl service-saisie test` | BUILD SUCCESS, aucune régression |
| Douze tests du sous-sprint | Tous passants |
| Combinaison déjà présente dans l'origine | Refusée |
| Combinaison présente dans un autre complémentaire | Refusée |
| Nature ou session différente | Acceptée |
| Journée différente | Acceptée |
| Autre unité ou autre période | Acceptée |
| Saisie sur état normal | Comportement inchangé |
| Temps de réponse | Sous la cible de trois secondes |
| Seconde transmission sur la période | Autorisée, une par processus |
| Contrôle passant par l'API du Workflow | Vérifié |
| Scénario complet CT-34 à CT-37 | Rejoué avec succès |

## 10. Points de vigilance

- **RG-15 est le seul rempart contre le double paiement en régularisation.** Sans lui, l'état complémentaire du sous-sprint précédent devient dangereux. C'est pourquoi la fonctionnalité ne devait pas être ouverte aux utilisateurs entre les deux sous-sprints.
- Le contrôle doit couvrir **tous** les états de la période, pas seulement l'état d'origine. Un second état complémentaire pourrait sinon repayer ce qu'un premier a déjà servi. Le test 6 le vérifie.
- RG-15 ne doit pas bloquer les régularisations légitimes. Les tests 3, 4 et 5 sont aussi importants que le test 2 : un contrôle trop large rendrait la fonctionnalité inutilisable.
- Étendre le service de contrôle existant, ne pas en créer un second. Deux services de contrôle en parallèle divergeraient à la première évolution.
- Le contrôle ajoute un appel inter-services à chaque ligne saisie. La marge sur la cible de trois secondes doit être mesurée, pas supposée.
- La seconde transmission sur une période déjà transmise est légitime : elle porte sur un processus différent. RG-13 ne doit pas la bloquer, et le test de l'étape 6 le prouve.
- Le comportement des états non clôturés dans le contrôle n'est pas évident. Les exclure ouvre la porte à deux saisies concurrentes de la même prestation.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Séquence du contrôle décrite avant codage | Fait |
| Inclusion des états non clôturés arbitrée | Fait |
| Contrôle couvrant tous les états de la période | Vérifié |
| Régularisations légitimes non bloquées | Vérifié |
| Service de contrôle étendu, non dupliqué | Vérifié |
| Message de refus explicite et circonstancié | Vérifié |
| Comportement inchangé sur les états normaux | Vérifié |
| Performance mesurée et sous la cible | Vérifié |
| Seconde transmission autorisée par processus | Vérifié |
| Contrôle passant par l'API, jamais par la base | Vérifié |
| Scénario CT-34 à CT-37 rejoué de bout en bout | Vérifié |
| CLAUDE.md mis à jour, point en attente retiré | Fait |

## 12. Commit

```bash
git add .
git commit -m "sprint-6bis.2: unicite inter-etats sur la periode

- Controle rg-15 couvrant tous les etats de l'unite et de la periode
- Extension du controle de doublon existant, sans duplication
- Regularisations legitimes preservees, double paiement empeche
- Cloture du sprint 6bis

Refs: RG-15, US-18, CT-35, CT-36"
```

---

**Fin du Sprint 6bis.2 et du Sprint 6bis.** Le backend est fonctionnellement complet. En attente de validation avant le Sprint 7F, frontend.
