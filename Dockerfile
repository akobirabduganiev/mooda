# Multi-stage Dockerfile for Kotlin Spring Boot (WebFlux)
# Build stage
FROM gradle:8.10.2-jdk21-alpine AS build
WORKDIR /workspace

# Copy Gradle wrapper and settings first for better caching
COPY gradle gradle
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
# Copy sources
COPY src src

# Build the application (skip tests here, they should run in CI)
RUN ./gradlew --no-daemon clean bootJar -x test

# Runtime stage - small JRE image
FROM eclipse-temurin:21-jre-alpine AS runtime

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# Copy fat jar from build stage
COPY --from=build /workspace/build/libs/*.jar /app/app.jar

ENV TZ=Asia/Tashkent
ENV JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Duser.timezone=${TZ}"

EXPOSE 8080

USER spring:spring

ENTRYPOINT ["java","-jar","/app/app.jar"]
