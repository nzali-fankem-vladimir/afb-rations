# Dispositifs provisoires pour les points en attente

Module Paiement des Rations et du Transport de la Garde Armée

Deux points restent en attente au moment où le développement se déroule : la confirmation métier de l'état complémentaire, et les réponses DSI sur l'infrastructure. Ce document décrit les dispositifs provisoires retenus.

Le principe est le même dans les deux cas : **centraliser en un seul endroit, rendre le provisoire visible, et faire du remplacement une opération d'une ligne.** Un point en attente dispersé dans quinze fichiers finit par être oublié dans deux d'entre eux.

---

## 1. État complémentaire : drapeau de fonctionnalité

### 1.1 Le problème

Le Sprint 6bis produit du code fonctionnel, mais le besoin n'est pas confirmé par le métier. Trois attitudes sont possibles, deux sont mauvaises.

Ne pas développer laisse le module incomplet si le besoin se confirme, et il faudra tout reprendre après coup. Développer et exposer met à disposition une fonctionnalité que personne n'a validée, avec un risque réel puisqu'elle touche au paiement. La troisième voie consiste à développer, livrer, mais **garder la fonctionnalité fermée** jusqu'à confirmation.

### 1.2 Le dispositif

Un paramètre dans `parametre_systeme`, table déjà en place depuis le Sprint 0.5 et déjà utilisée pour le seuil d'aiguillage. Aucune infrastructure nouvelle n'est nécessaire.

**Migration à ajouter au service Workflow, en V4 :**

```sql
INSERT INTO parametre_systeme (code, libelle, valeur, actif, date_modification)
VALUES
  ('RATTRAPAGE_ACTIF',
   'Ouverture d''un etat complementaire sur une periode close',
   'false',
   true,
   CURRENT_TIMESTAMP),
  ('DELAI_REGULARISATION_JOURS',
   'Delai en jours pendant lequel une periode close reste regularisable (VALEUR PROVISOIRE)',
   '90',
   true,
   CURRENT_TIMESTAMP);
```

### 1.3 Comportement côté backend

Le service d'ouverture d'un état complémentaire, construit au Sprint 6bis.1, lit `RATTRAPAGE_ACTIF` avant tout autre contrôle.

Quand le drapeau vaut `false`, la demande est refusée avec un code dédié, distinct d'une erreur de droits ou d'une anomalie technique :

```json
{
  "timestamp": "2026-08-20T09:12:00Z",
  "status": 422,
  "code": "FONCTIONNALITE_NON_OUVERTE",
  "message": "L'ouverture d'un etat complementaire n'est pas encore ouverte. Rapprochez-vous de la DRH.",
  "path": "/api/processus"
}
```

Le code distinct compte : un `403` laisserait croire à un problème d'habilitation, et l'agent perdrait du temps à chercher qui peut lui donner le droit.

Le paramètre `DELAI_REGULARISATION_JOURS` est lu par le même service, dans le contrôle d'ouverture : une période clôturée depuis plus longtemps que ce délai n'est plus régularisable. Sa valeur de 90 jours est **explicitement marquée provisoire dans son libellé**, ce qui la rend repérable par une simple requête.

### 1.4 Comportement côté frontend

Un endpoint expose les fonctionnalités actives, appelé au chargement de l'application :

```
GET /api/parametres/fonctionnalites
```

```json
{
  "rattrapageActif": false
}
```

Le frontend masque alors l'entrée de menu et l'écran d'ouverture d'état complémentaire du Sprint 7F.7. L'utilisateur ne voit pas une fonction qu'il ne peut pas utiliser.

Le masquage ne suffit pas : le contrôle backend reste la sécurité, le masquage n'est qu'un confort d'usage. C'est le même principe qu'au Sprint 7F.3 pour le filtrage de la sidebar.

### 1.5 Ce que le dispositif permet le jour de la confirmation

Si le métier confirme le besoin, l'ouverture se fait par une mise à jour :

```sql
UPDATE parametre_systeme SET valeur = 'true', date_modification = CURRENT_TIMESTAMP
WHERE code = 'RATTRAPAGE_ACTIF';
```

Aucun redéploiement, aucune reprise de code. Et si un problème apparaît, la fermeture est tout aussi immédiate.

Si le métier confirme le délai de régularisation, la même opération remplace la valeur provisoire, et le libellé est corrigé pour retirer la mention :

```sql
UPDATE parametre_systeme
SET valeur = '<valeur confirmee>',
    libelle = 'Delai en jours pendant lequel une periode close reste regularisable',
    date_modification = CURRENT_TIMESTAMP
WHERE code = 'DELAI_REGULARISATION_JOURS';
```

