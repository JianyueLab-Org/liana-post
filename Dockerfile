FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

ARG MODULE
COPY . .
RUN mvn -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app

ARG MODULE
COPY --from=build /workspace/${MODULE}/target/*-exec.jar /app/app.jar

ENV JAVA_OPTS=""
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
