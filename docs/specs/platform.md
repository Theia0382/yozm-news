# platform 모듈 Spec

공통 구조와 쓰는 규칙은 [specs/README.md](README.md)에 있다.

## 1. 책임과 범위

`platform` 은 여러 모듈에 걸친 것을 맡는다.

- **이벤트 발행 재처리** — 모듈 사이 이벤트의 리스너가 실패한 발행을 주기적으로 다시 처리하고, 상한을 넘긴 발행을 알린다
- **화면용 API 명세 설정** — 백엔드 코드가 만드는 화면용 API 명세의 고정 값과 속성 규칙. 구현은 이 모듈의 `OpenApiConfiguration` 과 `springdoc` 설정이다(정본 규약은 [overview.md §6](../overview.md#6-횡단-규약) "화면용 API 계약")
- **API 계약 확인 엔드포인트** — 명세 → 프론트엔드 생성 타입의 파이프라인을 확인하는 엔드포인트
- **(미구현)** 보안 설정, 오류 응답 형식, 공통 타입, 프로필별 설정 검사

하지 않는 것:

- 다른 모듈의 도메인 규칙을 갖지 않는다. 이벤트를 다시 처리할 뿐, 이벤트를 받아 처리하는 일은 각 리스너 모듈의 것이다
- 화면용 API 명세 파일(`api/openapi.json`)을 소유하지 않는다 — 명세는 각 모듈의 컨트롤러에서 만들어진다

다른 모듈과의 의존과 이벤트 흐름은 [overview.md §5](../overview.md#5-모듈-지도)에 있다.

## 2. 용어

| 용어 | 뜻 |
|---|---|
| 이벤트 발행 | Spring Modulith 이벤트 발행 레지스트리의 한 행 — 이벤트 하나를 리스너 하나에 전달하는 기록 |
| 처리 시도 | 한 발행의 리스너 실행 횟수 — 최초 처리와 재제출 |
| 멈춘 발행 | 발행하고 멈춤 기준 시간이 지나도 끝나지 않은 발행 — 처리 도중 프로세스가 죽은 경우 등 |
| 상한에 이른 발행 | 실패했고 처리 시도가 상한에 이른 발행 — 다시 처리하지 않는다 |

## 3. 공개 인터페이스

모듈 최상위 패키지 `net.yozmnews.platform` 의 공개 타입은 없다. 다른 모듈이 부르는 공개 API 도 없다.

HTTP 엔드포인트:

| 메서드 · 경로 | 용도 |
|---|---|
| `GET /api/ping` | API 계약 파이프라인(명세 → 프론트엔드 생성 타입) 확인. 화면에는 쓰지 않고 사용자 데이터를 담지 않는다(platform-R7) |

요청 · 응답 형식의 정본은 [`api/openapi.json`](../../api/openapi.json)이다([ADR-0020](../adr/0020-screen-api-contract-from-committed-openapi-spec.md)).

## 4. 규칙과 불변식

### 이벤트 발행 재처리

모듈 사이 이벤트는 Spring Modulith 이벤트 발행 레지스트리(JDBC)에 기록된다([ADR-0023](../adr/0023-jobrunr-oss-framework-schema-and-event-resubmission.md)).

- **platform-R1** — 다시 처리하는 것은 리스너가 실패한(`FAILED`) 발행뿐이다. 한 발행의 총 처리 시도 상한은 6회(최초 처리 1회 + 재제출 5회)다 — 발행의 처리 시도 수는 저장 때 1 이고 재제출마다 1 씩 는다. 처리 시도가 상한에 이른 실패 발행은 다시 처리하지 않고 실패로 남기며, 남아 있는 동안 재처리 작업이 돌 때마다 그 발행 ID 들을 경고 로그 한 줄로 알린다
- **platform-R2** — 처리 도중 멈춘 발행은 Spring Modulith 의 멈춤 감시가 발행 시각에서 멈춤 기준 시간(30분)이 지나면 실패로 바꾸고, 그 뒤 platform-R1 의 경로로 다시 처리된다. 멈춤 기준은 발행 · 처리 중 · 재제출 세 상태에 같은 값이다 — 감시는 기간이 하나라도 있으면 세 상태를 모두 판정하고, 비운 상태는 발행 전부를 멈춤으로 본다
- **platform-R3** — 재제출 주기와 멈춤 기준은 0 보다 크고, (처리 시도 상한 − 1) × 재제출 주기가 멈춤 기준보다 짧아야 한다 — 감시는 발행 시각으로 멈춤을 판정하므로, 마지막 재제출이 멈춤 기준 안에 있어야 정상 운영에서 재처리 중인 발행을 멈춤으로 오판하지 않는다. 기동 때 확인하고 어기면 기동하지 않는다
- **platform-R4** — 재처리는 미완료 발행 전체를 읽어 고른다 — 상한에 이른 실패 발행이 쌓여도 새 실패 발행이 밀리지 않는다. 같은 발행은 여러 인스턴스가 동시에 재처리해도 한 곳에서만 재제출된다(Spring Modulith 의 조건부 상태 갱신). 재제출은 리스너의 처리 끝을 기다리지 않는다
- **platform-R5** — 완료된 발행은 바로 지운다 — 결과는 각 모듈의 데이터에 남는다. 실패한 발행은 남아 재처리와 원인 조사의 근거가 된다. 이 보존 기간은 [data-model.md "보존 정책"](../data-model.md#보존-정책-초안)의 이벤트 발행 기록 행이다

### 화면용 API 명세

- **platform-R6** — 백엔드가 만드는 화면용 API 명세는 실행 환경과 무관하게 같다: `info` 는 제목 `요즘뉴스 화면용 API` · 버전 `0`(화면용 API 는 프론트엔드와 함께 바뀌어 버전을 매기지 않는다), `servers` 는 `/`(SPA 와 같은 오리진). 객체 스키마의 속성은 모두 필수로 내고(이름 순) — 응답은 null 인 속성도 키째 쓴다 — null 가능성은 JSpecify `@Nullable` 이 타입으로 남긴다. 명세는 `/api/**` 경로만 담고, 출력은 키 순 · 들여쓰기로 고정해 커밋된 명세와 바이트로 비교한다. 실행 중인 앱의 명세 엔드포인트는 기본으로 닫혀 있다. 명세가 정본이라는 규약과 프론트엔드 타입 생성은 [overview.md §6](../overview.md#6-횡단-규약) "화면용 API 계약"이 정한다
- **platform-R7** — `GET /api/ping` 은 `{"message": "pong"}` 을 돌려준다. 화면에는 쓰지 않고 사용자 데이터를 담지 않는다

## 5. 작업과 리스너

| 작업 · 리스너 | 계기 | 내용 |
|---|---|---|
| 이벤트 발행 재처리 — 반복 작업 ID `event-publication-resubmission` | 재제출 주기(§7, 기본 5분)마다 | 미완료 발행에서 다시 처리할 것을 재제출하고 상한에 이른 것을 알린다(platform-R1 · R4). 재시도하지 않는다 — 실패한 회차가 재시도를 기다리는 동안 작업 서버가 다음 회차를 건너뛰므로 다음 회차가 곧 재시도다 |
| 재처리 설정 검사 | 기동 | 재제출 주기와 멈춤 기준의 조건을 확인한다(platform-R3) |

## 6. 데이터

해당 없음 — 이 모듈은 자기 테이블이 없다. 이벤트 발행 레지스트리와 작업 테이블은 프레임워크 테이블이라 [data-model.md](../data-model.md)에 있다.

## 7. 설정

| 설정 키 | 바꾸는 규칙 |
|---|---|
| `yozmnews.events.stale-after` | 멈춤 기준 시간(기본 30분 — platform-R2 · R3). Spring Modulith 의 멈춤 감시(`spring.modulith.events.staleness.*` 세 상태)가 같은 키를 읽는다. 필수 |
| `yozmnews.events.resubmission-interval` | 재제출 주기(기본 5분 — platform-R3, §5). 반복 작업이 같은 키를 읽는다. 필수 |
| `spring.modulith.events.completion-mode` | `delete` — 완료된 발행을 바로 지운다(platform-R5) |
| `springdoc.api-docs.enabled` · `springdoc.paths-to-match` · `springdoc.writer-with-*` · `springdoc.model-converters.java-nullable-property-customizer.enabled` | 명세 엔드포인트 기본 꺼짐 · `/api/**` 만 · 출력 고정 · `@Nullable` 속성을 null 허용 타입으로(platform-R6) |

## 8. 외부 의존

해당 없음 — 포트가 없다. 이벤트 발행 레지스트리(Spring Modulith)와 반복 작업(JobRunr)은 프레임워크다.

## 9. 결정

| ADR | 한 줄 |
|---|---|
| [0013](../adr/0013-architecture-patterns.md) | 아키텍처 패턴: 모듈러 모놀리스 + 실용적 헥사고날 |
| [0020](../adr/0020-screen-api-contract-from-committed-openapi-spec.md) | 화면용 API 계약은 커밋된 OpenAPI 명세가 정본이고, 프론트엔드 타입은 그 명세에서 생성한다 |
| [0023](../adr/0023-jobrunr-oss-framework-schema-and-event-resubmission.md) | 백그라운드 작업은 JobRunr OSS 로 돌리고, 프레임워크 스키마는 라이브러리 원문을 Flyway 에 담으며, 실패한 이벤트는 반복 작업이 상한까지 다시 처리한다 |
