# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# Run stage (pin to an exact digest/tag in your registry pipeline and rebuild regularly for OS patches)
FROM eclipse-temurin:21.0.5_11-jre-noble
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --no-create-home appuser
WORKDIR /app
COPY --from=build /app/target/url-shortener-demo-0.0.1-SNAPSHOT.jar app.jar
USER appuser
# Container-aware sizing; override JAVA_OPTS for the real host
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -fs http://localhost:${PORT:-8090}/actuator/health/readiness || exit 1
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
