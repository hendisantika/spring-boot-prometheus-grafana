FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
# Optional proxy/trust configuration for the cloud build; no credentials are baked in.
ARG JAVA_TOOL_OPTIONS
RUN --mount=type=bind,source=.,target=/build-context \
    if [ -f /build-context/.docker-cacerts ]; then cp /build-context/.docker-cacerts /tmp/cacerts; fi; \
    bash gradlew --no-daemon --max-workers=2 dependencies --configuration runtimeClasspath
COPY src ./src
RUN bash gradlew --no-daemon --max-workers=2 bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-Xms128m", "-Xmx384m", "-jar", "app.jar"]
