FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy

RUN useradd --system --create-home --uid 10001 appuser
WORKDIR /app

COPY --from=build /workspace/target/*.jar /app/app.jar
RUN mkdir -p /app/data/media && chown -R appuser:appuser /app

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
