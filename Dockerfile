FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/crm.war app.war
EXPOSE 8080
# Configure JVM memory for Render.com (512MB limit) and PayProp sync operations
CMD ["java", "-Xms256m", "-Xmx450m", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=100", "-XX:+UseStringDeduplication", "-jar", "app.war"]