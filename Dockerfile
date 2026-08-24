FROM eclipse-temurin:24-jdk AS build
WORKDIR /workspace
COPY gradle gradle
COPY gradlew gradlew
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies
COPY src src
RUN ./gradlew --no-daemon clean bootJar --no-build-cache

FROM eclipse-temurin:24-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system merqadyn \
    && useradd --system --gid merqadyn --home-dir /app merqadyn
COPY --from=build /workspace/build/libs/merqadyn-api-1.0.0.jar app.jar
USER merqadyn
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
