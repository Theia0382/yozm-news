# reader 모듈 Spec

공통 구조와 쓰는 규칙은 [specs/README.md](README.md)에 있다.

## 1. 책임과 범위

`reader` 는 독자 화면(인스턴스 사용자가 뉴스 내용을 보는 화면)이 부르는 HTTP API 를 낸다. 각 모듈의 공개 조회를 엮어 응답 모양을 정하고, 데이터 · 판정을 갖지 않는다([ADR-0192](../adr/0192-reader-screens-are-composed-by-the-reader-module-over-module-public-queries.md)).

- **경계** — 뉴스 내용(기사 · 사건 · 스토리라인 · 다이제스트 · 변화 리포트 · 관심사 히스토리)을 보이는 사용자 화면의 응답은 이 모듈이 정한다. 사용자가 정해 두는 것(계정 · 키 · 관심 설정 · 구독 · 알림)의 화면은 그 데이터를 가진 모듈이 연다
- **스토리라인 목록** — 지금에서 거슬러 잰 기간(최근 24시간 · 7일 · 30일)의 스토리라인을 대표 기사 · 상태 · 사건 수 · 기간 기사 수 · 큰 여부와 함께 쪽으로 낸다(reader-R1 · R2)
- **스토리라인 타임라인** — 스토리라인 하나의 값, 상위 · 하위 · 앞 흐름 · 이어진 흐름, 사건마다 대표 기사와 그 사용자가 받은 날짜를 낸다(reader-R3 ~ R5)
- **스토리라인 카드** — 스토리라인 ID 여럿의 표시 정보를 낸다 — 구독 목록처럼 ID 만 가진 화면이 쓴다(reader-R6)
- **기사 정보** — 사용자 화면의 기사는 제목 · 출처 이름 · 발행 시각 · 원문 링크다(reader-R7)
- **(미구현)** 다이제스트 화면 · 관심사 히스토리 · 변화 리포트 열람의 응답 — 그 화면을 만드는 작업이 이 모듈에 처리기를, 내용을 가진 모듈에 공개 조회를 둔다

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

요청 · 응답 형식의 정본은 [`api/openapi.json`](../../api/openapi.json)이다([ADR-0020](../adr/0020-screen-api-contract-from-committed-openapi-spec.md)). 요청의 문제는 400 · 404 문제 응답이다([ADR-0044](../adr/0044-screen-api-results-as-flat-discriminated-200-and-rfc9457-problems.md)).

## 4. 내부 구조

### 개관

| 패키지 | 책임 |
|---|---|
| (최상위) | 모듈 선언 — 의존을 `storyline` · `event` · `article` · `digest` · `platform` 으로 둔다(`package-info`) |
| `adapter/in/web` | 독자 화면 컨트롤러, 응답 레코드, 요청 매개변수 읽기, 기간 열거, 기사 정보 · 대표 기사 · 받은 날짜 맞붙이기 |

협력: 컨트롤러는 `storyline` 의 공개 조회 `StorylineDirectory`(`list` · `find` · `findAll` · `events` · `relationsOf`), `event` 의 공개 조회 `EventDirectory`(`representativesOfAll` · `articleIdsOf`), `article` 의 공개 조회 `ArticleDirectory`(`headlines`), `digest` 의 공개 조회 `ReceivedArticles`(`receivedAmong`)만 부르고, 결과를 응답 레코드의 정적 팩토리로 옮긴다. 오류 응답은 `platform` 의 문제 응답 형식이다. `application` · `domain` 이 없다 — 판단이 없다([ADR-0192](../adr/0192-reader-screens-are-composed-by-the-reader-module-over-module-public-queries.md)).

역할: 모든 구성 요소가 화면 역할(`@WebRole`)이다(platform-R9).

### 들어오는 쪽

| 진입점 | 구성 요소 | 계기 | 내용 |
|---|---|---|---|
| 스토리라인 | `StorylineScreenController` | `GET /api/storylines` · `GET /api/storylines/{id}` · `GET /api/storyline-cards` | 목록 · 타임라인 · 카드(reader-R1 ~ R7). 트랜잭션을 열지 않는다 — 여러 공개 조회는 한 스냅숏이 아니다. "지금"은 생성자로 받은 시계로 읽는다 |

`adapter/in/web` 의 그 밖의 타입: 응답 `StorylinePageResponse` · `StorylinePageCursorResponse` · `StorylineCardResponse` · `StorylinePeriodResponse` · `StorylineTimelineResponse` · `StorylineRefResponse` · `TimelineEventResponse` · `StorylineCardsResponse` · `StorylineCardEntryResponse` · `NewsArticleResponse`, 보조 `ScreenRequests`(요청 매개변수 읽기와 그 400 · 404) · `StorylineWindow`(목록 기간의 열거 — 기간 길이의 정본) · `ScreenArticles`(기사 정보 맞붙이기) · `StorylineTitles`(사건의 대표 기사 맞붙이기) · `ReceivedMarks`(사건마다 받은 날짜 맞붙이기). 보조 맞붙이기 셋은 스프링 구성 요소가 아니라 컨트롤러가 만든다.

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

### 기사 정보

- **reader-R7** — 사용자 화면의 기사는 제목 · 출처 이름 · 발행 시각 · 원문 링크(정규 URL)다 — `article` 의 `ArticleDirectory.headlines` 를 한 응답에 한 번 부른다(출처 이름 엮기는 article-R17). 그새 지운 기사는 비어 있다

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
