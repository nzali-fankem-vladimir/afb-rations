# Image du passerelle (Spring Cloud Gateway, point d'entree unique) (Sprint 8.2).
#
# Le contexte de build est le dossier backend/, pas celui du module : le pom
# parent en est hors. Voir infra/docker/docker-compose.yml (context: ../../backend).
#
# Aucune valeur sensible dans cette image : base de donnees, Kafka, realm
# Keycloak arrivent par variables d'environnement au demarrage du conteneur.

# ---- Etape 1 : construction ------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Les descripteurs d'abord : ils changent rarement, et le cache Docker les
# garde tant que les dependances ne bougent pas.
COPY pom.xml pom.xml
COPY gateway/pom.xml gateway/pom.xml

COPY gateway/src gateway/src

# Le depot Maven vit dans un cache BuildKit : les dependances ne sont
# telechargees qu'une fois, meme quand les sources changent.
# Ce module ne depend PAS de rations-audit-commun, et l'image ne la contient
# donc pas : si une dependance y etait ajoutee un jour, ce build echouerait
# (bibliotheque introuvable) au lieu de la laisser passer en silence. Le parent
# est installe seul (-N) : Maven relit son descripteur.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -f pom.xml install -N -DskipTests \
 && mvn -B -ntp -f gateway/pom.xml package -DskipTests

# ---- Etape 2 : execution ---------------------------------------------------
# JRE seule (ni Maven, ni sources, ni JDK), Alpine : plus legere que la
# variante Ubuntu, et verifiee porteuse de wget (sonde) et des polices.
FROM eclipse-temurin:21-jre-alpine

# Utilisateur non privilegie, cree explicitement, identifiant NUMERIQUE : un
# orchestrateur ne peut pas verifier qu'un nom d'utilisateur n'est pas root,
# seul un identifiant le prouve (runAsNonRoot, Sprint 8.3).
RUN addgroup -g 10001 -S rations \
 && adduser -u 10001 -S -G rations -h /app -s /sbin/nologin rations

WORKDIR /app
COPY --from=build --chown=10001:10001 /build/gateway/target/gateway-*.jar /app/app.jar

USER 10001:10001

ENV SERVER_PORT=8080
EXPOSE 8080

# Sonde de sante : la seule route publique du service (CLAUDE.md section 10).
# 127.0.0.1 et non localhost, que busybox peut resoudre en IPv6 alors que le
# service n'ecoute qu'en IPv4. Une reponse 503 (base ou broker en panne) rend
# un code de sortie non nul : le conteneur est alors declare non sain.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${SERVER_PORT}/actuator/health" || exit 1

# MaxRAMPercentage : la JVM se cale sur la limite memoire du conteneur au lieu
# de la memoire de la machine hote. ExitOnOutOfMemoryError : un service qui
# manque de memoire s'arrete et est relance, plutot que de rester vivant et
# muet.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
