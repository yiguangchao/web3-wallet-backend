FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src ./src
# The required unit/integration suite is a prerequisite CI job. The image stage
# only creates the immutable artifact and must not require Docker/Anvil services.
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:17-jre-alpine AS runtime

RUN addgroup -S wallet && adduser -S -G wallet wallet
WORKDIR /app
COPY --from=build /workspace/target/web3-wallet-backend-*.jar app.jar
RUN chown wallet:wallet /app/app.jar

USER wallet
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
  CMD wget -q --spider http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
