# Un critère de validation portant sur l'audit exige une requête en base

**Sprint 6.3 — 4 septembre 2026** · Décision tranchée avec l'utilisateur, à
l'issue de l'inspection factuelle de l'étape 2.

**Statut :** appliquée à partir de ce sprint, et rétroactive dans son
enseignement : elle explique comment sept sprints ont pu valider des critères
d'audit sur un journal qui n'a jamais rien enregistré.

---

## 1. Le constat qui la motive

L'inspection de couverture du Sprint 6.3 a établi trois faits, chacun vérifié
par une commande et non par lecture de code :

| Fait | Preuve |
| --- | --- |
| Le topic `rations.audit.evenement` contient **176 messages réels** | `kafka-get-offsets.sh` → `rations.audit.evenement:0:176` |
| Le seul groupe de consommateurs du broker est `rations-transmission-accuse` | `kafka-consumer-groups.sh --list` |
| `audit_log` contient **0 ligne**, depuis sa création le 26 août 2026 | `SELECT COUNT(*) FROM audit_log` → `0` |

Ce ne sont pas des messages de test. Le premier message du topic est une
attribution de rôle réelle :

```json
{"idUtilisateur":6,"serviceEmetteur":"service-identite","action":"ATTRIBUTION_ROLE",
 "entiteCible":"utilisateurs","idEntite":2,"dateAction":"2026-08-27T12:24:35.6309673",
 "adresseIp":"0:0:0:0:0:0:0:1",
 "detailJson":"{\"role\":{\"avant\":\"CHEF_UNITE_DA\",\"apres\":\"DIRECTEUR_RESEAU_DR\"},…}"}
```

Le service Audit n'a jamais été construit : `backend/service-audit/src` ne
contient ni `@KafkaListener`, ni `@Entity`, ni `JpaRepository`, ni
`@RestController`, et son `pom.xml` ne déclare même pas `spring-kafka`.

## 2. La vraie question : comment sept sprints ont-ils validé leurs critères d'audit ?

C'est le point le plus important de ce constat, et il ne porte pas sur un
service manquant — il porte sur **la méthode de vérification elle-même**.

Les Sprints 1.3 à 6.2 comportaient tous des critères de validation mentionnant
la publication d'événements d'audit, et tous ont été cochés. Aucun ne pouvait
l'être honnêtement, puisque rien n'était écrit nulle part. La seule explication
tient dans ce qui était réellement observé pour les cocher :

- aucune exception levée pendant l'opération métier ;
- aucun `WARN` au préfixe `AUDIT PERDU` dans le journal applicatif ;
- un test unitaire vérifiant que `PublicateurAudit.publier(...)` a bien été
  appelé, sur un double de test.

Ces trois observations sont vraies, et **restent vraies quand le journal
n'enregistre rien**. Pire : elles restaient vraies quand le schéma refusait la
trace, puisque le refus n'aurait eu lieu que côté consommateur, dans un service
qui n'existe pas.

Le producteur est conçu pour ne jamais faire échouer le métier — cinq couches y
veillent (`PublicateurAuditKafka`). C'est une qualité, et c'est précisément ce
qui rend « aucune erreur » inutilisable comme preuve : **un dispositif dont la
panne est silencieuse par construction ne peut pas être validé par l'absence de
bruit.**

## 3. La règle

À partir du Sprint 6.3, un critère de validation qui mentionne l'audit n'est
coché que sur la foi d'une **lecture de `rations_audit`**.

Concrètement, la preuve attendue est l'une de celles-ci :

```bash
# La trace attendue existe, avec son service émetteur
docker exec rations-postgres psql -U rations_audit -d rations_audit \
  -c "SELECT action, service_emetteur, id_utilisateur, id_entite, date_action
        FROM audit_log
       WHERE entite_cible = 'processus_mensuel' AND id_entite = 1
       ORDER BY date_action;"
```

```bash
# Ou, par l'endpoint de consultation une fois le service Audit livré
curl -H "Authorization: Bearer <jeton>" http://localhost:8087/audit/processus/1
```

Ne sont **plus** acceptés comme preuve, seuls :

- l'absence d'exception pendant l'opération métier ;
- l'absence de `WARN AUDIT PERDU` dans le journal ;
- un test à double vérifiant l'appel à `publier(...)`.

Ces trois éléments restent utiles — ils prouvent que le producteur fonctionne.
Ils ne prouvent rien sur le journal.

## 4. Portée immédiate

Tant que le service Audit n'est pas livré (sprint bloquant, voir
[`2026-09-04-service-audit-sprint-bloquant.md`](2026-09-04-service-audit-sprint-bloquant.md)),
**aucun critère d'audit ne peut être coché**, et le Sprint 6.3 le consigne
explicitement au lieu de le contourner. La preuve intermédiaire disponible est
la lecture du topic :

```bash
docker exec rations-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic rations.audit.evenement \
  --from-beginning --max-messages 20 --timeout-ms 15000
```

Elle prouve que l'événement est **publié**, ce qui est la moitié de la chaîne.
Elle ne prouve pas qu'il est **conservé**, et la distinction est exactement
celle que ce sprint a mise en évidence.

## 5. Ce que cette règle généralise

Le principe dépasse l'audit : **une propriété n'est vérifiée que par
l'observation de son effet, jamais par l'absence de son symptôme d'échec.** Le
module l'applique déjà ailleurs — le Sprint 5.2 a découvert `@EnableKafka`
manquant en interrogeant le `KafkaListenerEndpointRegistry` plutôt qu'en
constatant que les beans existaient ; le Sprint 5.1 a découvert l'absence de
bean `ObjectMapper` en assemblant le contexte plutôt qu'en exécutant des tests
unitaires. Trois fois le même enseignement, sous trois formes.
