# ── Stage 1: Build ────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Runtime ──────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S wifak && adduser -S wifak -G wifak
COPY --from=build /app/target/*.jar app.jar
# Créer le dossier uploads avec les bonnes permissions AVANT de changer d'utilisateur
RUN mkdir -p /app/uploads/chat && chown -R wifak:wifak /app/uploads
VOLUME /app/uploads
RUN chown wifak:wifak app.jar
USER wifak
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
