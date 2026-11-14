# ADR-0148: 스토리라인의 보도량은 닫은 사건 집합의 기간 안 기사 수로 센다

- 상태: 승인
- 날짜: 2026-11-14
- 근거: [이슈 #137](https://github.com/Theia0382/yozm-news/issues/137) — 기간 집계와 포함 관계의 조회를 조사해 리드와 정함
- 관련: [ADR-0117](0117-big-judgement-materials-are-tallied-by-event-per-group-of-events-and-storyline-passes-only-event-ids.md)(상위가 하위를 세는 법 · 보도량을 세는 법 — 보충), [ADR-0103](0103-big-judgement-on-storylines-and-the-storyline-milestone-right-after-event-clustering-takes-over-big-events.md)(보도량을 어떻게 세는지 — 보충), [ADR-0131](0131-storyline-relations-are-explicit-links-with-containment-as-a-single-parent-tree-gathered-at-query-time.md)(포함은 조회 때 하위를 따라 모은다), [ADR-0147](0147-storyline-big-judgement-compares-importance-weighted-coverage-per-source-and-day-with-one-criterion-at-query-time.md)(큰 판정의 식)

## 맥락

- 변화 리포트는 스토리라인의 보도량을 쓴다. 보도량을 어떻게 세는지는 큰 판정의 식과 함께 정하기로 했다([ADR-0103](0103-big-judgement-on-storylines-and-the-storyline-milestone-right-after-event-clustering-takes-over-big-events.md) · [ADR-0117](0117-big-judgement-materials-are-tallied-by-event-per-group-of-events-and-storyline-passes-only-event-ids.md)).
- 여러 스토리라인의 합계는 구성 사건 ID 의 합집합으로 센다([ADR-0117](0117-big-judgement-materials-are-tallied-by-event-per-group-of-events-and-storyline-passes-only-event-ids.md)). 포함의 상위는 조회 때 하위의 사건까지 닫은 사건 집합으로 센다([ADR-0131](0131-storyline-relations-are-explicit-links-with-containment-as-a-single-parent-tree-gathered-at-query-time.md)).
- 큰 판정의 식은 중요도로 무게를 준다([ADR-0147](0147-storyline-big-judgement-compares-importance-weighted-coverage-per-source-and-day-with-one-criterion-at-query-time.md)). 보도량은 얼마나 보도됐는지를 말하는 값이다.
- 기사에는 발행 시각과 처음 본 시각이 있다. 조회 기간은 사건 배정의 발행 시각으로 기사를 고른다.

## 결정

- **스토리라인의 보도량은 닫은 사건 집합의 조회 기간 안 기사 수다.** 중요도로 무게를 주지 않는다. 기간 집계의 기사 수가 그 값이다. 주제별 보도량과 같은 단위다.
- **포함의 상위는 하위의 사건을 닫은 사건 집합으로 센다.** 상위의 보도량은 하위의 것을 품는다.
- **여러 스토리라인의 합계는 닫은 사건 집합의 합집합으로 센다.** 상위와 그 하위를 함께 합쳐도 사건과 기사는 한 번이다.
- **기간 안 기사는 사건 배정의 발행 시각으로 고른다.** 큰 판정 · 목록과 같은 기간 규칙이다. 주제별 보도량은 기사의 첫 평가 시각으로 세므로 두 보도량의 기간 시각은 다르다.

## 결과

- 보도량과 큰 판정이 같은 기간 집계에서 나오고, 새 집계가 필요 없다.
- 상위와 하위의 보도량을 그대로 더하면 하위의 몫이 두 번 든다 — 합계는 합집합으로 낸다.
- 한 기간의 스토리라인 보도량과 주제별 보도량은 기간에 넣는 시각이 달라 기사 단위로 맞지 않는다.
- 합계를 내는 공개 조회는 그것을 쓰는 기능을 만들 때 둔다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 중요도 합을 보도량으로 한다 | 보도된 양과 중요도가 한 값에 섞여 "기사 몇 건"으로 읽을 수 없다. 중요도는 큰 판정이 쓴다 |
| 서로 다른 소스 수를 보도량으로 한다 | 켠 소스 수에서 금방 차 흐름 사이의 차이가 드러나지 않는다 |
| 상위는 직접 소속만 센다 | 큰 흐름이 갈래의 보도를 세지 못한다([ADR-0131](0131-storyline-relations-are-explicit-links-with-containment-as-a-single-parent-tree-gathered-at-query-time.md)) |
| 기간을 처음 본 시각으로 고른다 | 큰 판정 · 목록과 기간의 뜻이 달라지고, 사건 배정이 가진 시각이 아니다 |
