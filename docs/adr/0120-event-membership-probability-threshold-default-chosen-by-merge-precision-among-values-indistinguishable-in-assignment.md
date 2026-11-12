# ADR-0120: 사건 소속 판정의 확률 기준 기본값은 배정에서 구별되지 않는 값 가운데 병합 판정의 정밀이 높은 값으로 정한다

- 상태: 승인
- 날짜: 2026-11-12
- 근거: [이슈 #102](https://github.com/Theia0382/yozm-news/issues/102) — 평가 세트에서 확률 기준에 따른 병합 판정의 정밀을 견주고, 고른 값으로 처음부터 다시 묶어 확인하고, 리드가 그 값의 사건 표본을 검수함(`7be22cb`)
- 관련: [ADR-0109](0109-event-clustering-defaults-chosen-on-the-evaluation-set-with-operator-review-and-closing-from-operating-records.md)(결정 절의 "같은 품질의 후보 가운데서는 … 고른다"에 병합 판정의 정밀을 더한다 — 보충. 견주는 방법과 표본 검수는 그대로), [ADR-0084](0084-event-merge-applies-assignment-scoring-between-events-and-keeps-merged-rows-pointing-to-survivors.md)(병합도 같은 확률 기준을 쓴다), [ADR-0119](0119-event-merge-does-not-ask-band-pairs-whose-prior-assignment-probability-is-below-the-probability-threshold.md)(병합이 묻는 쌍 — 그 위에서 확인했다), [ADR-0096](0096-instance-bound-evaluation-data-kept-out-of-the-public-repository-and-defaults-cite-only-the-evaluation-set.md)(근거 수치를 공개 문서에 적지 않는다)

## 맥락

- 확률 기준은 사건 소속 판정이 답한 확률이 이 이상이어야 붙이는 값이고, 배정 · 재판정 · 병합이 같은 값을 쓴다([ADR-0081](0081-event-assignment-criteria-in-settings-and-rejudgement-within-stored-scores.md) · [ADR-0084](0084-event-merge-applies-assignment-scoring-between-events-and-keeps-merged-rows-pointing-to-survivors.md)).
- 기본값은 평가 세트의 묶음 정확도로 견줘 정했다. 확률 기준의 후보 값들은 묶음 정확도의 구간 안에서 구별되지 않았고, 그 가운데 처음부터 다시 묶어 확인한 값을 골랐다([ADR-0109](0109-event-clustering-defaults-chosen-on-the-evaluation-set-with-operator-review-and-closing-from-operating-records.md)).
- 묶음 정확도는 기사 단위의 값이라 병합 하나가 맞았는지는 드러내지 않는다. 틀린 병합은 사건 둘을 통째로 섞고, 합치기는 되돌리지 않는다([ADR-0087](0087-closed-event-assignments-are-not-rejudged-and-closing-and-merging-are-not-undone.md)).
- 평가 세트에서 병합을 따로 보니, 묶음 정확도에서 구별되지 않던 값들 사이에 병합의 정밀(합친 사건 쌍이 정답에서 같은 사건인 비율)은 차이가 있었다.

## 결정

- **확률 기준의 기본값은 묶음 정확도에서 구별되지 않는 값 가운데 병합 판정의 정밀이 높은 값으로 정한다.** 병합의 정밀은 합쳐진 사건마다 합칠 때의 두 사건 구성을 정답 사건과 견줘 본다.
- **고른 값은 처음부터 다시 묶어 확인하고 운영자 표본 검수를 거친다.** 방법은 다른 기본값과 같다([ADR-0109](0109-event-clustering-defaults-chosen-on-the-evaluation-set-with-operator-review-and-closing-from-operating-records.md)).
- 값의 정본은 설정 클래스와 [event Spec §7](../specs/event.md#7-설정)이고, 근거 수치는 공개 문서에 적지 않는다([ADR-0096](0096-instance-bound-evaluation-data-kept-out-of-the-public-repository-and-defaults-cite-only-the-evaluation-set.md)).

## 결과

- 기본값이 올라갔다. 평가 세트에서 묶음의 정밀은 오르고 재현은 내려갔으며, 둘을 합친 정확도는 앞선 기본값과 구별되지 않았다. 다른 일이 한 사건에 섞이는 것을 줄이고, 같은 일이 여러 사건으로 갈리는 것을 그만큼 받아들인다.
- 병합의 정밀은 판단할 수 있는 병합의 수가 적어 값의 방향으로만 읽었다.
- 측정한 외부 임베딩 모델의 권장 확률 기준(운영 안내)은 그대로다.
- 기본값을 쓰던 인스턴스는 이 판으로 올리면 기준이 바뀐 것이라, 재판정 회차가 지난 배정을 저장된 확률로 다시 판정하고 병합 회차가 열린 사건을 한 번씩 다시 본다. 확률 기준을 환경 변수로 정해 둔 인스턴스는 그 값을 그대로 쓴다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 앞선 기본값을 그대로 둔다 | 묶음 정확도는 같은데 병합의 정밀이 낮았다 |
| 병합에만 쓰는 확률 기준을 따로 둔다 | 설정 · 기준 이력 · 운영자 화면이 늘어나는데, 배정에서는 두 값이 구별되지 않아 배정 쪽 값을 따로 둘 근거가 없다 |
