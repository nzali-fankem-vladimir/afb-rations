#!/bin/sh
# Ecrit /config.js a partir des variables d'environnement du conteneur
# (Sprint 8.2, option A). Execute par l'entrypoint de l'image nginx avant le
# demarrage du serveur.
#
# Refuse de demarrer si une variable manque ou porte un caractere hors d'une
# adresse : mieux vaut un conteneur qui s'arrete avec un message clair qu'une
# page blanche dont la cause est dans la console du navigateur. La liste des
# caracteres autorises ecarte aussi tout guillemet ou retour a la ligne, qui
# briseraient le fichier JavaScript genere.
#
# Ces valeurs ne sont pas des secrets : elles sont lisibles par tout
# navigateur qui charge la page. Aucun mot de passe ne doit jamais passer ici.
set -eu

CIBLE="/usr/share/nginx/html/config.js"
ECHEC=0

for nom in API_BASE_URL KEYCLOAK_URL KEYCLOAK_REALM KEYCLOAK_CLIENT_ID; do
    eval "valeur=\"\${$nom:-}\""
    if [ -z "$valeur" ]; then
        echo "ERREUR configuration frontend : la variable $nom est absente ou vide." >&2
        ECHEC=1
        continue
    fi
    # Apres suppression de tous les caracteres autorises, il ne doit rien rester.
    reste=$(printf '%s' "$valeur" | tr -d 'A-Za-z0-9:/._~%?&=#@+,-')
    if [ -n "$reste" ]; then
        echo "ERREUR configuration frontend : la variable $nom contient un caractere non autorise." >&2
        ECHEC=1
    fi
done

if [ "$ECHEC" -ne 0 ]; then
    exit 1
fi

{
    echo "// Genere au demarrage du conteneur. Ne pas modifier a la main."
    echo "window.__CONFIG__ = {"
    printf '  API_BASE_URL: "%s",\n' "$API_BASE_URL"
    printf '  KEYCLOAK_URL: "%s",\n' "$KEYCLOAK_URL"
    printf '  KEYCLOAK_REALM: "%s",\n' "$KEYCLOAK_REALM"
    printf '  KEYCLOAK_CLIENT_ID: "%s",\n' "$KEYCLOAK_CLIENT_ID"
    echo "}"
} > "$CIBLE"

echo "config.js genere : 4 variables de configuration ecrites."
