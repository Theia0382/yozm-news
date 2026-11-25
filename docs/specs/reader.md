# reader 모듈 Spec

공통 구조와 쓰는 규칙은 [specs/README.md](README.md)에 있다.

## 1. 책임과 범위

`reader` 는 독자 화면(인스턴스 사용자가 뉴스 내용을 보는 화면)이 부르는 HTTP API 를 낸다. 각 모듈의 공개 조회를 엮어 응답 모양을 정하고, 데이터 · 판정을 갖지 않는다([ADR-0192](../adr/0192-reader-screens-are-composed-by-the-reader-module-over-module-public-queries.md)).

- **경계** — 뉴스 내용(기사 · 사건 · 스토리라인 · 사용자 스토리라인의 정리 · 다이제스트 · 변화 리포트 · 관심사 히스토리)을 보이는 사용자 화면의 응답은 이 모듈이 정한다. 사용자가 정해 두는 것(계정 · 키 · 관심 설정 · 구독 · 알림)의 화면은 그 데이터를 가진 모듈이 연다
- **정리된 사용자 스토리라인** — 세션 사용자의 정리된 사용자 스토리라인의 목록(독자 화면의 "사건 흐름" 탭), 하나(개인 흐름 화면 — 사용자 스토리라인 전체의 정리 · 추적 대상 꼭대기마다의 정리와 상태 · 정리에 쓴 토큰), 그 스토리라인에 묶인 지난 다이제스트 항목을 낸다(reader-R13 · R14). 공용 스토리라인의 목록은 내지 않는다 — 운영자 화면(`admin`)에 있다
- **스토리라인 타임라인** — 스토리라인 하나의 값, 상위 · 하위 · 앞 스토리라인 · 이어진 스토리라인, 사건마다 대표 기사와 그 사용자가 받은 날짜를 낸다(reader-R3 ~ R5)
- **스토리라인 카드** — 스토리라인 ID 여럿의 표시 정보를 낸다 — 추적 중인 사용자 스토리라인 목록처럼 ID 만 가진 화면이 쓴다(reader-R6)
- **기사 정보** — 사용자 화면의 기사는 제목 · 출처 이름 · 발행 시각 · 원문 링크다(reader-R7)
- **관심사 히스토리** — 세션 사용자의 기간별 주제 관심 · 새로 생긴 관심 · 사라진 관심 · 주제 구독 변경과, 정리된 사용자 스토리라인마다의 기간별 관심 · 관심이 생긴 스토리라인 · 사라진 스토리라인을 고른 기간(주 · 월)으로 낸다(reader-R8)
- **다이제스트** — 세션 사용자의 가장 늦은 날짜의 다이제스트와 수동 생성 상태, 날짜 하나의 다이제스트, 다이제스트가 있는 날짜 목록을 내고, 수동 생성 요청을 받는다. 항목을 묶은 스토리라인이 정리돼 있으면 그 정리의 제목을 붙인다(reader-R9 ~ R11)
- **구독 제안** — 세션 사용자에게 지금 제안할 주제 중분류의 이름 · 대분류 이름과 태그 체계 버전을 낸다 — 다이제스트 홈의 제안 카드가 쓴다(reader-R12). 수락 · 거절은 `user` 의 처리기가 받는다
- **변화 리포트** — 세션 사용자의 종류 하나(주간 · 월간)의 리포트 목록과 리포트 한 편(요약 · 직전 리포트 · 숨긴 섹션 수 · 사용량 · 섹션마다 제목 · 본문 · 인용 · 그 갈래의 숫자)을 낸다. 인용 · 숫자 줄이 가리키는 스토리라인에는 지금 꼭대기를 가진 정리된 사용자 스토리라인의 제목과 대표 기사를, 사건에는 대표 기사를 붙인다. 실패한 리포트를 다시 만들고, 관심 설정이 안내할 생성 조건의 수를 낸다(reader-R15 ~ R17)

하지 않는 것:

