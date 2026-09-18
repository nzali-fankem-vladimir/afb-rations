# Repli du drapeau de fonctionnalité quand sa lecture échoue

**Date :** 18 septembre 2026
**Sprint :** 7F.7, étape 5
**Portée :** frontend, tout drapeau de fonctionnalité présent ou à venir

---

## La question

`GET /parametres/fonctionnalites` est lu une fois au chargement de
l'application et commande l'affichage de l'entrée de menu « Régularisation »
ainsi que l'accès à sa route (`docs/dispositifs_provisoires.md` section 1.4).

Ce document décrit le cas nominal, jamais le cas de panne. Que doit afficher
l'interface quand cette lecture échoue, le service Workflow étant momentanément
muet ? Il faut bien afficher quelque chose, et les deux réponses possibles ne
produisent pas la même erreur.

## Ce qui a été décidé

**Repli sur « ouvert ».** Quand la lecture échoue, l'interface se comporte comme
si `rattrapageActif` valait `true` : le menu reste visible, la route reste
accessible.

Arbitrage rendu par l'utilisateur le 18 septembre 2026, sur recommandation.

## Pourquoi, et pourquoi ce n'est pas la doctrine du refus conservateur à l'envers

Le refus conservateur (Sprints 1.3, 2.4, 4.3) s'applique aux contrôles qui
**autorisent** : habilitation, seuil d'aiguillage, résolution de montant. Devant
l'incertitude, ces contrôles refusent, parce qu'autoriser à tort engagerait la
banque.

Ce drapeau n'autorise rien. Le contrôle qui protège contre une régularisation
indue est backend, en tête de `OuvertureComplementaireService`, et il s'applique
quel que soit l'affichage : un frontend modifié, un appel direct à
`POST /processus`, ou une interface qui aurait mal lu son drapeau se heurtent au
même `422 FONCTIONNALITE_NON_OUVERTE`. Le masquage n'est, comme le dit le
dispositif lui-même, qu'un confort d'usage.

Restait donc à choisir laquelle des deux erreurs on préfère quand on ne sait pas :

| Repli | Si la réalité était l'inverse | Comment l'erreur se manifeste |
| --- | --- | --- |
| **Ouvert** (retenu) | la régularisation était fermée | l'agent clique et reçoit un refus explicite, avec un message qui nomme la cause |
| Fermé | la régularisation était ouverte | l'entrée de menu disparaît, **sans aucun message** |

La première erreur se voit et s'explique ; la seconde est silencieuse. Le guide
du Sprint 7F.7 décrit précisément ce défaut à propos d'un drapeau oublié fermé :
« coupe la régularisation en silence : aucune erreur, seulement un menu absent ».
Replier sur « fermé » reproduirait ce défaut à chaque micro-panne réseau.

C'est la même préférence que celle retenue au Sprint 5.3 pour l'ordre du verrou
de transmission : entre deux risques, on garde celui dont on verra qu'il s'est
réalisé.

## Ce que le code porte

`FonctionnalitesProvider` expose trois champs plutôt qu'un booléen nu :

- `chargement` : la lecture n'a ni abouti ni échoué. **Ni affichage ni
  redirection** pendant ce temps. La sidebar masque le lien conditionnel, et
  `ProtectedRoute` rend l'écran d'attente au lieu de rediriger — rediriger
  pendant la lecture renverrait un agent légitime hors d'une fonctionnalité
  ouverte, sur une simple question de temps de réponse.
- `rattrapageActif` : la valeur lue, ou le repli.
- `lectureEchouee` : la valeur ci-dessus est un repli, pas une lecture. Aucun
  écran ne s'en sert aujourd'hui ; le champ existe pour qu'un avertissement
  puisse être ajouté sans changer la forme du contexte.

La constante `REPLI_SI_LECTURE_IMPOSSIBLE` porte la décision en un seul endroit,
avec sa justification.

## Portée pour les sprints suivants

Cette décision ne vaut pas seulement pour `rattrapageActif` : elle fixe la règle
pour **tout drapeau de fonctionnalité** ajouté ensuite au module. Le critère est
énonçable en une phrase, et c'est lui qu'il faudra réappliquer :

> Un drapeau qui ne fait que masquer se replie sur « ouvert » ; un contrôle qui
> autorise se replie sur « refusé ».

Un futur drapeau qui, lui, commanderait une autorisation réelle — sans contrôle
backend équivalent derrière — ne relèverait pas de cette décision et devrait se
replier sur « fermé ». Le distinguer est le travail à faire avant d'en ajouter un.
