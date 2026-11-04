# admin 모듈 Spec

공통 구조와 쓰는 규칙은 [specs/README.md](README.md)에 있다.

## 1. 책임과 범위

`admin` 은 운영자 화면이 부르는 HTTP API 를 낸다.

- **소스 · 피드 관리** — 프리셋 목록 · 등록, 소스 목록 · 숨긴 소스 목록, 운영자 원문 등록 · 조회 · 수정 · 미리보기, 소스 · 피드 켜기 · 끄기, 운영자 수집 주기, 소스 · 피드 숨기기 · 다시 보이기 · 삭제와 삭제하면 함께 지워질 기사 수
- **수집 현황** — 피드마다 수집 상태와 엔트리 지표, 소스마다 그 합
- **이벤트 발행** — 남겨진 이벤트 발행의 목록 · 다시 처리 · 폐기
- **태그 체계** — 버전 목록 · 버전 원문 조회, 운영자 원문의 확인 · 활성화(재평가 기간 포함)
- **평가 현황** — 기간을 골라 주제별 보도량, 버전 하나의 평가 품질 지표, 지금의 평가 대기 · 실패 건수
- **평가 검수** — 버전 · 기간의 평가 목록(태그 · 주제 판정 거르기)과 표본, 평가 하나의 상세. 기사 정보(제목 · 출처 · 섹션 · 카테고리 · 요약문 · 원문 링크)를 맞붙인다
- **태그 기준 · 저장 기준** — 지금 값 · 어긋난 상태 · 이력, 바꾸기 전 미리보기, 변경
- **빠진 태그 채움** — 채울 기간(일)의 확인 · 요청, 채움 목록과 진행 · 결과
- **응답 모양** — 모듈의 운영 결과를 판별자 하나의 평평한 응답으로 옮기고, 요청의 문제를 HTTP 오류로 낸다

하지 않는 것:

