FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml ./
COPY meter-bom/pom.xml meter-bom/pom.xml
COPY meter-common/pom.xml meter-common/pom.xml
COPY meter-model/pom.xml meter-model/pom.xml
COPY meter-auth/pom.xml meter-auth/pom.xml
COPY meter-service/pom.xml meter-service/pom.xml
COPY meter-api/pom.xml meter-api/pom.xml
COPY meter-ai-simulator/pom.xml meter-ai-simulator/pom.xml
RUN mvn -B -ntp dependency:go-offline -DskipTests

COPY meter-common/src meter-common/src
COPY meter-model/src meter-model/src
COPY meter-auth/src meter-auth/src
COPY meter-service/src meter-service/src
COPY meter-api/src meter-api/src
COPY meter-ai-simulator/src meter-ai-simulator/src
ARG MODULE
RUN test "$MODULE" = meter-api || test "$MODULE" = meter-ai-simulator
RUN mvn -B -ntp -pl "$MODULE" -am -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
ARG MODULE
COPY --from=build /workspace/${MODULE}/target/${MODULE}-*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

