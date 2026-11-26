# reader 모듈 Spec

공통 구조와 쓰는 규칙은 [specs/README.md](README.md)에 있다.

## 1. 책임과 범위

`reader` 는 독자 화면(인스턴스 사용자가 뉴스 내용을 보는 화면)이 부르는 HTTP API 를 낸다. 각 모듈의 공개 조회를 엮어 응답 모양을 정하고, 데이터 · 판정을 갖지 않는다([ADR-0192](../adr/0192-reader-screens-are-composed-by-the-reader-module-over-module-public-queries.md)).

- **경계** — 뉴스 내용을 보이는 사용자 화면의 응답은 이 모듈이 정한다. 사용자가 정해 두는 것의 화면은 그 데이터를 가진 모듈이 연다.
  - 뉴스 내용은 아래다
    - 기사 · 사건 · 스토리라인
    - 사용자 스토리라인의 정리
    - 다이제스트 · 변화 리포트 · 관심사 히스토리
  - 사용자가 정해 두는 것은 아래다
    - 계정 · 키
    - 관심 설정 · 구독
    - 알림
- **정리된 사용자 스토리라인** — 세션 사용자의 정리된 사용자 스토리라인의 목록과 하나, 그 사용자 스토리라인에 묶인 지난 다이제스트 항목을 낸다(reader-R13 · R14). 공용 스토리라인의 목록은 내지 않는다. 그 목록은 운영자 화면(`admin`)에 있다
- **스토리라인 타임라인** — 스토리라인 하나의 값과 관계, 사건마다 대표 기사와 그 사용자가 받은 날짜를 낸다(reader-R3 ~ R5)
- **스토리라인 카드** — 스토리라인 ID 여럿의 표시 정보를 낸다. 추적 중인 사용자 스토리라인 목록처럼 ID 만 가진 화면이 쓴다(reader-R6)
- **기사 정보** — 사용자 화면의 기사 한 줄을 정한다(reader-R7)
- **관심사 히스토리** — 세션 사용자의 주제 관심과 정리된 사용자 스토리라인의 관심을 고른 기간(주 · 월)으로 낸다(reader-R8)
- **다이제스트** — 세션 사용자의 홈 · 날짜 하나 · 날짜 목록을 내고 수동 생성 요청을 받는다. 항목을 묶은 스토리라인이 정리돼 있으면 그 정리의 제목을 붙인다(reader-R9 ~ R11)
- **구독 제안** — 세션 사용자에게 지금 제안할 주제 중분류를 이름과 함께 낸다(reader-R12). 수락 · 거절은 `user` 의 처리기가 받는다
- **변화 리포트** — 세션 사용자의 종류 하나(주간 · 월간)의 리포트 목록과 리포트 한 편을 내고, 실패한 리포트를 다시 만들고, 생성 조건의 수를 낸다. 한 편의 인용 · 숫자 줄이 가리키는 스토리라인 · 사건에는 표시 이름을 붙인다(reader-R15 ~ R17)

하지 않는 것:

- 판정 · 저장을 하지 않는다. 그 지식을 가진 모듈의 공개 조회에 맡긴다

  | 판단 | 정하는 모듈 |
  |---|---|
  | 리포트의 비교 · 풀기 · 숨기기 · 기간 날짜 | `report`(report-R11 · R12 · R25 · R26) |
  | 받은 소식의 뜻 | `digest`(digest-R13) |
  | 무엇이 정리된 사용자 스토리라인인가 | `user`(user-R73) |
  | 스토리라인에 묶인 지난 항목 | `digest`(digest-R53) |
  | 스토리라인의 상태 | `storyline` |
  | 사건의 대표 기사 | `event` |
  | 출처 이름 엮기 | `article`(article-R17) |

