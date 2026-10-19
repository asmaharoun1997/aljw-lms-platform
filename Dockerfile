# Étape 1 : On fabrique l'application avec Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src/ src/
RUN mvn clean package -DskipTests -q

# Étape 2 : On met l'application dans Tomcat pour qu'elle tourne
FROM tomcat:11.0-jre21
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /app/target/lms.war /usr/local/tomcat/webapps/lms.war
EXPOSE 8080