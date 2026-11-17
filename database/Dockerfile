FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml pom.xml
COPY database database
RUN mvn -f database/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/database/target/liana-database-1.0.0.jar /app/database.jar
COPY --from=build /workspace/database/target/lib /app/lib
ENTRYPOINT ["java", "-cp", "/app/database.jar:/app/lib/*", "com.liana.post.database.DatabaseManager"]
