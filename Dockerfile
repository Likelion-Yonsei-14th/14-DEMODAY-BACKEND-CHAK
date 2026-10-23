# syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:17-jre
LABEL org.opencontainers.image.source="https://github.com/Likelion-Yonsei-14th/14-DEMODAY-BACKEND-CHAK"
RUN useradd --system --uid 10001 chak
WORKDIR /app
COPY --from=build /src/build/libs/chak-0.0.1-SNAPSHOT.jar app.jar
USER chak
EXPOSE 8080
# 메모리가 바닥나면 멈춘 채 버티지 않고 종료해서 도커가 바로 다시 띄우게 한다.
ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
