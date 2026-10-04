FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY voting.system/voting.system/pom.xml .
COPY voting.system/voting.system/src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

CMD ["java", "-jar", "app.jar"]