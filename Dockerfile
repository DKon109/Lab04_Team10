# Personal full-stack deployment: the original React app and real Spring API.
FROM node:22-bookworm-slim AS frontend
WORKDIR /web
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-17 AS backend
WORKDIR /workspace
COPY app/pom.xml ./
COPY app/src ./src
COPY --from=frontend /web/dist ./src/main/resources/static
RUN mvn package -B -ntp -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system tixora && useradd --system --gid tixora tixora
COPY --from=backend /workspace/target/eventticketing-0.0.1-SNAPSHOT.jar app.jar
USER tixora
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC"
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
