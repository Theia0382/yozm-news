# ADR-0282: 흐름 추적은 사용자 스토리라인을 가리키고, 사용자 스토리라인은 `user` 의 처리기가 잠그고 저장하며 읽는 쪽은 같은 규칙의 결과를 받는다

- 상태: 승인. 부분 대체 — 사용자 스토리라인을 저장하는 계기([ADR-0287](0287-user-storyline-summaries-are-made-and-stored-by-user-with-the-users-key-and-digest-supplies-the-flow-standing.md)) · 추적 중인 흐름 목록의 줄([ADR-0290](0290-the-readers-storyline-tab-shows-the-users-summarized-flows-and-the-tracking-list-has-one-row-per-user-storyline.md))
- 날짜: 2026-11-22
- 근거: [이슈 #244](https://github.com/Theia0382/yozm-news/issues/244) — 사용자 스토리라인([ADR-0281](0281-a-user-storyline-is-a-flow-that-received-interest-signals-and-tracks-the-tops-reached-forward-along-follows-from-its-seeds-as-one-group.md))을 어디에 저장하고 누가 고쳐 쓰는지, 구독 행 · 이력 · 화면이 그것과 어떻게 이어지는지를 리드가 정했다. 구현 커밋 `3db16e1`
- 관련: [ADR-0188](0188-storyline-subscriptions-follow-retired-storylines-by-event-and-before-each-use-and-notices-point-to-the-remaining-event.md)(부분 대체 — 결정 "`user` 는 그 이벤트를 받아 구독을 바로 고쳐 쓰고 …": 구독 행은 사용자 스토리라인을 가리키고 따라가기는 사용자 스토리라인의 맞추기가 한다), [ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)(보충 — 스토리라인 구독은 `user`), [ADR-0191](0191-the-consumer-query-of-storyline-subscriptions-is-opened-with-the-subscriptions-and-resolves-ids-without-writing.md)(보충 — 소비자 조회가 내는 것은 추적 씨앗이다), [ADR-0190](0190-storyline-subscription-handlers-are-open-without-a-key.md)(보충 — 추적 상태 조회도 키 없이 연다), [ADR-0228](0228-consumer-queries-scoped-by-a-user-argument-may-be-called-on-screen-paths-with-the-session-user.md) · [ADR-0248](0248-the-digest-interests-and-topic-suggestion-queries-may-be-called-on-screen-paths-with-the-session-user.md)(보충 — 사용자 스토리라인 조회도 화면 경로에서 세션 사용자로 불린다), [ADR-0189](0189-subscriptions-to-ended-storylines-stay-and-the-query-carries-the-current-state.md)(목록이 지금 상태를 싣는다 — 그대로)

## 맥락

- 구독 행은 사용자 · 스토리라인 ID 마다 하나였고, 합치거나 지운 스토리라인을 따라 고쳐 썼다(ADR-0188).
- 사용자 스토리라인은 신호 이력과 지금 관계에서 나온다(ADR-0281) — 반응 · 관계 변화마다 달라질 수 있다.
- 다이제스트 생성 작업과 화면의 관심 조회는 읽기만 하는 소비자다. 소비자 조회는 쓰지 않는다(ADR-0191).
- 관심 창의 길이는 `digest` 의 관심도가 갖는다.

## 결정

- **흐름 추적(스토리라인 구독)의 현재 상태는 사용자 스토리라인을 가리킨다.** 추적 중인 사용자 스토리라인마다 한 행이고, 사용자가 누른 스토리라인은 그 사용자 스토리라인의 추적 씨앗으로 남는다. 상한은 추적 중인 사용자 스토리라인 수이고 새로 추적할 때만 막는다.
- **구독 이력은 공용 스토리라인 ID 를 적고 추적 씨앗과 일대일이다.** 추적 씨앗을 더하고 뺄 때마다 한 행이고, 원인은 사용자 · 합쳐짐 · 사라짐 · 갈라져 물려받음이다. 지금 추적 씨앗은 이력의 재생이다.
- **해제는 그 스토리라인의 꼭대기가 든 사용자 스토리라인의 추적을 끈다.** 추적 씨앗을 모두 빼고 씨앗마다 이력을 남긴다. 사용자 스토리라인은 지우지 않는다.
> 부분 대체 — [ADR-0287](0287-user-storyline-summaries-are-made-and-stored-by-user-with-the-users-key-and-digest-supplies-the-flow-standing.md). 지금 상태: [`user` user-R53 · R68](../specs/user.md#사용자-스토리라인의-정리)
- **저장은 `user` 의 처리기(추적 · 해제 · 목록 · 추적 상태)와 `StorylinesRetired` 리스너가 설정 행을 잠근 뒤에 한다.** 새 이벤트를 두지 않는다.
- **읽는 소비자는 쓰지 않고 같은 규칙의 결과를 받는다.** 공개 조회는 저장 행과 받은 창의 신호 · 지금 관계로 맞춘 결과를 계산해 낸다 — 아직 저장되지 않은 묶음은 ID 가 없다. 창의 시작은 `digest` 가 구현하는 공급 인터페이스로 받는다.
> 부분 대체(셋째 문장) — [ADR-0290](0290-the-readers-storyline-tab-shows-the-users-summarized-flows-and-the-tracking-list-has-one-row-per-user-storyline.md). 지금 상태: [`user` user-R52](../specs/user.md#스토리라인-구독)
- **추적 상태 조회 하나가 스토리라인 ID 를 받아 그 흐름을 추적 중인지 낸다.** 모든 화면의 버튼이 그것을 읽는다. 목록은 추적 씨앗마다 한 줄이고 사용자 스토리라인 ID 를 함께 싣는다.

## 결과

- 한 흐름의 어느 스토리라인에서 물어도 추적 상태가 같다. 추적 중인 흐름의 다른 스토리라인을 누르면 추적 씨앗만 는다.
- 반응만 하고 `user` 의 처리기를 거치지 않은 사용자의 사용자 스토리라인은 저장되지 않은 채 읽기에서 계산된다 — 저장 여부와 무관하게 같은 값이다.
- 저장 행이 언제 고쳐졌는지는 결과에 든다(갈라진 조각의 물려받기 — ADR-0281).
- 추적 씨앗이 지워져도 흐름이 남아 추적이 넘어가면 사라진 구독 알림은 없다. 알림은 추적 중이던 사용자 스토리라인이 이을 흐름 없이 사라질 때 남는다.
- 목록의 줄 수는 추적 중인 사용자 스토리라인 수보다 많을 수 있다.
- `user` 가 `feedback` 의 좋아요 / 싫어요 이력을 읽는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 읽는 소비자도 맞추며 저장한다 | 생성 작업 · 화면 조회가 설정 행 잠금을 잡고, 읽는 공개 조회가 사용자 데이터를 고친다 |
| 반응 · 관계 변화마다 이벤트로 저장한다 | 이벤트가 둘 늘고, 이벤트가 닿기 전의 틈을 쓰는 자리 앞에서 다시 메워야 한다 |
| 사용자 스토리라인을 `digest` 나 새 모듈에 둔다 | 추적(구독)과 같은 자리에 있어야 한 잠금 · 한 트랜잭션으로 맞는다 |
| 추적 여부를 사용자 스토리라인의 칸으로 둔다 | 구독의 현재 상태 표와 이력 표의 짝이 깨진다 |
| 이력에 사용자 스토리라인 ID 를 적는다 | 합쳐지고 갈라지면 ID 가 바뀐다 — 스토리라인 ID 를 적고 읽을 때 푸는 쪽이 지금 이력과 같은 꼴이다 |
| 화면마다 추적 대상 목록을 받아 견준다 | 목록이 사용자 스토리라인의 추적 대상 전부를 실어야 하고 화면마다 판정을 다시 쓴다 |
