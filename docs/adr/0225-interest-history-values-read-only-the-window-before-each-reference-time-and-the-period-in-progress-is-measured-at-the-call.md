# ADR-0225: 관심사 히스토리의 기간 값은 기준 시각 앞 120일의 창만 읽어 다이제스트와 같은 산식으로 계산하고, 진행 중인 기간은 부르는 시각을 기준으로 한다

- 상태: 승인. 부분 대체 — 결과의 스토리라인 구독 주제 몫([ADR-0277](0277-storyline-interest-is-counted-per-containment-tree-top-with-the-topic-formula-and-reactions-to-a-flow-seen-within-seven-days-go-to-the-storyline-only.md))
- 날짜: 2026-11-18
- 근거: [이슈 #189](https://github.com/Theia0382/yozm-news/issues/189) — 관심도 재료 읽기의 범위와 감쇠를 대조해 지난 기간 값에 그 뒤 신호가 섞이는 것을 확인하고 리드와 정함. 구현 커밋 `77bfe43` · `7d7a1f8` · `bb602eb`
- 관련: [ADR-0222](0222-interest-per-subtopic-is-smoothed-reactions-over-exposures-with-a-half-life-and-exposures-are-all-ready-citations.md)(결정 "기준 시각은 기간 끝이고, 읽는 범위의 끝은 부르는 시각이다" — 다이제스트에 적용되는 규칙으로 밝힘, 보충), [ADR-0215](0215-interests-are-not-stored-entities-and-are-computed-from-signal-history-at-query-time.md)(결정 "관심사 히스토리도 기간마다 같은 산식으로 계산해 견준다" · 결과 "기간 여럿을 매번 계산한다(비용 미측정)" — 보충), [ADR-0028](0028-explicit-signals-and-interest-weighted-candidates.md)(관심도는 조회할 때 계산한다)

## 맥락

- 관심사는 저장하지 않고 신호 이력에서 조회할 때 계산하며, 히스토리도 기간마다 같은 산식으로 계산해 견준다([ADR-0215](0215-interests-are-not-stored-entities-and-are-computed-from-signal-history-at-query-time.md)).
- 다이제스트의 관심도는 기준 시각을 기간 끝으로 두고 [기간 끝 − 120일, 부르는 시각)을 읽는다 — 같은 기간을 다시 만들 때 앞 다이제스트의 노출과 그 뒤 누른 반응을 넣으려는 것이다. 감쇠는 기준 시각 뒤의 시각을 무게 1 로 센다([ADR-0222](0222-interest-per-subtopic-is-smoothed-reactions-over-exposures-with-a-half-life-and-exposures-are-all-ready-citations.md)).
- 이 읽는 범위를 지난 기간에 그대로 쓰면 그 기간 끝 뒤의 노출 · 좋아요 · 구독 변경이 무게 1 로 들어가 지난 기간 값이 오늘 행동으로 바뀐다.
- 기간 여럿을 계산할 때 재료를 기간마다 읽으면 52주에 재료 읽기가 53번이다.

## 결정

- **기간 값은 다이제스트와 같은 산식을 기준 시각 T 에서 계산하고, 재료는 창 [T − 120일, T)의 행만 쓴다.** 노출은 기사마다 창 안 가장 늦은 노출 시각, 반응은 창 안 기록 순 마지막 값, 구독 변경은 창 안 행이다. 기간 끝 뒤 신호는 그 기간에 들지 않는다.
- **지난 기간의 기준 시각은 기간 끝, 진행 중인 기간의 기준 시각은 부르는 시각이다** — "지금까지의 관심"이다.
- **재료는 첫 기준 시각의 창 시작부터 마지막 기준 시각까지 한 번 읽고, 기준 시각마다 창으로 자른다.** 다이제스트도 같은 길을 쓴다 — 다이제스트의 창 끝은 부르는 시각이라 [ADR-0222](0222-interest-per-subtopic-is-smoothed-reactions-over-exposures-with-a-half-life-and-exposures-are-all-ready-citations.md)의 읽는 범위 그대로다.

## 결과

> 부분 대체(마지막 문장) — [ADR-0277](0277-storyline-interest-is-counted-per-containment-tree-top-with-the-topic-formula-and-reactions-to-a-flow-seen-within-seven-days-go-to-the-storyline-only.md). 지금 상태: [digest Spec "관심도"](../specs/digest.md#관심도) digest-R25
- 지난 기간 값은 그 뒤의 행동으로 바뀌지 않는다. 다만 운영자가 기사를 지우거나(인용 · 좋아요 이력이 함께 지워진다) 산식 · 상수를 바꾸면 지난 기간도 바뀐다([ADR-0215](0215-interests-are-not-stored-entities-and-are-computed-from-signal-history-at-query-time.md) 결과와 같다). 스토리라인 구독의 주제 몫은 지금 그 스토리라인의 구성으로 센다([ADR-0214](0214-a-storyline-subscription-enters-topic-interest-by-the-topic-shares-of-its-event-representatives.md)).
- "범위 안 기사마다 가장 늦은 노출" 고르기가 저장소 질의에서 `domain` 의 창 고르기로 옮겨 한 곳에 있다 — 노출 저장소는 (기사, 끝낸 시각) 행을 낸다.
- 가장 긴 선택지(36개월)는 읽는 범위가 약 1,215일이고 기준 시각마다 재료 전체를 다시 훑는다. 읽기 비용 · 지연은 재지 않았다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 다이제스트와 같은 읽는 범위([T − 120일, 부르는 시각))를 지난 기간에도 쓴다 | 지난 기간 값에 그 뒤 신호가 무게 1 로 섞여 "기간별 관심"이 뜻을 잃는다 |
| 진행 중인 기간도 기간 끝을 기준 시각으로 둔다 | 아직 오지 않은 시각에서 본 값이 된다 — 오늘 신호도 감쇠한다 |
| 기준 시각마다 재료를 따로 읽는다 | 기간 수만큼 노출 기사 평가 · 스토리라인 몫을 다시 읽는다 |
| 히스토리 전용 재료 읽기를 따로 둔다 | 같은 재료 읽기와 "가장 늦은 노출" 고르기가 두 벌이 된다 |
