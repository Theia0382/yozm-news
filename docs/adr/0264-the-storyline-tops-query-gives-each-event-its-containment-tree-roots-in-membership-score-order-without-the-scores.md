# ADR-0264: 스토리라인 꼭대기 조회는 사건마다 포함 나무 뿌리를 소속 점수 순으로 내고 점수는 싣지 않는다

- 상태: 승인
- 날짜: 2026-11-19
- 근거: [이슈 #225](https://github.com/Theia0382/yozm-news/issues/225) — 항목 묶기의 꼭대기 조회를 `storyline` 의 공개 조회로 두는 것은 수용 기준이고, 여러 나무에 걸친 사건의 셋째 열쇠(소속 점수)를 꼭대기의 순서로만 내는 쪽을 골랐다. 구현 커밋 `7f401c6`
- 관련: [ADR-0131](0131-storyline-relations-are-explicit-links-with-containment-as-a-single-parent-tree-gathered-at-query-time.md)(보충 — 상위 하나인 포함 나무의 뿌리를 사건마다 공개 조회로 낸다), [ADR-0235](0235-the-big-list-has-one-row-per-containment-tree-carrying-the-big-storylines-under-it.md)(큰 판정 목록은 포함 나무마다 한 행), [ADR-0262](0262-chosen-events-are-grouped-into-items-by-containment-tree-top-code-splits-top-stories-and-headlines-the-llm-writes-per-item-and-an-item-has-one-badge.md)(항목 묶기)

## 맥락

- 다이제스트는 고른 사건을 포함 나무의 꼭대기로 묶고, 사건이 나무 여럿에 걸치면 마지막 열쇠로 소속 점수를 쓴다([ADR-0262](0262-chosen-events-are-grouped-into-items-by-containment-tree-top-code-splits-top-stories-and-headlines-the-llm-writes-per-item-and-an-item-has-one-badge.md)).
- 사건의 스토리라인 조회(`storylinesOf`)는 사건 하나씩 직접 든 스토리라인과 그 상위 전부를 낸다. 소속 점수는 판정 근거라 사용자 쪽 공개 조회에 싣지 않고 운영자 조회만 낸다. 합치기로 옮긴 소속은 점수가 없다.
- 포함의 상위는 하나라 스토리라인마다 뿌리가 하나다. 큰 판정 목록의 행 머리도 그 나무의 뿌리다 — 하위가 크면 상위도 크다.

## 결정

- **`storyline` 이 사건 여럿의 꼭대기를 한 번에 내는 공개 조회를 둔다.** 꼭대기는 사건이 직접 든 합쳐지지 않은 스토리라인에서 포함의 상위를 끝까지 따른 뿌리다(상위가 없으면 자기). 이어짐은 따르지 않는다.
- **사건마다 꼭대기를 그 꼭대기 아래 직접 소속의 가장 높은 소속 점수 내림차순으로 낸다** — 점수 없는 소속만 있는 꼭대기는 뒤, 같으면 꼭대기 ID 순. **점수는 싣지 않는다.**
- 합쳐졌거나 없는 사건은 남는 사건으로 따르지 않고 결과에서 뺀다. 한 번에 묻는 서로 다른 사건 수에 상한(1,000 — 측정 전 목표)을 둔다. 트랜잭션을 열지 않는다 — 사건 · 소속 · 관계를 따로 읽는다.

## 결과

- 꼭대기의 정본은 `storyline` 이다 — 다이제스트는 상위를 따라가지 않고 받은 순서를 마지막 열쇠로 쓴다. 그 순서가 무엇으로 정해졌는지는 문서로만 안다.
- 꼭대기와 큰 판정 행의 머리가 같은 단위라 큰 판정 나무에 든 구독 사건이 큰 슬롯 항목에 함께 묶인다.
- 선별과 꼭대기 조회 사이에 합쳐진 사건은 결과에 없어 부르는 쪽이 꼭대기 없는 사건으로 다룬다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 꼭대기 조회에 소속 점수를 싣는다 | 판정 근거를 사용자 쪽 공개 조회에 싣는 예외가 되고, 합치기로 옮긴 소속의 빈 점수 갈래가 생긴다 |
| 셋째 열쇠를 꼭대기 ID 순 같은 공개 값으로 바꾼다 | 이미 정한 열쇠(소속 점수)를 바꾼다 |
| 다이제스트가 사건마다 `storylinesOf` 를 불러 상위가 없는 것을 뿌리로 고른다 | 사건마다 조회가 하나씩 들고, 소속 점수가 없어 셋째 열쇠를 정하지 못한다 |
