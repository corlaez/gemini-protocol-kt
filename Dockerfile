FROM eclipse-temurin:21-jre-alpine

# Set the non-root user
USER 1000

# Set working directory for the runtime
WORKDIR /app

# IMPORTANT: This COPY assumes the JAR was built externally on the host machine
# and is available in the local 'build/libs' directory when 'docker build' is run.
COPY build/libs/*.jar /app/lib
COPY build/app.jar /app/app.jar

# Application port
EXPOSE 8080

# Run the application directly. The JVM arguments are read from the JAR's manifest/startup script.
#CMD ["java", "-jar", "app.jar"]
CMD ["java", "-cp", "app.jar:lib/*", "com.your.package.MainKt"]
