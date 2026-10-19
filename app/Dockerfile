# syntax=docker/dockerfile:1

FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -B

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn package -B -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN groupadd --system tixora && useradd --system --gid tixora tixora
COPY --from=build /workspace/target/eventticketing-0.0.1-SNAPSHOT.jar app.jar

USER tixora
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
