# Migrations de développement — service Identité

`V1000__profils_de_test_developpement.sql` pré-provisionne les profils locaux
correspondant aux comptes du realm `afb-rations-dev`.

---

## ⚠️ Ne jamais modifier `V1000`, pas même un commentaire

Flyway valide l'**empreinte** de chaque migration déjà appliquée. Modifier le
fichier — fût-ce pour y ajouter un commentaire — fait échouer le démarrage du
service sur tout environnement où la migration a déjà tourné :

```
Validate failed: Migrations have failed validation
Migration checksum mismatch for migration version 1000
  Applied to database : -956316021
  Resolved locally    : 435038405
```

C'est arrivé au Sprint 6.3 : un bloc d'avertissement ajouté en tête du fichier a
empêché `service-identite` de démarrer, alors que `mvn clean test` restait vert —
les tests ne montent pas le contexte contre la vraie base. Le défaut ne s'est vu
qu'en **démarrant** le service, comme le bean `ObjectMapper` absent du Sprint 5.1
et `@EnableKafka` manquant du 5.2.

C'est pourquoi cet avertissement vit **ici**, dans un fichier qu'aucune empreinte
ne scelle, et non dans la migration elle-même.

Pour changer le pré-provisionnement : **ajouter une nouvelle migration**, jamais
retoucher `V1000`.

---

## ⚠️ `thomas_ndzana` ne doit jamais recevoir de profil local

Ni dans une migration, ni par un `INSERT`, ni par un futur endpoint
d'administration des profils.

C'est le **compte de contrôle du refus 403** : présent à l'annuaire,
volontairement sans profil dans le module, il est le seul cas prouvant qu'un
jeton Keycloak parfaitement valide — bon realm, bonne audience, rôle applicatif
reconnu — est malgré tout refusé faute d'habilitation ouverte (invariant du
Sprint 0.4).

Lui ouvrir un profil ne casse rien de visible. Le build passe, les tests passent,
l'application fonctionne — et la vérification d'environnement rend **`200` là où
elle attend `403`**, sur un test de sécurité, sans un mot d'explication.

C'est exactement ce qui est arrivé à `pierre_belinga`, précédent compte de
contrôle, pendant la vérification du Sprint 6.3.

### En revanche, s'y connecter est sans danger

Se connecter avec ce compte **ne crée aucun profil** : sans profil local,
`UtilisateurCourantService.resoudre` lève `UtilisateurNonHabiliteException` et
n'écrit rien. Le module ne crée jamais de profil automatiquement — c'est
précisément l'invariant du Sprint 0.4 :

> L'habilitation au module reste un acte d'administration explicite, elle ne
> découle pas de la seule existence d'un compte à l'annuaire.

Vérifié par `UtilisateurCourantServiceTest.aucunProfilOuvertRefuse`, qui exige
`verify(utilisateurRepository, never()).save(any())`.

**Le geste destructeur est l'ouverture du profil, pas la connexion.** Une
connexion de vérification est donc légitime et attendue : c'est même le test.

### Besoin d'un profil pré-provisionné non lié ?

Pour éprouver la liaison automatique au premier accès
(`LIAISON_COMPTE_KEYCLOAK`), créer un compte jetable — jamais le compte de
contrôle. C'est le besoin qui a causé la collision du 6.3.

---

Garde au build : `CompteDeControleDuRefusTest`.
Décision : `docs/decisions/2026-09-04-compte-de-controle-du-refus-403.md`.
