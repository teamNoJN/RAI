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
# 멀티모듈 settings 지만 이 이미지는 루트 앱만 빌드한다 (':' = 루트 프로젝트 한정)
RUN ./gradlew :dependencies --no-daemon

# 소스 복사 및 빌드.
# ★ 루트 프로젝트는 build.gradle 22행에서 implementation project(':common') 을 선언한다.
#   common/src 를 안 넣으면 빈 jar 가 만들어져 com.rai.common.security 를 못 찾는다.
COPY common/src common/src
COPY src src
RUN ./gradlew :bootJar -x test --no-daemon

# 2단계: 실행 환경 (JRE만 포함)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
