# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/url-shortener-demo-0.0.1-SNAPSHOT.jar app.jar
# Render's free instance has 512 MB RAM, so keep the JVM small
ENV JAVA_OPTS="-Xmx300m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
EXPOSE 8090
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
