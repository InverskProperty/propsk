FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven

# Copy pom.xml and download dependencies
COPY pom.xml .
# Try to download dependencies with retries, but don't fail the build if Maven Central has issues
RUN mvn dependency:go-offline -B || echo "Dependency download had issues, will retry during build"

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/crm.war app.war
EXPOSE 8080
# Optimized JVM configuration - shell form to expand $PORT env var
CMD java -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -XX:+UseCompressedOops -Dserver.port=${PORT:-8080} -jar app.war
