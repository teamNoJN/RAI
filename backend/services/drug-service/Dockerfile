# 빌드 컨텍스트는 backend/ 루트 (멀티모듈이라 common 도 필요).
#   docker build -f services/drug-service/Dockerfile .
# 1단계: Gradle로 JAR 빌드
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# 의존성 캐시 최적화 (소스 변경 시 재다운로드 방지)
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY common/build.gradle common/
COPY services/drug-service/build.gradle services/drug-service/
RUN chmod +x gradlew
RUN ./gradlew :services:drug-service:dependencies --no-daemon

# 소스 복사 및 빌드
COPY common/src common/src
COPY services/drug-service/src services/drug-service/src
RUN ./gradlew :services:drug-service:bootJar -x test --no-daemon

# 2단계: 실행 환경 (JRE만 포함)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/services/drug-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
