# ADR-0306: 변화 리포트는 구독 주제 보도량과 흐름 보도량을 서로 견주지 않고 각자 자기 직전 값과만 견준다

- 상태: 승인. 부분 대체 — 결과의 생성 지시([ADR-0334](0334-change-report-llm-input-carries-judgements-in-words-without-article-counts-or-interest-values.md))
- 날짜: 2026-11-24
- 근거: [이슈 #238](https://github.com/Theia0382/yozm-news/issues/238) — 기점 `f1d0938` 의 주제별 보도량(tag Spec — 첫 평가 시각)과 스토리라인 기간 집계(event-R20 — 사건 배정의 발행 시각)를 대조해 리포트에서 두 보도량을 함께 보일 때의 처리를 정함
- 관련: [ADR-0148](0148-storyline-coverage-is-the-article-count-in-the-period-over-the-closed-event-set.md)(두 보도량은 기간에 넣는 시각이 다르다 — 보충), [ADR-0303](0303-a-change-report-covers-only-what-relates-to-the-user-in-three-sections-tracked-flows-subscribed-topic-coverage-and-interest-changes.md)(갈래 셋)

## 맥락

- 리포트는 구독 주제의 보도량과 추적한 흐름의 보도량을 함께 보인다.
- 주제별 보도량은 첫 평가 시각이 기간 안인 기사를, 스토리라인 보도량은 사건 배정의 발행 시각이 기간 안인 기사를 센다(ADR-0148). 같은 기사가 두 수에서 다른 기간에 들 수 있다.
- 두 수를 같은 시각으로 맞추려면 `event` 의 집계가 `tag` 의 평가 시각을 읽거나, `tag` 가 사건 배정을 알아야 한다. `event` 는 이미 `tag` 를 읽어 `tag` 가 `event` 를 읽으면 순환이다.

## 결정

- **두 보도량은 서로 더하거나 비율을 내지 않는다.** 구독 주제 보도량은 보도량 갈래에, 흐름 보도량은 추적한 흐름 갈래의 흐름마다 둔다.
- **각자 자기 직전 값과만 견준다.** 스냅숏에 두 수의 기간 시각 종류(첫 평가 시각 · 발행 시각)를 적는다.

규칙은 [report Spec](../specs/report.md#재료) report-R14 · R19 에 있다.

## 결과

- 새 집계가 없고 모듈 의존이 바뀌지 않는다.
> 부분 대체("생성 지시가 두 수를 견주지 않게 해야 한다") — [ADR-0334](0334-change-report-llm-input-carries-judgements-in-words-without-article-counts-or-interest-values.md). 지금 상태: [report Spec](../specs/report.md#프롬프트) report-R23
- "이 주제 120건, 그 안의 흐름 80건"처럼 나란히 읽히면 기사 단위로 맞지 않는다 — 생성 지시가 두 수를 견주지 않게 해야 한다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 흐름 보도량을 첫 평가 시각으로 새로 센다 | `event` 의 집계가 `tag` 의 평가 시각을 읽는 새 집계가 생기고, 큰 판정 · 목록의 기간 규칙과 리포트만 달라진다 |
| 주제 보도량을 발행 시각으로 새로 센다 | `tag` 가 사건 배정을 알아야 해 의존이 순환하고, 운영자의 주제별 보도량과 리포트의 수가 달라진다 |