- 판정 근거(소속의 점수 · 관계의 확률 · 물은 기록 · 기준)와 보도 강도 · 중요도를 내지 않는다. 이것들은 운영자 화면(`admin`)의 것이다
- RSS 요약문 · 피드 섹션 · RSS 카테고리를 내지 않는다([ADR-0068](../adr/0068-rss-summaries-shown-only-on-operator-screens-and-users-get-processed-information.md))
- 사용자를 인증하지 않는다. 세션 · 키 관문은 `account` 의 보안 구성이 한다([account Spec](account.md#화면용-api-의-공개와-세션-사용자) account-R5 · R6)

다른 모듈과의 의존과 주고받는 이벤트는 [overview.md §5](../overview.md#5-모듈-지도)에 있다.

## 2. 용어

주인을 한 모듈로 정할 수 없는 횡단 용어(스토리라인 · 사용자 스토리라인 · 꼭대기 · 헤드 등)는 [overview.md §8](../overview.md#8-용어)에 있다. 정리는 [user Spec §2](user.md#2-용어)에, 받은 소식은 [digest Spec](digest.md#받은-소식)(digest-R13)에 있다. 화면 이름(사건 흐름 · 개인 흐름 화면 등)은 [web Spec §2](web.md#2-용어)에 있다.

| 용어 | 뜻 |
|---|---|
| 카드 | 스토리라인 하나의 표시 정보. 칸은 reader-R3 이 정한다 |
| 앞 스토리라인 · 이어진 스토리라인 | 이어짐 관계의 두 방향이다. 앞 스토리라인은 보고 있는 스토리라인을 낳은 스토리라인이고, 이어진 스토리라인은 보고 있는 스토리라인이 낳은 스토리라인이다(reader-R21) |

## 3. 공개 인터페이스

모듈 최상위 패키지 `net.yozmnews.reader` 에 공개 타입이 없다. 다른 모듈이 부르는 공개 API 는 없다.

HTTP 엔드포인트는 아래 표와 같다. 모든 경로에 공통인 것:

- 세션이 필요하다
- 생성 조건(`GET /api/reports/conditions`)만 키 없이 여는 표시 `@AllowedWithoutLlmKey` 를 달고(reader-R68), 그 밖은 키 관문 뒤다(account-R5)
- 요청 방어 표시(`@RequestLimited` · `@HumanChecked`)가 없다(account-R32)

| 메서드 · 경로 | 용도 | 규칙 |
|---|---|---|
| `GET /api/user-storylines` | 세션 사용자의 정리된 사용자 스토리라인 목록 | reader-R38 |
| `GET /api/user-storylines/{id}` | 정리된 사용자 스토리라인 하나 | reader-R39 ~ R43 |
| `GET /api/user-storylines/{id}/items` | 그 사용자 스토리라인에 묶인 지난 다이제스트 항목 한 쪽. 질의는 다음 쪽 커서 `beforeDate` · `beforePosition` 이다 | reader-R44 ~ R46 |
| `GET /api/storylines/{id}` | 스토리라인 하나의 타임라인 | reader-R3 ~ R5 |
| `GET /api/storyline-cards` | 스토리라인 ID 여럿의 카드. 질의는 `ids`(쉼표로 이은 ID)다 | reader-R6 |
| `GET /api/digests/latest` | 세션 사용자의 홈 | reader-R9 |
| `GET /api/digests/{date}` | 날짜 하나의 다이제스트 | reader-R9 |
| `GET /api/digests` | 다이제스트가 있는 날짜 한 쪽. 질의는 `before` 이고 다음 쪽은 앞 응답의 `next` 다 | reader-R10 |
| `POST /api/digests` | 수동 생성 요청 | reader-R11 |
| `GET /api/topic-suggestions` | 세션 사용자에게 지금 제안할 주제 | reader-R12 |
| `GET /api/reports` | 세션 사용자의 변화 리포트 한 쪽. 질의는 `kind` · `before` 이고 다음 쪽은 앞 응답의 `next` 다 | reader-R15 |
| `GET /api/reports/{id}` | 변화 리포트 한 편 | reader-R59 ~ R64 |
| `GET /api/reports/conditions` | 변화 리포트의 생성 조건 | reader-R17 |
| `POST /api/reports/{id}/regeneration` | 실패한 변화 리포트 다시 만들기 | reader-R65 · R66 |
| `GET /api/interest-history` | 세션 사용자의 관심사 히스토리. 질의는 `span`(`WEEKS_13` · `WEEKS_26` · `WEEKS_52` · `MONTHS_12` · `MONTHS_24` · `MONTHS_36`)이다 | reader-R8 |

요청 · 응답 형식의 정본은 [`api/openapi.json`](../../api/openapi.json)이다([ADR-0020](../adr/0020-screen-api-contract-from-committed-openapi-spec.md)). 요청의 문제는 400 · 404 문제 응답이다([ADR-0044](../adr/0044-screen-api-results-as-flat-discriminated-200-and-rfc9457-problems.md)).

## 4. 내부 구조

### 개관

패키지와 책임:

- (최상위) — 모듈 선언. 의존을 `storyline` · `event` · `article` · `digest` · `report` · `tag` · `user` · `platform` 으로 둔다(`package-info`)
- `adapter/in/web` — 독자 화면 컨트롤러와 응답 레코드, 아래 보조 맞붙이기

`application` · `domain` 이 없다. 판단이 없기 때문이다([ADR-0192](../adr/0192-reader-screens-are-composed-by-the-reader-module-over-module-public-queries.md)). 컨트롤러는 다른 모듈의 공개 조회 · 공개 API 만 부르고, 결과를 응답 레코드의 정적 팩토리로 옮긴다. 오류 응답은 `platform` 의 문제 응답 형식이다. 부르는 것은 아래와 같다.

| 모듈 | 부르는 공개 조회 · 공개 API |
|---|---|
| `storyline` | `StorylineDirectory`(`find` · `findAll` · `events` · `relationsOf` · `topsOfStorylines`) |
| `user` | `UserStorylineSummaries`(`shown` · `find` · `ofTops`). 그 밖에는 값 타입 `TopicSubscriptionAction` · `GenerationFailureCause` 만 쓴다 |
| `event` | `EventDirectory`(`representativesOfAll` · `articleIdsOf`) |
| `article` | `ArticleDirectory`(`headlines`) |
| `digest` | `ReceivedArticles`(`receivedAmong`) · `InterestHistory`(`recent`) · `TopicSuggestions`(`suggestionsOf`) · `DigestDirectory`(`latest` · `on` · `dates` · `requestStatus` · `itemsOfStorylines`) · `DigestRequests`(`request`) |
| `report` | `ReportDirectory`(`list` · `find`) · `ReportRequests`(`regenerate`) · `ReportConditions`(`current`) |
| `tag` | `TagSetDirectory`(`names`)와 그 결과 `TagSetNames`(`topicName` · `categoryOf` · `topicOrder`) |

보조 맞붙이기는 스프링 구성 요소가 아니고 컨트롤러가 만든다.

- `ScreenRequests` — 요청 매개변수 읽기와 그 400 · 404
- `ScreenArticles` — 기사 정보 맞붙이기(reader-R7)
- `StorylineTitles` — 사건의 대표 기사 맞붙이기(reader-R3)
- `ReceivedMarks` — 사건마다 받은 날짜 맞붙이기(reader-R5)
- `UserStorylineRefs` — 꼭대기마다 그 꼭대기를 가진 정리된 사용자 스토리라인 맞붙이기. 다이제스트와 변화 리포트가 함께 쓴다(reader-R53 · R62)
- `ReportNames` — 변화 리포트가 가리키는 스토리라인 · 사건의 표시 이름 맞붙이기(reader-R62 ~ R64)

역할: 모든 구성 요소가 화면 역할(`@WebRole`)이다(platform-R9).

협력은 과정마다 아래와 같다.

- **타임라인** — `GET /api/storylines/{id}` 가 스토리라인 · 사건 · 관계를 읽고 대표 기사와 받은 날짜를 붙인다(reader-R4 · R5). 대표 기사는 카드 · 사건 · 상위 · 관계 상대의 대표 사건을 모아 한 번에 읽는다(reader-R3 · R7)
- **카드 여럿** — `GET /api/storyline-cards` 가 받은 ID 의 스토리라인을 한 번에 읽어 카드로 낸다(reader-R6)
- **정리된 사용자 스토리라인의 목록** — `user` 의 `shown` 결과를 카드로 옮긴다(reader-R38)
- **개인 흐름 화면** — 사용자 스토리라인 하나를 찾아 추적 대상 꼭대기를 읽거나, 그 추적 대상으로 지난 다이제스트 항목 한 쪽을 읽는다(reader-R13 · R14). 지난 항목은 커서를 검사한 뒤 사용자 스토리라인을 찾는다(reader-R46)
- **날짜 보기** — 홈과 날짜 하나가 `digest` 의 날짜 보기를 읽고 인용 기사의 정보와 항목의 정리를 붙인다(reader-R9). 홈은 날짜 보기를 엮은 뒤 수동 생성 상태를 읽는다(reader-R47)
- **날짜 목록 · 수동 생성** — `digest` 의 날짜 목록 · 수동 생성 요청을 한 번 부르고 결과를 옮긴다(reader-R10 · R11)
- **관심사 히스토리** — `digest` 의 관심사 히스토리를 읽고 주제 이름과 정리된 사용자 스토리라인을 붙인다(reader-R8). `span` 의 열거 변환은 처리기 앞에서 한다(reader-R27)
- **구독 제안** — `digest` 의 구독 제안을 읽고 주제 이름을 붙인다(reader-R12)
- **리포트 목록 · 다시 만들기 · 생성 조건** — `report` 의 조회 · 공개 API 를 한 번 부르고 결과를 옮긴다(reader-R15 · R16 · R17)
- **리포트 한 편** — `report` 의 한 편을 읽고, 인용 · 숫자 줄이 가리키는 스토리라인 · 사건에 표시 이름을 붙인다(reader-R16). 스토리라인 · 사건은 모든 섹션에서 모은 뒤 한 번에 이름을 붙인다(reader-R61 ~ R64)

### 들어오는 쪽

계기는 모두 세션 사용자의 HTTP 요청이고, 주기 · 겹침 방지가 없다. 모든 컨트롤러는 트랜잭션을 열지 않는다. 여러 공개 조회는 한 스냅숏이 아니다. 수동 생성 요청(`DigestRequests.request`)은 트랜잭션 밖에서 불러야 한다. 실패는 [§3](#3-공개-인터페이스) 끝 문단의 문제 응답으로 낸다.

| 진입점 | 구성 요소 | 계기 | 규칙 |
|---|---|---|---|
| 스토리라인 | `StorylineScreenController` | `GET /api/storylines/{id}` · `GET /api/storyline-cards` | reader-R3 ~ R7 |
| 정리된 사용자 스토리라인 | `UserStorylineScreenController` | `GET /api/user-storylines` · `GET /api/user-storylines/{id}` · `GET /api/user-storylines/{id}/items` | reader-R13 · R14 |
| 다이제스트 | `DigestScreenController` | `GET /api/digests/latest` · `GET /api/digests/{date}` · `GET /api/digests` · `POST /api/digests` | reader-R9 ~ R11 |
| 변화 리포트 | `ReportScreenController` | `GET /api/reports` · `GET /api/reports/{id}` · `POST /api/reports/{id}/regeneration` · `GET /api/reports/conditions` | reader-R15 ~ R17 |
| 관심사 히스토리 | `InterestHistoryController` | `GET /api/interest-history` | reader-R8 |
| 구독 제안 | `TopicSuggestionController` | `GET /api/topic-suggestions` | reader-R12 |

### `application`

해당 없음.

### `domain`

해당 없음.

### 나가는 쪽

해당 없음 — 다른 모듈의 공개 조회만 부른다.

## 5. 규칙과 불변식

### 스토리라인 목록

이 주제의 규칙 reader-R1 · R2 는 폐지한 규칙이라 [폐지한 규칙](#폐지한-규칙) 절에 있다. 독자의 스토리라인 목록은 reader-R13 이다.

### 스토리라인 타임라인

- **reader-R3** — 카드는 스토리라인 값(`StorylineView`)과 대표 기사를 아래 칸으로 싣는다. 보도 강도 · 소스 수 · 중요도는 싣지 않는다.

  | 칸 | 값 |
  |---|---|
  | 상태 | 스토리라인의 지금 상태 |
  | 사건 수 · 기사 수 | 닫은 사건 집합에서 센 값 |
  | 첫 · 마지막 기사 시각 | 같은 집합의 값 |
  | 상위 ID | 이 스토리라인을 포함하는 상위 |
  | 대표 기사 | 대표 사건의 대표 기사(reader-R7) |

  - 대표 사건이 합쳐지거나 지워진 뒤 다음 잇기 회차까지는 대표 기사가 없어 제목이 비어 있다. `EventDirectory.representativesOfAll` 이 합쳐진 · 지워진 사건을 빼기 때문이다
- **reader-R4** — 타임라인(`GET /api/storylines/{id}`)의 찾기 · 사건 줄 · 관계를 덮는다.
  - reader-R18 — 없는 스토리라인
  - reader-R19 — 합쳐진 스토리라인
  - reader-R20 — 사건 줄
  - reader-R21 — 관계 나누기
  - reader-R22 — 상위
  - reader-R23 — 상위 · 관계 상대의 표시
  - reader-R24 — 판정 근거를 싣지 않음
- **reader-R5** — 타임라인의 사건마다 그 사건 기사들을 세션 사용자가 받은 날짜 가운데 가장 이른 날짜를 단다. 받은 기사가 없는 사건은 비어 있다. 받은 소식의 뜻은 digest-R13 이다([ADR-0193](../adr/0193-the-per-user-storyline-information-is-the-received-news-counted-from-the-digest-shown-for-each-date.md)).
  - 사건의 기사 ID 는 `EventDirectory.articleIdsOf` 로 얻는다
  - 받은 날짜는 `ReceivedArticles.receivedAmong` 에 세션 사용자로 `ReceivedArticles.MAX_ARTICLES` 개씩 나눠 묻는다
- **reader-R18** — 경로 ID 가 1 미만이거나 `StorylineDirectory.find` 가 빈 값이면 404 다
- **reader-R19** — 합쳐진 스토리라인이면 남는 스토리라인을 낸다. 응답의 ID 가 경로 ID 와 다르다(storyline-R21)
- **reader-R20** — 사건은 `StorylineDirectory.events` 의 순서(첫 기사 시각 · 사건 ID)와 직접 소속인지를 그대로 싣는다. 사건마다 기사 수 · 첫 · 마지막 기사 시각 · 대표 기사를 싣고, 대표는 사건 기사 전체에서 고른 것이다(event-R14)
- **reader-R21** — 관계는 `relationsOf` 를 보고 있는 스토리라인 S 기준으로 나눈다.
  - 포함이고 S 가 앞이면 하위다
  - 이어짐이고 S 가 뒤면 앞 스토리라인, S 가 앞이면 이어진 스토리라인이다
  - 포함이고 S 가 뒤인 고리는 목록에 담지 않는다. 상위는 reader-R22 가 정한다
- **reader-R22** — 상위는 `StorylineView.parent` 하나로 정한다. 상위는 하나다(storyline-R25)
- **reader-R23** — 상위와 관계 상대는 `findAll` 한 번으로 읽어 ID · 대표 기사 제목 · 상태를 싣는다.
  - ID 는 합쳐졌으면 남는 쪽이다
  - 그새 없어진 상대는 뺀다
  - 한 목록에 같은 ID 는 한 번이다
- **reader-R24** — 타임라인은 판정 근거를 싣지 않는다

### 스토리라인 카드

- **reader-R6** — 카드 여럿(`GET /api/storyline-cards`)의 입력과 응답 줄을 덮는다.
  - reader-R25 — `ids` 의 검사
  - reader-R26 — 응답 줄
- **reader-R25** — `ids` 는 받은 값의 수(같은 ID 포함)가 1 ~ 100개(`StorylineDirectory.MAX_PAGE_SIZE`)이고 모두 1 이상이어야 한다. 아니면 400 이다
- **reader-R26** — 응답은 `StorylineDirectory.findAll` 의 결과를 받은 ID 순으로 싣고, 줄마다 받은 ID 와 카드(reader-R3)를 싣는다.
  - 같은 ID 는 처음 자리 한 번만 본다
  - 합쳐진 ID 면 카드의 ID 가 남는 스토리라인이다
  - 없는 ID 는 빠진다

### 관심사 히스토리

판단의 이유는 [ADR-0227](../adr/0227-the-interest-history-shows-subtopic-interest-and-the-users-own-topic-subscription-changes-only.md) · [ADR-0229](../adr/0229-interest-history-periods-are-monday-weeks-and-calendar-months-in-the-users-zone-and-the-query-takes-a-span-or-reference-times.md) · [ADR-0228](../adr/0228-consumer-queries-scoped-by-a-user-argument-may-be-called-on-screen-paths-with-the-session-user.md) · [ADR-0293](../adr/0293-the-interest-history-also-shows-the-interest-of-summarized-flows.md)에 있다. 기간 · 관심 · 변화의 판단은 `digest` 의 것이다(digest-R34 ~ R37).

- **reader-R8** — 관심사 히스토리(`GET /api/interest-history`)의 기간 선택과 응답을 덮는다. 세션 사용자로 `InterestHistory.recent` 를 부른다. 화면 동작은 [web-R15](web.md#관심사-히스토리)다.
  - reader-R27 — 기간 선택
  - reader-R28 — 응답의 칸
  - reader-R29 — 기간 줄
  - reader-R30 — 주제 줄
  - reader-R31 — 주제 이름의 태그 체계 버전
  - reader-R32 — 사용자 스토리라인 줄
  - reader-R33 — 관심이 생긴 · 사라진 사용자 스토리라인
  - reader-R34 — 기준을 싣지 않음
- **reader-R27** — 질의 `span` 이 없으면 `InterestHistorySpan.DEFAULT` 이고, 열거 값이 아니면 400 이다
- **reader-R28** — 응답은 아래 칸을 싣는다. 계정이 없으면(조회가 빈 값) 기간 · 주제 · 사용자 스토리라인이 비어 있다.

  | 칸 | 값 |
  |---|---|
  | `span` | 고른 기간 |
  | `spans[]` | 단위마다 고를 수 있는 기간과 단위를 바꿨을 때의 기간(`defaultOf`). `InterestHistorySpan.Unit` 순 |
  | `periods[]` | 기간. 시간 순이고 마지막이 진행 중이다(reader-R29) |
  | `topics[]` | 주제(reader-R30) |
  | `userStorylines[]` | 정리된 사용자 스토리라인(reader-R32) |

- **reader-R29** — 기간 줄은 아래를 싣는다. 키는 태그 체계 순이다.
  - 첫날 · 마지막 날(사용자 시간대)
  - 진행 중 여부
  - 새로 생긴 관심 · 사라진 관심
  - 사용자가 직접 바꾼 주제 구독(시각 · 더함 / 뺌 · 덮는 중분류)
  - 관심이 생긴 · 사라진 사용자 스토리라인(reader-R33)
- **reader-R30** — 주제는 기간 가운데 한 번이라도 관심 값이 있는 중분류이고 `TagSetNames.topicOrder()` 순이다. 주제마다 아래를 싣는다.
  - 키 · 이름 · 대분류 이름(`TagSetNames.categoryOf` → `topicName`)
  - 기간마다 관심 칸 `level`. 신호가 없으면 null 이다
  - 드러남 `prominent`(digest-R36)
- **reader-R31** — 주제 이름은 조회가 낸 태그 체계 버전(`InterestTimeline.tagSet`)으로 읽는다. 그 버전에 없는 키는 불변식이 깨진 것이라 `IllegalStateException` 이다
- **reader-R32** — 사용자 스토리라인 줄은 지금 정리된 사용자 스토리라인이고 `UserStorylineSummaries.shown` 의 순서다. 줄마다 ID · 정리의 제목 · 기간마다 관심 칸 `level` 을 싣는다.
  - `level` 은 그 기간 `InterestPoint.storylineLevels` 의 헤드 값이고, 없으면 null 이다
  - 헤드는 그 사용자 스토리라인에만 들어, 그 값과 생김 · 사라짐이 그 사용자 스토리라인 하나의 것이다
- **reader-R33** — 기간마다 관심이 생긴 사용자 스토리라인(`enteredUserStorylines`) · 사라진 사용자 스토리라인(`leftUserStorylines`)은 헤드가 그 기간의 `storylineEntered` · `storylineLeft` 에 든 사용자 스토리라인의 ID 다. 정리되지 않은 꼭대기의 변화는 싣지 않는다
- **reader-R34** — 판정 · 관심의 계산은 하지 않는다. θ 와 스토리라인의 기준은 응답에 없다

### 구독 제안

판단의 이유는 [ADR-0249](../adr/0249-topic-suggestions-show-as-a-card-on-the-digest-home-and-accepting-uses-the-tag-preference-save.md) · [ADR-0245](../adr/0245-a-topic-suggestion-is-made-when-the-current-interest-of-an-unsubscribed-subtopic-reaches-its-own-threshold.md) · [ADR-0248](../adr/0248-the-digest-interests-and-topic-suggestion-queries-may-be-called-on-screen-paths-with-the-session-user.md)에 있다. 제안의 판정은 `digest` 의 것이다(digest-R41 · R42).

- **reader-R12** — 구독 제안(`GET /api/topic-suggestions`)의 응답을 덮는다. `digest` 의 `TopicSuggestions.suggestionsOf` 를 세션 사용자로 부른다. 화면 동작(카드 · 수락 · 거절)은 [web-R11](web.md#다이제스트)이다.
  - reader-R35 — 응답의 칸
  - reader-R36 — 이름의 태그 체계 버전
  - reader-R37 — 관심 값 · 기준을 싣지 않음
- **reader-R35** — 빈 값(계정 없음)이면 `tagSetId` 가 null 이고 `topics` 가 빈 목록이다. 있으면 `tagSetId` 는 그 버전 ID 이고, `topics` 는 낸 순서 그대로 중분류마다 키 · 이름 · 대분류 이름(`TagSetNames.categoryOf` → `topicName`)이다
- **reader-R36** — 이름은 제안이 낸 버전으로 읽는다. 그 버전에 없는 키는 불변식이 깨진 것이라 `IllegalStateException` 이다
- **reader-R37** — 관심 값 · 기준은 싣지 않는다

### 정리된 사용자 스토리라인

판단의 이유는 [ADR-0289](../adr/0289-shown-flows-are-user-storylines-with-a-summary-which-the-summary-job-creates-and-removes.md) · [ADR-0290](../adr/0290-the-readers-storyline-tab-shows-the-users-summarized-flows-and-the-tracking-list-has-one-row-per-user-storyline.md) · [ADR-0291](../adr/0291-tokens-spent-on-a-summary-are-shown-on-the-personal-flow-screen-as-the-last-summary-and-the-running-total.md) · [ADR-0292](../adr/0292-reader-screens-call-a-storyline-an-event-flow.md)에 있다. 무엇이 정리된 사용자 스토리라인인지와 정리의 글은 `user` 의 것이다([user Spec](user.md#사용자-스토리라인의-정리) user-R73).

- **reader-R13** — 정리된 사용자 스토리라인의 목록(`GET /api/user-storylines`)과 하나(`GET /api/user-storylines/{id}`)를 덮는다. 화면 동작은 [web-R12](web.md#사건-흐름)다.
  - reader-R38 — 목록
  - reader-R39 — 하나를 찾기
  - reader-R40 — 하나의 응답
  - reader-R41 — 헤드
  - reader-R42 — 꼭대기
  - reader-R43 — 사용량
- **reader-R14** — 사용자 스토리라인에 묶인 지난 다이제스트 항목(`GET /api/user-storylines/{id}/items`)을 덮는다. 화면 동작은 [web-R13](web.md#사건-흐름)이다.
  - reader-R44 — 한 쪽 묻기
  - reader-R45 — 응답
  - reader-R46 — 커서의 검사
- **reader-R38** — 목록은 `UserStorylineSummaries.shown` 을 세션 사용자로 불러 낸 순서(마지막으로 정리한 순) 그대로 카드를 싣는다. 카드의 칸은 아래다.

  | 칸 | 값 |
  |---|---|
  | `id` | 사용자 스토리라인 ID |
  | `title` · `summary` · `recent` | 사용자 스토리라인 전체의 제목 · 흐름 요약 · 최근 전개 |
  | `tracked` | 추적 중인가 |
  | `storylineId` | 헤드의 스토리라인 ID(reader-R41) |
  | `topCount` | 추적 대상 수 |
  | `updatedAt` | 마지막 정리 시각 |

- **reader-R39** — 하나는 `UserStorylineSummaries.find` 로 읽는다. ID 가 1 보다 작거나 빈 값(다른 사용자의 것 · 정리 없음)이면 404 다
- **reader-R40** — 하나의 응답은 카드의 칸(reader-R38)에 꼭대기마다의 글 `tops[]`(reader-R42) · 사용량 `usage`(reader-R43) · 처음 정리 시각을 더한다
- **reader-R41** — 헤드는 `UserStorylineSummaryView.head` 이고 [user-R73](user.md#사용자-스토리라인의-정리)이 정한다. 헤드를 저장하기 전의 개체의 대체도 거기 한 곳에 있다. 흐름 추적 버튼이 이 ID 를 쓴다([web-R12](web.md#사건-흐름))
- **reader-R42** — 꼭대기는 추적 대상을 `StorylineDirectory.findAll` 로 읽어 낸 지금 스토리라인만 싣는다. 꼭대기마다 스토리라인 ID · 상태 · 글 셋을 싣는다.
  - ID 는 합쳐졌으면 남는 쪽이다
  - 그새 없어진 스토리라인은 뺀다
  - 글 없는 꼭대기는 글 셋이 모두 null 이다
- **reader-R43** — 사용량은 금액으로 바꾸지 않고 아래를 싣는다.
  - 마지막 정리의 공급자 키 · 모델 ID · 입력 · 출력 토큰
  - 호출 수
  - 모든 호출의 토큰 합
- **reader-R44** — reader-R39 와 같이 사용자 스토리라인을 찾고, `DigestDirectory.itemsOfStorylines` 를 세션 사용자 · 그 사용자 스토리라인의 저장한 추적 대상으로 부른다(digest-R53). 한 쪽은 20개 (측정 전)이고 `DigestDirectory.MAX_ITEMS` 이하다. 정본은 `UserStorylineScreenController.ITEMS_PAGE_SIZE` 다
- **reader-R45** — 항목마다 다이제스트 날짜 · 종류 · 제목(없으면 null)을 싣고, 다음 쪽 커서 `next`(`beforeDate` · `beforePosition`)를 그대로 낸다
- **reader-R46** — 커서 두 값은 함께 오거나 함께 없다(`ScreenRequests.itemCursor`). 아래면 400 이고, 이 검사는 사용자 스토리라인 찾기(reader-R39)보다 먼저다.
  - 하나만 왔다
  - 날짜가 reader-R48 의 꼴이 아니다
  - 위치가 0 보다 작다

### 기사 정보

- **reader-R7** — 사용자 화면의 기사는 제목 · 출처 이름 · 발행 시각 · 원문 링크(정규 URL)다. `article` 의 `ArticleDirectory.headlines` 를 한 응답에 한 번 부른다. 출처 이름 엮기는 article-R17 이다.
  - 그새 지운 기사는 비어 있다

### 다이제스트

판단의 이유는 [ADR-0237](../adr/0237-the-digest-home-shows-the-latest-date-and-every-date-has-its-own-page-and-a-list.md) · [ADR-0238](../adr/0238-a-digest-date-shows-the-visible-digest-with-the-latest-attempt-and-the-list-holds-every-date-with-a-state.md) · [ADR-0239](../adr/0239-digest-screens-show-tokens-of-the-visible-digest-and-the-sum-of-every-attempt-of-the-date-without-prices.md) · [ADR-0240](../adr/0240-digest-failures-show-the-cause-action-and-the-code-and-never-the-failure-message.md) · [ADR-0262](../adr/0262-chosen-events-are-grouped-into-items-by-containment-tree-top-code-splits-top-stories-and-headlines-the-llm-writes-per-item-and-an-item-has-one-badge.md)(항목마다 배지 하나 · 출처 기사 목록)에 있다.

- **reader-R9** — 홈(`GET /api/digests/latest`)과 날짜 하나(`GET /api/digests/{date}`)의 날짜 보기를 덮는다. 화면 동작은 [web-R10](web.md#다이제스트)이고, 화면의 시각 표기와 실패 알림의 링크는 web-R10 · [web-R19](web.md#알림)이다.
  - reader-R47 — 홈
  - reader-R48 — 날짜의 꼴
  - reader-R49 — 없는 날짜
  - reader-R50 — 날짜 보기의 칸
  - reader-R51 — 홈의 수동 생성 상태
  - reader-R52 — 섹션의 칸
  - reader-R53 — 항목의 정리
  - reader-R54 — 인용
- **reader-R10** — 날짜 목록(`GET /api/digests`)은 `DigestDirectory.dates` 를 한 쪽 30개 (측정 전)로 부르고, 날짜 · 상태 · 다음 쪽 `next` 를 그대로 낸다(digest-R39). 한 쪽의 정본은 `DigestScreenController.DATES_PAGE_SIZE` 이고 `DigestDirectory.MAX_DATES` 이하다.
  - 질의 `before` 는 reader-R48 의 날짜 꼴이어야 한다. 아니면 400 이다
- **reader-R11** — 수동 생성 요청(`POST /api/digests`)을 덮는다. 홈의 수동 생성 상태는 reader-R51 이다.
  - reader-R55 — 결과 옮기기
  - reader-R56 — 요청 방어 표시 없음
- **reader-R47** — 홈은 세션 사용자로 `DigestDirectory.latest`(가장 늦은 날짜의 날짜 보기)와 `requestStatus` 를 부른다. 날짜 보기가 없으면 날짜 보기 칸이 null 이고, 수동 생성 상태가 빈 값(계정 없음)이면 그 칸이 null 이다
- **reader-R48** — 경로 `date` 는 연도 네 자리의 `YYYY-MM-DD` 꼴이고 달력의 날짜여야 한다(`ScreenRequests.date`). 아니면 400 이다
- **reader-R49** — 날짜 하나는 세션 사용자로 `DigestDirectory.on` 을 부르고, 그 날짜의 다이제스트가 없으면 404 다
- **reader-R50** — 날짜 보기는 아래 칸을 싣는다(digest-R38). 실패 메시지는 싣지 않는다.

  | 칸 | 값 |
  |---|---|
  | 날짜 · 이웃 날짜 | 그 날짜와 다이제스트가 있는 앞 · 뒤 날짜 |
  | 보이는 다이제스트 | 아래 목록의 칸 |
  | 가장 최근 시도 | 상태 · 실패 코드 · 실패 원인 · 끝낸 시각 |
  | 사용량 | 그 날짜의 사용량 |

  보이는 다이제스트는 아래를 싣는다.

  - 기간 · 모델
  - 후보 수(사건 수)
  - 토큰 · 끝낸 시각
  - 섹션(reader-R52)

- **reader-R51** — 홈의 수동 생성 상태는 `DigestDirectory.requestStatus`(digest-R40)의 값에 아래를 함께 싣는다.
  - 사용자 시간대(IANA 이름)
  - 오늘 일정 생성 시각. 아직 오지 않았을 때만 싣고, 아니면 null 이다
  - 해결되지 않은 마지막 실패의 날짜 · 끝낸 시각. 없으면 null 이다
- **reader-R52** — 섹션마다 아래 칸을 싣는다. 스토리라인의 추적 상태는 싣지 않는다.

  | 칸 | 값 |
  |---|---|
  | 종류 | 개요 · 주요 뉴스 항목 · 헤드라인 항목 |
  | 제목 | 항목의 제목 |
  | 본문 | 개요 · 주요 뉴스의 요약 · 헤드라인의 코멘트 |
  | `reason` | 선정 이유 하나. 개요는 null 이다([digest-R48](digest.md#다이제스트-조회)) |
  | 인용 | reader-R54 |
  | `eventIds` | 항목의 사건 ID. 위치 순이고 첫 사건이 좋아요 / 싫어요의 항목 키다. 개요는 빈 배열이다([feedback Spec](feedback.md#값과-기록) feedback-R2) |
  | `storylineId` | 항목을 묶은 스토리라인. 스토리라인 포함 나무의 지금 꼭대기다. 개요 · 스토리라인 없는 항목은 null 이다([digest-R38](digest.md#다이제스트-조회)) |
  | `userStorylines` | 그 스토리라인의 정리(reader-R53) |

- **reader-R53** — 항목의 정리 `userStorylines` 는 그 꼭대기를 저장한 추적 대상으로 가진 정리된 사용자 스토리라인 모두의 ID 와 제목이고, 사용자 스토리라인 ID 순이다.
  - 갈라지기 전 스토리라인이면 그 뒤 여러 사용자 스토리라인의 것이 든다
  - 없으면 빈 배열이다
  - 보이는 다이제스트의 서로 다른 `storylineId` 를 `UserStorylineSummaries.ofTops` 에 한 번의 상한(`MAX_TOPS`)씩 나눠 물어 붙인다
- **reader-R54** — 인용마다 기사 ID 와 기사 정보(reader-R7)만 싣는다. 모든 섹션의 인용 기사 정보를 한 응답에 한 번 읽고, 그새 지운 기사의 인용은 뺀다
- **reader-R55** — `DigestRequests.request` 를 세션 사용자로 부르고, 결과 넷을 판별자 `result` 의 200 으로 망라 분기해 옮긴다.

  | `result` | 함께 싣는 것 |
  |---|---|
  | `REQUESTED` | 날짜 · 다시 만들기 여부 |
  | `IN_PROGRESS` | 없음 |
  | `DAILY_LIMIT_REACHED` | 오늘 횟수가 다시 차는 시각 |
  | `NO_USABLE_MODEL` | 없음 |

- **reader-R56** — 수동 생성 요청에는 요청 방어 표시가 없다. 하루 횟수와 진행 중 거절은 `digest` 가 지킨다(digest-R31, [`docs/security.md`](../security.md) 악용 방지)

### 변화 리포트

판단의 이유는 [ADR-0316](../adr/0316-a-change-report-view-carries-each-visible-sections-numbers-as-typed-values-and-resolves-flow-lines-like-citations.md) · [ADR-0317](../adr/0317-a-change-report-row-points-to-the-previous-report-it-took-the-previous-values-from.md) · [ADR-0319](../adr/0319-the-change-report-screen-has-a-list-per-kind-and-a-report-page-with-four-states-and-the-failure-cause-only.md) · [ADR-0338](../adr/0338-a-change-report-writes-only-kind-sections-without-an-overview.md) · [ADR-0339](../adr/0339-change-report-kinds-are-tracked-flows-and-interest-changes-and-it-leaves-out-subscribed-topic-coverage.md) · [ADR-0341](../adr/0341-a-monthly-report-does-not-compare-flow-coverage-with-the-previous-monthly-and-shows-the-flow-state-kept-at-generation.md)에 있다. 비교 · 풀기 · 숨기기 · 기간 날짜의 판단은 `report` 의 것이다([report Spec](report.md#리포트-조회) report-R11 · R12 · R25 · R26).

- **reader-R15** — 리포트 목록(`GET /api/reports`)의 질의와 한 쪽을 덮는다. 화면 동작은 [web-R16](web.md#변화-리포트)이다.
  - reader-R57 — 질의의 검사
  - reader-R58 — 한 쪽
- **reader-R16** — 리포트 한 편(`GET /api/reports/{id}`)과 다시 만들기(`POST /api/reports/{id}/regeneration`)를 덮는다. 화면 동작은 [web-R17](web.md#변화-리포트)이다.
  - reader-R59 — 없는 리포트
  - reader-R60 — 한 편의 칸
  - reader-R61 — 섹션의 칸
  - reader-R62 — 스토리라인의 표시
  - reader-R63 — 사건 인용
  - reader-R64 — 대표 기사 읽기
  - reader-R65 — 다시 만들기의 없는 리포트
  - reader-R66 — 다시 만들기의 결과
- **reader-R17** — 생성 조건(`GET /api/reports/conditions`)을 덮는다. 화면 동작은 [web-R21](web.md#관심-설정)이다.
  - reader-R67 — 응답
  - reader-R68 — 키 관문 밖
- **reader-R57** — 질의의 아래 경우는 400 이다.
  - 종류 `kind` 가 없거나 `ReportPeriodKind` 의 값(`WEEK` · `MONTH`)이 아니다
  - 커서 `before` 가 1 보다 작다(`ScreenRequests.reportCursor`)
- **reader-R58** — `ReportDirectory.list` 를 세션 사용자 · 종류 · 커서로 한 쪽 20개 (측정 전)씩 부른다. 한 쪽의 정본은 `ReportScreenController.REPORTS_PAGE_SIZE` 이고 `ReportDirectory.MAX_PAGE_SIZE` 이하다.
  - 줄마다 아래를 싣는다
    - ID · 종류
    - 기간 첫날 · 마지막 날
    - 상태 · 실패 코드 · 끝낸 시각
  - 다음 쪽 `next` 를 그대로 낸다
  - 실패 메시지는 싣지 않는다
- **reader-R59** — 경로 ID 가 1 보다 작거나 `ReportDirectory.find` 가 빈 값이면 404 다
- **reader-R60** — 한 편은 아래 칸을 싣는다.

  | 칸 | 값 |
  |---|---|
  | 요약 | 그 리포트의 요약 |
  | 직전 리포트 | 생성 때 이은 직전 리포트의 요약. 없으면 null |
  | 숨긴 섹션 수 | 숨긴 종류 섹션 수 |
  | 사용량 | 공급자 · 모델 · 토큰. 시작 전이면 null 이고, 금액으로 바꾸지 않는다 |
  | 섹션 | 보이는 섹션(reader-R61) |
  | `regenerationsLeft` | 남은 다시 만들기 수. `FAILED` 일 때만 싣고 아니면 null 이다([report-R32](report.md#다시-만들기)) |

- **reader-R61** — 섹션마다 아래를 싣는다. 스토리라인 인용과 사용자 스토리라인 줄은 가리키는 스토리라인의 표시(reader-R62)를 칸 `storyline` 에 싣는다.
  - 종류 · 제목 · 본문 · 인용. 제목은 언제나 있고, 모델이 쓴 그 섹션 종류의 요지다
  - 그 섹션 종류의 숫자. 칸은 사용자 스토리라인 보도량과 생성 때 상태 `userStorylines` · 관심 `interests` 둘이고, 그 섹션 종류의 칸이 아니거나 숫자가 없으면 null 이다. 응답은 수를 그대로 싣고, 화면이 수를 보이는 꼴은 [web-R26](web.md#변화-리포트)이다
- **reader-R62** — 스토리라인의 표시는 `ReportNames` 가 붙이고, 정리된 사용자 스토리라인 모두(ID · 제목)와 그 스토리라인의 대표 사건 대표 기사를 싣는다.
  - 정리는 그 스토리라인의 지금 꼭대기(`StorylineDirectory.topsOfStorylines`, `MAX_TOP_STORYLINES` 씩)를 `UserStorylineSummaries.ofTops`(`MAX_TOPS` 씩)에 물어 얻는다
  - 대표 기사는 `findAll` 로 읽은 대표 사건의 것이다(reader-R3)
  - 생성 뒤 다른 스토리라인의 하위로 들어간 스토리라인도 지금 꼭대기의 정리로 잇는다
- **reader-R63** — 사건 인용은 대표 기사(reader-R7)를 싣고, 찾지 못하면 null 이다
- **reader-R64** — 대표 기사는 스토리라인과 사건을 모아 한 응답에 한 번 읽는다
- **reader-R65** — 다시 만들기는 경로 ID 가 1 보다 작거나 `ReportRequests.regenerate` 가 빈 값이면 404 다. 빈 값은 그 사용자의 리포트가 아니거나 없다는 뜻이다
- **reader-R66** — 다시 만들기의 그 밖은 200 이다. 결과는 판별자 `result`(`REQUESTED` · `NOT_FAILED` · `LIMIT_REACHED` · `NO_USABLE_MODEL`)와 지금 상태 `status` 다.
  - `status` 는 `NOT_FAILED` 일 때만 싣고 그 밖은 null 이다
  - 받는 조건 · 횟수는 `report` 가 정한다(report-R32)
- **reader-R67** — `report` 의 `ReportConditions.current` 를 그대로 낸다(`minDigestDays` · `minWeeklyReports`, [report-R28](report.md#생성-조건)). 사용자의 데이터가 아니라 누구의 세션이든 같은 값이다
- **reader-R68** — 생성 조건은 키 관문 밖이다(`@AllowedWithoutLlmKey`). 관심 설정 화면이 키 없이도 열리고 이 값을 쓴다

### 폐지한 규칙

- **reader-R1** — (폐지 — 공용 스토리라인의 독자 목록을 내지 않는다. 독자의 목록은 reader-R13)
- **reader-R2** — (폐지 — reader-R1 과 같다)

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
| [0193](../adr/0193-the-per-user-storyline-information-is-the-received-news-counted-from-the-digest-shown-for-each-date.md) | 사용자마다 달라지는 스토리라인 정보는 타임라인의 받은 소식 하나이고, 날짜별로 보이는 다이제스트의 인용으로 센다(사용자마다 달라지는 스토리라인 정보는 ADR-0287 이 부분 대체) |
| [0227](../adr/0227-the-interest-history-shows-subtopic-interest-and-the-users-own-topic-subscription-changes-only.md) | 관심사 히스토리는 주제 중분류의 관심과 사용자가 직접 바꾼 주제 구독만 보인다(관심사 히스토리의 단위는 ADR-0293 이 부분 대체) |
| [0228](../adr/0228-consumer-queries-scoped-by-a-user-argument-may-be-called-on-screen-paths-with-the-session-user.md) | 사용자를 인자로 받는 소비자 조회는 화면 경로에서 세션 사용자로 부를 수 있다 |
| [0229](../adr/0229-interest-history-periods-are-monday-weeks-and-calendar-months-in-the-users-zone-and-the-query-takes-a-span-or-reference-times.md) | 관심사 히스토리의 기간은 관심 설정 시간대의 월요일 시작 주 · 1일 시작 월이고, 화면은 정해 둔 기간 수 가운데 하나를 고른다 |
| [0237](../adr/0237-the-digest-home-shows-the-latest-date-and-every-date-has-its-own-page-and-a-list.md) | 다이제스트 홈은 가장 늦은 날짜의 다이제스트를 보이고, 날짜마다 화면과 날짜 목록을 둔다 |
| [0238](../adr/0238-a-digest-date-shows-the-visible-digest-with-the-latest-attempt-and-the-list-holds-every-date-with-a-state.md) | 날짜 보기는 보이는 다이제스트와 가장 최근 시도를 함께 내고, 날짜 목록은 다이제스트가 있는 모든 날짜를 상태와 함께 낸다 |
| [0239](../adr/0239-digest-screens-show-tokens-of-the-visible-digest-and-the-sum-of-every-attempt-of-the-date-without-prices.md) | 토큰은 보이는 다이제스트의 것과 그 날짜 모든 시도의 합을 보이고 금액으로 바꾸지 않는다 |
| [0240](../adr/0240-digest-failures-show-the-cause-action-and-the-code-and-never-the-failure-message.md) | 실패는 실패 원인의 할 일과 코드의 무슨 일 두 층으로 보이고, 실패 메시지는 화면에 내지 않는다 |
| [0241](../adr/0241-selection-reasons-mark-only-citations-outside-subscribed-topics-named-interest-and-big-topic.md) | 선정 이유는 구독 주제가 아닌 인용에만 표시하고, 화면 이름은 구독 주제 · 관심 반영 · 큰 화제다(인용에 표시하는 것은 ADR-0262 가 부분 대체) |
| [0245](../adr/0245-a-topic-suggestion-is-made-when-the-current-interest-of-an-unsubscribed-subtopic-reaches-its-own-threshold.md) | 구독 제안은 구독하지 않은 활성 중분류의 지금 관심이 "관심 있음" 기준과 따로 둔 제안 기준 이상일 때 낸다 |
| [0248](../adr/0248-the-digest-interests-and-topic-suggestion-queries-may-be-called-on-screen-paths-with-the-session-user.md) | 다이제스트가 읽는 관심 설정과 구독 제안의 조회는 화면 경로에서 세션 사용자로 부를 수 있다 |
| [0249](../adr/0249-topic-suggestions-show-as-a-card-on-the-digest-home-and-accepting-uses-the-tag-preference-save.md) | 구독 제안은 다이제스트 홈에 카드로 보이고 수락은 태그 선호 저장을 쓴다 |
| [0262](../adr/0262-chosen-events-are-grouped-into-items-by-containment-tree-top-code-splits-top-stories-and-headlines-the-llm-writes-per-item-and-an-item-has-one-badge.md) | 고른 사건은 포함 나무 꼭대기로 항목을 묶고, 주요 뉴스와 헤드라인은 코드가 가르며, LLM 은 항목마다 쓰고 배지는 항목에 하나다 |
| [0265](../adr/0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md) | 좋아요 / 싫어요는 다이제스트 항목에 한 번 누른다 — 섹션 응답이 항목의 사건 ID 를 싣는다 |
| [0284](../adr/0284-a-digest-item-keeps-the-top-that-grouped-it-on-its-section-and-items-with-a-flow-carry-a-flow-tracking-button.md) | 다이제스트 항목은 묶은 꼭대기를 섹션에 남기고, 스토리라인이 있는 항목에 흐름 추적 버튼을 둔다 |
| [0287](../adr/0287-user-storyline-summaries-are-made-and-stored-by-user-with-the-users-key-and-digest-supplies-the-flow-standing.md) | 사용자 스토리라인의 정리는 `user` 가 사용자 키로 만들고 저장한다 — 이 모듈은 그 공개 조회를 엮는다 |
| [0289](../adr/0289-shown-flows-are-user-storylines-with-a-summary-which-the-summary-job-creates-and-removes.md) | 정리가 있는 사용자 스토리라인만 독자 화면에 보이고, 정리 작업이 정리를 만들고 지운다 |
| [0290](../adr/0290-the-readers-storyline-tab-shows-the-users-summarized-flows-and-the-tracking-list-has-one-row-per-user-storyline.md) | 독자의 스토리라인 탭은 내 정리된 사용자 스토리라인을 보이고, 스토리라인 구독 목록은 사용자 스토리라인마다 한 줄이다 |
| [0291](../adr/0291-tokens-spent-on-a-summary-are-shown-on-the-personal-flow-screen-as-the-last-summary-and-the-running-total.md) | 정리에 쓴 토큰은 개인 흐름 화면에 마지막 정리와 지금까지의 합으로 보인다 |
| [0292](../adr/0292-reader-screens-call-a-storyline-an-event-flow.md) | 독자 화면은 스토리라인을 "사건 흐름"으로 부른다 |
| [0293](../adr/0293-the-interest-history-also-shows-the-interest-of-summarized-flows.md) | 관심사 히스토리는 정리된 사용자 스토리라인의 관심도 보인다 |
| [0294](../adr/0294-a-user-storyline-is-one-per-head-and-a-top-before-a-fork-belongs-to-several-of-them.md) | 정리된 사용자 스토리라인의 손잡이는 헤드이고, 항목의 사용자 스토리라인 링크는 그 스토리라인을 가진 정리된 사용자 스토리라인 모두다 |
| [0316](../adr/0316-a-change-report-view-carries-each-visible-sections-numbers-as-typed-values-and-resolves-flow-lines-like-citations.md) | 변화 리포트 조회는 보이는 종류 섹션마다 스냅숏의 숫자를 싣는다 — 이 모듈은 스토리라인 · 사건의 표시 이름만 붙인다 |
| [0317](../adr/0317-a-change-report-row-points-to-the-previous-report-it-took-the-previous-values-from.md) | 변화 리포트 행은 생성 때 직전 값을 꺼낸 직전 리포트를 가리킨다 — 한 편이 그 링크를 단다 |
| [0319](../adr/0319-the-change-report-screen-has-a-list-per-kind-and-a-report-page-with-four-states-and-the-failure-cause-only.md) | 변화 리포트 화면은 종류마다 목록과 한 편 화면으로 보이고, 상태는 넷, 실패는 코드의 무슨 일 문구만 보인다(실패 화면에 다시 만들기가 없는 것은 ADR-0325 가 부분 대체) |
| [0323](../adr/0323-report-generation-jobs-and-stalled-report-recovery-follow-the-digest-and-a-report-ending-without-a-start-records-the-zone.md) | 리포트의 생성 작업 · 거두기는 다이제스트를 따르고, 시작하지 않고 끝난 리포트도 그때의 시간대를 적는다 |
| [0325](../adr/0325-a-failed-report-is-regenerated-from-the-screen-on-the-same-row-up-to-three-times.md) | 실패한 리포트는 화면에서 같은 행을 다시 생성을 기다리게 해 리포트마다 세 번까지 다시 만든다 |
| [0326](../adr/0326-development-names-call-a-shared-storyline-storyline-a-user-storyline-user-storyline-and-a-judgement-candidate-of-unknown-kind-candidate.md) | 개발 이름은 공유 스토리라인을 `Storyline`, 사용자 스토리라인을 `UserStoryline` 으로 부르고, 종류를 모르는 판정 후보는 `Candidate` 로 부른다 |
| [0338](../adr/0338-a-change-report-writes-only-kind-sections-without-an-overview.md) | 변화 리포트는 개요 없이 종류 섹션만 쓴다 — 섹션의 제목은 언제나 있다 |
| [0339](../adr/0339-change-report-kinds-are-tracked-flows-and-interest-changes-and-it-leaves-out-subscribed-topic-coverage.md) | 변화 리포트의 섹션 종류는 추적한 사용자 스토리라인 · 관심 변화 둘이다 — 주제 보도량 칸이 없다 |
| [0341](../adr/0341-a-monthly-report-does-not-compare-flow-coverage-with-the-previous-monthly-and-shows-the-flow-state-kept-at-generation.md) | 월간 리포트는 사용자 스토리라인 보도량을 견주지 않고 생성 때의 헤드 상태를 보인다 — 숫자 줄이 상태를 싣는다 |
