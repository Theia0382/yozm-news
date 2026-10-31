# admin 모듈 Spec

공통 구조와 쓰는 규칙은 [specs/README.md](README.md)에 있다.

## 1. 책임과 범위

`admin` 은 운영자 화면이 부르는 HTTP API 를 낸다.

- **소스 · 피드 관리** — 프리셋 목록 · 등록, 소스 목록, 운영자 원문 등록 · 조회 · 수정 · 미리보기, 소스 · 피드 켜기 · 끄기, 운영자 수집 주기
- **응답 모양** — 모듈의 운영 결과를 판별자 하나의 평평한 응답으로 옮기고, 요청의 문제를 HTTP 오류로 낸다

하지 않는 것:

- 판정 · 저장 · 조회를 하지 않는다 — 그 지식을 가진 모듈의 공개 운영 API 에 맡긴다
- 운영자를 인증하지 않는다 — 운영자 경로의 보호는 `platform` 의 보안 구성이 한다([platform Spec "운영자 접근 통제"](platform.md#운영자-접근-통제))
- **(미구현)** 수집 지표 · 태그 체계 · 평가 결과 · 사건의 운영자 기능

다른 모듈과의 의존과 이벤트 흐름은 [overview.md §5](../overview.md#5-모듈-지도)에 있다.

## 2. 용어

| 용어 | 뜻 |
|---|---|
| 판별자 | 결과 응답의 `result` — 결과의 종류. 종류마다 쓰이는 속성이 정해져 있다(admin-R1) |

## 3. 공개 인터페이스

모듈 최상위 패키지 `net.yozmnews.admin` 에 공개 타입이 없다. 다른 모듈이 부르는 공개 API 는 없다.

HTTP 엔드포인트 — 모두 운영자 경로다(운영자 토큰 필요 — 없거나 틀리면 401, 운영자 기능이 닫혔으면 403):

| 메서드 · 경로 | 용도 |
|---|---|
| `GET /api/admin/presets` | 프리셋 목록 — 등록한 소스 ID 포함 |
| `POST /api/admin/presets/{key}/registration` | 프리셋 소스 등록 |
| `GET /api/admin/sources` | 소스 목록과 수집 주기의 하한 |
| `POST /api/admin/sources` | 운영자 원문으로 소스 등록 |
| `GET /api/admin/sources/{id}/profile` | 운영자 원문 조회 |
| `PUT /api/admin/sources/{id}/profile` | 운영자 원문 수정 |
| `POST /api/admin/profile-previews` | 등록 전 원문의 피드 하나 미리보기 |
| `POST /api/admin/sources/{id}/enable` · `/disable` | 소스 켜기 · 끄기 |
| `PUT /api/admin/sources/{id}/poll-interval` | 운영자 소스 수집 주기 — `null` 이면 지운다 |
| `POST /api/admin/feeds/{id}/enable` · `/disable` | 피드 켜기 · 끄기 |
| `PUT /api/admin/feeds/{id}/poll-interval` | 운영자 피드 수집 주기 — `null` 이면 지운다 |

요청 · 응답 형식의 정본은 [`api/openapi.json`](../../api/openapi.json)이다([ADR-0020](../adr/0020-screen-api-contract-from-committed-openapi-spec.md)). 결과 · 오류의 대응은 admin-R1 ~ admin-R3 이다.

## 4. 내부 구조

### 개관

| 패키지 | 책임 |
|---|---|
| (최상위) | 모듈 선언 — 의존을 `source` · `platform` 으로 둔다(`package-info`) |
| `adapter/in/web` | 운영자 컨트롤러, 결과 · 목록의 응답 레코드, 없는 대상의 404 |

협력: 컨트롤러는 `source` 의 공개 운영 API `SourceAdministration` 만 부르고, 결과를 응답 레코드의 정적 팩토리로 옮긴다. 오류 응답은 `platform` 의 `ProblemResponses` 로 만든다. `application` · `domain` 이 없다 — 판단이 없다.

역할: 모든 구성 요소가 화면 역할(`@WebRole`)이다(platform-R9).

### 들어오는 쪽

| 진입점 | 구성 요소 | 계기 | 내용 |
|---|---|---|---|
| 프리셋 | `AdminPresetController` | `GET /api/admin/presets` · `POST …/{key}/registration` | 목록 · 등록(admin-R1 · admin-R2) |
| 소스 · 운영자 원문 | `AdminSourceController` | `/api/admin/sources/**` · `POST /api/admin/profile-previews` | 목록 · 원문 등록 · 조회 · 수정 · 미리보기, 소스 켜기 · 끄기 · 주기(admin-R1 ~ admin-R3) |
| 피드 | `AdminFeedController` | `/api/admin/feeds/**` | 켜기 · 끄기 · 주기(admin-R1 ~ admin-R3) |
| 없는 대상 | `AdminExceptionHandling` | 운영자 컨트롤러의 공개 예외 | 없는 소스 · 피드를 404 문제 응답으로 낸다(admin-R2) |

컨트롤러는 트랜잭션을 열지 않는다 — 피드 켜기 · 원문 미리보기는 피드를 받으므로 응답까지 걸린다(source-R9).

`adapter/in/web` 의 그 밖의 타입: 응답 `PresetResponse` · `SourceListResponse` · `SourceResponse`(`FeedResponse`) · `RegistrationResponse` · `RevisionResponse` · `SourceEnableResponse` · `FeedEnableResponse` · `PreviewResponse`, 요청 `ProfileText` · `PreviewRequest` · `PollIntervalRequest`, 보조 `AdminIds`(경로 ID) · `PollIntervals`(하한 미만의 400).

### `application`

해당 없음.

### `domain`

해당 없음 — 판단과 저장은 `source` 에 있다.

### 나가는 쪽

해당 없음 — 포트가 없다. 부르는 것은 다른 모듈의 공개 API 다.

## 5. 규칙과 불변식

### 결과와 오류의 대응

- **admin-R1** — 운영 기능이 정상으로 내놓는 결과는 200 응답이다. 응답은 판별자 `result` 하나와, 결과마다 쓰이는 속성을 모두 담은 평평한 레코드다 — 그 결과에 쓰이지 않는 속성은 `null` 이거나 빈 목록이다. 결과 → 판별자:
  - 등록 — `REGISTERED`(소스 ID) · `ALREADY_REGISTERED` · `INVALID_PROFILE`(문제)
  - 원문 수정 — `REVISED`(끈 피드의 식별자 · 소스를 껐는지 · 더한 피드의 식별자) · `INVALID_PROFILE`(문제)
  - 소스 켜기 — `ENABLED` · `REFUSED`(이유)
  - 피드 켜기 — `ENABLED`(미리보기, 이미 켜졌으면 `null`) · `REFUSED`(이유) · `BELOW_CRITERIA`(미리보기 · 못 미친 기준) · `UNFETCHED`(실패) · `READING_CHANGED`
  - 원문 미리보기 — `INVALID_PROFILE`(문제) · `FEED_NOT_IN_PROFILE` · `PASSED`(미리보기) · `BELOW_CRITERIA`(미리보기 · 못 미친 기준) · `UNFETCHED`(실패)

  판별자와 속성의 조합은 응답 레코드의 정적 팩토리 하나에서만 만든다. 응답의 소스 · 피드 ID 는 JSON 수다
- **admin-R2** — 대상이 없으면 404 문제 응답이다: 없는 소스 · 피드(`source` 의 공개 예외), 경로의 ID 가 1 미만, 카탈로그에 없는 프리셋, 프리셋 소스의 운영자 원문(조회 · 수정). 없는 소스 · 피드는 이 모듈의 조언이 앱 전체 조언보다 먼저 받는다 — 처리하지 못한 예외(500)가 되지 않는다
- **admin-R3** — 요청의 문제는 그 상태의 문제 응답이다: 본문 형식 · 원문 없음 400, 수집 주기의 하한 미만 400(설명 "수집 주기는 {하한}분 이상이다" — 하한은 `SourceAdministration.MINIMUM_POLL_INTERVAL_MINUTES`), 다른 요청이 먼저 바꿈 409(platform-R12). 끄기 · 주기 변경은 성공하면 204 다. 소스 목록 응답은 수집 주기의 하한을 함께 낸다

## 6. 데이터

해당 없음 — 이 모듈은 자기 테이블이 없다.

## 7. 설정

해당 없음.

## 8. 결정

| ADR | 한 줄 |
|---|---|
| [0043](../adr/0043-admin-functions-guarded-by-env-bearer-token-until-account-roles.md) | 운영자 기능은 계정 인증 전까지 환경 변수의 Bearer 토큰으로 막고, 계정 인증이 생기면 같은 경로의 `ADMIN` 역할 판정으로 바꾼다 |
| [0044](../adr/0044-screen-api-results-as-flat-discriminated-200-and-rfc9457-problems.md) | 화면 API 는 판정 결과를 판별자 하나의 평평한 200 응답으로, 요청의 문제만 RFC 9457 형식의 자체 오류 레코드로 앱 전체에서 낸다 |
| [0045](../adr/0045-admin-module-serves-operator-api-over-module-public-operation-apis.md) | 운영자 API 는 `admin` 모듈이 내고, 각 모듈은 운영 기능과 결과 타입을 최상위 공개 API 로 낸다 |
