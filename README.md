# RAI

msa-lecture 구조를 기반으로 한 MSA 프로젝트.

```
RAI/
├── backend/
│   ├── parser-service/   # Spring Boot 3.4.5 / Java 21, port 8086
│   └── eureka-server/    # 서비스 디스커버리, port 8761 (full 프로필)
├── ai/
├── frontend/
├── init-db/              # MariaDB 초기 DDL
└── docker-compose.yml    # 개발용 인프라
```

## 사전 준비

- **Java 21** (Temurin 등 아무 JDK). Gradle 은 wrapper(`./gradlew`) 를 쓰므로 설치 불필요.
- **Docker Desktop** (compose 포함)

## 로컬 개발 (권장)

인프라만 도커로 띄우고 서비스는 IDE 나 gradle 로 직접 실행한다.

```bash
# 1. DB 기동 (Kafka 필요 시: docker compose up -d mariadb kafka)
docker compose up -d mariadb

# 2. 서비스 기동 (기본 프로필 local)
cd backend/parser-service
./gradlew bootRun
```

확인:

- Health: http://localhost:8086/api/parser/health
- Swagger UI: http://localhost:8086/swagger-ui.html

테스트:

```bash
cd backend/parser-service && ./gradlew test
```

## 전체 스택 (docker compose)

Eureka 와 서비스까지 컨테이너로 올릴 때는 `full` 프로필을 사용한다.

```bash
docker compose --profile full up -d --build
docker compose --profile full down
```

auth-server / api-gateway 는 msa-lecture 의 `infra-images.tar` 를 `docker load -i` 한 뒤 compose 에 추가해서 사용한다 (레포에는 포함되지 않음).

## 포트

| 구성요소 | 호스트 포트 | 비고 |
|---|---|---|
| MariaDB (`lecturedb`) | 3379 | DB `rai_db`, 계정 `manager` / `SqlDba-1` |
| Kafka | 29092 | 호스트에서 접속 시. 컨테이너 내부는 `kafka:9092` |
| parser-service | 8086 | |
| eureka-server | 8761 | full 프로필 |

## Spring 프로필

| 프로필 | 용도 |
|---|---|
| `local` (기본) | `localhost:3379` DB 사용, Eureka 비활성 |
| `docker` | compose 에서 사용. datasource / eureka 값은 environment 로 주입 |

## 참고

- `SecurityConfig` 는 개발용으로 전체 `permitAll` 상태. 게이트웨이/auth-server 연동 시 JWT 리소스 서버 설정으로 전환할 것.
- DB 비밀번호는 로컬 개발용으로 하드코딩되어 있음. 배포 환경에서는 `.env` 로 분리.
