# ADR-0013: 아키텍처 패턴: 모듈러 모놀리스 + 실용적 헥사고날

- 상태: 승인 (이벤트 재처리 시점 — "재시작 후에도 처리된다" — 은 [ADR-0023](0023-jobrunr-oss-framework-schema-and-event-resubmission.md)이 대체)
- 날짜: 2026-10-25

## 맥락

- 모듈 사이 경계는 Spring Modulith로 정했다 ([ADR-0004](0004-tech-stack.md)).
- 모듈 내부 구조는 아직 정하지 않았다.
- 교체 가능해야 하는 외부 의존성이 많다 ([ADR-0012](0012-swappable-external-dependencies.md)): 뉴스 공급원, 평가기, LLM, 메일, 봇 차단, 외부 로그인.
- 모든 모듈에 계층을 엄격하게 갖추면 이 규모에서는 군더더기가 많아진다.

## 결정

### 기본 패키지

`net.yozmnews`. 도메인 `yozmnews.net`을 거꾸로 쓴 것이다. 도메인 자체가 서비스 이름이라 2단계로 충분하다. 이후 다른 결과물이 생기면 `net.yozmnews.android`처럼 하위에 붙인다.

### 모듈 사이: Spring Modulith

- `net.yozmnews` 바로 아래 패키지 하나가 모듈 하나다.
- 다른 모듈에는 모듈 최상위 패키지의 공개 타입(API, 이벤트)으로만 접근한다. 하위 패키지는 외부에 보이지 않는다.
- 모듈 간 비동기 연동은 도메인 이벤트로 한다. 이벤트는 이벤트 발행 레지스트리(JDBC)에 기록되어 재시작 후에도 처리된다.
- 모듈 경계 위반은 Modulith 검증 테스트로 CI에서 막는다.

### 모듈 내부: 실용적 헥사고날

```
net.yozmnews.<module>
├── <Module>Api.java, <Event>.java   공개 API와 이벤트
├── domain/                          aggregate, 값 객체, 도메인 서비스, 포트(외부 의존 인터페이스)
├── application/                     유스케이스, 트랜잭션 경계, 이벤트 리스너
└── adapter/
    ├── in/web/                      REST 컨트롤러
    ├── in/job/                      백그라운드 작업
    └── out/<기술>/                  포트 구현 (예: out/jev, out/fake)
```

- **포트와 어댑터는 외부 의존성에만 엄격하게** 적용한다. 외부 연동은 반드시 `domain`의 포트 인터페이스 뒤에 둔다. 실제 구현과 개발용 Fake 구현은 모두 `adapter/out` 아래에 둔다.
- **영속성은 포트로 추상화하지 않는다.** PostgreSQL은 교체 대상이 아니다. aggregate를 Spring Data JDBC 엔티티로 그대로 쓰고, 저장소 인터페이스는 `domain`에 둔다 ([ADR-0014](0014-spring-data-jdbc.md)).
- 외부 연동이 없는 단순한 모듈(예: `feedback`)은 `domain`·`application` 구분 없이 서비스와 저장소만 둔다.
- **컨트롤러는 각 모듈 안**(`adapter/in/web`)에 둔다. 별도 `web` 모듈은 두지 않는다. 보안 설정, 오류 응답 형식, 공통 타입처럼 여러 모듈에 걸친 것은 `platform` 모듈에 둔다.

### 함께 적용하는 패턴

| 패턴 | 적용 방식 |
|---|---|
| Aggregate (DDD 일부) | `Feed`, `Article`, `TaxonomyVersion`, `Digest`, `LlmCredential`, `User` 등. aggregate 사이 참조는 ID로만 한다 |
| 가벼운 CQRS | 쓰기는 aggregate를 거친다. 화면·운영자용 조회와 후보 선별은 SQL 기반 전용 조회 모델(JdbcClient)로 한다. 이벤트 소싱은 쓰지 않는다 |
| 상태 머신 | 평가 상태, 다이제스트 상태, 피드 상태는 aggregate 메서드로만 전이한다. 허용되지 않은 전이는 예외로 막는다 |
| 멱등 작업 | JobRunr 작업은 다시 실행해도 결과가 같아야 한다. 재시도와 중복 실행에 대비한다 |
| 외부 호출은 트랜잭션 밖 | ① 트랜잭션 안에서 상태를 `RUNNING`으로 바꾸고 커밋 → ② 트랜잭션 밖에서 외부 API(LLM, Jev) 호출 → ③ 새 트랜잭션에서 결과 저장 |

### 테스트

| 수준 | 대상 | 방법 |
|---|---|---|
| 단위 | `domain` | 스프링 없이 순수 Java |
| 애플리케이션 | `application` | 포트에 Fake 구현을 꽂아서 |
| 어댑터 | `adapter` | WireMock(외부 API), Testcontainers(PostgreSQL), MockMvc(컨트롤러) |
| 모듈 | 모듈 하나 | `@ApplicationModuleTest` |
| 구조 | 전체 | Modulith 검증 테스트 + 모듈 문서 자동 생성 |

### 프론트엔드

- 기능별 폴더(`features/digest`, `features/settings` …)로 나눈다. 여러 기능이 같이 쓰는 것은 `shared/`에 둔다.
- 서버 상태는 TanStack Query로 관리한다. API 호출은 OpenAPI에서 생성한 타입과 클라이언트를 쓴다.

## 결과

- 외부 의존성이 교체될 때 바뀌는 범위가 어댑터 하나로 한정된다.
- 모듈 하나가 화면 API부터 저장까지 기능 하나를 온전히 가진다.
- 영속성 계층의 변환 코드(도메인 모델 ↔ 엔티티)가 없다. 대신 도메인 클래스에 Spring Data JDBC 애노테이션이 일부 붙는다.
- 공통 규칙(오류 응답, 경로 규칙)이 모듈마다 흩어지지 않도록 `platform`의 공통 설정과 리뷰로 관리해야 한다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 전통적 계층형 (controller → service → repository) | 외부 의존성 교체 지점이 구조에 드러나지 않는다 |
| 교과서적 헥사고날 / 클린 아키텍처 | 영속성까지 포트로 만들고 모델을 두 벌 두면 변환 코드만 늘어난다 |
| 컨트롤러를 별도 `web` 모듈에 모음 | `web`이 모든 모듈을 알아야 하고, 각 모듈이 내부 서비스를 공개해야 해서 경계가 약해진다 |
| 마이크로서비스 | [ADR-0004](0004-tech-stack.md) 참고 |

## 후속 기록

- 2026-10-29: 테스트 표의 "단위 — `domain` — 스프링 없이 순수 Java"를 읽는 법과, `domain` 이 교체하지 않는 프레임워크(Spring Data JDBC · Spring Modulith)의 타입을 쓰는 기준은 [ADR-0037](0037-domain-may-use-non-replaceable-framework-types.md)에 있다.
