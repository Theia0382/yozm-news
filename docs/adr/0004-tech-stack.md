# ADR-0004: 백엔드 기술 스택

- 상태: 승인. 데이터 접근 항목은 [ADR-0014](0014-spring-data-jdbc.md)가 대체
- 날짜: 2026-10-25

## 맥락

- 기획 초안의 언어는 Java다.
- 소규모 인프라에서 운영해야 하고, 구성 요소는 적을수록 좋다.
- 여러 LLM 공급자(BYOK)와 이후 MCP 서버를 지원해야 한다.

## 결정

| 영역 | 선택 |
|---|---|
| 런타임 | Java 25 (LTS) |
| 프레임워크 | Spring Boot 4.x |
| 구조 | Spring Modulith 기반 모듈러 모놀리스. 같은 이미지를 `web`/`worker` profile로 나눠 실행 |
| LLM | Spring AI 2.x (2026년 6월 GA, Boot 4 대상, MCP 서버를 코어에 포함) |
| DB | PostgreSQL 17+, Flyway |
| 데이터 접근 | Spring Data JPA를 기본으로 하고, 후보 선별 같은 복잡한 조회는 SQL로 직접 작성. jOOQ는 필요해지면 검토 |
| 백그라운드 작업 | JobRunr (8.3부터 Boot 4 지원). 저장소는 PostgreSQL |
| 세션 | Spring Session JDBC |
| RSS | Rome |
| 빌드·테스트 | Gradle Kotlin DSL, JUnit 5, Testcontainers, WireMock |

## 결과

- 상태 저장소가 PostgreSQL 하나다. Redis나 메시지 브로커가 필요해지면 그때 추가한다.
- 마이크로서비스로 쪼개지 않아도 모듈 경계를 테스트로 강제할 수 있다. 나중에 worker를 별도 서비스로 분리하기도 쉽다.
- Spring AI 2.x는 1.x에서 바뀐 점이 많으므로, 1.x 기준 예제를 참고할 때 주의한다.
- JVM 메모리 사용량 때문에 아주 작은 인스턴스(1GB 미만)에서는 `web`과 `worker`를 합쳐 실행하는 편이 낫다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| Kotlin | 초안이 Java 기준이다. 필요하면 일부 모듈에 도입할 수 있다 |
| 마이크로서비스 | 운영 부담이 규모에 비해 과하다 |
| Quartz / db-scheduler / 직접 구현한 `SKIP LOCKED` 큐 | 모두 가능. JobRunr가 재시도, 반복 작업, 대시보드를 한 번에 제공해서 골랐다. 라이선스(LGPL)와 OSS 기능 범위는 착수 시 다시 확인한다 |
| Redis 기반 큐 | 구성 요소가 늘어난다 |

## 후속 기록

- 2026-10-27: 착수 때 다시 확인한 JobRunr 의 라이선스 · OSS 기능 범위와 도입 방식은 [ADR-0023](0023-jobrunr-oss-framework-schema-and-event-resubmission.md)에 있다.
