# ADR-0268: 관심도의 노출 · 반응 단위는 사건이고 항목 하나의 무게를 그 사건들에 1/n 씩 나누며, 같은 사건은 가장 늦은 노출의 몫으로 한 번 센다

- 상태: 승인. 부분 대체 — 주제 관심에 드는 사건([ADR-0277](0277-storyline-interest-is-counted-per-containment-tree-top-with-the-topic-formula-and-reactions-to-a-flow-seen-within-seven-days-go-to-the-storyline-only.md)) · 스토리라인 관심의 노출 E([ADR-0298](0298-storyline-interest-drops-silent-exposures-before-the-latest-reacted-exposure-per-flow-and-topics-and-tracking-are-left-out.md))
- 날짜: 2026-11-20
- 근거: [이슈 #226](https://github.com/Theia0382/yozm-news/issues/226) — 관심도의 노출 · 반응 단위를 사건으로, 항목 하나의 노출 · 반응을 그 항목의 사건들에 1/n 씩 나누기로 정하고(사건마다 1 로 세면 사건 10개짜리 큰 슬롯 항목의 반응 한 번이 노출 10 · 반응 10 이 되어 가상 노출 3 을 압도한다), 같은 사건이 몫이 다른 항목에 여러 번 노출되면 가장 늦은 노출의 몫을 노출 · 반응에 함께 쓰기로 정했다. 측정 인스턴스 사용자 하나가 받은 사건 280 가운데 18 이 다이제스트 두 날 이상에 나왔다. 구현 커밋 `a948926`
- 관련: [ADR-0222](0222-interest-per-subtopic-is-smoothed-reactions-over-exposures-with-a-half-life-and-exposures-are-all-ready-citations.md)(부분 대체 — 결정의 "노출 기사", 산식의 노출 E · 반응 F, "노출은 인용 · 기사마다 한 번"), [ADR-0263](0263-articles-sent-per-event-are-sampled-up-to-a-cap-with-a-fixed-seed-from-those-in-the-period-and-the-sample-is-the-citations-and-sources.md)(부분 대체 — 결정 셋째의 "인용이 노출 기록"), [ADR-0261](0261-event-topics-and-regions-are-keys-on-at-least-half-of-evaluated-members-importance-is-the-maximum-and-digest-counts-them-at-query-time.md)(사건의 주제), [ADR-0262](0262-chosen-events-are-grouped-into-items-by-containment-tree-top-code-splits-top-stories-and-headlines-the-llm-writes-per-item-and-an-item-has-one-badge.md)(항목 — 한 사건은 다이제스트 하나에서 한 항목에만 든다), [ADR-0265](0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md)(반응은 항목에 누르고 사건마다 기록), [ADR-0266](0266-feedback-is-recorded-under-the-original-event-id-and-feedback-resolves-merged-events-through-event-when-reading-with-the-last-pressed-value-winning.md)(합쳐진 사건 풀기)

## 맥락

- 관심도는 주제 중분류마다 (반응 + 구독 신호) ÷ (노출 + 가상 노출 3)이고 노출 시각으로 감쇠한다. 노출은 그 사용자의 `READY` 다이제스트(덮인 것 포함)의 인용 기사였고 기사마다 한 번이었다([ADR-0222](0222-interest-per-subtopic-is-smoothed-reactions-over-exposures-with-a-half-life-and-exposures-are-all-ready-citations.md)).
- 반응은 항목에 한 번 누르고 그 항목의 사건마다 기록한다([ADR-0265](0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md)). 기사 단위 노출에 사건 단위 반응을 나누면 분자와 분모의 단위가 다르다.
- 다이제스트는 섹션(항목)마다 그 항목의 사건을 섹션 사건 행으로 남긴다. 항목의 사건 수는 하나부터 큰 슬롯 항목의 열 개 넘게까지 다르다.
- 사건의 주제는 평가 있는 소속 기사의 절반 이상에 붙은 중분류다([ADR-0261](0261-event-topics-and-regions-are-keys-on-at-least-half-of-evaluated-members-importance-is-the-maximum-and-digest-counts-them-at-query-time.md)).
- 같은 사건이 다이제스트 두 날 이상에 나오면 날마다 항목의 구성이 다를 수 있다 — 첫날 사건 셋인 항목에서 1/3, 다음 날 혼자인 항목에서 1. 사건이 뒤에 합쳐지면 같은 다이제스트의 행 여럿이 한 사건으로 풀린다.

## 결정

> 부분 대체(주제 관심에 드는 사건) — [ADR-0277](0277-storyline-interest-is-counted-per-containment-tree-top-with-the-topic-formula-and-reactions-to-a-flow-seen-within-seven-days-go-to-the-storyline-only.md). 지금 상태: [digest Spec "관심도"](../specs/digest.md#관심도) digest-R23 · R50
- **노출 · 반응의 단위는 사건이고, 주제는 사건의 주제다.** 노출은 그 사용자의 `READY` 다이제스트(같은 날짜의 덮인 다이제스트 포함) 가운데 끝낸 시각이 범위 안인 것의 섹션 사건 행이다.
- **섹션 사건 행마다 몫은 1 ÷ 그 항목의 사건 행 수다.** 항목 하나의 무게 1 을 그 사건들에 나눈다. 사건 행 수는 생성 때 항목에 든 수라 그 뒤 지운 사건도 센다 — 화면에 보인 구성이다.
- **노출 행의 사건은 지금 사건으로 푼다** — 합쳐진 사건은 남는 사건으로, 지운 사건의 행은 버린다.
- **같은 사건은 창 안에서 한 번 센다.** 노출 시각은 가장 늦은 다이제스트((끝낸 시각, 다이제스트 ID)가 가장 큰 것)의 끝낸 시각이고, 몫은 그 다이제스트에서 그 사건으로 풀린 행들의 몫 합이며 1 을 넘으면 1 이다 — 한 항목의 사건들이 합쳐지면 그 항목의 무게가 남는 사건에 모이고, 서로 다른 항목에서 한 사건으로 풀리면 사건마다 한 번으로 자른다.
> 부분 대체 — 둘째 문장의 노출 E · 반응 F 에 드는 사건은 [ADR-0277](0277-storyline-interest-is-counted-per-containment-tree-top-with-the-topic-formula-and-reactions-to-a-flow-seen-within-seven-days-go-to-the-storyline-only.md), 둘째 문장의 스토리라인 관심의 노출 E 는 [ADR-0298](0298-storyline-interest-drops-silent-exposures-before-the-latest-reacted-exposure-per-flow-and-topics-and-tracking-are-left-out.md). 지금 상태: [digest Spec "관심도"](../specs/digest.md#관심도) digest-R23 · R49 · R50
- **반응은 그 사건의 창 안 이력 가운데 지금 사건으로 묶은 행의 기록 순 마지막 값이고, 노출과 같은 시각 · 몫으로 무게를 매긴다.** 산식의 노출 E 는 사건의 w(노출 시각) × 몫 합, 반응 F 는 같은 사건의 w(노출 시각) × 몫 × (좋아요 +1 · 싫어요 −1 · 없음 0) 합이다.
- 산식의 꼴 · 상수 · 기준 시각과 읽는 범위, 덮인 다이제스트를 노출로 세는 것은 그대로다.

## 결과

- 사건 수가 다른 항목의 반응이 같은 무게 1 로 든다 — 큰 슬롯 항목의 반응 한 번이 가상 노출을 압도하지 않는다.
- 노출 · 반응이 같은 몫이라 반응이 노출을 넘지 않는다.
- 반응을 누른 항목보다 큰 몫으로 셀 수 있다 — 사건 셋인 항목에서 누른 사건이 다음 날 혼자 나오면 몫 1 로 센다.
- 여러 날 보인 사건은 가장 늦은 노출 하나로 줄어 분모를 키우지 않는다.
- 구독하지 않은 중분류의 노출은 그 중분류가 사건의 주제인 사건(큰 슬롯 사건 · 여러 주제에 걸친 구독 사건)에서 생긴다.
- 지운 사건의 노출 · 반응은 관심도에서 빠진다. 노출 사건 수천의 풀기 · 소속 기사 · 평가 읽기 비용은 재지 않았다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 사건마다 몫 1 로 센다 | 사건이 많은 항목의 반응 한 번이 노출 · 반응 여럿이 되어 가상 노출을 압도한다 |
| 노출은 가장 늦은 몫, 반응은 누른 항목의 몫으로 센다 | 같은 사건에 몫이 둘이고, 반응 행에서 누른 항목을 다시 찾아야 하며, 몫이 작은 쪽에서 누르면 반응이 작게 남는다 |
| 노출을 항목 노출마다 따로 쌓는다 | "사건마다 한 번"과 다르고, 여러 날 보인 사건은 대부분 묶기 잔여라 그 잡음이 분모를 키운다 |
| 같은 다이제스트에서 한 사건으로 풀린 행의 몫 가운데 큰 값을 쓴다 | 한 항목의 사건들이 합쳐졌을 때 그 항목의 무게 일부가 사라진다 |
| 노출을 인용 기사로 두고 반응만 사건으로 센다 | 분자와 분모의 단위가 달라 반응이 노출을 넘을 수 있다 |
