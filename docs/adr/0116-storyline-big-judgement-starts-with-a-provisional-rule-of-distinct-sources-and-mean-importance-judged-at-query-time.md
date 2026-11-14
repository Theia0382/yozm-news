# ADR-0116: 스토리라인의 큰 판정은 기간 안 서로 다른 소스 수와 중요도 평균이 둘 다 기준 이상인지를 조회할 때 판정하는 임시 산식으로 시작한다

- 상태: 승인. 부분 대체 — 큰 판정의 식([ADR-0147](0147-storyline-big-judgement-compares-importance-weighted-coverage-per-source-and-day-with-one-criterion-at-query-time.md)) · 큰 스토리라인의 순서([ADR-0147](0147-storyline-big-judgement-compares-importance-weighted-coverage-per-source-and-day-with-one-criterion-at-query-time.md))
- 날짜: 2026-11-12
- 근거: [이슈 #124](https://github.com/Theia0382/yozm-news/issues/124) — `event` 의 큰 사건 판정 · 운영자 API · 화면 · 평가 도구와 `storyline` 의 소속 · 잇기 규칙을 조사해 리드와 정함
- 관련: [ADR-0082](0082-big-events-judged-at-query-time-by-distinct-sources-and-mean-importance-both-reaching-criteria.md)(큰 사건 판정 — 전체 대체), [ADR-0098](0098-label-rule-2-samples-hold-no-big-event-gold-and-big-event-precision-is-not-measured-without-marks.md)(사건 평가의 큰 사건 표시와 정밀도 — 전체 대체), [ADR-0095](0095-event-clustering-evaluated-by-an-operator-tool-comparing-admin-api-results-with-full-day-labels.md)(큰 사건 목록 · 미리보기 · 정밀도 — 부분 대체), [ADR-0103](0103-big-judgement-on-storylines-and-the-storyline-milestone-right-after-event-clustering-takes-over-big-events.md)(큰 판정의 단위와 거두는 시점 — 보충), [ADR-0106](0106-provisional-storyline-linking-opens-only-with-a-partner-event-and-adds-the-single-best-storyline-without-removal.md)(임시 잇기 규칙 — 짝이 있어야 연다), [ADR-0107](0107-storyline-state-is-judged-at-query-time-from-the-last-article-and-an-ended-storyline-still-links.md)(상태), [ADR-0110](0110-storyline-evaluation-labels-system-events-of-a-fixed-dump-and-measures-overlapping-clusters-from-random-seed-events.md)(스토리라인 평가의 큰 판정 정밀도), [ADR-0117](0117-big-judgement-materials-are-tallied-by-event-per-group-of-events-and-storyline-passes-only-event-ids.md)(재료를 세는 곳), [ADR-0118](0118-big-judgement-criteria-are-storyline-operator-settings-and-do-not-inherit-event-criteria-values-or-history.md)(기준)

## 맥락

- 큰 판정의 단위는 스토리라인이고, 스토리라인의 큰 판정을 만드는 작업이 `event` 의 큰 사건 판정을 거둔다([ADR-0103](0103-big-judgement-on-storylines-and-the-storyline-milestone-right-after-event-clustering-takes-over-big-events.md)). 큰 판정의 식은 스토리라인 사이 관계(포함 · 이어짐)와 잇는 방법이 정해진 뒤에 평가 세트로 견주어 정한다 — 그 전에도 운영자가 큰 일을 보는 화면은 끊기지 않아야 한다.
- 사건의 큰 사건 판정은 조회 기간 안 기사의 서로 다른 소스 수와 중요도 평균을 두 기준과 견주고, 조회할 때 판정해 저장하지 않았다([ADR-0082](0082-big-events-judged-at-query-time-by-distinct-sources-and-mean-importance-both-reaching-criteria.md)).
- 스토리라인 평가는 큰 판정을 스토리라인 × 날짜로 잰다([ADR-0110](0110-storyline-evaluation-labels-system-events-of-a-fixed-dump-and-measures-overlapping-clusters-from-random-seed-events.md)).
- 스토리라인의 행에는 전체 첫 · 마지막 기사 시각만 있다. 어느 날 기사가 있었는지는 구성 사건의 배정이 안다.
- 임시 잇기 규칙은 짝이 될 이웃 사건이 있어야 스토리라인을 열고([ADR-0106](0106-provisional-storyline-linking-opens-only-with-a-partner-event-and-adds-the-single-best-storyline-without-removal.md)), 사건 하나만 남은 스토리라인은 합치거나 푼다([ADR-0108](0108-storylines-with-the-same-events-merge-and-a-storyline-left-with-one-event-merges-or-dissolves.md)). 잇기를 켜기 전의 사건은 운영자가 채우기 전까지 스토리라인에 들지 않는다.
- 사건 평가 도구는 운영자 API 로 큰 사건 목록 · 미리보기를 읽어 큰 사건 정밀도를 쟀고([ADR-0095](0095-event-clustering-evaluated-by-an-operator-tool-comparing-admin-api-results-with-full-day-labels.md)), 라벨 기준 2판 표본은 큰 사건을 표시하지 않아 재지 않았다([ADR-0098](0098-label-rule-2-samples-hold-no-big-event-gold-and-big-event-precision-is-not-measured-without-marks.md)).

## 결정

> 부분 대체(항목 전체) — [ADR-0147](0147-storyline-big-judgement-compares-importance-weighted-coverage-per-source-and-day-with-one-criterion-at-query-time.md). 지금 상태: [storyline Spec "큰 판정"](../specs/storyline.md#큰-판정)
- **큰 스토리라인 = 조회 기간 안 구성 사건 기사의 서로 다른 소스 수 ≥ 소스 기준, 그리고 그 기사들의 중요도 평균 ≥ 중요도 기준(경계 포함).** 사건의 큰 사건 판정을 스토리라인에 옮긴 임시 산식이다. 식과 기준값은 측정 전 목표이고, 식을 평가 세트로 정할 때의 비교 기준선이다.
> 부분 대체(셋째 문장) — [ADR-0147](0147-storyline-big-judgement-compares-importance-weighted-coverage-per-source-and-day-with-one-criterion-at-query-time.md). 지금 상태: [storyline Spec "큰 판정"](../specs/storyline.md#큰-판정)
- **조회할 때 지금 기준으로 판정하고 결과를 저장하지 않는다.** 스토리라인의 상태 · 모델 버전과 무관하다. 순서는 소스 수, 중요도 평균(둘 다 내림차순), 스토리라인 ID 순이다.
- **판정 규칙은 `storyline` 의 `domain` 한 곳에 둔다.** 큰 스토리라인 조회 · 미리보기 · 목록의 큰 여부가 같은 규칙을 부른다.
- **조회 기간에 드는 스토리라인은 그 기간 안 기사가 있는 구성 사건이 하나라도 있는 스토리라인이다.** 목록과 큰 판정이 같은 뜻을 쓴다. 기간의 범위 규칙은 사건 조회와 같다.
- **스토리라인에 들지 않은 사건은 큰 판정에 들지 않는다.** 운영자 화면은 조회 기간 안 소속 없는 사건의 수와 기사 수를 큰 스토리라인 목록과 함께 보인다.
- **사건 평가 도구는 큰 사건을 재지 않는다.** 큰 판정 정밀도는 스토리라인 평가가 잰다([ADR-0110](0110-storyline-evaluation-labels-system-events-of-a-fixed-dump-and-measures-overlapping-clusters-from-random-seed-events.md)).

## 결과

- `event` 의 큰 사건 판정 · 큰 사건 목록 · 사건 목록의 큰 사건 여부와 운영자 화면이 없어지고, 운영자는 큰 스토리라인 화면을 본다.
- 사건 판정이 보이던 단발성 큰 일(짝이 될 사건이 없는 사건)은 큰 판정에서 빠진다. 빠지는 몫의 크기는 소속 없는 사건 수로만 보이고, 양은 재지 않았다. 새 스토리라인을 여는 조건이 바뀌면 달라진다.
- 임베딩 모델을 바꾼 직후에는 같은 흐름이 모델 버전마다 다른 스토리라인으로 나뉘어 각각 작게 세어질 수 있다.
- 조회마다 기간 안 사건, 그 사건이 든 스토리라인의 구성 사건, 기간 안 기사의 소스 · 중요도를 읽는다. 부하는 재지 않았다.
- 사건이 합쳐진 뒤 다음 잇기 회차가 소속을 옮길 때까지 그 사건의 기사는 스토리라인 집계에서 빠진다.
- 사건 평가 도구의 큰 사건 표시 파일 · 제안 기준 측정 · 큰 사건 정밀도가 없어지고, 사건 평가의 결과 기록 형식 판이 오른다. 사건 라벨 기준의 판은 그대로다.
- 운영 인스턴스의 큰 판정을 스토리라인 평가의 결과 파일로 내는 길은 스토리라인을 운영자 API 로 읽는 작업이 만든다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 식이 정해질 때까지 큰 판정을 비워 두고 사건 판정을 남긴다 | 큰 판정의 단위가 둘인 기간이 길어지고, 공개 조회 · 운영자 화면을 식이 정해진 뒤에 다시 만든다 |
| 큰 판정을 잇기 회차에서 저장한다 | 재료(엔트리 · 재평가)와 기준이 나중에 바뀌어 다시 판정할 장치가 필요하고, 미리보기가 서지 않는다 |
| 조회 기간에 드는 스토리라인을 첫 ~ 마지막 기사 시각이 기간과 겹치는 것으로 한다 | 그 기간에 기사가 없는 긴 스토리라인이 목록에 섞이고, 목록과 큰 판정의 기간 뜻이 달라진다 |
| 기간으로 거르지 않고 전체 목록에 집계만 기간으로 단다 | 기간과 무관한 행마다 집계를 세고, 그 기간의 큰 판정과 함께 읽기 어렵다 |
| 소속 없는 사건을 큰 판정에 섞는다 | 판정의 단위가 둘이 된다([ADR-0103](0103-big-judgement-on-storylines-and-the-storyline-milestone-right-after-event-clustering-takes-over-big-events.md)) |
| 소속 없는 사건을 문서에만 적는다 | 판정에서 빠지는 몫이 화면에 보이지 않아 잇기가 덜 이은 것을 가린다 |
| 스토리라인 평가 도구가 큰 스토리라인 운영자 API 를 읽어 결과 파일의 큰 표시를 채운다 | 결과 파일은 스토리라인의 기사 목록과 함께여야 쓸 수 있고, 그것을 내는 운영자 API 가 아직 없다 |
