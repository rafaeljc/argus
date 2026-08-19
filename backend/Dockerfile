FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY config/ config/
COPY src/ src/
RUN mvn -B -q package -DskipTests \
 && APP_JAR="$(ls target/argus-*.jar)" \
 && java -Djarmode=tools -jar "$APP_JAR" extract --layers --launcher --destination /layers

FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build --chown=65532:65532 /layers/dependencies/ ./
COPY --from=build --chown=65532:65532 /layers/spring-boot-loader/ ./
COPY --from=build --chown=65532:65532 /layers/snapshot-dependencies/ ./
COPY --from=build --chown=65532:65532 /layers/application/ ./
EXPOSE 8080 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", \
            "org.springframework.boot.loader.launch.JarLauncher"]
