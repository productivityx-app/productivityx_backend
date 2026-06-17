FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Build artifact copied in
COPY build/libs/productivityx-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]