# Portée d'accès du Directeur Réseau (DR)

**Date :** 26 août 2026
**Sprint :** 1.1, domaine Utilisateur et portée d'accès
**Statut :** tranchée

## Question

Le document de conception (section 10) précise que la portée d'accès dépend
du rôle : l'agent d'unité et le chef d'unité (DA) voient leur unité, l'ARH,
la DRH et l'administrateur voient toutes les unités, et le Directeur Réseau
(DR) voit « son réseau ». Aucune spécification ne définit ce qu'est un
réseau, ni comment une unité s'y rattache : la notion n'existe dans aucun
document du projet.

## Options examinées

| Option | Coût | Risque |
|---|---|---|
| Champ `code_reseau` sur `utilisateurs` | Migration + saisie manuelle par DR | Notion de données inventée, jamais validée par le métier |
| Table de rattachement unité → réseau | Nouvelle table, migration, référentiel à maintenir | Découpage réel des réseaux inconnu : table à refaire une fois le métier tranché |
| Portée nationale par défaut | Aucun schéma nouveau | Le DR voit temporairement plus d'unités que prévu par le métier |

## Décision

**Portée nationale par défaut pour `DIRECTEUR_RESEAU_DR`**, au même titre que
`ARH`, `DRH` et `ADMIN`, tant que le métier n'a pas défini le découpage en
réseaux.

## Motifs

- Aucune donnée de rattachement réseau n'existe nulle part dans le projet :
  l'inventer maintenant reviendrait à coder une règle de gestion non
  spécifiée, contraire à la consigne de ne jamais supposer un choix non
  couvert par CLAUDE.md.
- `PorteeAccesService` expose une méthode stable (accès à un code unité,
  liste des codes unité accessibles) consommée par les Sprints 3, 4 et 6.
  Faire évoluer l'implémentation interne du cas DR n'impose aucun changement
  de signature côté appelants.
- Réversible à coût faible : un seul point de code change le jour où le
  métier fournit le découpage réseau (champ ou table de rattachement).

## Conséquences

- Provisoirement, un DR a la même portée fonctionnelle qu'un ARH/DRH/ADMIN :
  toutes les unités. À signaler comme sur-largeur temporaire tant que la
  question métier n'est pas tranchée (section 16 de CLAUDE.md).
- Aucune migration, aucun champ nouveau sur `utilisateurs`.
- Quand le métier précise le découpage, seule la méthode interne de
  `PorteeAccesService` traitant `DIRECTEUR_RESEAU_DR` est à reprendre.
