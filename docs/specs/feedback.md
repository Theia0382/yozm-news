# feedback 모듈 Spec

공통 구조와 쓰는 규칙은 [specs/README.md](README.md)에 있다.

## 1. 책임과 범위

`feedback` 은 사용자가 다이제스트 항목에 누르는 좋아요 / 싫어요 / 없음을 받아, 그 항목에 누른 값과 그 항목의 사건마다의 값을 기록한다 — 관심 신호 가운데 사건에 대한 반응이다([overview.md §6](../overview.md#6-횡단-규약) "신호 이력").

- **항목 기록** — (사용자, 다이제스트, 항목 키)마다 그 항목에 누른 값 하나. 화면이 보이는 값이고 이력이 없다
- **사건마다의 현재 상태와 이력** — (사용자, 사건)마다 지금 값 하나(현재 상태)와, 값이 바뀔 때마다 한 줄씩 더하는 이력. 관심 신호이고 이력이 정본이다. 어느 다이제스트에서 눌렀는지를 함께 남긴다. 사건은 누른 때의 ID 로 남고 고쳐 쓰지 않는다
- **받을 수 있는 항목** — 그 사용자의 다이제스트에서 요청의 사건이 든 항목에만 받는다. 항목의 사건들은 이 모듈이 선언한 공급 인터페이스 `DigestItems` 로 묻고 `digest` 가 답한다
- **지운 사건 · 지운 항목 따르기** — `event` 가 지운 사건의 현재 상태 · 이력 행을 지우고, `digest` 가 지운 항목 섹션의 항목 기록을 지운다
- **화면용 API** — 항목의 누르기 · 되돌리기와, 화면이 쓸 항목 기록 조회
- **소비자 조회** — 다이제스트 후보 선별 · 관심도 · 관심사 히스토리와 `user` 의 사용자 스토리라인 맞추기가 읽는 싫어요 사건과 기간의 이력(`EventFeedback`). 합쳐진 사건은 읽을 때 `event` 로 푼다
- **(미구현)** 탈퇴한 사용자의 항목 기록 · 현재 상태 · 이력 지우기

하지 않는 것:

- 관심도를 계산하지 않는다 — 산식은 노출 기록을 가진 `digest` 의 것이다([pipeline.md §7](../pipeline.md#7-피드백))
- 다이제스트의 내용 · 항목 구성을 읽지 않는다 — 다이제스트는 ID 로만 가리키고, 항목의 사건은 `digest` 가 답한다
- 사건을 합치거나 지우지 않는다 — 합쳐진 사건과 지운 사건은 `event` 의 공개 조회 · 이벤트로 안다
- 요청 제한을 세지 않는다 — 처리기에 표시를 달 뿐이고, 세기와 한도는 `account` 의 것이다([account Spec](account.md#요청-방어) account-R30 · R32)
- 사용자를 인증하지 않는다 — 세션 · 키 관문은 `account` 의 보안 구성이 한다([account Spec](account.md#화면용-api-의-공개와-세션-사용자) account-R5 · R6)

다른 모듈과의 의존과 이벤트 흐름은 [overview.md §5](../overview.md#5-모듈-지도)에 있다.

## 2. 용어

| 용어 | 뜻 |
|---|---|
| 값 | 항목에 누르는 좋아요(`LIKE`) · 싫어요(`DISLIKE`) · 없음(`NONE`) |
| 항목 | 다이제스트의 주요 뉴스 · 헤드라인 섹션 하나 — 한 스토리라인으로 묶은 사건들이다([digest Spec](digest.md#항목-묶기와-가르기) digest-R46) |
| 항목 키 | 항목을 가리키는 사건 — 그 항목의 첫 사건(섹션 사건 위치 0)이다. 항목은 (다이제스트, 항목 키)로 가리킨다 |
| 항목 기록 | (사용자, 다이제스트, 항목 키)의 지금 값 — 좋아요나 싫어요다. 없음이면 행이 없다. 화면이 보이는 값이다 |
| 현재 상태 | (사용자, 사건)의 지금 값 — 좋아요나 싫어요다. 없음이면 행이 없다. 이력의 투영이다 |
| 이력 | 사건마다 값이 바뀔 때 더하는 기록 — 없음으로 되돌린 것도 한 줄이다 |
| 누르기 · 되돌리기 | 항목의 값을 좋아요 · 싫어요로 바꾸는 요청 · 없음으로 바꾸는 요청. 같은 처리기의 같은 요청이다 |
| 누른 다이제스트 | 그 값을 누른 화면의 다이제스트 — 요청이 싣고, 항목을 확인한 뒤 이력과 현재 상태에 남는다 |
| 기록한 사건 · 지금 사건 | 기록한 사건은 누른 때의 사건 ID 다. 지금 사건은 그 사건이 합쳐졌으면 남는 사건, 그대로면 그 사건이고, 지운 사건에는 없다 |
| 마지막으로 누른 값 | 한 지금 사건으로 풀리는 기록한 사건들의 이력 가운데 행 ID 가 가장 큰 행의 값(feedback-R8) |
| 울타리 | 기록과 삭제(지운 사건 · 지운 항목 따르기)가 서로를 지나치지 않게 하는 모듈 하나의 advisory 잠금 — 기록은 공유, 삭제는 배타(feedback-R6) |

## 3. 공개 인터페이스

모듈 최상위 패키지 `net.yozmnews.feedback` 의 공개 타입은 다섯이다 — 값 `FeedbackValue`, 소비자 조회 `EventFeedback` 과 그 값 `FeedbackChange`, 다른 모듈이 구현하는 공급 인터페이스 `DigestItems`, 지운 항목의 항목 기록 지우기 `DigestItemRemoval`. 받는 이벤트는 `event` 의 `EventsRemoved` 다(§4 들어오는 쪽).

HTTP 엔드포인트:

| 경로 | 메서드 | 용도 |
|---|---|---|
| `/api/feedback/{eventId}` | `PUT` | 누르기 · 되돌리기 — 경로는 항목의 사건 하나(화면은 항목 키를 보낸다), 본문은 값과 누른 다이제스트다. `RECORDED`(+지금 값) · `UNCHANGED`(+지금 값) · `NOT_IN_DIGEST`(feedback-R2 ~ R4) |
| `/api/feedback` | `GET` | 항목 기록 — 질의 `digestId` 의 다이제스트에서 세션 사용자가 값을 누른 항목의 항목 키와 값(feedback-R7) |

두 경로 모두 세션이 필요하고 키 관문 뒤이며(키 없이 여는 표시가 없다 — [account Spec](account.md#화면용-api-의-공개와-세션-사용자) account-R5) 사용자 격리 시험에 등록돼 있다(account-R6). `PUT` 에는 요청 제한이 걸려 한도를 넘으면 처리기 앞에서 429 다(feedback-R9). 판정 결과는 200 이고, 요청 · 응답 형식은 `api/openapi.json` 에 있다. 항목의 사건 ID 는 독자 화면의 다이제스트 응답이 섹션마다 싣는다([reader Spec](reader.md#다이제스트) reader-R9).

### FeedbackValue

다이제스트 항목에 누르고 그 항목의 사건마다 기록하는 값.

```java
public enum FeedbackValue { LIKE, DISLIKE, NONE }
```

| 값 | 뜻 |
|---|---|
| `LIKE` | 좋아요 |
| `DISLIKE` | 싫어요 |
| `NONE` | 없음 — 항목 기록 · 현재 상태에는 없고(행이 없다) 이력과 요청에만 있다 |

관련 규칙: feedback-R1

### EventFeedback · FeedbackChange

좋아요 / 싫어요의 소비자 조회 — 사건마다의 기록을 다이제스트 후보 선별 · 관심도 · 관심사 히스토리가 읽고, 기간의 이력은 `user` 의 사용자 스토리라인 맞추기도 읽는다([user-R66](user.md#사용자-스토리라인)). 구현은 `application` 의 `EventFeedbackService` 다.

```java
public interface EventFeedback {
    int MAX_EVENTS = 1000;                                    // 한 번에 묻는 서로 다른 사건 수의 상한 — 정본. 측정 전 목표
    Set<EventId> dislikedAmong(UserId user, Collection<EventId> events);
    List<FeedbackChange> changes(UserId user, Instant from, Instant to);
}

public record FeedbackChange(EventId recorded, @Nullable EventId current, FeedbackValue value, long digestId,
        Instant occurredAt) { }
```

| 멤버 | 뜻 |
|---|---|
| `dislikedAmong` | 주어진 (지금) 사건 가운데 그 사용자의 값이 싫어요인 사건 — 사건마다 그 사건과 그 사건으로 합쳐진 사건들의 마지막으로 누른 값이 싫어요인 것이다(feedback-R8). 같은 사건이 두 번 와도 한 번. 빈 입력이면 빈 집합. 서로 다른 사건이 `MAX_EVENTS` 개를 넘으면 `IllegalArgumentException` |
| `changes` | 그 사용자의 이력 행 가운데 시각이 `from` 이상 · `to` 미만인 것 — 기록한 순서(행 ID 순). 행마다 기록한 사건과 지금 사건을 싣는다. `from` 이 `to` 보다 늦으면 `IllegalArgumentException` |

| `FeedbackChange` 의 필드 | 뜻 | 제약 |
|---|---|---|
| `recorded` | 값을 기록한 사건 — 누른 때의 사건 ID | — |
| `current` | 지금 사건 — 합쳐졌으면 남는 사건, 그대로면 `recorded` 와 같다 | 지운 사건이면 null |
| `value` | 바뀐 뒤의 값 | 없음으로 되돌린 행은 `NONE` |
| `digestId` | 누른 다이제스트의 ID | 1 이상. `digest` 의 다이제스트 ID 다 |
| `occurredAt` | 요청을 받은 시각 | 마이크로초까지 |

- 계약
  - 사용자를 인자로 받는 조회다 — 화면 경로에서는 세션 사용자로만 부른다([ADR-0228](../adr/0228-consumer-queries-scoped-by-a-user-argument-may-be-called-on-screen-paths-with-the-session-user.md)). 사용자는 부르는 쪽이 넘긴 `UserId` 다
  - 읽기 전용 트랜잭션 하나다. 합쳐진 사건은 `event` 의 공개 조회로 풀고 쓰지 않는다(feedback-R8). 역할 애너테이션이 없다(두 역할 공통)
  - 기간을 사용자 시간대의 날로 자르는 일, 행을 지금 사건으로 묶는 일, 주제로 모으는 일은 부르는 쪽이 한다
  - `changes` 의 순서는 기록한 순서다 — 같은 (사용자, 기록한 사건)의 마지막 행의 값이 그 사건의 현재 상태와 같다(feedback-R4). 시각 순으로 다시 정렬하면 이 성질이 깨질 수 있다
  - 지운 사건의 행은 없다(feedback-R5)

`EventId` 는 `event` 의 공개 타입이고 `UserId` 는 `platform` 의 공개 타입이다([platform Spec](platform.md#userid)).

관련 규칙: feedback-R8

### DigestItems

다이제스트의 항목 확인 — 이 모듈이 선언하고 `digest` 가 구현한다([digest Spec](digest.md#항목-확인) digest-R14). 기록이 받을 수 있는 항목을 판정할 때 부른다.

```java
public interface DigestItems {
    List<EventId> itemOf(UserId user, long digestId, EventId event);
}
```

| 멤버 | 뜻 |
|---|---|
| `itemOf` | `digestId` 가 그 사용자의 다이제스트이고 그 사건이 든 항목(주요 뉴스 · 헤드라인 섹션)이 하나면 그 항목의 사건들 — 위치 순이고 첫 사건이 항목 키다. 없는 다이제스트 · 다른 사용자의 다이제스트 · 그 다이제스트가 보이지 않은 사건 · 그 사건이 든 항목이 둘 이상이면 빈 목록 |

- 계약
  - 구현은 앱에 하나다 — `digest` 가 낸다. 이 모듈은 `digest` 의 타입을 쓰지 않고 다이제스트를 `long` ID 로만 든다
  - 그 다이제스트가 날짜별로 보이는 다이제스트([digest Spec](digest.md#받은-소식) digest-R13)인지는 보지 않는다
  - 부르는 쪽의 트랜잭션에 들고 쓰지 않는다

관련 규칙: feedback-R2

### DigestItemRemoval

지운 항목의 항목 기록 지우기 — `digest` 가 지운 기사를 따라 항목 섹션을 지우는 트랜잭션 안에서 부른다([digest Spec](digest.md#지운-기사-따르기) digest-R10). 구현은 `application` 의 `DigestItemRemovalService` 다.

```java
public interface DigestItemRemoval {
    void forget(Map<Long, ? extends Collection<EventId>> itemKeysByDigest);
}
```

| 멤버 | 뜻 |
|---|---|
| `forget` | 다이제스트 ID 마다 주어진 항목 키의 항목 기록을 모든 사용자에 대해 지운다. 빈 입력(모든 다이제스트의 키가 빈 것 포함)이면 아무것도 하지 않는다. 다시 불러도 결과가 같다 |

- 계약
  - 부르는 쪽의 트랜잭션에 들어 울타리를 배타로 잡는다(feedback-R6). 부르는 쪽은 다이제스트 행을 잠근 뒤 부른다 — 기록은 울타리를 먼저 잡되 다이제스트 행을 잠그지 않아 둘 사이에 교착이 없다
  - 사건마다의 현재 상태 · 이력은 건드리지 않는다
  - 역할 애너테이션이 없다(두 역할 공통)

관련 규칙: feedback-R5 · R6

## 4. 내부 구조

### 개관

| 패키지 | 책임 |
|---|---|
| (최상위) | 공개 타입 다섯 |
| `domain/record` | 받을 수 있는 항목의 판정(`FeedbackAcceptance`), 풀린 사건의 값 고르기(`MergedValue` — 값 `LastChange`), 현재 상태와 이력의 저장소(`FeedbackRepository` — 값 `HistoryRow`), 항목 기록의 저장소(`ItemRecordRepository`), 울타리(`RemovalFence`) |
| `application` | 기록(`FeedbackRecording` — 결과 `RecordOutcome`), 화면의 항목 기록 조회(`FeedbackStates`), 소비자 조회의 구현(`EventFeedbackService`), 기록한 사건 풀기(`EventResolution`), 지운 사건 따르기(`FeedbackRemoval`)와 그 리스너, 지운 항목의 항목 기록 지우기(`DigestItemRemovalService`) |
| `adapter/in/web` | 처리기(`FeedbackController`)와 요청 · 응답 |
| `adapter/out/persistence` | 저장소 · 울타리의 JdbcClient 구현 |

저장은 JdbcClient 로 한다 — 항목 기록 · 현재 상태 행은 값 하나이고 쓰기가 조건 있는 한 문장이다([ADR-0014](../adr/0014-spring-data-jdbc.md)).

협력 — 누르기 한 번:

```mermaid
sequenceDiagram
    participant C as FeedbackController
    participant R as FeedbackRecording
    participant F as RemovalFence
    participant D as DigestItems(digest)
    participant E as EventResolution
    participant I as ItemRecordRepository
    participant DB as FeedbackRepository
    C->>R: record(세션 사용자, 사건, 값, 누른 다이제스트, 지금)
    R->>F: 울타리 공유(feedback-R6)
    R->>D: 그 사건이 든 항목의 사건들
    Note over R: 받을 수 있는 항목인가(feedback-R2)
    R->>E: 항목의 사건 가운데 지금 있는 사건(event 의 currentOf)
    R->>I: 항목 기록 한 문장 쓰기(feedback-R3 · R4)
    loop 지금 있는 사건마다 — 사건 ID 오름차순
        R->>DB: 현재 상태 한 문장 쓰기 · 바뀌었으면 이력 한 줄
    end
    R-->>C: Recorded / Unchanged / NotInDigest
```

### 들어오는 쪽

| 진입점 | 구성 요소 | 계기 | 하는 일 |
|---|---|---|---|
| 누르기 · 되돌리기 | `FeedbackController` → `FeedbackRecording` | `PUT /api/feedback/{eventId}` | feedback-R2 ~ R4 · R9. 요청 값 `FeedbackRequest`, 응답 값 `FeedbackResponse` |
| 항목 기록 | `FeedbackController` → `FeedbackStates` | `GET /api/feedback?digestId=` | feedback-R7 · R9. 응답 값 `FeedbackItemsResponse` · `FeedbackItemState` |
| 지운 사건 | `FeedbackEventRemovalListener` → `FeedbackRemoval` | `event` 의 `EventsRemoved` | 그 사건들의 현재 상태 · 이력 행을 지운다(feedback-R5 · R6). 비동기 · 새 트랜잭션이고 이벤트 발행 레지스트리에 기록된다(Spring Modulith `@ApplicationModuleListener`) — 실패하면 다시 처리되고, 다시 처리해도 결과가 같다. 지운 사건 ID 의 순서에 기대지 않는다 |
| 지운 항목 | `DigestItemRemovalService` | `digest` 의 지운 기사 따르기가 부르는 `DigestItemRemoval.forget` | 지운 항목 섹션의 항목 기록을 지운다(feedback-R5 · R6). 부르는 쪽의 트랜잭션 안이다 |

처리기는 화면 역할(`@WebRole`)이다. 반복 작업 · 기동 시 동작은 없다.

### `application`

- `FeedbackRecording` — 누르기 · 되돌리기. 트랜잭션 하나에서 울타리를 공유로 잡고, 요청의 사건이 든 항목을 `DigestItems` 로 찾아 판정한 뒤, 항목의 사건 가운데 지금 있는 사건을 `EventResolution` 으로 확인하고, 항목 기록을 쓰고, 지금 있는 사건마다(사건 ID 오름차순) 현재 상태를 쓰고 바뀌었으면 이력을 더한다. 결과는 `RecordOutcome`(`Recorded` · `Unchanged` · `NotInDigest`)이다
- `FeedbackStates` — 화면의 항목 기록 조회(`itemsOf(UserId, long digestId)` → 항목 키 → 값). 읽기 전용 트랜잭션(feedback-R7)
- `EventFeedbackService` — 공개 조회 `EventFeedback` 의 구현. 읽기 전용 트랜잭션. 싫어요는 `EventResolution.recordedOf` 로 기록한 사건을 모아 `FeedbackRepository.lastChanges` 를 `MergedValue` 에 넘기고, 이력은 `FeedbackRepository.history` 의 행을 `EventResolution.currentOf` 로 푼다(feedback-R8)
- `EventResolution` — 기록한 사건 풀기의 정본. `event` 의 `EventDirectory.currentOf`(사건마다 지금 사건 — 지운 사건은 빠짐) · `absorbedInto`(지금 사건마다 그 사건으로 합쳐진 사건들)를 `EventDirectory.MAX_RESOLVE_EVENTS` 씩 나눠 부른다. `currentOf(기록한 사건들)` 은 기록한 사건 → 지금 사건, `recordedOf(지금 사건들)` 은 그 사건 자신과 합쳐진 사건 → 지금 사건을 낸다
- `FeedbackRemoval` — 지운 사건 따르기. 트랜잭션 하나에서 울타리를 배타로 잡고 그 사건들의 현재 상태 · 이력 행을 지운다
- `FeedbackEventRemovalListener` — `EventsRemoved` 를 받아 `FeedbackRemoval` 을 부른다
- `DigestItemRemovalService` — 공개 API `DigestItemRemoval` 의 구현. 부르는 쪽의 트랜잭션에서 울타리를 배타로 잡고 항목 기록을 지운다

### `domain`

- `FeedbackAcceptance` — 받을 수 있는 항목의 판정의 정본(feedback-R2). 항목의 사건 목록(`DigestItems.itemOf` 의 답)을 받아, 비어 있지 않으면 받는다
- `MergedValue` — 풀린 사건의 값 고르기의 정본(feedback-R8). `of(기록한 사건마다 마지막 이력 행, 기록한 사건 → 지금 사건)` → 지금 사건 → 마지막으로 누른 값(없음 포함). 지금 사건이 없는 행은 버린다
- `FeedbackRepository` — 사건마다의 현재 상태와 이력의 저장소. 현재 상태 쓰기는 값이 바뀌었는지를 돌려준다(feedback-R3 · R4). 사건마다 마지막 이력 행(`lastChanges`), 기간의 이력(`history`), 사건들의 행 지우기(`removeAll`)
- `ItemRecordRepository` — 항목 기록의 저장소. 쓰기는 값이 바뀌었는지를 돌려주는 한 문장이고(feedback-R3), (사용자, 다이제스트)의 기록 읽기(`valuesOf` — 항목 키 순)와 다이제스트마다 항목 키의 기록 지우기(`removeAll`)
- `RemovalFence` — 울타리. 공유로 들어가기 · 배타로 들어가기 — 둘 다 트랜잭션 안에서 부르고 트랜잭션이 끝나면 풀린다(feedback-R6)

aggregate 는 없다 — 항목 기록 · 현재 상태는 행마다 값 하나이고 전이 규칙이 "값이 다르면 바꾼다" 하나다.

### 나가는 쪽

- 포트는 없다 — 외부 의존성이 없다
- 저장소 구현 — `JdbcFeedbackRepository`(현재 상태 쓰기는 한 문장, 사건마다 마지막 이력 행은 `distinct on (event_id)` 를 행 ID 내림차순으로), `JdbcItemRecordRepository`(항목 기록 쓰기는 한 문장), `JdbcRemovalFence`(트랜잭션 advisory 잠금 — 이 모듈의 이름공간 하나)
- 다른 모듈의 공개 조회: `event` 의 `EventDirectory.currentOf` · `absorbedInto`([event Spec](event.md#eventdirectory)) — `EventResolution` 만 부른다
- 신뢰할 수 없는 입력 — 요청의 사건 ID · 다이제스트 ID · 값. 방어는 feedback-R2(받을 수 있는 항목) · feedback-R9(꼴 검증)다

## 5. 규칙과 불변식

### 값과 기록

판단의 이유는 [ADR-0265](../adr/0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md) · [ADR-0266](../adr/0266-feedback-is-recorded-under-the-original-event-id-and-feedback-resolves-merged-events-through-event-when-reading-with-the-last-pressed-value-winning.md) · [ADR-0028](../adr/0028-explicit-signals-and-interest-weighted-candidates.md)에 있다.

- **feedback-R1** — 값은 좋아요 · 싫어요 · 없음이다. 기록은 둘이다
  - 항목 기록은 (사용자, 다이제스트, 항목 키)마다 많아야 한 행이고 좋아요나 싫어요다 — 없음이면 행이 없다. 이력이 없다. 화면이 보이는 값이다
  - 사건마다의 현재 상태는 (사용자, 사건)마다 많아야 한 행이고 좋아요나 싫어요다 — 없음이면 행이 없다. 이력은 사건마다 값이 바뀔 때 한 줄이고 없음으로 되돌린 것도 한 줄이다. 관심 신호의 정본은 이력이고 현재 상태는 그 투영이다. 현재 상태와 이력 행은 누른 다이제스트를 갖는다 — 없음 행은 되돌리기를 누른 다이제스트다
  - 사건은 누른 때의 ID(기록한 사건)로 남는다 — 합쳐져도 재판정으로 기사가 옮겨 가도 고쳐 쓰지 않는다(읽을 때 푸는 일은 feedback-R8). 이력은 고쳐 쓰지 않는다 — 지우는 것은 지운 사건을 따를 때뿐이다(feedback-R5)
- **feedback-R2** — 받을 수 있는 항목: 요청의 다이제스트가 그 사용자의 것이고, 그 다이제스트에서 요청의 사건이 든 항목이 하나 있다(`DigestItems.itemOf` 가 빈 목록이 아니다). 판정의 정본은 `FeedbackAcceptance` 다. 누르기와 되돌리기 모두 같은 확인을 지난다. 그 다이제스트가 날짜별로 보이는 다이제스트인지는 묻지 않는다 — 화면을 열어 둔 사이 같은 날짜가 다시 만들어져도 열어 둔 화면에서 누른 것을 받는다. 받을 수 없으면 아무것도 쓰지 않고 결과는 `NotInDigest` 다
  - 항목의 사건 가운데 지금 없는(지운) 사건이 있어도 받는다 — 항목 기록은 쓰고, 지운 사건의 사건 기록은 쓰지 않는다(feedback-R6). 합쳐진 사건은 지금 있는 사건이고 기록한 사건 ID 로 쓴다
- **feedback-R3** — 누르기 한 번은 항목 기록을 그 값으로 두고, 그 항목의 지금 있는 사건 모두를 그 값으로 둔다 — 없음(되돌리기)이면 항목 기록을 지우고 그 사건들을 모두 없음으로 둔다. 다른 항목에서 그 사건에 남긴 값도 바뀐다
  - 값이 바뀌지 않는 기록은 쓰지 않는다 — 좋아요인 사건에 좋아요, 현재 상태가 없는 사건에 없음. 이력을 더하지 않고 현재 상태의 누른 다이제스트 · 시각도 그대로다(다른 다이제스트에서 같은 값을 다시 눌러도 처음 누른 다이제스트가 남는다). 항목 기록도 같다. 그래서 이미 같은 값인 사건에는 이력 행이 생기지 않는다 — 사건 이력의 "마지막으로 누른 값"(feedback-R8)은 값이 바뀐 누르기 기준이다
  - 항목 기록이나 사건 하나라도 바뀌었으면 결과는 `Recorded`, 아무것도 바뀌지 않았으면 `Unchanged` 다
- **feedback-R4** — 같은 행의 기록은 차례로 선다 — 항목 기록 · 현재 상태의 쓰기는 각각 한 문장이고(넣기 · 값이 다를 때만 고치기, 없음은 지우기) 그 행의 잠금에서 뒤 요청이 앞 요청의 커밋을 기다린 뒤 판정한다. 사건마다 현재 상태 행의 잠금을 쥔 뒤에 이력 행을 더하므로, 같은 값의 동시 요청 둘은 그 사건에 이력 한 줄을 남기고 (사용자, 기록한 사건)의 이력에서 마지막 행(ID 가 가장 큰 것)의 값은 현재 상태와 같다(마지막 행이 없음이면 현재 상태가 없다). 이력의 시각은 요청을 받은 때라 잠금을 기다린 요청끼리 행 순서와 어긋날 수 있다 — 순서의 정본은 행 ID 다
  - 누르기 한 번의 잠금 순서는 항목 기록 행 → 사건 행(사건 ID 오름차순)이다 — 같은 사건을 서로 다른 순서로 담은 두 항목을 동시에 눌러도 교착하지 않는다

### 지운 사건 · 지운 항목 따르기

판단의 이유는 [ADR-0267](../adr/0267-removed-events-are-announced-by-event-and-feedback-deletes-their-feedback-with-recording-and-removal-ordered-by-a-fence.md) · [ADR-0060](../adr/0060-article-deletion-announced-by-removed-article-ids-and-followed-by-referencing-modules.md)에 있다.

- **feedback-R5** — 지운 것을 가리키는 행을 따라 지운다
  - `EventsRemoved` 를 받으면 한 트랜잭션에서 그 사건들의 현재 상태 행과 이력 행을 모든 사용자에 대해 지운다([event Spec](event.md#사건-변화-알림--잇기의-재료) event-R25 — 비어서 지운 사건과 그 사건에 합쳐져 함께 지운 사건). 항목 기록과 다른 사건의 행은 건드리지 않는다. 재판정으로 원래 사건이 비어 지워져도 같다 — 반응은 기사가 옮겨 간 사건을 따라가지 않는다. 다시 처리해도 결과가 같고 지운 사건 ID 의 순서에 기대지 않는다
  - `DigestItemRemoval.forget` 이 불리면 부르는 쪽의 트랜잭션에서 다이제스트마다 주어진 항목 키의 항목 기록을 모든 사용자에 대해 지운다 — `digest` 가 인용이 모두 지운 기사인 항목 섹션을 지울 때 그 섹션의 위치 0 사건으로 부른다([digest Spec](digest.md#지운-기사-따르기) digest-R10). 사건마다의 현재 상태 · 이력은 건드리지 않는다
- **feedback-R6** — 기록과 삭제는 울타리로 차례를 선다. 기록은 울타리를 공유로 잡은 뒤에 항목과 지금 있는 사건을 확인하고, 삭제(지운 사건 · 지운 항목)는 울타리를 배타로 잡고 지운 뒤 트랜잭션이 끝날 때 놓는다. 삭제가 울타리를 얻은 뒤에 시작한 기록은 사건 삭제 · 섹션 삭제가 이미 커밋돼 있어 그 사건의 기록을 쓰지 않거나(feedback-R2) 항목을 찾지 못해 받지 않고, 그 전에 진행 중이던 기록은 삭제보다 먼저 커밋돼 삭제가 그 행을 지운다 — 삭제가 지나간 뒤에는 지운 사건을 가리키는 현재 상태 · 이력 행과 지운 항목을 가리키는 항목 기록이 남지 않는다
  - 지운 항목 따르기의 잠금 순서는 다이제스트 행(`digest`) → 울타리다. 기록은 울타리를 먼저 잡지만 다이제스트 행을 잠그지 않는다(`DigestItems` 는 읽기만) — 둘 사이에 교착이 없다

### 화면의 항목 기록 조회

- **feedback-R7** — 그 다이제스트에서 세션 사용자가 값을 누른 항목의 항목 키와 그 값(좋아요 · 싫어요)을 항목 키 순으로 낸다. 누른 값이 없는 항목은 빠진다. 사건마다의 현재 상태는 읽지 않는다 — 같은 사건이 다른 다이제스트의 다른 항목에서 받은 값은 이 항목에 보이지 않는다. 다이제스트 하나의 항목 수만큼이라 상한을 두지 않는다. 그 다이제스트가 그 사용자의 것인지 보지 않는다 — 항목 기록을 세션 사용자로만 읽어 다른 사용자의 다이제스트 ID 로 물으면 빈 목록이다

### 소비자 조회와 합쳐진 사건 풀기

판단의 이유는 [ADR-0266](../adr/0266-feedback-is-recorded-under-the-original-event-id-and-feedback-resolves-merged-events-through-event-when-reading-with-the-last-pressed-value-winning.md) · [ADR-0191](../adr/0191-the-consumer-query-of-storyline-subscriptions-is-opened-with-the-subscriptions-and-resolves-ids-without-writing.md)(같은 꼴의 선례)에 있다.

- **feedback-R8** — 소비자 조회(`EventFeedback`)는 그 사용자의 것만 읽고, 합쳐진 사건은 읽을 때 `event` 로 푼다 — 풀기의 정본은 `EventResolution` 하나다
  - 마지막으로 누른 값: 한 지금 사건으로 풀리는 기록한 사건들(그 사건과 그 사건으로 합쳐진 사건)의 이력 가운데 행 ID 가 가장 큰 행의 값이다. 그 값이 없음(되돌리기)이면 앞의 다른 사건에 남은 값이 있어도 없음이다. 사건 하나의 마지막 이력 행의 값은 그 사건의 현재 상태와 같아(feedback-R4) 현재 상태를 읽은 것과 같은 값이고, 풀린 사건들 사이는 행 ID 로 견준다. 고르기의 정본은 `MergedValue` 다
  - 싫어요 사건(`dislikedAmong`): 주어진 (지금) 사건마다 마지막으로 누른 값이 싫어요인 사건이다 — 합쳐진 사건의 싫어요는 그보다 늦게 누른 값이 없으면 남는 사건을 싫어요로 만든다
  - 기간의 이력(`changes`): 시각이 반열린 구간(시작 포함 · 끝 제외)에 드는 행을 기록한 순서로 그대로 내고, 행마다 기록한 사건과 지금 사건(지운 사건이면 없음)을 싣는다 — 관심 신호로 쓸 행을 고르고 지금 사건으로 묶는 일은 부르는 쪽이 한다([digest Spec](digest.md#관심도) digest-R25)

### 처리기의 관문

판단의 이유는 [ADR-0198](../adr/0198-feedback-recording-is-request-limited-per-session-user.md) · [ADR-0265](../adr/0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md) · [ADR-0192](../adr/0192-reader-screens-are-composed-by-the-reader-module-over-module-public-queries.md)에 있다.

- **feedback-R9** — 두 처리기는 사용자를 세션에서 채운 `UserId` 인자로만 받는다(account-R6). 키 없이 여는 표시가 없다 — 반응은 다이제스트 화면에서 누르고 그 화면은 키 관문 뒤다. 누르기 · 되돌리기에는 요청 제한 표시(`@RequestLimited(GuardedAction.FEEDBACK)`)가 있다 — 세션 사용자로 세고 한도는 [account Spec](account.md#요청-방어) account-R30 의 정책 표에 있다. 요청 하나에 한 번 세고 결과와 무관하다 — 값이 바뀌지 않는 요청 · 받을 수 없는 항목의 요청도 센다(account-R31). 항목 기록 조회에는 표시가 없다. 꼴 검증: 경로의 사건 ID · 본문의 다이제스트 ID · 조회의 `digestId` 가 1 보다 작으면 400, 본문의 칸이 없거나 값 이름이 틀리면 본문 형식 400, 조회의 `digestId` 가 없거나 숫자가 아니면 400 이다. 받을 수 없는 항목은 오류가 아니라 판정 결과 200 `NOT_IN_DIGEST` 다

## 6. 데이터

컬럼 타입 · 인덱스 · 제약 문면의 정본은 `backend/src/main/resources/db/migration/V72__feedback_by_event.sql` 이다.

| 테이블 | 주요 컬럼 | 뜻 |
|---|---|---|
| `feedback` | user_id, event_id, value(`LIKE`/`DISLIKE`), digest_id, updated_at | 사건마다의 현재 상태(feedback-R1) — (user_id, event_id) 가 기본 키다. 없음이면 행이 없다. `event_id` 는 기록한 사건, `digest_id` 는 그 값을 누른 다이제스트, `updated_at` 은 그 값으로 바뀐 시각이다. 같은 사건의 기록이 차례를 서는 잠금 자리다(feedback-R4) |
| `feedback_events` | id, user_id, event_id, value(`LIKE`/`DISLIKE`/`NONE`), digest_id, occurred_at | 이력(feedback-R1) — 사건마다 값이 바뀔 때 한 줄. 순서의 정본은 `id` 다(feedback-R4). (user_id, event_id, id) 인덱스로 사건마다 마지막 행을 읽는다(feedback-R8) |
| `feedback_items` | user_id, digest_id, item_event_id, value(`LIKE`/`DISLIKE`), updated_at | 항목 기록(feedback-R1) — (user_id, digest_id, item_event_id) 가 기본 키다. `item_event_id` 는 항목 키(그 항목의 첫 사건)다. 없음이면 행이 없고 이력이 없다 |

`user_id` 는 `account` 의 사용자 ID, `event_id` · `item_event_id` 는 `event` 의 사건 ID, `digest_id` 는 `digest` 의 다이제스트 ID 다 — 모듈 사이라 FK 가 없다. 지운 사건과 지운 항목은 feedback-R5 가 따른다(사건 ID 인덱스 · 다이제스트 ID 인덱스). **(미구현)** 탈퇴한 사용자의 행 지우기.

## 7. 설정

해당 없음 — 읽는 설정 키가 없다. 상한 · 한도는 코드 상수다(`EventFeedback.MAX_EVENTS`, 풀기 조회의 상한은 `event` 의 `EventDirectory.MAX_RESOLVE_EVENTS`, 요청 제한은 `account` 의 정책 표).

## 8. 결정

| ADR | 한 줄 |
|---|---|
| [0028](../adr/0028-explicit-signals-and-interest-weighted-candidates.md) | 관심 신호는 명시적 행동만 쓰고, 추가 전용 이벤트 기록이 정본이다 |
| [0060](../adr/0060-article-deletion-announced-by-removed-article-ids-and-followed-by-referencing-modules.md) | 지운 것을 ID 로 알리고, 가리키는 모듈이 받아 자기 행을 지운다 — 사건 삭제도 같은 꼴이다 |
| [0125](../adr/0125-screen-api-handlers-need-a-session-unless-marked-public-and-take-the-user-only-as-a-session-filled-argument.md) | 화면용 API 처리기는 세션 사용자를 인자로만 받고 격리 시험에 등록한다 |
| [0191](../adr/0191-the-consumer-query-of-storyline-subscriptions-is-opened-with-the-subscriptions-and-resolves-ids-without-writing.md) | 소비자 조회는 신호를 만든 작업이 열고 소비자의 필요는 비워 둔다 |
| [0192](../adr/0192-reader-screens-are-composed-by-the-reader-module-over-module-public-queries.md) | 사용자가 정해 두는 것의 화면은 그 데이터를 가진 모듈이 연다 |
| [0196](../adr/0196-feedback-is-accepted-only-on-articles-cited-by-the-users-digest-and-digest-answers-through-an-interface-declared-by-feedback.md) | 그 다이제스트가 날짜별로 보이는 다이제스트인지는 묻지 않고, 다이제스트의 확인은 `feedback` 이 선언한 인터페이스를 `digest` 가 구현해 답한다 |
| [0198](../adr/0198-feedback-recording-is-request-limited-per-session-user.md) | 반응 기록에 세션 사용자 축 요청 제한을 건다 |
| [0228](../adr/0228-consumer-queries-scoped-by-a-user-argument-may-be-called-on-screen-paths-with-the-session-user.md) | 사용자를 인자로 받는 소비자 조회는 화면 경로에서 세션 사용자로 부를 수 있다 |
| [0265](../adr/0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md) | 반응은 다이제스트 항목에 한 번 누르고 그 항목의 사건들에 기록하며, 화면은 항목에 누른 값을 보이고 신호는 사건 기록이 맡는다 |
| [0266](../adr/0266-feedback-is-recorded-under-the-original-event-id-and-feedback-resolves-merged-events-through-event-when-reading-with-the-last-pressed-value-winning.md) | 반응은 원래 사건 ID 로 기록하고 읽을 때 `event` 로 합쳐진 사건을 풀며, 풀린 사건들의 값은 마지막으로 누른 값이다 |
| [0267](../adr/0267-removed-events-are-announced-by-event-and-feedback-deletes-their-feedback-with-recording-and-removal-ordered-by-a-fence.md) | 지운 사건의 반응은 `event` 의 알림으로 지우고, 지운 항목의 항목 기록은 `digest` 가 같은 트랜잭션에서 지우게 하며, 기록과 삭제는 울타리로 차례를 세운다 |