Si le métier écarte le besoin, le drapeau reste à `false` et le code reste dormant, sans nuire.

### 1.6 Requête de contrôle

À exécuter avant toute mise en production, pour savoir ce qui est encore provisoire :

```sql
SELECT code, valeur, libelle
FROM parametre_systeme
WHERE libelle LIKE '%PROVISOIRE%' OR valeur = 'A_CONFIRMER';
```

---

## 2. Points DSI : centralisation et marquage

### 2.1 Le problème

Le Sprint 8.3 produit neuf manifests de déploiement. Sans dispositif, les valeurs en attente se retrouveraient dispersées : un namespace ici, une adresse de registre là, une URL de realm ailleurs. Le jour où la DSI répond, il faudrait toutes les retrouver, et il en resterait forcément une.

Le second risque est plus insidieux. Une valeur plausible mais inventée, comme `afb-rations-prod`, ne se distingue pas d'une valeur confirmée. Elle se propage, et personne ne sait plus ce qui a été validé.

### 2.2 Convention de marquage

Toute valeur non confirmée porte le préfixe `A_CONFIRMER_DSI_`. Elle est ainsi impossible à confondre avec une valeur réelle, et repérable par une seule recherche.

```yaml
namespace: A_CONFIRMER_DSI_NAMESPACE
image: A_CONFIRMER_DSI_REGISTRE/rations-identite:1.0.0
issuerUri: A_CONFIRMER_DSI_REALM_URL
```

Ne jamais écrire une valeur qui aurait l'air définitive, même en commentant qu'elle est provisoire : le commentaire se perd, la valeur reste.

### 2.3 Fichier de valeurs centralisé

**`infra/k8s/valeurs-environnement.yml`**, source unique de toutes les valeurs variables :

```yaml
# Valeurs d'environnement du module rations et transport garde armee
#
# Toute valeur prefixee A_CONFIRMER_DSI_ est en attente de reponse.
# Voir docs/points-en-attente.md pour le suivi de chaque point.
#
# Le script scripts/verifier-valeurs.sh refuse un deploiement en
# production tant qu'un marqueur subsiste.

infrastructure:
  namespace: A_CONFIRMER_DSI_NAMESPACE
  registre: A_CONFIRMER_DSI_REGISTRE
  prefixeNommage: A_CONFIRMER_DSI_PREFIXE

authentification:
  realmUrl: A_CONFIRMER_DSI_REALM_URL
  clientId: rations-frontend

messagerie:
  brokers: A_CONFIRMER_DSI_KAFKA_BROKERS
  topicEtatValide: A_CONFIRMER_DSI_TOPIC_ETAT
  topicAccuse: A_CONFIRMER_DSI_TOPIC_ACCUSE

exposition:
  urlPasserelle: A_CONFIRMER_DSI_URL_API
  urlFrontend: A_CONFIRMER_DSI_URL_FRONT

secrets:
  methode: A_CONFIRMER_DSI_METHODE_SECRETS
```

Les manifests référencent ces valeurs plutôt que de les répéter. Le jour de la réponse, un seul fichier change.

Les noms de topics figurent dans la liste : ceux utilisés en développement, `rations.etat.valide` et `rations.etat.accuse`, ont été fixés au Sprint 0.5 mais la convention de nommage en environnement partagé n'est pas arrêtée.

### 2.4 Script de garde-fou

**`scripts/verifier-valeurs.sh`**, à exécuter avant tout déploiement :

```bash
#!/usr/bin/env bash
# Verifie qu'aucune valeur en attente ne subsiste avant deploiement.
# Usage : ./verifier-valeurs.sh [dev|recette|production]

ENVIRONNEMENT="${1:-dev}"
FICHIER="infra/k8s/valeurs-environnement.yml"

MARQUEURS=$(grep -c "A_CONFIRMER_DSI_" "$FICHIER" || true)

if [ "$MARQUEURS" -eq 0 ]; then
  echo "Aucune valeur en attente. Deploiement possible sur tout environnement."
  exit 0
fi

echo "$MARQUEURS valeur(s) en attente de confirmation DSI :"
grep -n "A_CONFIRMER_DSI_" "$FICHIER"
echo ""

case "$ENVIRONNEMENT" in
  production)
    echo "REFUS : deploiement en production impossible avec des valeurs non confirmees."
    echo "Voir docs/points-en-attente.md"
    exit 1
    ;;
  recette)
    echo "AVERTISSEMENT : deploiement en recette avec des valeurs de substitution."
    exit 0
    ;;
  *)
    echo "Developpement local : valeurs de substitution acceptees."
    exit 0
    ;;
esac
```

