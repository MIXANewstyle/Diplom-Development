FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY backend backend
RUN mvn -q -DskipTests -pl backend/content-service -am package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/backend/content-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
