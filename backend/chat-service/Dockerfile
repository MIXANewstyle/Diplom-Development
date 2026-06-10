FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY backend backend
RUN mvn -q -DskipTests -pl backend/chat-service -am package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/backend/chat-service/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