Le script laisse travailler en développement et en recette, et bloque uniquement la production. C'est le seul environnement où une valeur inventée ferait des dégâts.

### 2.5 Intégration au moment de la réponse DSI

Le jour où la DSI répond, la procédure tient en trois étapes.

Remplacer les valeurs concernées dans `valeurs-environnement.yml`, en supprimant le préfixe. Exécuter le script pour vérifier qu'il ne reste aucun marqueur. Mettre à jour le registre des points en attente en passant la ligne à résolu, avec la date.

Aucun manifest n'est touché, puisqu'ils référencent le fichier central.

---

## 3. Registre des points en attente

Un fichier unique, tenu à jour, qui répond à une question simple : où en est-on ?

**`docs/points-en-attente.md`**

| Réf | Objet | Interlocuteur | Demandé le | Valeur provisoire | Incidence si non résolu | État |
|---|---|---|---|---|---|---|
| M-01 | Confirmation du besoin d'état complémentaire | Métier | | `RATTRAPAGE_ACTIF = false` | Fonctionnalité livrée mais fermée | En attente |
| M-02 | Délai de régularisation d'une période close | Métier | | 90 jours, marqué provisoire | Délai arbitraire appliqué | En attente |
| M-03 | Position sur une seconde transmission comptable | DFT | | Autorisée, un envoi par processus | Risque de rejet côté comptabilité | En attente |
| D-01 | Namespace Kubernetes | DSI | | `A_CONFIRMER_DSI_NAMESPACE` | Déploiement production impossible | En attente |
| D-02 | Adresse du registre Harbor | DSI | | `A_CONFIRMER_DSI_REGISTRE` | Publication des images impossible | En attente |
| D-03 | Convention de nommage des déploiements | DSI | | `A_CONFIRMER_DSI_PREFIXE` | Nommage à reprendre après coup | En attente |
| D-04 | Méthode de gestion des secrets | DSI | | `A_CONFIRMER_DSI_METHODE_SECRETS` | Secrets non injectables | En attente |
| D-05 | URL du realm Keycloak de production | DSI | | `A_CONFIRMER_DSI_REALM_URL` | Authentification impossible | En attente |
| D-06 | Adresses des brokers Kafka | DSI | | `A_CONFIRMER_DSI_KAFKA_BROKERS` | Transmission comptable impossible | En attente |
| D-07 | Nommage des topics en environnement partagé | DSI | | Noms de développement conservés | Topics à renommer après coup | En attente |
| D-08 | URL publiques passerelle et frontend | DSI | | `A_CONFIRMER_DSI_URL_API` et `_URL_FRONT` | Exposition impossible | En attente |
| D-09 | Pipeline de livraison Harbor vers cluster | DSI | | Procédure manuelle documentée | Déploiement manuel | En attente |

La colonne « demandé le » sert à relancer : un point en attente depuis trois semaines n'a pas le même statut qu'un point demandé hier.

---

## 4. Ce que ces dispositifs changent concrètement

Sans dispositif, l'ouverture de la fonctionnalité de régularisation supposerait de retrouver le code concerné, de le décommenter ou de le rebrancher, de recompiler et de redéployer. Avec le drapeau, elle tient en une mise à jour de paramètre, réversible.

Sans dispositif, une réponse DSI supposerait de parcourir neuf manifests et d'espérer n'en oublier aucun. Avec le fichier central, un seul fichier change, et le script confirme qu'il n'en reste aucune.

Sans registre, l'état d'avancement des points en attente vit dans la mémoire de celui qui a posé les questions. Avec le registre, il est lisible par n'importe qui, y compris après une interruption du projet.

---

## 5. Incidence sur les guides de sprint

Trois guides déjà produits sont concernés. Les ajustements sont limités.

**Sprint 6bis.1.** La condition de démarrage reste valable, mais elle change de nature : le sprint peut désormais démarrer avant confirmation, puisque la fonctionnalité restera fermée. Ajouter à l'étape 2 la lecture du drapeau `RATTRAPAGE_ACTIF` en premier contrôle, et le contrôle du délai de régularisation.

**Sprint 7F.7.** L'étape 5 n'est plus conditionnelle : les écrans se construisent, mais leur affichage dépend de l'appel aux fonctionnalités actives. Ajouter cet appel au chargement de l'application.

**Sprint 8.3.** Remplacer les valeurs de substitution dispersées par des références au fichier central, et ajouter l'exécution du script de vérification à la procédure de déploiement.

Ces trois ajustements peuvent être intégrés au moment d'exécuter les sprints concernés, sans reprendre les guides dès maintenant.
