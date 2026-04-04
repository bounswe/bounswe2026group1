# Stage 1: Build the application
# Using a standard JDK for compiling
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline

COPY src src
RUN ./mvnw clean package -DskipTests

# Stage 2: Run the application securely
# Using the lightweight Alpine-based JRE to reduce attack surface
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a dedicated non-root user and group
# -S stands for creating a system user/group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the built jar file and change its ownership to our restricted user
COPY --from=builder --chown=appuser:appgroup /app/target/*.jar app.jar

# Switch to the non-root user BEFORE running the application
USER appuser

EXPOSE 8080

# Run the application
# "sh" is used for variable substitution but runs safely under 'appuser'
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Dserver.port=${PORT:-8080} -jar app.jar"]
