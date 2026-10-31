# ADR-0020: 화면용 API 계약은 커밋된 OpenAPI 명세가 정본이고, 프론트엔드 타입은 그 명세에서 생성한다

- 상태: 승인
- 날짜: 2026-10-26
- 근거: [이슈 #4](https://github.com/Theia0382/yozm-news/issues/4) — 기점 `6ca51c0` 의 worktree 에 springdoc-openapi 3.2.0 과 실험 테스트를 임시로 넣고, 프론트엔드 사본에서 `openapi-typescript` · `openapi-fetch` 로 생성 · 타입 검사 · 검사 이미지 명령을 실측
- 관련: [ADR-0005](0005-frontend-react-spa.md)(OpenAPI 명세로 TS 타입 생성), [ADR-0013](0013-architecture-patterns.md)(`platform` · 프론트엔드 API 호출), [ADR-0016](0016-containerized-checks.md)(컨테이너 검사), [ADR-0019](0019-dev-compose-per-checkout-and-test-image-source.md)(테스트가 읽는 저장소 파일의 배선)

## 맥락

- 화면용 API(`/api/**`)는 프론트엔드와 함께 바뀌는 내부 계약이고, 외부 API(`/public-api/v1/**`)와 따로 관리한다([architecture.md](../architecture.md)). 백엔드 코드와 프론트엔드가 기대하는 모양이 어긋나면 검사에서 드러나야 한다.
- springdoc 은 실행 중인 앱이 명세를 낸다. 이 앱은 DB 없이 기동하지 않는다.
- 검사는 `backend` · `frontend` 두 서비스로 돌고, 서로 산출물을 주고받지 않는다. 프론트엔드 검사에는 JDK · DinD 가 없다([ADR-0016](0016-containerized-checks.md)).
- springdoc 기본 설정은 응답 필드를 모두 선택으로 내고 JSpecify `@Nullable` 을 반영하지 않는다. 그대로 두면 생성 타입이 `name?: string` 이 되어 필드를 빼거나 null 로 바꿔도 프론트엔드 타입이 달라지지 않는다.
- 응답 직렬화(Jackson 3 기본 설정)는 null 필드를 키째 쓴다.

## 결정

- **명세 정본** — 화면용 API 명세를 저장소 루트 `api/openapi.json` 에 커밋한다. 백엔드 테스트가 현재 코드가 내는 명세와 그 파일을 바이트 비교하고, 다르면 갱신 명령을 알리며 실패한다. 갱신은 Gradle 태스크(`updateOpenApiSpec`)가 같은 생성 경로로 파일을 다시 쓴다. 명세는 `/api/**` 만 담고, 출력은 키 정렬 · 들여쓰기 · 고정 `info` · `servers` 로 결정적으로 만든다. 두 검사 이미지가 이 파일을 담는다.
- **생성 타입** — 프론트엔드 TS 타입은 커밋하지 않는다. 명세에서 타입을 만드는 스크립트(`openapi-typescript`)를 타입이 필요한 스크립트(`build` · `dev`)가 먼저 돌린다. 호출은 생성 타입 기반 클라이언트(`openapi-fetch`)로 한다.
- **필수 · null 규칙** — 명세의 객체 속성은 모두 필수다. JSpecify `@Nullable` 이 붙은 속성만 null 을 허용하는 타입으로 낸다. 규칙은 `platform` 의 springdoc 확장(필수)과 springdoc 의 내장 nullable 속성(null)으로 구현한다.
- **노출** — 명세 엔드포인트(`/v3/api-docs`)는 기본으로 끄고 테스트와 `local` 프로필에서만 켠다. Swagger UI 는 넣지 않는다.
- **확인용 엔드포인트** — 파이프라인 확인용 `GET /api/ping` 을 `platform` 에 상시 둔다. 사용자 데이터를 담지 않는다.

## 결과

- 화면 계약 변경은 PR diff 의 명세 변경으로 보인다. 화면용 API 를 바꾸면 갱신 명령을 돌려 명세를 함께 커밋해야 하고, 잊으면 백엔드 검사가 실패한다.
- 명세가 바뀌어 프론트엔드 사용 코드와 맞지 않으면 프론트엔드 `build` 의 타입 검사가 실패한다. 두 검사는 지금처럼 독립이고, 프론트엔드 개발에 JDK · Docker 가 필요 없다.
- clone 직후와 명세가 바뀐 브랜치를 받은 뒤에는 타입 생성 명령을 돌리기 전까지 IDE 가 타입 오류를 보인다. `dev` · `build` 는 스스로 생성한다.
- 필수 규칙은 "직렬화가 null 키를 쓴다"에 기댄다. 전역 직렬화 설정을 바꾸면(`non_null` 등) 규칙과 함께 고쳐야 하고, 응답 리터럴 테스트가 그 어긋남을 드러낸다. 타입 단위로 키를 빼는 직렬화(`@JsonInclude(NON_NULL)`, Spring `ProblemDetail` 의 `NON_EMPTY`)는 이 규칙과 어긋나므로, 그런 타입을 화면 API 에 처음 들이는 작업이 예외 처리 방식을 정한다.
- 요청 본문 스키마에도 같은 규칙이 걸린다 — 프론트엔드는 null 가능한 키도 명시적으로 보낸다.
- 운영에서 명세를 보려면 설정(`springdoc.api-docs.enabled`)으로 켜야 한다. 프록시 구성과 무관하게 앱이 기본으로 닫는다.
- 화면용 API 에 인증이 들어오면 확인용 엔드포인트를 인증 없이 열어 두는 예외를 보안 설정에 두거나, 확인 테스트가 세션을 갖춰야 한다.
- `openapi-typescript` 의 피어 범위는 `typescript ^5.x` 다. TS 6 에서 생성 · 타입 검사가 동작함을 확인했고, 이 예외는 pnpm 피어 허용 규칙 한 줄로 명시한다. TS 를 올리는 작업이 생성기를 함께 본다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 명세를 커밋하지 않고 검사 때 앱을 띄워 생성 | 프론트엔드 검사가 백엔드 빌드 · DB(DinD)에 묶이고, 프론트엔드만 만지는 사람도 JDK · Docker 가 필요하다. 계약 변경이 diff 에 보이지 않는다 |
| 생성 타입도 커밋하고 검사가 재생성해 대조 | 생성 비용이 사실상 0 인데 명세의 두 번째 사본이 생긴다. 생성 출력의 서식이 저장소 규칙과 달라 서식 · 린트 처리가 더 필요하다 |
| 필드마다 Jakarta `@NotNull` · `@Schema(types)` | 규칙이 DTO 마다 흩어지고, 검증 어노테이션이나 Swagger 어노테이션이 응답 DTO 에 들어온다 |
| springdoc 기본(모두 선택)을 따름 | 필드 누락 · null 변경이 프론트엔드 타입에 드러나지 않고, null 이 오는 필드가 non-null 타입으로 적힌다 |
| `@Nullable` 속성을 "선택 + null" 로 | 응답은 null 키를 항상 쓰므로 런타임보다 느슨한 타입이 된다 |
| 명세 엔드포인트 기본 켜짐, 운영에서 끔 | 끄는 것을 잊으면 내부 API 구조가 노출되고, 막는 것이 프록시 구성에 기댄다 |
| Swagger UI 를 `local` 에서 켬 | 요구가 없고 의존성 · 노출 표면이 는다. 명세 파일을 외부 뷰어로 열 수 있다 |
| 확인용 엔드포인트를 첫 화면 API 가 생기면 제거 | 제거와 확인 테스트 이전이 다른 작업의 할 일로 남는다 |

## 후속 기록

- 2026-10-31: 결과 절이 "그런 타입을 화면 API 에 처음 들이는 작업이 정한다"고 남긴 오류 타입의 처리는 [ADR-0044](0044-screen-api-results-as-flat-discriminated-200-and-rfc9457-problems.md)가 정했다 — Spring `ProblemDetail` 을 직렬화하지 않고, RFC 9457 의 필드 이름을 따르며 모든 속성이 값을 갖는 자체 레코드로 낸다.
