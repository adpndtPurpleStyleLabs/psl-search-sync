FROM eclipse-temurin:24-jdk
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
COPY target/psl-search-sync.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
