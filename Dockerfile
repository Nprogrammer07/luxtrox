# syntax=docker/dockerfile:1

# ---------- Etapa 1: build ----------
# 3.9-eclipse-temurin-21-alpine es un tag flotante que Maven SI sigue
# actualizando activamente (a diferencia de eclipse-temurin:21-jre-alpine
# solo, ver la imagen de runtime mas abajo para el porque de esa
# distincion).
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copiar solo el pom.xml primero y descargar dependencias en su PROPIA
# capa de Docker -- mientras pom.xml no cambie, reconstruir despues de
# tocar solo codigo fuente reutiliza esta capa entera, sin re-descargar
# medio internet en cada build.
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B
# -DskipTests a proposito: los tests de integracion/API necesitan
# Testcontainers (acceso al socket de Docker), que no esta disponible
# de forma confiable DENTRO de un build de Docker. Los tests corren en
# CI antes de construir esta imagen, no aqui.

# ---------- Etapa 2: runtime ----------
# 21-jre-alpine (sin sufijo de version de Alpine) deja de recibir
# actualizaciones -- Adoptium solo sigue publicando parches de
# seguridad sobre tags con el sufijo explicito (3.23 es el mas
# reciente al momento de escribir esto). Usar el generico arriesgaria
# quedarse atras en CVEs del sistema base sin que ningun rebuild lo
# note.
FROM eclipse-temurin:21-jre-alpine-3.23
WORKDIR /app

# Nunca corre como root dentro del contenedor.
RUN addgroup -S luxtrox && adduser -S luxtrox -G luxtrox

COPY --from=build /app/target/luxtrox-backend-*.jar app.jar
RUN chown luxtrox:luxtrox app.jar
USER luxtrox

EXPOSE 8080

# --spider just revisa que la URL responda (2xx/3xx), sin descargar el
# cuerpo -- mas simple y robusto que parsear el JSON de salud con grep.
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget --spider -q http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]