FROM maven:3.9.12-eclipse-temurin-17 AS build
WORKDIR /build
ENV MAVEN_OPTS="-XX:ActiveProcessorCount=4"
COPY storage-service/pom.xml ./pom.xml
COPY storage-service/src ./src
COPY scripts/ContainerHealthcheck.java /tmp/ContainerHealthcheck.java
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B -q -DskipTests package && javac -d /health /tmp/ContainerHealthcheck.java

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /build/target/*-SNAPSHOT.jar /app/app.jar
COPY --from=build /health /app/health
ENV JAVA_TOOL_OPTIONS="-XX:ActiveProcessorCount=4 -XX:MaxRAMPercentage=70"
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=8s --start-period=60s --retries=6 CMD ["java", "-cp", "/app/health", "ContainerHealthcheck", "http://127.0.0.1:9080/actuator/health"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