- 판정 · 저장 · 조회를 하지 않는다 — 그 지식을 가진 모듈의 공개 API(운영 API · 공개 조회)에 맡긴다
- 운영자를 인증하지 않는다 — 운영자 경로의 보호는 `platform` 의 보안 구성이 한다([platform Spec "운영자 접근 통제"](platform.md#운영자-접근-통제))
- **(미구현)** 사건의 운영자 기능

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
| `GET /api/admin/presets` | 프리셋 목록 — 등록한 소스 ID 와 그 소스를 숨겼는지 포함 |
| `POST /api/admin/presets/{key}/registration` | 프리셋 소스 등록 |
| `GET /api/admin/sources` | 소스 목록(숨기지 않은 소스 — 피드와 숨긴 피드를 나눠 싣는다)과 수집 주기의 하한. 프로필을 얻지 못한 소스의 이름은 기록된 출처 이름이다(source-R51 · source-R57) |
| `GET /api/admin/sources/hidden` | 숨긴 소스 목록 — 소스 목록과 같은 모양, 숨긴 시각 순 |
| `POST /api/admin/sources` | 운영자 원문으로 소스 등록 |
| `GET /api/admin/sources/{id}/profile` | 운영자 원문 조회 |
| `PUT /api/admin/sources/{id}/profile` | 운영자 원문 수정 |
| `POST /api/admin/profile-previews` | 등록 전 원문의 피드 하나 미리보기 |
| `POST /api/admin/sources/{id}/enable` · `/disable` | 소스 켜기 · 끄기 |
| `PUT /api/admin/sources/{id}/poll-interval` | 운영자 소스 수집 주기 — `null` 이면 지운다 |
| `POST /api/admin/feeds/{id}/enable` · `/disable` | 피드 켜기 · 끄기 |
| `PUT /api/admin/feeds/{id}/poll-interval` | 운영자 피드 수집 주기 — `null` 이면 지운다 |
| `POST /api/admin/sources/{id}/hide` · `/unhide` | 소스 숨기기 · 다시 보이기(source-R53) |
| `DELETE /api/admin/sources/{id}` | 소스 삭제(source-R55) |
| `POST /api/admin/feeds/{id}/hide` · `/unhide` | 피드 숨기기 · 다시 보이기 — 숨기기는 결과를 낸다(source-R53 · source-R54) |
| `DELETE /api/admin/feeds/{id}` | 피드 삭제 — 결과를 낸다(source-R54 · source-R55) |
| `GET /api/admin/sources/{id}/deletion-impact` | 소스를 지우면 함께 지워질 기사 수 — 지금 기준 그 소스만 가진 기사(article-R15) |
| `GET /api/admin/sources/{sourceId}/feeds/{feedId}/deletion-impact` | 피드를 지우면 함께 지워질 기사 수 — 지금 기준 그 소스의 그 피드만 가진 기사(article-R15). 기사의 기록을 소스로 찾으므로 소스를 함께 받는다. 소스와 맞지 않는 피드 · 없는 대상은 0 이다 |
| `GET /api/admin/collection-status` | 수집 현황 — 집계 기간, 피드 상태의 기준값 · 재판정 간격, 소스마다 합계 · 칸별 합과 피드 행(수집 상태 · 합계 · 칸별 개수) |
| `GET /api/admin/event-publications` | 남겨진 이벤트 발행 — 발행 시각 오름차순 앞쪽 최대 상한 건, 전체 건수, 실을 수 있는 건수(`limit`) |
| `POST /api/admin/event-publications/resubmission` | 고른 발행(본문 `ids`) 다시 처리 — 발행마다 결과 |
| `POST /api/admin/event-publications/discard` | 고른 발행(본문 `ids`) 폐기 — 발행마다 결과 |
| `GET /api/admin/tag-sets` | 태그 체계 버전 목록(번호가 큰 것부터)과 재평가 기간의 상한 |
| `GET /api/admin/tag-sets/{id}/text` | 버전의 원문 — 그 버전의 내용과 지금 태그 기준 · 저장 기준으로 만든다([evaluation-R27](evaluation.md#체계-변경)) |
| `POST /api/admin/tag-set-reviews` | 운영자 원문(본문 `text`)의 확인 — 아무것도 쓰지 않는다. 본문 `recentDays` 는 재평가 기간(없으면 0) |
| `POST /api/admin/tag-sets` | 운영자 원문(본문 `text`)으로 새 버전을 만들어 활성으로 바꾼다. 본문 `recentDays` 는 재평가 기간(없으면 0 — 재평가하지 않는다) |
| `GET /api/admin/topic-coverage` | 주제별 보도량 — 질의 `from` · `to`(시작일 · 종료일, `yyyy-MM-dd`, 끝 포함) · `zone`(날짜를 해석할 시간대) · 선택 `topic`(되풀이 — 없으면 모든 주제). 활성 버전 ID 와 날짜 범위의 상한(`maxDays`)을 함께 낸다([evaluation-R28](evaluation.md#평가-현황--주제별-보도량) · R30) |
| `GET /api/admin/evaluation-overview` | 평가 현황 — 질의 `version`(태그 체계 버전 ID) · `from` · `to` · `zone`([evaluation-R29](evaluation.md#평가-현황--주제별-보도량)) |
| `GET /api/admin/evaluation-queue` | 지금의 평가 대기 · 실패 건수 — 기간과 무관하다 |
| `GET /api/admin/evaluations` | 평가 목록 — 질의 `version` · `from` · `to` · `zone`, 선택 `tagKind` · `tag`(함께) · `topicStatus` · 커서 `afterEvaluatedAt` · `afterId`(함께) · `limit`(기본 20). 행마다 판정과 기사의 제목 · 출처([evaluation-R33](evaluation.md#평가-검수--기준-미리보기)) |
| `GET /api/admin/evaluations/{id}` | 평가 하나의 상세와 기사의 제목 · 요약문 · 정규 URL · 엔트리별 출처 이름 · 섹션 · RSS 카테고리 |
| `GET /api/admin/evaluation-samples` | 표본 — 질의 `version` · `from` · `to` · `zone` · `seed` · `size`(기본 `EvaluationReview.DEFAULT_SAMPLE_SIZE`). 행 모양은 목록과 같다([evaluation-R34](evaluation.md#평가-검수--기준-미리보기)) |
| `GET /api/admin/thresholds` | 활성 버전의 태그 기준 · 저장 기준 — 태그 종류마다 지금 값 · 지난 저장 기준 최댓값 · 어긋난 상태, 이력([evaluation-R36](evaluation.md#평가-검수--기준-미리보기)) |
| `POST /api/admin/threshold-previews` | 기준 미리보기 — 본문 `kind` · `tagThreshold` · `storeThreshold` · `from` · `to` · `zone`. 아무것도 쓰지 않는다([evaluation-R35](evaluation.md#평가-검수--기준-미리보기)) |
| `PUT /api/admin/thresholds/{kind}` | 활성 버전의 한 태그 종류(`TOPIC` · `REGION`)의 태그 기준 · 저장 기준 변경 — 본문 `tagThreshold` · `storeThreshold`, 행위자는 운영자([evaluation-R11](evaluation.md#태그-기준--저장-기준) · R12) |
| `POST /api/admin/tag-backfill-reviews` | 빠진 태그 채움의 확인 — 본문 `days`(채울 기간, 일). 덮을 평가 시각 범위와 버전별 평가 수. 아무것도 쓰지 않는다([evaluation-R31](evaluation.md#빠진-태그-채움--저장-바닥)) |
| `POST /api/admin/tag-backfills` | 빠진 태그 채움 요청 — 본문 `days`. 기록한 채움, 또는 끝나지 않은 채움이 있으면 그것 |
| `GET /api/admin/tag-backfills` | 빠진 태그 채움 목록(최근 것부터) — 덮는 범위 · 상태 · 버전별 채울 저장 기준과 건수, 채울 기간의 상한(`maxDays`) |

요청 · 응답 형식의 정본은 [`api/openapi.json`](../../api/openapi.json)이다([ADR-0020](../adr/0020-screen-api-contract-from-committed-openapi-spec.md)). 결과 · 오류의 대응은 admin-R1 ~ admin-R3, 수집 현황의 응답은 admin-R4, 평가 현황 · 보도량 · 대기 건수의 응답은 admin-R5, 평가 검수 응답의 기사 정보는 admin-R6 이다. 빠진 태그 채움의 응답은 `evaluation` 의 결과 · 보기를 옮기고 버전 ID 를 JSON 수로 낸다(admin-R1). 이벤트 발행 목록은 `platform` 의 `StrandedPublications` 를 옮기기만 하고 이벤트 내용을 싣지 않는다(platform-R14).

## 4. 내부 구조

### 개관

| 패키지 | 책임 |
|---|---|
| (최상위) | 모듈 선언 — 의존을 `source` · `article` · `evaluation` · `platform` 으로 둔다(`package-info`) |
| `adapter/in/web` | 운영자 컨트롤러, 결과 · 목록의 응답 레코드, 요청 매개변수 읽기, 없는 대상의 404 |

협력: 컨트롤러는 `source` 의 공개 운영 API `SourceAdministration`, `article` 의 공개 운영 API `ArticleAdministration`, `evaluation` 의 공개 운영 API `TagSetAdministration` · `EvaluationAdministration` · `EvaluationReview` 와 공개 조회 `TopicCoverage`, `article` 의 공개 조회 `ArticleDirectory`, `source` 의 공개 조회 `FeedLabels`, `platform` 의 공개 운영 API `EventPublicationAdministration` 만 부르고, 결과를 응답 레코드의 정적 팩토리로 옮긴다. 오류 응답은 `platform` 의 `ProblemResponses` 로 만든다. `application` · `domain` 이 없다 — 판단이 없다.

역할: 모든 구성 요소가 화면 역할(`@WebRole`)이다(platform-R9).

### 들어오는 쪽

| 진입점 | 구성 요소 | 계기 | 내용 |
|---|---|---|---|
| 프리셋 | `AdminPresetController` | `GET /api/admin/presets` · `POST …/{key}/registration` | 목록 · 등록(admin-R1 · admin-R2) |
| 소스 · 운영자 원문 | `AdminSourceController` | `/api/admin/sources/**` · `POST /api/admin/profile-previews` | 목록 · 숨긴 목록 · 원문 등록 · 조회 · 수정 · 미리보기, 소스 켜기 · 끄기 · 주기 · 숨기기 · 다시 보이기 · 삭제(admin-R1 ~ admin-R3) |
| 피드 | `AdminFeedController` | `/api/admin/feeds/**` | 켜기 · 끄기 · 주기 · 숨기기 · 다시 보이기 · 삭제(admin-R1 ~ admin-R3) |
| 삭제 영향 | `AdminDeletionImpactController` | `GET …/deletion-impact` 둘 | `article` 의 기사 수를 옮긴다(admin-R2) |
| 수집 현황 | `AdminCollectionController` | `GET /api/admin/collection-status` | 소스 목록(수집 상태 포함)과 엔트리 집계를 소스 · 피드 행으로 맞붙인다(admin-R4) |
| 이벤트 발행 | `AdminEventPublicationController` | `GET /api/admin/event-publications` · `POST …/resubmission` · `POST …/discard` | 남겨진 발행의 목록과 다시 처리 · 폐기의 발행별 결과를 옮긴다. 한 번에 받는 발행 수의 상한은 공개 API 가 판정하고 컨트롤러는 그 결과를 400 으로 옮긴다(admin-R1 · admin-R3) |
| 태그 체계 | `AdminTagSetController` | `GET /api/admin/tag-sets` · `GET …/{id}/text` · `POST /api/admin/tag-set-reviews` · `POST /api/admin/tag-sets` | 버전 목록 · 원문 · 확인 · 활성화의 결과를 옮긴다. 재평가 기간의 범위는 공개 API 가 판정하고 컨트롤러는 그 결과를 400 으로 옮긴다(admin-R1 ~ admin-R3) |
| 평가 현황 | `AdminEvaluationController` | `GET /api/admin/topic-coverage` · `GET /api/admin/evaluation-overview` · `GET /api/admin/evaluation-queue` | 보도량 · 평가 현황 · 대기 건수의 결과를 옮긴다. 기간의 범위는 공개 타입이 판정하고, 컨트롤러는 날짜 · 시간대를 읽어 넘기고 그 결과를 400 으로 옮긴다(admin-R2 · admin-R3 · admin-R5) |
| 평가 검수 | `AdminEvaluationReviewController` | `GET /api/admin/evaluations` · `GET …/{id}` · `GET /api/admin/evaluation-samples` | 목록 · 상세 · 표본의 결과를 옮기고 기사 정보를 맞붙인다. 쪽 크기 · 표본 크기 · 씨앗의 범위는 공개 API 가 판정하고 컨트롤러는 그 결과를 400 으로 옮긴다(admin-R1 ~ admin-R3 · admin-R6) |
| 태그 기준 · 저장 기준 | `AdminThresholdController` | `GET /api/admin/thresholds` · `POST /api/admin/threshold-previews` · `PUT /api/admin/thresholds/{kind}` | 상태 · 미리보기 · 변경의 결과를 옮긴다. 변경은 `ChangeActor.OPERATOR` 로 부른다. 값의 범위는 공개 API 가 판정하고 컨트롤러는 그 결과를 400 으로 옮긴다(admin-R1 · admin-R3) |
| 빠진 태그 채움 | `AdminTagBackfillController` | `POST /api/admin/tag-backfill-reviews` · `POST /api/admin/tag-backfills` · `GET /api/admin/tag-backfills` | 확인 · 요청 · 목록의 결과를 옮긴다. 채울 기간의 범위는 공개 API 가 판정하고 컨트롤러는 그 결과를 400 으로 옮긴다(admin-R1 · admin-R3) |
| 없는 대상 | `AdminExceptionHandling` | 운영자 컨트롤러의 공개 예외 | 없는 소스 · 피드를 404 문제 응답으로 낸다(admin-R2) |

컨트롤러는 트랜잭션을 열지 않는다 — 피드 켜기 · 원문 미리보기는 피드를 받으므로 응답까지 걸린다(source-R9).

`adapter/in/web` 의 그 밖의 타입: 응답 `PresetResponse` · `SourceListResponse` · `SourceResponse`(`FeedResponse`) · `RegistrationResponse` · `RevisionResponse` · `SourceEnableResponse` · `FeedEnableResponse` · `PreviewResponse` · `FeedRemovalResponse` · `DeletionImpactResponse` · `CollectionStatusResponse`(`StatusCriteriaResponse` · `SourceCollectionResponse` · `FeedCollectionResponse` · `EntryMetricsResponse` · `EntryBucketResponse`) · `StrandedPublicationListResponse`(`StrandedPublicationResponse`) · `ResubmissionListResponse`(`ResubmissionItemResponse`) · `DiscardListResponse`(`DiscardItemResponse`) · `TagSetListResponse`(`TagSetVersionItem`) · `TagSetText` · `TagSetReviewResponse` · `TagSetActivationResponse` · `TopicCoverageResponse` · `EvaluationOverviewResponse` · `EvaluationQueueResponse` · `EvaluationListResponse` · `EvaluationRowResponse` · `EvaluationSampleResponse` · `EvaluationDetailResponse` · `ReviewArticleResponse` · `ReviewArticleDetailResponse` · `ReviewEntryResponse` · `ThresholdStatusResponse` · `ThresholdPreviewResponse` · `ThresholdChangeResponse` · `TagBackfillReviewResponse` · `TagBackfillResponse` · `TagBackfillListResponse` · `TagBackfillRunResponse`(채움 하나 — 요청 · 목록 응답이 함께 쓴다), 요청 `ProfileText` · `PreviewRequest` · `PollIntervalRequest` · `EventPublicationIdsRequest` · `TagSetRequest` · `ThresholdPreviewRequest` · `ThresholdChangeRequest` · `TagBackfillRequest`, 보조 `AdminIds`(경로 ID) · `PollIntervals`(하한 미만의 400) · `ReevaluationDays`(범위 밖 재평가 기간의 400) · `BackfillDays`(범위 밖 채울 기간의 400) · `AdminPeriods`(기간 매개변수 — 날짜 · 시간대 읽기와 그 400) · `ReviewArticles`(평가 검수 응답의 기사 정보 맞붙이기 — admin-R6) · `ThresholdValuesProblem`(범위 밖 태그 기준 · 저장 기준의 400).

### `application`

해당 없음.

### `domain`

해당 없음 — 판단과 저장은 그 기능을 가진 모듈(`source` · `article` · `evaluation` · `platform`)에 있다.

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
  - 피드 숨기기 · 삭제 — `REMOVED` · `REFUSED`(이유 `IN_PROFILE` · `PROFILE_UNAVAILABLE`)
  - 이벤트 발행 다시 처리 — 발행마다 `RESUBMITTED` · `NOT_EXHAUSTED` · `UNREADABLE` · `NOT_FOUND`(platform-R14)
  - 이벤트 발행 폐기 — 발행마다 `DISCARDED` · `NOT_STRANDED` · `CHANGED` · `NOT_FOUND`(platform-R15)
  - 태그 체계 확인 — `READY`(변경 목록 · 재평가 기간에 처음 본 기사 수) · `UNCHANGED` · `INVALID_TAG_SET`(문제)
  - 태그 체계 활성화 — `ACTIVATED`(새 버전 ID · 번호 · 변경 목록 · 재평가 대기에 넣은 수) · `UNCHANGED` · `INVALID_TAG_SET`(문제)
  - 기준 미리보기 — `PREVIEWED`(버전 · 지금 값 · 제안 값 · 경고 · 평가 수 · 판정이 달라지는 평가 수 · 두 기준의 평가 현황) · `UNCHANGED`(버전 · 지금 값)
  - 기준 변경 — `CHANGED`(버전 · 이전 값 · 새 값 · 경고) · `UNCHANGED`(버전 · 지금 값)
  - 빠진 태그 채움 확인 — `READY`(덮을 범위 · 평가 수 · 버전별 수)
  - 빠진 태그 채움 요청 — `REQUESTED`(기록한 채움) · `ALREADY_OPEN`(끝나지 않은 채움)

  판별자와 속성의 조합은 응답 레코드의 정적 팩토리 하나에서만 만든다. 응답의 소스 · 피드 · 태그 체계 버전 ID 는 JSON 수이고, 이벤트 발행 ID 는 UUID 문자열이다. 여러 발행을 한 번에 다루는 조작은 발행마다 판별자 하나를 단 항목(`id` · `result`)의 목록을 낸다
- **admin-R2** — 대상이 없으면 404 문제 응답이다: 없는 소스 · 피드(`source` 의 공개 예외), 경로의 ID 가 1 미만, 카탈로그에 없는 프리셋, 프리셋 소스의 운영자 원문(조회 · 수정), 없는 태그 체계 버전의 원문 · 평가 현황 · 평가 목록 · 표본(질의 `version` 의 1 미만도 경로의 ID 와 같다), 없는 평가(경로의 ID 1 미만 포함). 없는 소스 · 피드는 이 모듈의 조언이 앱 전체 조언보다 먼저 받는다 — 처리하지 못한 예외(500)가 되지 않는다
- **admin-R3** — 요청의 문제는 그 상태의 문제 응답이다: 본문 형식 · 원문 없음 400, 수집 주기의 하한 미만 400(설명 "수집 주기는 {하한}분 이상이다" — 하한은 `SourceAdministration.MINIMUM_POLL_INTERVAL_MINUTES`), 다른 요청이 먼저 바꿈 409(platform-R12), 이벤트 발행 다시 처리 · 폐기의 ID 형식 오류 · `ids` 없음 · 빈 원소 400, 한 번에 받는 발행 수 초과 400(설명 "한 번에 {상한}건까지 처리한다" — 상한은 `EventPublicationAdministration.MAX_BATCH`, 넘었는지는 공개 API 가 판정한다), 태그 체계 확인 · 활성화의 재평가 기간 범위 밖 400(설명 "재평가 기간은 0일에서 {상한}일까지다" — 상한은 `TagSetAdministration.MAX_REEVALUATION_DAYS`, 범위는 공개 API 가 판정한다), 빠진 태그 채움 확인 · 요청의 채울 기간 범위 밖 400(설명 "채울 기간은 1일에서 {상한}일까지다" — 상한은 `TagBackfillAdministration.MAX_DAYS`, 범위는 공개 API 가 판정한다) · `days` 없음 400, 평가 현황 · 보도량 · 평가 목록 · 표본 · 기준 미리보기의 날짜 형식 400(설명 "날짜는 yyyy-MM-dd 형식이다: {값}") · 없는 시간대 400("없는 시간대다: {값}") · 기간 범위 밖 400("기간은 1일에서 {상한}일까지다" — 상한은 `EvaluationPeriod.MAX_DAYS`, 범위는 공개 타입이 판정한다 — 하루가 통째로 없는 날도 여기다) · 활성 태그 체계에 없는 주제 400("활성 태그 체계에 없는 주제다: {키, …}" — 키는 사전 순), 평가 목록의 고른 버전에 없는 태그 400("그 태그 체계 버전에 없는 태그다: {종류} {키}") · `tagKind` 와 `tag` 가운데 하나만 400("tagKind 와 tag 는 함께 준다") · 커서 둘 가운데 하나만 400("afterEvaluatedAt 과 afterId 는 함께 준다") · 커서 시각 형식 400("시각은 ISO-8601 형식이다: {값}") · 커서 평가 ID 1 미만 400("평가 ID 는 1 이상이다: {값}") · 쪽 크기 범위 밖 400("한 번에 {상한}건까지 본다" — 상한은 `EvaluationReview.MAX_PAGE_SIZE`), 표본 크기 범위 밖 400("표본은 1건에서 {상한}건까지다" — 상한은 `EvaluationReview.MAX_SAMPLE_SIZE`) · 씨앗 범위 밖 400("씨앗은 0에서 {상한}까지다" — 상한은 `EvaluationReview.MAX_SEED`), 태그 기준 · 저장 기준 범위 밖 400("태그 기준 · 저장 기준은 0 이상 1 이하, 소수 셋째 자리까지다" — 범위는 공개 API 가 판정한다) · 경로의 태그 종류가 `TOPIC` · `REGION` 밖 400, 질의 매개변수 없음 · 형식 오류 400. 빈 `ids` 는 200 빈 결과다. 끄기 · 주기 변경 · 소스 숨기기 · 다시 보이기 · 피드 다시 보이기 · 소스 삭제는 성공하면 204 다. 삭제는 버전 조건이 없어 409 가 없다. 소스 목록 응답은 수집 주기의 하한을 함께 낸다

### 수집 현황

- **admin-R4** — 수집 현황 응답은 `source` 의 운영 목록과 `article` 의 엔트리 집계를 옮기기만 한다:
  - 숨긴 소스 · 숨긴 피드는 싣지 않는다. 소스 행의 개수는 그 소스의 숨기지 않은 피드 전부(꺼진 피드 · 프로필에 없는 피드 포함)의 칸별 합(`EntryStatistics.combined`)이고, 피드 행은 그 피드의 칸별 개수(`EntryStatistics.feed`)다. 칸은 집계 결과의 칸과 같은 순서로 짝짓는다
  - 비율은 `EntryCounts` 가 낸 값을 옮긴다 — 다시 나누지 않고, 엔트리가 없으면 `null` 이다(article-R14)
  - 피드 상태의 기준값과 재판정 간격은 `SourceAdministration.FEED_STATUS_CRITERIA` 에서 오고, 무소식 기간은 시간 수 · 재판정 간격은 분 수로 낸다
  - 피드 행의 수집 상태와 마지막 실패는 `FeedHealthView` 를 옮긴다 — 마지막 실패(`LastFailureView`)는 ID 가 없는 값이라 그대로 싣는다

### 평가 현황

- **admin-R5** — 평가 현황 · 주제별 보도량 · 대기 건수 응답은 `evaluation` 의 결과를 옮기기만 한다:
  - 버전 · 활성 버전 ID 만 JSON 수로 옮기고, 수 · 비율 · 칸 · 주제의 값 레코드는 ID 가 없는 값이라 그대로 싣는다
  - 비율은 공개 결과의 값이다 — 다시 나누지 않고, 평가가 없으면 `null` 이다(evaluation-R29)
  - 날짜 범위의 상한(`maxDays`)은 `EvaluationPeriod.MAX_DAYS` 에서 온다
  - 컨트롤러는 트랜잭션을 열지 않는다 — 집계의 읽기 전용 트랜잭션은 공개 API 가 연다

### 평가 검수

- **admin-R6** — 평가 검수 응답은 `evaluation` 의 결과에 기사 정보를 맞붙인다(정본 `ReviewArticles`):
  - 쪽 · 표본의 기사 ID 를 모아 `article` 의 `ArticleDirectory.findAll` 을 한 번, 그 엔트리의 피드 ID 를 모아 `source` 의 `FeedLabels.labels` 를 한 번 부른다
  - 목록 · 표본 행은 제목과 출처 이름(엔트리 순서, 같은 이름은 한 번, 이름이 없는 피드는 뺀다)을 싣는다. 상세는 제목 · 요약문 · 정규 URL 과 엔트리마다 출처 이름 · 섹션 · RSS 카테고리를 싣는다 — 요약문 원문은 운영자 화면에만 보인다([ADR-0068](../adr/0068-rss-summaries-shown-only-on-operator-screens-and-users-get-processed-information.md))
  - 그새 지운 기사는 기사 정보가 `null` 이다 — 평가 결과는 그대로 싣는다
  - 평가 · 버전 ID 는 JSON 수로 옮기고, 판정 · 분포의 값 레코드는 그대로 싣는다

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
| [0050](../adr/0050-operator-resubmits-once-or-discards-stranded-event-publications-read-from-registry.md) | 이벤트를 읽을 수 없는 발행은 재제출 전에 거르고, 상한에 이른 · 읽을 수 없는 발행은 운영자가 즉시 1회 재제출하거나 기록 없이 폐기하며, 목록 · 폐기는 `platform` 이 레지스트리 테이블을 직접 다룬다 |
| [0051](../adr/0051-operator-hides-or-deletes-sources-and-feeds-and-deletion-removes-exclusive-articles.md) | 운영자는 소스 · 피드를 숨기거나 삭제하고, 숨김은 끄고 남기며 삭제는 그 소스 · 피드만 가진 기사까지 지운다 |
| [0062](../adr/0062-tag-set-versions-from-operator-text-activated-forward-with-key-lineage-and-recent-reevaluation.md) | 태그 체계는 운영자 원문으로 새 버전을 만들어 앞으로만 켜고, 키 계보를 원문에 적으며, 켤 때 정한 일수 안에 처음 본 기사를 재평가 대기에 넣는다 |
| [0068](../adr/0068-rss-summaries-shown-only-on-operator-screens-and-users-get-processed-information.md) | RSS 요약문은 운영자 화면에만 보이고, 인스턴스 사용자와 외부 API 에는 가공한 정보만 낸다 |
