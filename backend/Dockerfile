FROM eclipse-temurin:25.0.4.1_1-jdk-noble AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew dependencies --no-daemon > /dev/null
COPY src src
RUN ./gradlew bootJar -x test --no-daemon \
    && cp build/libs/*-SNAPSHOT.jar app.jar

FROM eclipse-temurin:25.0.4.1_1-jdk-noble AS extract
WORKDIR /workspace
COPY --from=build /workspace/app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25.0.4.1_1-jre-noble
RUN useradd --system --uid 1001 spring
USER spring
WORKDIR /app
COPY --from=extract /workspace/extracted/dependencies/ ./
COPY --from=extract /workspace/extracted/spring-boot-loader/ ./
COPY --from=extract /workspace/extracted/snapshot-dependencies/ ./
COPY --from=extract /workspace/extracted/application/ ./
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
