# 빌드: Gradle(JDK 21) → 실행: JRE 21. 테스트는 Testcontainers(Docker)가 필요해 이미지 빌드에서는 빼고 로컬/CI에서 돈다
FROM gradle:9.2.1-jdk21 AS build
WORKDIR /src
COPY settings.gradle build.gradle ./
RUN gradle dependencies --no-daemon -q > /dev/null
COPY src ./src
RUN gradle bootJar --no-daemon -q

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
EXPOSE 8080
USER 1000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
