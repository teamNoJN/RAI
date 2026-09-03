# 1단계: Gradle로 JAR 빌드
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# 의존성 캐시 최적화 (소스 변경 시 재다운로드 방지)
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
RUN chmod +x gradlew
# 멀티모듈 settings 지만 이 이미지는 루트 앱만 빌드한다 (':' = 루트 프로젝트 한정)
RUN ./gradlew :dependencies --no-daemon

# 소스 복사 및 빌드 — 루트 앱이 :common 에 의존하므로 common 모듈도 함께 복사한다
COPY common common
COPY src src
RUN ./gradlew :bootJar -x test --no-daemon

# 2단계: 실행 환경 (JRE만 포함)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
