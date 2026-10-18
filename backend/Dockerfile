# Build with Maven and JDK 21: the runtime also needs javac and jdk.jdi.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jdk
WORKDIR /app
RUN useradd --system --uid 10001 --create-home visualizer
COPY --from=build /workspace/target/java-visualizer-0.1.0.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
