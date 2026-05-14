# ── Stage 1: Build ────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -B
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests -B

# ── Stage 2: Runtime ──────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S wifak && adduser -S wifak -G wifak
COPY --from=build /app/target/authentification-service-1.0.0.jar app.jar
RUN chown wifak:wifak app.jar
USER wifak
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
