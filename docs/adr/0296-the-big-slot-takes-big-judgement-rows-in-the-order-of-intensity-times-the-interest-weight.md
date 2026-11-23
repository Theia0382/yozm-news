# ADR-0296: 큰 슬롯은 보도 강도에 관심 가중치를 곱한 순서로 큰 판정 행을 쓴다

- 상태: 승인
- 날짜: 2026-11-23
- 근거: [이슈 #190](https://github.com/Theia0382/yozm-news/issues/190) — 측정에서 큰 슬롯 항목의 싫어요가 구독 밖 주제의 큰 흐름에 거듭 몰렸다. 리드가 큰 슬롯에도 구독 후보와 같은 관심 가중치(주제 × 스토리라인)를 곱하고, 싫어하는 것은 따로 거르기로 했다(거르기는 [ADR-0297](0297-an-event-whose-mean-topic-interest-is-at-or-below-the-exclusion-threshold-does-not-enter-the-big-slot.md)). 구현 커밋 `cd044ca`
- 관련: [ADR-0279](0279-storyline-interest-multiplies-the-candidate-rank-and-flows-at-or-below-the-exclusion-threshold-leave-subscribed-candidates-and-the-big-slot.md)(부분 대체 — "행의 순서는 큰 판정 순서 그대로다"), [ADR-0260](0260-digest-candidates-are-events-and-only-assigned-events-with-an-article-first-evaluated-in-the-period-enter.md)(부분 대체 — 큰 판정 행을 쓰는 순서), [ADR-0103](0103-big-judgement-on-storylines-and-the-storyline-milestone-right-after-event-clustering-takes-over-big-events.md)(보충 — 큰 판정 행을 사용자마다 관심으로 놓는다), [ADR-0235](0235-the-big-list-has-one-row-per-containment-tree-carrying-the-big-storylines-under-it.md)(큰 판정 목록의 순서는 그대로), [ADR-0209](0209-maximum-candidates-and-big-slots-are-user-settings-and-the-big-slot-takes-at-most-half.md)(대안 표의 되먹임 우려)

## 맥락

- 큰 슬롯은 큰 판정 목록(보도 강도 순)의 행을 앞에서부터 사용자의 큰 슬롯 수만큼 쓰고, 거른 흐름의 행은 건너뛴다(ADR-0103 · ADR-0279). 행의 순서는 사용자와 무관하다.
- 구독 후보의 순위는 사건 중요도에 관심 가중치(주제 가중치 × 스토리라인 가중치)를 곱한다(ADR-0028 · ADR-0279).
- ADR-0279 는 "남은 큰 판정 행을 보도 강도 × 스토리라인 가중치 순으로 다시 놓는다"를 기각했다 — 보도 강도의 폭이 커서 곱의 효과가 작고, 큰 판정 순서와 다른 순서를 하나 더 둔다는 이유였다.
- 큰 판정 행이 큰 슬롯 수보다 많은 기간이 있다. 그런 기간에는 어느 행이 앞에 오는지가 사용자가 받는 큰 슬롯을 정한다.

## 결정

- **큰 판정 행을 행 점수 내림차순으로 놓고 그 순서로 큰 슬롯을 쓴다.** 행 점수는 보도 강도 × 그 행 후보 사건들의 관심 가중치의 산술 평균이다. 관심 가중치는 구독 후보 순위와 같은 사건 가중치(주제 × 스토리라인)다. 같으면 큰 판정 순서다.
- 후보가 없는 사건은 평균에 넣지 않는다. 후보가 하나도 없는 행은 가중치 1 이다 — 어차피 건너뛴다.
- 거른 흐름의 행 건너뛰기 · 행마다 사건 고르기 · 상한과 행마다 고르게 줄이기는 그대로다. 행 위치와 큰 판정 행의 꼭대기 목록은 정렬한 순번이고, 줄이기 · 예산 자르기의 "앞 행"도 그 순서다.
- 큰 판정 목록의 순서(`storyline` 의 보도 강도 순)는 바꾸지 않는다. 이 순서는 `digest` 가 사용자마다 그 위에 곱한다.

## 결과

- 신호가 없는 사용자는 지금과 같다(가중치 모두 1).
- ADR-0279 의 기각 이유에 답한다. 곱은 크기가 비슷한 이웃 행의 순서를 바꾸고, 큰 판정 행이 큰 슬롯 수보다 많은 기간에는 실제로 고르는 행이 바뀐다. 큰 판정 순서(사회적 크기)는 `storyline` 에 그대로 있고, 사용자마다의 순서는 고를 때만 쓴다.
- 행이 슬롯보다 많은 날에는 관심이 낮은 행이 슬롯 밖으로 밀린다 — 관찰된 관심(좋아요 · 노출)이 고르는 행을 바꾸는 되먹임이다(ADR-0209 가 막으려던 것). 가중치 곱의 폭(주제 · 흐름 각 0.5 ~ 1.5)이 보도 강도의 폭보다 좁아 크기 차이가 큰 행은 뒤집히지 않고, 큰 슬롯 수는 사용자 설정 그대로다.
- 반복되는 큰 흐름은 반응이 없으면 그대로 든다 — 반복은 큰 슬롯만이 아니라 다이제스트 구성 전체의 문제로 따로 다룬다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 순서는 큰 판정 그대로 두고 거르기만 한다 | 행이 남는 기간에 관심을 반영하지 못한다 |
| 행 점수에 머리 꼭대기의 흐름 가중치만 쓴다 | 구독 후보 순위와 다른 가중치 꼴이 된다. 주제 관심이 순서에 들지 않는다 |
| 순서를 고르기(`BigSlotPicker`) 안에서 정한다 | 고르기 · 줄이기 · 순서가 한 함수에 몰리고, 행 위치가 입력 순번이라는 계약을 바꿔야 한다 |
