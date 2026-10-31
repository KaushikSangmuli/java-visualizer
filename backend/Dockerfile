# Backend image. The runtime needs a full JDK 21 (javac + jdk.jdi) because user programs are compiled
# and traced inside the container. JDK 21 is pinned: the user-code sandbox relies on the Security
# Manager, which was removed in JDK 24.

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
# Download dependencies in their own layer so code-only changes rebuild quickly.
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jdk
WORKDIR /app
RUN useradd --system --uid 10001 --create-home visualizer
# Writable folder for the local profile's H2 database (hosted profiles use Azure SQL instead).
RUN mkdir -p /app/data && chown 10001:10001 /app/data
COPY --from=build /workspace/target/java-visualizer-0.1.0.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
# The backend gets about a third of the container's memory; the rest is left for up to
# visualizer.max-concurrent user-program JVMs (128 MB heap each plus JVM overhead). The flags are
# passed on the command line, not via JAVA_TOOL_OPTIONS, so the user-program JVMs do not inherit them.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=35", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
