# ADR-0299: 큰 판정 기준의 처음 값은 네 단계 중요도 척도에서 같은 엄격함을 지키도록 14.00 으로 둔다

- 상태: 승인
- 날짜: 2026-11-24
- 근거: [이슈 #236](https://github.com/Theia0382/yozm-news/issues/236) — 운영 인스턴스에 네 단계 중요도 체계를 들이고 7일치를 재평가한 뒤, 같은 기사의 옛 · 새 중요도와 날마다의 큰 판정 미리보기를 견줌
- 관련: [ADR-0174](0174-big-judgement-criterion-first-value-kept-by-operator-daily-preview-review-where-the-evaluation-set-cannot-separate-neighbours.md)(부분 대체 — 결정 첫째 항의 값), [ADR-0285](0285-importance-asks-what-the-matter-now-changes-and-levels-run-to-society-wide-in-four-steps-without-field-words.md)(중요도를 네 단계로 바꿔 보도 강도가 낮아졌다), [ADR-0147](0147-storyline-big-judgement-compares-importance-weighted-coverage-per-source-and-day-with-one-criterion-at-query-time.md)(보도 강도는 기간 안 기사 중요도 합을 소스 수와 일수로 나눈 값)

## 맥락

- 큰 판정 기준의 처음 값 15.00 은 다섯 단계 중요도 척도 위에서 평가 세트와 운영자의 날마다 미리보기 검수로 정했다(ADR-0174).
- 중요도가 네 단계가 되며(ADR-0285) 중요도가 높은 기사일수록 값이 더 내려갔다. 운영 인스턴스에서 같은 기사를 두 척도로 견주면, 옛 척도에서 중요도가 높던 기사의 중요도 합이 새 척도에서 한 할 안팎 줄었다. 보도 강도는 중요도 합에 비례하므로 큰 스토리라인 후보의 보도 강도도 그만큼 내려간다.
- 날마다 미리보기로 보면, 옛 척도에서 처음 값을 넘던 흐름의 일부가 새 척도에서 14 ~ 15 사이로 내려와 큰 판정에서 빠졌다. 주말에는 큰 스토리라인이 거의 없었다.

## 결정

- **큰 판정 기준의 처음 값은 14.00 이다.** 옛 척도의 15.00 과 같은 엄격함을 새 척도에서 지키는 값이다. 값의 정본은 `BigStorylineCriteria.DEFAULT` 와 [storyline Spec "큰 판정"](../specs/storyline.md#큰-판정)이다.
- 값을 정하는 방법(평가 세트와 운영자의 날마다 미리보기 검수 — ADR-0174)과 알릴 만함의 기준은 그대로다. 이번 값은 척도가 바뀐 만큼 옮긴 것이다.
- 근거 수치는 공개 문서에 적지 않는다([ADR-0096](0096-instance-bound-evaluation-data-kept-out-of-the-public-repository-and-defaults-cite-only-the-evaluation-set.md)).

## 결과

- 처음 값은 새 인스턴스에만 들어간다. 돌고 있는 인스턴스는 지금 값을 그대로 쓰므로, 중요도 체계를 바꾼 운영자가 미리보기로 다시 맞춘다([태그 체계 바꾸기](../operations/tag-sets.md#기본-체계-파일의-개정-들이기)).
- 이번 값은 척도를 옮긴 것이고 새 척도에서 날마다 알릴 만함을 다시 검수한 것은 아니다. 다이제스트의 큰 슬롯과 함께 값을 조정하는 일은 다이제스트 측정의 몫이다(ADR-0174 결과).

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 15.00 그대로 | 새 척도에서 옛 기준보다 엄격해져, 옛 척도에서 들던 흐름이 빠지고 주말의 큰 스토리라인이 거의 없어진다 |
| 새 척도에서 날마다 검수를 다시 해 값을 고름 | 방법은 같고 비용이 크다. 척도만 바뀐 경우는 옮긴 값으로 두고 운영하며 본다 |
