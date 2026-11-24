# ADR-0260: 다이제스트 후보는 사건이고, 기간에 첫 평가된 기사가 있는 배정된 사건만 든다

- 상태: 승인. 부분 대체 — 기사 제외 가운데 싫어요([ADR-0265](0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md)) · 순위의 관심도 가중치 · 큰 판정 행을 쓰는 순서와 사건이 드는 조건([ADR-0279](0279-storyline-interest-multiplies-the-candidate-rank-and-flows-at-or-below-the-exclusion-threshold-leave-subscribed-candidates-and-the-big-slot.md)) · 큰 판정 행의 순서([ADR-0296](0296-the-big-slot-takes-big-judgement-rows-in-the-order-of-intensity-times-the-interest-weight.md)) · 큰 슬롯 사건의 주제 거름([ADR-0297](0297-an-event-whose-mean-topic-interest-is-at-or-below-the-exclusion-threshold-does-not-enter-the-big-slot.md)) · `INTEREST` 를 구독 사건으로 한정한 것([ADR-0280](0280-events-of-followed-flows-outside-the-subscribed-topics-enter-one-per-flow-within-a-share-of-the-subscribed-seats-as-interest.md))
- 날짜: 2026-11-19
- 근거: [이슈 #225](https://github.com/Theia0382/yozm-news/issues/225) — 단위를 사건으로 정하고(주요 뉴스 = 사건 묶음, 큰 스토리라인은 그 행의 사건을 묶음 그대로, 최대 후보 수 = 사건 수), 배정 전 기사의 처분(사건이 있는 기사만)을 골랐다. 측정 인스턴스에서 평가와 사건 배정이 따로 돌아 첫 평가 뒤에 배정된 기사가 흔했으나, 재판정이 배정 시각을 다시 써 배정이 늦은 기사의 크기는 재지 못했다. 구현 커밋 `4c4a33b`(후보 사건 · 순위 · 큰 슬롯 · 몫 · 선정 이유 · 후보 수)
- 관련: [ADR-0208](0208-the-big-slot-takes-the-representatives-of-the-heaviest-events-of-each-big-storyline-row.md)(전체 대체 — 큰 슬롯이 넣는 것이 사건의 대표 기사에서 사건으로), [ADR-0028](0028-explicit-signals-and-interest-weighted-candidates.md)(부분 대체 — 결정 "후보 선별 순서"의 사건 단위 중복 제거와 기사마다 선정 이유), [ADR-0209](0209-maximum-candidates-and-big-slots-are-user-settings-and-the-big-slot-takes-at-most-half.md)(부분 대체 — 큰 슬롯 상한의 단위), [ADR-0216](0216-the-interest-selection-reason-marks-subscribed-candidates-that-enter-only-by-interest-with-a-weight-above-neutral.md)(부분 대체 — `INTEREST` 를 가르는 단계와 단위), [ADR-0207](0207-digest-candidates-enter-a-period-by-first-evaluation-time-with-a-published-time-floor.md)(부분 대체 — 결과 "정착 시간 동안 수집된 기사가 다음 다이제스트에 든다"), [ADR-0102](0102-an-event-is-the-reports-of-one-happening-narrowed-by-the-event-membership-question-wording.md)(사건의 범위), [ADR-0261](0261-event-topics-and-regions-are-keys-on-at-least-half-of-evaluated-members-importance-is-the-maximum-and-digest-counts-them-at-query-time.md)(사건의 값)

## 맥락

- 사건은 한 번 일어난 일의 기사 묶음이고 같은 사안의 다음 단계는 다른 사건이다([ADR-0102](0102-an-event-is-the-reports-of-one-happening-narrowed-by-the-event-membership-question-wording.md)). 대부분 하루 안에 끝나 일일 다이제스트의 단위로 맞고, 며칠에 걸친 흐름은 스토리라인이 잇는다.
- 후보가 기사이면 같은 사건의 구독 후보를 대표 기사 하나로 줄이고, 큰 슬롯은 사건마다 대표 기사를 넣으며, 어느 기사들을 주요 뉴스 하나로 묶을지는 LLM 이 정한다. 주요 뉴스 수와 큰 슬롯 기사가 구독 기사와 섞이는 정도가 사용자 · 날마다 달랐다.
- 사건 배정과 태그 평가는 서로 다른 대기열이다. 선별 때 평가는 끝났지만 아직 사건이 없는 기사가 있을 수 있다.

## 결정

> 부분 대체(둘째 문장의 기사 제외 가운데 싫어요) — [ADR-0265](0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md). 지금 상태: [digest Spec "후보 선별"](../specs/digest.md#후보-선별) digest-R17
- **후보는 사건이다.** 구독 후보 사건은 읽는 기간에 첫 평가된 기사 가운데 발행 시각 하한과 사용자의 기사 제외(형식 · 소스 · 싫어요)를 지난 기사가 하나 이상 있는, 배정된 사건이다. 그 기사들이 그 사용자에게 그 기간에 든 사건의 기사다. **선별 때 사건에 배정되지 않은 기사는 그 기간의 후보가 아니다.**
- 지역 제외와 구독 대조는 사건의 값([ADR-0261](0261-event-topics-and-regions-are-keys-on-at-least-half-of-evaluated-members-importance-is-the-maximum-and-digest-counts-them-at-query-time.md))으로 본다 — 사건의 지역이 제외 지역과 겹치면 빼고, 사건의 주제가 구독 행과 겹쳐야 구독 후보다.
> 부분 대체(아래 두 항목의 관심도 가중치 · 큰 판정 행을 쓰는 순서와 사건이 드는 조건) — [ADR-0279](0279-storyline-interest-multiplies-the-candidate-rank-and-flows-at-or-below-the-exclusion-threshold-leave-subscribed-candidates-and-the-big-slot.md) · [ADR-0296](0296-the-big-slot-takes-big-judgement-rows-in-the-order-of-intensity-times-the-interest-weight.md) · [ADR-0297](0297-an-event-whose-mean-topic-interest-is-at-or-below-the-exclusion-threshold-does-not-enter-the-big-slot.md). 지금 상태: [digest Spec "후보 선별"](../specs/digest.md#후보-선별) digest-R17 ~ R19
- **순위는 사건 점수다** — 사건 중요도 × (`low_confidence` 면 0.7) × 관심도 가중치(사건 주제 중분류 가중치의 평균). 같으면 그 사건의 기간에 든 기사 가운데 가장 늦은 발행 시각, 그다음 사건 ID 순이다.
- **큰 슬롯은 큰 판정 행의 사건을 넣는다.** 큰 판정 목록의 행을 순서대로 사용자의 큰 슬롯 수만큼 쓰고, 행마다 기간 안(사건 배정의 발행 시각) 기사가 있는 사건을 기간 안 중요도 합 내림차순(같으면 사건 ID 순)으로 10 개까지 넣는다. 사건은 그 기간 안 기사 가운데 평가가 있고 기사 제외를 지난 기사가 있고 사건의 지역이 제외와 겹치지 않을 때 든다. 사건을 하나도 내지 못한 행은 세지 않고, 앞 행이 쓴 사건은 다시 들지 않으며, 큰 슬롯 사건은 구독 후보에서 뺀다. 10 은 측정 전 목표다.
- **최대 후보 수 · 큰 슬롯 상한(최대 후보 수의 절반, 내림) · 다이제스트 행의 후보 수는 사건 수다.**
> 부분 대체(`INTEREST` 를 구독 사건에만 주는 것) — [ADR-0280](0280-events-of-followed-flows-outside-the-subscribed-topics-enter-one-per-flow-within-a-share-of-the-subscribed-seats-as-interest.md). 지금 상태: [digest Spec "후보 선별"](../specs/digest.md#후보-선별) digest-R22 · R52
- **선정 이유는 사건마다다** — 큰 슬롯 사건은 `MAJOR_STORYLINE`, 구독 사건은 같은 재료를 중립 순위로 같은 단계(다양성 몫 · 최대 후보 수 · 입력 토큰 예산)에 넣은 결과에 없고 관심도 가중치가 1 보다 크면 `INTEREST`, 아니면 `SUBSCRIBED` 다. 사건의 값과 표본 기사의 내용은 한 번 읽어 두 결과가 함께 쓴다.

## 결과

- 같은 사건의 기사가 후보로 두 번 들지 않고, 중복 제거 단계가 없다.
- 선별 때까지 배정되지 않은 기사만으로 된 사건은 그 기간에 오르지 않는다. 다음 기간에는 그 기사가 그 기간에 첫 평가된 기사가 아니라 다시 들지 않는다 — 그 사건은 새로 첫 평가된 기사가 붙어야 오른다. 그런 사건의 크기는 재지 않았다.
- 최대 후보 수가 같아도 사건마다 기사 여럿을 넘겨 입력이 기사 단위보다 길 수 있다 — 입력 토큰 예산이 먼저 자를 수 있다([ADR-0263](0263-articles-sent-per-event-are-sampled-up-to-a-cap-with-a-fixed-seed-from-those-in-the-period-and-the-sample-is-the-citations-and-sources.md)).
- 제외 지역 기사가 절반 미만인 사건은 지역 제외에 걸리지 않아 그 기사도 사건의 기사로 남는다.
- 화면의 "후보 n건"이 사건 수다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 기사 단위 후보에 사건 단위 중복 제거를 둔다 | 큰 사건도 기사 하나로만 들고, 어느 기사들이 한 흐름인지를 LLM 이 정해 주요 뉴스가 사용자 · 날마다 다르게 묶인다 |
| 기간에 드는 시각을 첫 평가 시각과 배정 시각 가운데 늦은 것으로 | 재판정이 배정 시각을 다시 써 재판정된 기사의 사건이 다시 후보가 된다. `event` 의 공개 조회가 바뀐다 |
| 배정 전 기사를 혼자 한 사건처럼 다룬다 | 저장 · 입력에 사건이 없는 항목 갈래가 생기고, 그 기사와 그 기사가 곧 들 사건이 같은 다이제스트에 따로 오를 수 있다 |

## 후속 기록

- 2026-11-20: 결정 "순위는 사건 점수다"의 관심도 가중치(사건 주제 중분류 가중치의 평균)는 [ADR-0222](0222-interest-per-subtopic-is-smoothed-reactions-over-exposures-with-a-half-life-and-exposures-are-all-ready-citations.md) 결정 "여러 주제에 걸친 기사의 가중치는 그 기사 주제 중분류 가중치의 평균이다"의 단위를 사건으로 바꾼 것이다 — 0222 에 부분 대체 표시, 지금 상태는 [digest Spec "관심도"](../specs/digest.md#관심도) digest-R23
