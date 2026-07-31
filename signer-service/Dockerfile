FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S signer && adduser -S -G signer signer
WORKDIR /app
COPY --from=build /workspace/target/signer-service-*.jar app.jar
RUN chown signer:signer app.jar
USER signer
EXPOSE 8443
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/app.jar"]

