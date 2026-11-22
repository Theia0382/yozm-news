# reader 모듈 Spec

공통 구조와 쓰는 규칙은 [specs/README.md](README.md)에 있다.

## 1. 책임과 범위

`reader` 는 독자 화면(인스턴스 사용자가 뉴스 내용을 보는 화면)이 부르는 HTTP API 를 낸다. 각 모듈의 공개 조회를 엮어 응답 모양을 정하고, 데이터 · 판정을 갖지 않는다([ADR-0192](../adr/0192-reader-screens-are-composed-by-the-reader-module-over-module-public-queries.md)).

- **경계** — 뉴스 내용(기사 · 사건 · 스토리라인 · 다이제스트 · 변화 리포트 · 관심사 히스토리)을 보이는 사용자 화면의 응답은 이 모듈이 정한다. 사용자가 정해 두는 것(계정 · 키 · 관심 설정 · 구독 · 알림)의 화면은 그 데이터를 가진 모듈이 연다
- **스토리라인 목록** — 지금에서 거슬러 잰 기간(최근 24시간 · 7일 · 30일)의 스토리라인을 대표 기사 · 상태 · 사건 수 · 기간 기사 수 · 큰 여부와 함께 쪽으로 낸다(reader-R1 · R2)
- **스토리라인 타임라인** — 스토리라인 하나의 값, 상위 · 하위 · 앞 흐름 · 이어진 흐름, 사건마다 대표 기사와 그 사용자가 받은 날짜를 낸다(reader-R3 ~ R5)
- **스토리라인 카드** — 스토리라인 ID 여럿의 표시 정보를 낸다 — 구독 목록처럼 ID 만 가진 화면이 쓴다(reader-R6)
- **기사 정보** — 사용자 화면의 기사는 제목 · 출처 이름 · 발행 시각 · 원문 링크다(reader-R7)
- **관심사 히스토리** — 세션 사용자의 기간별 주제 관심 · 새로 생긴 관심 · 사라진 관심 · 주제 구독 변경을 고른 기간(주 · 월)으로 낸다(reader-R8)
- **다이제스트** — 세션 사용자의 가장 늦은 날짜의 다이제스트와 수동 생성 상태, 날짜 하나의 다이제스트, 다이제스트가 있는 날짜 목록을 내고, 수동 생성 요청을 받는다(reader-R9 ~ R11)
- **구독 제안** — 세션 사용자에게 지금 제안할 주제 중분류의 이름 · 대분류 이름과 태그 체계 버전을 낸다 — 다이제스트 홈의 제안 카드가 쓴다(reader-R12). 수락 · 거절은 `user` 의 처리기가 받는다
- **(미구현)** 변화 리포트 열람의 응답 — 그 화면을 만드는 작업이 이 모듈에 처리기를 두고 `report` 의 공개 조회(`ReportDirectory`)를 엮는다

하지 않는 것:

