# Stage 1: Build the JAR with Gradle
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copy everything needed for the build
COPY . .

# Make gradlew executable and build the bootJar (skip tests to speed up deploys)
RUN chmod +x ./gradlew && ./gradlew clean bootJar --no-daemon -x test

# Stage 2: Lightweight runtime image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy only the built jar from the build stage
COPY --from=build /app/build/libs/productivityx-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]