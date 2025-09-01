# Etapa 1: Build con Maven
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY Proyecto /app
WORKDIR /app
RUN mvn clean package -DskipTests

# Etapa 2: Runtime con Java
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY --from=builder /app/target/Proyecto-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]



