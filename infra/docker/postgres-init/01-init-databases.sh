#!/usr/bin/env bash
# Cree les cinq roles applicatifs et leurs bases, une par service ayant besoin
# d'une base propre (CLAUDE.md section 3). Execute automatiquement par l'image
# postgres au tout premier demarrage du volume (docker-entrypoint-initdb.d).
#
# Chaque role est proprietaire de sa seule base : un service ne peut pas, meme
# par erreur de configuration, se connecter a la base d'un autre.
set -euo pipefail

MOT_DE_PASSE_APPLICATIF="${RATIONS_DB_PASSWORD:-changeme-in-development}"

for SERVICE in identite saisie grilles workflow audit; do
    ROLE="rations_${SERVICE}"
    BASE="rations_${SERVICE}"

    echo "Creation du role et de la base : ${BASE}"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-SQL
        CREATE ROLE ${ROLE} LOGIN PASSWORD '${MOT_DE_PASSE_APPLICATIF}';
        CREATE DATABASE ${BASE} OWNER ${ROLE};
SQL
done
