# Stage 1: Build the application
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app

# Copy the maven wrapper and pom.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Make the wrapper executable and download dependencies
RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline

# Copy the source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Expose the default Spring Boot port
EXPOSE 8080

# Copy the built jar from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
