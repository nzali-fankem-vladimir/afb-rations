# Image du frontend (Sprint 8.2).
#
# UNE SEULE image pour tous les environnements : les adresses de la passerelle
# et de Keycloak ne sont PAS figees au build. Elles sont ecrites dans
# /config.js au demarrage du conteneur, a partir de ses variables
# d'environnement (docker-entrypoint-config.sh, decision de l'option A).
#
# Aucune valeur sensible ni propre a un environnement dans cette image : le
# fichier .env local est exclu par .dockerignore, sans quoi Vite l'aurait lu au
# build et inscrit ses adresses localhost dans le paquet.

# ---- Etape 1 : construction ------------------------------------------------
FROM node:24-alpine AS build
WORKDIR /build

# Les descripteurs d'abord : les dependances ne sont reinstallees que si
# elles changent. Le cache npm vit dans un cache BuildKit.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci

COPY . .
# tsc -b puis vite build : un defaut de typage fait echouer la construction.
RUN npm run build

# ---- Etape 2 : execution ---------------------------------------------------
# Serveur statique nginx SANS privilege : utilisateur 101, port 8080 (un
# utilisateur non root ne peut pas ecouter sur le port 80).
FROM nginxinc/nginx-unprivileged:stable-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf

# Les fichiers appartiennent a l'utilisateur 101 : le script de demarrage doit
# pouvoir reecrire config.js dans ce repertoire.
COPY --from=build --chown=101:101 /build/dist /usr/share/nginx/html

# Les scripts de /docker-entrypoint.d sont executes avant nginx, dans l'ordre.
COPY --chmod=755 docker-entrypoint-config.sh /docker-entrypoint.d/40-config-execution.sh

USER 101

EXPOSE 8080

# /healthz est servi par nginx lui-meme (voir nginx.conf).
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
