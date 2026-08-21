# groovy-notification-service

**Groovy**(태그 기반 스터디 매칭 플랫폼) MSA의 **알림(Notification)** 도메인을 담당하는 백엔드 서비스입니다. 다른 서비스가 발행한 이벤트를 Kafka로 소비해 알림을 저장하고, SSE(Server-Sent Events)로 브라우저에 실시간 푸시합니다.

## 1. 이 레포는 무엇인가

Groovy 폴리레포 중 `notification-service` 하나만 담은 독립 배포 단위입니다. **나가는 동기 호출이 없는 leaf 서비스**(Kafka 구독만 하고 다른 서비스의 도메인 API를 호출하지 않음)라, MSA 전환 계획서에서 gateway와 함께 가장 먼저 분리·검증된 파일럿 서비스입니다.

## 2. 주요 기능

- SSE 기반 실시간 알림 구독(`/api/notifications/subscribe`)
- 안 읽은 알림 목록 조회, 단건/전체 읽음 처리
- SSE 구독용 1회성 티켓 발급(`/api/notifications/subscribe-ticket`)
- study/calendar/content-service가 발행한 이벤트(신청/승인/댓글/좋아요/레벨업/일정변경/대기열좌석 등)를 소비해 알림 생성
- 만료된 알림 자동 정리(스케줄러)

## 3. 시스템 아키텍처

### 데이터베이스

| 항목 | 값 |
|---|---|
| DB(스키마)명 | `notification_db` |
| 전용 계정 | `notification_service` (다른 서비스 DB 접근 불가) |
| DBMS | MySQL 8.0, Flyway (`V1__create_notifications_table.sql`, `V2__create_processed_events_table.sql`) |

| 테이블 | 역할 | 주요 컬럼 | 관계 |
|---|---|---|---|
| `notifications` | 알림 | `id` PK, `recipient_id`, `type`, `title`, `message`, `target_id`(nullable), `is_read`, `read_at` | `recipient_id` → **identity_db.users.id**(서비스 간 참조). `target_id`는 알림이 가리키는 대상(스터디/회고록 등)의 PK를 도메인 구분 없이 저장 — 어느 서비스의 PK인지는 `type`으로만 구분(느슨한 참조) |
| `processed_events` | Inbox(멱등성) | `event_id` PK(varchar 36) | study/calendar/content-service의 `outbox_events.event_id`와 **같은 값**이 Kafka를 거쳐 여기 기록됨 — DB FK가 아니라 메시지 payload로 연결되는 비동기 상관관계 |

### Redis 활용

이 서비스만 Redis(`spring-boot-starter-data-redis`)를 사용합니다: SSE는 인스턴스별로 커넥션을 물고 있는 stateful 통신이라, 여러 인스턴스로 스케일 아웃해도 알림이 전달되도록 Redis Pub/Sub으로 인스턴스 간 브로드캐스트하고, SSE 구독용 1회성 티켓(TTL 30초)을 저장합니다.

## 4. 기술 스택

| 카테고리 | 기술 |
|---|---|
| Language / Framework | Java 21, Spring Boot 4.1.0 |
| Build | Gradle 멀티모듈 (`event-contract`, `observability`, `web-common`, `security-common` 4개 lib — `client-common`은 미사용, 나가는 호출이 없어서) |
| Data | Spring Data JPA + MySQL, Flyway |
| Cache / Pub-Sub | Spring Data Redis — SSE 브로드캐스트 + 구독 티켓 |
| Security | `security-common`의 `JwtAuthenticationFilter`/`JwksKeyLocator` — JWT 검증만 |
| 메시징 | Spring Kafka `@KafkaListener` — **소비 전용**(발행 없음), consumer group `notification-service` |
| Observability | Actuator, Micrometer(Prometheus), OpenTelemetry(OTLP → Tempo) |

## 5. 다른 MSA 서비스와의 네트워크 호출 관계

### 동기 HTTP (Out) — JWKS 검증뿐

| 대상 | 엔드포인트 | 용도 |
|---|---|---|
| identity-service | `GET /.well-known/jwks.json` | JWT 서명 검증(이 서비스가 수행하는 유일한 나가는 동기 호출) |

### 비동기 Kafka (In) — 유일한 소비자

study/calendar/content-service가 발행자, notification-service가 **유일한 소비자**입니다.

```
study-service ─┐
calendar-service ├─(Kafka: notification-events, group-id: notification-service)─▶ notification-service ─▶ SSE(브라우저)
content-service ─┘                                                                      │
                                                                                    실패 시 → notification-events.DLT
```

