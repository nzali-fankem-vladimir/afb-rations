# Publication des images sur le registre Harbor

**Sprint 8.2.** Procédure **rédigée, jamais exécutée** : les accès au registre ne sont pas confirmés. Elle est écrite en document plutôt qu'en script tant qu'ils ne le sont pas, pour qu'un script ne soit pas lancé contre une adresse inventée.

## 1. Valeurs de substitution : à remplacer, à ne pas deviner

Aucune de ces valeurs n'est connue. Elles suivent la convention `A_CONFIRMER_DSI_*` du registre des points en attente. **Ne jamais les remplacer par une valeur plausible** : une adresse inventée publierait, au mieux, dans le vide, au pire, sur un registre qui n'est pas celui de la banque.

| Marqueur | Signification | Point en attente |
|---|---|---|
| `A_CONFIRMER_DSI_REGISTRE` | Adresse (nom d'hôte) du registre Harbor de la banque | D-02 |
| `A_CONFIRMER_DSI_PROJET_HARBOR` | Nom du projet Harbor qui reçoit les images du module | nouveau, voir §7 |
| `A_CONFIRMER_DSI_COMPTE_ROBOT` | Compte robot autorisé à publier dans ce projet | nouveau, voir §7 |

Le secret du compte robot **n'est écrit nulle part** : ni dans ce document, ni dans un fichier du dépôt, ni dans un historique de commandes (voir §4).

## 2. Nommage des images

```
A_CONFIRMER_DSI_REGISTRE/A_CONFIRMER_DSI_PROJET_HARBOR/rations-<module>:<étiquette>
```

`<module>` est le nom de l'image locale, déjà celui de la composition :

| Module | Nom d'image | Port |
|---|---|---|
| Registre | `rations-registry` | 8761 |
| Passerelle | `rations-gateway` | 8080 |
| Identité | `rations-identite` | 8081 |
| Saisie | `rations-saisie` | 8082 |
| Grilles | `rations-grilles` | 8083 |
| Workflow | `rations-workflow` | 8084 |
| Reporting | `rations-reporting` | 8085 |
| Transmission | `rations-transmission` | 8086 |
| Audit | `rations-audit` | 8087 |
| Frontend | `rations-frontend` | 8080 (conteneur), 5173 (poste) |

Dix images. `rations-audit-commun` **n'a aucune image** : c'est une bibliothèque, compilée dans les six services qui en dépendent.

## 3. Étiquetage des versions

| Étiquette | Exemple | Usage |
|---|---|---|
| `X.Y.Z` | `1.0.0` | Version livrée. **Immuable** : une fois publiée, jamais republiée. |
| `X.Y.Z-rcN` | `1.0.0-rc1` | Candidate à la recette. |
| `sha-<7 caractères du commit>` | `sha-59bacf2` | Une par construction, pour remonter de l'image au code. |

Règles :

- **Une seule construction par version, puis promotion.** L'image testée en recette est celle, octet pour octet, qui part en production : on lui ajoute une étiquette, on ne la reconstruit pas. C'est ce que permet le frontend sans URL figée (Sprint 8.2, option A).
- **Les dix images d'une livraison portent la même étiquette de version.** Une livraison mélangeant `1.0.0` et `1.0.1` selon le module est ingérable.
- **`latest` n'est jamais publié.** Il désigne « la dernière construction locale » et ne dit rien de ce qui tourne. Il reste réservé au poste du développeur.
- **Activer l'immuabilité des étiquettes dans Harbor** pour le projet (règle de projet), afin qu'une étiquette de version ne puisse pas être écrasée par erreur. À demander avec le point D-02.

## 4. Commandes

Remplacer les marqueurs par les valeurs confirmées. Les commandes qui suivent sont un modèle, **non testé contre un registre réel**.

### Avant toute publication : les deux contrôles du Sprint 8.2, sur l'image exacte

```bash
docker run --rm --entrypoint sh rations-identite:latest -c 'id -u'
# Attendu : 10001 (jamais 0). Les images du frontend rendent 101.

docker history rations-identite:latest --no-trunc | grep -i "password\|secret"
# Attendu : aucun résultat.
```

À faire pour les dix images. Un historique d'image publié est consultable par tous ceux qui ont accès au projet, et **conserve les couches intermédiaires** : un secret inscrit puis supprimé reste visible.

### Connexion

```bash
# Le secret arrive par l'environnement et par l'entrée standard : il n'apparaît ni dans
# la ligne de commande, ni dans l'historique du shell, ni dans la liste des processus.
export HARBOR_ROBOT_SECRET='<à lire dans le coffre de secrets, jamais à recopier ici>'
echo "$HARBOR_ROBOT_SECRET" | docker login A_CONFIRMER_DSI_REGISTRE \
    --username 'A_CONFIRMER_DSI_COMPTE_ROBOT' --password-stdin
unset HARBOR_ROBOT_SECRET
```

### Marquage et publication

```bash
REGISTRE="A_CONFIRMER_DSI_REGISTRE/A_CONFIRMER_DSI_PROJET_HARBOR"
VERSION="1.0.0"                       # étiquette de version, voir §3
COMMIT="$(git rev-parse --short=7 HEAD)"

for module in registry gateway identite saisie grilles workflow reporting transmission audit frontend; do
    docker tag "rations-${module}:latest" "${REGISTRE}/rations-${module}:${VERSION}"
    docker tag "rations-${module}:latest" "${REGISTRE}/rations-${module}:sha-${COMMIT}"
    docker push "${REGISTRE}/rations-${module}:${VERSION}"
    docker push "${REGISTRE}/rations-${module}:sha-${COMMIT}"
done
```

### Vérification après publication

```bash
# L'empreinte (digest) publiée doit être celle de l'image locale.
docker inspect --format '{{index .RepoDigests 0}}' "${REGISTRE}/rations-identite:${VERSION}"
```

Consigner ces dix empreintes dans la note de livraison : c'est elles, et non les étiquettes, que Kubernetes doit référencer en production.

## 5. Ce qui n'a pas été fait, et pourquoi

- **Aucun script.** Un script lancé par habitude contre une adresse non confirmée est exactement le risque que ce document évite. Il se rédigera quand D-02 sera résolu.
- **Aucune analyse de vulnérabilités.** Harbor sait scanner les images à la publication ; la politique (blocage au-delà de quelle gravité) est à arrêter avec la DSI.
- **Aucun pipeline.** Publication manuelle jusqu'à la résolution de D-09.

## 6. Points d'attention propres à ce module

- **Profil `dev` dans les images.** Chaque service ne porte sa configuration de base et de Keycloak que dans `application-dev.yml`, et **aucun profil de production n'existe**. Une image publiée telle quelle démarre en `dev`, donc avec les comptes de test de la migration `V1000`. **À traiter avant la première publication vers un environnement autre que le poste du développeur** (Sprint 8.3).
- **Les images ne contiennent aucune adresse d'environnement**, ni mot de passe : tout arrive au démarrage par variable d'environnement (`infra/docker/.env.example` recense les variables). Le frontend refuse de démarrer si les quatre adresses lui manquent.
- **Les documents signés** (`/donnees/pieces-jointes`, service Workflow) sont des **données**, pas de l'image : volume persistant et sauvegardé, à arrêter avec la DSI au même titre que les Secrets Kubernetes.

## 7. À demander à la DSI

| Demande | Pourquoi |
|---|---|
| Adresse du registre (D-02) | Sans elle, aucune publication |
| Nom du projet Harbor dédié au module | Détermine le chemin de toutes les images |
| Compte robot limité à la publication dans ce projet, et son mode de remise | Ne jamais publier avec un compte nominatif |
| Immuabilité des étiquettes de version, politique d'analyse de vulnérabilités, durée de rétention | Réglages du projet |
| Pipeline registre vers cluster (D-09) | Sinon, déploiement manuel |
