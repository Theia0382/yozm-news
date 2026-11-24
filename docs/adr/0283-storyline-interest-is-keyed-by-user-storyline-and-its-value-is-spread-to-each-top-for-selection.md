# ADR-0283: 스토리라인 관심의 키는 사용자 스토리라인이고, 값을 그 꼭대기마다 펼쳐 선별에 쓴다

- 상태: 승인. 부분 대체 — 꼭대기의 사용자 스토리라인 하나와 모든 꼭대기에 같은 값([ADR-0294](0294-a-user-storyline-is-one-per-head-and-a-top-before-a-fork-belongs-to-several-of-them.md))
- 날짜: 2026-11-22
- 근거: [이슈 #244](https://github.com/Theia0382/yozm-news/issues/244) — 스토리라인 관심이 꼭대기마다라 앞 흐름의 관심이 이어진 뒤 흐름에 닿지 않았다. 스토리라인 관심을 사용자 스토리라인([ADR-0281](0281-a-user-storyline-is-a-flow-that-received-interest-signals-and-tracks-the-tops-reached-forward-along-follows-from-its-seeds-as-one-group.md))의 값으로 두고 공용 꼭대기 관심을 따로 두지 않기로 정했다. 구현 커밋 `3185742`
- 관련: [ADR-0277](0277-storyline-interest-is-counted-per-containment-tree-top-with-the-topic-formula-and-reactions-to-a-flow-seen-within-seven-days-go-to-the-storyline-only.md)(부분 대체 — 관심의 키 "포함 나무의 꼭대기마다"와 반복의 범위 "그 사건의 꼭대기"), [ADR-0278](0278-followed-flows-are-tops-at-or-above-the-follow-threshold-or-subscribed-less-the-excluded-and-the-keep-threshold-is-0-2.md)(부분 대체 — 관심 흐름의 "지금 구독 중인 스토리라인의 꼭대기"), [ADR-0279](0279-storyline-interest-multiplies-the-candidate-rank-and-flows-at-or-below-the-exclusion-threshold-leave-subscribed-candidates-and-the-big-slot.md) · [ADR-0280](0280-events-of-followed-flows-outside-the-subscribed-topics-enter-one-per-flow-within-a-share-of-the-subscribed-seats-as-interest.md)(보충 — 값이 사용자 스토리라인에서 온다, 규칙은 그대로), [ADR-0215](0215-interests-are-not-stored-entities-and-are-computed-from-signal-history-at-query-time.md)(보충 — 관심 값은 저장하지 않는다, 키가 되는 묶음은 `user` 가 낸다)

## 맥락

- 스토리라인 관심은 포함 나무의 꼭대기마다 세고(ADR-0277), 그 값으로 순위를 곱하고 · 거르고 · 관심 흐름을 정한다(ADR-0278 ~ 0280).
- 사용자 스토리라인은 이어진 흐름을 한 묶음으로 갖는다(ADR-0281). 묶음은 `user` 의 공개 조회가 낸다(ADR-0282).
- 선별은 사건의 꼭대기로 항목을 묶고 꼭대기마다의 값을 읽는다.

## 결정

> 부분 대체(둘째 문장) — [ADR-0294](0294-a-user-storyline-is-one-per-head-and-a-top-before-a-fork-belongs-to-several-of-them.md). 지금 상태: [digest Spec "관심도"](../specs/digest.md#관심도) digest-R49
- **스토리라인 관심의 키는 사용자 스토리라인이다.** 노출 · 반응은 사건의 꼭대기마다 그 꼭대기의 사용자 스토리라인에 1/k 로 들고(같은 사용자 스토리라인의 꼭대기 둘이면 더한다), 추적 신호는 그 스토리라인의 꼭대기의 사용자 스토리라인에 든다. 사용자 스토리라인이 없는 꼭대기는 스토리라인 관심이 없다(중립).
> 부분 대체 — [ADR-0294](0294-a-user-storyline-is-one-per-head-and-a-top-before-a-fork-belongs-to-several-of-them.md). 지금 상태: [digest Spec "관심도"](../specs/digest.md#관심도) digest-R49 · R51
- **값은 그 사용자 스토리라인의 모든 꼭대기에 같은 값으로 펼친다.** 순위 · 거름 · 큰 슬롯 · 관심 흐름은 꼭대기마다의 값을 그대로 읽는다.
- **다시 본 흐름(반복)의 범위는 사용자 스토리라인이다.** 사용자 스토리라인이 없는 꼭대기는 그 꼭대기가 범위다.
- **관심 흐름의 "추적한 흐름"은 추적 중인 사용자 스토리라인의 모든 꼭대기다.**
- **추적 신호는 원인이 사용자인 이력 행마다 ±1 이다.** 갈라져 물려받은 추적은 신호가 아니다.
- 묶음은 관심 재료를 읽는 범위와 같은 창으로 읽는다. 기준 시각이 여럿인 조회도 지금의 묶음 하나를 함께 쓴다.

## 결과

- 앞 흐름을 거듭 싫어요한 사용자에게 뒤 흐름의 사건과 큰 판정 행이 걸러지고, 추적한 흐름이 이어진 뒤 흐름의 사건은 주제 구독 밖이어도 든다.
- 이어진 앞 흐름을 본 뒤 7일 안의 뒤 흐름 첫 사건은 다시 본 흐름이라 그 반응이 주제 관심에 들지 않는다.
- 관심 신호를 준 적 없는 흐름의 노출은 스토리라인 관심에 들지 않는다 — 노출만으로 값이 0 쪽으로 눌리지 않는다.
- 한 흐름의 스토리라인 둘을 추적하면 +2, 해제하면 −2 다. 물려받은 추적을 해제하면 더함 없이 −1 이 든다.
- 과거 기준 시각의 값도 지금의 관계로 낸 묶음으로 센다 — 지난 관계는 저장하지 않는다.
- 선별의 코드와 사건 → 꼭대기 풀기는 바뀌지 않는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 꼭대기 관심과 사용자 스토리라인 관심을 둘 다 둔다 | 같은 신호를 두 번 세고, 어느 값으로 거르는지 자리마다 정해야 한다 |
| 선별도 사용자 스토리라인을 키로 고친다 | 항목 묶기와 함께 쓰는 사건 → 꼭대기 풀기를 바꿔야 하고, 꼭대기마다 펼치면 같은 결과다 |
| 키를 사용자 스토리라인 ID 로 쓴다 | 저장되지 않은 묶음은 ID 가 없다 — 계산에는 묶음의 구분만 필요하다 |
| 사용자 스토리라인이 없는 꼭대기도 노출을 센다 | 신호 없는 흐름마다 값이 생기고, 사용자 스토리라인의 값이라는 뜻이 흐려진다 |
| 물려받은 추적도 신호로 센다 | 사용자가 하지 않은 일이 관심을 올린다 |
