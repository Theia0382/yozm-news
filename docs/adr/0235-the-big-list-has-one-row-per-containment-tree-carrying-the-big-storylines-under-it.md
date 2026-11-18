# ADR-0235: 큰 판정 목록은 포함 나무마다 한 행으로 내고 그 아래의 큰 스토리라인을 행에 싣는다

- 상태: 승인
- 날짜: 2026-11-18
- 근거: [이슈 #170](https://github.com/Theia0382/yozm-news/issues/170) — 수용 기준 "큰 판정 목록에 한 포함 나무가 한 번만 나온다". 구현 커밋 `e3b27b4`
- 관련: [ADR-0131](0131-storyline-relations-are-explicit-links-with-containment-as-a-single-parent-tree-gathered-at-query-time.md)(부분 대체 — 상위와 하위를 가리지 않고 낸다는 결정, 큰 판정 목록에 한해), [ADR-0147](0147-storyline-big-judgement-compares-importance-weighted-coverage-per-source-and-day-with-one-criterion-at-query-time.md)(부분 대체 — 상위와 하위는 둘 다 낸다는 결정), [ADR-0174](0174-big-judgement-criterion-first-value-kept-by-operator-daily-preview-review-where-the-evaluation-set-cannot-separate-neighbours.md) · [ADR-0208](0208-the-big-slot-takes-the-representatives-of-the-heaviest-events-of-each-big-storyline-row.md) · [ADR-0220](0220-big-slot-candidates-are-grouped-by-big-storyline-row-in-the-prompt-and-the-digest-llm-neither-makes-nor-links-groups.md)(보충)

## 맥락

- 포함의 상위는 하위의 사건까지 세므로 하위가 크면 상위도 언제나 크다. 큰 판정 목록이 둘을 각각 행으로 내어 같은 사안이 두 번 나왔다.
- 다이제스트의 큰 슬롯은 큰 판정 목록의 행을 그대로 쓴다 — 한 포함 나무가 슬롯 둘을 쓸 수 있었다.
- 무엇이 큰가(보도 강도 · 기준)는 이 문제와 따로다.

## 결정

- **큰 판정 목록은 포함 나무마다 한 행이다** — 큰 스토리라인마다 조상 가운데 큰 것의 가장 위를 머리로 삼고(없으면 자기 자신), 머리마다 한 행을 낸다. 그 아래의 큰 스토리라인은 행의 아래 행으로 싣는다 — 아래 행은 다시 아래 행을 갖지 않는다.
- 이어짐은 나무가 아니다 — 이어진 두 스토리라인은 각각 행이다.
- 무엇이 큰가는 바꾸지 않는다 — 행과 아래 행 모두 지금 기준의 큰 스토리라인이고, 순서는 둘 다 보도 강도 순이다.
- 기간의 스토리라인 목록은 그대로 상위와 하위를 모두 낸다.

## 결과

- 다이제스트의 큰 슬롯은 한 포함 나무에 하나다 — 행의 닫은 사건 집합에 하위의 사건이 함께 들어 하위의 사건도 그 슬롯의 후보가 된다.
- 운영자 화면은 아래 행을 행 밑에 들여 보이고, 큰 판정 기준 미리보기는 행과 아래 행을 함께 세어 행이 아래 행이 되는 것을 "새로 듦" · "빠짐"으로 세지 않는다.
- 평가 도구의 큰 표시는 행만 셀지 아래 행까지 셀지를 고른다.
- 같은 사안이 포함으로 이어지지 않고 두 스토리라인으로 남으면 여전히 두 행이다 — 같은 사안의 조각은 합치기가 맡는다([ADR-0234](0234-fragments-of-one-matter-are-recognised-by-the-relation-judgement-and-merged-above-a-merge-probability-without-a-way-back.md)).

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 큰 판정에서 하위를 빼고 최상위만 낸다 | 큰 갈래가 보이지 않는다. 운영자와 평가 도구가 어느 하위가 컸는지 알 수 없다 |
| 지금처럼 모두 내고 다이제스트가 고른다 | 같은 규칙을 쓰는 쪽마다 따로 두게 된다. 큰 판정 목록의 뜻(알릴 만한 사안)과 어긋난다 |
| 이어짐도 나무로 묶는다 | 이어짐은 다른 사안이다 — 한 행으로 묶으면 다른 사안이 가려진다 |
