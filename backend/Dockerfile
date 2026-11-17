FROM maven:3.9.16-eclipse-temurin-17 AS build
WORKDIR /workspace/backend
COPY backend/pom.xml ./
COPY backend/.mvn ./.mvn
COPY backend/mvnw backend/mvnw.cmd ./
RUN mvn -B dependency:go-offline
COPY backend/src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /workspace/backend/target/giova-backend-0.1.0-SNAPSHOT.jar app.jar
RUN mkdir -p /var/lib/giova/documents && chown -R 10001:10001 /var/lib/giova
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
