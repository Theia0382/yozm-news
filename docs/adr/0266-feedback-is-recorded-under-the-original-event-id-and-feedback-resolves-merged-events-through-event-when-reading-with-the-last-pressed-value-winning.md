# ADR-0266: 반응은 원래 사건 ID 로 기록하고 읽을 때 `feedback` 이 `event` 로 합쳐진 사건을 풀며, 풀린 사건들의 값은 마지막으로 누른 값이다

- 상태: 승인
- 날짜: 2026-11-20
- 근거: [이슈 #226](https://github.com/Theia0382/yozm-news/issues/226) — 원래 사건 ID 로 기록하고 읽을 때 푸는 꼴, 풀기를 `feedback` 이 하는 것, 합쳐진 사건들에 서로 다른 값이 모이면 마지막으로 누른 값, 합쳐진 사건의 싫어요가 남는 사건을 빼는 귀결을 정했다. 구현 커밋 `cc5da40`(`event` 의 여러 사건 풀기 조회) · `a948926`(`feedback` 의 풀기 · 값 고르기 · 소비자 조회)
- 관련: [ADR-0228](0228-consumer-queries-scoped-by-a-user-argument-may-be-called-on-screen-paths-with-the-session-user.md)(보충 — 결정의 대상 목록 가운데 `ArticleFeedback` 은 사건 단위 `EventFeedback` 이 되었다. 화면 경로에서 세션 사용자로 부를 수 있는 것은 그대로다), [ADR-0265](0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md)(사건마다 기록), [ADR-0191](0191-the-consumer-query-of-storyline-subscriptions-is-opened-with-the-subscriptions-and-resolves-ids-without-writing.md) · [ADR-0188](0188-storyline-subscriptions-follow-retired-storylines-by-event-and-before-each-use-and-notices-point-to-the-remaining-event.md)(선례 — 기록을 가진 모듈이 대상 모듈에 의존해 풀고 기록 ID 와 푼 ID 를 함께 낸다), [ADR-0084](0084-event-merge-applies-assignment-scoring-between-events-and-keeps-merged-rows-pointing-to-survivors.md)(합쳐진 사건은 남는 사건을 가리키는 행으로 남는다), [ADR-0028](0028-explicit-signals-and-interest-weighted-candidates.md)(신호는 추가 전용 이력이 정본이다)

## 맥락

- 사건은 병합으로 합쳐진다. 합쳐진 사건은 상태 `MERGED` 로 남아 남는 사건을 바로 가리키고(가리킴이 평평하다), 그 배정은 남는 사건으로 옮겨 간다([ADR-0084](0084-event-merge-applies-assignment-scoring-between-events-and-keeps-merged-rows-pointing-to-survivors.md)). 재판정으로 기사가 다른 사건으로 옮겨 가기도 한다.
- 반응은 누른 때의 사건에 남는다. 뒤에 그 사건이 합쳐지면 후보 선별 · 관심도는 남는 사건을 다룬다.
- 스토리라인 구독은 합쳐진 스토리라인의 현재 상태를 남는 스토리라인으로 고쳐 쓰고, 소비자 조회는 기록 ID 와 지금 ID 를 함께 낸다([ADR-0188](0188-storyline-subscriptions-follow-retired-storylines-by-event-and-before-each-use-and-notices-point-to-the-remaining-event.md) · [ADR-0191](0191-the-consumer-query-of-storyline-subscriptions-is-opened-with-the-subscriptions-and-resolves-ids-without-writing.md)).
- `event` 의 공개 조회로는 사건을 하나씩만 풀 수 있었다(`find`). 여러 사건을 지금 사건으로, 남는 사건을 그 사건으로 합쳐진 사건들로 한 번에 푸는 길이 없었다.
- 사건 a 에 싫어요, 사건 b 에 좋아요가 남은 뒤 a 가 b 에 합쳐지면 남는 사건 b 하나에 값이 둘이다.

## 결정

- **반응은 누른 때의 사건 ID 로 기록하고 고쳐 쓰지 않는다** — 현재 상태도 이력도 그대로다. 재판정으로 기사가 옮겨 가도 반응은 원래 사건에 남는다.
- **읽을 때 `feedback` 이 `event` 로 푼다.** `event` 는 공개 조회 둘을 낸다 — 사건마다 지금 사건(합쳐졌으면 남는 사건, 지운 사건은 빠짐)과 남는 사건마다 그 사건으로 합쳐진 사건들. 한 번에 묻는 사건 수에 상한이 있고 부르는 쪽이 나눠 부른다. `feedback` 안의 풀기 정본은 하나이고, `feedback` → `event` 의존이 생긴다.
- **소비자 조회의 이력 행은 기록한 사건과 지금 사건(지웠으면 없음)을 함께 싣는다.** 행을 지금 사건으로 묶는 일은 부르는 쪽이 한다.
- **한 사건으로 풀리는 사건들의 값은 마지막으로 누른 값이다** — 그 사건과 그 사건으로 합쳐진 사건들의 이력 가운데 행 ID 가 가장 큰 행의 값이고, 그 값이 없음(되돌리기)이면 앞의 다른 사건에 값이 남아 있어도 없음이다. 사건 하나의 마지막 이력 행이 그 사건의 현재 상태와 같으므로 현재 상태 표를 읽은 것과 같은 값이다. 고르는 판단은 `feedback` 의 `domain` 한 곳이다.
- **싫어요 조회는 주어진 (지금) 사건마다 이 값으로 답한다.** 합쳐진 사건의 싫어요는 그보다 늦게 누른 값이 없으면 남는 사건을 후보에서 뺀다. 관심도는 창 안 이력에서 같은 규칙(지금 사건으로 묶은 행의 기록 순 마지막 값)을 쓴다.

## 결과

- 합치기 · 재판정이 반응 행을 고치지 않아 기록 경로가 사건 변화를 따라가지 않는다. 대신 읽을 때마다 풀기 조회가 한 번 더 돈다 — 비용은 재지 않았다.
- 작은 사건에 누른 싫어요가 그 사건이 합쳐진 큰 사건을 통째로 뺀다. 큰 사건에 더 늦게 좋아요를 눌렀으면 빠지지 않는다.
- 오래전 싫어요가 합쳐진 뒤의 최근 좋아요에 묻혀 거름이 풀릴 수 있다 — 사용자의 최근 판단을 따른다.
- 재판정으로 원래 사건이 비어 지워지면 그 반응은 옮겨 간 사건을 따라가지 않는다([ADR-0267](0267-removed-events-are-announced-by-event-and-feedback-deletes-their-feedback-with-recording-and-removal-ordered-by-a-fence.md)).
- 이미 같은 값인 사건은 이력 행이 생기지 않아, "마지막으로 누른 값"은 값이 바뀐 누르기 가운데 마지막이다.
- 스토리라인 구독과 달리 현재 상태도 읽을 때 푼다 — 원인을 남길 이력 행이 없다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 합치기를 받아 현재 상태를 남는 사건으로 고쳐 쓴다(스토리라인 구독의 꼴) | 사건 변화마다 반응 행을 고치는 길이 생기고, 값이 둘 모일 때의 처분을 쓰는 순간에 정해야 한다. 이력과 현재 상태의 사건이 달라진다 |
| `feedback` 은 원래 ID 를 그대로 내고 `digest` 가 푼다 | 풀기가 소비자마다 생겨 정본이 둘이 된다 |
| 합쳐진 사건들 가운데 싫어요를 앞세운다 | 싫어요 때문에 그 사건이 다시 보이지 않아 사용자가 고칠 길이 막히고, 최근의 좋아요가 관심도에 들지 않는다 |
| 마지막 누르기를 현재 상태 행에서 고른다(이력 행 ID 칸 · 바뀐 시각) | 되돌리기는 현재 상태 행을 지워, 마지막 누르기가 없음일 때 앞의 다른 사건 값이 살아난다. 이력 행 ID 칸은 현재 상태를 넣는 문장이 이력보다 먼저라 채울 수 없고, 바뀐 시각은 잠금 대기로 행 순서와 어긋날 수 있다 |
