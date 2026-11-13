# ADR-0147: 큰 판정은 기간에 기사가 있는 소스 수와 일수로 나눈 중요도 가중 보도량이 기준 이상인지를 조회할 때 판정한다

- 상태: 승인
- 날짜: 2026-11-14
- 근거: [이슈 #137](https://github.com/Theia0382/yozm-news/issues/137) — 고정 덤프의 스토리라인 평가 세트에서 식의 후보들을 스토리라인 × 날짜의 큰 표시와 견주어 리드와 정함
- 관련: [ADR-0116](0116-storyline-big-judgement-starts-with-a-provisional-rule-of-distinct-sources-and-mean-importance-judged-at-query-time.md)(큰 판정의 식과 순서 — 부분 대체), [ADR-0118](0118-big-judgement-criteria-are-storyline-operator-settings-and-do-not-inherit-event-criteria-values-or-history.md)(기준의 범위 — 부분 대체), [ADR-0117](0117-big-judgement-materials-are-tallied-by-event-per-group-of-events-and-storyline-passes-only-event-ids.md)(기간 집계의 재료 — 보충), [ADR-0103](0103-big-judgement-on-storylines-and-the-storyline-milestone-right-after-event-clustering-takes-over-big-events.md)(중요도 세분화 — 보충), [ADR-0131](0131-storyline-relations-are-explicit-links-with-containment-as-a-single-parent-tree-gathered-at-query-time.md)(상위와 하위를 함께 낸다 — 보충), [ADR-0110](0110-storyline-evaluation-labels-system-events-of-a-fixed-dump-and-measures-overlapping-clusters-from-random-seed-events.md)(큰 판정을 스토리라인 × 날짜로 잰다), [ADR-0148](0148-storyline-coverage-is-the-article-count-in-the-period-over-the-closed-event-set.md)(보도량)

## 맥락

- 큰 판정은 임시 산식으로 시작했다 — 조회 기간 안 기사의 서로 다른 소스 수와 중요도 평균이 둘 다 기준 이상인가([ADR-0116](0116-storyline-big-judgement-starts-with-a-provisional-rule-of-distinct-sources-and-mean-importance-judged-at-query-time.md)). 식은 스토리라인 사이 관계와 잇는 방법이 정해진 뒤 평가 세트로 정하기로 했고, 관계(포함 · 이어짐)와 잇기가 정해졌다([ADR-0131](0131-storyline-relations-are-explicit-links-with-containment-as-a-single-parent-tree-gathered-at-query-time.md)).
- 포함의 상위는 하위의 사건까지 닫은 사건 집합으로 센다. 상위와 하위는 가리지 않고 함께 낸다([ADR-0131](0131-storyline-relations-are-explicit-links-with-containment-as-a-single-parent-tree-gathered-at-query-time.md)).
- 임시 산식은 평가 세트에서 큰 판정 정밀도가 낮았다. 소스 수는 운영자가 켠 소스 수에 매인 값이라 켠 소스가 적으면 거의 거르지 못하고, 중요도 평균은 기사 수와 무관하다.
- 평가 세트의 큰 표시는 원래 라벨과 보강한 라벨 둘이고 대응 하한도 둘로 쟀다. 식의 후보마다 조건에 따라 순위가 흔들렸다.
- 켠 소스 수와 조회 기간의 길이는 인스턴스와 조회마다 다르다. 기준값 하나가 그 차이를 견디려면 식이 둘을 고르게 해야 한다.
- 기간 집계는 `event` 가 사건 묶음 단위로 세고 `storyline` 은 사건 ID 만 넘긴다([ADR-0117](0117-big-judgement-materials-are-tallied-by-event-per-group-of-events-and-storyline-passes-only-event-ids.md)).

## 결정

- **큰 스토리라인 = 보도 강도 ≥ 기준(경계 포함).** 평가 세트의 네 조건(라벨 둘 × 대응 하한 둘)에서 평균이 가장 높았던 중요도 합을, 켠 소스 수와 기간 길이에 매이지 않게 나눈 식이다. 보도 강도는 스토리라인의 닫은 사건 집합의 조회 기간 안 기사 중요도 합을, 그 기간에 기사가 있는 서로 다른 소스 수와 기간의 길이(일)로 나눈 값이다. 중요도가 없는 기사는 합에 들지 않는다.
- **분모의 소스 수는 조회 기간 안 기사가 있는 모든 사건의 기간 전체 집계에서 얻고, `event` 가 센다.** 스토리라인에 들지 않은 사건의 기사도 소스 수에 든다. 소스 수가 0 이면 아무것도 크지 않다.
- **기간의 길이는 길이 ÷ 24시간으로 센다.** 하루가 23 · 25시간인 날과 임의 시각 범위에서도 같은 식이다.
- **기준은 값 하나다.** 0 보다 크고 소수 둘째 자리까지인 운영자 설정이다. 처음 값은 평가 세트로 정한 측정 전 목표다.
- **판정은 나누지 않고 곱으로 견주고, 보이는 값은 소수 둘째 자리로 반올림한다.** 순서는 보이는 보도 강도 내림차순, 스토리라인 ID 순이다.
- **조회할 때 지금 기준으로 판정하고 결과를 저장하지 않는다.** [ADR-0116](0116-storyline-big-judgement-starts-with-a-provisional-rule-of-distinct-sources-and-mean-importance-judged-at-query-time.md)의 결정 그대로다.
- **상위와 하위는 둘 다 낸다.** [ADR-0131](0131-storyline-relations-are-explicit-links-with-containment-as-a-single-parent-tree-gathered-at-query-time.md)의 결정 그대로다. 상위의 닫은 사건 집합이 하위의 것을 품으므로 하위가 크면 상위도 언제나 크고, 상위의 보도 강도가 하위보다 작지 않다.
- **큰 여부보다 잘게 나눈 등급을 두지 않는다.** 보도 강도를 큰 스토리라인과 목록의 행에 싣고, 쓰는 쪽이 그 값으로 순서를 정한다.
- **옛 기준(소스 기준 · 중요도 기준)의 값과 이력은 옮기지 않는다.** 두 기준을 새 값 하나로 옮기는 식이 없다. 판을 올리면 기준은 처음 값으로 시작한다.

## 결과

- 기준이 하나라 운영자가 조정할 값이 준다. 켠 소스 수와 조회 기간의 길이가 달라도 같은 기준을 쓴다.
- 판정(`storyline`)의 재료가 늘어 `event` 의 기간 집계가 중요도 합을 싣고, 기간 전체 집계를 내는 공개 조회가 생긴다. 큰 스토리라인 조회와 목록 조회가 기간 전체를 한 번 더 읽는다 — 그 시간은 재지 않았다.
- `storyline` 의 큰 판정이 `evaluation` 의 중요도 수준 범위를 쓰지 않는다.
- 소스마다 기사량이 크게 다르면 소스 하나를 켜고 끄는 것으로 보도 강도가 달라진다.
- 여러 날 기간에서는 하루에 몰린 큰 일이 묽어진다. 평가 세트는 하루 단위로만 쟀다.
- 운영자가 조정한 옛 기준과 그 이력은 판을 올릴 때 사라진다.
- 식과 처음 값은 평가 세트 하나로 정했다 — 운영 인스턴스의 실측으로 다시 본다.
- 큰 흐름과 그 갈래가 함께 나오는 것은 그대로다 — 쓰는 쪽이 행의 상위로 중복을 다룬다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 소스 수와 중요도 평균을 둘 다 기준과 견주는 꼴을 두고 값만 조정한다 | 보강한 라벨에서는 앞섰지만 원래 라벨에서 하위였고 네 조건의 평균이 낮았다. 소스 수가 켠 소스 수에 매인다 |
| 기간 전체 중요도 합에서 스토리라인이 차지하는 몫 | 라벨과 대응 하한에 따라 순위가 크게 흔들렸다. 기사가 적은 날에는 평범한 흐름도 기준을 넘기 쉽다 |
| 날마다 중요도 합 상위 몇 개를 큰 것으로 한다 | 네 조건의 평균이 채택한 식보다 낮았다. 큰 일이 없는 날에도 같은 수를 낸다 |
| 소스 수와 중요도가 높은 기사 수를 둘 다 기준과 견준다 | 보강한 라벨에서는 가장 앞섰지만 원래 라벨에서 하위였다. 소스 수가 켠 소스 수에 매이고 조정할 값이 셋이다 |
| 소스 수로 나누지 않는다(하루의 중요도 합) | 켠 소스 수가 다른 인스턴스마다 기준을 다시 잡아야 한다 |
| 분모의 소스 수를 켠 소스 수로 한다 | `storyline` 이 소스 설정을 알아야 하고, 그 기간에 기사를 내지 않은 소스가 값을 깎는다 |
| 큰 판정에서 최상위만 낸다 | [ADR-0131](0131-storyline-relations-are-explicit-links-with-containment-as-a-single-parent-tree-gathered-at-query-time.md)이 기각했다 — 큰 갈래가 큰 상위에 가려진다 |
| 큰 여부 위에 등급을 둔다 | 등급의 경계가 기준을 여럿으로 늘리고, 등급을 쓰는 곳이 아직 없다. 값을 행에 실으면 쓰는 쪽이 정할 수 있다 |
| 옛 기준을 새 기준으로 환산해 옮긴다 | 두 값을 하나로 옮기는 뜻 있는 식이 없다 |
