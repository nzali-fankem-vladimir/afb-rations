#!/usr/bin/env bash
# Creation des trois topics du contrat d'API (CLAUDE.md section 9).
#
# Noms de developpement uniquement : le nommage definitif en environnement
# partage reste un point en attente DSI (D-07, docs/points-en-attente.md).
# Ne pas figer ces noms ailleurs que dans cette configuration.
#
# Une partition et un facteur de replication de 1 : suffisant pour un broker
# unique en developpement local, sans tolerance de panne recherchee ici.
set -euo pipefail

# Git Bash (Windows) convertit les chemins Unix passes en argument (/opt/...)
# en chemins Windows avant l'exec dans le conteneur. Sans objet ailleurs.
export MSYS_NO_PATHCONV=1

CONTAINER="${KAFKA_CONTAINER:-rations-kafka}"
BROKER="${KAFKA_BROKER:-localhost:9092}"

# Deux modes, un seul jeu de noms de topics (ce fichier reste le SEUL endroit
# ou ils sont ecrits) :
#   - depuis l'hote (defaut) : passe par docker exec dans le conteneur du broker ;
#   - dans un conteneur (KAFKA_LOCAL=1) : la composition Docker (service
#     kafka-init, Sprint 8.2) monte ce fichier et l'execute avec
#     KAFKA_BROKER=kafka:19092. Ainsi un poste neuf a ses topics sans rien faire.
if [ "${KAFKA_LOCAL:-0}" = "1" ]; then
    kafka_topics() { /opt/kafka/bin/kafka-topics.sh "$@"; }
else
    kafka_topics() { docker exec "$CONTAINER" /opt/kafka/bin/kafka-topics.sh "$@"; }
fi

creer_topic() {
    local topic="$1"
    local retention_ms="${2:-}"
    local config_args=()

    if [ -n "$retention_ms" ]; then
        config_args=(--config "retention.ms=$retention_ms")
    fi

    echo "Creation du topic : $topic"
    kafka_topics \
        --bootstrap-server "$BROKER" \
        --create --if-not-exists \
        --topic "$topic" \
        --partitions 1 \
        --replication-factor 1 \
        "${config_args[@]}"
}

# Echange comptable : retention par defaut du broker (7 jours), aucune raison
# de s'en ecarter en developpement.
creer_topic "rations.etat.valide"
creer_topic "rations.etat.accuse"

# Journal d'audit : retention de 7 jours (604800000 ms), explicite plutot que
# par defaut. Le stockage durable des traces est la base rations_audit, pas ce
# topic ; la retention n'a qu'un role de tampon, pour qu'une panne prolongee du
# service Audit (redemarrage, incident) n'entraine pas de perte d'evenements
# avant qu'il ait pu les consommer. Sept jours couvre une panne de plusieurs
# jours sans faire grossir indefiniment le disque du broker en local.
creer_topic "rations.audit.evenement" 604800000

echo ""
echo "Topics presents sur le broker :"
kafka_topics --bootstrap-server "$BROKER" --list
