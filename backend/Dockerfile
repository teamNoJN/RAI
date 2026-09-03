# 루트 모놀리스 (:8090) — /api/reports, /api/regulations.
# 빌드 컨텍스트는 backend/ 루트.
#   docker build -f Dockerfile .
# 1단계: Gradle로 JAR 빌드
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# 의존성 캐시 최적화. settings.gradle 이 include 하는 모듈의 build.gradle 이 모두 있어야
# Gradle 이 구성 단계를 통과한다 — 루트 프로젝트만 빌드해도 마찬가지다.
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY common/build.gradle common/
COPY gateway/build.gradle gateway/
COPY services/user-service/build.gradle services/user-service/
COPY services/drug-service/build.gradle services/drug-service/
COPY services/chat-service/build.gradle services/chat-service/
RUN chmod +x gradlew
RUN ./gradlew :dependencies --no-daemon

# 소스 복사 및 빌드 (루트 프로젝트만)
COPY src src
RUN ./gradlew :bootJar -x test --no-daemon

# 2단계: 실행 환경 (JRE만 포함)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