1. `NotificationEventConsumer`(`@KafkaListener`)가 `notification-events` 토픽을 구독해 `NotificationPayload`를 역직렬화
2. `notifications` 테이블에 저장 + `processed_events`(Inbox)에 기록해 멱등 처리(At-least-once 전달 하에서 중복 소비 방지)
3. Redis Pub/Sub으로 SSE 커넥션을 물고 있는 인스턴스에 브로드캐스트 → 브라우저로 실시간 푸시
4. 역직렬화 실패 등 재시도해도 실패할 메시지는 `notification-events.DLT`(Dead Letter Topic)로 이동

### 외부 노출

api-gateway가 `Path=/api/notifications/**`를 이 서비스로 라우팅합니다.

## 6. 로컬 실행 방법

### 방법 A — 독립 빌드

```bash
./gradlew :services:notification-service:bootJar
docker build -t groovy-notification-service .
```

### 방법 B — 로컬 JVM (MySQL/Redis/Kafka/identity-service 필요)

```bash
export SPRING_DEV_DB_URL="jdbc:mysql://localhost:3306/notification_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul"
export SPRING_DEV_DB_USERNAME=notification_service
export SPRING_DEV_DB_PASSWORD=notification_service_local_only_pw
export JWT_JWKS_URL=http://localhost:8081/.well-known/jwks.json
export REDIS_HOST=localhost
export REDIS_PORT=6379
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export KAFKA_SECURITY_PROTOCOL=SASL_PLAINTEXT

./gradlew :services:notification-service:bootRun
```

### 방법 C — 전체 스택 (권장)

```bash
# 원본 Groovy 레포 루트에서
cp .env.example .env
docker compose -f docker-compose.local.yml up -d --build notification-service
```

기본 포트는 `8085`입니다.

> 참고: `application.yml`의 `jwt.jwks-url` 기본값이 아직 `legacy-monolith:8080`을 가리키는 흔적이 남아 있으나, 실제로는 `JWT_JWKS_URL` 환경변수로 항상 덮어써서 동작에는 문제가 없습니다(정리되지 않은 기본값, 알려진 사소한 한계).

## 7. 기존 모노레포에서 어느 부분을 떼온 것인가

레거시 모놀리스 `groovy/`의 `domain/notification` 패키지가 이 서비스로 이전되었으며, **Phase 6에서 가장 먼저 추출된 도메인**입니다(나가는 의존성이 0인 leaf라 파일럿으로 선택됨).

- **그대로 옮겨온 것**: 알림 엔티티, SSE 구독/브로드캐스트 로직, 만료 알림 정리 스케줄러
- **MSA 전환 과정에서 근본적으로 바뀐 것**: 모놀리식 시절에는 스터디 신청/승인 등의 이벤트가 **같은 프로세스 내 직접 메서드 호출/이벤트**로 알림 서비스에 전달됐지만, 서비스가 물리적으로 분리되면서 **Kafka를 통한 비동기 이벤트 소비**로 재설계되었습니다. `event`(Kafka consumer)/`inbox`(멱등 처리) 패키지가 이때 신규로 생겼습니다.
- **격리 작업 상세**: `docs/transfer/groovy-notification-service.md`(원본 레포 기준) — CI에 계약 테스트(`NotificationEventConsumerContractTest`) 실행 스텝이 처음에 누락되어 있었던 것을 바로잡은 기록도 남아 있습니다.

## 8. 모니터링 스택에서 관측되는 부분

| 스택 | 관측 내용 |
|---|---|
| **Prometheus** | `job=notification-service`로 `:8085/actuator/prometheus` 15초 스크레이프. JVM/HikariCP(`notification_db` 풀)/HTTP 지표 |
| **Alertmanager** | HikariCP 커넥션 대기, JVM 힙 40% 초과, CPU 95% 초과 알림 |
| **Grafana** | `springboot-dashboard.json`(JVM), `backend-app-logs-dashboard.json`(Loki 로그) |
| **Loki + Alloy** | 컨테이너 stdout JSON 로그 자동 수집 |
| **Tempo** | study/calendar/content-service가 Kafka 메시지에 실어 보낸 traceId를 이어받아 발행 시점 트레이스와 이 서비스의 소비 스팬이 하나로 연결됨(비동기 흐름도 분산 트레이싱으로 추적 가능) |
| **계약 테스트** | `NotificationEventConsumerContractTest`(고정 fixture로 `NotificationPayload` 역직렬화 검증)가 CI의 `contract-test` job에 포함되어, 발행자 측 스키마가 조용히 깨지지 않도록 방어 |
