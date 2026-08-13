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
RUN chown -R appuser:appuser /app

# The container is deliberately started as root by Compose because Docker
# Socket access is an explicit capability of this service only.
USER appuser
EXPOSE 8084 8085

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