- 판정 · 저장을 하지 않는다 — 그 지식을 가진 모듈의 공개 조회에 맡긴다. 받은 소식의 뜻은 `digest`(digest-R13), 큰 여부 · 상태는 `storyline`, 대표 기사는 `event`, 출처 이름 엮기는 `article`(article-R17)이 정한다
- 판정 근거(소속의 점수 · 관계의 확률 · 물은 기록 · 기준)와 보도 강도 · 중요도를 내지 않는다 — 운영자 화면(`admin`)의 것이다
- RSS 요약문 · 피드 섹션 · RSS 카테고리를 내지 않는다([ADR-0068](../adr/0068-rss-summaries-shown-only-on-operator-screens-and-users-get-processed-information.md))
- 사용자를 인증하지 않는다 — 세션 · 키 관문은 `user` 의 보안 구성이 한다([user Spec](user.md#화면용-api-의-공개와-세션-사용자) user-R5 · R6)

다른 모듈과의 의존과 이벤트 흐름은 [overview.md §5](../overview.md#5-모듈-지도)에 있다.

## 2. 용어

스토리라인 · 큰 스토리라인 · 받은 소식은 [overview.md §8](../overview.md#8-용어)에 있다.

| 용어 | 뜻 |
|---|---|
| 목록 기간 | 스토리라인 목록이 고르는 기간 — 기간 끝(`until`)에서 거슬러 잰 24시간 · 7일 · 30일(reader-R1) |
| 카드 | 스토리라인 하나의 표시 정보 — 대표 기사 · 상태 · 사건 수 · 기사 수 · 첫 · 마지막 기사 시각 · 상위(reader-R3) |
| 앞 흐름 · 이어진 흐름 | 보고 있는 스토리라인을 낳은 흐름 · 보고 있는 스토리라인이 낳은 흐름 — 이어짐 관계의 두 방향(reader-R4) |

## 3. 공개 인터페이스

모듈 최상위 패키지 `net.yozmnews.reader` 에 공개 타입이 없다. 다른 모듈이 부르는 공개 API 는 없다.

HTTP 엔드포인트 — 모두 세션이 필요하고 키 관문 뒤다(키 없이 여는 표시가 없다 — user-R5). 요청 방어 표시(`@RequestLimited` · `@HumanChecked`)도 없다(user-R40):

| 메서드 · 경로 | 용도 |
|---|---|
| `GET /api/storylines` | 목록 기간의 스토리라인 한 쪽 — 질의 `window`(`DAY` · `WEEK` · `MONTH`, 기본 `WEEK`) · `until`(ISO-8601, 없으면 지금) · `afterLastArticleAt` · `afterId`(다음 쪽 커서 — 함께) |
| `GET /api/storylines/{id}` | 스토리라인 하나의 타임라인 — 카드 · 상위 · 하위 · 앞 흐름 · 이어진 흐름 · 사건마다 대표 기사와 받은 날짜. 합쳐진 ID 면 남는 스토리라인을 낸다 |
| `GET /api/storyline-cards` | 스토리라인 ID 여럿의 카드 — 질의 `ids`(쉼표로 이은 ID, 1 ~ 100개) |
| `GET /api/digests/latest` | 세션 사용자의 홈 — 가장 늦은 날짜의 날짜 보기(없으면 null)와 수동 생성 상태(계정이 없으면 null — 오늘 일정 생성 시각 · 사용자 시간대 · 해결되지 않은 마지막 실패를 함께) |
| `GET /api/digests/{date}` | 날짜 하나의 다이제스트 — 경로 `date`(YYYY-MM-DD). 형식이 틀리면 400, 그 날짜의 다이제스트가 없으면 404 |
| `GET /api/digests` | 다이제스트가 있는 날짜 한 쪽 — 질의 `before`(YYYY-MM-DD, 다음 쪽은 앞 응답의 `next`) |
| `POST /api/digests` | 수동 생성 요청 — 판별자 200(받음 · 진행 중 · 오늘 횟수 소진 · 쓸 모델 없음) |
| `GET /api/topic-suggestions` | 세션 사용자에게 지금 제안할 주제 — 태그 체계 버전 ID(계정이 없으면 null) · 중분류마다 키 · 이름 · 대분류 이름 |
| `GET /api/interest-history` | 세션 사용자의 관심사 히스토리 — 질의 `span`(`WEEKS_13` · `WEEKS_26` · `WEEKS_52` · `MONTHS_12` · `MONTHS_24` · `MONTHS_36`, 없으면 `digest` 의 `InterestHistorySpan.DEFAULT`) |

요청 · 응답 형식의 정본은 [`api/openapi.json`](../../api/openapi.json)이다([ADR-0020](../adr/0020-screen-api-contract-from-committed-openapi-spec.md)). 요청의 문제는 400 · 404 문제 응답이다([ADR-0044](../adr/0044-screen-api-results-as-flat-discriminated-200-and-rfc9457-problems.md)).

## 4. 내부 구조

### 개관

| 패키지 | 책임 |
|---|---|
| (최상위) | 모듈 선언 — 의존을 `storyline` · `event` · `article` · `digest` · `tag` · `user` · `platform` 으로 둔다(`package-info`) |
| `adapter/in/web` | 독자 화면 컨트롤러, 응답 레코드, 요청 매개변수 읽기, 기간 열거, 기사 정보 · 대표 기사 · 받은 날짜 맞붙이기, 관심사 히스토리 · 구독 제안의 주제 이름 맞붙이기, 다이제스트 인용 기사의 정보 맞붙이기 |

협력: 컨트롤러는 `storyline` 의 공개 조회 `StorylineDirectory`(`list` · `find` · `findAll` · `events` · `relationsOf`), `event` 의 공개 조회 `EventDirectory`(`representativesOfAll` · `articleIdsOf`), `article` 의 공개 조회 `ArticleDirectory`(`headlines`), `digest` 의 공개 조회 `ReceivedArticles`(`receivedAmong`) · `InterestHistory`(`recent`) · `TopicSuggestions`(`suggestionsOf`) · `DigestDirectory`(`latest` · `on` · `dates` · `requestStatus`)와 공개 API `DigestRequests`(`request`), `tag` 의 공개 조회 `TagSetDirectory`(`names` — `TagSetNames` 의 `topicName` · `categoryOf` · `topicOrder`)만 부르고(`user` 는 값 타입 `TopicSubscriptionAction` · `DigestFailureCause` 만 쓴다), 결과를 응답 레코드의 정적 팩토리로 옮긴다. 오류 응답은 `platform` 의 문제 응답 형식이다. `application` · `domain` 이 없다 — 판단이 없다([ADR-0192](../adr/0192-reader-screens-are-composed-by-the-reader-module-over-module-public-queries.md)).

역할: 모든 구성 요소가 화면 역할(`@WebRole`)이다(platform-R9).

### 들어오는 쪽

| 진입점 | 구성 요소 | 계기 | 내용 |
|---|---|---|---|
| 스토리라인 | `StorylineScreenController` | `GET /api/storylines` · `GET /api/storylines/{id}` · `GET /api/storyline-cards` | 목록 · 타임라인 · 카드(reader-R1 ~ R7). 트랜잭션을 열지 않는다 — 여러 공개 조회는 한 스냅숏이 아니다. "지금"은 생성자로 받은 시계로 읽는다 |
| 다이제스트 | `DigestScreenController` | `GET /api/digests/latest` · `GET /api/digests/{date}` · `GET /api/digests` · `POST /api/digests` | 홈 · 날짜 · 날짜 목록 · 수동 생성 요청(reader-R9 ~ R11). 트랜잭션을 열지 않는다 — 수동 생성 요청은 트랜잭션 밖에서 불러야 한다 |
| 관심사 히스토리 | `InterestHistoryController` | `GET /api/interest-history` | 세션 사용자의 관심사 히스토리(reader-R8). 트랜잭션을 열지 않는다 |
| 구독 제안 | `TopicSuggestionController` | `GET /api/topic-suggestions` | 세션 사용자의 구독 제안(reader-R12). 트랜잭션을 열지 않는다 |

`adapter/in/web` 의 그 밖의 타입: 응답 `StorylinePageResponse` · `StorylinePageCursorResponse` · `StorylineCardResponse` · `StorylinePeriodResponse` · `StorylineTimelineResponse` · `StorylineRefResponse` · `TimelineEventResponse` · `StorylineCardsResponse` · `StorylineCardEntryResponse` · `NewsArticleResponse` · `InterestHistoryResponse` · `InterestSpanChoicesResponse` · `InterestPeriodResponse` · `TopicSubscriptionStepResponse` · `InterestTopicResponse` · `InterestLevelResponse` · `DigestHomeResponse` · `DigestDayResponse` · `ShownDigestResponse` · `DigestSectionResponse` · `DigestCitationResponse` · `DigestAttemptResponse` · `DigestUsageResponse` · `DigestDatesResponse`(중첩 `DigestDateResponse`) · `DigestRequestStatusResponse` · `DigestUnresolvedFailureResponse` · `DigestRequestResponse` · `TopicSuggestionsResponse` · `TopicSuggestionResponse`, 보조 `ScreenRequests`(요청 매개변수 읽기와 그 400 · 404) · `StorylineWindow`(목록 기간의 열거 — 기간 길이의 정본) · `ScreenArticles`(기사 정보 맞붙이기) · `StorylineTitles`(사건의 대표 기사 맞붙이기) · `ReceivedMarks`(사건마다 받은 날짜 맞붙이기). 보조 맞붙이기 셋은 스프링 구성 요소가 아니라 컨트롤러가 만든다.

### `application`

해당 없음.

### `domain`

해당 없음.

### 나가는 쪽

해당 없음 — 다른 모듈의 공개 조회만 부른다.

## 5. 규칙과 불변식

### 스토리라인 목록

- **reader-R1** — 목록 기간: 기간은 `[until − 길이, until)` 이다. 길이는 `DAY` 24시간 · `WEEK` 7 × 24시간 · `MONTH` 30 × 24시간이다(`StorylineWindow` — 정본). `until` 이 없으면 지금이다. `until` 이 지금보다 뒤거나 시각으로 읽을 수 없거나 `until − 길이` 를 만들 수 없으면 400 이다. 응답은 쓴 기간의 시작 · 끝(`from` · `until`)을 싣는다 — 날짜 경계 · 시간대가 없다. 같은 스토리라인도 기간마다 큰 여부가 다를 수 있다(storyline-R19 — 보도 강도는 기간 일수로 나눈다)
- **reader-R2** — 쪽: `StorylineDirectory.list` 를 한 쪽 30건(측정 전 목표 — `StorylineDirectory.MAX_PAGE_SIZE` 이하)으로 부르고 순서 · 다음 쪽 커서를 그대로 낸다(storyline-R22). 다음 쪽은 첫 응답의 `until` 과 응답의 커서(`afterLastArticleAt` · `afterId`)를 넘긴다 — 기간이 쪽 사이에 미끄러지지 않는다. 커서 두 값은 함께 오거나 함께 없다 — 하나만 오거나 시각 · ID(1 이상)를 읽을 수 없으면 400 이다. 행은 카드(reader-R3)와 그 기간의 기사 수 · 큰 여부를 싣는다. 포함의 상위 · 하위는 각각 행이고(storyline-R19 · R22) 하위 행은 상위 ID 를 싣는다. 기간 안 기사가 없으면 빈 목록이다

### 스토리라인 타임라인

- **reader-R3** — 카드: 스토리라인 값(`StorylineView` — 상태 · 닫은 사건 집합의 사건 수 · 기사 수 · 첫 · 마지막 기사 시각 · 상위 ID)과 대표 기사(대표 사건의 대표 기사 — reader-R7)다. 대표 사건이 합쳐지거나 지워진 뒤 다음 잇기 회차까지는 대표 기사가 없어 제목이 비어 있다(`EventDirectory.representativesOfAll` 이 합쳐진 · 지워진 사건을 뺀다). 보도 강도 · 소스 수 · 중요도는 싣지 않는다
- **reader-R4** — 타임라인: 경로 ID 가 1 미만이거나 `StorylineDirectory.find` 가 없으면 404 다. 합쳐진 스토리라인이면 남는 스토리라인을 낸다 — 응답의 ID 가 경로 ID 와 다르다(storyline-R21). 사건은 `StorylineDirectory.events` 의 순서(첫 기사 시각 · 사건 ID)와 직접 소속인지를 그대로 싣고, 사건마다 기사 수 · 첫 · 마지막 기사 시각 · 대표 기사를 싣는다(대표는 사건 기사 전체에서 고른 것 — event-R14). 관계는 `relationsOf` 를 보고 있는 스토리라인 S 기준으로 나눈다 — 포함이고 S 가 앞이면 하위, 이어짐이고 S 가 뒤면 앞 흐름, S 가 앞이면 이어진 흐름이다. 상위는 `StorylineView.parent` 하나로 정하고(storyline-R25 — 상위는 하나) 포함의 S 가 뒤인 고리는 목록에 담지 않는다. 상위 · 상대는 `findAll` 한 번으로 읽어 ID(합쳐졌으면 남는 쪽) · 대표 기사 제목 · 상태를 싣고, 그새 없어진 상대는 빼며, 한 목록에 같은 ID 는 한 번이다. 판정 근거는 싣지 않는다
- **reader-R5** — 받은 날짜: 타임라인 사건의 기사 ID 를 `EventDirectory.articleIdsOf` 로 얻어 `ReceivedArticles.receivedAmong` 에 세션 사용자로 묻고(`ReceivedArticles.MAX_ARTICLES` 개씩 나눠), 사건마다 그 기사들의 받은 날짜 가운데 가장 이른 날짜를 단다. 받은 기사가 없는 사건은 비어 있다. 받은 소식의 뜻은 digest-R13 이다([ADR-0193](../adr/0193-the-per-user-storyline-information-is-the-received-news-counted-from-the-digest-shown-for-each-date.md))

### 스토리라인 카드

- **reader-R6** — 카드 여럿: `ids` 는 받은 값의 수(같은 ID 포함)가 1 ~ 100개(`StorylineDirectory.MAX_PAGE_SIZE`)이고 모두 1 이상이어야 한다 — 아니면 400 이다. 같은 ID 는 처음 자리 한 번만 본다. `StorylineDirectory.findAll` 의 결과를 받은 ID 순으로 싣고 행마다 받은 ID 와 카드(reader-R3)를 싣는다 — 합쳐진 ID 면 카드의 ID 가 남는 스토리라인이고, 없는 ID 는 빠진다. 기간이 없어 기간 기사 수 · 큰 여부는 싣지 않는다

### 관심사 히스토리

판단의 이유는 [ADR-0227](../adr/0227-the-interest-history-shows-subtopic-interest-and-the-users-own-topic-subscription-changes-only.md) · [ADR-0229](../adr/0229-interest-history-periods-are-monday-weeks-and-calendar-months-in-the-users-zone-and-the-query-takes-a-span-or-reference-times.md) · [ADR-0228](../adr/0228-consumer-queries-scoped-by-a-user-argument-may-be-called-on-screen-paths-with-the-session-user.md)에 있다. 기간 · 관심 · 변화의 판단은 `digest` 의 것이다(digest-R34 ~ R37).

- **reader-R8** — 관심사 히스토리: 세션 사용자로 `InterestHistory.recent` 를 부른다. 질의 `span` 이 없으면 `InterestHistorySpan.DEFAULT`, 열거 값이 아니면 400 이다. 응답은 고른 기간(`span`), 단위마다 고를 수 있는 기간과 단위를 바꿨을 때의 기간(`spans[]` — `InterestHistorySpan.Unit` 순, `defaultOf`), 기간(`periods[]` — 시간 순, 마지막이 진행 중), 주제(`topics[]`)를 싣는다. 계정이 없으면(조회가 빈 값) 기간 · 주제가 비어 있다
  - 기간마다 첫날 · 마지막 날(사용자 시간대) · 진행 중 여부 · 새로 생긴 관심 · 사라진 관심 · 사용자가 직접 바꾼 주제 구독(시각 · 더함 / 뺌 · 덮는 중분류)을 싣는다. 키는 태그 체계 순이다
  - 주제는 기간 가운데 한 번이라도 관심 값이 있는 중분류이고 `TagSetNames.topicOrder()` 순이다. 주제마다 키 · 이름 · 대분류 이름(`TagSetNames.categoryOf` → `topicName`) · 기간마다 관심 칸(`level` — 신호가 없으면 null) · 드러남(`prominent` — digest-R36)을 싣는다. 이름은 조회가 낸 태그 체계 버전(`InterestTimeline.tagSet`)으로 읽는다 — 그 버전에 없는 키는 불변식이 깨진 것이라 `IllegalStateException` 이다
  - 판정 · 관심의 계산은 하지 않는다 — θ 는 응답에 없다

### 구독 제안

판단의 이유는 [ADR-0249](../adr/0249-topic-suggestions-show-as-a-card-on-the-digest-home-and-accepting-uses-the-tag-preference-save.md) · [ADR-0245](../adr/0245-a-topic-suggestion-is-made-when-the-current-interest-of-an-unsubscribed-subtopic-reaches-its-own-threshold.md) · [ADR-0248](../adr/0248-the-digest-interests-and-topic-suggestion-queries-may-be-called-on-screen-paths-with-the-session-user.md)에 있다. 제안의 판정은 `digest` 의 것이다(digest-R41 · R42).

- **reader-R12** — 구독 제안: `GET /api/topic-suggestions` 는 `digest` 의 `TopicSuggestions.suggestionsOf` 를 세션 사용자로 부른다. 빈 값(계정 없음)이면 `tagSetId` null · `topics` 빈 목록이다. 있으면 `tagSetId` 는 그 버전 ID, `topics` 는 낸 순서 그대로 중분류마다 키 · 이름 · 대분류 이름(`TagSetNames.categoryOf` → `topicName`)이다. 이름은 제안이 낸 버전으로 읽는다 — 그 버전에 없는 키는 불변식이 깨진 것이라 `IllegalStateException` 이다. 관심 값 · 기준은 싣지 않는다
  - 화면(다이제스트 홈)은 제안이 있을 때만 카드를 그린다. 수락은 지금 태그 선호에 그 중분류를 더해 `user` 의 태그 선호 저장(`PUT /api/interests/tags` — 이 응답의 `tagSetId`)으로, 거절은 `user` 의 `POST /api/interests/declined-suggestions` 로 보낸다 — 선호와 제안의 버전이 다르면 수락을 보내지 않고 둘을 다시 읽는다([user Spec](user.md#구독-제안-거절) user-R64)

### 기사 정보

- **reader-R7** — 사용자 화면의 기사는 제목 · 출처 이름 · 발행 시각 · 원문 링크(정규 URL)다 — `article` 의 `ArticleDirectory.headlines` 를 한 응답에 한 번 부른다(출처 이름 엮기는 article-R17). 그새 지운 기사는 비어 있다

### 다이제스트

판단의 이유는 [ADR-0237](../adr/0237-the-digest-home-shows-the-latest-date-and-every-date-has-its-own-page-and-a-list.md) · [ADR-0238](../adr/0238-a-digest-date-shows-the-visible-digest-with-the-latest-attempt-and-the-list-holds-every-date-with-a-state.md) · [ADR-0239](../adr/0239-digest-screens-show-tokens-of-the-visible-digest-and-the-sum-of-every-attempt-of-the-date-without-prices.md) · [ADR-0240](../adr/0240-digest-failures-show-the-cause-action-and-the-code-and-never-the-failure-message.md) · [ADR-0262](../adr/0262-chosen-events-are-grouped-into-items-by-containment-tree-top-code-splits-top-stories-and-headlines-the-llm-writes-per-item-and-an-item-has-one-badge.md)(항목마다 배지 하나 · 출처 기사 목록)에 있다.

- **reader-R9** — 날짜 보기: `GET /api/digests/latest` 는 `DigestDirectory.latest` 와 `requestStatus` 를, `GET /api/digests/{date}` 는 `on` 을 세션 사용자로 부른다. 경로 `date` 는 연도 네 자리의 `YYYY-MM-DD` 꼴이고 달력의 날짜여야 한다 — 아니면 400(`ScreenRequests.date`), 그 날짜의 다이제스트가 없으면 404 다. 응답은 날짜 · 이웃 날짜 · 보이는 다이제스트(기간 · 모델 · 후보 수(사건 수) · 토큰 · 끝낸 시각 · 섹션) · 가장 최근 시도(상태 · 실패 코드 · 갈래 · 끝낸 시각) · 그 날짜의 사용량이다(digest-R38). 홈의 수동 생성 상태(digest-R40)는 사용자 시간대(IANA 이름) · 오늘 일정 생성 시각(아직 오지 않았을 때만, 아니면 null) · 해결되지 않은 마지막 실패(날짜 · 끝낸 시각, 없으면 null)를 함께 싣는다 — 화면은 그 시각을 사용자 시간대로 적고, 알림 화면의 실패 알림 본문은 날짜 · 끝낸 시각이 같은 알림에만 다시 만들기 링크를 단다([user-R62](user.md#화면-내-알림)). 실패 메시지는 싣지 않는다. 섹션마다 종류(개요 · 주요 뉴스 항목 · 헤드라인 항목) · 제목 · 본문(개요 · 주요 뉴스의 요약 · 헤드라인의 코멘트) · 선정 이유 하나(`reason` — 개요는 null, [digest-R48](digest.md#다이제스트-조회)) · 인용 · 항목의 사건 ID(`eventIds` — 위치 순, 첫 사건이 좋아요 / 싫어요의 항목 키, 개요는 빈 배열 — [feedback Spec](feedback.md#값과-기록) feedback-R2) · 항목을 묶은 흐름(`storylineId` — 스토리라인 포함 나무의 지금 꼭대기, 개요 · 흐름 없는 항목은 null — [digest-R38](digest.md#다이제스트-조회))을 싣는다. 흐름의 추적 상태는 싣지 않는다. 인용마다 기사 ID 와 기사 정보(reader-R7)만 싣고, 모든 섹션의 인용 기사 정보를 한 응답에 한 번 읽는다 — 그새 지운 기사의 인용은 뺀다
  - 화면은 주요 뉴스 · 헤드라인 항목마다 머리(제목 · 선정 이유 배지 — 구독 주제(`SUBSCRIBED`)와 개요는 배지가 없다 — 와 좋아요 / 싫어요 버튼 한 벌) · 본문 · 출처 기사 목록(인용 — 줄마다 제목 링크 · 출처 · 발행 시각)을 그린다. 반응 버튼은 항목 키(`eventIds` 의 첫 사건)로 누르고, 그 항목에 누른 값은 보이는 다이제스트 ID 로 `feedback` 의 항목 기록 조회에서 읽는다([feedback Spec](feedback.md#화면의-항목-기록-조회) feedback-R7) — 같은 사건이 든 다른 날짜의 항목에 누른 값은 보이지 않는다. 버튼 묶음의 접근 이름은 항목 제목을 담는다. 개요에는 버튼이 없다. 흐름이 있는 항목(`storylineId` 가 있는 주요 뉴스 · 헤드라인)의 머리에는 흐름 추적 버튼을 둔다 — 스토리라인 목록 · 타임라인의 버튼과 같은 버튼이고, 누르는 대상은 `storylineId` 다. 추적 상태는 그 다이제스트의 `storylineId` 전부를 `user` 의 추적 상태 조회에 한 번 물어 읽는다([user-R67](user.md#사용자-스토리라인), [ADR-0284](../adr/0284-a-digest-item-keeps-the-top-that-grouped-it-on-its-section-and-items-with-a-flow-carry-a-flow-tracking-button.md)). 버튼의 접근 이름은 항목 제목을 담는다. 헤드라인 항목은 머리글 하나 아래에 모은다. 사용량 줄의 후보 수는 사건 수다
- **reader-R10** — 날짜 목록: `GET /api/digests` 는 `DigestDirectory.dates` 를 한 쪽 30개(`DigestScreenController.DATES_PAGE_SIZE` — 측정 전 목표, `DigestDirectory.MAX_DATES` 이하)로 부르고 날짜 · 상태 · 다음 쪽 `next` 를 그대로 낸다(digest-R39). 질의 `before` 는 reader-R9 의 날짜 꼴이어야 한다 — 아니면 400
- **reader-R11** — 수동 생성: `POST /api/digests` 는 `DigestRequests.request` 를 세션 사용자로 부르고 결과 넷을 판별자 `result`(`REQUESTED` · `IN_PROGRESS` · `DAILY_LIMIT_REACHED` · `NO_USABLE_MODEL`)의 200 으로 옮긴다 — 받았으면 날짜 · 다시 만들기 여부, 오늘 횟수를 다 썼으면 다시 차는 시각을 싣는다(망라 분기). 요청 방어 표시가 없다 — 하루 횟수와 진행 중 거절은 `digest` 가 지킨다(digest-R31, [`docs/security.md`](../security.md) 악용 방지). 홈의 수동 생성 상태는 `DigestDirectory.requestStatus`(digest-R40)다

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
