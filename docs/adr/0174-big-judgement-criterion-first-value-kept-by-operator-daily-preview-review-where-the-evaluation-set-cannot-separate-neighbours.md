# ADR-0174: 큰 판정 기준의 처음 값은 평가 세트가 이웃한 값을 가르지 못해 운영자의 날마다 미리보기 검수로 정한다

- 상태: 승인
- 날짜: 2026-11-15
- 근거: [이슈 #126](https://github.com/Theia0382/yozm-news/issues/126) — 정한 잇기 기준 위에서 평가 세트로 큰 판정 기준 후보를 견주고, 실측 인스턴스의 날마다 미리보기를 운영자가 검수함
- 관련: [ADR-0147](0147-storyline-big-judgement-compares-importance-weighted-coverage-per-source-and-day-with-one-criterion-at-query-time.md)(결정 절 "처음 값은 평가 세트로 정한 측정 전 목표다" — 보충), [ADR-0118](0118-big-judgement-criteria-are-storyline-operator-settings-and-do-not-inherit-event-criteria-values-or-history.md)(맥락 절 "기준값은 측정 전 목표이고 평가 세트와 운영자의 표본 검수로 맞춘다" · 결정 절 "처음 값은 측정 전 목표다" — 보충), [ADR-0173](0173-storyline-linking-threshold-default-chosen-for-precision-on-an-evaluation-set-grid-and-operator-sample-review.md)(잇기 기준 — 큰 판정은 그 위에서 잰다), [ADR-0103](0103-big-judgement-on-storylines-and-the-storyline-milestone-right-after-event-clustering-takes-over-big-events.md)(M4 완료 기준), [ADR-0096](0096-instance-bound-evaluation-data-kept-out-of-the-public-repository-and-defaults-cite-only-the-evaluation-set.md)(근거 수치를 공개 문서에 적지 않는다)

## 맥락

- 큰 판정 기준(하루 보도 강도의 하한)의 처음 값은 평가 세트로 정한 측정 전 목표였다([ADR-0147](0147-storyline-big-judgement-compares-importance-weighted-coverage-per-source-and-day-with-one-criterion-at-query-time.md)).
- 큰 판정은 스토리라인 위에서 한다. 잇기 기준이 바뀌면 스토리라인의 구성이 바뀌어 큰 판정의 결과도 바뀐다.
- 평가 세트의 큰 일 라벨은 며칠치에 수십 건이다. 후보 값마다 정밀도의 구간이 넓고 이웃한 값끼리 구간이 겹친다. 라벨을 좁게 잡은 판과 넓게 잡은 판에서 같은 값의 정밀도 · 재현율이 다르다.
- 운영자는 화면의 미리보기로 기간마다 제안 기준의 큰 스토리라인을 볼 수 있고, 미리보기는 아무것도 바꾸지 않는다([ADR-0118](0118-big-judgement-criteria-are-storyline-operator-settings-and-do-not-inherit-event-criteria-values-or-history.md)).

## 결정

- **큰 판정 기준의 처음 값은 15.00 그대로다.** 값의 정본은 `BigStorylineCriteria.DEFAULT` 와 [storyline Spec "큰 판정"](../specs/storyline.md#큰-판정)이다.
- **잇기 기준을 먼저 정하고 큰 판정 기준은 그 위에서 잰다.**
- **값은 평가 세트와 운영자의 날마다 미리보기 검수로 정하고, 평가 세트가 값을 가르지 못하거나 둘이 엇갈리면 검수를 따른다.** 검수는 실측 인스턴스의 날마다, 후보 값 가운데 가장 낮은 값 이상의 스토리라인을 모두 놓고 그날 알릴 만한가를 판정한다. 낮은 기준의 목록이 높은 기준의 목록을 품으므로 한 번의 판정으로 모든 후보 값의 결과를 낸다.
- **운영자가 알릴 만함의 기준을 정한다.** 그날의 본 전개가 있는 일은 알릴 만하고, 결론이 난 뒤의 반응이나 예고 보도, 무관한 사건을 모은 스토리라인, 스포츠 결승은 아니다. 해외의 큰 사건과 지표의 이정표는 알릴 만하다.
- **식은 그대로 둔다.** 주말에 큰 스토리라인이 없는 날, 하루 기준을 여러 날 기간에 쓴 모습, 큰 판정 목록의 중복은 관찰로 남긴다([미결 사항](../open-questions.md) 21).
- **스토리라인이 없는 사건은 큰 판정에 넣지 않는다.** 큰 판정이 보지 못하는 단발성 큰 일은 실측 인스턴스에서 드물었다.
- 근거 수치는 공개 문서에 적지 않는다([ADR-0096](0096-instance-bound-evaluation-data-kept-out-of-the-public-repository-and-defaults-cite-only-the-evaluation-set.md)).

## 결과

- 처음 값은 측정한 소스 구성에 맞춘 값이다. 보도 강도는 켠 소스의 기사량을 따르므로, 소스를 켜고 끈 인스턴스는 운영자가 미리보기로 값을 다시 본다.
- 기사가 적은 주말에는 큰 스토리라인이 하나도 없는 날이 있다.
- 포함 관계의 상위는 하위의 기사까지 센다. 그날 직접 든 기사가 없는 상위와 그 하위가 같은 날 함께 크다고 나올 수 있다. 같은 사안이 두 스토리라인으로 갈라져 있으면 둘이 같은 날 함께 크다고 나온다.
- 검수는 운영자가 기준을 정하고 애매한 스토리라인을 판정했으며, 나머지는 그 기준으로 미리 판정해 둔 것을 따랐다.
- 다이제스트의 큰 슬롯 수와 함께 값을 조정하는 일은 다이제스트 측정의 몫이다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 평가 세트의 정밀도가 가장 높은 값으로 올린다 | 이웃한 값의 구간이 겹쳐 평가 세트로는 가르지 못한다. 검수에서 그 값은 알릴 만한 스토리라인의 다수를 놓쳤고 큰 스토리라인이 없는 날이 늘었다 |
| 재현율을 얻으려고 값을 내린다 | 검수에서 알릴 정도가 아닌 스토리라인이 함께 들어왔다 |
| 평일과 주말의 기준을 따로 둔다 | 기준은 값 하나다([ADR-0147](0147-storyline-big-judgement-compares-importance-weighted-coverage-per-source-and-day-with-one-criterion-at-query-time.md)). 주말을 며칠만 봤다 |
| 스토리라인이 없는 큰 사건을 큰 판정에 더한다 | 그런 사건이 드물었고, 큰 판정의 재료와 화면이 늘어난다 |
