# ADR-0277: 스토리라인 관심은 포함 나무의 꼭대기마다 주제와 같은 산식으로 세고, 7일 안에 다시 본 흐름의 반응 · 노출과 스토리라인 구독은 스토리라인에만 든다

- 상태: 승인. 부분 대체 — 관심의 키인 포함 나무의 꼭대기와 반복의 범위([ADR-0283](0283-storyline-interest-is-keyed-by-user-storyline-and-its-value-is-spread-to-each-top-for-selection.md)) · 스토리라인 묶음의 처음 값([ADR-0295](0295-the-subscription-signal-of-interest-is-a-term-outside-the-exposure-denominator-and-the-topic-value-is-below-the-presence-threshold.md))
- 날짜: 2026-11-22
- 근거: [이슈 #243](https://github.com/Theia0382/yozm-news/issues/243) — 측정 인스턴스의 반응 기록에서 싫어요 74건 가운데 48건이 이미 본 흐름에 눌렸고, 싫어요한 흐름이 다시 나온 항목 83개 가운데 55개가 다시 싫어요였다. 그 싫어요가 사건의 주제로 들어 주제 관심을 끌어내렸다. 리드가 흐름 단위 관심의 꼴 · 나누는 기준 · 반복의 기간 · 스토리라인 구독의 행선지를 골랐다. 구현 커밋 `b1cf943` · `ab1bba5` · `1e95385`
- 관련: [ADR-0214](0214-a-storyline-subscription-enters-topic-interest-by-the-topic-shares-of-its-event-representatives.md)(전체 대체 — 스토리라인 구독은 주제 관심에 들지 않는다), [ADR-0261](0261-event-topics-and-regions-are-keys-on-at-least-half-of-evaluated-members-importance-is-the-maximum-and-digest-counts-them-at-query-time.md)(부분 대체 — 스토리라인 구독의 주제 몫), [ADR-0225](0225-interest-history-values-read-only-the-window-before-each-reference-time-and-the-period-in-progress-is-measured-at-the-call.md)(부분 대체 — 결과의 스토리라인 구독 주제 몫), [ADR-0245](0245-a-topic-suggestion-is-made-when-the-current-interest-of-an-unsubscribed-subtopic-reaches-its-own-threshold.md)(부분 대체 — 맥락의 구독 신호에 든 스토리라인 구독 주제 몫), [ADR-0268](0268-interest-counts-exposures-and-reactions-per-event-splitting-an-item-weight-one-over-n-and-counting-an-event-once-with-its-latest-share.md)(부분 대체 — 주제 관심에 드는 사건을 좁힌다), [ADR-0222](0222-interest-per-subtopic-is-smoothed-reactions-over-exposures-with-a-half-life-and-exposures-are-all-ready-citations.md)(보충 — 같은 산식을 꼭대기 키에도 쓴다), [ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)(보충 — 관심사에 스토리라인 관심이 든다), [ADR-0264](0264-the-storyline-tops-query-gives-each-event-its-containment-tree-roots-in-membership-score-order-without-the-scores.md)(꼭대기 조회)

## 맥락

- 관심도는 주제 중분류마다 반응을 노출로 평활해 센다(ADR-0222). 반응은 다이제스트 항목에 누르고 그 항목의 사건마다 기록된다(ADR-0265). 사건의 주제는 평가된 소속 기사의 절반 이상에 붙은 키다(ADR-0261).
- 같은 흐름(스토리라인 포함 나무)의 사건이 여러 날 이어서 다이제스트에 든다. 그 흐름이 싫어서 누른 싫어요는 그 흐름 사건의 주제 관심으로 들어가, 같은 주제의 다른 소식까지 순위를 잃는다. 측정에서는 이미 본 흐름에 눌린 싫어요가 싫어요의 3분의 2 였다.
- 스토리라인 구독은 지금 그 스토리라인 사건의 주제 몫으로 주제 관심에 든다(ADR-0214). 흐름을 구독했다는 신호가 그 흐름이 아닌 같은 주제 전체를 올린다.
- 포함 나무의 꼭대기는 `storyline` 이 사건마다 내는 공개 조회가 있다(ADR-0264). 꼭대기는 상위가 하위의 사건까지 세는 흐름 하나의 단위다.

## 결정

> 부분 대체 — 아래 세 항목의 키(꼭대기마다 · 그 사건의 꼭대기 · 지금 꼭대기의 스토리라인 관심)는 [ADR-0283](0283-storyline-interest-is-keyed-by-user-storyline-and-its-value-is-spread-to-each-top-for-selection.md), 첫 항목의 "스토리라인 묶음의 처음 값은 주제 묶음과 같다"는 [ADR-0295](0295-the-subscription-signal-of-interest-is-a-term-outside-the-exposure-denominator-and-the-topic-value-is-below-the-presence-threshold.md). 지금 상태: [digest Spec "관심도"](../specs/digest.md#관심도) digest-R49 · R50
- **스토리라인 관심은 포함 나무의 꼭대기마다 주제 관심과 같은 산식으로 센다.** 산식(평활 · 반감기 감쇠 · 가중치 변환)은 키 종류에 무관한 하나이고, 상수는 키 종류마다 묶음으로 둔다 — 스토리라인 묶음의 처음 값은 주제 묶음과 같다. 사건의 꼭대기가 k 개면 그 사건은 꼭대기마다 몫 1/k 로 든다.
- **사건의 첫 노출 날짜 앞 7일 안(앞선 날짜)에 그 사건의 꼭대기가 노출됐으면 다시 본 흐름의 사건(반복)이다.** 꼭대기가 없는 사건은 주제 관심에만, 처음 본 흐름의 사건은 주제 · 스토리라인 관심 둘 다, 반복인 사건은 스토리라인 관심에만 든다. 노출과 반응은 같은 길을 간다.
- **스토리라인 구독 변경은 그 스토리라인의 지금 꼭대기의 스토리라인 관심에만 든다.** 주제 관심에는 들지 않는다.
- 꼭대기 · 반복은 조회 때 지금 관계로 정한다 — 스토리라인마다 꼭대기는 `storyline` 의 공개 조회로 푼다.

## 결과

- 이미 본 흐름에 거듭 누른 싫어요가 주제로 새지 않는다. 처음 본 흐름의 반응은 주제 이유인지 흐름 이유인지 가를 수 없어 둘 다에 든다 — 흐름의 첫 싫어요부터 스토리라인 관심이 내려간다.
- 같은 흐름이 매일 이어지면 둘째 날부터의 노출은 주제의 분모에 들지 않는다 — 주제 관심 값이 지금보다 반응 쪽으로 기운다. 주제 상수 조정은 바뀐 값 위에서 한다.
- 반복의 기준은 그 사건의 창 안 첫 노출이다 — 같은 사건이 뒤에 다시 나와도 판정이 바뀌지 않는다. 창 시작 7일 안의 첫 노출은 그 앞을 보지 못해 반복이 아니다(창 시작의 무게는 1/16).
- 포함 관계가 나중에 맺어지면 지난 노출의 꼭대기와 반복 판정도 바뀐다 — 관심을 저장하지 않는 성질(ADR-0215) 그대로다.
- 스토리라인 구독은 주제 구독 제안(digest-R41)에 기여하지 않는다. 관심도 재료가 스토리라인마다 사건 · 평가를 읽던 길이 꼭대기 조회 둘로 바뀐다.
- 상수 · 7일은 측정 전 목표다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 흐름에 마지막으로 누른 값과 구독만 보고 일정 기간 유지한다 | 노출 대비가 없어 큰 흐름과 작은 흐름이 같은 무게이고, 관심사 히스토리에 낼 크기가 없다 |
| 반응 횟수를 누적해 옮긴다 | 노출이 많은데 반응이 한 번인 흐름과 노출 한 번에 반응 한 번인 흐름이 같고 주제와 뜻이 다르다 |
| 처음 본 흐름의 반응은 주제에만 넣는다 | 흐름이 첫 싫어요를 받지 못해 같은 흐름을 한 번 더 보여야 거름이 걸린다 |
| 꼭대기가 있는 사건은 흐름에만 넣는다 | 주제가 반응의 3분의 2 를 잃어 순위 · 구독 제안 · 히스토리의 주제 관심이 거의 서지 않는다 |
| 앞선 날짜에 그 흐름이 한 번이라도 노출됐으면 반복이다(기간 없음) | 한 달 뒤 다시 이어진 흐름의 반응도 주제에서 빠진다 — 오래 떨어진 소식에 대한 반응은 주제 이유일 수 있다 |
| 스토리라인 구독을 흐름과 주제 몫 둘 다에 넣는다 | 같은 신호를 두 번 세고 흐름 신호가 주제로 새지 않는다는 원리와 어긋난다 |