- 판정 · 저장을 하지 않는다 — 그 지식을 가진 모듈의 공개 조회에 맡긴다. 리포트의 비교 · 풀기 · 숨기기 · 기간 날짜는 `report`(report-R11 · R12 · R25 · R26), 받은 소식의 뜻은 `digest`(digest-R13), 무엇이 정리된 사용자 스토리라인인지는 `user`(user-R73), 스토리라인에 묶인 지난 항목은 `digest`(digest-R53), 상태는 `storyline`, 대표 기사는 `event`, 출처 이름 엮기는 `article`(article-R17)이 정한다
- 판정 근거(소속의 점수 · 관계의 확률 · 물은 기록 · 기준)와 보도 강도 · 중요도를 내지 않는다 — 운영자 화면(`admin`)의 것이다
- RSS 요약문 · 피드 섹션 · RSS 카테고리를 내지 않는다([ADR-0068](../adr/0068-rss-summaries-shown-only-on-operator-screens-and-users-get-processed-information.md))
- 사용자를 인증하지 않는다 — 세션 · 키 관문은 `account` 의 보안 구성이 한다([account Spec](account.md#화면용-api-의-공개와-세션-사용자) account-R5 · R6)

다른 모듈과의 의존과 이벤트 흐름은 [overview.md §5](../overview.md#5-모듈-지도)에 있다.

## 2. 용어

스토리라인 · 사용자 스토리라인 · 정리 · 받은 소식은 [overview.md §8](../overview.md#8-용어)에 있다. 독자 화면은 스토리라인을 "사건 흐름"(탭 · 화면 제목) · "흐름"(본문)이라 부른다([ADR-0292](../adr/0292-reader-screens-call-a-storyline-an-event-flow.md)).

| 용어 | 뜻 |
|---|---|
| 개인 흐름 화면 | 정리된 사용자 스토리라인 하나를 보이는 화면 — 주소 `/user-storylines/{id}`(reader-R13) |
| 헤드 | 정리된 사용자 스토리라인의 손잡이 — 추적 대상 가운데 뒤 스토리라인이 없는 꼭대기로 그 스토리라인에만 든다([user Spec](user.md#2-용어)). 흐름 추적 버튼의 대상이고 관심사 히스토리의 값을 읽는 자리다(reader-R13 · R8) |
| 카드 | 스토리라인 하나의 표시 정보 — 대표 기사 · 상태 · 사건 수 · 기사 수 · 첫 · 마지막 기사 시각 · 상위(reader-R3) |
| 앞 스토리라인 · 이어진 스토리라인 | 보고 있는 스토리라인을 낳은 스토리라인 · 보고 있는 스토리라인이 낳은 스토리라인 — 이어짐 관계의 두 방향(reader-R4) |

## 3. 공개 인터페이스

모듈 최상위 패키지 `net.yozmnews.reader` 에 공개 타입이 없다. 다른 모듈이 부르는 공개 API 는 없다.

HTTP 엔드포인트 — 모두 세션이 필요하고, 리포트의 생성 조건(`GET /api/reports/conditions` — 키 없이 여는 표시 `@AllowedWithoutLlmKey`)밖은 키 관문 뒤다(account-R5). 요청 방어 표시(`@RequestLimited` · `@HumanChecked`)도 없다(account-R32):

| 메서드 · 경로 | 용도 |
|---|---|
| `GET /api/user-storylines` | 세션 사용자의 정리된 사용자 스토리라인 — 마지막으로 정리한 순 |
| `GET /api/user-storylines/{id}` | 정리된 사용자 스토리라인 하나 — 사용자 스토리라인 전체의 정리 · 꼭대기마다의 정리와 상태 · 사용량. ID 가 1 보다 작거나 그 사용자의 정리된 사용자 스토리라인이 아니면 404 |
| `GET /api/user-storylines/{id}/items` | 그 스토리라인에 묶인 지난 다이제스트 항목 한 쪽 — 질의 `beforeDate`(YYYY-MM-DD) · `beforePosition`(다음 쪽 커서 — 함께) |
| `GET /api/storylines/{id}` | 스토리라인 하나의 타임라인 — 카드 · 상위 · 하위 · 앞 스토리라인 · 이어진 스토리라인 · 사건마다 대표 기사와 받은 날짜. 합쳐진 ID 면 남는 스토리라인을 낸다 |
| `GET /api/storyline-cards` | 스토리라인 ID 여럿의 카드 — 질의 `ids`(쉼표로 이은 ID, 1 ~ 100개) |
| `GET /api/digests/latest` | 세션 사용자의 홈 — 가장 늦은 날짜의 날짜 보기(없으면 null)와 수동 생성 상태(계정이 없으면 null — 오늘 일정 생성 시각 · 사용자 시간대 · 해결되지 않은 마지막 실패를 함께) |
| `GET /api/digests/{date}` | 날짜 하나의 다이제스트 — 경로 `date`(YYYY-MM-DD). 형식이 틀리면 400, 그 날짜의 다이제스트가 없으면 404 |
| `GET /api/digests` | 다이제스트가 있는 날짜 한 쪽 — 질의 `before`(YYYY-MM-DD, 다음 쪽은 앞 응답의 `next`) |
| `POST /api/digests` | 수동 생성 요청 — 판별자 200(받음 · 진행 중 · 오늘 횟수 소진 · 쓸 모델 없음) |
| `GET /api/topic-suggestions` | 세션 사용자에게 지금 제안할 주제 — 태그 체계 버전 ID(계정이 없으면 null) · 중분류마다 키 · 이름 · 대분류 이름 |
| `GET /api/reports` | 세션 사용자의 변화 리포트 한 쪽 — 질의 `kind`(`WEEK` · `MONTH`, 필수) · `before`(1 이상, 다음 쪽은 앞 응답의 `next`). 종류가 없거나 틀리면 · `before` 가 1 보다 작으면 400 |
| `GET /api/reports/{id}` | 변화 리포트 한 편 — 요약 · 직전 리포트 · 숨긴 섹션 수 · 사용량 · 섹션(제목 · 본문 · 인용 · 숫자) · 남은 다시 만들기 수(실패일 때만). ID 가 1 보다 작거나 그 사용자의 리포트가 아니면 404 |
| `GET /api/reports/conditions` | 변화 리포트의 생성 조건 — 주간의 READY 다이제스트 날짜 수 · 월간의 READY 주간 리포트 수. 키가 없어도 연다 |
| `POST /api/reports/{id}/regeneration` | 실패한 변화 리포트 다시 만들기 — `REQUESTED` · `NOT_FAILED`(+지금 상태) · `LIMIT_REACHED` · `NO_USABLE_MODEL`. ID 가 1 보다 작거나 그 사용자의 리포트가 아니면 404 |
| `GET /api/interest-history` | 세션 사용자의 관심사 히스토리 — 질의 `span`(`WEEKS_13` · `WEEKS_26` · `WEEKS_52` · `MONTHS_12` · `MONTHS_24` · `MONTHS_36`, 없으면 `digest` 의 `InterestHistorySpan.DEFAULT`) |

요청 · 응답 형식의 정본은 [`api/openapi.json`](../../api/openapi.json)이다([ADR-0020](../adr/0020-screen-api-contract-from-committed-openapi-spec.md)). 요청의 문제는 400 · 404 문제 응답이다([ADR-0044](../adr/0044-screen-api-results-as-flat-discriminated-200-and-rfc9457-problems.md)).

## 4. 내부 구조

### 개관

| 패키지 | 책임 |
|---|---|
| (최상위) | 모듈 선언 — 의존을 `storyline` · `event` · `article` · `digest` · `report` · `tag` · `user` · `platform` 으로 둔다(`package-info`) |
| `adapter/in/web` | 독자 화면 컨트롤러, 응답 레코드, 요청 매개변수 읽기, 기사 정보 · 대표 기사 · 받은 날짜 맞붙이기, 관심사 히스토리 · 구독 제안의 주제 이름 맞붙이기, 다이제스트 인용 기사의 정보와 항목의 정리 맞붙이기, 정리된 사용자 스토리라인의 응답 |

협력: 컨트롤러는 `storyline` 의 공개 조회 `StorylineDirectory`(`find` · `findAll` · `events` · `relationsOf` · `topsOfStorylines`), `user` 의 공개 조회 `UserStorylineSummaries`(`shown` · `find` · `ofTops`), `event` 의 공개 조회 `EventDirectory`(`representativesOfAll` · `articleIdsOf`), `article` 의 공개 조회 `ArticleDirectory`(`headlines`), `digest` 의 공개 조회 `ReceivedArticles`(`receivedAmong`) · `InterestHistory`(`recent`) · `TopicSuggestions`(`suggestionsOf`) · `DigestDirectory`(`latest` · `on` · `dates` · `requestStatus` · `itemsOfStorylines`)와 공개 API `DigestRequests`(`request`), `report` 의 공개 조회 `ReportDirectory`(`list` · `find`) · 공개 API `ReportRequests`(`regenerate`) · 생성 조건 값 `ReportConditions`(`current`), `tag` 의 공개 조회 `TagSetDirectory`(`names` — `TagSetNames` 의 `topicName` · `categoryOf` · `topicOrder`)만 부르고(`user` 의 그 밖의 것은 값 타입 `TopicSubscriptionAction` · `GenerationFailureCause` 만 쓴다), 결과를 응답 레코드의 정적 팩토리로 옮긴다. 오류 응답은 `platform` 의 문제 응답 형식이다. `application` · `domain` 이 없다 — 판단이 없다([ADR-0192](../adr/0192-reader-screens-are-composed-by-the-reader-module-over-module-public-queries.md)).

역할: 모든 구성 요소가 화면 역할(`@WebRole`)이다(platform-R9).

### 들어오는 쪽

| 진입점 | 구성 요소 | 계기 | 내용 |
|---|---|---|---|
| 스토리라인 | `StorylineScreenController` | `GET /api/storylines/{id}` · `GET /api/storyline-cards` | 타임라인 · 카드(reader-R3 ~ R7). 트랜잭션을 열지 않는다 — 여러 공개 조회는 한 스냅숏이 아니다 |
| 정리된 사용자 스토리라인 | `UserStorylineScreenController` | `GET /api/user-storylines` · `GET /api/user-storylines/{id}` · `GET /api/user-storylines/{id}/items` | 목록 · 하나 · 지난 다이제스트 항목(reader-R13 · R14). 트랜잭션을 열지 않는다 |
| 다이제스트 | `DigestScreenController` | `GET /api/digests/latest` · `GET /api/digests/{date}` · `GET /api/digests` · `POST /api/digests` | 홈 · 날짜 · 날짜 목록 · 수동 생성 요청(reader-R9 ~ R11). 트랜잭션을 열지 않는다 — 수동 생성 요청은 트랜잭션 밖에서 불러야 한다 |
| 변화 리포트 | `ReportScreenController` | `GET /api/reports` · `GET /api/reports/{id}` · `POST /api/reports/{id}/regeneration` · `GET /api/reports/conditions` | 종류 하나의 목록 · 한 편 · 다시 만들기 · 생성 조건(reader-R15 ~ R17). 트랜잭션을 열지 않는다 |
| 관심사 히스토리 | `InterestHistoryController` | `GET /api/interest-history` | 세션 사용자의 관심사 히스토리(reader-R8). 트랜잭션을 열지 않는다 |
| 구독 제안 | `TopicSuggestionController` | `GET /api/topic-suggestions` | 세션 사용자의 구독 제안(reader-R12). 트랜잭션을 열지 않는다 |

`adapter/in/web` 의 그 밖의 타입: 응답 `UserStorylineResponses`(정리된 사용자 스토리라인의 응답 레코드 묶음 — `ListResponse` · `CardResponse` · `DetailResponse` · `TopResponse` · `UsageResponse` · `ItemsResponse` · `ItemResponse` · `ItemCursorResponse` · `RefResponse`) · `InterestUserStorylineResponse` · `StorylineCardResponse` · `StorylineTimelineResponse` · `StorylineRefResponse` · `TimelineEventResponse` · `StorylineCardsResponse` · `StorylineCardEntryResponse` · `NewsArticleResponse` · `InterestHistoryResponse` · `InterestSpanChoicesResponse` · `InterestPeriodResponse` · `TopicSubscriptionStepResponse` · `InterestTopicResponse` · `InterestLevelResponse` · `DigestHomeResponse` · `DigestDayResponse` · `ShownDigestResponse` · `DigestSectionResponse` · `DigestCitationResponse` · `DigestAttemptResponse` · `DigestUsageResponse` · `DigestDatesResponse`(중첩 `DigestDateResponse`) · `DigestRequestStatusResponse` · `DigestUnresolvedFailureResponse` · `DigestRequestResponse` · `TopicSuggestionsResponse` · `TopicSuggestionResponse` · `ReportListResponse` · `ReportSummaryResponse` · `ReportDetailResponse` · `ReportUsageResponse` · `ReportSectionResponse` · `ReportCitationResponse` · `ReportStorylineResponse` · `ReportUserStorylineCountResponse` · `ReportInterestsResponse` · `ReportTopicLevelResponse` · `ReportUserStorylineLevelResponse`, 보조 `ScreenRequests`(요청 매개변수 읽기와 그 400 · 404) · `ScreenArticles`(기사 정보 맞붙이기) · `StorylineTitles`(사건의 대표 기사 맞붙이기) · `ReceivedMarks`(사건마다 받은 날짜 맞붙이기) · `UserStorylineRefs`(꼭대기 → 정리된 사용자 스토리라인 — 다이제스트와 변화 리포트가 함께 쓴다) · `ReportNames`(변화 리포트의 스토리라인 · 사건 이름 맞붙이기). 보조 맞붙이기는 스프링 구성 요소가 아니라 컨트롤러가 만든다.

### `application`

해당 없음.

### `domain`

해당 없음.

### 나가는 쪽

해당 없음 — 다른 모듈의 공개 조회만 부른다.

## 5. 규칙과 불변식

### 스토리라인 목록

- **reader-R1** — (폐지 — 공용 스토리라인의 독자 목록을 내지 않는다. 독자의 목록은 reader-R13)
- **reader-R2** — (폐지 — reader-R1 과 같다)

### 스토리라인 타임라인

- **reader-R3** — 카드: 스토리라인 값(`StorylineView` — 상태 · 닫은 사건 집합의 사건 수 · 기사 수 · 첫 · 마지막 기사 시각 · 상위 ID)과 대표 기사(대표 사건의 대표 기사 — reader-R7)다. 대표 사건이 합쳐지거나 지워진 뒤 다음 잇기 회차까지는 대표 기사가 없어 제목이 비어 있다(`EventDirectory.representativesOfAll` 이 합쳐진 · 지워진 사건을 뺀다). 보도 강도 · 소스 수 · 중요도는 싣지 않는다
- **reader-R4** — 타임라인: 경로 ID 가 1 미만이거나 `StorylineDirectory.find` 가 없으면 404 다. 합쳐진 스토리라인이면 남는 스토리라인을 낸다 — 응답의 ID 가 경로 ID 와 다르다(storyline-R21). 사건은 `StorylineDirectory.events` 의 순서(첫 기사 시각 · 사건 ID)와 직접 소속인지를 그대로 싣고, 사건마다 기사 수 · 첫 · 마지막 기사 시각 · 대표 기사를 싣는다(대표는 사건 기사 전체에서 고른 것 — event-R14). 관계는 `relationsOf` 를 보고 있는 스토리라인 S 기준으로 나눈다 — 포함이고 S 가 앞이면 하위, 이어짐이고 S 가 뒤면 앞 스토리라인, S 가 앞이면 이어진 스토리라인이다. 상위는 `StorylineView.parent` 하나로 정하고(storyline-R25 — 상위는 하나) 포함의 S 가 뒤인 고리는 목록에 담지 않는다. 상위 · 상대는 `findAll` 한 번으로 읽어 ID(합쳐졌으면 남는 쪽) · 대표 기사 제목 · 상태를 싣고, 그새 없어진 상대는 빼며, 한 목록에 같은 ID 는 한 번이다. 판정 근거는 싣지 않는다
- **reader-R5** — 받은 날짜: 타임라인 사건의 기사 ID 를 `EventDirectory.articleIdsOf` 로 얻어 `ReceivedArticles.receivedAmong` 에 세션 사용자로 묻고(`ReceivedArticles.MAX_ARTICLES` 개씩 나눠), 사건마다 그 기사들의 받은 날짜 가운데 가장 이른 날짜를 단다. 받은 기사가 없는 사건은 비어 있다. 받은 소식의 뜻은 digest-R13 이다([ADR-0193](../adr/0193-the-per-user-storyline-information-is-the-received-news-counted-from-the-digest-shown-for-each-date.md))

### 스토리라인 카드

- **reader-R6** — 카드 여럿: `ids` 는 받은 값의 수(같은 ID 포함)가 1 ~ 100개(`StorylineDirectory.MAX_PAGE_SIZE`)이고 모두 1 이상이어야 한다 — 아니면 400 이다. 같은 ID 는 처음 자리 한 번만 본다. `StorylineDirectory.findAll` 의 결과를 받은 ID 순으로 싣고 행마다 받은 ID 와 카드(reader-R3)를 싣는다 — 합쳐진 ID 면 카드의 ID 가 남는 스토리라인이고, 없는 ID 는 빠진다

### 관심사 히스토리

판단의 이유는 [ADR-0227](../adr/0227-the-interest-history-shows-subtopic-interest-and-the-users-own-topic-subscription-changes-only.md) · [ADR-0229](../adr/0229-interest-history-periods-are-monday-weeks-and-calendar-months-in-the-users-zone-and-the-query-takes-a-span-or-reference-times.md) · [ADR-0228](../adr/0228-consumer-queries-scoped-by-a-user-argument-may-be-called-on-screen-paths-with-the-session-user.md)에 있다. 기간 · 관심 · 변화의 판단은 `digest` 의 것이다(digest-R34 ~ R37). 스토리라인을 더한 이유는 [ADR-0293](../adr/0293-the-interest-history-also-shows-the-interest-of-summarized-flows.md)에 있다.

- **reader-R8** — 관심사 히스토리: 세션 사용자로 `InterestHistory.recent` 를 부른다. 질의 `span` 이 없으면 `InterestHistorySpan.DEFAULT`, 열거 값이 아니면 400 이다. 응답은 고른 기간(`span`), 단위마다 고를 수 있는 기간과 단위를 바꿨을 때의 기간(`spans[]` — `InterestHistorySpan.Unit` 순, `defaultOf`), 기간(`periods[]` — 시간 순, 마지막이 진행 중), 주제(`topics[]`), 정리된 사용자 스토리라인(`userStorylines[]`)을 싣는다. 계정이 없으면(조회가 빈 값) 기간 · 주제 · 스토리라인이 비어 있다
  - 기간마다 첫날 · 마지막 날(사용자 시간대) · 진행 중 여부 · 새로 생긴 관심 · 사라진 관심 · 사용자가 직접 바꾼 주제 구독(시각 · 더함 / 뺌 · 덮는 중분류)을 싣는다. 키는 태그 체계 순이다
  - 주제는 기간 가운데 한 번이라도 관심 값이 있는 중분류이고 `TagSetNames.topicOrder()` 순이다. 주제마다 키 · 이름 · 대분류 이름(`TagSetNames.categoryOf` → `topicName`) · 기간마다 관심 칸(`level` — 신호가 없으면 null) · 드러남(`prominent` — digest-R36)을 싣는다. 이름은 조회가 낸 태그 체계 버전(`InterestTimeline.tagSet`)으로 읽는다 — 그 버전에 없는 키는 불변식이 깨진 것이라 `IllegalStateException` 이다
  - 스토리라인은 지금 정리된 사용자 스토리라인이다(`UserStorylineSummaries.shown` 의 순서). 스토리라인마다 ID · 정리의 제목 · 기간마다 관심 칸(`level` — 그 기간 `InterestPoint.storylineLevels` 의 헤드 값, 없으면 null)을 싣는다. 헤드는 그 스토리라인에만 들어 그 값과 생김 · 사라짐이 그 스토리라인 하나의 것이다. 기간마다 관심이 생긴 스토리라인(`enteredUserStorylines`) · 사라진 스토리라인(`leftUserStorylines`)은 헤드가 그 기간의 `storylineEntered` · `storylineLeft` 에 든 스토리라인의 ID 다 — 정리되지 않은 꼭대기의 변화는 싣지 않는다
  - 화면은 주제 표와 스토리라인 표를 따로 그리고, 스토리라인의 제목은 개인 흐름 화면으로 잇는다. 주제 행이 없어도 스토리라인이 있으면 스토리라인 표를 보인다
  - 판정 · 관심의 계산은 하지 않는다 — θ 와 스토리라인의 기준은 응답에 없다

### 구독 제안

판단의 이유는 [ADR-0249](../adr/0249-topic-suggestions-show-as-a-card-on-the-digest-home-and-accepting-uses-the-tag-preference-save.md) · [ADR-0245](../adr/0245-a-topic-suggestion-is-made-when-the-current-interest-of-an-unsubscribed-subtopic-reaches-its-own-threshold.md) · [ADR-0248](../adr/0248-the-digest-interests-and-topic-suggestion-queries-may-be-called-on-screen-paths-with-the-session-user.md)에 있다. 제안의 판정은 `digest` 의 것이다(digest-R41 · R42).

- **reader-R12** — 구독 제안: `GET /api/topic-suggestions` 는 `digest` 의 `TopicSuggestions.suggestionsOf` 를 세션 사용자로 부른다. 빈 값(계정 없음)이면 `tagSetId` null · `topics` 빈 목록이다. 있으면 `tagSetId` 는 그 버전 ID, `topics` 는 낸 순서 그대로 중분류마다 키 · 이름 · 대분류 이름(`TagSetNames.categoryOf` → `topicName`)이다. 이름은 제안이 낸 버전으로 읽는다 — 그 버전에 없는 키는 불변식이 깨진 것이라 `IllegalStateException` 이다. 관심 값 · 기준은 싣지 않는다
  - 화면(다이제스트 홈)은 제안이 있을 때만 카드를 그린다. 수락은 지금 태그 선호에 그 중분류를 더해 `user` 의 태그 선호 저장(`PUT /api/interests/tags` — 이 응답의 `tagSetId`)으로, 거절은 `user` 의 `POST /api/interests/declined-suggestions` 로 보낸다 — 선호와 제안의 버전이 다르면 수락을 보내지 않고 둘을 다시 읽는다([user Spec](user.md#구독-제안-거절) user-R64)

### 정리된 사용자 스토리라인

판단의 이유는 [ADR-0289](../adr/0289-shown-flows-are-user-storylines-with-a-summary-which-the-summary-job-creates-and-removes.md) · [ADR-0290](../adr/0290-the-readers-storyline-tab-shows-the-users-summarized-flows-and-the-tracking-list-has-one-row-per-user-storyline.md) · [ADR-0291](../adr/0291-tokens-spent-on-a-summary-are-shown-on-the-personal-flow-screen-as-the-last-summary-and-the-running-total.md) · [ADR-0292](../adr/0292-reader-screens-call-a-storyline-an-event-flow.md)에 있다. 무엇이 정리된 사용자 스토리라인인지와 정리의 글은 `user` 의 것이다([user Spec](user.md#사용자-스토리라인의-정리) user-R73).

- **reader-R13** — 정리된 사용자 스토리라인의 목록과 하나: `GET /api/user-storylines` 는 `UserStorylineSummaries.shown` 을 세션 사용자로 불러 낸 순서(마지막으로 정리한 순) 그대로 카드를 싣는다 — 사용자 스토리라인 ID · 사용자 스토리라인 전체의 제목 · 흐름 요약 · 최근 전개 · 추적 중인가 · 헤드의 스토리라인 ID(`storylineId`) · 추적 대상 수 · 마지막 정리 시각. `GET /api/user-storylines/{id}` 는 `find` 로 읽는다 — ID 가 1 보다 작거나 빈 값(다른 사용자의 것 · 정리 없음)이면 404 다. 응답은 카드의 칸에 꼭대기마다의 글(`tops[]`) · 사용량(`usage`) · 처음 정리 시각을 더한다
  - 헤드는 `UserStorylineSummaryView.head` 다([user-R73](user.md#사용자-스토리라인의-정리) — 헤드를 저장하기 전의 개체의 대체도 거기 한 곳). 흐름 추적 버튼이 그 ID 로 추적 · 해제하고 추적 상태를 읽는다 — 헤드는 그 스토리라인에만 들어 해제가 다른 갈래를 끄지 않는다([user-R52 · R67](user.md#사용자-스토리라인))
  - 꼭대기는 추적 대상을 `StorylineDirectory.findAll` 로 읽어 낸 지금 스토리라인만 싣는다 — 합쳐졌으면 남는 쪽의 ID 이고 그새 없어진 스토리라인은 뺀다. 꼭대기마다 스토리라인 ID · 상태 · 글 셋(글 없는 꼭대기는 셋 다 null)이다
  - 사용량은 마지막 정리의 공급자 키 · 모델 ID · 입력 · 출력 토큰과 호출 수 · 모든 호출의 토큰 합이다. 금액으로 바꾸지 않는다
  - 화면: 탭은 카드마다 제목(개인 흐름 화면 링크) · 흐름 요약 · 최근 전개 · 정리 시각 · 갈래 수(둘 이상일 때) · 흐름 추적 버튼을 그리고, 정리된 사용자 스토리라인이 없으면 어떻게 생기는지 안내한다. 개인 흐름 화면은 사용자 스토리라인 전체의 정리, 꼭대기마다의 정리와 공용 타임라인 링크(꼭대기가 하나면 링크만, 글 없는 꼭대기는 대체 제목과 링크만), 지난 다이제스트 항목, 맨 아래 작은 줄 둘(마지막 정리의 모델 · 토큰, 호출이 둘 이상이면 모든 호출의 합)을 그린다
- **reader-R14** — 스토리라인에 묶인 지난 다이제스트 항목: `GET /api/user-storylines/{id}/items` 는 reader-R13 과 같이 스토리라인을 찾고(없으면 404) `DigestDirectory.itemsOfStorylines` 를 세션 사용자 · 그 스토리라인의 저장한 추적 대상으로 한 쪽 20개(`UserStorylineScreenController.ITEMS_PAGE_SIZE` — 측정 전 목표, `DigestDirectory.MAX_ITEMS` 이하)씩 부른다(digest-R53). 항목마다 다이제스트 날짜 · 종류 · 제목(없으면 null)을 싣고 다음 쪽 커서(`next` — `beforeDate` · `beforePosition`)를 그대로 낸다. 커서 두 값은 함께 오거나 함께 없다 — 하나만 오거나 날짜 꼴이 틀리거나 위치가 0 보다 작으면 400 이다(`ScreenRequests.itemCursor`). 화면은 항목을 그 날짜의 다이제스트 화면으로 잇는다

### 기사 정보

- **reader-R7** — 사용자 화면의 기사는 제목 · 출처 이름 · 발행 시각 · 원문 링크(정규 URL)다 — `article` 의 `ArticleDirectory.headlines` 를 한 응답에 한 번 부른다(출처 이름 엮기는 article-R17). 그새 지운 기사는 비어 있다

### 다이제스트

판단의 이유는 [ADR-0237](../adr/0237-the-digest-home-shows-the-latest-date-and-every-date-has-its-own-page-and-a-list.md) · [ADR-0238](../adr/0238-a-digest-date-shows-the-visible-digest-with-the-latest-attempt-and-the-list-holds-every-date-with-a-state.md) · [ADR-0239](../adr/0239-digest-screens-show-tokens-of-the-visible-digest-and-the-sum-of-every-attempt-of-the-date-without-prices.md) · [ADR-0240](../adr/0240-digest-failures-show-the-cause-action-and-the-code-and-never-the-failure-message.md) · [ADR-0262](../adr/0262-chosen-events-are-grouped-into-items-by-containment-tree-top-code-splits-top-stories-and-headlines-the-llm-writes-per-item-and-an-item-has-one-badge.md)(항목마다 배지 하나 · 출처 기사 목록)에 있다.

- **reader-R9** — 날짜 보기: `GET /api/digests/latest` 는 `DigestDirectory.latest` 와 `requestStatus` 를, `GET /api/digests/{date}` 는 `on` 을 세션 사용자로 부른다. 경로 `date` 는 연도 네 자리의 `YYYY-MM-DD` 꼴이고 달력의 날짜여야 한다 — 아니면 400(`ScreenRequests.date`), 그 날짜의 다이제스트가 없으면 404 다. 응답은 날짜 · 이웃 날짜 · 보이는 다이제스트(기간 · 모델 · 후보 수(사건 수) · 토큰 · 끝낸 시각 · 섹션) · 가장 최근 시도(상태 · 실패 코드 · 갈래 · 끝낸 시각) · 그 날짜의 사용량이다(digest-R38). 홈의 수동 생성 상태(digest-R40)는 사용자 시간대(IANA 이름) · 오늘 일정 생성 시각(아직 오지 않았을 때만, 아니면 null) · 해결되지 않은 마지막 실패(날짜 · 끝낸 시각, 없으면 null)를 함께 싣는다 — 화면은 그 시각을 사용자 시간대로 적고, 알림 화면의 실패 알림 본문은 날짜 · 끝낸 시각이 같은 알림에만 다시 만들기 링크를 단다([user-R62](user.md#화면-내-알림)). 실패 메시지는 싣지 않는다. 섹션마다 종류(개요 · 주요 뉴스 항목 · 헤드라인 항목) · 제목 · 본문(개요 · 주요 뉴스의 요약 · 헤드라인의 코멘트) · 선정 이유 하나(`reason` — 개요는 null, [digest-R48](digest.md#다이제스트-조회)) · 인용 · 항목의 사건 ID(`eventIds` — 위치 순, 첫 사건이 좋아요 / 싫어요의 항목 키, 개요는 빈 배열 — [feedback Spec](feedback.md#값과-기록) feedback-R2) · 항목을 묶은 스토리라인(`storylineId` — 스토리라인 포함 나무의 지금 꼭대기, 개요 · 스토리라인 없는 항목은 null — [digest-R38](digest.md#다이제스트-조회)) · 그 스토리라인의 정리(`userStorylines` — 그 꼭대기를 저장한 추적 대상으로 가진 정리된 사용자 스토리라인 모두의 ID 와 제목, 사용자 스토리라인 ID 순. 갈라지기 전 스토리라인이면 그 뒤 갈래의 것 여럿이고, 없으면 빈 배열)를 싣는다. 정리는 보이는 다이제스트의 서로 다른 `storylineId` 를 `UserStorylineSummaries.ofTops` 에 한 번의 상한(`MAX_TOPS`)씩 나눠 물어 붙인다. 스토리라인의 추적 상태는 싣지 않는다. 인용마다 기사 ID 와 기사 정보(reader-R7)만 싣고, 모든 섹션의 인용 기사 정보를 한 응답에 한 번 읽는다 — 그새 지운 기사의 인용은 뺀다
  - 화면은 주요 뉴스 · 헤드라인 항목마다 머리(제목 · 선정 이유 배지 — 구독 주제(`SUBSCRIBED`)와 개요는 배지가 없다 — 와 좋아요 / 싫어요 버튼 한 벌) · 본문 · 출처 기사 목록(인용 — 줄마다 제목 링크 · 출처 · 발행 시각)을 그린다. 반응 버튼은 항목 키(`eventIds` 의 첫 사건)로 누르고, 그 항목에 누른 값은 보이는 다이제스트 ID 로 `feedback` 의 항목 기록 조회에서 읽는다([feedback Spec](feedback.md#화면의-항목-기록-조회) feedback-R7) — 같은 사건이 든 다른 날짜의 항목에 누른 값은 보이지 않는다. 버튼 묶음의 접근 이름은 항목 제목을 담는다. 개요에는 버튼이 없다. 스토리라인이 있는 항목(`storylineId` 가 있는 주요 뉴스 · 헤드라인)의 머리에는 흐름 추적 버튼을 둔다 — 사건 흐름 탭 · 개인 흐름 화면 · 타임라인의 버튼과 같은 버튼이고, 누르는 대상은 `storylineId` 다. 추적 상태는 그 다이제스트의 `storylineId` 전부를 `user` 의 추적 상태 조회에 한 번 물어 읽는다([user-R67](user.md#사용자-스토리라인), [ADR-0284](../adr/0284-a-digest-item-keeps-the-top-that-grouped-it-on-its-section-and-items-with-a-flow-carry-a-flow-tracking-button.md)). 버튼의 접근 이름은 항목 제목을 담는다. 정리가 있는 항목(`userStorylines` 가 비지 않은 것)은 제목 아래 한 줄에 정리의 제목들을 받은 순서대로 개인 흐름 화면 링크로 둔다 — 제목 요소 밖이다. 헤드라인 항목은 머리글 하나 아래에 모은다. 사용량 줄의 후보 수는 사건 수다
- **reader-R10** — 날짜 목록: `GET /api/digests` 는 `DigestDirectory.dates` 를 한 쪽 30개(`DigestScreenController.DATES_PAGE_SIZE` — 측정 전 목표, `DigestDirectory.MAX_DATES` 이하)로 부르고 날짜 · 상태 · 다음 쪽 `next` 를 그대로 낸다(digest-R39). 질의 `before` 는 reader-R9 의 날짜 꼴이어야 한다 — 아니면 400
- **reader-R11** — 수동 생성: `POST /api/digests` 는 `DigestRequests.request` 를 세션 사용자로 부르고 결과 넷을 판별자 `result`(`REQUESTED` · `IN_PROGRESS` · `DAILY_LIMIT_REACHED` · `NO_USABLE_MODEL`)의 200 으로 옮긴다 — 받았으면 날짜 · 다시 만들기 여부, 오늘 횟수를 다 썼으면 다시 차는 시각을 싣는다(망라 분기). 요청 방어 표시가 없다 — 하루 횟수와 진행 중 거절은 `digest` 가 지킨다(digest-R31, [`docs/security.md`](../security.md) 악용 방지). 홈의 수동 생성 상태는 `DigestDirectory.requestStatus`(digest-R40)다

### 변화 리포트

판단의 이유는 [ADR-0316](../adr/0316-a-change-report-view-carries-each-visible-sections-numbers-as-typed-values-and-resolves-flow-lines-like-citations.md) · [ADR-0317](../adr/0317-a-change-report-row-points-to-the-previous-report-it-took-the-previous-values-from.md) · [ADR-0319](../adr/0319-the-change-report-screen-has-a-list-per-kind-and-a-report-page-with-four-states-and-the-failure-cause-only.md)에 있다. 비교 · 풀기 · 숨기기 · 기간 날짜의 판단은 `report` 의 것이다([report Spec](report.md#리포트-조회) report-R11 · R12 · R25 · R26).

- **reader-R15** — 목록: `GET /api/reports` 는 `ReportDirectory.list` 를 세션 사용자 · 질의의 종류(`kind` — 필수, `ReportPeriodKind` 의 값이 아니면 400) · 커서(`before` — 1 보다 작으면 400, `ScreenRequests.reportCursor`)로 한 쪽 20개(`ReportScreenController.REPORTS_PAGE_SIZE` — 측정 전 목표, `ReportDirectory.MAX_PAGE_SIZE` 이하)씩 부른다. 줄마다 ID · 종류 · 기간 첫날 · 마지막 날 · 상태 · 실패 코드 · 끝낸 시각을 싣고 다음 쪽 `next` 를 그대로 낸다. 실패 메시지는 싣지 않는다
  - 화면(`/reports`)은 종류 탭 둘(주간 · 월간 — 주소의 `kind`, 없거나 모르는 값이면 주간)마다 최근 순으로 줄마다 기간 링크와 상태(만드는 중 · 보일 변화 없음 · 만들지 못함 — 내용 있음은 적지 않는다)를 그리고, 리포트가 없으면 관심 설정에서 켜면 만들어진다고 안내한다
- **reader-R16** — 한 편: `GET /api/reports/{id}` 는 경로 ID 가 1 보다 작거나 `ReportDirectory.find` 가 빈 값이면 404 다. 응답은 요약 · 생성 때 이은 직전 리포트의 요약(없으면 null) · 숨긴 갈래 섹션 수 · 사용량(공급자 · 모델 · 토큰 — 시작 전이면 null, 금액으로 바꾸지 않는다) · 보이는 섹션 · 남은 다시 만들기 수(`regenerationsLeft` — `FAILED` 일 때만, 아니면 null — [report-R32](report.md#다시-만들기))다. 섹션마다 종류 · 제목 · 본문 · 인용 · 그 갈래의 숫자(사용자 스토리라인 보도량과 생성 때 상태 `userStorylines` · 관심 `interests` — 그 갈래 밖이거나 숫자가 없으면 null)를 싣는다. 섹션의 제목은 언제나 있다 — 모델이 쓴 그 갈래의 요지다. 스토리라인 인용 · 사용자 스토리라인 줄은 가리키는 스토리라인의 표시를 칸 `storyline` 에 싣는다
  - 스토리라인(인용 · 숫자 줄의 `storyline`)은 `ReportNames` 가 이름을 붙인다 — 그 스토리라인의 지금 꼭대기(`StorylineDirectory.topsOfStorylines`, `MAX_TOP_STORYLINES` 씩)를 `UserStorylineSummaries.ofTops`(`MAX_TOPS` 씩)에 물어 정리된 사용자 스토리라인 모두(ID · 제목), 그 스토리라인의 대표 사건 대표 기사(`findAll` · reader-R3)를 싣는다. 생성 뒤 다른 스토리라인의 하위로 들어간 스토리라인도 지금 꼭대기의 정리로 잇는다
  - 사건 인용은 대표 기사(reader-R7)를 싣고, 찾지 못하면 null 이다. 대표 기사는 스토리라인과 사건을 모아 한 응답에 한 번 읽는다
  - 다시 만들기: `POST /api/reports/{id}/regeneration` 은 경로 ID 가 1 보다 작거나 `ReportRequests.regenerate` 가 빈 값(그 사용자의 리포트가 아니거나 없다)이면 404 다. 그 밖은 200 이고 결과는 판별자(`result` — `REQUESTED` · `NOT_FAILED` · `LIMIT_REACHED` · `NO_USABLE_MODEL`)와 실패가 아닐 때의 지금 상태(`status` — 그 밖은 null)다. 받는 조건 · 횟수는 `report` 가 정한다(report-R32)
  - 화면(`/reports/:id`)은 상태 넷을 가른다 — 만드는 중(`SCHEDULED` · `RUNNING`) · 비어 있음(`EMPTY` — 담을 변화가 없었다) · 실패(`FAILED` — 실패 코드마다 무슨 일 문구, 메시지 없음. 남은 다시 만들기 수가 있으면 그 수와 다시 만들기 버튼 — 누르면 요청하는 동안 막고, 받으면 리포트를 다시 읽어 만드는 중이 되며, 받지 않으면 까닭을 보인다. 까닭은 다시 읽은 리포트가 실패가 아니어도(다른 곳에서 먼저 다시 만들었다) 한 편 화면에 남는다. 다 썼으면 버튼 없이 다 썼다고 안내한다) · 내용(`READY`). 내용은 갈래 섹션마다 갈래의 고정 제목(추적한 흐름 · 관심의 변화 — 정본 `reportTexts.ts` 의 `sectionHeadings`) 아래 모델이 쓴 제목을 요지 한 줄로, 이어 본문 · 표 · 인용 목록(접힘)을 그리고, 숨긴 섹션이 있으면 그 수를, 섹션이 하나도 없으면(갈래 섹션이 모두 숨음) 글 대신 안내를 보인다
  - 표는 수를 글자로 보이지 않는다([ADR-0337](../adr/0337-the-change-report-screen-shows-no-numbers-in-text-interest-as-history-cells-and-judgements-as-coloured-words.md) · [ADR-0341](../adr/0341-a-monthly-report-does-not-compare-flow-coverage-with-the-previous-monthly-and-shows-the-flow-state-kept-at-generation.md)). 표 이름은 짧게 — 흐름 표는 "보도"(주간) · "상태"(월간), 관심 표는 "주제" · "흐름". 주간 흐름 표는 이름과 판정 칸 하나이고, 판정은 색 낱말이다 — 늘었음 · 새로 생김은 주 색, 줄었음은 경고 색, 비슷함 · 비교 없음은 바탕색. 이번 · 직전 기간 기사 수("이번 기간 N건 · 직전 기간 M건", 직전이 없으면 "없음")는 판정 칸의 `title` 과 화면 낭독에만 있다. 월간 흐름 표는 이름과 상태 칸 하나이고(월간은 직전과 견주지 않는다 — report-R15), 상태는 생성 때 헤드의 상태를 다른 화면과 같은 낱말(진행 · 잠잠 · 끝남 — 정본 `shared/lib/storylineState.ts` 의 `storylineStateLabels`)의 색 낱말로 보인다 — 진행은 주 색, 잠잠 · 끝남은 바탕색. 이번 기간 기사 수("이번 기간 N건")는 칸의 `title` 과 화면 낭독에만 있고, 상태를 남기지 않은 옛 리포트의 줄은 상태 칸이 비어 있다. 관심 표(주제 · 사용자 스토리라인)의 기간 시작 · 끝 칸은 관심사 히스토리의 칸과 같은 색 네모(관심 쪽 주 색 · 싫어요 쪽 경고 색 · 신호 없음과 0 은 바탕, 진하기는 크기 — 색 판단의 정본은 `shared/lib/interestLevel.ts`)이고 값(소수 둘째 자리, 없으면 "신호 없음")은 칸의 `title` 과 화면 낭독에만 있다. 변화 칸은 색 낱말이다 — 새로 생긴 관심은 주 색, 사라진 관심은 경고 색, 변화 없음은 바탕색. 응답(`ReportSectionStats`)은 수를 그대로 싣는다
  - 스토리라인의 이름은 정리된 사용자 스토리라인의 제목(개인 흐름 화면 링크), 없으면 대표 기사 제목(공용 타임라인 링크), 그것도 없으면 이름 없는 스토리라인(공용 타임라인 링크)이다. 사건은 원문 링크(새 창) · 출처 · 발행 시각, 대표 기사가 없으면 링크 없는 안내다
  - 직전 리포트 줄은 생성 때 직전 리포트를 이은 리포트 — 내용 리포트와 시작한 실패 리포트(사용량이 있다) — 에만 그린다. 시작하지 않고 실패한 리포트는 잇지 않아 줄이 없다. 주간은 직전 리포트가 있으면 "비교한 직전 주간 리포트 · 기간" 링크, 없으면 비교가 빈 까닭의 안내다. 월간은 직전 값과 견주지 않으므로 "비교한" 없이 "직전 월간 리포트 · 기간" 링크, 없으면 직전 월간이 없다는 안내다(문구의 정본 `reportTexts.ts` 의 `previousLink` · `noPreviousTexts`). 맨 아래에 모델 · 토큰 · 끝낸 시각을 적는다
  - 진입은 머리줄의 "변화 리포트"와 관심 설정 리포트 절의 링크다
- **reader-R17** — 생성 조건: `GET /api/reports/conditions` 는 `report` 의 `ReportConditions.current` 를 그대로 낸다(`minDigestDays` · `minWeeklyReports` — [report-R28](report.md#생성-조건)). 사용자의 데이터가 아니라 누구의 세션이든 같은 값이고, 관심 설정 화면이 키 없이도 열리므로 키 관문 밖이다(`@AllowedWithoutLlmKey`). 관심 설정의 일정 · 변화 리포트 절은 이 값으로 "그 주에 다이제스트를 받은 날이 {수}일보다 적으면 주간 리포트를, 그 기간 안의 주간 리포트가 {수}편보다 적으면 월간 리포트를 만들지 않습니다"를 안내하고, 읽지 못하면 수 없이 안내한다

## 6. 데이터

해당 없음 — 이 모듈은 자기 테이블이 없다.

## 7. 설정

해당 없음.

## 8. 결정

| ADR | 한 줄 |
|---|---|
| [0044](../adr/0044-screen-api-results-as-flat-discriminated-200-and-rfc9457-problems.md) | 화면 API 는 판정 결과를 판별자 하나의 평평한 200 응답으로, 요청의 문제만 RFC 9457 형식의 자체 오류 레코드로 앱 전체에서 낸다 |
| [0068](../adr/0068-rss-summaries-shown-only-on-operator-screens-and-users-get-processed-information.md) | RSS 요약문은 운영자 화면에만 보이고, 인스턴스 사용자와 외부 API 에는 가공한 정보만 낸다 |
| [0192](../adr/0192-reader-screens-are-composed-by-the-reader-module-over-module-public-queries.md) | 독자 화면의 응답은 `reader` 모듈이 각 모듈의 공개 조회를 엮어 내고, 뉴스 내용을 보이는 사용자 화면은 모두 `reader` 가 맡는다 |
| [0193](../adr/0193-the-per-user-storyline-information-is-the-received-news-counted-from-the-digest-shown-for-each-date.md) | 사용자마다 달라지는 스토리라인 정보는 타임라인의 받은 소식 하나이고, 날짜별로 보이는 다이제스트의 인용으로 센다 |
| [0227](../adr/0227-the-interest-history-shows-subtopic-interest-and-the-users-own-topic-subscription-changes-only.md) | 관심사 히스토리는 주제 중분류의 관심과 사용자가 직접 바꾼 주제 구독만 보인다 |
| [0228](../adr/0228-consumer-queries-scoped-by-a-user-argument-may-be-called-on-screen-paths-with-the-session-user.md) | 사용자를 인자로 받는 소비자 조회는 화면 경로에서 세션 사용자로 부를 수 있다 |
| [0229](../adr/0229-interest-history-periods-are-monday-weeks-and-calendar-months-in-the-users-zone-and-the-query-takes-a-span-or-reference-times.md) | 관심사 히스토리의 기간은 관심 설정 시간대의 월요일 시작 주 · 1일 시작 월이고, 화면은 정해 둔 기간 수 가운데 하나를 고른다 |
| [0237](../adr/0237-the-digest-home-shows-the-latest-date-and-every-date-has-its-own-page-and-a-list.md) | 다이제스트 홈은 가장 늦은 날짜의 다이제스트를 보이고, 날짜마다 화면과 날짜 목록을 둔다 |
| [0238](../adr/0238-a-digest-date-shows-the-visible-digest-with-the-latest-attempt-and-the-list-holds-every-date-with-a-state.md) | 날짜 보기는 보이는 다이제스트와 가장 최근 시도를 함께 내고, 날짜 목록은 다이제스트가 있는 모든 날짜를 상태와 함께 낸다 |
| [0239](../adr/0239-digest-screens-show-tokens-of-the-visible-digest-and-the-sum-of-every-attempt-of-the-date-without-prices.md) | 토큰은 보이는 다이제스트의 것과 그 날짜 모든 시도의 합을 보이고 금액으로 바꾸지 않는다 |
| [0240](../adr/0240-digest-failures-show-the-cause-action-and-the-code-and-never-the-failure-message.md) | 실패는 갈래의 할 일과 코드의 무슨 일 두 층으로 보이고, 실패 메시지는 화면에 내지 않는다 |
| [0241](../adr/0241-selection-reasons-mark-only-citations-outside-subscribed-topics-named-interest-and-big-topic.md) | 선정 이유는 구독 주제가 아닌 인용에만 표시하고, 화면 이름은 구독 주제 · 관심 반영 · 큰 화제다 |
| [0245](../adr/0245-a-topic-suggestion-is-made-when-the-current-interest-of-an-unsubscribed-subtopic-reaches-its-own-threshold.md) | 구독 제안은 구독하지 않은 활성 중분류의 지금 관심이 "관심 있음" 기준과 따로 둔 제안 기준 이상일 때 낸다 |
| [0248](../adr/0248-the-digest-interests-and-topic-suggestion-queries-may-be-called-on-screen-paths-with-the-session-user.md) | 다이제스트가 읽는 관심 설정과 구독 제안의 조회는 화면 경로에서 세션 사용자로 부를 수 있다 |
| [0249](../adr/0249-topic-suggestions-show-as-a-card-on-the-digest-home-and-accepting-uses-the-tag-preference-save.md) | 구독 제안은 다이제스트 홈에 카드로 보이고 수락은 태그 선호 저장을 쓴다 |
| [0265](../adr/0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md) | 좋아요 / 싫어요는 다이제스트 항목에 한 번 누른다 — 섹션 응답이 항목의 사건 ID 를 싣는다 |
| [0284](../adr/0284-a-digest-item-keeps-the-top-that-grouped-it-on-its-section-and-items-with-a-flow-carry-a-flow-tracking-button.md) | 다이제스트 항목은 묶은 꼭대기를 섹션에 남기고, 흐름이 있는 항목에 흐름 추적 버튼을 둔다 |
| [0287](../adr/0287-user-storyline-summaries-are-made-and-stored-by-user-with-the-users-key-and-digest-supplies-the-flow-standing.md) | 사용자 스토리라인의 정리는 `user` 가 사용자 키로 만들고 저장한다 — 이 모듈은 그 공개 조회를 엮는다 |
| [0289](../adr/0289-shown-flows-are-user-storylines-with-a-summary-which-the-summary-job-creates-and-removes.md) | 보이는 흐름은 정리가 있는 사용자 스토리라인이고, 정리 작업이 만들고 지운다 |
| [0290](../adr/0290-the-readers-storyline-tab-shows-the-users-summarized-flows-and-the-tracking-list-has-one-row-per-user-storyline.md) | 독자의 스토리라인 탭은 정리된 내 흐름을 보이고, 추적 중인 흐름 목록은 사용자 스토리라인마다 한 줄이다 |
| [0291](../adr/0291-tokens-spent-on-a-summary-are-shown-on-the-personal-flow-screen-as-the-last-summary-and-the-running-total.md) | 정리에 쓴 토큰은 개인 흐름 화면에 마지막 정리와 지금까지의 합으로 보인다 |
| [0292](../adr/0292-reader-screens-call-a-storyline-an-event-flow.md) | 독자 화면은 스토리라인을 "사건 흐름"으로 부른다 |
| [0293](../adr/0293-the-interest-history-also-shows-the-interest-of-summarized-flows.md) | 관심사 히스토리는 정리된 흐름의 관심도 보인다 |
| [0294](../adr/0294-a-user-storyline-is-one-per-head-and-a-top-before-a-fork-belongs-to-several-of-them.md) | 정리된 사용자 스토리라인의 손잡이는 헤드이고, 항목의 사용자 스토리라인 링크는 그 스토리라인을 가진 정리된 사용자 스토리라인 모두다 |
| [0316](../adr/0316-a-change-report-view-carries-each-visible-sections-numbers-as-typed-values-and-resolves-flow-lines-like-citations.md) | 변화 리포트 조회는 보이는 갈래 섹션마다 스냅숏의 숫자를 싣는다 — 이 모듈은 스토리라인 · 사건의 표시 이름만 붙인다 |
| [0317](../adr/0317-a-change-report-row-points-to-the-previous-report-it-took-the-previous-values-from.md) | 변화 리포트 행은 생성 때 직전 값을 꺼낸 직전 리포트를 가리킨다 — 한 편이 그 링크를 단다 |
| [0319](../adr/0319-the-change-report-screen-has-a-list-per-kind-and-a-report-page-with-four-states-and-the-failure-cause-only.md) | 변화 리포트 화면은 종류마다 목록과 한 편 화면으로 보이고, 상태는 넷, 실패는 코드의 무슨 일 문구만 보인다 |
| [0323](../adr/0323-report-generation-jobs-and-stalled-report-recovery-follow-the-digest-and-a-report-ending-without-a-start-records-the-zone.md) | 리포트의 생성 작업 · 거두기는 다이제스트를 따르고, 시작하지 않고 끝난 리포트도 그때의 시간대를 적는다 |
| [0325](../adr/0325-a-failed-report-is-regenerated-from-the-screen-on-the-same-row-up-to-three-times.md) | 실패한 리포트는 화면에서 같은 행을 다시 생성을 기다리게 해 리포트마다 세 번까지 다시 만든다 |
| [0326](../adr/0326-development-names-call-a-shared-storyline-storyline-a-user-storyline-user-storyline-and-a-judgement-candidate-of-unknown-kind-candidate.md) | 개발 이름은 공유 스토리라인을 `Storyline`, 사용자 스토리라인을 `UserStoryline` 으로 부르고, 종류를 모르는 판정 후보는 `Candidate` 로 부른다 |
